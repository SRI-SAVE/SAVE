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

/**
 * Implementations of this interface will try to parse a file as a particular
 * type of data.
 */
public interface RepoArtifactFactory {
    /**
     * Try to parse the given file.
     *
     * @param file
     *            the file to parse
     * @param url
     *            the URL from which clients can retrieve the file
     * @return the parsed object or null
     */
    public RepoArtifact parse(File file, URL url);

    /**
     * Gives a name used to identify the type of file that this factory can
     * parse.
     */
    public String getTypeName();

    /**
     * Returns true if the artifact is of the type created by this factory.
     */
    public boolean isArtifact(RepoArtifact art);
}
