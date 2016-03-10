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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sri.save.backend.http.HttpUtil;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sri.floralib.ext.interprolog.FlrException;
import com.sri.save.backend.FloraWrapper;

public class FloraHttpServlet
        extends AbstractHandler {
    private final FloraJsonServer florajson;

    public FloraHttpServlet(FloraWrapper floraWrapper,
                            URL ontBase)
            throws IOException,
            FlrException {
        florajson = new FloraJsonServer(floraWrapper, ontBase);
    }

    @Override
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response)
            throws IOException,
            ServletException {
		if (request.getMethod().equals("OPTIONS")) {
			baseRequest.setHandled(true);
			HttpUtil.corsOptions(request, response, true);
			return;
		}

		baseRequest.setHandled(true);
		PrintWriter out = response.getWriter();
		response.setContentType("application/json");
		response.setHeader("Access-Control-Allow-Origin", "*");  // allows CORS
		
		
		// Dispatch the call to the Flora JSON server
		String method = request.getParameter("method");
		if(method==null) {
		    throw new ServletException("Missing method parameter");
		} else if (method.equals("loadFile")){                 // perhaps this should be done using POST
			String filename = request.getParameter("filename");
			try {
				florajson.loadFile(filename);
				out.print("true");
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
