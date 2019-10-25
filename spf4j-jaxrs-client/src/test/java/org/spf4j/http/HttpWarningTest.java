
package org.spf4j.http;

import java.time.ZonedDateTime;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Zoltan Farkas
 */
public class HttpWarningTest {

private static final Logger LOG = LoggerFactory.getLogger(HttpWarningTest.class);

  @Test
  public void tesSerDeser() {
    HttpWarning warning = new HttpWarning(HttpWarning.MISCELLANEOUS, "super",  ZonedDateTime.now(), "aaaa   ");
    LOG.debug("Warning", warning);
    HttpWarning nwarn = HttpWarning.parse(warning.toString());
    Assert.assertEquals(warning, nwarn);
  }


  @Test
  public void testParse() {
    HttpWarning parse = HttpWarning.parse("299 - \"blabla\\\" \\\\ \"");
    Assert.assertEquals("blabla\" \\ ", parse.getText());
    Assert.assertEquals(299, parse.getCode());
    Assert.assertEquals("-", parse.getAgent());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testParseFail() {
    HttpWarning parse = HttpWarning.parse("299 - \"blabla\\\" \\\\ ");
    Assert.assertEquals("blabla\" \\ ", parse.getText());
  }

}
