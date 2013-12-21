package org.openqa.grid.web.servlet.rmn;

import org.openqa.grid.internal.ProxySet;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.web.servlet.rmn.aws.ManageEC2;

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

// This class moves nodes that are past their expired date into the 'Expired' state
public class AutomationNodeCleanupTask extends Thread {

    private static final Logger log = Logger.getLogger(AutomationNodeCleanupTask.class.getName());

    private IRetrieveContext retrieveContext;
    private ManageEC2 ec2;

    public AutomationNodeCleanupTask(IRetrieveContext retrieveContext) {
        this.retrieveContext = retrieveContext;
        ec2 = new ManageEC2();

    }

    // We're going to continuously iterate over registered nodes with the hub.  If they're expired, we're going to mark them for removal.
    // If nodes marked for removal are used into the next billing cycle, then we're going to move their end date back again and put them back
    // into the running queue
    @Override
    public void run() {
        log.info("Performing cleanup on nodes.");
        AutomationRunContext context = AutomationContext.getContext();
        Map<String,AutomationDynamicNode> nodes = context.getNodes();
        Iterator<String> iterator = nodes.keySet().iterator();
        Date nowDate = new Date();
        while(iterator.hasNext()) {
            String instanceId = iterator.next();
            AutomationDynamicNode node = nodes.get(instanceId);
            AutomationDynamicNode.STATUS nodeStatus = node.getStatus();
            // If the current time is after the scheduled end time for this node and the node is still running, go ahead and queue it to be removed
            if(nodeStatus == AutomationDynamicNode.STATUS.RUNNING && nowDate.after(node.getEndDate())) {
                int freeSlots = AutomationUtils.getNumFreeSlotsForBrowser(retrieveContext.retrieveRegistry(),node.getBrowser(),node.getOs());
                // If free slots are greater than OR equal to our node capacity, that means we have enough wiggle room to go ahead and delete this node
                if(freeSlots >= node.getNodeCapacity()) {
                    log.info(String.format("Updating node %s to 'EXPIRED' status.  Start date [%s] End date [%s]",instanceId,node.getStartDate(),node.getEndDate()));
                    node.updateStatus(AutomationDynamicNode.STATUS.EXPIRED);
                    // Adding a continue here as no other checks need to be made for this node at this time
                    continue;
                }
            } else if(nodeStatus == AutomationDynamicNode.STATUS.EXPIRED) {
                if(isNodeInNextBillingCycle(node, nowDate)) {
                    log.info(String.format("Node [%s] was still running after initial allotted time.  Resetting status and increasing end date.",instanceId));
                    node.incrementEndDateByOneHour();
                    node.updateStatus(AutomationDynamicNode.STATUS.RUNNING);
                } else if(isNodeCurrentlyEmpty(retrieveContext.retrieveRegistry(), instanceId)) {
                    log.info(String.format("Terminating node %s and updating status to 'TERMINATED'",instanceId));
                    // Delete node
                    ec2.terminateInstance(instanceId);
                    node.updateStatus(AutomationDynamicNode.STATUS.TERMINATED);

                }
            }
        }
    }

    /**
     * Returns true if the specified node has passed into the next billing cycle (start + 60 minutes)
     * and false otherwise
     * @param node
     * @param nowDate
     * @return
     */
    private boolean isNodeInNextBillingCycle(AutomationDynamicNode node, Date nowDate) {
        Calendar futureExpirationDate = Calendar.getInstance();
        // We're currently doing end date (start + 55 minutes) + 6 minutes, so 61 minutes total from the start date.
        // Would it be better to just do start date + 61 minutes?
        futureExpirationDate.setTime(node.getEndDate());
        futureExpirationDate.add(Calendar.MINUTE, 6);  // 6 minutes instead of 5 just to be safe
        // If we're basically into the next hour of billing, go ahead and leave it on until the next cycle
        return nowDate.after(futureExpirationDate.getTime());
    }

    /**
     * Returns true if the specified node is empty and has no runs on it, and false otherwise
     * @param registry
     * @param instanceToFind
     * @return
     */
    public boolean isNodeCurrentlyEmpty(Registry registry,String instanceToFind) {
        ProxySet proxySet = registry.getAllProxies();
        for(RemoteProxy proxy : proxySet){
            List<TestSlot> slots = proxy.getTestSlots();
            boolean nodeFound = false;
            for (TestSlot testSlot : slots) {
                Map<String,Object> capabilities = testSlot.getCapabilities();
                Object instanceId = capabilities.get(AutomationConstants.INSTANCE_ID);
                if(instanceId != null) {
                    if(instanceToFind.equals(instanceId)) {
                        nodeFound = true;
                    }
                }
                // If we haven't found the node we're looking for, continue on to the next node
                if(!nodeFound) {
                    break;
                }
                // If we find a running session, this node is still occupied, so we should return false
                if(testSlot.getSession() != null) {
                    return false;
                }
            }
            // If we didn't find the node we're looking for, continue on to the next one to check
            if(!nodeFound) {
                continue;
            } else {
                // If we found the node, and no running sessions were found, the node is currently empty
                return true;
            }
        }
        // Throw an exception if we could not find a matching node, as this is probably a serious problem
        throw new RuntimeException("Node was not found in registry.  Node instanceId: " + instanceToFind);
    }
}
