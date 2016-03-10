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

import com.sri.save.backend.S3DMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

/**
 * Represents an S3D file in the content repository. This is a placeholder for
 * the RepoScanner; it doesn't do anything.
 */
public class S3DArtifact
        implements RepoArtifact {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final S3DMap s3dMap;
    private final URL url;

    public S3DArtifact(S3DMap s3dMap,
                       URL url) {
        this.s3dMap = s3dMap;
        this.url = url;
    }

    @Override
    public void startup() {
        try {
            s3dMap.load(url, false);
        } catch (IOException e) {
            log.info("Failed to read new S3D file " + url, e);
        }
    }

    @Override
    public void shutdown() {
        s3dMap.remove(url);
    }

    @Override
    public URL getUrl() {
        return url;
    }
}
