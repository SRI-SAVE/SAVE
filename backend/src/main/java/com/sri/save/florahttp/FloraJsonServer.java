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
package com.sri.save.florahttp;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sri.floralib.ast.FloraTerm;
import com.sri.floralib.ast.FrameTriple;
import com.sri.floralib.ast.FrameTripleWithCardinalities;
import com.sri.floralib.ast.Identifier;
import com.sri.floralib.ast.NamespaceMapping;
import com.sri.floralib.ext.interprolog.FlrException;
import com.sri.floralib.model.doc.FileFloraDocumentService;
import com.sri.floralib.model.ont.FileOntologyService;
import com.sri.floralib.model.ont.FrameTripleAssertion;
import com.sri.floralib.model.ont.IOntologyModel;
import com.sri.floralib.parser.FloraParser;
import com.sri.floralib.reasoner.IFloraEngine;
import com.sri.floralib.reasoner.QueryResult;
import com.sri.floralib.util.JsonUtils;
import com.sri.save.backend.FloraWrapper;

/** Main API for SAVE and other HTTP clients.
 * 
 * FloraJsonServer can only load one ontology at a time.
 * This class is (in theory, at least) thread-safe, but the reasoning itself is not threaded, i.e. it is blocking. 
 * 
 * This class is a thin layer on top of FloraEngine, FileFloraDocumentService, and FileOntologyService.
 * The main responsibility for this class is to translate between JSON and the internal FloraTerm
 * representation, and providing some convenience for dealing with the underlying "service" classes. 
 * Not all floralib functionality is exposed. More features can be added as needed.
 * 
 * @author elenius
 *
 */
public class FloraJsonServer {
    private final Logger logger = Logger.getLogger(getClass());

    private final FloraWrapper floraWrapper;
    private final URL ontBase;
    private final ObjectMapper om;
    private final IFloraEngine engine;
    private final FileFloraDocumentService docService;
    private final FileOntologyService ontService;

    public FloraJsonServer(FloraWrapper floraWrapper,
                           URL ontBase)
            throws IOException,
            FlrException {
        this.floraWrapper = floraWrapper;
        this.ontBase = ontBase;
        om = new ObjectMapper();
        engine = floraWrapper.getEngine();
        docService = floraWrapper.getDocumentService();
        ontService = new FileOntologyService(docService);
    }

	/**
	 * Loads the given file into the main module, in addition to any previously loaded file.
	 * Sets the given file as the active file.
     *
	 * Note: The file should import any other files that it depends on. Import paths are
	 * relative to the contentdir that FloraJsonServer was created with.
	 *
	 * @param filename JSON node containing the filename to be loaded, relative to the content
	 * dir that this FloraJsonServer was created with.
	 * 
	 */
	public BooleanNode loadFile(String filename) throws FloraJsonServerException {
		logger.debug("loadFile: " + filename);

        try {
            URL url = new URL(ontBase, filename);
            floraWrapper.load(url);
            return BooleanNode.TRUE;
        } catch (Exception ex) {
            throw new FloraJsonServerException(ex.getMessage());
        }
	}
	
	/**
	 * loadFile() must be called before calling this method, to load *some* flora file.
	 * 
	 * @return A JSON array containing all the top-level classes in the current ontology.
	 * Use getSubClasses to expand the hierarchy. The members of the array are strings.
	 * 
	 */
	public ArrayNode getRootClasses() throws FloraJsonServerException {
		logger.debug("getRootClasses");
		IOntologyModel<File> ontModel = ontService.getActiveOntologyModel();
		if (ontModel == null)
			throw new FloraJsonServerException("No active ontology model found!");
		Set<Identifier> rootClasses = ontModel.getAssertedRootClasses();
		List<FloraTerm> roots = new ArrayList<FloraTerm>(rootClasses);
		ObjectMapper om = new ObjectMapper();		
		return JsonUtils.floraTermListToJsonStrings(roots,om); 
	}
	
	private ObjectNode getSubClasses(FloraTerm clsterm) throws FloraJsonServerException {
		IOntologyModel<File> ontModel = ontService.getActiveOntologyModel();
		if (!(clsterm instanceof Identifier))
			throw new FloraJsonServerException("Parameter must have an Identifier termtype");

		Identifier clsid = (Identifier)clsterm;
		Set<Identifier> subClasses = ontModel.getSubclasses(clsid,false,true,true);
		List<FloraTerm> subs = new ArrayList<FloraTerm>(subClasses);

		ObjectNode result = om.createObjectNode();
		result.put("superclass", clsterm.toString());
		result.set("subclasses", JsonUtils.floraTermListToJsonStrings(subs,om));
		
		return result; 
	}

	/**
	 * 
	 * @param cls A string representing an Identifier
	 * @return A JSON object with:
	 *   - a "superclass" property containing the superclass, as a string
	 *   - a "subclasses" property containing an array with the subclasses of the given class, as strings
     *
	 */
	public ObjectNode getSubClasses(String cls) throws FloraJsonServerException {
		logger.debug("getSubClasses " + cls);
		try{
			FloraTerm clsterm = FloraParser.parseTerm(cls,getCurrentNamespaceMapping());
			return getSubClasses(clsterm);
		} catch (Exception ex){
			throw new FloraJsonServerException(ex.getMessage());
		}
	}
	
	private ArrayNode triples2ArrayNode(Collection<FrameTripleAssertion<File>> triples){
		List<ObjectNode> props = new ArrayList<ObjectNode>();
		for (FrameTripleAssertion<File> tr : triples){
			FrameTriple ft = tr.getFrameTriple();
			for (FloraTerm value : ft.getValues()){
				ObjectNode entry = om.createObjectNode();
				entry.put("property", ft.getMethod().toString());
				entry.put("value", value.toString());
				entry.put("kind", ft.getArrow().toString());
				if (ft instanceof FrameTripleWithCardinalities){
					FrameTripleWithCardinalities ftc = (FrameTripleWithCardinalities)ft;
					entry.put("min", ftc.getMinCardinality());
					entry.put("max", ftc.getMaxCardinality());
				}
				entry.put("assertedIn", tr.getSubject().toString());
				entry.put("file", tr.getSource().getSource().getName());
				props.add(entry);
			}
		}
		ArrayNode arr = om.createArrayNode();
		arr.addAll(props);
		return arr;
	}
	
	private ObjectNode getClassDetails(FloraTerm term){
		IOntologyModel<File> ontModel = ontService.getActiveOntologyModel();

		ObjectNode result = om.createObjectNode();
		result.put("id", term.toString());

		if (term instanceof Identifier){
			Identifier clsid = (Identifier)term;
			
			// Add the superclasses
			Set<Identifier> superClasses = ontModel.getSuperclasses(clsid,false,false,true);
			List<FloraTerm> sups = new ArrayList<FloraTerm>(superClasses);
			result.set("superclasses", JsonUtils.floraTermListToJsonStrings(sups,om));

			// Add the class properties
			result.set("classproperties", triples2ArrayNode(ontModel.getClassPropertyValues(clsid, true, true)));
			
			/* TODO: Add these
		public Collection<Triple<IOntologySource<SourceType>, Identifier, BooleanMethodSpec>> 
			getClassBooleanProperties(Identifier cls, boolean inherited, boolean equivalenceClasses); 

		public Collection<Triple<IOntologySource<SourceType>, Identifier, BooleanValue>> 
			getClassBooleanValues(Identifier cls, boolean inherited, boolean equivalenceClasses); 
			*/

			// Add the types
			Set<Identifier> typeClses = ontModel.getTypes(clsid);
			List<FloraTerm> types = new ArrayList<FloraTerm>(typeClses);
			result.set("types", JsonUtils.floraTermListToJsonStrings(types,om));
			
			// Add the individual properties
			result.set("individualproperties", triples2ArrayNode(ontModel.getIndividualPropertyValues(clsid, true, true,false)));

			/* TODO: Add these
			public Collection<Triple<IOntologySource<SourceType>, Identifier, BooleanMethodSpec>> 
			getIndividualBooleanProperties(Identifier cls, boolean inherited, boolean equivalenceClasses, boolean assertedOnly); 

		public Collection<Triple<IOntologySource<SourceType>, Identifier, BooleanValue>> 
			getIndividualBooleanValues(Identifier cls, boolean inherited, boolean equivalenceClasses, boolean assertedOnly); 
			 */
			
		}
		else
			logger.warn("Class term is not an Identifier: " + term.toString());
			
		// TODO
		return result;
	}

	/**
	 * 
	 * @param cls A string representing an Identifier
	 * @return The details of the the given class. If there is an error, a JSON text node is returned with the
	 * error message.
	 */
	public ObjectNode getClassDetails(String cls) throws FloraJsonServerException {
		try{
			FloraTerm clsterm = FloraParser.parseTerm(cls,getCurrentNamespaceMapping());
			return getClassDetails(clsterm);
		} catch (Exception ex){
			throw new FloraJsonServerException(ex.getMessage());
		}
	}

	private NamespaceMapping getCurrentNamespaceMapping(){
	    return floraWrapper.getNamespaceMapping();
	}

	/**
	 * Send a query, in plain text form, to the reasoner. Prefixes are as defined in the current
	 * NamespaceMapping.
	 * 
	 * @param q The query string. Any variables in q will be shown in the query result.
	 * @return a "QueryResult" JSON node.
	 */
	public ObjectNode query(String q) throws FloraJsonServerException {
		try{
			NamespaceMapping nm = getCurrentNamespaceMapping();
			FloraTerm queryTerm = FloraParser.parseTerm(q, nm);
			QueryResult qr = engine.floraQuery(queryTerm, nm);
			ObjectNode result = qr.toJson(om);
			return result;
		} catch (Exception ex){
			ex.printStackTrace();
			throw new FloraJsonServerException(ex.getMessage());
		}
	}

	public BooleanNode command(String cmd) throws FloraJsonServerException {
		try{
			boolean result = engine.floraCommand(cmd);
			if (result)
				return BooleanNode.TRUE;
			else
				return BooleanNode.FALSE;				
		} catch (Exception ex){
			ex.printStackTrace();
			throw new FloraJsonServerException(ex.getMessage());
		}
	}
}
