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
package org.spf4j.jaxrs.common.providers.avro;

import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;

/**
 *
 * @author Zoltan Farkas
 */
public final class DecodedSchema {

  private final Schema schema;

  private final Decoder decoder;

  public DecodedSchema(final Schema schema, final Decoder decoder) {
    this.schema = schema;
    this.decoder = decoder;
  }

  public Schema getSchema() {
    return schema;
  }

  public Decoder getDecoder() {
    return decoder;
  }

  @Override
  public String toString() {
    return "DecodedSchema{" + "schema=" + schema + ", decoder=" + decoder + '}';
  }

}
