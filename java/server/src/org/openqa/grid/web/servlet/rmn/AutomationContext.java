package org.openqa.grid.web.servlet.rmn;

/**
 * Created with IntelliJ IDEA. User: mhardin Date: 12/18/13 Time: 8:13 AM To change this template
 * use File | Settings | File Templates.
 */
public class AutomationContext {

    private static AutomationRunContext context = new AutomationRunContext();

    // Singleton to maintain a context object
    public static AutomationRunContext getContext() {
        return context;
    }

    static {
        String totalNodeCount = System.getProperty("totalNodeCount");
        // Default to 150 if node count was not passed in
        if(totalNodeCount == null) {
            totalNodeCount = "150";
        }
        getContext().setTotalNodeCount(Integer.parseInt(totalNodeCount));
    }

}
