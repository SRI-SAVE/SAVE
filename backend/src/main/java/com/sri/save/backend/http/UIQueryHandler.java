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
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sri.floralib.ext.interprolog.FlrException;
import com.sri.save.backend.ExerciseHandler;
import com.sri.save.backend.FloraWrapper;
import com.sri.save.backend.S3DMap;
import com.sri.save.backend.S3DMap.S3DAsset;
import com.sri.save.backend.S3DMap.S3DComponent;

/**
 * Services requests for information from the UI.
 */
public class UIQueryHandler
        extends AbstractHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Gson gson;
    private final S3DMap s3dMap;
    private final FloraWrapper flora;
    private final ExerciseHandler exerHand;

    public UIQueryHandler(ExerciseHandler exerciseHandler,
                          S3DMap s3dMap,
                          FloraWrapper flora) {
        exerHand = exerciseHandler;
        this.s3dMap = s3dMap;
        this.flora = flora;
        gson = new GsonBuilder().disableHtmlEscaping().create();
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

        response.setContentType("application/json;charset=utf-8");
        baseRequest.setHandled(true);

        String body = HttpUtil.readJSON(request, response, "query");
        if (body == null) {
            /* If null, there was a problem that's already been handled. */
            return;
        }

        /*
         * The request body should be a JSON object with a type field to
         * indicate the type of request.
         */
        Typed typed = gson.fromJson(body, Typed.class);
        if (typed != null && typed.type.equals("KbId")) {
            KbIdQuery kbq = gson.fromJson(body, KbIdQuery.class);
            KbIdResponse kbr = kbIdQuery(kbq);
            response.setStatus(HttpServletResponse.SC_OK);

            // Allow CORS from anywhere.
            response.setHeader("Access-Control-Allow-Origin", "*");

            String respJson = gson.toJson(kbr);
            try (PrintWriter out = response.getWriter()) {
                out.print(respJson);
            }
            log.debug("query: {}, response: {}", body, respJson);
        } else if (typed != null && typed.type.equals("AllActions")) {
            AllActionsQuery aaq = gson.fromJson(body, AllActionsQuery.class);
            AllActionsResponse aar = allActionsQuery(aaq);
            response.setStatus(HttpServletResponse.SC_OK);

            // Allow CORS from anywhere.
            response.setHeader("Access-Control-Allow-Origin", "*");

            String respJson = gson.toJson(aar);
            try (PrintWriter out = response.getWriter()) {
                out.print(respJson);
            }
            log.debug("AllActions query: {}, response: {}", body, respJson);
        } else if (typed != null && typed.type.equals("Reset")) {
            response.setContentType("text/html");

            // Allow CORS from anywhere.
            response.setHeader("Access-Control-Allow-Origin", "*");

            try {
                exerHand.reset();
                response.setStatus(HttpServletResponse.SC_OK);
                log.info("State reset");
            } catch (FlrException e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                log.info("Flora reset failure", e);
                try (PrintWriter out = response.getWriter()) {
                    out.println("Flora reset failure");
                }
            }
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            log.info("Unknown type ({}) of request to {}", typed.type,
                    request.getServletPath());
            try (PrintWriter out = response.getWriter()) {
                out.println("Unknown type '" + typed.type + "'");
            }
        }
    }

    private KbIdResponse kbIdQuery(KbIdQuery kbq) {
        KbIdResponse response = new KbIdResponse();
        response.KbIds = new ArrayList<>();
        for (String s3dType : kbq.query) {
            response.KbIds.add(getComponentKbId(kbq.parent, s3dType));
        }
        return response;
    }

    private AllActionsResponse allActionsQuery(AllActionsQuery aaq) {
        AllActionsResponse aar = new AllActionsResponse();
        aar.allActions = new ArrayList<>();
        for (String kbid : aaq.kbIds) {
            Set<String> actions = new HashSet<>();
            try {
                actions = flora.allActions(kbid);
            } catch (Exception e) {
                log.info("Unable to get actions for KB ID {}", kbid);
            }
            aar.allActions.add(actions);
        }
        return aar;
    }

    private String getComponentKbId(String parentKbId,
                                    String nodeName) {
        S3DAsset asset = s3dMap.getAssetByKbId(parentKbId);
        if (asset == null) {
            log.info("Unable to find asset with KB ID {}", parentKbId);
            return null;
        }
        S3DComponent mapping = asset.getObject(nodeName);
        if (mapping == null) {
            log.debug("Unable to find KB class for 3D node ({}) in {}", nodeName,
                    asset);
            return null;
        }
        String ontType = mapping.getFloraClass();
        try {
            List<String> childKbIds = flora
                    .getComponentIds(parentKbId, ontType);
            if (childKbIds.isEmpty()) {
                throw new NoSuchElementException();
            }

            // TODO: This line is buggy; see SAVE-164.
            return childKbIds.get(0);
        } catch (Exception e) {
            log.warn("Unable to get child of '" + parentKbId + "' with type '"
                    + ontType + "'", e);
            return null;
        }
    }

    private static class KbIdQuery {
        public String parent;
        public List<String> query;
    }

    private static class KbIdResponse {
        public List<String> KbIds;
    }

    private static class AllActionsQuery {
        public List<String> kbIds;
    }

    private static class AllActionsResponse {
        public List<Set<String>> allActions;
    }
}
