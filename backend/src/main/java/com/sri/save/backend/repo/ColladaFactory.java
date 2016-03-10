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

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Identifies COLLADA (.dae) files.
 */
public class ColladaFactory
        implements RepoArtifactFactory {
    private static final String schemaPath = "/com/sri/save/backend/repo/collada_schema_1_4_1.xsd";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Validator validator;

    public ColladaFactory()
            throws SAXException {
        SchemaFactory schemaFactory = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        URL schemaUrl = getClass().getResource(schemaPath);
        Schema schema = schemaFactory.newSchema(schemaUrl);
        validator = schema.newValidator();
    }

    @Override
    public String getTypeName() {
        return "collada";
    }

    @Override
    public RepoArtifact parse(File file,
                              URL url) {
        if (!file.getName().toLowerCase().endsWith(".dae")) {
            return null;
        }
        synchronized (validator) {
            StreamSource source = new StreamSource(file);
            ErrorHandler eh = new ErrorHandler();
            validator.setErrorHandler(eh);
            try {
                validator.validate(source);
                return new ColladaArtifact(url);
            } catch (Exception e) {
                log.debug("Not a COLLADA file: {} ({})", file, e);
                if (log.isTraceEnabled()) {
                    log.trace("Not a COLLADA file: " + file, e);
                }
                return null;
            }
        }
    }

    @Override
    public boolean isArtifact(RepoArtifact art) {
        return (art instanceof ColladaArtifact);
    }

    private static class ErrorHandler
            extends DefaultHandler {
        @Override
        public void error(SAXParseException err)
                throws SAXException {
            throw err;
        }
    }
}
