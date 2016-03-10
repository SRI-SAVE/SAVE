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
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.sri.save.backend.S3DMap;
import com.sri.save.backend.S3DMap.S3DAsset;

/**
 * Lists the inventory available to the EUI, meaning what's in the tool tray.
 * This is determined by examining the contents of the S3D files referenced for
 * this exercise.
 */
public class InventoryHandler
        extends AbstractHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Gson gson;
    private final S3DMap s3dMap;
    private final boolean hasSolution;

    public InventoryHandler(S3DMap s3dMap,
                            boolean hasSolution) {
        this.s3dMap = s3dMap;
        this.hasSolution = hasSolution;
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
        // Allow CORS from anywhere.
        response.setHeader("Access-Control-Allow-Origin", "*");

        response.setContentType("application/json;charset=utf-8");
        baseRequest.setHandled(true);

        // Not reading the body; we respond to an empty GET.

        // Build the response.
        Inventory inv = new Inventory(!hasSolution);
        for (S3DAsset asset : s3dMap.getAssets()) {
            if (asset.isAuto()) {
                continue;
            }
            inv.addItem(asset.getName(), asset.getId());
        }

        response.setStatus(HttpServletResponse.SC_OK);

        String respJson = gson.toJson(inv);
        try (PrintWriter out = response.getWriter()) {
            out.print(respJson);
        }
        log.debug("response: {}", respJson);
    }

    private static class Inventory {
        @SuppressWarnings("unused")
        private boolean instructorMode;
        private List<TooltrayItem> tooltray;

        public Inventory(boolean instructorMode) {
            this.instructorMode = instructorMode;
            tooltray = new ArrayList<>();
        }

        public void addItem(String name,
                            String id) {
            TooltrayItem tti = new TooltrayItem(name, id);
            tooltray.add(tti);
        }
    }

    @SuppressWarnings("unused")
    private static class TooltrayItem {
        private String name;
        private String ID;

        public TooltrayItem(String name,
                            String id) {
            this.name = name;
            this.ID = id;
        }
    }
}
