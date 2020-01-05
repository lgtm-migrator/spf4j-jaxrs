package org.spf4j.actuator.cluster.logs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.spf4j.jaxrs.server.AsyncResponseWrapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.StreamingOutput;
import org.spf4j.actuator.logs.LogUtils;
import org.spf4j.actuator.logs.LogsResource;
import org.spf4j.base.avro.LogRecord;
import org.spf4j.base.avro.NetworkService;
import org.spf4j.base.avro.Order;
import org.spf4j.cluster.Cluster;
import org.spf4j.cluster.ClusterInfo;
import org.spf4j.concurrent.ContextPropagatingCompletableFuture;
import org.spf4j.concurrent.DefaultExecutor;
import org.spf4j.jaxrs.ProjectionSupport;
import org.spf4j.jaxrs.client.Spf4JClient;
import org.spf4j.jaxrs.client.Spf4jWebTarget;
import org.spf4j.log.LogPrinter;

/**
 *
 * @author Zoltan Farkas
 */
@Path("logs/cluster")
@SuppressWarnings("checkstyle:DesignForExtension")// methods cannot be final due to interceptors
@RolesAllowed("operator")
@Singleton
public class LogsClusterResource {

  private final Cluster cluster;

  private final Spf4JClient httpClient;

  private final LogsResource localLogs;

  @Inject
  public LogsClusterResource(final LogsResource localLogs,
          final Cluster cluster, final Spf4JClient httpClient) {
    this.cluster = cluster;
    this.httpClient = httpClient;
    this.localLogs = localLogs;
  }

  @GET
  @Produces(value = {"text/plain"})
  public void getClusterLogsText(@QueryParam("limit") @DefaultValue("1000") final int limit,
          @QueryParam("filter") @Nullable final String filter,
          @QueryParam("order") @DefaultValue("DESC") final Order resOrder,
          @Suspended final AsyncResponse ar)
          throws IOException, URISyntaxException {
    getClusterLogs(limit, filter, resOrder, new AsyncResponseWrapper(ar) {
      @Override
      public boolean resume(final Object response) {
        return super.resume(new StreamingOutput() {
          @Override
          public void write(final OutputStream output) throws IOException, WebApplicationException {
            LogPrinter printer = new LogPrinter(StandardCharsets.UTF_8);
            for (LogRecord record : (Iterable<LogRecord>) response) {
              printer.print(record, output);
            }
          }
        });
      }

    });
  }

  @Operation(
         description = "Get logs logged by the default appender aggregated from all nodes",
         responses = {
           @ApiResponse(
                 responseCode = "200",
                 content = @Content(array = @ArraySchema(schema = @Schema(implementation = LogRecord.class))))
         }
  )
  @GET
  @Produces(value = {"application/avro-x+json", "application/json",
    "application/avro+json", "application/avro", "application/octet-stream"})
  @ProjectionSupport
  public void getClusterLogs(@QueryParam("limit") @DefaultValue("1000") final int limit,
          @QueryParam("filter") @Nullable final String filter,
          @QueryParam("order") @DefaultValue("DESC") final Order resOrder,
          @Suspended final AsyncResponse ar)
          throws IOException, URISyntaxException {
    getClusterLogs(limit, filter, resOrder, "default", ar);
  }


  @Operation(
         description = "Get logs logged by a particular appender aggregated from all nodes",
         responses = {
           @ApiResponse(
                 responseCode = "200",
                 content = @Content(array = @ArraySchema(schema = @Schema(implementation = LogRecord.class))))
         }
  )
  @Path("{appenderName}")
  @GET
  @Produces(value = {"application/avro-x+json", "application/json",
    "application/avro+json", "application/avro", "application/octet-stream"})
  @ProjectionSupport
  public void getClusterLogs(@QueryParam("limit") @DefaultValue("1000") final int limit,
          @QueryParam("filter") @Nullable final String filter,
           @QueryParam("order") @DefaultValue("DESC") final Order resOrder,
          @PathParam("appenderName") final String appender, @Suspended final AsyncResponse ar)
          throws IOException, URISyntaxException {
    if (limit == 0) {
      ar.resume(Collections.EMPTY_LIST);
      return;
    }
    if (limit < 0) {
      throw new ClientErrorException("limit parameter must be positive: " + limit, 400);
    }
    CompletableFuture<PriorityQueue<LogRecord>> cf
            = ContextPropagatingCompletableFuture.supplyAsync(() -> {
              PriorityQueue<LogRecord> result = new PriorityQueue(limit, LogUtils.TS_ORDER_ASC);
              Collection<LogRecord> ll;
              try {
                ll = localLogs.getLocalLogs(0, limit, filter, resOrder, appender);
              } catch (IOException ex) {
                throw new UncheckedIOException(ex);
              }
              LogUtils.addAll(limit, result, ll);
              return result;
            }, DefaultExecutor.INSTANCE);
    ClusterInfo clusterInfo = cluster.getClusterInfo();
    Set<InetAddress> peerAddresses = clusterInfo.getPeerAddresses();
    NetworkService service = clusterInfo.getHttpService();
    for (InetAddress addr : peerAddresses) {
      URI uri = new URI(service.getName(), null,
                  addr.getHostAddress(), service.getPort(), "/logs/local", null, null);
      Spf4jWebTarget invTarget = httpClient.target(uri)
              .path(appender)
              .queryParam("limit", limit);
      if (filter != null) {
        invTarget = invTarget.queryParam("filter", filter);
      }
      cf = cf.thenCombine(
              invTarget.request("application/avro").rx().get(new GenericType<List<LogRecord>>() { }),
              (PriorityQueue<LogRecord> result, List<LogRecord> rl) -> {
                LogUtils.addAll(limit, result, rl);
                return result;
              }
      );
    }
    cf.whenComplete((records, t) -> {
      if (t != null) {
        ar.resume(t);
      } else {
        ArrayList<LogRecord> result = new ArrayList(limit);
        result.addAll(records);
        Collections.sort(result, (resOrder == Order.DESC) ? LogUtils.TS_ORDER_DESC : LogUtils.TS_ORDER_ASC);
        ar.resume(result);
      }
    });
  }

  @Override
  public String toString() {
    return "LogsClusterResource{" + "cluster=" + cluster + ", httpClient=" + httpClient + '}';
  }

}
