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

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * TODO Just use this for all our handlers instead putting custom code in each
 * one. Then delete HttpUtil.corsOptions().
 */

/**
 * Adds CORS headers to HTTP responses.
 */
public class CorsFilter
        implements Filter {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void init(FilterConfig filterConfig)
            throws ServletException {
        log.debug("init");
    }

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException,
            ServletException {
        log.debug("req: {}", request);

        if (!(request instanceof HttpServletRequest)) {
            log.warn("Request {} ({}) not HttpServletRequest", request,
                    request.getClass());
            chain.doFilter(request, response);
            return;
        }
        if (!(response instanceof HttpServletResponse)) {
            log.warn("Response {} ({}) not HttpServletResponse", response,
                    response.getClass());
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (req.getMethod().equals("OPTIONS")) {
            HttpUtil.corsOptions(req, resp, true);
            log.debug("resp: {}", resp);
            return;
        }

        // Allow CORS from anywhere.
        String origin = req.getHeader("Origin");
        if (origin == null || origin.isEmpty()) {
            origin = "*";
        }
        resp.setHeader("Access-Control-Allow-Origin", origin);
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT");
        if (req.getMethod().equals("PUT")) {
            resp.setHeader("Access-Control-Allow-Headers", "Authorization");
            resp.setHeader("Access-Control-Allow-Credentials", "true");
        }
        log.debug("resp: {}", resp);

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
