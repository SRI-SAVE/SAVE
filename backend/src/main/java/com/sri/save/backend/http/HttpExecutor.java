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

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sri.pal.ActionExecutor;
import com.sri.pal.ActionInvocation;
import com.sri.pal.ActionInvocation.StepCommand;
import com.sri.pal.ActionStreamEvent;
import com.sri.pal.PALException;

/**
 * Sits on the DEFT side of the DEFT/UI interaction. Receives action execution
 * requests from DEFT (including callbacks for query actions from the pattern
 * matcher in the assessment module).
 */
public class HttpExecutor
        implements ActionExecutor {
    private final ConcurrentLinkedQueue<ActionInvocation> pendingCallbacks;

    public HttpExecutor() {
        pendingCallbacks = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void execute(ActionInvocation invocation)
            throws PALException {
        pendingCallbacks.add(invocation);
    }

    public ActionInvocation poll() {
        return pendingCallbacks.poll();
    }

    @Override
    public void executeStepped(ActionInvocation invocation)
            throws PALException {
        execute(invocation);
    }

    @Override
    public void cancel(ActionStreamEvent event) {
        // Ignore.
    }

    @Override
    public void continueStepping(ActionInvocation invocation,
                                 StepCommand command,
                                 List<Object> actionArgs)
            throws PALException {
        // Ignore.
    }
}
