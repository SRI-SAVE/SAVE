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

package com.sri.save.backend;

import java.util.List;

import com.sri.pal.ActionDef;
import com.sri.pal.ActionExecutor;
import com.sri.pal.ActionInvocation;
import com.sri.pal.ActionInvocation.StepCommand;
import com.sri.pal.ActionStreamEvent;
import com.sri.pal.ActionStreamEvent.Status;
import com.sri.pal.PALException;
import com.sri.pal.common.ErrorInfo;
import com.sri.pal.common.SimpleTypeName;

/**
 * Handles query constraints coming back from the assessment engine, which can
 * be executed here in the gateway rather than going all the way to the UI.
 * Query constraints can take any number of inputs, but must return a single
 * boolean output.
 */
public class LocalQueryExecutor
        implements ActionExecutor {

    @Override
    public void cancel(ActionStreamEvent runningAction) {
        // Ignore.
    }

    @Override
    public void continueStepping(ActionInvocation invocation,
                                 StepCommand command,
                                 List<Object> actionArgs)
            throws PALException {
        // Ignore.
    }

    @Override
    public void executeStepped(ActionInvocation invocation)
            throws PALException {
        // Ignore.
    }

    @Override
    public void execute(ActionInvocation invocation)
            throws PALException {
        ActionDef def = invocation.getDefinition();
        SimpleTypeName name = def.getName();
        if (name.getSimpleName().equals("lessThanOrEqualTo")) {
            lessThanOrEqualTo(invocation);
        } else {
            String strerror = "unrecognized action: " + name;
            ErrorInfo ei = new ErrorInfo("gateway", 1, strerror, strerror, null);
            invocation.error(ei);
        }
    }

    private void lessThanOrEqualTo(ActionInvocation invoc) {
        invoc.setStatus(Status.RUNNING);
        Object obj0 = invoc.getValue(0);
        Object obj1 = invoc.getValue(1);
        if (!(obj0 instanceof Number)) {
            String strerror = "Arg 0 should be Number, not "
                    + obj0.getClass().getName();
            ErrorInfo ei = new ErrorInfo("gateway", 1, strerror, strerror, null);
            invoc.error(ei);
            return;
        }
        if (!(obj1 instanceof Number)) {
            String strerror = "Arg 1 should be Number, not "
                    + obj1.getClass().getName();
            ErrorInfo ei = new ErrorInfo("gateway", 1, strerror, strerror, null);
            invoc.error(ei);
            return;
        }
        double int0 = ((Number) obj0).doubleValue();
        double int1 = ((Number) obj1).doubleValue();
        boolean result = int0 <= int1;
        invoc.setValue(2, result);
        invoc.setStatus(Status.ENDED);
    }
}
