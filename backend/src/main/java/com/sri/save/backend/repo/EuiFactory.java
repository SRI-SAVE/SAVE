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

/**
 * Scans files under the VWF tree, looking for anything that appears to be an
 * EUI instance.
 */
public class EuiFactory
        implements RepoArtifactFactory {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public String getTypeName() {
        return "eui";
    }

    @Override
    public RepoArtifact parse(File file,
                              URL url) {
        if (file.isFile() && file.getName().equals("eui.json.js")) {
            try {
                url = new URL(url, ".");
            } catch (MalformedURLException e) {
                log.info("Can't make url from " + url + ", .", e);
                return null;
            }
            return new EuiArtifact(url);
        } else {
            return null;
        }
    }

    @Override
    public boolean isArtifact(RepoArtifact art) {
        return (art instanceof EuiArtifact);
    }
}
