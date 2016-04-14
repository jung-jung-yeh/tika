/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.ner.grobidquantity;

import org.apache.tika.parser.ner.NERecogniser;

import org.json.simple.parser.JSONParser;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Properties;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.WebClient;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;


public class GrobidQuantityNERecogniser implements NERecogniser {

  private static final Logger LOG = LoggerFactory.getLogger(GrobidQuantityNERecogniser.class);
  private static final String GROBID_REST_HOST = "http://localhost:8080";

  private static final String GROBID_ISALIVE_PATH = "/index.html";

  private static final String GROBID_PROCESSHEADER_PATH = "/processQuantityText";

  private static String restHostUrlStr;
  private static String restHeaderUrlStr;
  
  
  public static final Set<String> ENTITY_TYPES = new HashSet<>();

  static {
  	   ENTITY_TYPES.add("length");
	   ENTITY_TYPES.add("mass");
	   ENTITY_TYPES.add("time");
	   ENTITY_TYPES.add("electric_current");
	   ENTITY_TYPES.add("temperature");
	   ENTITY_TYPES.add("luminous_intensity");
	   ENTITY_TYPES.add("amount_of_substance");
	   ENTITY_TYPES.add("concentration");
	   ENTITY_TYPES.add("volume");
	   ENTITY_TYPES.add("voltage");
	   ENTITY_TYPES.add("frequency");
	   ENTITY_TYPES.add("velocity");
	   ENTITY_TYPES.add("density");
	   ENTITY_TYPES.add("power");
  }

  public GrobidQuantityNERecogniser() {
	
    String restHostUrlStr = null;
    try {
      restHostUrlStr = readRestUrl();
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (restHostUrlStr == null
        || (restHostUrlStr != null && restHostUrlStr.equals(""))) {
      this.restHostUrlStr = GROBID_REST_HOST;
    } 
    else {
      this.restHostUrlStr = GROBID_REST_HOST;
      restHeaderUrlStr = GROBID_PROCESSHEADER_PATH;
    }
  }

  /* 
   * TODO: read RestUrl from config
   * 
   */
  private static String readRestUrl() throws IOException {
	  return GROBID_REST_HOST;
  }
  	/**
  	 * Return whether GrobidQuantity is available
  	 */
	@Override
	public boolean isAvailable() {
		Response response = null;
		try
		{
	      response = WebClient.create(readRestUrl() + GROBID_ISALIVE_PATH)
	          .accept(MediaType.TEXT_HTML).get();
	      String resp = response.readEntity(String.class);
	      return resp != null && !resp.equals("") && resp.startsWith("<!DOCTYPE XHTML>");
	    } 
		catch (Exception e) 
		{
	      e.printStackTrace();
	      return false;
	    }
	}
	
	@Override
	public Set<String> getEntityTypes() {
		return ENTITY_TYPES;
	}
	
	@Override
	public Map<String, Set<String>> recognise(String text) {
		Map<String, Set<String>> measurements = new HashMap<>();
		try 
		{
            String url = restHostUrlStr + restHeaderUrlStr+ "?text=" + URLEncoder.encode(text,"UTF-8");
            Response response = WebClient.create(url).accept(MediaType.APPLICATION_JSON).get();
            // Request Error Response
            if ( response.getStatus() != 200 )
            {
            	LOG.warn("Response code is " + response.getStatus() );
            	return null;
            }
            String resultString = response.readEntity(String.class);
            if ( resultString == null || resultString == "" )
            {
            	return null;
            }
            JSONObject JsonResult = StringtoJson( resultString );
            if ( JsonResult == null )
            {
            	return null;
            }
            //Get measurements
            if (!JsonResult.containsKey("measurements"))
            {
            	return null;
            }
            JSONArray results = new JSONArray();
            
            results = (JSONArray) JsonResult.get("measurements");
            if ( results.isEmpty() )
            {
            	return null;
            }
            Iterator iter = results.iterator();
            while( iter.hasNext() )
            {
            	JSONObject cur = (JSONObject) iter.next();
            	JSONObject quantity = (JSONObject) cur.get("quantity");
            	if ( quantity != null )
            	{
            		if ( !quantity.containsKey("type") )
            		{
            			continue;
            		}
            		String type = quantity.get("type").toString();
            		if (!measurements.containsKey(type)) {
            			measurements.put(type, new HashSet<String>());
                    }
            		if ( !quantity.containsKey("rawValue") )
            		{
            			continue;
            		}
            		String value = quantity.get("rawValue").toString();
            		if ( !quantity.containsKey("rawUnit") )
            		{
            			continue;
            		}
            		JSONObject rawunits = (JSONObject) quantity.get("rawUnit");
            		if ( !rawunits.containsKey("name") )
            		{
            			continue;
            		}            		
            		String rawunits_name = rawunits.get("name").toString();
            		
            		measurements.get(type).add(value + " " + rawunits_name );
            		System.out.println(type + ":" + value + " " + rawunits_name);
                }
            }
            
	            
		}catch(Exception e){
			LOG.error(e.getMessage(), e);
		}
		 
		return measurements;
	}
	/**
	 * convert string to JSONObject
	 * @param JsonString
	 * @return
	 */
	private JSONObject StringtoJson(String JsonString){
    	JSONObject JsonObject = new JSONObject();
    	try{
    		JsonObject = (JSONObject)  new JSONParser().parse(JsonString);
    	}
    	catch(Exception e){
    	   	LOG.error(e.getMessage(), e);
        }
		return JsonObject;
    }
}
