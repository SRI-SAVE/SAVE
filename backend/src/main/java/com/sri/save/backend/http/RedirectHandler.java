/*
 * Copyright 2016 SRI International
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sri.save.backend.http;

import java.io.IOException;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * This dumb thing just redirects the client to a specified URL.
 */
public class RedirectHandler
        extends AbstractHandler {
    private final URL url;

    public RedirectHandler(URL url) {
        this.url = url;
    }

    @Override
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response)
            throws IOException,
            ServletException {
        if (request.getMethod().equals("OPTIONS")) {
            baseRequest.setHandled(true);
            HttpUtil.corsOptions(request, response, true);
            return;
        }

        if (request.getPathInfo() != null) {
            return;
        }

        baseRequest.setHandled(true);

        response.sendRedirect(url.toString());
    }
}
