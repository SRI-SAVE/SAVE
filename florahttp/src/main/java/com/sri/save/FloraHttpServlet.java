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

package com.sri.save;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class FloraHttpServlet extends HttpServlet {

	private FloraJsonServer florajson;
	
	/** 
	 *  
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public void init() throws ServletException {
		Properties properties = new Properties();
		try {
			ServletContext context = getServletContext();
			properties.load(context.getResourceAsStream("/WEB-INF/config.properties"));
		} catch (IOException e) {
			throw new ServletException("Could not open config.properties!");
		}
		String xsb = properties.getProperty("xsb");
		String floradir = properties.getProperty("flora.dir");
		String content_dir = properties.getProperty("ontology.dir");  // base dir for imported files

		// Initialize the server
		try{
			florajson = new FloraJsonServer(new File(xsb),new File(floradir),new File(content_dir));
		} catch (FloraJsonServerException ex){
			throw new ServletException(ex);
		}
	}
	
	@Override
	public void destroy() {
		System.out.println("Shutting down Flora Server");
		try{
			florajson.shutdown();
		} catch (FloraJsonServerException ex){
			ex.printStackTrace();
		}
	}
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		System.out.println(request);
		PrintWriter out = response.getWriter();
		response.setContentType("application/json");
		response.setHeader("Access-Control-Allow-Origin", "*");  // allows CORS
		
		
		// Dispatch the call to the Flora JSON server
		String method = request.getParameter("method");
		if (method.equals("loadFile")){                 // perhaps this should be done using POST
			String filename = request.getParameter("filename");
			try {
				florajson.loadFile(filename);
			} catch (FloraJsonServerException ex){
				throw new ServletException(ex.getMessage());
			}
		}
		else if (method.equals("getTaxonomyRoots")){
			try{
				ArrayNode result = florajson.getRootClasses();
				out.print(result.toString());
			} catch (FloraJsonServerException ex){
				throw new ServletException(ex.getMessage());
			}
		}		
		else if (method.equals("getSubClasses")){
			String id = request.getParameter("id");
			try{
				ObjectNode result = florajson.getSubClasses(id);
				out.print(result.toString());
			} catch (FloraJsonServerException ex){
				throw new ServletException(ex.getMessage());
			}
		}		
		else if (method.equals("getClassDetails")){
			String id = request.getParameter("id");
			try{
				ObjectNode result = florajson.getClassDetails(id);
				out.print(result.toString());
			} catch (FloraJsonServerException ex){
				throw new ServletException(ex.getMessage());
			}
		}		
		else if (method.equals("query")){
			String qstring = request.getParameter("queryString");
			try{
				ObjectNode result = florajson.query(qstring);
				out.print(result.toString());
			} catch (FloraJsonServerException ex){
				throw new ServletException(ex.getMessage());
			}
		}		
		else
			throw new ServletException("Unrecognized method: " + method);
	}
}
