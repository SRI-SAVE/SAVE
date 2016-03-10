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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

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

import com.sri.pal.ActionStreamEvent;
import com.sri.pal.Bridge;
import com.sri.pal.GlobalActionListener;
import com.sri.pal.Learner;
import com.sri.pal.PALException;
import com.sri.pal.training.core.exercise.Exercise;
import com.sri.pal.training.core.exercise.Option;
import com.sri.pal.training.core.exercise.Solution;
import com.sri.pal.training.core.exercise.Task;
import com.sri.pal.training.core.exercise.TaskSolution;
import com.sri.pal.training.core.storage.ExerciseFactory;
import com.sri.save.backend.Backend;

/**
 * Receives a request to generate a raw solution to the exercise, by learning
 * from the user's demonstration (which has already been sent). Calls LAPDOG to
 * do the learning, then writes the result into the exercise XML file.
 */
public class SolutionHandler
        extends AbstractHandler
        implements ResettableActionListener {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Learner learner;
    private final Exercise exercise;
    private final File exerciseFile;
    private final ActionListener actionListener;
    private final Backend backend;

    public SolutionHandler(Backend backend,
                           Exercise exercise,
                           File exerciseFile) {
        this.backend = backend;
        Bridge bridge = backend.getBridge();
        learner = bridge.getLearner();
        this.exercise = exercise;
        this.exerciseFile = exerciseFile;
        actionListener = new ActionListener();
        bridge.addActionListener(actionListener);
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

        baseRequest.setHandled(true);

        // Allow CORS from anywhere.
        response.setHeader("Access-Control-Allow-Origin", "*");

        // Call LAPDOG to learn the option.
        ActionStreamEvent[] demo = actionListener.getActions();
        log.debug("Learning a new option from the demonstration {}", demo);
        Option option;
        try {
            option = learner.learnOption(demo);
        } catch (PALException e) {
            log.info("Failed to learn option", e);
            try (StringWriter sw = new StringWriter();
                    PrintWriter out = new PrintWriter(sw)) {
                e.printStackTrace(out);
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        sw.toString());
            }
            return;
        }

        // Add the option into the existing exercise.
        Solution solution = new Solution();
        exercise.setSolution(solution);
        TaskSolution taskSolution = new TaskSolution();
        solution.getTaskSolutions().add(taskSolution);
        taskSolution.setOption(option);
        Task task = exercise.getProblem().getTasks().get(0);
        taskSolution.setTask(task);

        // Write it back to the same file we got it from.
        JAXBElement<Exercise> jaxbExer = ExerciseFactory
                .createExercise(exercise);
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

        // Notify the repo scanner that we've modified this file.
        backend.getRepoScanner().scan(exerciseFile);

        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    public void reset() {
        actionListener.reset();
    }

    private class ActionListener
            implements GlobalActionListener {
        private final List<ActionStreamEvent> actions = new ArrayList<>();

        @Override
        public synchronized void actionStarted(ActionStreamEvent action) {
            actions.add(action);
        }

        public synchronized ActionStreamEvent[] getActions() {
            return actions.toArray(new ActionStreamEvent[0]);
        }

        public void reset() {
            actions.clear();
        }
    }
}
