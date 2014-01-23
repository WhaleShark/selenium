package org.openqa.grid.web.servlet.rmn;

import org.openqa.grid.internal.ProxySet;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.selenium.remote.CapabilityType;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA. User: mhardin Date: 12/18/13 Time: 7:48 AM To change this template
 * use File | Settings | File Templates.
 */
public class AutomationUtils {

    /**
     * Returns the number of available nodes for a specified browser
     * @param registry
     * @param requestedBrowser
     * @return
     */
    public static int getNumFreeSlotsForBrowser(Registry registry, String requestedBrowser,String requestedOs) {
        // This will keep a count of the number of instances that can run our requested test
        int totalCapableInstances = 0;
        // Current runs registered with the hub.  Make a copy of the set so we don't muck with the original set of registered runs
        Set<String> currentRuns = new HashSet<String>(AutomationContext.getContext().getRunUuids());
        ProxySet proxySet = registry.getAllProxies();
        Iterator<RemoteProxy> iterator = proxySet.iterator();
        while (iterator.hasNext()) {
            RemoteProxy proxy = iterator.next();
            int nodeBrowserCapableInstances = 0;
            int nodeTotalCapableInstances = proxy.getMaxNumberOfConcurrentTestSessions();
            List<TestSlot> slots = proxy.getTestSlots();
            int currentNodeCapacity = 0;
            for (TestSlot testSlot : slots) {
                TestSession session = testSlot.getSession();
                Map<String,Object> testSlotCapabilities = testSlot.getCapabilities();
                Object browser = testSlotCapabilities.get(CapabilityType.BROWSER_NAME);
                Object os = testSlotCapabilities.get(CapabilityType.PLATFORM);
                Object instanceId = testSlotCapabilities.get(AutomationConstants.INSTANCE_ID);
                if(instanceId != null) {
                    AutomationDynamicNode node = AutomationContext.getContext().getNode((String)instanceId);
                    // If this node has been spun up and it is no longer in the running state, go to the next test slot
                    // as we cannot consider this node to be a free resource
                    if(node != null) {// There really shouldn't ever be a null node here but adding the check regardless
                        if(node.getStatus() != AutomationDynamicNode.STATUS.RUNNING) {
                            break;
                        }
                    }
                }
                // An active session means there is a test running on this node (proxy), so increment our count
                if (session != null) {
                    Map<String,Object> sessionCapabilities = session.getRequestedCapabilities();
                    Object uuid = sessionCapabilities.get(AutomationConstants.UUID);
                    // If the session has a UUID, go ahead and remove it from our runs that we're going to subtract from our available
                    // node count as this means the run is under way
                    if(uuid != null) {
                        currentRuns.remove(uuid);
                    }
                    currentNodeCapacity++;
                } else if (browser != null && ((String) browser).replace(" ", "").toLowerCase().contains(requestedBrowser.replace(" ", "").toLowerCase())) {
                    String browserString = ((String)browser).replace(" ","").toLowerCase();
                    String osString = ((String)os).replace(" ","").toLowerCase();
                    // Handle OS not being passed in
                    if(browserString.contains(requestedBrowser.replace(" ","")) && (requestedOs == null || osString.contains(requestedOs.replace(" ", "").toLowerCase()))) {
                        nodeBrowserCapableInstances++;
                    }
                }
                // If this node has enough tests running to meet its available capacity, then we want to break out
                // as this node has no available capacity
                if (currentNodeCapacity >= nodeTotalCapableInstances) {
                    // Reset the number for available tests for the node so we don't add them later on
                    nodeTotalCapableInstances = 0;
                    break;
                }
            }
            // If the node has 0 amount of total instances it can run, or if there are 0 matching browsers, continue to the next test slot
            // as requested tests can NOT run on this slot
            if (nodeTotalCapableInstances == 0 || nodeBrowserCapableInstances == 0) {
                continue;
            }
            // Take the lesser of the two for number of tests that can run on this machine at a given time
            if (nodeBrowserCapableInstances > nodeTotalCapableInstances) {
                totalCapableInstances += nodeTotalCapableInstances;
            } else if (nodeTotalCapableInstances >= nodeBrowserCapableInstances) {
                totalCapableInstances += nodeBrowserCapableInstances;
            }
        }
        // Any runs still in this set means that run has not started yet, so we should consider this in our math
        for(String uuid : currentRuns) {
            AutomationRunRequest request = AutomationContext.getContext().getRunRequest(uuid);
            // If we're not dealing with an old run request that just never started, go ahead and decrement
            // the value from available nodes on this hub
            if(!isRunOld(request)) {
                totalCapableInstances -= request.getThreadCount();
            }
        }
        // Make sure we don't return a negative number to the caller
        if(totalCapableInstances < 0) {
            totalCapableInstances = 0;
        }
        return totalCapableInstances;
    }

    /**
     * Returns true if the request passed in is more than 2 minutes old, false otherwise
     * @param request
     * @return
     */
    private static boolean isRunOld(AutomationRunRequest request) {
        Date requestedDate = request.getCreatedDate();
        Calendar c = Calendar.getInstance();
        c.setTime(requestedDate);
        // Add 2 minutes so we can see how new this run request is
        c.add(Calendar.MINUTE, 2);
        // If the current time is more than 2 minutes after the create date of the request, return false
        return new Date().after(c.getTime());
    }

    /**
     * Sleeps for the specified time
     * @param msToSleep Time in milliseconds to sleep
     */
    public static void sleep(int msToSleep) {
        try{
            Thread.sleep(msToSleep);
        } catch(InterruptedException e) {

        }
    }

}
