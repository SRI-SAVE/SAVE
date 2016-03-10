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
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.sri.save.backend.repo.RepoScanner;

/**
 * Responds to requests for listings of files of a particular type. Unlike our
 * other handlers, this one reads GET parameters rather than JSON.
 */
public class RepoDirHandler
        extends AbstractHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final RepoScanner[] scanners;
    private final Gson gson;

    public RepoDirHandler(RepoScanner... scanners) {
        this.scanners = scanners;
        gson = new Gson();
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

        baseRequest.setHandled(true);

        // What are all the known file types we keep track of?
        Set<String> fileTypes = new HashSet<>();
        for (RepoScanner scanner : scanners) {
            fileTypes.addAll(scanner.getKnownFileTypes());
        }

        // Read the request.
        String reqPath = request.getPathInfo();
        String[] reqPaths = reqPath.split("/");
        if (reqPaths.length < 2) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "missing file type on path (try /listfiles/exercise)");
            return;
        }
        String fileType = reqPaths[1];
        if (!fileTypes.contains(fileType)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "unknown file type '" + fileType + "'; must be one of "
                            + fileTypes);
            return;
        }
        String outType = "html";
        if (reqPaths.length > 2) {
            outType = reqPaths[2];
        }
        if (!(outType.equals("html") || outType.equals("json"))) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "bad output type '" + outType + "': Must be html or json");
            return;
        }

        List<URL> urls = new ArrayList<>();
        String hostname = request.getServerName();
        for (RepoScanner scanner : scanners) {
            urls.addAll(scanner.listUrls(fileType, hostname));
        }
        Collections.sort(urls, new UrlComparator());
        log.debug("Sending in {}: {}", outType, urls);

        // Allow CORS from anywhere.
        response.setHeader("Access-Control-Allow-Origin", "*");

        response.setStatus(HttpServletResponse.SC_OK);

        try (PrintWriter out = response.getWriter()) {
            if (outType.equals("html")) {
                response.setContentType("text/html;charset=utf-8");
                sendHtml(out, urls);
            } else if (outType.equals("json")) {
                response.setContentType("application/json;charset=utf-8");
                sendJson(out, urls);
            } else {
                throw new RuntimeException("bad outType");
            }
        }
    }

    private void sendHtml(PrintWriter out,
                          List<URL> urls) {
        out.println("<html>");
        out.println("<head>");
        out.println("<title>file listing</title>");
        out.println("</head>");
        out.println("<body bgcolor=\"#ffffff\">");

        for (URL url : urls) {
            out.println("<br>");
            out.println("<a href=\"" + url + "\">" + url + "</a>");
        }

        out.println("</body>");
        out.println("</html>");
    }

    private void sendJson(PrintWriter out,
                          List<URL> urls) {
        List<String> strings = new ArrayList<>();
        for (URL url : urls) {
            strings.add(url.toString());
        }
        gson.toJson(strings, out);
    }

    private static class UrlComparator
            implements Comparator<URL> {
        @Override
        public int compare(URL arg0,
                           URL arg1) {
            return arg0.toString().compareTo(arg1.toString());
        }
    }
}
