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
package org.spf4j.actuator.cluster.metrics;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.apache.avro.generic.GenericRecord;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spf4j.actuator.ServiceIntegrationBase;
import org.spf4j.base.CloseableIterable;
import org.spf4j.perf.impl.MeasurementsInfoImpl;
import org.spf4j.perf.impl.RecorderFactory;

/**
 *
 * @author Zoltan Farkas
 */
public class MetricsClusterResourceTest extends ServiceIntegrationBase {

  private static final Logger LOG = LoggerFactory.getLogger(MetricsClusterResourceTest.class);

  @BeforeClass
  public static void init() throws IOException {
    long mid = RecorderFactory.MEASUREMENT_STORE.alocateMeasurements(
            new MeasurementsInfoImpl("test-1", "test measurement",
                    new String[]{"a", "b"}, new String[]{"ms", "ms"}), 0);
    RecorderFactory.MEASUREMENT_STORE.saveMeasurements(mid, System.currentTimeMillis(), 1, 2);
    RecorderFactory.MEASUREMENT_STORE.flush();
  }

  @Test
  public void testMetrics() throws IOException {
    List<String> nodes = getTarget().path("metrics/cluster")
            .request(MediaType.APPLICATION_JSON).get(new GenericType<List<String>>() {
    });
    LOG.debug("metrics: {}", nodes);
    Assert.assertFalse(nodes.isEmpty());
    CloseableIterable<GenericRecord> measurements = getTarget().path("metrics/cluster/" + nodes.get(0)
            + "/data")
            .request("application/avro").get(new GenericType<CloseableIterable<GenericRecord>>() {
    });
    for (GenericRecord data : measurements) {
      LOG.debug("data", data);
      Assert.assertEquals(1L, data.get(2));
      Assert.assertEquals(2L, data.get(3));
    }
  }

  @Test
  public void testMetricsJoin() throws IOException {
    CloseableIterable<GenericRecord> measurements = getTarget().path("metrics/cluster/test-1/data/b")
            .request("application/avro").get(new GenericType<CloseableIterable<GenericRecord>>() {
    });
    for (GenericRecord data : measurements) {
      LOG.debug("data", data);
      Assert.assertEquals(BigDecimal.valueOf(2L), data.get(1));
    }
  }


  @Test
  public void testMetricsProject() throws IOException {
    CloseableIterable<GenericRecord> measurements = getTarget().path("metrics/cluster/test-1/data")
            .queryParam("_project", "node,ts,b")
            .request("application/avro").get(new GenericType<CloseableIterable<GenericRecord>>() {
    });
    for (GenericRecord data : measurements) {
      LOG.debug("data", data);
      Assert.assertEquals(2L, data.get(2));
    }
  }

  @Test
  public void testMetricsProject2() throws IOException {
    CloseableIterable<GenericRecord> measurements = getTarget().path("metrics/cluster/test-1/data")
            .queryParam("_project", "ts,b")
            .request("*/*").get(new GenericType<CloseableIterable<GenericRecord>>() {
    });
    for (GenericRecord data : measurements) {
      LOG.debug("data", data);
      Assert.assertEquals(2L, data.get(1));
    }
  }

}
