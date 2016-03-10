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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.sri.floralib.ast.FloraTerm;
import com.sri.floralib.ext.interprolog.FlrException;
import com.sri.floralib.parser.FloraParserException;
import com.sri.floralib.reasoner.QueryResult;
import com.sri.floralib.reasoner.Solution;
import com.sri.pal.ActionDef;
import com.sri.pal.ActionInvocation;
import com.sri.pal.ActionStreamEvent.Status;
import com.sri.pal.PALException;
import com.sri.save.backend.FloraWrapper;
import com.sri.save.backend.JsonAction;

/**
 * Handles the UI end of the DEFT/UI interactions. The UI communicates by HTTP,
 * so this is an HTTP server. The UI has to check in with us every so often, so
 * when we get a callback request from DEFT, we queue it up and deliver it the
 * next time we hear from the UI.
 */
public class ActionHandler
        extends AbstractHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Gson gson;
    private final Map<String, ActionDef> actions;
    private final FloraWrapper flora;
    private final HttpExecutor uiExec;
    private ActionInvocation runningCallback;

    public ActionHandler(Set<ActionDef> actions,
                         FloraWrapper flora,
                         HttpExecutor uiExec) {
        gson = new Gson();
        this.flora = flora;
        this.uiExec = uiExec;
        this.actions = new HashMap<>();
        for (ActionDef actDef : actions) {
            this.actions.put(actDef.getName().getSimpleName(), actDef);
        }
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

        String body = HttpUtil.readJSON(request, response, "activity");
        if (body == null) {
            /* If null, there was a problem that's already been handled. */
            return;
        }

        // If there's a body, it's a JSON action that the UI just finished.
        if (body.length() != 0) {
            JsonAction act = gson.fromJson(body, JsonAction.class);
            log.info("UI action: {}", act);
            // If we're waiting for a callback response, update that.
            if (runningCallback != null) {
                act.updateDeft(runningCallback);
                runningCallback.setStatus(Status.ENDED);
                runningCallback = null;
            } else {
                // Otherwise make a new DEFT action.
                ActionDef actDef = actions.get(act.action);
                try {
                    ActionInvocation ai = act.toDeft(actDef);
                    ai.setStatus(Status.ENDED);
                } catch (PALException e) {
                    response.sendError(
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            "Unable to convert " + act.action + ": "
                                    + e.getMessage());
                    log.info("Unable to convert " + act + " to DEFT", e);
                    return;
                }

                try {
                    notifyFlora(act);
                } catch (Exception e) {
                    log.info("Flora error reporting action " + act, e);
                }
            }
        }

        response.setStatus(HttpServletResponse.SC_OK);

        // Allow CORS from anywhere.
        response.setHeader("Access-Control-Allow-Origin", "*");

        try (PrintWriter out = response.getWriter()) {
            // Check to see if there are any callbacks waiting to go out.
            runningCallback = uiExec.poll();
            if (runningCallback != null) {
                JsonAction jsact = new JsonAction(runningCallback);
                out.print(gson.toJson(jsact));
            } else {
                out.println("{}");
            }
        }
    }

    private void notifyFlora(JsonAction act)
            throws FlrException,
            IOException,
            FloraParserException {
        // First verify that it's an action in Flora's vocabulary.
        String action = actionNameToFlora(act.action) + "("
                + joinArgs(act.arguments) + ")";
        String query1 = action + ": Action";
        QueryResult result1 = flora.query(query1);
        if (!result1.isEmptySuccess()) {
            log.info("Unknown action in Flora: {}", action);
            throw new FlrException("Unknown Flora action " + act.action);
        }

        String query2 = "%do(" + action + ", ?del, ?add)";
        log.info("Report action to Flora: {}", act);
        QueryResult result2 = flora.query(query2);
        if (result2.isError()) {
            throw new FlrException("Flora error: " + result2.getError());
        } else {
            List<Solution> sols = result2.getSolutions();
            for (Solution sol : sols) {
                FloraTerm del = sol.getBinding("?del");
                FloraTerm add = sol.getBinding("?add");
                log.info("... del = {}, add = {}", del, add);
            }
        }
    }

    /**
     * Convert "PushAndHold" to "push_and_hold".
     */
    private static String actionNameToFlora(String actName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actName.length(); i++) {
            char ch = actName.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (sb.length() == 0) {
                    sb.append(Character.toLowerCase(ch));
                } else {
                    sb.append("_");
                    sb.append(Character.toLowerCase(ch));
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String joinArgs(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length - 1; i++) {
            sb.append(args[i]);
            sb.append(", ");
        }
        sb.append(args[args.length - 1]);
        return sb.toString();
    }
}
