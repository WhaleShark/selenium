/*
Copyright 2011 Selenium committers
Copyright 2011 Software Freedom Conservancy

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/


package org.openqa.grid.web.servlet.rmn;

import com.amazonaws.services.ec2.model.Instance;

import org.openqa.grid.internal.Registry;
import org.openqa.grid.web.servlet.RegistryBasedServlet;
import org.openqa.grid.web.servlet.rmn.aws.ManageEC2;
import org.openqa.selenium.remote.BrowserType;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet used to register new runs as well as delete existing runs.  New runs will automatically spawn up new AMIs as needed
 */
public class AutomationTestRunServlet extends RegistryBasedServlet {

    private static final long serialVersionUID = 8484071790930378855L;
    private static final Logger log = Logger.getLogger(AutomationTestRunServlet.class.getName());
    private static final long START_DELAY_IN_SECONDS = 60L;
    private static final long HUB_TERMINATE_START_DELAY_IN_MINUTES= 5L;
    private static final long TEST_RUN_CLEANUP_POLLING_TIME_IN_SECONDS = 60L;
    private static final long EXPIRED_POLLING_TIME_IN_SECONDS = 15L;
    private static final long HUB_TERMINATION_POLLING_TIME_IN_MINUTES = 1L;

    public AutomationTestRunServlet() {
        this(null);
    }

    private void initCleanupThreads() {
        // Wrapper to lazily fetch the Registry object as this is not populated at instantiation time
        IRetrieveContext retrieveContext = new IRetrieveContext() {
            @Override
            public Registry retrieveRegistry() {
                return getRegistry();
            }
        };
        // Spin up a scheduled thread to poll for unused test runs and clean up them
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new AutomationRunCleanupTask(retrieveContext),
                AutomationTestRunServlet.START_DELAY_IN_SECONDS,AutomationTestRunServlet.TEST_RUN_CLEANUP_POLLING_TIME_IN_SECONDS, TimeUnit.SECONDS);
        // Spin up a scheduled thread to clean up and terminate nodes that were spun up
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new AutomationNodeCleanupTask(retrieveContext),
                AutomationTestRunServlet.START_DELAY_IN_SECONDS,AutomationTestRunServlet.EXPIRED_POLLING_TIME_IN_SECONDS, TimeUnit.SECONDS);
        String instanceId = System.getProperty(AutomationConstants.INSTANCE_ID);
        if(instanceId != null && instanceId.length() > 0) {
            log.info("Instance ID detected.  Hub termination thread will be started.");
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new AutomationHubCleanupTask(retrieveContext,instanceId),
                 AutomationTestRunServlet.HUB_TERMINATE_START_DELAY_IN_MINUTES,AutomationTestRunServlet.HUB_TERMINATION_POLLING_TIME_IN_MINUTES, TimeUnit.MINUTES);
        } else {
            log.info("Hub is not a dynamic hub -- termination logic will not be started");
        }
    }

    public AutomationTestRunServlet(Registry registry) {
        super(registry);
        // Start up our cleanup thread that will cleanup unused runs
        this.initCleanupThreads();
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String uuid = request.getParameter(AutomationConstants.UUID);
        if (uuid == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parameter 'uuid' must exist");
            return;
        }
        boolean deleteSuccessful;
        deleteSuccessful = AutomationContext.getContext().deleteRun(uuid);

        if (!deleteSuccessful) {
            response.sendError(HttpServletResponse.SC_CONFLICT, "Run does not exist on server");
            return;
        }
        response.setStatus(HttpServletResponse.SC_ACCEPTED);
        return;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        this.doPost(request,response);
    }


    /**
     * Attempts to register a new run request with the server.
     * Returns a 200 if the request can be fulfilled
     * Returns a 201 if the request can be fulfilled but AMIs must be started
     * Returns a 400 if the required parameters are not passed in.
     * Returns a 409 if the server is at full node capacity
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        String browserRequested = request.getParameter("browser");
        String osRequested = request.getParameter("os");
        String threadCount = request.getParameter("threadCount");
        String uuid = request.getParameter(AutomationConstants.UUID);
        // Return a 400 if any of the required parameters are not passed in
        // Check for uuid first as this is the most important variable
        if (uuid == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parameter 'uuid' must be passed in as a query string parameter");
            return;
        }
        if (browserRequested == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parameter 'browser' must be passed in as a query string parameter");
            return;
        }
        if (threadCount == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parameter 'threadCount' must be passed in as a query string parameter");
            return;
        }
        Integer threadCountRequested = Integer.valueOf(threadCount);
        log.info(String.format("Server request received.  Browser [%s] - Requested Node Count [%s] - Request UUID [%s]", browserRequested, threadCountRequested, uuid));
        boolean amisNeeded;
        int amisToStart=0;
        int currentlyAvailableNodes;
        // Synchronize this block until we've added the run to our context for other potential threads to see
        synchronized (this) {
            int remainingNodesAvailable = AutomationContext.getContext().getTotalHubNodesAvailable();
            // If the number of nodes this grid hub can actually run is less than the number requested, this hub can not fulfill this run at this time
            if(remainingNodesAvailable < threadCountRequested) {
                log.severe(String.format("Requested node count of [%d] could not be fulfilled due to hub limit. [%d] nodes available - Request UUID [%s]",threadCountRequested,remainingNodesAvailable,uuid));
                response.sendError(HttpServletResponse.SC_CONFLICT, "Server cannot fulfill request due to configured node limit being reached.");
                return;
            }
            // Get the number of matching, free nodes to determine if we need to start up AMIs or not
            currentlyAvailableNodes = AutomationUtils.getNumFreeSlotsForBrowser(getRegistry(), browserRequested,osRequested);
            // If the number of available nodes is less than the total number requested, we will have to spin up AMIs in order to fulfill the request
            amisNeeded = currentlyAvailableNodes < threadCountRequested;
            if(amisNeeded) {
                // Get the difference which will be the number of additional nodes we need to spin up to supplement existing nodes
                amisToStart = threadCountRequested - currentlyAvailableNodes;
            }
            // If the browser requested is not supported by AMIs, we need to not unnecessarily spin up AMIs
            if(amisNeeded && !browserSupportedByAmis(browserRequested)) {
                response.sendError(HttpServletResponse.SC_GONE,"Request cannot be fulfilled and browser is not supported by AMIs");
                return;
            }
            // Add the run to our context so we can track it
            AutomationRunRequest newRunRequest = new AutomationRunRequest(uuid, threadCountRequested, browserRequested);
            boolean addSuccessful = AutomationContext.getContext().addRun(newRunRequest);
            if(!addSuccessful) {
                log.warning(String.format("Test run already exists for the same UUID [%s]",uuid));
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Test run already exists with the same UUID.");
                return;
            }
        }
        if (amisNeeded) {
            // Start up AMIs as that will be required
            log.warning(String.format("Insufficient nodes to fulfill request. New AMIs will be queued up. Requested [%s] - Available [%s] - Request UUID [%s]",threadCountRequested, currentlyAvailableNodes, uuid));
            boolean amisStartedSuccessfully = startNodes(uuid,amisToStart,browserRequested,osRequested);
            if(!amisStartedSuccessfully) {
                // Make sure and de-register the run if the AMI startup was not successful
                AutomationContext.getContext().deleteRun(uuid);
                response.sendError(HttpServletResponse.SC_CONFLICT,"Nodes could not be started");
                return;
            }
            // Return a 201 to let the caller know AMIs will be started
            response.setStatus(HttpServletResponse.SC_CREATED);
            return;
        } else {
            // Otherwise just return a 202 letting the caller know the requested resources are available
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            return;
        }
    }

    /**
     * Starts up AMIs
     * @param threadCountRequested
     * @return
     */
    private boolean startNodes(String uuid,int threadCountRequested, String browser, String os) {
        log.info(String.format("%d threads requested",threadCountRequested));
        try{ String localhostname;
            // Try and get the IP address from the system property
            String runTimeHostName = System.getProperty(AutomationConstants.IP_ADDRESS);
            try{
                localhostname = (runTimeHostName != null ) ? runTimeHostName : InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                return false;
            }
            ManageEC2 ec2 = new ManageEC2();
            // TODO Make matching logic better
            int numThreads;
            if(browser.equals(BrowserType.CHROME)) {
                numThreads = ManageEC2.CHROME_THREAD_COUNT;
            } else if (browser.equals(BrowserType.IE.replace(" ","")) || browser.equals(BrowserType.FIREFOX)) {
                numThreads=ManageEC2.FIREFOX_IE_THREAD_COUNT;
            } else {
                throw new RuntimeException("Unsupported browser: " + browser);
            }
            int leftOver = threadCountRequested % numThreads;
            int machinesNeeded = (threadCountRequested / numThreads);
            if(leftOver != 0) {
                // Add the remainder
                machinesNeeded++;
            }
            log.info(String.format("%s nodes will be requested",machinesNeeded));
            List<Instance> instances = ec2.launchNodes(uuid, os, browser, localhostname,
                                                       machinesNeeded, numThreads);
            log.info(String.format("%d instances started", instances.size()));
            // Reuse the start date since all the nodes were created within the same request
            Date startDate = new Date();
            for(Instance instance : instances) {
                log.info("Node instance id: " + instance.getInstanceId());
                AutomationContext.getContext().addNode(
                    new AutomationDynamicNode(uuid, instance.getInstanceId(), browser, os, startDate,
                                              numThreads));
            }
        } catch(Exception e) {
            log.severe("Error trying to start nodes: " + e);
            return false;
        }

        return true;
    }

    /**
     * Returns true if the requested browser can be used within AMIs, and false otherwise
     * @param browser
     * @return
     */
    private boolean browserSupportedByAmis(String browser) {
        return "chrome".equals(browser) || "firefox".equals(browser) || "internetexplorer".equals(browser);
    }

}
