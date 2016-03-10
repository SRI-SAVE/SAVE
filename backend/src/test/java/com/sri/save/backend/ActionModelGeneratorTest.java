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

import java.io.File;
import java.nio.file.Files;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.sri.floralib.ast.FloraDocument;
import com.sri.floralib.model.doc.FileFloraDocumentService;
import com.sri.floralib.model.ont.FileOntologyService;
import com.sri.floralib.model.ont.IOntologyModel;
import com.sri.pal.ActionModel;
import com.sri.pal.jaxb.ActionModelType;
import com.sri.save.backend.flora2deft.ActionModelGenerator;

public class ActionModelGeneratorTest {

	private static String contentDir;
	private static File testDir;

	private static FileFloraDocumentService docService;
	private static FileOntologyService ontService;
	
	
	@BeforeClass
	public static void setUp() throws Exception {
    	contentDir = "../repos/knowledge/weapons/M4";
    	testDir = Files.createTempDirectory("SAVE").toFile();

		// Setup the document and ontology services
		docService = new FileFloraDocumentService(new File(contentDir));
		ontService = new FileOntologyService(docService);  // we'll use this later, below
	}

	@AfterClass
	public static void cleanup() throws Exception{
	    SystemTest.deleteAll(testDir);
	}

	    @Test
	public void testActionModelGenerator() throws Exception {
		String FLORA_KB = contentDir + "/m4.flr";

		// Set the active file
		File floraKBFile = new File (FLORA_KB);
		docService.setActiveFile(floraKBFile);
		
		FloraDocument doc = docService.getActiveDocument();
		Assert.assertNotNull(doc);
		
		// Get the ontology model for the active file		
		IOntologyModel<File> ont = ontService.getActiveOntologyModel();
		
		Assert.assertNotNull(ont);

		File top_file = new File(testDir + "/m4_gen.xml");
		File types_file = new File(testDir + "/m4_types_gen.xml");
		File addin_file = new File(contentDir + "/m4_create.xml");
		
		ActionModelGenerator.generateActionModel(ont, top_file, types_file, addin_file);

        JAXBContext jc = JAXBContext.newInstance(ActionModelType.class
                .getPackage().getName());
        Unmarshaller unmarsh = jc.createUnmarshaller();
        ActionModelType am = Backend.jaxbReader(top_file.toURI().toURL(),
                ActionModel.class.getResource("ActionModel.xsd"), unmarsh,
                ActionModelType.class);
        Assert.assertTrue(am.getAction().size() > 10);
	}
	
}
