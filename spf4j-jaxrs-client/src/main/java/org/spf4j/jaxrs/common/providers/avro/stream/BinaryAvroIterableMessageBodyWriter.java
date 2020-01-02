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
package org.spf4j.jaxrs.common.providers.avro.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import org.apache.avro.Schema;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.spf4j.jaxrs.common.providers.avro.SchemaProtocol;

/**
 *
 * @author Zoltan Farkas
 */
@Provider
@Produces({"application/octet-stream;fmt=avro", "application/avro"})
public final class BinaryAvroIterableMessageBodyWriter  extends AvroIterableMessageBodyWriter {

  public BinaryAvroIterableMessageBodyWriter(final SchemaProtocol protocol) {
    super(protocol);
  }

  @Override
  public Encoder getEncoder(final Schema writerSchema, final OutputStream os) {
    return EncoderFactory.get().binaryEncoder(os, null);
  }

  @Override
  public void writeTo(final Iterable t, final Class<?> type, final Type genericType,
          final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
          final OutputStream entityStream) throws IOException {
    super.writeTo(t, type, genericType, annotations, mediaType, httpHeaders, entityStream);
  }

}
