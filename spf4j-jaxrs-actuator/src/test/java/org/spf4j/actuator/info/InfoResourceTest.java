
package org.spf4j.actuator.info;

import java.net.MalformedURLException;
import javax.ws.rs.core.MediaType;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spf4j.actuator.ServiceIntegrationBase;
import org.spf4j.base.avro.ApplicationInfo;
import org.spf4j.base.avro.ClusterInfo;
import org.spf4j.base.avro.ProcessInfo;

/**
 * @author Zoltan Farkas
 */
public class InfoResourceTest extends ServiceIntegrationBase {

  private static final Logger LOG = LoggerFactory.getLogger(InfoResourceTest.class);

  @Test
  public void testInfo() {
    ApplicationInfo ai = getTarget().path("info")
            .queryParam("_Acccept", "application/avro")
            .request(MediaType.APPLICATION_JSON).get(ApplicationInfo.class);
    LOG.debug("application info", ai);
    Assert.assertNotNull(ai);
  }

  @Test(timeout = 10000)
  public void testClusterInfo() throws MalformedURLException {
    ClusterInfo ai = getTarget().path("info/cluster").request(MediaType.APPLICATION_JSON).get(ClusterInfo.class);
    LOG.debug("cluster info", ai);
    Assert.assertNotNull(ai);
  }

  @Test(timeout = 10000)
  public void testProcessInfo() throws MalformedURLException {
    ProcessInfo ai = getTarget().path("info/local").request("application/avro").get(ProcessInfo.class);
    LOG.debug("process info", ai);
    Assert.assertNotNull(ai);
  }

}
