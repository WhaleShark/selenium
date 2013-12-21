package org.openqa.grid.web.servlet.rmn;

import java.util.Calendar;
import java.util.Date;

public class AutomationDynamicNode implements Comparable<AutomationDynamicNode> {

    // RUNNING means this node is running and no further action needs to be taken
    // EXPIRED means the node has passed its expiration date and needs to be terminated.  A node
    // will not be marked expired if there is sufficient load on the system to require the node resources
    // TERMINATED means the node has been successfully terminated through the EC2 API
    public enum STATUS {RUNNING,EXPIRED,TERMINATED};

    private static final int NODE_INTERVAL_LIFETIME = 55; // 55 minutes

    private String uuid,instanceId,browser,os;
    private Date startDate,endDate;
    private int nodeCapacity;
    private volatile STATUS status;

    public AutomationDynamicNode(String uuid,String instanceId,String browser, String os,Date startDate,int nodeCapacity){
        this.uuid = uuid;
        this.instanceId = instanceId;
        this.browser = browser;
        this.os = os;
        this.startDate = startDate;
        this.endDate = getEndDate(startDate);
        this.nodeCapacity = nodeCapacity;
        this.status = STATUS.RUNNING;
    }

    public void updateStatus(STATUS status) {
        this.status = status;
    }

    private Date getEndDate(Date dateStarted) {
        Calendar c = Calendar.getInstance();
        c.setTime(dateStarted);
        c.add(Calendar.MINUTE, AutomationDynamicNode.NODE_INTERVAL_LIFETIME);  // number of days to add
        return c.getTime();
    }

    public String getUuid() {
        return uuid;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getBrowser() {
        return browser;
    }

    public String getOs() {
        return os;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * Increments the end date by an hour
     */
    public void incrementEndDateByOneHour() {
        Calendar c = Calendar.getInstance();
        c.setTime(getEndDate());
        // Add 60 seconds so we're as close to the hour as we can be instead of adding 55 again
        c.add(Calendar.MINUTE,60);
        setEndDate(c.getTime());
    }

    public int getNodeCapacity() {
        return nodeCapacity;
    }

    public void setNodeCapacity(int nodeCapacity) {
        this.nodeCapacity = nodeCapacity;
    }

    public STATUS getStatus(){
        return status;
    }

    @Override
    public int compareTo(AutomationDynamicNode o) {
        return this.startDate.compareTo(getStartDate());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AutomationDynamicNode that = (AutomationDynamicNode) o;

        if (!instanceId.equals(that.instanceId)) {
            return false;
        }
        if (!uuid.equals(that.uuid)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = uuid.hashCode();
        result = 31 * result + instanceId.hashCode();
        return result;
    }
}
