/*
 * Copyright 2019 SPF4J.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spf4j.jaxrs.aql;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Reader;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.spf4j.jaxrs.IterableArrayContent;

/**
 * REST avro sql endpoint.
 * @author Zoltan Farkas
 */
@SuppressFBWarnings("JAXRS_ENDPOINT")
public interface AvroQueryResource {

  @GET
  @Produces({"application/json", "application/avro+json", "application/avro"})
  IterableArrayContent<GenericRecord> query(@QueryParam("query") String query);

  @POST
  @Produces({"application/json", "application/avro+json", "application/avro"})
  @Consumes("text/plain")
  IterableArrayContent<GenericRecord> query(Reader query);


  @GET
  @Path("plan")
  @Produces({"text/plain"})
  CharSequence plan(@QueryParam("plan") String query);

  @POST
  @Path("plan")
  @Produces({"text/plain"})
  @Consumes("text/plain")
  CharSequence plan(Reader query);


  @GET
  @Path("schemas")
  @Produces({"application/json"})
  Map<String, Schema> schemas();

  @GET
  @Path("schemas/{entityName}")
  @Produces({"application/json"})
  Schema entitySchema(@PathParam("entityName") String entityName);

}