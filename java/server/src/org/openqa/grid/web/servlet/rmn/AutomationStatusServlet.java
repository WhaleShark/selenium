/*
Copyright 2011 Selenium committers
Copyright 2011 Software Freedom Conservancy

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.openqa.grid.web.servlet.rmn;

import com.google.common.io.ByteStreams;

import org.openqa.grid.internal.Registry;
import org.openqa.grid.web.servlet.RegistryBasedServlet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Legacy API to pull free threads for a given browser
 */
public class AutomationStatusServlet extends RegistryBasedServlet {

    private static final long serialVersionUID = 8484071790930378855L;
    private static final Logger log = Logger.getLogger(AutomationStatusServlet.class.getName());

    public AutomationStatusServlet() {
        this(null);
    }

    public AutomationStatusServlet(Registry registry) {
        super(registry);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        String browserRequested = request.getParameter("browser");

        // OS is optional
        String os = request.getParameter("os");
        if (browserRequested == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parameter 'browser' must be passed in as a query string parameter");
            return;
        }
        log.info(String.format("Legacy server request received.  Browser [%s]", browserRequested));
        AutomationRunContext context = AutomationContext.getContext();
        // If a run is already going on with this browser, return an error code
        if(context.hasRun(browserRequested)) {
            response.setStatus(400);
            return;
        }
        // Synchronize this block until we've added the run to our context for other potential threads to see
        int availableNodes = AutomationUtils.getNumFreeSlotsForBrowser(getRegistry(), browserRequested, os);
        response.setStatus(HttpServletResponse.SC_OK);
        // Add the browser so we know the nodes are occupied
        context.addRun(new AutomationRunRequest(browserRequested,availableNodes,browserRequested));
        InputStream in = new ByteArrayInputStream(String.valueOf(availableNodes).getBytes("UTF-8"));
        try {
            ByteStreams.copy(in, response.getOutputStream());
        } finally {
            in.close();
            response.flushBuffer();
        }
    }

}
