package org.openqa.grid.web.servlet.rmn;

import java.util.Date;

/**
 * Created with IntelliJ IDEA. User: mhardin Date: 12/18/13 Time: 8:03 AM To change this template
 * use File | Settings | File Templates.
 */
// This is used to represent a run request which will get sent in by a test run requesting resources.
public class AutomationRunRequest {

    private String uuid;
    private int threadCount;
    private String browser;
    private Date createdDate;

    // Require callers to have required variables through constructor below
    private AutomationRunRequest() {
    }

    public AutomationRunRequest(String runUuid, int threadCount, String browser) {
        this.uuid = runUuid;
        this.threadCount = threadCount;
        this.browser = browser;
        createdDate = new Date();
    }

    /**
     * Returns the UUID for this run request
     * @return
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Returns the thread count requested by this run
     * @return
     */
    public int getThreadCount() {
        return threadCount;
    }

    /**
     * Returns the browser (e.g. 'chrome', 'firefox', etc) for this run request
     * @return
     */
    public String getBrowser() {
        return browser;
    }

    /**
     * Returns the created date for this run request
     * @return
     */
    public Date getCreatedDate() {
        return createdDate;
    }

}
