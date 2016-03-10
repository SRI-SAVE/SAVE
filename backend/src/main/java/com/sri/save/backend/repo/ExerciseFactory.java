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
import java.net.MalformedURLException;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sri.save.backend.Backend;
import com.sri.save.backend.ExerciseHandler;

public class ExerciseFactory
        implements RepoArtifactFactory {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Backend backend;

    public ExerciseFactory(Backend backend) {
        this.backend = backend;
    }

    @Override
    public String getTypeName() {
        return "exercise";
    }

    @Override
    public RepoArtifact parse(File file,
                              URL url) {
        if (!file.getName().toLowerCase().endsWith(".xml")) {
            return null;
        }
        String urlStr = url.toString();
        urlStr = urlStr.replaceFirst("\\.xml$", "");
        urlStr = urlStr.replaceFirst("_exer$", "");
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            log.info("Failed to build URL for " + file, e);
            return null;
        }
        try {
            ExerciseHandler exercise = new ExerciseHandler(file, url, backend);
            return exercise;
        } catch (Exception e) {
            log.debug("Unable to read {} as exercise ({})", file, e);
            if (log.isTraceEnabled()) {
                log.trace("Unable to read " + file, e);
            }
            return null;
        }
    }

    @Override
    public boolean isArtifact(RepoArtifact art) {
        return (art instanceof ExerciseHandler);
    }
}
