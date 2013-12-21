package org.openqa.grid.web.servlet.rmn;

import org.openqa.grid.internal.utils.DefaultCapabilityMatcher;

import java.util.Map;
import java.util.logging.Logger;

/**
 * CapabilityMatcher which will not match a node that is marked as Expired/Terminated per AutomationNodeCleanupTask
 */
public class AutomationCapabilityMatcher extends DefaultCapabilityMatcher {

    private static final Logger log = Logger.getLogger(AutomationCapabilityMatcher.class.getName());

    @Override
    public boolean matches(Map<String, Object> nodeCapability,Map<String, Object> requestedCapability) {
        // If neither expected config value exists, go ahead and default to the default matching behavior
        // as this node is most likely not a dynamically started node
        if(!nodeCapability.containsKey(AutomationConstants.INSTANCE_ID) || !nodeCapability.containsKey(AutomationConstants.UUID)) {
            return super.matches(nodeCapability,requestedCapability);
        }
        String instanceId = (String)nodeCapability.get(AutomationConstants.INSTANCE_ID);
        String uuid = (String)nodeCapability.get(AutomationConstants.UUID);
        AutomationRunContext context = AutomationContext.getContext();
        // If the run that spun up these hubs is still happening, just perform the default matching behavior
        // as that run is the one that requested these nodes.
        if(context.hasRun(uuid)) {
            return super.matches(nodeCapability,requestedCapability);
        } else {
            AutomationDynamicNode node = context.getNode(instanceId);
            if(node != null && (node.getStatus() == AutomationDynamicNode.STATUS.EXPIRED || node.getStatus() == AutomationDynamicNode.STATUS.TERMINATED) ) {
                log.info(String.format("Node [%s] will not be used to match a request as it is expired/terminated",instanceId));
                // If the run that spun these hubs up is not in progress AND this node has been flagged to shutdown,
                // do not match this node up to fulfill a test request
                return false;
            } else {
                // If the node couldn't be retrieved or was not expired/terminated, then we should just use the default matching behavior
                return super.matches(nodeCapability,requestedCapability);
            }
        }
    }
}
