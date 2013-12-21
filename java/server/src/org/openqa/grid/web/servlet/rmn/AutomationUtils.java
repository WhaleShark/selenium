package org.openqa.grid.web.servlet.rmn;

import org.openqa.grid.internal.ProxySet;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.selenium.remote.CapabilityType;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
                Map<String,Object> capabilities = testSlot.getCapabilities();
                Object browser = capabilities.get(CapabilityType.BROWSER_NAME);
                Object os = capabilities.get(CapabilityType.PLATFORM);
                Object instanceId = capabilities.get(AutomationConstants.INSTANCE_ID);
                if(instanceId != null) {
                    AutomationDynamicNode node = AutomationContext.getContext().getNode((String)instanceId);
                    // If this node has been spun up and it is no longer in the running state, go to the next test slot
                    // as we cannot count the current node for available slots
                    if(node != null) {// There really shouldn't ever be a null node here but adding the check regardless
                        synchronized (node) {
                            if(node.getStatus() != AutomationDynamicNode.STATUS.RUNNING) {
                                break;
                            }
                        }

                    }
                }
                // An active session means there is a test running on this node (proxy), so increment our count
                if (session != null) {
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
        return totalCapableInstances;
    }

    public static void sleep(int msToSleep) {
        try{
            Thread.sleep(msToSleep);
        } catch(InterruptedException e) {

        }
    }

}
