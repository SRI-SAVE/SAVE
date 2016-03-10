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

import java.util.Arrays;

import com.sri.pal.ActionDef;
import com.sri.pal.ActionInvocation;
import com.sri.pal.ActionModel;
import com.sri.pal.ActionStreamEvent.Status;
import com.sri.pal.PALException;
import com.sri.pal.common.SimpleTypeName;
import com.sri.pal.common.TypeNameFactory;

/**
 * JSON representation of a single action invocation. The JSON format is used to
 * communicate with the UI.
 */
public class JsonAction {
    public String action;
    public Object[] arguments;

    /**
     * Create an empty action.
     */
    public JsonAction() {
    }

    /**
     * Create an action which duplicates the parameter values in the given DEFT
     * action.
     */
    public JsonAction(ActionInvocation act) {
        ActionDef def = act.getDefinition();
        action = def.getName().getSimpleName();
        int size = def.size();
        if (act.getStatus() == Status.CREATED) {
            size = def.numInputParams();
        }
        arguments = new Object[size];
        for (int i = 0; i < size; i++) {
            arguments[i] = act.getValue(i);
        }
    }

    /**
     * Convert this JSON action into a DEFT action.
     * 
     * @param am
     *            the action model to retrieve definitions from.
     * @param version
     *            the version of the application's action model to retrieve
     *            definitions from.
     * @param namespace
     *            the application namespace to retrieve definitions from.
     * @return a new DEFT action with all argument values set according to the
     *         contents of this JSON object.
     * @throws PALException
     *             if the specified action can't be created in DEFT.
     */
    public ActionInvocation toDeft(ActionModel am,
                                   String version,
                                   String namespace)
            throws PALException {
        SimpleTypeName name = (SimpleTypeName) TypeNameFactory.makeName(action,
                version, namespace);
        ActionDef ad = (ActionDef) am.getSimpleType(name);
        if (ad == null) {
            throw new PALException("No type " + name);
        }
        return toDeft(ad);
    }

    public ActionInvocation toDeft(ActionDef ad)
            throws PALException {
        ActionInvocation ai = ad.invoke(null);
        for (int i = 0; i < arguments.length; i++) {
            ai.setValue(i, arguments[i]);
        }
        return ai;
    }

    /**
     * Update a DEFT action's argument values according to the values in this
     * JSON action.
     * 
     * @param action
     */
    public void updateDeft(ActionInvocation action) {
        ActionDef def = action.getDefinition();
        SimpleTypeName name = def.getName();
        if (!name.getSimpleName().equals(action)) {
            throw new IllegalStateException("Can't update DEFT " + name
                    + " with JSON " + action);
        }
        if (action.getStatus() == Status.CREATED) {
            // Only update the input parameters if the action isn't running yet.
            for (int i = 0; i < def.numInputParams() && i < arguments.length; i++) {
                action.setValue(i, arguments[i]);
            }
        }
        // Update the output parameters regardless.
        for (int i = def.numInputParams(); i < def.size()
                && i < arguments.length; i++) {
            action.setValue(i, arguments[i]);
        }
    }

    @Override
    public String toString() {
        return "JsonAction [action=" + action + ", arguments="
                + Arrays.toString(arguments) + "]";
    }
}
