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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.sri.save.backend.XApiRequestTracker;
import com.sri.save.backend.XApiRequestTracker.XApiStatement;

/**
 * Receives xAPI requests, stores them in the content repository, and sends back
 * responses when a student completes an exercise.
 */
public class XApiHandler
        extends AbstractHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Gson gson;
    private final XApiRequestTracker tracker;

    public XApiHandler(XApiRequestTracker tracker) {
        gson = new Gson();
        this.tracker = tracker;
    }

    @Override
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response)
            throws IOException,
            ServletException {
        response.setContentType("application/json;charset=utf-8");
        baseRequest.setHandled(true);

        String body = HttpUtil.readJSON(request, response, "xapi");
        if (body == null) {
            /* If null, there was a problem that's already been handled. */
            return;
        }

        XApiStatement stmt = gson.fromJson(body, XApiStatement.class);
        log.debug("Received xAPI: {}", stmt);

        tracker.add(stmt);

        response.setStatus(HttpServletResponse.SC_OK);
    }
}
