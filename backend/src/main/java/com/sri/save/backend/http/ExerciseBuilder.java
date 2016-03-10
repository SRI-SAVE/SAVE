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
package com.sri.save.backend.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.sri.pal.training.core.exercise.Datafile;
import com.sri.pal.training.core.exercise.Exercise;
import com.sri.pal.training.core.exercise.Problem;
import com.sri.pal.training.core.exercise.Task;
import com.sri.pal.training.core.storage.ExerciseFactory;
import com.sri.save.backend.Backend;
import com.sri.save.backend.ExerciseHandler;
import com.sri.save.backend.S3DMap.S3DAsset;
import com.sri.save.backend.repo.RepoScanner;

/**
 * Called by the CAT when the user is done assembling the exercise, this class
 * is responsible for building the initial exercise file which just contains
 * links to S3D files. It also copies the EUI setup files (which were written by
 * the CAT via our RepositoryServlet) into the VWF tree.
 */
public class ExerciseBuilder
        extends AbstractHandler
        implements ResettableActionListener {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final List<S3DAsset> assets;
    private final Gson gson;
    private final URL rootUrl;
    private final File repoDir;
    private final File vwfDir;
    private final File publicDir;
    private final File behaviorDir;
    private final RepoScanner vwfScanner;
    private final RepoScanner repoScanner;

    public ExerciseBuilder(Backend backend,
                           RepoScanner repoScanner,
                           RepoScanner vwfScanner)
            throws IOException {
        assets = new ArrayList<>();
        gson = new Gson();
        this.repoScanner = repoScanner;
        this.vwfScanner = vwfScanner;
        rootUrl = backend.getRootUrl();
        repoDir = backend.getRepoRoot();
        vwfDir = backend.getVwfDir();
        publicDir = new File(vwfDir, "public");
        behaviorDir = new File(publicDir, "SAVE/behavior").getCanonicalFile();
        if (!behaviorDir.isDirectory()) {
            throw new IOException("Can't find VWF behavior dir "
                    + behaviorDir.getCanonicalPath());
        }
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

        response.setContentType("application/json;charset=utf-8");
        baseRequest.setHandled(true);

        // Allow CORS from anywhere.
        response.setHeader("Access-Control-Allow-Origin", "*");

        String body = HttpUtil.readJSON(request, response, "save");
        if (body == null) {
            /* If null, there was a problem that's already been handled. */
            return;
        }

        // If there's a body, it's a JSON action that the UI just finished.
        if (body.length() != 0) {
            ExerciseDone exMsg = gson.fromJson(body, ExerciseDone.class);
            log.info("Finished exercise: {}", exMsg);

            // Set up all the URLs and paths we'll need.
            final File exerciseDir = new File(repoDir, exMsg.getDestPath());
            final String name = exerciseDir.getName();
            final File euiJsonFile = new File(exerciseDir, "eui.json.js");
            final File exerciseFile = new File(exerciseDir, "exercise.xml");
            final URL exerciseUrl = new URL(rootUrl, exMsg.getDestPath() + "/"
                    + "exercise.xml");
            final File euiDir = new File(publicDir, exMsg.getDestPath());
            final File euiBehaveDir = new File(euiDir, ".behave");
            if (log.isDebugEnabled()) {
                log.debug("exerciseDir: {}, name: {}, euiJsonFile: {},"
                        + " exerciseFile: {}, exerciseUrl: {}, euiDir: {},"
                        + " euiBehaveDir: {}", new Object[] { exerciseDir,
                        name, euiJsonFile, exerciseFile, exerciseUrl, euiDir,
                        euiBehaveDir });
            }

            // From the task tray IDs, figure out S3Ds in the auto list.
            Set<URL> autoS3ds = new HashSet<>();
            for (String ttId : exMsg.auto) {
                for (S3DAsset asset : assets) {
                    if (asset.getId().equals(ttId)) {
                        autoS3ds.add(asset.getS3dUrl());
                    }
                }
            }

            // Build the skeleton Exercise XML object.
            Exercise exer = new Exercise();
            exer.setName(name);
            exer.setId(name);
            exer.setSuccessHtml("Congratulations! You successfully performed this exercise.");
            Problem problem = new Problem();
            exer.setProblem(problem);
            Task task = new Task();
            problem.getTasks().add(task);
            task.setName(name);
            task.setId("task-1");

            // Add S3Ds and action models to it.
            Set<URL> amUrls = new HashSet<>();
            for (S3DAsset asset : assets) {
                URL s3dUrl = asset.getS3dUrl();
                Datafile df = new Datafile();
                df.setValue(s3dUrl.toString());
                df.setAuto(autoS3ds.contains(s3dUrl));
                exer.getDatafile().add(df);
                URL floraUrl = asset.getFloraBase();
                URL amUrl = ExerciseHandler.getActionModelForFlora(floraUrl);
                amUrls.add(amUrl);
            }
            for (URL amUrl : amUrls) {
                exer.getActionModel().add(amUrl.toString());
            }

            // Write the file.
            JAXBElement<Exercise> jaxbExer = ExerciseFactory
                    .createExercise(exer);
            Marshaller marsh = ExerciseFactory.getMarshaller();
            try {
                marsh.marshal(jaxbExer, exerciseFile);
            } catch (JAXBException e) {
                log.info("Failed to marshal exercise file", e);
                try (StringWriter sw = new StringWriter();
                        PrintWriter out = new PrintWriter(sw)) {
                    e.printStackTrace(out);
                    response.sendError(
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            sw.toString());
                }
                return;
            }

            // Scan the newly written exercise file.
            repoScanner.scan(exerciseFile);

            // Write an eui.json.js.
            try (FileWriter fw = new FileWriter(euiJsonFile);
                    PrintWriter out = new PrintWriter(fw)) {
                out.println("// Automatically generated by the SAVE backend");
                out.println();
                out.println("var __EUI = {");
                out.println("    \"default\": \"Settings for exercise\",");
                String url = exerciseUrl.toString().replaceAll("\\.xml$", "");
                out.println("    \"baseServerAddress\": \"" + url + "\"");
                out.println("};");
            }

            // Copy the whole exerciseDir to its final home under VWF.
            copyDir(exerciseDir, euiDir);

            // Create .behave as either a symbolic link or a copied directory.
            if (euiBehaveDir.exists()) {
                Backend.deleteAll(euiBehaveDir);
            }
            try {
                Files.createSymbolicLink(euiBehaveDir.toPath(), behaviorDir.toPath());
            }catch(UnsupportedOperationException|IOException|SecurityException e) {
                // Link didn't work, so try copy instead.
                copyDir(behaviorDir, euiBehaveDir);
            }

            // Re-scan for EUI instances.
            vwfScanner.scanDir(euiDir);

            // Don't add the same toolshelf items if the CAT is run again.
            reset();
        }
    }

    private static void copyDir(File src,
                                File dest)
            throws IOException {
        if (src.isFile()) {
            try (FileInputStream in = new FileInputStream(src);
                    FileOutputStream out = new FileOutputStream(dest)) {
                while (in.available() > 0) {
                    int ch = in.read();
                    out.write(ch);
                }
            }
        } else if (src.isDirectory()) {
            if (!dest.isDirectory()) {
                if (!dest.mkdirs()) {
                    throw new IOException("Unable to make directory " + dest);
                }
            }
            for (File srcFile : src.listFiles()) {
                File destFile = new File(dest, srcFile.getName());
                copyDir(srcFile, destFile);
            }
        } else {
            throw new IOException("Unable to copy non-file, non-directory "
                    + src.getAbsolutePath());
        }
    }

    @Override
    public void reset() {
        assets.clear();
    }

    public void addAsset(S3DAsset asset) {
        if (!assets.contains(asset)) {
            assets.add(asset);
        }
    }

    private static class ExerciseDone {
        private String exercise;
        private List<String> auto;

        /**
         * CAT sends a full destination URL, when it should just send a
         * destination path. So we parse it as a URL and extract the path.
         */
        public String getDestPath()
                throws MalformedURLException {
            URL fakeRoot = new URL("http://localhost/");
            URL url = new URL(fakeRoot, exercise);
            String path = url.getPath();
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return path;
        }

        @Override
        public String toString() {
            return "ExerciseDone [exercise=" + exercise + ", auto=" + auto
                    + "]";
        }
    }
}
