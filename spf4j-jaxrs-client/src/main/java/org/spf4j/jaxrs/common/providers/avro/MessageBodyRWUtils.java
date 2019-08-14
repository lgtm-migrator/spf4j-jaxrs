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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.avro.Schema;
import org.apache.avro.reflect.AvroSchema;
import org.apache.avro.reflect.ExtendedReflectData;

/**
 *
 * @author Zoltan Farkas
 */
@SuppressFBWarnings("PCAIL_POSSIBLE_CONSTANT_ALLOCATION_IN_LOOP") //Schema.Parser has state.
public final class MessageBodyRWUtils {

  private static final ExtendedReflectData RFLCTOR = ExtendedReflectData.get();

  private MessageBodyRWUtils() { }

  /**
   * when using dynamic resources, type can be an ArrayList.class, and genericType is Object.class.
   */
  @Nullable
  public static Type effectiveType(final Class<?> type, final Type genericType) {
    Type res = genericType != null && genericType != Object.class ? genericType : type;
    if (res == Object.class) {
      return null;
    }
    return res;
  }

  @Nonnull
  public static Schema getAvroSchemaFromType(final Class<?> type,
          final Type genericType, @Nullable final Object object, final Annotation[] annotations) {
    for (Annotation annot : annotations) {
      if (annot.annotationType() == AvroSchema.class) {
        return new Schema.Parser().parse(((AvroSchema) annot).value()); //todo cache parsing.
      }
    }
    Type effectiveType = effectiveType(type, genericType);
    Schema schema = null;
    if (effectiveType != null) {
      schema = RFLCTOR.getSchema(effectiveType);
      if (schema == null) {
        schema = RFLCTOR.createSchema(effectiveType, object, new HashMap<>());
      }
    } else {
      if (object == null) {
        return Schema.create(Schema.Type.NULL);
      } else {
        schema = RFLCTOR.createSchema(object.getClass(), object, new HashMap<>());
      }
    }
    for (Annotation annot : annotations) {
      if (annot.annotationType() == Nullable.class) {
        schema = Schema.createUnion(Schema.create(Schema.Type.NULL), schema);
      }
    }
    return schema;
  }

  @Nullable
  public static Schema getAvroSchemaFromType(final Class<?> type,
          final Type genericType, final Annotation[] annotations) {
    for (Annotation annot : annotations) {
      if (annot.annotationType() == AvroSchema.class) {
        return new Schema.Parser().parse(((AvroSchema) annot).value()); //todo cache parsing.
      }
    }
    Type effectiveType = effectiveType(type, genericType);
    if (effectiveType == null) {
      return null;
    }
    Schema schema = RFLCTOR.getSchema(effectiveType);
    if (schema == null) {
      return null;
    }
    for (Annotation annot : annotations) {
      if (annot.annotationType() == Nullable.class) {
        schema = Schema.createUnion(Schema.create(Schema.Type.NULL), schema);
      }
    }
    return schema;
  }

  @Nullable
  public static Schema getAvroSchemaFromType(
          final Type effectiveType, final Annotation[] annotations) {
    for (Annotation annot : annotations) {
      if (annot.annotationType() == AvroSchema.class) {
        return new Schema.Parser().parse(((AvroSchema) annot).value()); //todo cache parsing.
      }
    }
    if (effectiveType == null) {
      return null;
    }
    Schema schema = RFLCTOR.getSchema(effectiveType);
    if (schema == null) {
      return null;
    }
    for (Annotation annot : annotations) {
      if (annot.annotationType() == Nullable.class) {
        schema = Schema.createUnion(Schema.create(Schema.Type.NULL), schema);
      }
    }
    return schema;
  }


}
