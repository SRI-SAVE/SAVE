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

import java.net.URL;

/**
 * Represents a particular type of object that has been parsed from a file in
 * the content repository.
 */
public interface RepoArtifact {
    /**
     * Called to signal that the artifact is being accepted and added to the
     * system. If it has any registrations that need to be performed (like
     * servlet registration), those should be done now. If this method is
     * called, it means shutdown will eventually be called.
     */
    public void startup();

    /**
     * Called when the file corresponding to this artifact has been removed
     * from, or replaced in, the content repository.
     */
    public void shutdown();

    /**
     * Provides the publically-visible URL of this artifact.
     */
    public URL getUrl();
}
