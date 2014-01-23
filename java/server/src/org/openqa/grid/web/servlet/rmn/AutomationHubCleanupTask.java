package org.openqa.grid.web.servlet.rmn;

import org.openqa.grid.internal.ProxySet;
import org.openqa.grid.web.servlet.rmn.aws.ManageEC2;

import java.util.logging.Logger;

// This class moves nodes that are past their expired date into the 'Expired' state
public class AutomationHubCleanupTask extends Thread {

    private static final Logger log = Logger.getLogger(AutomationHubCleanupTask.class.getName());

    private IRetrieveContext retrieveContext;
    private ManageEC2 ec2;
    private final String instanceId;

    public AutomationHubCleanupTask(IRetrieveContext retrieveContext, String instanceId) {
        this.retrieveContext = retrieveContext;
        ec2 = new ManageEC2();
        this.instanceId = instanceId;

    }

    // We're going to continuously iterate over registered nodes with the hub.  If they're expired, we're going to mark them for removal.
    // If nodes marked for removal are used into the next billing cycle, then we're going to move their end date back again and put them back
    // into the running queue
    @Override
    public void run() {
        log.info("Performing cleanup on hub.");
        ProxySet proxySet = retrieveContext.retrieveRegistry().getAllProxies();
        if(proxySet.isEmpty()) {
            log.warning("No running nodes found -- terminating hub: " + instanceId);
            ec2.terminateInstance(instanceId);
        }
    }
}
