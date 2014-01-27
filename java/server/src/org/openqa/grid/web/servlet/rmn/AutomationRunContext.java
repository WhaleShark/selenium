package org.openqa.grid.web.servlet.rmn;

import com.google.common.collect.Maps;

import org.openqa.grid.internal.ProxySet;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Created with IntelliJ IDEA. User: mhardin Date: 12/18/13 Time: 8:02 AM To change this template
 * use File | Settings | File Templates.
 */
public class AutomationRunContext {

    private static final Logger log = Logger.getLogger(AutomationRunContext.class.getName());

    private static final int CLEANUP_LIFE_LENGTH_IN_SECONDS = 90; // 1.5 minutes
    private Map<String, AutomationRunRequest> requests = Maps.newConcurrentMap();
    private Map<String,AutomationDynamicNode> nodes = Maps.newConcurrentMap();

    private int totalNodeCount;

    /**
     * Deletes the run from the requests map
     *
     * @param uuid UUID of the run to delete
     */
    public boolean deleteRun(String uuid) {
        AutomationRunRequest request;
        synchronized (requests) {
            request = requests.remove(uuid);
        }
        return request != null;
    }

    /**
     * Adds the specified run to the requests map
     *
     * @param runRequest Request object to add to the map
     * @return Returns false if the request already exists
     */
    public boolean addRun(AutomationRunRequest runRequest) {
        String uuid = runRequest.getUuid();
        synchronized (requests) {
            if (requests.containsKey(uuid)) {
                return false;
            }
            requests.put(uuid, runRequest);
        }
        return true;
    }

    /**
     * Returns true if the run already exists, false otherwise
     * @param uuid
     * @return
     */
    public boolean hasRun(String uuid) {
        return requests.containsKey(uuid);
    }

    /**
     * Gets the run request for the specified UUID
     * @param uuid
     * @return
     */
    public AutomationRunRequest getRunRequest(String uuid) {
        synchronized (requests) {
            return requests.get(uuid);
        }
    }

    /**
     * Returns the registered run UUIDs
     * @return
     */
    public Set<String> getRunUuids() {
        return requests.keySet();
    }

    /**
     * Returns true if there are any registered runs that have started in the last 2 minutes, false otherwise
     * @return
     */
    public boolean isNewRunQueuedUp() {
        synchronized (requests) {
            Set<String> uuids = AutomationContext.getContext().getRunUuids();
            for(String uuid : uuids) {
                AutomationRunRequest request = AutomationContext.getContext().getRunRequest(uuid);
                if(request.isNewRun()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds the node to the internal tracking map
     * @param node
     */
    public void addNode(AutomationDynamicNode node) {
        nodes.put(node.getInstanceId(), node);
    }

    /**
     * Adds the node to the internal tracking map
     * @param instanceId Instance ID
     * @param node
     */
    public void addNode(String instanceId,AutomationDynamicNode node) {
        nodes.put(instanceId, node);
    }

    /**
     * Returns the specified node from the internal tracking map
     * @param instanceId
     * @return
     */
    public AutomationDynamicNode getNode(String instanceId) {
        return nodes.get(instanceId);
    }

    /**
     * Returns true if the specified instance exists, false otherwise
     * @param instanceId
     * @return
     */
    public boolean nodeExists(String instanceId) {
        return nodes.containsKey(instanceId);
    }

    /**
     * Returns the internal tracking map.  Make sure any operation done on this map is thread safe
     * using each node for synchronization
     * @return
     */
    public Map<String,AutomationDynamicNode> getNodes() {
        return nodes;
    }

    /**
     * Clean up any requests with no remaining running tests.
     * @param registry
     */
    public void cleanUpRunRequests(Registry registry) {
        AutomationRunContext context = AutomationContext.getContext();
        Set<String> uuidsToRemove = new HashSet<String>();
        Iterator<String> requestsIterator = requests.keySet().iterator();
        if(requestsIterator.hasNext()) {
            // Grab our current date to use on all the requests we check
            Date currentDate = new Date();
            synchronized (requests) {
                while(requestsIterator.hasNext())
                {
                    String uuid = requestsIterator.next();
                    AutomationRunRequest request = requests.get(uuid);
                    if(!isRunOld(currentDate,request)) {
                        log.info(String.format("Run [%s] is not at least [%d] seconds old.  Will not analyze.",uuid,AutomationRunContext.CLEANUP_LIFE_LENGTH_IN_SECONDS));
                        continue;
                    }
                    boolean uuidFound = false;
                    ProxySet proxySet = registry.getAllProxies();
                    for(RemoteProxy proxy : proxySet) {
                        List<TestSlot> slots = proxy.getTestSlots();
                        // Once we find at least one test run with the given UUID, we want to break out
                        // as we are looking for runs with NO running tests with a matching UUID
                        for(int i = 0;!uuidFound && i< slots.size(); i++) {
                            TestSession testSession = slots.get(i).getSession();
                            if(testSession != null) {
                                // Pull out the UUID so we can see
                                Object testUuid = testSession.getRequestedCapabilities().get(AutomationConstants.UUID);
                                if(uuid.equals(testUuid)) {
                                    uuidFound = true;
                                    break;
                                }
                            }
                        }
                        // If we found the UUID on this node, we don't need to check any other nodes as we only need to know about
                        // at least run test still running in order to know we don't need to remove this run
                        if(uuidFound) {
                            break;
                        }
                    }
                    // If we didn't find a test belonging to this uuid, go ahead and remove the run
                    if(!uuidFound) {
                        log.info(String.format("Tracked test run [%s] found with no running tests.  Adding to set for removal.",uuid));
                        uuidsToRemove.add(uuid);
                    }
                    // Otherwise go ahead and continue to look at our other registered runs
                    else {
                        continue;
                    }
                }
            }
        }
        if(uuidsToRemove.size() == 0) {
            log.warning("No runs found to clean up");
        }
        for(String uuidToRemove : uuidsToRemove) {
            log.warning(String.format("Removing run because it has no more running test slots. UUID [%s]",uuidToRemove));
            context.deleteRun(uuidToRemove);
        }
    }

    /**
     * Returns true if the run request is old enough for the configure criteria
     * @param currentDate
     * @param runRequest
     * @return
     */
    private boolean isRunOld(Date currentDate, AutomationRunRequest runRequest) {
        // Get the amount of seconds passed
        Calendar c = Calendar.getInstance();
        c.setTime(runRequest.getCreatedDate());
        c.add(Calendar.SECOND,AutomationRunContext.CLEANUP_LIFE_LENGTH_IN_SECONDS);
        return currentDate.after(c.getTime());
    }

    /**
     * Returns the total node count supported for this grid hub
     * @return
     */
    public int getTotalNodeCount() {
        return totalNodeCount;
    }

    /**
     * Sets the total node count supported for this grid hub
     * @param totalNodeCount
     */
    public void setTotalNodeCount(int totalNodeCount) {
        this.totalNodeCount = totalNodeCount;
    }

    /**
     * Returns the number of additional nodes this hub can support.  This considers all
     * test runs that are in progress
     * @return
     */
    public int getTotalHubNodesAvailable() {
        int currentlyUsedNodes = 0;
        // Iterate over all runs currently running and add up their count so we can diff this from the total
        // count this hub can support
        for(AutomationRunRequest request : requests.values()){
            currentlyUsedNodes += request.getThreadCount();
        }
        return totalNodeCount - currentlyUsedNodes;
    }

}
