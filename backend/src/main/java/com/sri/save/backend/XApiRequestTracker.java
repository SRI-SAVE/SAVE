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
package com.sri.save.backend;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.UUID;

import org.eclipse.jetty.util.B64Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class XApiRequestTracker {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final String PERLS_AUTH_USER = "sri_save";
    private static final String PERLS_AUTH_PASS = "perls&save";
    private final URL PERLS_URL;
    private final URL PERLS_SAVE_URL;
    private final URI COMPLETED;
    private final URI EXERCISE_KEY;
    private final Gson gson;
    private final Map<UUID, XApiStatement> pendingStatements;
    private final File persistFile;

    public XApiRequestTracker(File repoRoot)
            throws URISyntaxException,
            IOException {
        PERLS_URL = new URL("http://perls.sri.com:8000/perls-test/xAPI/statements");
        PERLS_SAVE_URL = new URL(
                "http://perls.sri.com:8000/perls-test/secure/users/sri_save");
        COMPLETED = new URI("http://adlnet.gov/expapi/verbs/completed");
        EXERCISE_KEY = new URI(
                "http://semantic3d.com:3000/profiles/exercises/extensions/saveexercise");
        gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
                .create();
        persistFile = new File(repoRoot, "PERLS_SAVE.json");
        pendingStatements = read();
    }

    public void add(XApiStatement stmt)
            throws IOException {
        if (stmt.id == null) {
            throw new IOException("No statement ID");
        }
        synchronized (pendingStatements) {
            if (!pendingStatements.containsKey(stmt.id)) {
                pendingStatements.put(stmt.id, stmt);
                write();
            }
        }
    }

    /**
     * Notify PERLS, if it's registered an interest in this exercise, that the
     * exercise has been completed.
     *
     * @param exercise
     *            the full URL of the exercise, including session suffix
     *            (http://semantic3d.com:3000/CAT/1S2wSVQ3WFFXNwSy/)
     * @param passed
     *            Did the student pass the exercise?
     */
    public void exerciseFinished(URL exercise,
                                 boolean passed)
            throws IOException {
        /*
         * Check if we have a pending request for this exercise. If so, build a
         * new xAPI statement about the exercise completion, send it to PERLS,
         * and remove the pending request from storage.
         */
        log.debug("Finished (success={}) exercise at {}", passed, exercise);

        // Remove the session suffix.
        String exerStr = exercise.toString();
        if (exerStr.endsWith("/")) {
            exerStr = exerStr.substring(0, exerStr.length() - 1);
        }
        exerStr = exerStr.substring(0, exerStr.lastIndexOf('/'));
        exerStr += "/";
        URL exerciseProto = new URL(exerStr);
        log.debug("Cleaned up exercise URL {}", exerciseProto);

        synchronized (pendingStatements) {
            Iterator<Map.Entry<UUID, XApiStatement>> it = pendingStatements
                    .entrySet().iterator();
            while (it.hasNext()) {
                Entry<UUID, XApiStatement> entry = it.next();
                UUID id = entry.getKey();
                XApiStatement stmt = entry.getValue();
                Map<URI, Object> ext = stmt.object.definition.extensions;
                Object reqUrl = ext.get(EXERCISE_KEY);
                log.debug("Checking request ID {}...", id);
                if (exerciseProto.toString().equals(reqUrl)) {
                    // The completed exercise matches this exercise request.
                    log.debug("matched.");
                    it.remove();
                    sendResponse(stmt, passed);
                    write();
                }
            }
        }
    }

    /**
     * Send a response to PERLS saying the user finished the exercise.
     */
    private void sendResponse(XApiStatement req,
                              boolean passed)
            throws IOException {
        XApiStatement resp = new XApiStatement();
        resp.id = UUID.randomUUID();
        resp.actor = req.actor;
        resp.verb = new XApiVerb();
        resp.verb.id = COMPLETED;
        resp.verb.display = new HashMap<>();
        resp.verb.display.put("en-US", "completed");
        resp.object = req.object;
        resp.result = new XApiResult();
        resp.result.success = passed;
        resp.result.completion = true;
        resp.context = new XApiContext();
        resp.context.registration = req.id;
        resp.context.platform = "SAVE";
        resp.authority = new XApiAuthority();
        resp.authority.objectType = "Agent";
        resp.authority.account = new XApiAccount();
        resp.authority.account.homePage = PERLS_SAVE_URL;
        resp.authority.account.name = "sri_save";
        Date now = new Date();
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        df.setTimeZone(tz);
        resp.timestamp = df.format(now);

        // Send it.
        if (log.isDebugEnabled()) {
            log.debug("Sending to {}: {}", PERLS_URL, gson.toJson(resp));
        }
        HttpURLConnection conn = (HttpURLConnection) PERLS_URL.openConnection();
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setRequestProperty("X-Experience-API-Version", "1.0");
        conn.setRequestProperty("Content-Type", "application/json");
        String userpass = PERLS_AUTH_USER + ":" + PERLS_AUTH_PASS;
        String auth = B64Code.encode(userpass);
        conn.setRequestProperty("Authorization", "Basic " + auth);
        conn.setRequestMethod("POST");
        try (OutputStream os = conn.getOutputStream();
                OutputStreamWriter osw = new OutputStreamWriter(os, "utf-8");
                PrintWriter out = new PrintWriter(osw)) {
            gson.toJson(resp, out);
        }
        String response;
        try (InputStream is = conn.getInputStream();
                InputStreamReader in = new InputStreamReader(is, "utf-8")) {
            StringBuilder sb = new StringBuilder();
            for (int ch = in.read(); ch != -1; ch = in.read()) {
                sb.append(ch);
            }
            response = sb.toString();
        }
        int code = conn.getResponseCode();
        if (code != 200) {
            log.info("PERLS rejected our data:\n{}", response);
        }
    }

    /**
     * Read pendingStatements from disk.
     */
    private Map<UUID, XApiStatement> read()
            throws FileNotFoundException,
            IOException {
        if (!persistFile.exists()) {
            return new HashMap<>();
        }

        Type listType = new TypeToken<List<XApiStatement>>() {
        }.getType();
        try (FileReader in = new FileReader(persistFile)) {
            Map<UUID, XApiStatement> result = new HashMap<>();
            List<XApiStatement> stmts = gson.fromJson(in, listType);
            for (XApiStatement stmt : stmts) {
                result.put(stmt.id, stmt);
            }
            return result;
        }
    }

    /**
     * Write the contents of pendingStatements to disk.
     */
    private void write()
            throws IOException {
        try (FileWriter out = new FileWriter(persistFile)) {
            List<XApiStatement> stmts = new ArrayList<>();
            stmts.addAll(pendingStatements.values());
            gson.toJson(stmts, out);
        }
    }

    /**
     * https://github.com/adlnet/xAPI-Spec/blob/master/xAPI.md
     */
    @SuppressWarnings("unused")
    public static class XApiStatement {
        private UUID id;
        private XApiActor actor;
        private XApiVerb verb;
        private XApiObject object;
        private XApiResult result;
        private XApiContext context;
        private XApiAuthority authority;
        private String timestamp;
    }

    @SuppressWarnings("unused")
    public static class XApiActor {
        private String objectType;
        private String name;
        private List<XApiActor> member;
        private URI mbox;
        private String mbox_sha1sum;
        private URI openid;
        private XApiAccount account;
    }

    @SuppressWarnings("unused")
    public static class XApiAccount {
        private URL homePage;
        private String name;
    }

    @SuppressWarnings("unused")
    public static class XApiVerb {
        private URI id;
        private Map<String, String> display;
    }

    @SuppressWarnings("unused")
    public static class XApiObject {
        private String objectType;
        private URI id;
        private XApiDefinition definition;

        // From XApiActor:
        private String name;
        private List<XApiActor> member;
        private URI mbox;
        private String mbox_sha1sum;
        private URI openid;
        private XApiAccount account;

        // If sub-statement:
        private XApiActor actor;
        private XApiVerb verb;
        private XApiObject object;
    }

    @SuppressWarnings("unused")
    public static class XApiDefinition {
        private Map<String, String> name;
        private Map<String, String> description;
        private URI type;
        private URL moreInfo;
        private String interactionType;
        private List<String> correctResponsesPattern;
        private List<XApiComponents> choices;
        private List<XApiComponents> scale;
        private List<XApiComponents> source;
        private List<XApiComponents> target;
        private List<XApiComponents> steps;
        private Map<URI, Object> extensions;
    }

    @SuppressWarnings("unused")
    public static class XApiComponents {
        private String id;
        private Map<String, String> description;
    }

    @SuppressWarnings("unused")
    public static class XApiResult {
        private XApiScore score;
        private Boolean success;
        private Boolean completion;
        private String response;
        private String duration;
        private Map<URI, Object> extensions;
    }

    @SuppressWarnings("unused")
    public static class XApiScore {
        private Double scaled;
        private Double raw;
        private Double min;
        private Double max;
    }

    @SuppressWarnings("unused")
    public static class XApiContext {
        private UUID registration;
        private XApiActor instructor;
        private XApiActor team;
        private Map<String, XApiObject> contextActivities;
        private String revision;
        private String platform;
        private String language;
        private XApiStatement statement;
        private Map<URI, Object> extensions;
    }

    @SuppressWarnings("unused")
    public static class XApiAuthority {
        // included Agent fields:
        private String objectType;
        private String name;
        private URI mbox;
        private String mbox_sha1sum;
        private URI openid;
        private XApiAccount account;
    }
}
