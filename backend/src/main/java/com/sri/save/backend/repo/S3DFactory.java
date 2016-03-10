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
import java.net.URL;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.sri.save.backend.S3DMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sri.save.backend.Backend;
import com.sri.save.s3d.S3DType;

public class S3DFactory
        implements RepoArtifactFactory {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final S3DMap s3dMap;
    private final Unmarshaller unmarshaller;

    /**
     * Parses S3D files and returns Java objects to represent them.
     *
     * @param s3dMap will be updated when S3D files are added or removed
     */
    public S3DFactory(S3DMap s3dMap)
            throws JAXBException {
        this.s3dMap = s3dMap;
        JAXBContext jc = JAXBContext.newInstance(S3DType.class.getPackage()
                .getName());
        unmarshaller = jc.createUnmarshaller();
    }

    @Override
    public String getTypeName() {
        return "s3d";
    }

    @Override
    public RepoArtifact parse(File file,
                              URL url) {
        if (!file.getName().toLowerCase().endsWith(".s3d")) {
            return null;
        }
        try {
            Backend.jaxbReader(file.toURI().toURL(),
                    S3DType.class.getResource("s3d.xsd"), unmarshaller,
                    S3DType.class);
            return new S3DArtifact(s3dMap, url);
        } catch (Exception e) {
            log.debug("Not an S3D file: {} ({})", file, e);
            if (log.isTraceEnabled()) {
                log.trace("Not an S3D file: " + file, e);
            }
            return null;
        }
    }

    @Override
    public boolean isArtifact(RepoArtifact art) {
        return (art instanceof S3DArtifact);
    }
}
