package org.spf4j.jaxrs.common.avro;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.inject.Inject;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import org.apache.avro.Schema;
import org.apache.avro.SchemaResolver;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.ExtendedJsonEncoder;

/**
 * @author Zoltan Farkas
 */
@Provider
@Produces({"application/json;fmt=avro-x", "application/avro-x+json", "text/plain;fmt=avro-x+json"})
public class JsonAvroMessageBodyWriter extends  AvroMessageBodyWriter {

  @Inject
  public JsonAvroMessageBodyWriter(final SchemaResolver client) {
    super(client);
  }

  @Override
  public Encoder getEncoder(Schema writerSchema, OutputStream os) throws IOException {
    return new ExtendedJsonEncoder(writerSchema, os);
  }

  @Override
  public void writeTo(Object t, Class<?> type, Type genericType, Annotation[] annotations,
          MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
          throws IOException, WebApplicationException {
    httpHeaders.putSingle(HttpHeaders.CONTENT_TYPE, "application/avro-x+json");
    super.writeTo(t, type, genericType, annotations, mediaType, httpHeaders, entityStream);
  }




}
