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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sri.save.backend.repo.RepoScanner;

/**
 * Supports writing files via HTTP PUT.
 */
public class RepositoryServlet
        extends DefaultServlet {
    private static final long serialVersionUID = 1L;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final RepoScanner scanner;
    private final Set<Pattern> readOnlyFiles;

    public RepositoryServlet(RepoScanner scanner) {
        this.scanner = scanner;
        readOnlyFiles = new HashSet<>();
        readOnlyFiles.add(Pattern.compile("/exercises/071-100-0032/step01/m4_flora_clear_exer\\.xml"));
        readOnlyFiles.add(Pattern.compile("/s3d/weapons/M4/M4\\.s3d"));
        readOnlyFiles.add(Pattern.compile("/s3d/weapons/M4/M4_starter\\.s3d"));
        readOnlyFiles.add(Pattern.compile("/s3d/environments/range/ShootingRange\\.s3d"));
        readOnlyFiles.add(Pattern.compile("/published/.*"));
    }

    @Override
    protected void doPut(HttpServletRequest req,
                         HttpServletResponse resp) {
        if (req.getMethod().equals("OPTIONS")) {
            if (req instanceof Request) {
                ((Request) req).setHandled(true);
            }
            HttpUtil.corsOptions(req, resp, true);
            return;
        }

        // Allow CORS from anywhere.
        String origin = req.getHeader("Origin");
        resp.setHeader("Access-Control-Allow-Origin", origin);

        /* We don't allow overwriting the blessed example files. */
        String path = req.getPathInfo();
        log.debug("writing {}", path);
        for(Pattern p : readOnlyFiles) {
            if (p.matcher(path).matches()) {
                try {
                    log.info("Denying write request to {}", path);
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN, path + " is an example file; it cannot be altered.");
                } catch (IOException e) {
                    log.info("Unable to send error to client", e);
                }
                return;
            }
        }

        try {
            innerDoPut(req, resp);
        } catch (IOException e) {
            log.info("Unable to put", e);
            try {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        e.getMessage());
            } catch (IOException e1) {
                log.info("Unable to send error to client", e);
            }
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
    }

    private void innerDoPut(HttpServletRequest req,
                            HttpServletResponse resp)
            throws IOException {
        File file = new File(req.getPathTranslated());
        File dir = file.getParentFile();
        if (!dir.isDirectory()) {
            if (!dir.mkdirs()) {
                throw new IOException("Unable to make directory " + dir
                        + " for HTTP path " + req.getPathTranslated());
            }
        }
        try (OutputStream os = new FileOutputStream(file);
                OutputStreamWriter out = new OutputStreamWriter(os);
                InputStream is = req.getInputStream();
                InputStreamReader in = new InputStreamReader(is)) {
            for (int ch = in.read(); ch != -1; ch = in.read()) {
                out.write(ch);
            }
        }

        log.debug("Scanning new file {}", file);
        scanner.scan(file);
    }
}
