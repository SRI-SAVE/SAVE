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

package com.sri.save.backend.flora2deft;

import com.sri.floralib.ast.AtomicTerm;

/**
 * FloraTerm instances for identifiers in the flora action ontology.
 * 
 * @author elenius
 *
 */
public interface ActionOntologyTerms {

	public final static AtomicTerm actionTypeCls = new AtomicTerm("ActionType");
	public final static AtomicTerm enumeratedTypeCls = new AtomicTerm("EnumeratedType");
	public final static AtomicTerm actionParameterCls = new AtomicTerm("ActionParameter");
	public final static AtomicTerm descriptionProp = new AtomicTerm("description");
	public final static AtomicTerm fancyNameProp = new AtomicTerm("fancyName");
	
}
