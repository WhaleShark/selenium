package org.openqa.grid.web.servlet.rmn;

import java.util.logging.Logger;

public class AutomationRunCleanupTask extends Thread {

    private static final Logger log = Logger.getLogger(AutomationTestRunServlet.class.getName());

    private IRetrieveContext retrieveContext;

    public AutomationRunCleanupTask(IRetrieveContext retrieveContext) {
        this.retrieveContext = retrieveContext;
    }

    // Basically we want to continuously monitor all runs that are registered and ensure that at least
    // one test is being run that belongs to that test run.  If there are any orphaned runs, we will
    // go ahead and remove it from AutomationRunContext providing they are old enough
    @Override
    public void run() {
        log.info("Performing cleanup on runs.");
        AutomationRunContext context = AutomationContext.getContext();
        context.cleanUpRunRequests(retrieveContext.retrieveRegistry());
    }
}
