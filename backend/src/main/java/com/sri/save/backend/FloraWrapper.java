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

import com.sri.floralib.ast.FloraDocument;
import com.sri.floralib.ast.FloraList;
import com.sri.floralib.ast.FloraTerm;
import com.sri.floralib.ast.NamespaceMapping;
import com.sri.floralib.ext.interprolog.FlrException;
import com.sri.floralib.model.doc.FileFloraDocumentService;
import com.sri.floralib.parser.FloraParser;
import com.sri.floralib.parser.FloraParserException;
import com.sri.floralib.reasoner.FloraInterprologEngine;
import com.sri.floralib.reasoner.IFloraEngine;
import com.sri.floralib.reasoner.QueryResult;
import com.sri.floralib.reasoner.Solution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wrapper around Flora which makes it easier to do lazy initialization.
 */
public class FloraWrapper
        implements AutoCloseable {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static boolean inited = false;

    private final File ontologyDir;
    private final Set<String> loadedFiles;

    private FloraInterprologEngine flora;
    private NamespaceMapping nsmap;
    private FileFloraDocumentService docService;
    private boolean dataLoaded = false;

    public FloraWrapper(File xsb,
                        File floraDir)
            throws IOException,
            FlrException {
        /*
         * This class relies on floralib to talk to Flora, and floralib appears
         * to deadlock against itself if more than one instance is running.
         */
        synchronized (getClass()) {
            if (inited) {
                throw new IllegalStateException(
                        "This must be a singleton object");
            }
            inited = true;
        }

        ontologyDir = Files.createTempDirectory("SAVE").toFile();
        loadedFiles = new HashSet<>();

        // Connect to Flora.
        log.debug("Starting Flora in {}", ontologyDir);
        flora = new FloraInterprologEngine(xsb.getCanonicalPath(), floraDir.getCanonicalPath(),
                ontologyDir.getAbsolutePath());

        // Set up the Flora document service. We'll eventually write KB changes.
        docService = new FileFloraDocumentService(ontologyDir);
    }

    /**
     * Load an ontology file from a URL.
     */
    public synchronized void load(URL url)
            throws FlrException,
            IOException {
        String filename = new File(url.getPath()).getName();
        if (loadedFiles.contains(filename)) {
            log.debug("Not re-loading {}", url);
            return;
        }
        log.debug("Loading {}", url);

        // Load the ontology files.
        File ontFile = extractOntFile(docService, url);
        /*
         * TODO Which file do we set as the active file if there are multiple
         * files?
         */
        docService.setActiveFile(ontFile);
        String ontName = ontFile.getName();
        log.debug("Loading Flora file {}", ontName);
        boolean result;
        if (!dataLoaded) {
            result = flora.loadFile(ontName, "main");
            dataLoaded = true;
        } else {
            result = flora.addFile(ontName, "main");
        }
        if (!result) {
            throw new IllegalStateException("Failed to load Flora file '"
                    + ontName + "'");
        }

        loadedFiles.add(filename);

        nsmap = docService.getAllPrefixes(docService.getActiveFile());

        log.info("Flora is running.");
        if (log.isDebugEnabled()) {
            log.debug("All prefixes from {}: {}", docService.getActiveFile(),
                    nsmap.getAllPrefixes());
        }
        if (log.isTraceEnabled()) {
            log.trace("Active doc service file:\n{}", docService.getActiveDocument());
        }
    }

    /**
     * Extracts an ontology file from a URL into the Flora working directory.
     *
     * @param src
     *            refers to a resource on our classpath
     */
    private File extractOntFile(FileFloraDocumentService fds,
                                URL src)
            throws IOException {
        String basename = src.getPath().replaceFirst("^.*/", "");
        File dest = new File(ontologyDir, basename);
        if (dest.exists()) {
            return dest;
        }
        log.debug("Extracting ontology file '{}' to '{}'", src, dest);
        try (OutputStream out = new FileOutputStream(dest);
                InputStream in = src.openStream()) {
            if (in == null) {
                out.close();
                dest.delete();
                throw new FileNotFoundException("resource " + src);
            }
            for (int b = in.read(); b != -1; b = in.read()) {
                out.write(b);
            }
        }

        /* Now find files it imports, and extract them too. */
        try (InputStream is = new FileInputStream(dest)) {
            FloraDocument doc = FloraParser.parse(is);
            for (String importStr : doc.getImports()) {
                if (importStr.startsWith("'")) {
                    importStr = importStr.substring(1);
                }
                if (importStr.endsWith("'")) {
                    importStr = importStr.substring(0, importStr.length() - 1);
                }
                if (!importStr.endsWith(".flr")) {
                    importStr = importStr + ".flr";
                }
                URL newImport = new URL(src, importStr);
                extractOntFile(fds, newImport);
            }
        }
        return dest;
    }

    public IFloraEngine getEngine() {
        return flora;
    }

    public FileFloraDocumentService getDocumentService()
            throws IOException,
            FlrException {
        return docService;
    }

    public NamespaceMapping getNamespaceMapping() {
        if (dataLoaded) {
            return nsmap;
        } else {
            return new NamespaceMapping();
        }
    }

    /**
     * Perform a generic Flora query.
     *
     * @param querystr
     *            the query to perform
     * @return the results of the query
     * @throws IOException
     * @throws FloraParserException
     *             if the query string can't be parsed by Flora
     * @throws FlrException
     *             if Flora can't be initialized
     */
    public QueryResult query(String querystr)
            throws IOException,
            FloraParserException, FlrException {
        if (!dataLoaded) {
            throw new IllegalStateException("No ontology loaded");
        }
        log.debug("Flora query: {}", querystr);
        FloraTerm query = FloraParser.parseTerm(querystr, nsmap);
        QueryResult qr = flora.floraQuery(query, nsmap);
        log.debug("Flora response: {}", qr);
        return qr;
    }

    /**
     * Given a Flora class and parent object's ID, return the IDs of the
     * children with the indicated class.
     *
     * @param parentId
     *            the parent (composition, not inheritance) of the desired
     *            object
     * @param ontClass
     *            the ontology class of the desired object
     * @return the KB IDs of the desired objects
     * @throws FloraParserException
     *             if the query string can't be built correctly
     * @throws IOException
     * @throws FlrException
     *             if Flora can't be initialized
     */
    public List<String> getComponentIds(String parentId,
                                        String ontClass)
            throws IOException,
            FloraParserException,
            FlrException {
        String query = parentId + "[hasComponent -> ?y], ?y : " + ontClass;
        QueryResult qr = query(query);
        List<String> result = new ArrayList<>();
        for (Solution sol : qr.getSolutions()) {
            FloraTerm solTerm = sol.getBinding("?y");
            result.add(solTerm.toString());
        }
        return result;
    }

    /**
     * Given a KB ID, return the list of actions that can be performed on the
     * object represented by that KB ID.
     */
    public Set<String> allActions(String kbid)
            throws IOException,
            FloraParserException,
            FlrException {
        String query = "possible_actions(" + kbid + ", ?actions)";
        QueryResult qr = query(query);
        List<Solution> sols = qr.getSolutions();
        Set<String> actions = new HashSet<>();
        if (sols.isEmpty()) {
            return actions;
        }
        Solution sol = sols.get(0);
        FloraList actList = (FloraList) sol.getBinding("?actions");
        for (FloraTerm actTerm : actList.getChildren()) {
            String actName = actTerm.toString();
            actions.add(actName);
        }
        return actions;
    }

    /**
     * Creates a new object of a requested type in Flora, returning the new KB
     * ID.
     */
    public String create(String objClass)
            throws IOException,
            FloraParserException,
            FlrException {
        String query = "%create(" + objClass + ", ?objid)";
        QueryResult qr = query(query);
        List<Solution> sols = qr.getSolutions();
        if (sols.isEmpty()) {
            throw new IllegalStateException("object " + objClass
                    + " was not created by Flora");
        }
        Solution sol = sols.get(0);
        FloraTerm solTerm = sol.getBinding("?objid");
        return solTerm.toString();
    }

    public synchronized void reset()
            throws FlrException,
            IOException {
        // For now, this is a no-op.
    }

    @Override
    public void close() {
        if (flora != null) {
            log.debug("Closing Flora");
            flora.close();
        }
        if (ontologyDir != null) {
            log.debug("Deleting {}", ontologyDir);
            try {
                Backend.deleteAll(ontologyDir);
            } catch (IOException e) {
                log.info("Failed to delete " + ontologyDir, e);
            }
        }
    }
}
