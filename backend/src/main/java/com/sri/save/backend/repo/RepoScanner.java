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
package com.sri.save.backend.repo;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scan the content repository. If a file changes, re-scan it.
 */
public class RepoScanner {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final File repoRoot;
    private final URL rootUrl;
    private final RepoArtifactFactory[] factories;
    private final Map<File, RepoArtifact> artifacts;

    public RepoScanner(File repoRoot,
                       URL rootUrl,
                       RepoArtifactFactory... factories)
            throws IOException {
        this.repoRoot = repoRoot.getCanonicalFile();
        this.rootUrl = rootUrl;
        this.factories = factories;
        artifacts = new HashMap<>();
    }

    /**
     * List the names of the types of files which this class scans for and knows
     * how to parse.
     */
    public Set<String> getKnownFileTypes() {
        Set<String> types = new HashSet<>();
        for (RepoArtifactFactory fact : factories) {
            types.add(fact.getTypeName());
        }
        return types;
    }

    public void scanAll()
            throws IOException {
        log.debug("Scanning all files under {} using {}", repoRoot, factories);
        scanDir(repoRoot);
        log.debug("Scan done.");
    }

    public void scanDir(File dir)
            throws IOException {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                scanDir(file);
            } else {
                scan(file);
            }
        }
    }

    public void scan(File file)
            throws IOException {
        file = file.getCanonicalFile();
        URL url = fileToUrl(file);
        boolean added = false;
        for (RepoArtifactFactory fact : factories) {
            RepoArtifact art = fact.parse(file, url);
            if (art != null) {
                addArtifact(file, art);
                added = true;
                break;
            }
        }
        if (!added) {
            log.debug("Ignoring file ({}) with unknown type", file);
        }
    }

    private synchronized void addArtifact(File file,
                                          RepoArtifact art) {
        RepoArtifact oldArt = artifacts.get(file);
        if (oldArt != null) {
            oldArt.shutdown();
        }
        artifacts.put(file, art);
        art.startup();
    }

    /**
     * List all of the URLs of files of the requested type.
     * @param fileType a type returned by a registered RepoArtifactFactory.getTypeName().
     * @param hostname rewrite URLs to be on the provided host
     */
    public synchronized List<URL> listUrls(String fileType,
                                           String hostname)
            throws MalformedURLException {
        List<URL> urls = new ArrayList<>();
        for (RepoArtifactFactory fact : factories) {
            if (fact.getTypeName().equals(fileType)) {
                for (File file : artifacts.keySet()) {
                    RepoArtifact art = artifacts.get(file);
                    if (fact.isArtifact(art)) {
                        URL url = art.getUrl();
                        if (hostname != null && !hostname.isEmpty()) {
                            url = new URL(url.getProtocol(), hostname, url.getPort(), url.getFile());
                        }
                        urls.add(url);
                    }
                }
            }
        }
        return urls;
    }

    private URL fileToUrl(File file) {
        Path root = repoRoot.toPath();
        Path filePath = file.toPath();
        Path relPath = root.relativize(filePath);
        String urlPath = relPath.toString();
        urlPath = urlPath.replace('\\', '/');
        try {
            URL url = new URL(rootUrl, urlPath);
            return url;
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        for (RepoArtifact art : artifacts.values()) {
            art.shutdown();
        }
    }
}
