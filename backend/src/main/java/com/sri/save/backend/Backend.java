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

import com.sri.floralib.ext.interprolog.FlrException;
import com.sri.pal.*;
import com.sri.pal.common.SimpleTypeName;
import com.sri.save.backend.http.*;
import com.sri.save.backend.repo.*;
import com.sri.save.florahttp.FloraHttpServlet;
import com.sri.tasklearning.util.LogUtil;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.servlet.DispatcherType;
import javax.xml.XMLConstants;
import javax.xml.bind.*;
import javax.xml.bind.util.ValidationEventCollector;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Properties;

/**
 * This is the gateway which handles communication between DEFT and the UI.
 * Typically it's run after the DEFT Student UI is running and listening for
 * events. Actions are received from the UI and translated into DEFT actions.
 * Also callback requests are received from DEFT and sent to the UI.
 * <p>
 * If files are specified on the command line, they're read and translated from
 * JSON into DEFT before being sent off to the DEFT Student UI.
 */
public class Backend {
    private static final Logger log = LoggerFactory.getLogger(Backend.class);

    /**
     * Directory which contains our static content repositories.
     */
    private static final String REPO_KEY = "repos";
    static final String REPO_PATH = "../repos";
    /**
     * Top level directory of the VWF tree.
     */
    private static final String VWF_KEY = "vwf";
    private static final String VWF_PATH = "../../vwf";
    /**
     * Location of the XSB executable.
     */
    static final String XSB_KEY = "xsb";
    private static final String XSB_PATH = "../../xsb-src/XSB/config/x64-pc-windows/bin/xsb.exe";
    /**
     * Top directory of the Flora2 tree.
     */
    static final String FLORA_KEY = "flora2";
    private static final String FLORA_PATH = "../../flora2";
    /**
     * Action model version. Corresponds to the version tag in the action model
     * XML file.
     */
    private static final String VERSION = "0.1";
    /**
     * Action model namespace. Indicates what application is running the
     * actions.
     */
    private static final String NAMESPACE = "gateway";
    /**
     * We listen on this port for HTTP requests from the Exercise UI.
     */
    private static final int DEFAULT_HTTP_PORT = 3001;
    private static final String PORT_PROP = "SAVE.HTTPport";

    /**
     * The port that VWF listens on. Should be listening on the same machine.
     */
    private static final int VWF_PORT = 3000;

    /**
     * Authentication info for HTTP PUT, write access to the content repository.
     */
    public static final String PUT_USER = "writeuser";
    public static final String PUT_PASSWORD = "i6YeOSg12@MO";

    /**
     * Base URL, relative to our root URL, under which ontologies can be found.
     * This is kind of a hack, since we should be able to load ontologies from
     * anywhere. This hack probably only affects the S3D tool.
     */
    private static final String ONTOLOGY_PATH = "knowledge/weapons/M4/";

    private final Bridge bridge;
    private final Server server;
    private final ContextHandlerCollection contexts;
    private final File repoDir;
    private final File vwfDir;
    private final HttpExecutor uiExecutor;
    private final URL rootUrl;
    private final XApiRequestTracker reqTracker;
    private final RepoScanner repoScanner;
    private final RepoScanner vwfScanner;
    private final FloraWrapper floraWrapper;
    private final S3DMap catS3dMap;
    private ExerciseHandler catExer;


    public static void main(String[] args)
            throws Exception {
        Backend main = new Backend(args);
        main.start();
        main.waitForShutdown();
    }

    Backend(String[] args)
            throws PALException,
            IOException,
            URISyntaxException,
            JAXBException,
            SAXException,
            FlrException {
        LogUtil.configureLogging("log_config", Backend.class);

        /*
         * Disable loop learning. Otherwise LAPDOG will see us loosen both carry
         * handle screws and generalize to loosening all carry handle screws.
         * It's technically correct, but other parts of the software can't
         * handle loops yet. Can LAPDOG make an option out of a looped
         * procedure? Can the solution editor handle loops?
         */
        System.setProperty("lapdog.disable-loop-learning", "true");

        File propFile = new File("backend.properties");
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(propFile)) {
            props.load(is);
        }
        String repoPath = props.getProperty(REPO_KEY, REPO_PATH);
        String vwfPath = props.getProperty(VWF_KEY, VWF_PATH);
        String xsbPath = props.getProperty(XSB_KEY, XSB_PATH);
        String floraPath = props.getProperty(FLORA_KEY, FLORA_PATH);
        repoDir = new File(repoPath);
        vwfDir = new File(vwfPath);
        File xsbExe = new File(xsbPath);
        File floraDir = new File(floraPath);

        URL baseUrl = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-baseUrl")) {
                i++;
                baseUrl = new URL(args[i]);
            } else {
                throw new IllegalArgumentException("Unknown command line arg '"
                        + arg + "'");
            }
        }

        // Initialize the Bridge.
        Bridge.startPAL();
        bridge = Bridge.newInstance(NAMESPACE);
        bridge.setTypeStorage(new FileTypeStorage(Files.createTempDirectory(
                "SAVE_am").toFile(), bridge.getSpine().getClientId()));
        log.info("Connected to DEFT");

        // Build the web server.
        int port = httpPort();
        server = new Server(port);
        contexts = new ContextHandlerCollection();
        server.setHandler(contexts);
        if (baseUrl == null) {
            String hostname = InetAddress.getLocalHost().getHostName();
            baseUrl = new URL("http://" + hostname + ":" + port);
        }
        rootUrl = baseUrl;

        File vwfPublicDir = new File(vwfDir, "public");
        URL vwfBaseUrl = new URL(baseUrl.getProtocol() + "://"
                + baseUrl.getHost() + ":" + VWF_PORT);

        reqTracker = new XApiRequestTracker(repoDir);

        // This is the executor of all action callbacks from Adept.
        uiExecutor = new HttpExecutor();

        ExerciseFactory exerFact = new ExerciseFactory(this);
        catS3dMap = new S3DMap();
        S3DFactory s3dFact = new S3DFactory(catS3dMap);
        ColladaFactory collFact = new ColladaFactory();
        FloraFactory floraFact = new FloraFactory();
        repoScanner = new RepoScanner(repoDir, rootUrl, exerFact, s3dFact,
                floraFact);
        EuiFactory euiFact = new EuiFactory();
        vwfScanner = new RepoScanner(vwfPublicDir, vwfBaseUrl, euiFact, collFact);

        floraWrapper = new FloraWrapper(xsbExe, floraDir);
    }

    void start()
            throws Exception {
        /*
         * Also serve static content from the content repository directory, but
         * filter known file types replacing relative URLs with absolute ones.
         */
        ServletContextHandler repoHandler = new ServletContextHandler();
        repoHandler.addFilter(QualifyingFilter.class, "/*",
                EnumSet.of(DispatcherType.REQUEST));
        repoHandler.addFilter(CorsFilter.class, "/*",
                EnumSet.of(DispatcherType.REQUEST));
        ServletHolder sh = new ServletHolder(new RepositoryServlet(repoScanner));
        repoHandler.addServlet(sh, "/*");
        repoHandler.setResourceBase(repoDir.getAbsolutePath());
        repoHandler.setSecurityHandler(setupAuth());
        contexts.addHandler(repoHandler);

        /* The handler to talk to PERLS. */
        XApiHandler xh = new XApiHandler(reqTracker);
        ContextHandler xhCtx = new ContextHandler();
        xhCtx.setAllowNullPathInfo(true);
        xhCtx.setContextPath("/xAPI");
        xhCtx.setHandler(xh);
        contexts.addHandler(xhCtx);

        /* The florahttp server, used by the S3D tool. */
        URL ontBase = new URL(rootUrl, ONTOLOGY_PATH);
        FloraHttpServlet floraHttp = new FloraHttpServlet(floraWrapper, ontBase);
        ContextHandler flCtx = new ContextHandler();
        flCtx.setAllowNullPathInfo(true);
        flCtx.setContextPath("/flora/server");
        flCtx.setHandler(floraHttp);
        contexts.addHandler(flCtx);

        /*
         * We have to start this before loading all the exercises, because the
         * exercises will load data files from the web server. There's a small
         * window race condition here.
         */
        server.start();

        repoScanner.scanAll();
        vwfScanner.scanAll();
        RepoDirHandler rdh = new RepoDirHandler(repoScanner, vwfScanner);
        ContextHandler rdhCtx = new ContextHandler();
        rdhCtx.setAllowNullPathInfo(true);
        rdhCtx.setContextPath("/listfiles");
        rdhCtx.setHandler(rdh);
        contexts.addHandler(rdhCtx);

        // Set up an "exercise" for the CAT tool.
        URL catUrl = new URL(rootUrl, "CAT");
        catExer = new ExerciseHandler(catUrl, this, catS3dMap);
        catExer.startup();

        // Register any internal actions.
        SimpleTypeName leName = new SimpleTypeName("lessThanOrEqualTo",
                VERSION, NAMESPACE);
        LocalQueryExecutor localQueryExec = new LocalQueryExecutor();
        bridge.getActionModel().registerExecutor(leName, localQueryExec);

        log.info("Listening on HTTP port {}", httpPort());
    }

    public Bridge getBridge() {
        return bridge;
    }

    public HttpExecutor getUiExecutor() {
        return uiExecutor;
    }

    public ContextHandlerCollection getContexts() {
        return contexts;
    }

    public File getRepoRoot() {
        return repoDir;
    }

    public URL getRootUrl() {
        return rootUrl;
    }

    public File getVwfDir() {
        return vwfDir;
    }

    public RepoScanner getRepoScanner() {
        return repoScanner;
    }

    public RepoScanner getVwfScanner() {
        return vwfScanner;
    }

    public XApiRequestTracker getRequestTracker() {
        return reqTracker;
    }

    public FloraWrapper getFloraWrapper() {
        return floraWrapper;
    }

    void waitForShutdown()
            throws Exception {
        // Now wait until the system shuts down, servicing both callbacks from
        // DEFT to the UI and calls from the UI to DEFT.
        StatusListener sl = new StatusListener();
        PALStatusMonitor.addListener(sl);
        while(sl.isRunning()) {
            Thread.sleep(1000);
        }

        // Shut things down.
        repoScanner.shutdown();
        vwfScanner.shutdown();
        if (catExer != null) {
            catExer.shutdown();
        }
        floraWrapper.close();
        server.stop();
        bridge.shutdown();
    }

    public static int httpPort() {
        String port = System.getProperty(PORT_PROP);
        if (port != null) {
            return Integer.parseInt(port);
        } else {
            return DEFAULT_HTTP_PORT;
        }
    }

    private SecurityHandler setupAuth() {
        final String realm = "SAVE content repositories";

        HashLoginService ls = new HashLoginService();
        ls.putUser(PUT_USER, Credential.getCredential(PUT_PASSWORD),
                new String[] { "user" });
        ls.setName(realm);

        Constraint con = new Constraint();
        con.setName(Constraint.__BASIC_AUTH);
        con.setRoles(new String[] { "user" });
        con.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(con);
        cm.setPathSpec("/*");
        cm.setMethod("PUT");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName(realm);
        csh.addConstraintMapping(cm);
        csh.setLoginService(ls);

        return csh;
    }

    public static <T> T jaxbReader(URL sourceUrl,
                                   URL schemaUrl,
                                   Unmarshaller unmarshaller,
                                   Class<T> clazz)
            throws JAXBException,
            SAXException {
        SchemaFactory schemaFactory = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = schemaFactory.newSchema(schemaUrl);
        unmarshaller.setSchema(schema);
        ValidationEventCollector vec = new ValidationEventCollector();
        unmarshaller.setEventHandler(vec);
        JAXBElement<?> ele = (JAXBElement<?>) unmarshaller.unmarshal(sourceUrl);
        for (ValidationEvent ve : vec.getEvents()) {
            String msg = ve.getMessage();
            ValidationEventLocator vel = ve.getLocator();
            int line = vel.getLineNumber();
            int column = vel.getColumnNumber();
            log.warn("XML parse error detail: line " + line + ", col "
                    + column + ": " + msg);
        }
        @SuppressWarnings("unchecked")
        T obj = (T) ele.getValue();
        return obj;
    }

    public static void deleteAll(File file)
            throws IOException {
        if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
            for (File child : file.listFiles()) {
                deleteAll(child);
            }
        }
        boolean ok = file.delete();
        if (!ok) {
            throw new IOException("Failed to delete " + file.getCanonicalPath());
        }
    }

    private static class StatusListener implements PALStatusListener {
        private volatile Status status;

        public StatusListener() {
            status = Status.UNKNOWN;
        }

        @Override
        public void newStatus(Status newStatus) {
            status = newStatus;
        }

        public boolean isRunning() {
            if (status != Status.DOWN) {
                return true;
            } else {
                return false;
            }
        }
    }
}
