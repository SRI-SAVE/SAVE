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
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.sri.floralib.ast.Arrow;
import com.sri.floralib.ast.ArrowType;
import com.sri.floralib.ast.AtomicTerm;
import com.sri.floralib.ast.FloraTerm;
import com.sri.floralib.ast.FloraVar;
import com.sri.floralib.ast.FrameTerm;
import com.sri.floralib.ast.FrameTriple;
import com.sri.floralib.ast.InstanceFrame;
import com.sri.floralib.ast.NamespaceMapping;
import com.sri.floralib.parser.FloraParser;
import com.sri.floralib.reasoner.FloraInterprologEngine;
import com.sri.floralib.reasoner.IFloraEngine;
import com.sri.floralib.reasoner.QueryResult;
import com.sri.floralib.reasoner.Solution;

public class GetComponentsAndTypesTest {

	private static String contentDir;

	@BeforeClass
	public static void setUp() throws Exception {
    	contentDir = "../repos/knowledge/weapons/M4";
	}

	@Test
	public void testGetComponentsAndTypes() throws Exception {
		String FLORA_KB = contentDir + "/m4.flr";

		Properties props = new Properties();
		try (InputStream is = new FileInputStream(new File("backend.properties"))) {
			props.load(is);
		}
        // Start the flora engine
        String xsb = props.getProperty(Backend.XSB_KEY);
        if (xsb == null) {
            throw new IllegalArgumentException("Property '" + Backend.XSB_KEY
                    + "' must point to xsb.");
        }
        String floraDir = props.getProperty(Backend.FLORA_KEY);
        if (floraDir == null) {
            throw new IllegalArgumentException("Property '" + Backend.FLORA_KEY
                    + "' must point to Flora-2.");
        }
        File contentDirFile = new File(contentDir);
		IFloraEngine engine = new FloraInterprologEngine(xsb, floraDir, contentDirFile.getAbsolutePath());
		
		// Load the m4 ontology into the flora engine
		engine.loadFile(FLORA_KB, "main");
		
		// The namespace mapping doesn't matter here because we don't have any prefixes
		NamespaceMapping nm = new NamespaceMapping();
		
		// Use flora to create an m4 instance
		QueryResult m4qr = engine.floraQuery(FloraParser.parseTerm("%create(M4,?m4)"), nm);
		FloraTerm m4 = m4qr.getSolutions().get(0).getBinding("?m4");
		
		// Ask flora for all the components of the M4
		FloraTerm hasCompQueryTerm = new FrameTerm(m4,  // we can also create flora terms manually... 
				new InstanceFrame(
						new FrameTriple(
								new AtomicTerm("hasComponent"),
								new Arrow(ArrowType.value),
								new FloraVar("?c"))));
		QueryResult compsqr = engine.floraQuery(hasCompQueryTerm,nm);

		// For each component solution, find its types
		for (Solution sol : compsqr.getSolutions()){
			FloraTerm comp = sol.getBinding("?c");
			QueryResult ctypes = engine.floraQuery(FloraParser.parseTerm("first_direct_type(" + comp + ",?t)"), nm);
			System.out.println("Component: " + comp + ", types: " + ctypes);
		}

	}
	
}
