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
import com.sri.floralib.ext.interprolog.FlrException;
import com.sri.floralib.parser.FloraParserException;
import com.sri.pal.ActionDef;
import com.sri.pal.ActionInvocation;
import com.sri.pal.ActionModel;
import com.sri.pal.ActionStreamEvent.Status;
import com.sri.pal.ListDef;
import com.sri.pal.PALException;
import com.sri.pal.SetDef;
import com.sri.pal.Struct;
import com.sri.pal.StructDef;
import com.sri.pal.TypeDef;
import com.sri.pal.TypeStorage.Subset;
import com.sri.pal.common.SimpleTypeName;
import com.sri.save.backend.FloraWrapper;
import com.sri.save.backend.S3DMap;
import com.sri.save.backend.S3DMap.S3DAsset;
import com.sri.save.backend.S3DMap.S3DHierGroup;

public class ObjectHandler
        extends AbstractHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final Gson gson = new Gson();

    private final S3DMap s3dMap;
    private final FloraWrapper flora;
    private final ActionModel actionModel;
    private final ExerciseBuilder exerciseBuilder;

    public ObjectHandler(S3DMap s3dMap,
                         FloraWrapper floraWrapper,
                         ActionModel actionModel,
                         ExerciseBuilder exerciseBuilder) {
        this.s3dMap = s3dMap;
        flora = floraWrapper;
        this.actionModel = actionModel;
        this.exerciseBuilder = exerciseBuilder;
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

        try {
            doHandle(target, baseRequest, request, response);
        } catch(Exception e) {
            log.info("Failure handling request " + request, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.println("Internal failure:");
                e.printStackTrace(out);
            }
        }
    }

    private void doHandle(String target,
                          Request baseRequest,
                          HttpServletRequest request,
                          HttpServletResponse response)
            throws IOException,
            FloraParserException,
            FlrException {
        response.setContentType("application/json;charset=utf-8");
        baseRequest.setHandled(true);

        String body = HttpUtil.readJSON(request, response, "object");
        if (body == null) {
            /* If null, there was a problem that's already been handled. */
            return;
        }

        /*
         * The request body should be a JSON object with a type field to
         * indicate the type of request.
         */
        Typed typed = gson.fromJson(body, Typed.class);
        if (typed != null && typed.type.equals("create")) {
            CreateRequest req = gson.fromJson(body, CreateRequest.class);
            List<CreateResponse> resp = doCreate(req);
            response.setStatus(HttpServletResponse.SC_OK);

            String respJson = gson.toJson(resp);
            try (PrintWriter out = response.getWriter()) {
                out.print(respJson);
            }
            log.debug("query: {}, response: {}", body, respJson);
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            log.info("Unknown type ({}) of request to {}", typed.type,
                    request.getServletPath());
            try (PrintWriter out = response.getWriter()) {
                out.println("Unknown type '" + typed.type + "'");
            }
        }
    }

    private List<CreateResponse> doCreate(CreateRequest req)
            throws IOException,
            FloraParserException,
            FlrException {
        List<CreateResponse> result = new ArrayList<>();

        if (req.auto) {
            // Create all the assets marked "auto" and return their data.
            for (S3DAsset asset : s3dMap.getAssets()) {
                if (asset.isAuto()) {
                    CreateRequest subReq = new CreateRequest();
                    subReq.ID = asset.getId();
                    result.addAll(doCreate(subReq));
                }
            }
            return result;
        }

        S3DAsset asset = s3dMap.getAssetById(req.ID);
        if (asset == null) {
            throw new NoSuchElementException("No asset with id " + req.ID);
        }
        if (exerciseBuilder != null) {
            exerciseBuilder.addAsset(asset);
        }
        String floraClass = asset.getFloraClass();
        String kbId = flora.create(floraClass);
        try {
            generateCreateAction(floraClass, kbId);
        } catch (Exception e) {
            log.info("Unable to send create action to DEFT", e);
        }
        s3dMap.addAssetByKbId(kbId, asset);
        String url = asset.getAssetUrl();
        S3DHierGroup grouping = asset.getGrouping();
        CreateResponse response = new CreateResponse(kbId, url, grouping);
        result.add(response);
        return result;
    }

    /**
     * Generate an action for the assessment engine that encapsulates the
     * created object and all of its components.
     */
    private void generateCreateAction(String parentClass,
                                      String kbId)
            throws PALException,
            IOException,
            FloraParserException,
            FlrException {
        /*
         * TODO SAVE-185 Look for any action named "Create$class" (where $class
         * is the Flora class name) and assume that's ours.
         */
        SimpleTypeName actionName = null;
        for(SimpleTypeName name : actionModel.listTypes(Subset.ACTION)) {
            if (name.getSimpleName().equals("Create" + parentClass)) {
                if (actionName != null) {
                    throw new PALException("Multiple 'Create' actions: "
                            + actionName.getFullName() + " and "
                            + name.getFullName());
                }
                actionName = name;
            }
        }
        if (actionName == null) {
            throw new PALException("No 'Create" + parentClass + "' action");
        }

        ActionDef actionDef = (ActionDef) actionModel.getType(actionName);

        StructDef structDef = (StructDef) actionDef.getParamType(1);
        Struct children = structDef.newInstance();
        for (int fn = 0; fn < structDef.size(); fn++) {
            TypeDef childType = structDef.getFieldType(fn);
            String childFloraClass = structDef.getFieldName(fn);
            List<String> childKbIds = flora.getComponentIds(kbId,
                    childFloraClass);
            if (childType instanceof SetDef) {
                Set<String> childKbIdSet = new HashSet<>();
                childKbIdSet.addAll(childKbIds);
                children.setValue(fn, childKbIdSet);
            } else if(childType instanceof ListDef) {
                children.setValue(fn, childKbIds);
            } else {
                if (childKbIds.size() > 1) {
                    throw new PALException("Need one Flora object of class "
                            + childFloraClass + " and child of " + kbId
                            + ", but got " + childKbIds.size() + ": "
                            + childKbIds);
                }
                String value;
                if (childKbIds.isEmpty()) {
                    log.info("No children of {} with Flora class {}", kbId,
                            childFloraClass);
                    value = null;
                } else {
                    value = childKbIds.iterator().next();
                }
                children.setValue(fn, value);
            }
        }

        ActionInvocation action = actionDef.invoke(null);
        action.setValue(0, kbId);
        action.setValue(1, children);

        action.setStatus(Status.ENDED);

        log.info("Synthesized create action: {}", action);
    }

    private static class CreateRequest {
        public String ID;
        public boolean auto;
    }

    @SuppressWarnings("unused")
    private static class CreateResponse {
        public String KbId;
        public String assetURL;
        public S3DHierGroup grouping;

        public CreateResponse(String kbid,
                              String assetUrl,
                              S3DHierGroup grouping) {
            KbId = kbid;
            this.assetURL = assetUrl;
            this.grouping = grouping;
        }
    }
}
