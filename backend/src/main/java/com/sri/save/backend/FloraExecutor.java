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

import java.util.Formatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sri.floralib.reasoner.QueryResult;
import com.sri.pal.ActionDef;
import com.sri.pal.ActionExecutor;
import com.sri.pal.ActionInvocation;
import com.sri.pal.ActionInvocation.StepCommand;
import com.sri.pal.ActionStreamEvent;
import com.sri.pal.ActionStreamEvent.Status;
import com.sri.pal.PALException;

/**
 * DEFT executor backed by Flora queries. As an executor, it executes actions as
 * requested by other DEFT entities such as the assessment module. It fills the
 * action arguments into a format string to create a Flora query which is then
 * executed. The resulting value is returned as the last action parameter.
 */
public class FloraExecutor
        implements ActionExecutor {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String format;
    private final FloraWrapper flora;

    public FloraExecutor(FloraWrapper flora,
                         String format) {
        this.flora = flora;
        this.format = format;
    }

    @Override
    public void cancel(ActionStreamEvent event) {
        // Ignore
    }

    @Override
    public void continueStepping(ActionInvocation invoc,
                                 StepCommand cmd,
                                 List<Object> args)
            throws PALException {
        // Ignore
    }

    @Override
    public void execute(ActionInvocation invoc)
            throws PALException {
        ActionDef def = invoc.getDefinition();
        String name = def.getName().getSimpleName();
        if (!name.equals("checkProperty")) {
            throw new PALException("Unknown action " + name);
        }
        invoc.setStatus(Status.RUNNING);

        /*
         * Put the action invocation's input arguments into an array for use
         * with the varargs call to format.
         */
        Object[] inputs = new Object[def.numInputParams()];
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = invoc.getValue(i);
        }
        Formatter fmt = new Formatter();
        fmt.format(format, inputs);
        String querystr = fmt.toString();
        fmt.close();

        // Send the query to flora.
        QueryResult qr;
        try {
            qr = flora.query(querystr);
        } catch (Exception e) {
            throw new PALException("Flora query '" + querystr + "'", e);
        }
        boolean result = qr.isEmptySuccess();

        log.info("Flora query '{}' result: {}", querystr, result);
        invoc.setValue("result", result);
        invoc.setStatus(Status.ENDED);
    }

    @Override
    public void executeStepped(ActionInvocation action)
            throws PALException {
        execute(action);
    }
}
