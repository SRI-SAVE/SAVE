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

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Some HTTP (Jetty) utilities, especially for use with JSON.
 */
public class HttpUtil {
    private static final Logger log = LoggerFactory.getLogger(HttpUtil.class);

    /**
     * Read the body of the request as a string suitable for parsing as a JSON
     * object. If an error occurs, send an HTTP error code to the client, along
     * with an explanatory message.
     *
     * @param request
     *            the HTTP request to read
     * @param response
     *            the HTTP response which may be used to send an error to the
     *            client
     * @param paramName
     *            If the request is of type application/x-www-form-urlencoded,
     *            the given paramName will be used as the name of the parameter
     *            to extract from the request body.
     * @return the requested part of the request body, or null if an error
     *         occurred
     * @throws IOException
     *             if the request can't be read, or if an error response can't
     *             be sent
     */
    public static String readJSON(HttpServletRequest request,
                                  HttpServletResponse response,
                                  String paramName)
            throws IOException {
        String body = "";

        // Get the content type.
        String cType = request.getContentType();
        if (cType.contains(";")) {
            int loc = cType.indexOf(';');
            cType = cType.substring(0, loc);
        }
        cType = cType.trim();

        if (cType.equals("application/json")) {
            // Read the body.
            try (BufferedReader in = request.getReader()) {
                while (true) {
                    String line = in.readLine();
                    if (line == null) {
                        break;
                    }
                    body += line;
                }
                body = body.trim();
            } catch (IOException e) {
                log.info("error reading request JSON", e);
                throw e;
            }
        } else if (cType.equals("application/x-www-form-urlencoded")) {
            body = request.getParameter(paramName);
            if (body == null) {
                log.info("Missing param ({}), found params ({})", paramName,
                        request.getParameterMap().keySet());
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Missing param '" + paramName + "'");
                return null;
            }
        } else {
            log.info("Unsupported content type ({})", cType);
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported content type '" + cType
                            + "'; must be 'application/json' or "
                            + "'application/x-www-form-urlencoded'");
            return null;
        }

        if (body.length() == 0) {
            log.info("Empty request body to {}", request.getServletPath());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Empty request body.");
            return null;
        }

        return body;
    }

    /**
     * Handle a CORS OPTIONS request.
     *
     * @param request
     *            the HTTP request
     * @param response
     *            the response
     * @param allow
     *            if true, allow the CORS request
     */
    public static void corsOptions(HttpServletRequest request,
                                   HttpServletResponse response,
                                   boolean allow) {
        String origin = request.getHeader("Origin");
        String method = request.getHeader("Access-Control-Request-Method");
        String headers = request.getHeader("Access-Control-Request-Headers");
        if (log.isDebugEnabled()) {
            log.debug("OPTIONS request url={} origin={} method={} headers={}",
                    new Object[] { request.getRequestURL(), origin, method,
                            headers });
        }

        if (allow) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Methods", method);
            if (headers != null) {
                response.setHeader("Access-Control-Allow-Headers", headers);
            }
            if (method.equals("PUT")) {
                response.setHeader("Access-Control-Allow-Credentials", "true");
            }
        }

        response.setStatus(HttpServletResponse.SC_OK);
    }
}
