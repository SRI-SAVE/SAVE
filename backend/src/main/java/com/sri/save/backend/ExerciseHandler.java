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

import com.sri.floralib.ast.FloraTerm;
import com.sri.floralib.ext.interprolog.FlrException;
import com.sri.floralib.model.doc.FileFloraDocumentService;
import com.sri.floralib.model.ont.FileOntologyService;
import com.sri.floralib.model.ont.IOntologyModel;
import com.sri.floralib.parser.FloraParserException;
import com.sri.floralib.reasoner.QueryResult;
import com.sri.floralib.reasoner.Solution;
import com.sri.pal.ActionDef;
import com.sri.pal.ActionModel;
import com.sri.pal.ActionModelDef;
import com.sri.pal.Bridge;
import com.sri.pal.PALException;
import com.sri.pal.common.SimpleTypeName;
import com.sri.pal.jaxb.ActionModelType;
import com.sri.pal.jaxb.ActionType;
import com.sri.pal.jaxb.ListType;
import com.sri.pal.jaxb.MemberType;
import com.sri.pal.jaxb.MetadataType;
import com.sri.pal.jaxb.ObjectFactory;
import com.sri.pal.jaxb.ParamType;
import com.sri.pal.jaxb.StructMemberType;
import com.sri.pal.jaxb.StructType;
import com.sri.pal.jaxb.TypeRef;
import com.sri.pal.jaxb.TypeType;
import com.sri.pal.training.core.basemodels.ExerciseBase;
import com.sri.pal.training.core.exercise.Datafile;
import com.sri.pal.training.core.exercise.Exercise;
import com.sri.pal.training.core.storage.ExerciseFactory;
import com.sri.save.backend.S3DMap.S3DAsset;
import com.sri.save.backend.flora2deft.ActionModelGenerator;
import com.sri.save.backend.http.ActionHandler;
import com.sri.save.backend.http.AssessmentHandler;
import com.sri.save.backend.http.ExerciseBuilder;
import com.sri.save.backend.http.HttpExecutor;
import com.sri.save.backend.http.HttpUtil;
import com.sri.save.backend.http.InventoryHandler;
import com.sri.save.backend.http.ObjectHandler;
import com.sri.save.backend.http.RedirectHandler;
import com.sri.save.backend.http.ResettableActionListener;
import com.sri.save.backend.http.SolutionHandler;
import com.sri.save.backend.http.UIQueryHandler;
import com.sri.save.backend.repo.RepoArtifact;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.B64Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Represents one exercise the user could perform.
 */
public class ExerciseHandler
        implements RepoArtifact {
    private static final String QUERY_KEY = "ontologyQuery";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final File exerciseFile;
    private final URL baseUrl;
    private final Backend backend;
    private final Set<ContextHandler> handlers;
    private final Exercise exercise;
    private final FloraWrapper floraWrapper;
    private final S3DMap s3dMap;
    private ResettableActionListener actionListener;


    /**
     * Typical constructor for handling a regular exercise file. Parses all the
     * data file and sets up HTTP handlers.
     */
    public ExerciseHandler(File exerFile,
                           URL baseUrl,
                           Backend backend)
            throws IOException,
            JAXBException,
            SAXException {
        exerciseFile = exerFile;
        this.baseUrl = baseUrl;
        this.backend = backend;
        floraWrapper = backend.getFloraWrapper();
        handlers = new HashSet<>();
        s3dMap = new S3DMap();

        // Load the exercise file, since it points to all the other resources.
        exercise = Backend.jaxbReader(exerFile.toURI().toURL(),
                ExerciseBase.class.getResource("training.xsd"),
                ExerciseFactory.getUnmarshaller(), Exercise.class);
    }

    /**
     * Constructor to support the CAT tool. Scans all the S3D files in the
     * content repository. Only offers a subset of our total REST endpoints.
     */
    ExerciseHandler(URL baseUrl,
                    Backend backend,
                    S3DMap s3dMap)
            throws MalformedURLException,
            IOException {
        exerciseFile = null;
        this.baseUrl = baseUrl;
        this.backend = backend;
        this.s3dMap = s3dMap;
        floraWrapper = backend.getFloraWrapper();
        handlers = new HashSet<>();
        exercise = null;
    }

    @Override
    public void startup() {
        try {
            if (exerciseFile == null) {
                startupCat();
            } else {
                startupNormal();
            }
        } catch (Exception e) {
            /*
             * Something went wrong. Capture the error and show it to any HTTP
             * clients of any path under our base URL. If the exercise file is
             * replaced, our shutdown() should unregister the error handler.
             */
            ErrorHandler eh = new ErrorHandler(e);
            ContextHandler ehCtx = new ContextHandler();
            ehCtx.setAllowNullPathInfo(true);
            ehCtx.setContextPath(baseUrl.getPath());
            ehCtx.setHandler(eh);
            handlers.add(ehCtx);
            backend.getContexts().addHandler(ehCtx);
        }
    }

    /**
     * "Normal" means we're handling an exercise file.
     */
    private void startupNormal()
            throws IOException,
            JAXBException,
            FlrException,
            FloraParserException,
            SAXException,
            PALException {
        final boolean hasSolution;
        if (exercise.getSolution() != null) {
            hasSolution = true;
        } else {
            hasSolution = false;
        }

        // Load the S3D files.
        for (Datafile df : exercise.getDatafile()) {
            URL s3dUrl = new URL(baseUrl, df.getValue());
            boolean isAuto = false;
            if (df.isAuto() != null) {
                isAuto = df.isAuto();
            }
            s3dMap.load(s3dUrl, isAuto);
        }

        // Load the ontologies referenced by those S3Ds.
        Set<URL> floraUrls = s3dMap.getFloraUrls();
        for(URL url : floraUrls) {
            floraWrapper.load(url);
        }

        // Load the action models.
        Set<ActionDef> uiActions = new HashSet<>();
        ActionModel actionModel = backend.getBridge().getActionModel();
        for (URL floraUrl : floraUrls) {
            URL amUrl = getActionModelForFlora(floraUrl);
            String namespace = getNamespaceForFlora(floraUrl);
            maybeGenerateActionModel(floraUrl, amUrl, s3dMap);
            Set<ActionModelDef> loaded = actionModel.load(amUrl, namespace);

            // Sort the actions we just loaded into different buckets.
            Set<ActionDef> queryActions = new HashSet<>();
            for (ActionModelDef def : loaded) {
                if (!(def instanceof ActionDef)) {
                    continue;
                }
                ActionDef actDef = (ActionDef) def;
                String shortName = actDef.getName().getSimpleName();
                if (actDef.getMetadata(QUERY_KEY) != null) {
                    /*
                     * If the key exists, its value should be a flora expression
                     * in terms of this action's parameters, such as
                     * "%1$s [ %2$s -> %3$s ]"
                     */
                    queryActions.add(actDef);
                } else if (shortName.equals("lessThanOrEqualTo")) {
                    // skip it; it's handled in Main
                } else {
                    uiActions.add(actDef);
                }
            }

            // Set up executors for any query actions.
            for (ActionDef actDef : queryActions) {
                String query = actDef.getMetadata(QUERY_KEY);
                FloraExecutor exec = new FloraExecutor(floraWrapper, query);
                actionModel.registerExecutor(actDef.getName(), exec);
            }
        }

        HttpExecutor uiExec = backend.getUiExecutor();
        Bridge bridge = backend.getBridge();

        // Register an HTTP handler to serve the underlying exercise file.
        RedirectHandler redirectHandler = new RedirectHandler(new URL(baseUrl,
                exerciseFile.getName()));
        ContextHandler rCtx = new ContextHandler();
        rCtx.setAllowNullPathInfo(true);
        rCtx.setContextPath(baseUrl.getPath());
        rCtx.setHandler(redirectHandler);
        handlers.add(rCtx);

        // Register an HTTP handler for user action events.
        ActionHandler actionHandler = new ActionHandler(uiActions,
                floraWrapper, uiExec);
        ContextHandler actCtx = new ContextHandler();
        actCtx.setAllowNullPathInfo(true);
        actCtx.setContextPath(baseUrl.getPath() + "/action");
        actCtx.setHandler(actionHandler);
        handlers.add(actCtx);

        /*
         * Register executors for the UI actions in the action model. Callbacks
         * will come from DEFT and be sent to the UI via the web server.
         */
        for (ActionDef actDef : uiActions) {
            SimpleTypeName actName = actDef.getName();
            if (actionModel.getExecutor(actName) == null) {
                actionModel.registerExecutor(actName, uiExec);
            }
        }

        // Register a handler for various queries from the UI.
        UIQueryHandler qh = new UIQueryHandler(this, s3dMap, floraWrapper);
        ContextHandler qhCtx = new ContextHandler();
        qhCtx.setAllowNullPathInfo(true);
        qhCtx.setContextPath(baseUrl.getPath() + "/query");
        qhCtx.setHandler(qh);
        handlers.add(qhCtx);

        // Handler for inventory (and tool tray) requests.
        InventoryHandler ih = new InventoryHandler(s3dMap, hasSolution);
        ContextHandler ihCtx = new ContextHandler();
        ihCtx.setAllowNullPathInfo(true);
        ihCtx.setContextPath(baseUrl.getPath() + "/inventory");
        ihCtx.setHandler(ih);
        handlers.add(ihCtx);

        // Handler for object instantiation requests.
        ObjectHandler oh = new ObjectHandler(s3dMap, floraWrapper, actionModel,
                null);
        ContextHandler ohCtx = new ContextHandler();
        ohCtx.setAllowNullPathInfo(true);
        ohCtx.setContextPath(baseUrl.getPath() + "/object");
        ohCtx.setHandler(oh);
        handlers.add(ohCtx);

        /*
         * Register an end-of-exercise handler, either assessment or exercise
         * construction.
         */
        if (hasSolution) {
            AssessmentHandler assessmentHandler = new AssessmentHandler(bridge,
                    backend.getRequestTracker(), exercise);
            actionListener = assessmentHandler;
            ContextHandler ahCtx = new ContextHandler();
            ahCtx.setAllowNullPathInfo(true);
            ahCtx.setContextPath(baseUrl.getPath() + "/assessment");
            ahCtx.setHandler(assessmentHandler);
            handlers.add(ahCtx);
        } else {
            SolutionHandler solutionHandler = new SolutionHandler(backend,
                    exercise, exerciseFile);
            actionListener = solutionHandler;
            ContextHandler shCtx = new ContextHandler();
            shCtx.setAllowNullPathInfo(true);
            shCtx.setContextPath(baseUrl.getPath() + "/generateSolution");
            shCtx.setHandler(solutionHandler);
            handlers.add(shCtx);
        }

        // Register all the handlers.
        ContextHandlerCollection httpContexts = backend.getContexts();
        for (ContextHandler handler : handlers) {
            httpContexts.addHandler(handler);
        }
    }

    /**
     * Handle requests coming from the Content Assembly Tool. This "exercise"
     * handler assists the CAT in building exercises.
     */
    private void startupCat()
            throws IOException,
            FlrException {
        File repoDir = backend.getRepoRoot();
        Bridge bridge = backend.getBridge();

        // Load all the referenced Flora ontologies.
        for (URL url : s3dMap.getFloraUrls()) {
            floraWrapper.load(url);
            // TODO SAVE-289 Load new ontologies when new S3Ds are added to the content repo.
        }

        // Register a handler to build an initial exercise.
        ExerciseBuilder eb = new ExerciseBuilder(backend,
                backend.getRepoScanner(), backend.getVwfScanner());
        ContextHandler ebCtx = new ContextHandler();
        ebCtx.setAllowNullPathInfo(true);
        ebCtx.setContextPath(baseUrl.getPath() + "/finishExercise");
        ebCtx.setHandler(eb);
        handlers.add(ebCtx);

        actionListener = eb;

        // Register a handler for various queries from the UI.
        UIQueryHandler qh = new UIQueryHandler(this, s3dMap, floraWrapper);
        ContextHandler qhCtx = new ContextHandler();
        qhCtx.setAllowNullPathInfo(true);
        qhCtx.setContextPath(baseUrl.getPath() + "/query");
        qhCtx.setHandler(qh);
        handlers.add(qhCtx);

        // Handler for inventory (and tool tray) requests.
        InventoryHandler ih = new InventoryHandler(s3dMap, false);
        ContextHandler ihCtx = new ContextHandler();
        ihCtx.setAllowNullPathInfo(true);
        ihCtx.setContextPath(baseUrl.getPath() + "/inventory");
        ihCtx.setHandler(ih);
        handlers.add(ihCtx);

        // Handler for object instantiation requests.
        ObjectHandler oh = new ObjectHandler(s3dMap, floraWrapper,
                bridge.getActionModel(), eb);
        ContextHandler ohCtx = new ContextHandler();
        ohCtx.setAllowNullPathInfo(true);
        ohCtx.setContextPath(baseUrl.getPath() + "/object");
        ohCtx.setHandler(oh);
        handlers.add(ohCtx);

        // Register all the handlers.
        ContextHandlerCollection httpContexts = backend.getContexts();
        for (ContextHandler handler : handlers) {
            httpContexts.addHandler(handler);
        }
    }

    /**
     * Generate an action model from a Flora ontology, if the action model
     * doesn't already exist.
     */
    private void maybeGenerateActionModel(URL floraUrl,
                                          URL amUrl,
                                          S3DMap s3dMap)
            throws IOException,
            JAXBException,
            FlrException,
            FloraParserException,
            SAXException {
        if (!urlExists(amUrl)) {
            generateActionModel(floraUrl, amUrl, s3dMap);
        }
    }

    private boolean urlExists(URL url)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setUseCaches(false);
        int code = conn.getResponseCode();
        if (code == 200) {
            return true;
        }
        if (code == 404) {
            return false;
        }
        throw new IOException(code + " " + conn.getResponseMessage());
    }

    private void generateActionModel(URL floraUrl,
                                     URL amUrl,
                                     S3DMap s3dMap)
            throws JAXBException,
            IOException,
            FlrException,
            FloraParserException,
            SAXException {
        log.debug("Generating action model from {} into {}", floraUrl, amUrl);

        /* Set up the Flora objects. */
        Set<URL> floraUrls = new HashSet<>();
        floraUrls.add(floraUrl);
        FileFloraDocumentService ffds = floraWrapper.getDocumentService();
        FileOntologyService fos = new FileOntologyService(ffds);
        IOntologyModel<File> ont = fos.getActiveOntologyModel();
        File workingDir = ffds.getActiveFile().getParentFile();

        /*
         * Call the action model generator. It write the first two files, but
         * not the third.
         */
        String name = amUrl.getPath().replaceFirst("^.*/", "")
                .replaceFirst("\\.[^.]+$", "");
        File amTop = new File(workingDir, name + ".xml");
        File amTypes = new File(workingDir, name + "_types.xml");
        File amCreate = new File(workingDir, name + "_create.xml");
        ActionModelGenerator.generateActionModel(ont, amTop, amTypes, amCreate);

        /*
         * Find an S3D asset which references a class from this Flora file.
         * Things will break if there's more than one.
         */
        String assetClass = null;
        for (S3DAsset asset : s3dMap.getAssets()) {
            URL assetFloraUrl = asset.getFloraBase();
            if (assetFloraUrl.equals(floraUrl)) {
                assetClass = asset.getFloraClass();
                break;
            }
        }
        if (assetClass == null) {
            throw new NoSuchElementException("Can't find asset for " + floraUrl);
        }

        /*
         * Build the JAXB object containing the "Create" action and everything
         * it depends on.
         */
        ActionModelType am = new ActionModelType();

        /*
         * Read the version from the top action model file, and copy it to this
         * new file.
         */
        JAXBContext jaxbCtx = JAXBContext.newInstance(ActionModelType.class
                .getPackage().getName());
        Unmarshaller unmar = jaxbCtx.createUnmarshaller();
        SchemaFactory schemaFactory = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        URL schemaUrl = ActionModel.class.getResource("ActionModel.xsd");
        Schema schema = schemaFactory.newSchema(schemaUrl);
        unmar.setSchema(schema);
        JAXBElement<?> ele = (JAXBElement<?>) unmar.unmarshal(amTop);
        ActionModelType topAm = (ActionModelType) ele.getValue();
        am.setVersion(topAm.getVersion());

        /* Build the struct containing the created object. */
        TypeType struct = new TypeType();
        am.getType().add(struct);
        struct.setId(assetClass + "Components");
        struct.setDescription("One instance of " + assetClass
                + " and all its components");
        StructType innerStruct = new StructType();
        struct.setStruct(innerStruct);
        // Fields added below.

        /* Build the "Create" action. */
        ActionType create = new ActionType();
        am.getAction().add(create);
        create.setId("Create" + assetClass);
        create.setDescription("Create a new " + assetClass);
        MetadataType md1 = new MetadataType();
        create.getMetadata().add(md1);
        md1.setKey("name");
        md1.setValue("Create an object");
        MetadataType md2 = new MetadataType();
        create.getMetadata().add(md2);
        md2.setKey("fancyName");
        md2.setValue("Create " + assetClass + " instance");
        ParamType param = new ParamType();
        create.getOutputParam().add(param);
        param.setId(assetClass);
        param.setDescription("The created " + assetClass);
        TypeRef typeRef = new TypeRef();
        param.setTypeRef(typeRef);
        typeRef.setTypeId("PhysicalEntity");
        param = new ParamType();
        create.getOutputParam().add(param);
        param.setId("components");
        param.setDescription("The " + assetClass + "'s components");
        typeRef = new TypeRef();
        param.setTypeRef(typeRef);
        typeRef.setTypeId(struct.getId());

        /*
         * Find all of the Flora classes of things which make up this object,
         * and establish whether the component class has multiple instances per
         * object.
         */
        String parentId = floraWrapper.create(assetClass);
        String query = parentId
                + "[hasComponent -> ?y], first_direct_type(?y, ?z)";
        QueryResult qr = floraWrapper.query(query);
        SortedMap<String, Boolean> hasMultiple = new TreeMap<>();
        for (Solution sol : qr.getSolutions()) {
            FloraTerm solTerm = sol.getBinding("?z");
            String componentClass = solTerm.toString();
            if (hasMultiple.containsKey(componentClass)) {
                hasMultiple.put(componentClass, true);
            } else {
                hasMultiple.put(componentClass, false);
            }
        }

        /* For each component class, add a struct field. */
        for (String componentClass : hasMultiple.keySet()) {
            StructMemberType field = new StructMemberType();
            innerStruct.getRef().add(field);
            field.setName(componentClass);
            // TODO They're not all PhysicalEntity. Detect Screw and Switch.
            if (hasMultiple.get(componentClass)) {
                field.setTypeRef("PhysicalEntities");
            } else {
                field.setTypeRef("PhysicalEntity");
            }
        }

        /* Add other types which our struct depends on. */
        TypeType entities = new TypeType();
        am.getType().add(0, entities);
        entities.setId("PhysicalEntities");
        entities.setDescription("A list of physical entities");
        ListType listType = new ListType();
        entities.setList(listType);
        MemberType listMemberType = new MemberType();
        listType.setRef(listMemberType);
        listMemberType.setTypeRef("PhysicalEntity");

        /* Write out the JAXB structure into a file. */
        Marshaller marsh = jaxbCtx.createMarshaller();
        marsh.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        try (FileOutputStream os = new FileOutputStream(amCreate)) {
            ObjectFactory of = new ObjectFactory();
            JAXBElement<ActionModelType> jaxb = of.createActionModel(am);
            marsh.marshal(jaxb, os);
        }

        /* Copy the files' contents back to their respective URLs. */
        List<File> files = new ArrayList<>();
        files.add(amTop);
        files.add(amTypes);
        if (!urlExists(new URL(amUrl, amCreate.getName()))) {
            files.add(amCreate);
        }
        for (File file : files) {
            URL url = new URL(amUrl, file.getName());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            String userpass = Backend.PUT_USER + ":" + Backend.PUT_PASSWORD;
            String auth = B64Code.encode(userpass);
            conn.setRequestProperty("Authorization", "Basic " + auth);
            conn.setDoOutput(true);
            conn.setRequestMethod("PUT");
            try (FileInputStream is = new FileInputStream(file);
                    OutputStream os = conn.getOutputStream()) {
                int written = 0;
                while (is.available() > 0) {
                    os.write(is.read());
                    written++;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Wrote {} bytes from {} to {}", new Object[] {
                            written, file, url });
                }
            }
            try {
                int code = conn.getResponseCode();
                if (code != 200) {
                    throw new IOException("Unable to write to " + url + " ("
                            + code + ")");
                }
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
    }

    @Override
    public URL getUrl() {
        return baseUrl;
    }

    public void reset()
            throws FlrException,
            IOException {
        if (actionListener != null) {
            actionListener.reset();
        }
        if (floraWrapper != null) {
            floraWrapper.reset();
        }
    }

    @Override
    public void shutdown() {
        ContextHandlerCollection contexts = backend.getContexts();
        for(ContextHandler handler : handlers) {
            contexts.removeHandler(handler);
        }
    }

    @Override
    public String toString() {
        return "ExerciseHandler[" + baseUrl + "]";
    }

    public static URL getActionModelForFlora(URL floraUrl)
            throws MalformedURLException {
        String basename = floraUrl.getPath();
        basename = basename.replaceFirst("^.*/", "");
        basename = basename.replace(".flr", "");
        URL amUrl = new URL(floraUrl, basename + ".xml");
        return amUrl;
    }

    public static String getNamespaceForFlora(URL floraUrl) {
        String basename = floraUrl.getPath();
        basename = basename.replaceFirst("^.*/", "");
        basename = basename.replace(".flr", "");
        return basename;
    }

    /**
     * Handles HTTP requests by displaying the error that prevented this
     * exercise handler from initializing.
     */
    private static class ErrorHandler
            extends AbstractHandler {
        private final Logger log = LoggerFactory.getLogger(getClass());

        private final Exception error;

        public ErrorHandler(Exception e) {
            error = e;
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

            try (StringWriter sw = new StringWriter();
                    PrintWriter out = new PrintWriter(sw)) {
                error.printStackTrace(out);
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        sw.toString());
            } catch (IOException e) {
                log.warn("Unable to send error", e);
                return;
            }
        }
    }
}
