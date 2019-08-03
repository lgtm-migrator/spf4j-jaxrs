package org.spf4j.servlet;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.spf4j.http.ContextTags;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.avro.SchemaResolver;
import org.glassfish.jersey.uri.UriComponent;
import org.spf4j.base.Arrays;
import org.spf4j.base.ExecutionContext;
import org.spf4j.base.ExecutionContexts;
import org.spf4j.base.Methods;
import org.spf4j.base.StackSamples;
import org.spf4j.base.SysExits;
import org.spf4j.base.Throwables;
import org.spf4j.base.TimeSource;
import org.spf4j.base.avro.Converters;
import org.spf4j.base.avro.DebugDetail;
import org.spf4j.base.avro.ServiceError;
import org.spf4j.base.avro.StackSampleElement;
import org.spf4j.http.DeadlineProtocol;
import org.spf4j.http.DefaultDeadlineProtocol;
import org.spf4j.http.Headers;
import org.spf4j.http.HttpWarning;
import org.spf4j.io.LazyOutputStreamWrapper;
import org.spf4j.jaxrs.common.providers.avro.DefaultSchemaProtocol;
import org.spf4j.jaxrs.common.providers.avro.XJsonAvroMessageBodyWriter;
import org.spf4j.log.Level;
import org.spf4j.log.LogAttribute;
import org.spf4j.log.LogUtils;
import org.spf4j.log.Slf4jLogRecord;

/**
 * A Filter for REST services.
 *
 * This filter implements the following:
 * <ul>
 * <li>Deadline propagation, fully customizable via:
 * DeadlineProtocol and the DefaultDeadlineProtocol implementation.</li>
 * <li>Configurable header overwrite via query Parameters.</li>
 * <li>Standard access log</li>
 * <li>Debug logs on error.</li>
 * <li>Profiling information on error.</li>
 * <li>Execution time, timeout relative access log level upgrade.</li>
 * <li>Execution context creation/closing.</li>
 * <li>Context log level overwrites.</li>
 * </ul>
 */
@WebFilter(asyncSupported = true)
public final class ExecutionContextFilter implements Filter {

  public static final String CFG_ID_HEADER_NAME = "spf4j.jaxrs.idHeaderName";

  public static final String CFG_CTX_LOG_LEVEL_HEADER_NAME = "spf4j.jaxrs.ctxLogLevelHeaderName";

  public static final String CFG_HEADER_OVERWRITE_QP_PREFIX = "spf4j.jaxrs.headerOverwriteQueryParamPrefix";

  private DeadlineProtocol deadlineProtocol;

  private String idHeaderName;

  private String ctxLogLevelHeaderName;

  private Logger log;

  private float warnThreshold;

  private float errorThreshold;

  private String headerOverwriteQueryParamPrefix;

  public ExecutionContextFilter() {
    this(new DefaultDeadlineProtocol());
  }

  public ExecutionContextFilter(final DeadlineProtocol deadlineProtocol) {
    this(deadlineProtocol, 0.3f, 0.9f);
  }

  public ExecutionContextFilter(final DeadlineProtocol deadlineProtocol,
          final float warnThreshold, final float errorThreshold) {
    this.deadlineProtocol = deadlineProtocol;
    this.warnThreshold = warnThreshold;
    this.errorThreshold = errorThreshold;
    this.headerOverwriteQueryParamPrefix = "_";
  }

  public DeadlineProtocol getDeadlineProtocol() {
    return deadlineProtocol;
  }

  @Override
  public void init(final FilterConfig filterConfig) {
    log = Logger.getLogger("org.spf4j.servlet." + filterConfig.getFilterName());
    ctxLogLevelHeaderName = Filters.getStringParameter(filterConfig,
            CFG_CTX_LOG_LEVEL_HEADER_NAME, Headers.CTX_LOG_LEVEL);
    idHeaderName = Filters.getStringParameter(filterConfig, CFG_ID_HEADER_NAME, Headers.REQ_ID);
    headerOverwriteQueryParamPrefix = Filters.getStringParameter(filterConfig, CFG_HEADER_OVERWRITE_QP_PREFIX, "_");
  }


  @SuppressFBWarnings("SERVLET_QUERY_STRING")
  private HttpServletRequest overwriteHeadersIfNeeded(final HttpServletRequest request) {
    String queryStr = request.getQueryString();
    if (queryStr == null || !queryStr.contains(headerOverwriteQueryParamPrefix)) {
      return request;
    }
    MultivaluedMap<String, String> qPS = UriComponent.decodeQuery(queryStr, true);
    MultivaluedMap<String, String> overwrites = null;
    for (Map.Entry<String, List<String>> entry : qPS.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith(headerOverwriteQueryParamPrefix)) {
        if (overwrites == null) {
          overwrites = new MultivaluedHashMap<>(4);
        }
        overwrites.put(key.substring(headerOverwriteQueryParamPrefix.length()), entry.getValue());
      }
    }
    if (overwrites == null) {
      return request;
    } else {
      return new HeaderOverwriteHttpServletRequest(request, overwrites);
    }
  }


  @Override
  public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
          throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      chain.doFilter(request, response);
      return;
    }
    CountingHttpServletRequest httpReq = new CountingHttpServletRequest(
            overwriteHeadersIfNeeded((HttpServletRequest) request));
    CountingHttpServletResponse httpResp = new CountingHttpServletResponse((HttpServletResponse) response);
    String name = httpReq.getMethod() + '/' + httpReq.getRequestURL();
    String reqId = httpReq.getHeader(idHeaderName);
    long startTimeNanos = TimeSource.nanoTime();
    long deadlineNanos;
    try {
      deadlineNanos = deadlineProtocol.deserialize(httpReq::getHeader, startTimeNanos);
    } catch (IllegalArgumentException ex) {
      errorResponse(httpResp, 400, "Invalid deadline/timeout", "", ex);
      logRequestEnd(org.spf4j.log.Level.WARN, name, reqId, httpReq, httpResp);
      return;
    }
    ExecutionContext ctx = ExecutionContexts.start(name, reqId, null, startTimeNanos, deadlineNanos);
    ctx.put(ContextTags.HTTP_REQ, httpReq);
    ctx.put(ContextTags.HTTP_RESP, httpResp);
    String ctxLoglevel = httpReq.getHeader(ctxLogLevelHeaderName);
    if (ctxLoglevel != null) {
      try {
        ctx.setBackendMinLogLevel(Level.valueOf(ctxLoglevel));
      } catch (IllegalArgumentException ex) {
        errorResponse(httpResp, 400, "Invalid log level", ctxLoglevel, ex);
        logRequestEnd(org.spf4j.log.Level.WARN, name, reqId, httpReq, httpResp);
        return;
      }
    }
    try {
      chain.doFilter(httpReq, httpResp);
      if (request.isAsyncStarted()) {
        ctx.detach();
        AsyncContext asyncContext = request.getAsyncContext();
        asyncContext.setTimeout(ctx.getMillisToDeadline());
        asyncContext.addListener(new AsyncListener() {
          @Override
          public void onComplete(final AsyncEvent event) {
            ExecutionContexts.current().closeAllButRoot();
            logRequestEnd(org.spf4j.log.Level.INFO, ctx, httpReq, httpResp);
            ctx.close();
          }

          @Override
          public void onTimeout(final AsyncEvent event) {
            ExecutionContexts.current().closeAllButRoot();
            ctx.combine(ContextTags.LOG_LEVEL, Level.ERROR);
            ctx.add(ContextTags.LOG_ATTRIBUTES, LogAttribute.of("warning", "Request timed out"));
          }

          @Override
          public void onError(final AsyncEvent event) {
            ExecutionContexts.current().closeAllButRoot();
            ctx.combine(ContextTags.LOG_LEVEL, Level.ERROR);
            ctx.add(ContextTags.LOG_ATTRIBUTES, event.getThrowable());
          }

          @Override
          public void onStartAsync(final AsyncEvent event) {
          }
        }, request, response);
      } else {
        logRequestEnd(org.spf4j.log.Level.INFO, ctx, httpReq, httpResp);
        ctx.close();
      }
    } catch (Throwable t) {
      if (Throwables.isNonRecoverable(t)) {
        org.spf4j.base.Runtime.goDownWithError(t, SysExits.EX_SOFTWARE);
      }
      ctx.add(ContextTags.LOG_ATTRIBUTES, t);
      logRequestEnd(org.spf4j.log.Level.ERROR, ctx, httpReq, httpResp);
    }
  }

  @SuppressFBWarnings("UCC_UNRELATED_COLLECTION_CONTENTS")
  private void logRequestEnd(final Level plevel,
          final ExecutionContext ctx, final CountingHttpServletRequest req, final CountingHttpServletResponse resp) {
    org.spf4j.log.Level level;
    org.spf4j.log.Level ctxOverride = ctx.get(ContextTags.LOG_LEVEL);
    if (ctxOverride != null && ctxOverride.ordinal() > plevel.ordinal()) {
      level = ctxOverride;
    } else {
      level = plevel;
    }
    Object[] args;
    List<Object> logAttrs = ctx.get(ContextTags.LOG_ATTRIBUTES);
    long startTimeNanos = ctx.getStartTimeNanos();
    long execTimeNanos = TimeSource.nanoTime() - startTimeNanos;
    long maxTime = ctx.getDeadlineNanos() - startTimeNanos;
    long etn = (long) (maxTime * errorThreshold);
    if (execTimeNanos > etn) {
      if (logAttrs == null) {
        logAttrs = new ArrayList<>(2);
      }
      logAttrs.add(LogAttribute.of("performanceError", "exec time > " + etn + " ns"));
      if (level.ordinal() < Level.ERROR.ordinal()) {
        level = level.ERROR;
      }
    } else {
      long wtn = (long) (maxTime * warnThreshold);
      if (execTimeNanos > wtn) {
        if (logAttrs == null) {
          logAttrs = new ArrayList<>(2);
        }
        logAttrs.add(LogAttribute.of("performanceWarning", "exec time > " + wtn + " ns"));
        if (level.ordinal() < Level.WARN.ordinal()) {
          level = level.WARN;
        }
      }
    }
    boolean clientWarning = false;
    List<HttpWarning> warnings = ctx.get(ContextTags.HTTP_WARNINGS);
    if (warnings != null) {
      if (logAttrs == null) {
        logAttrs = new ArrayList<>(warnings);
      } else {
        logAttrs.addAll(warnings);
      }
      if (level.ordinal() < Level.WARN.ordinal()) {
        level = level.WARN;
        clientWarning = true;
      }
    }
    if (logAttrs == null) {
      args = new Object[]{ctx.getName(),
        LogAttribute.traceId(ctx.getId()),
        LogAttribute.of("clientHost", getRemoteHost(req)),
        LogAttribute.value("httpStatus", resp.getStatus()),
        LogAttribute.execTimeMicros(execTimeNanos, TimeUnit.NANOSECONDS),
        LogAttribute.value("inBytes", req.getBytesRead()), LogAttribute.value("outBytes", resp.getBytesWritten())
      };
    } else {
      args = new Object[7 + logAttrs.size()];
      args[0] = ctx.getName();
      args[1] = LogAttribute.traceId(ctx.getId());
      args[2] = LogAttribute.of("clientHost", getRemoteHost(req));
      args[3] = LogAttribute.value("httpStatus", resp.getStatus());
      args[4] = LogAttribute.execTimeMicros(execTimeNanos, TimeUnit.NANOSECONDS);
      args[5] = LogAttribute.value("inBytes", req.getBytesRead());
      args[6] = LogAttribute.value("outBytes", resp.getBytesWritten());
      int i = 7;
      for (Object obj : logAttrs) {
        args[i++] = obj;
      }
    }
    try {
      if (!clientWarning && level.getIntValue() >= Level.WARN.getIntValue()) {
        logContextLogs(log, ctx);
      }
    } catch (Exception ex) {
      log.log(Level.ERROR.getJulLevel(), "Exception while dumping context detail", ex);
    } finally {
      log.log(level.getJulLevel(), "Done {0}", args);
    }
  }

  private void logRequestEnd(final Level level, final String reqStr,
          final String reqId, final CountingHttpServletRequest req,
          final CountingHttpServletResponse resp) {
    Object[] args;
    args = new Object[]{reqStr,
      LogAttribute.traceId(reqId),
      LogAttribute.of("clientHost", getRemoteHost(req)),
      LogAttribute.value("httpStatus", resp.getStatus()),
      LogAttribute.execTimeMicros(0, TimeUnit.NANOSECONDS),
      LogAttribute.value("inBytes", req.getBytesRead()), LogAttribute.value("outBytes", resp.getBytesWritten())
    };
    log.log(level.getJulLevel(), "Done {0}", args);
  }


  private void errorResponse(final HttpServletResponse resp,
          final int status, final String reasonPhrase, final String description, final Throwable exception) {
    resp.setStatus(status);
    ServiceError err = ServiceError.newBuilder()
            .setCode(status)
            .setMessage(reasonPhrase + "; " + description)
            .setDetail(new DebugDetail("origin", Collections.EMPTY_LIST,
                    exception != null ? Converters.convert(exception) : null, Collections.EMPTY_LIST))
            .build();
    XJsonAvroMessageBodyWriter writer = new XJsonAvroMessageBodyWriter(new DefaultSchemaProtocol(
            SchemaResolver.NONE));
    try {
      MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<String, Object>(2);
      writer.writeTo(err, err.getClass(), err.getClass(),
              Arrays.EMPTY_ANNOT_ARRAY,
              MediaType.APPLICATION_JSON_TYPE, headers,
              new LazyOutputStreamWrapper(new HeaderWriteBeforeOutput(headers, resp)));
    } catch (RuntimeException ex) {
      if (exception != null) {
        ex.addSuppressed(exception);
      }
      log.log(java.util.logging.Level.SEVERE, "Exception while writing detail", ex);
      throw ex;
    } catch (IOException ex) {
      if (exception != null) {
        ex.addSuppressed(exception);
      }
      log.log(java.util.logging.Level.SEVERE, "Exception while writing detail", ex);
      throw new UncheckedIOException(ex);
    }
  }


  @SuppressFBWarnings("SERVLET_HEADER") // no sec decisions are made with this. (only logged)
  private String getRemoteHost(final HttpServletRequest req) {
    try {
      String addr = req.getRemoteAddr();
      String fwdFor = req.getHeader("x-forwarded-for");
      if (fwdFor == null) {
        return addr;
      } else {
        return fwdFor + ',' + addr;
      }
    } catch (RuntimeException ex2) {
      log.log(java.util.logging.Level.FINE, "Unable to obtain remote add", ex2);
      return "Unknown";
    }
  }

  private static void logContextLogs(final Logger logger, final ExecutionContext ctx) {
    List<Slf4jLogRecord> ctxLogs = new ArrayList<>();
    ctx.streamLogs((log) -> {
      if (!log.isLogged()) {
        ctxLogs.add(log);
      }
    });
    Collections.sort(ctxLogs, Slf4jLogRecord::compareByTimestamp);
    LogAttribute<CharSequence> traceId = LogAttribute.traceId(ctx.getId());
    for (Slf4jLogRecord log : ctxLogs) {
      LogUtils.logUpgrade(logger, org.spf4j.log.Level.INFO, "Detail on Error",
              traceId, log.toLogRecord("", ""));
    }
    StackSamples stackSamples = ctx.getStackSamples();
    if (stackSamples != null) {
        final List<StackSampleElement> samples = new ArrayList<>();
        Converters.convert(Methods.ROOT, stackSamples, -1, 0,
                (final StackSampleElement object, final long deadline) -> {
          samples.add(object);
        });
      logger.log(java.util.logging.Level.INFO, "profileDetail", new Object[]{traceId,
        new org.spf4j.base.avro.StackSamples(samples)});
    }
  }

  @Override
  public void destroy() {
    // nothing to destroy
  }

  @Override
  public String toString() {
    return "ExecutionContextFilter{" + "deadlineProtocol=" + deadlineProtocol + ", idHeaderName="
            + idHeaderName + ", warnThreshold=" + warnThreshold + ", errorThreshold="
            + errorThreshold + '}';
  }

  @SuppressFBWarnings("DMC_DUBIOUS_MAP_COLLECTION")
  private static class HeaderWriteBeforeOutput implements Supplier<OutputStream> {

    private final MultivaluedHashMap<String, Object> headers;
    private final HttpServletResponse resp;

    HeaderWriteBeforeOutput(final MultivaluedHashMap<String, Object> headers, final HttpServletResponse resp) {
      this.headers = headers;
      this.resp = resp;
    }

    @Override
    @SuppressFBWarnings("HTTP_RESPONSE_SPLITTING")
    public OutputStream get() {
      for (Map.Entry<String, List<Object>> entry : headers.entrySet()) {
        String key = entry.getKey();
        for (Object val : entry.getValue()) {
          String strVal = val.toString();
          if (strVal.indexOf('\n') >= 0 || strVal.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("No multiline warning messages supported: " + strVal);
          }
          resp.addHeader(key, strVal);
        }
      }
      try {
        return resp.getOutputStream();
      } catch (IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }
  }

}
