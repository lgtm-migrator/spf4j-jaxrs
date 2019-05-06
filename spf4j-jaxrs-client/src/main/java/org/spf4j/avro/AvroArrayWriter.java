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
package org.spf4j.avro;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.spf4j.jaxrs.ArrayWriter;

/**
 *
 * @author Zoltan Farkas
 */
@ThreadSafe
public final class AvroArrayWriter<T> implements ArrayWriter<T> {

  private final T[] buffer;

  private final Encoder encoder;

  private final DatumWriter<T> writer;

  private int at;

  private boolean start;

  private boolean isClosed;

  public AvroArrayWriter(final Encoder encoder, final DatumWriter<T> elementWriter,
          final Class<T> type, final int bufferSize) {
    if (bufferSize < 1) {
      throw new IllegalArgumentException("Invalid buffer size " + bufferSize);
    }
    this.encoder = encoder;
    this.writer = elementWriter;
    buffer = (T[]) java.lang.reflect.Array.newInstance(type, bufferSize);
    this.at = 0;
    this.start = true;
    this.isClosed = false;
  }

  @Override
  public synchronized void accept(final T t) {
    if (isClosed) {
      throw new IllegalStateException("writer " + this + " already closed");
    }
    buffer[at++] = t;
    if (at >= buffer.length)  {
      writeBuffer();
    }
  }

  private void writeBuffer() {
    try {
      if (start) {
        encoder.writeArrayStart();
        start = false;
      }
      encoder.setItemCount(at);
      for (int i = 0; i < at; i++) {
        encoder.startItem();
        writer.write(buffer[i], encoder);
        buffer[i] = null;
      }
      at = 0;
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }


  @Override
  public synchronized void flush() throws IOException {
    if (isClosed) {
      throw new IllegalStateException("writer " + this + " already closed");
    }
    writeBuffer();
    encoder.flush();
  }

  @Override
  public synchronized void close() throws IOException {
    if (!isClosed) {
      writeBuffer();
      encoder.writeArrayEnd();
      encoder.flush();
      isClosed = true;
    }
  }

  @Override
  public String toString() {
    return "AvroArrayWriter{" + "buffer=" + Arrays.toString(buffer) + ", encoder=" + encoder
            + ", writer=" + writer + ", at=" + at + ", start=" + start + ", isClosed=" + isClosed + '}';
  }

}
