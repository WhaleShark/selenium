package org.openqa.grid.web.servlet.rmn;

import org.openqa.grid.internal.Registry;

/**
 * Created with IntelliJ IDEA.
 * User: mhardin
 * Date: 12/21/13
 * Time: 1:13 PM
 * To change this template use File | Settings | File Templates.
 */
public interface IRetrieveContext {

    /**
     * Retrieves a Selenium Registry
     * @return
     */
    Registry retrieveRegistry();

}
