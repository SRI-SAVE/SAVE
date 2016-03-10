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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jetty.util.B64Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.sri.pal.Bridge;
import com.sri.save.backend.S3DMap.S3DHierGroup;

/**
 * Test the backend with a sample data file and a series of HTTP interactions
 * from a mock EUI client.
 */
public class SystemTest {
    private static final Logger log = LoggerFactory.getLogger(SystemTest.class);
    private final Gson gson = new Gson();
    private Thread saveThread;
    private boolean didMakeFakeVwf = false;

    /**
     * Start the SAVE backend in a separate thread.
     */
    @BeforeClass
    public void startMain()
            throws Exception {
        final Backend main = new Backend(new String[0]);
        main.start();

        saveThread = new Thread() {
            @Override
            public void run() {
                try {
                    main.waitForShutdown();
                } catch (Exception e) {
                    log.error("main failed", e);
                }
            }
        };
        saveThread.start();
    }

    /**
     * Shutting down the PAL Bridge should cause SAVE's main to exit.
     */
    @AfterClass
    public void stopPAL()
            throws Exception {
        Bridge bridge = Bridge.newInstance("shutdown system test");
        bridge.shutdown();
        saveThread.join();
    }

    @BeforeTest
    public void makeFakeVwf() {
        File repoDir = new File(Backend.REPO_PATH);
        File fakeVwf = new File(repoDir, "../../vwf");
        if (!fakeVwf.isDirectory()) {
            File behaviorDir = new File(fakeVwf, "public/SAVE/behavior");
            Assert.assertTrue(behaviorDir.mkdirs());
            didMakeFakeVwf  = true;
        }
    }

    @AfterTest
    public void removeFakeVwf() {
        File repoDir = new File(Backend.REPO_PATH);
        File fakeVwf = new File(repoDir, "../../vwf");
        if (didMakeFakeVwf) {
            deleteAll(fakeVwf);
        }
    }

    @Test
    public void systemTest()
            throws Exception {
        Assert.assertTrue(saveThread.isAlive());

        URL base = new URL("http://localhost:" + Backend.httpPort()
                + "/exercises/071-100-0032/step01/m4_flora_clear/");

        /* Query the inventory, which is the tool tray. */
        URL url = new URL(base, "inventory");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        TooltrayItem tti = null;
        try (InputStream is = conn.getInputStream();
                Reader in = new InputStreamReader(is)) {
            String cType = conn.getContentType();
            Assert.assertTrue(cType.startsWith("application/json"));

            Inventory inv = gson.fromJson(in, Inventory.class);
            Assert.assertEquals(inv.instructorMode, false);
            Assert.assertEquals(inv.tooltray.size(), 1);
            tti = inv.tooltray.get(0);
            Assert.assertNotNull(tti.ID);
            Assert.assertNotEquals(tti.ID, "");
            Assert.assertNotNull(tti);
        } catch (IOException e) {
            try (InputStream is = conn.getErrorStream();
                    StringWriter sw = new StringWriter()) {
                while (is.available() > 0) {
                    sw.append((char) is.read());
                }
                throw new IOException(sw.toString(), e);
            }
        }

        /* Instantiate an object. */
        url = new URL(base, "object");
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.addRequestProperty("Content-Type",
                "application/json;charset=utf-8");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream();
                Writer out = new OutputStreamWriter(os, "utf-8")) {
            CreateRequest request = new CreateRequest(tti.ID);
            gson.toJson(request, out);
        }
        String m4KbId;
        try (InputStream is = conn.getInputStream();
                Reader in = new InputStreamReader(is)) {
            String cType = conn.getContentType();
            Assert.assertTrue(cType.startsWith("application/json"));

//            for(int ch = in.read(); ch != -1; ch = in.read()) {
//                System.out.write(ch);
//            }

            CreateResponse[] crArr = gson.fromJson(in, CreateResponse[].class);
            Assert.assertEquals(crArr.length, 1);
            CreateResponse cr = crArr[0];
            Assert.assertNotNull(cr.assetURL);
            Assert.assertNotEquals(cr.assetURL, "");
            m4KbId = cr.KbId;
            Assert.assertNotNull(m4KbId);
            Assert.assertNotEquals(m4KbId, "");
            S3DHierGroup m4Grouping = cr.grouping;
            Assert.assertNotNull(m4Grouping);
            Assert.assertNotEquals(m4Grouping.toString(), "");
        } catch (IOException e) {
            try (InputStream is = conn.getErrorStream();
                    StringWriter sw = new StringWriter()) {
                while (is.available() > 0) {
                    sw.append((char) is.read());
                }
                throw new IOException(sw.toString(), e);
            }
        }

        /* Get component KB IDs. */
        url = new URL(base, "query");
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.addRequestProperty("Content-Type",
                "application/json;charset=utf-8");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream();
                Writer out = new OutputStreamWriter(os, "utf-8")) {
            KbIdRequest req = new KbIdRequest(m4KbId, "Selector_Lever",
                    "Buttstock Group");
            gson.toJson(req, out);
        }
        String selectorKbId;
        String buttstockGroupKbId;
        try (InputStream is = conn.getInputStream();
                Reader in = new InputStreamReader(is)) {
            String cType = conn.getContentType();
            Assert.assertTrue(cType.startsWith("application/json"));

            KbIdResponse resp = gson.fromJson(in, KbIdResponse.class);
            Assert.assertEquals(resp.KbIds.size(), 2);
            selectorKbId = resp.KbIds.get(0);
            Assert.assertNotNull(selectorKbId);
            Assert.assertNotEquals(selectorKbId, "");
            buttstockGroupKbId = resp.KbIds.get(1);
            Assert.assertNotNull(buttstockGroupKbId);
            Assert.assertNotEquals(buttstockGroupKbId, "");
            Assert.assertNotEquals(selectorKbId, buttstockGroupKbId);
        } catch (IOException e) {
            try (InputStream is = conn.getErrorStream();
                    StringWriter sw = new StringWriter()) {
                while (is.available() > 0) {
                    sw.append((char) is.read());
                }
                throw new IOException(sw.toString(), e);
            }
        }

        /* Get applicable actions. */
        url = new URL(base, "query");
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.addRequestProperty("Content-Type",
                "application/json;charset=utf-8");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream();
                Writer out = new OutputStreamWriter(os, "utf-8")) {
            AllActionsRequest req = new AllActionsRequest(selectorKbId,
                    buttstockGroupKbId);
            gson.toJson(req, out);
        }
        Set<String> selectorActions;
        Set<String> bsgActions;
        try (InputStream is = conn.getInputStream();
                Reader in = new InputStreamReader(is)) {
            String cType = conn.getContentType();
            Assert.assertTrue(cType.startsWith("application/json"));

            AllActionsResponse resp = gson.fromJson(in,
                    AllActionsResponse.class);
            Assert.assertNotNull(resp.allActions);
            Assert.assertEquals(resp.allActions.size(), 2);
            selectorActions = resp.allActions.get(0);
            Set<String> expectedSelectorActions = new HashSet<>();
            expectedSelectorActions.add("Attach");
            expectedSelectorActions.add("Close");
            expectedSelectorActions.add("Detach");
            expectedSelectorActions.add("Extract");
            expectedSelectorActions.add("Insert");
            expectedSelectorActions.add("Inspect");
            expectedSelectorActions.add("Lift");
            expectedSelectorActions.add("Open");
            expectedSelectorActions.add("Point");
            expectedSelectorActions.add("Press");
            expectedSelectorActions.add("Pull");
            expectedSelectorActions.add("PullAndHold");
            expectedSelectorActions.add("Push");
            expectedSelectorActions.add("PushAndHold");
            expectedSelectorActions.add("Release");
            expectedSelectorActions.add("SelectSwitchPosition");
            Assert.assertTrue(expectedSelectorActions.equals(selectorActions),
                    selectorActions.toString());

            bsgActions = resp.allActions.get(1);
            Set<String> expectedBSGActions = new HashSet<>();
            expectedBSGActions.add("Attach");
            expectedBSGActions.add("Close");
            expectedBSGActions.add("Detach");
            expectedBSGActions.add("Extract");
            expectedBSGActions.add("Insert");
            expectedBSGActions.add("Inspect");
            expectedBSGActions.add("Lift");
            expectedBSGActions.add("Open");
            expectedBSGActions.add("Point");
            expectedBSGActions.add("Press");
            expectedBSGActions.add("Pull");
            expectedBSGActions.add("PullAndHold");
            expectedBSGActions.add("Push");
            expectedBSGActions.add("PushAndHold");
            expectedBSGActions.add("Release");
            Assert.assertTrue(expectedBSGActions.equals(bsgActions),
                    bsgActions.toString());
        } catch (IOException e) {
            try (InputStream is = conn.getErrorStream();
                    StringWriter sw = new StringWriter()) {
                while (is.available() > 0) {
                    sw.append((char) is.read());
                }
                throw new IOException(sw.toString(), e);
            }
        }
    }

    @Test
    public void httpPut()
            throws Exception {
        Assert.assertTrue(saveThread.isAlive());

        String content = "This is a text file.\nIt originated remotely.\n";

        URL base = new URL("http://localhost:" + Backend.httpPort() + "/test/");
        File repoDir = new File(Backend.REPO_PATH);
        File testDir = new File(repoDir, "test");
        try {
            URL url = new URL(base, "file1.txt");
            HttpURLConnection httpCon = (HttpURLConnection) url
                    .openConnection();
            String userpass = Backend.PUT_USER + ":" + Backend.PUT_PASSWORD;
            String auth = B64Code.encode(userpass);
            httpCon.setRequestProperty("Authorization", "Basic " + auth);
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("PUT");
            try (OutputStream os = httpCon.getOutputStream();
                    OutputStreamWriter osw = new OutputStreamWriter(os, "utf-8");
                    PrintWriter out = new PrintWriter(osw)) {
                out.print(content);
            }
            String response;
            try (InputStream is = httpCon.getInputStream();
                    InputStreamReader in = new InputStreamReader(is, "utf-8")) {
                StringBuilder sb = new StringBuilder();
                for (int ch = in.read(); ch != -1; ch = in.read()) {
                    sb.append(ch);
                }
                response = sb.toString();
            } catch (IOException e) {
                try (InputStream is = httpCon.getErrorStream();
                        StringWriter sw = new StringWriter()) {
                    while (is.available() > 0) {
                        sw.append((char) is.read());
                    }
                    throw new IOException(sw.toString(), e);
                }
            }
            int code = httpCon.getResponseCode();
            Assert.assertEquals(response, "");
            Assert.assertEquals(code, 200);

            // Now read the same file.
            httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(false);
            httpCon.setRequestMethod("GET");
            try (InputStream is = httpCon.getInputStream();
                    InputStreamReader in = new InputStreamReader(is, "utf-8")) {
                StringBuilder sb = new StringBuilder();
                for (int ch = in.read(); ch != -1; ch = in.read()) {
                    sb.append((char) ch);
                }
                response = sb.toString();
            } catch (IOException e) {
                try (InputStream is = httpCon.getErrorStream();
                        StringWriter sw = new StringWriter()) {
                    while (is.available() > 0) {
                        sw.append((char) is.read());
                    }
                    throw new IOException(sw.toString(), e);
                }
            }
            code = httpCon.getResponseCode();
            Assert.assertEquals(code, 200);
            Assert.assertEquals(response, content);
        } finally {
            deleteAll(testDir);
        }
    }

    /**
     * Test RepoDirHandler. It provides listings of files of various types.
     */
    @Test
    public void fileListing()
            throws Exception {
        Assert.assertTrue(saveThread.isAlive());

        URL baseUrl = new URL("http://"
                + InetAddress.getLocalHost().getHostName() + ":"
                + Backend.httpPort());
        URL listUrl = new URL(baseUrl, "/listfiles/");

        URL query1 = new URL(listUrl, "exercise/json");
        String response = getUrl(query1);
        String[] urls = gson.fromJson(response, String[].class);
        Set<String> urlSet = new HashSet<>();
        urlSet.addAll(Arrays.asList(urls));
        Assert.assertTrue(!urlSet.isEmpty());
        URL exerUrl = new URL(baseUrl,
                "/exercises/071-100-0032/step01/m4_flora_clear");
        Assert.assertTrue(urlSet.contains(exerUrl.toString()),
                exerUrl.toString());

        URL query2 = new URL(listUrl, "s3d/json");
        response = getUrl(query2);
        urls = gson.fromJson(response, String[].class);
        urlSet.clear();
        urlSet.addAll(Arrays.asList(urls));
        Assert.assertTrue(!urlSet.isEmpty());
        URL s3dUrl = new URL(baseUrl, "/s3d/weapons/M4/M4.s3d");
        Assert.assertTrue(urlSet.contains(s3dUrl.toString()), s3dUrl.toString());

        /* This has been commented out because it depends on files in the VWF repository. */
//        URL query3 = new URL(listUrl, "collada/json");
//        response = getUrl(query3);
//        urls = gson.fromJson(response, String[].class);
//        urlSet.clear();
//        urlSet.addAll(Arrays.asList(urls));
//        Assert.assertTrue(!urlSet.isEmpty());
//        URL colladaUrl = new URL(baseUrl, "/models/weapons/M4/M4.dae");
//        Assert.assertTrue(urlSet.contains(colladaUrl.toString()),
//                colladaUrl.toString());

        URL query4 = new URL(listUrl, "flora/json");
        response = getUrl(query4);
        urls = gson.fromJson(response, String[].class);
        urlSet.clear();
        urlSet.addAll(Arrays.asList(urls));
        Assert.assertTrue(!urlSet.isEmpty());
        URL floraUrl = new URL(baseUrl, "/knowledge/weapons/M4/m4.flr");
        Assert.assertTrue(urlSet.contains(floraUrl.toString()),
                floraUrl.toString());
    }

    private String getUrl(URL url)
            throws IOException {
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        String response;
        try (InputStream is = httpCon.getInputStream();
                InputStreamReader in = new InputStreamReader(is, "utf-8")) {
            StringBuilder sb = new StringBuilder();
            for (int ch = in.read(); ch != -1; ch = in.read()) {
                sb.append((char) ch);
            }
            response = sb.toString();
        } catch (IOException e) {
            try (InputStream is = httpCon.getErrorStream();
                    StringWriter sw = new StringWriter()) {
                while (is.available() > 0) {
                    sw.write(is.read());
                }
                throw new IOException(sw.toString(), e);
            }
        }
        int code = httpCon.getResponseCode();
        Assert.assertEquals(code, 200);

        log.info("Query to '{}' received: {}", url, response);

        return response;
    }

    public static void deleteAll(File f) {
        if (f.isDirectory()) {
            for (File child : f.listFiles()) {
                deleteAll(child);
            }
        }
        if (!f.delete()) {
            log.warn("Unable to delete {}", f);
        }
    }

    private static class Inventory {
        public boolean instructorMode;
        public List<TooltrayItem> tooltray;
    }

    private static class TooltrayItem {
        @SuppressWarnings("unused")
        public String name;
        public String ID;
    }

    @SuppressWarnings("unused")
    private static class CreateRequest {
        public String type;
        public String ID;

        public CreateRequest(String id) {
            type = "create";
            ID = id;
        }
    }

    private static class CreateResponse {
        public String KbId;
        public String assetURL;
        public S3DHierGroup grouping;
    }

    @SuppressWarnings("unused")
    private static class KbIdRequest {
        public String type = "KbId";
        public String parent;
        public List<String> query;

        public KbIdRequest(String parent, String... components) {
            this.parent = parent;
            query = Arrays.asList(components);
        }
    }

    private static class KbIdResponse {
        public List<String> KbIds;
    }

    @SuppressWarnings("unused")
    private static class AllActionsRequest {
        public String type = "AllActions";
        public List<String> kbIds;

        public AllActionsRequest(String... kbIds) {
            this.kbIds = Arrays.asList(kbIds);
        }
    }

    private static class AllActionsResponse {
        public List<Set<String>> allActions;
    }
}
