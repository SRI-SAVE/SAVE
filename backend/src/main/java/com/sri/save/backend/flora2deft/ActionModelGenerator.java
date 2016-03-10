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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import com.sri.floralib.ast.AtomicTerm;
import com.sri.floralib.ast.FloraInt;
import com.sri.floralib.ast.FloraString;
import com.sri.floralib.ast.FloraTerm;
import com.sri.floralib.ast.HilogTerm;
import com.sri.floralib.ast.Identifier;
import com.sri.floralib.ast.TypedFloraConstant;
import com.sri.floralib.ext.lex.FloraKeywords;
import com.sri.floralib.model.ont.IOntologyModel;
import com.sri.pal.jaxb.ActionModelType;
import com.sri.pal.jaxb.ActionType;
import com.sri.pal.jaxb.CustomType;
import com.sri.pal.jaxb.EnumType;
import com.sri.pal.jaxb.MetadataType;
import com.sri.pal.jaxb.ObjectFactory;
import com.sri.pal.jaxb.ParamType;
import com.sri.pal.jaxb.RequireType;
import com.sri.pal.jaxb.TypeRef;
import com.sri.pal.jaxb.TypeType;

/** Generates a DEFT action model from a floralib IOntologyModel */
public class ActionModelGenerator implements ActionOntologyTerms {
	
	private static String getDescriptionString(FloraTerm term){
		if (term instanceof FloraString)
			return ((FloraString)term).getString();
		else if (term instanceof TypedFloraConstant && 
				((TypedFloraConstant)term).getType().equals(FloraKeywords.STRING_TYPE))
			return ((TypedFloraConstant)term).getValue();
		else
			return null;
	}
	
	private static void writeToFile(JAXBElement<ActionModelType> am, File file) throws JAXBException, FileNotFoundException{
		JAXBContext jc = JAXBContext.newInstance(ActionModelType.class.getPackage().getName());
		Marshaller m = jc.createMarshaller();
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		FileOutputStream fos = new FileOutputStream(file);
		m.marshal(am, fos);
	}
	
	/**
	 * 
	 * @param ont Model to translate
	 * @param top_file Top-level file for generated model. This should be an empty file!
	 * @param types_file Types file for generated model. This should be an empty file!
	 * @param addin_file Boilerplate file that will be imported into top_model. This will not be written to.
	 * @return
	 * @throws JAXBException 
	 * @throws FileNotFoundException 
	 */
	public static void generateActionModel(IOntologyModel<File> ont,
			File top_file, File types_file, File addin_file) throws FileNotFoundException, JAXBException{
		ObjectFactory of = new ObjectFactory();
		ActionModelType top_amt = of.createActionModelType();
		top_amt.setVersion("0.1");
		
		// A set of all types that we encounter as we go along
		Set<FloraTerm> types = new HashSet<FloraTerm>();
		
		// Translate the actions
		SortedSet<Identifier> actions = new TreeSet<>();
		actions.addAll(ont.getInstances(actionTypeCls));
		for (Identifier action : actions){
			// Create the action entry
			ActionType at = of.createActionType();

			// Set the 'id' attribute to the class name
			at.setId(action.toString());
			
			// Set the 'description'
			FloraTerm description = ont.getIndividualPropertyValue(action, descriptionProp, true);
			String dstr = getDescriptionString(description);
			if (dstr != null)
				at.setDescription(dstr);

			// For now, use the description as the name
			MetadataType mt1 = of.createMetadataType();
			mt1.setKey("name");
			mt1.setValue(dstr);
			at.getMetadata().add(mt1);

			// Set the 'fancyName'
			FloraTerm fName = ont.getIndividualPropertyValue(action, fancyNameProp, true);
			String fstr = getDescriptionString(fName);
			if (fstr != null){
				MetadataType mt2 = of.createMetadataType();
				mt2.setKey("fancyName");
				mt2.setValue(fstr);
				at.getMetadata().add(mt2);
			}

			// Add the action parameters
			Identifier methodId = null;
			int param_index = 1;
			while(true){
				HilogTerm arg_prop = new HilogTerm(new AtomicTerm("arg"), 
						Arrays.asList((FloraTerm)new FloraInt(param_index)));
				methodId = (Identifier)ont.getIndividualPropertyValue(action, arg_prop, true);
				if (methodId == null)
					break;
					
				// Create the inputParam entry
				ParamType param = of.createParamType();
				
				// Set the 'id' attribute to the term name
				param.setId(methodId.toString());
				
				// Set the 'description' of the parameter
				FloraTerm pdescription = ont.getIndividualPropertyValue(methodId, descriptionProp, true);
				String pdstr = getDescriptionString(pdescription);
				if (pdstr != null)
					param.setDescription(pdstr);

				// Get the range of the parameter
				FloraTerm floratype = ont.getClassPropertyValue(action, methodId, true);
				types.add(floratype);
				
				// Use the range as the type of the parameter
				TypeRef tref = of.createTypeRef();
				tref.setTypeId(floratype.toString());
				param.setTypeRef(tref);
				
				// Add the param to the action
				at.getInputParam().add(param);
				
				param_index++;
			}
			
			// Add the action to the action model
			top_amt.getAction().add(at);
			
		}

		// Create the type definitions
		ActionModelType types_amt = of.createActionModelType();
		types_amt.setVersion("0.1");
		for (FloraTerm typeterm : types){
			if (typeterm instanceof Identifier){
				Identifier typeid = (Identifier)typeterm;
				TypeType type = of.createTypeType();
				type.setId(typeterm.toString());

				// Set the 'description' of the type
				FloraTerm tdescription = ont.getIndividualPropertyValue((Identifier)typeterm, descriptionProp, true);
				String tstr = getDescriptionString(tdescription);
				if (tstr != null)
					type.setDescription(tstr);

				// Check if it is an enumerated type
				if (ont.isInstanceOf(typeid,enumeratedTypeCls)){
					EnumType etype = of.createEnumType();
					// Add the values of the enum
					for (Identifier enumInstance : ont.getInstances(typeid)){
						etype.getValue().add(enumInstance.toString()); 
					}
					type.setEnum(etype);
				}
				else{
					// Use Java String to represent the type
					CustomType custom = of.createCustomType();
					custom.setJavaType(String.class.getName());
					type.setCustom(custom);
				}

				types_amt.getType().add(type);
			}
		}
		
		// Import types and addin into top
		RequireType rt_types = of.createRequireType();
		rt_types.setUrl(types_file.getName());
		top_amt.getRequire().add(rt_types);
		
		RequireType rt_addin = of.createRequireType();
		rt_addin.setUrl(addin_file.getName());
		top_amt.getRequire().add(rt_addin);
		
		// Create the action model files		
		JAXBElement<ActionModelType> top_am = of.createActionModel(top_amt);
		writeToFile(top_am, top_file);
		
		JAXBElement<ActionModelType> types_am = of.createActionModel(types_amt);
		writeToFile(types_am, types_file);
	}
	

}
