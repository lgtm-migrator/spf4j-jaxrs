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
package org.spf4j.kube.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import org.glassfish.jersey.client.ClientProperties;
import org.spf4j.http.DeadlineProtocol;
import org.spf4j.jaxrs.client.Spf4JClient;
import org.spf4j.jaxrs.client.Spf4jWebTarget;
import org.spf4j.jaxrs.client.security.providers.BearerAuthClientFilter;
import org.spf4j.jaxrs.client.providers.ClientCustomExecutorServiceProvider;
import org.spf4j.jaxrs.client.providers.ClientCustomScheduledExecutionServiceProvider;
import org.spf4j.jaxrs.client.providers.ExecutionContextClientFilter;
import org.spf4j.jaxrs.common.providers.avro.SchemaProtocol;
import org.spf4j.jaxrs.common.providers.avro.XJsonAvroMessageBodyReader;
import org.spf4j.jaxrs.common.providers.avro.XJsonAvroMessageBodyWriter;

/**
 * A mini kubernetes client that implements "discovery" and is meant to be used within a
 * kubernetes pod.
 *
 * example invocation:
 * curl --cacert /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
 * -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)"
 * https://kubernetes.default.svc/api/v1/namespaces/default/endpoints/jaxrs-spf4j-demo
 *
 * @see https://kubernetes.io/docs/tasks/access-application-cluster/access-cluster/#accessing-the-api-from-a-pod
 *
 * @author Zoltan Farkas
 */
public final class Client {

  private final WebTarget apiTarget;

  private final WebTarget tokenReviewTarget;

  public Client(@Nullable final Supplier<String> apiToken,
          @Nullable final byte[] caCertificate) {
    this("kubernetes.default.svc", apiToken, caCertificate);
  }

  public Client(final String kubernetesMaster,
          @Nullable final Supplier<String> apiToken,
          @Nullable final byte[] caCertificate) {
    ClientBuilder clBuilder = ClientBuilder
            .newBuilder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS);
    if (caCertificate != null) {
      clBuilder = clBuilder.sslContext(buildSslContext(caCertificate));
    }
    if (apiToken != null) {
      clBuilder = clBuilder.register(new BearerAuthClientFilter((hv) -> hv.append(apiToken.get())));
    }
    Spf4jWebTarget rootTarget = new Spf4JClient(clBuilder
            .register(new ExecutionContextClientFilter(DeadlineProtocol.NONE, true))
            .register(ClientCustomExecutorServiceProvider.class)
            .register(ClientCustomScheduledExecutionServiceProvider.class)
            .register(new XJsonAvroMessageBodyReader(SchemaProtocol.NONE))
            .register(new XJsonAvroMessageBodyWriter(SchemaProtocol.NONE))
            .property(ClientProperties.USE_ENCODING, "gzip")
            .build()).target((caCertificate == null ? "http://" : "https://")
                    + kubernetesMaster);
    apiTarget = rootTarget.path("api/v1");
    tokenReviewTarget = rootTarget.path("apis/authentication.k8s.io/v1/tokenreviews");
  }


  public TokenReview.Status tokenReview(final String token) {
    return tokenReviewTarget.request(MediaType.APPLICATION_JSON_TYPE).post(
            Entity.entity(new TokenReview(token), MediaType.APPLICATION_JSON), TokenReview.class).getStatus();
  }


  public Endpoints getEndpoints(final String namesSpace, final String endpointName) {
    return apiTarget.path("namespaces/{namespace}/endpoints/{endpointName}")
            .resolveTemplate("namespace", namesSpace)
            .resolveTemplate("endpointName", endpointName)
            .request(MediaType.APPLICATION_JSON_TYPE)
            .get(Endpoints.class);
  }

  private Certificate generateCertificate(final byte[] caCertificate)
          throws IOException, CertificateException {
    try (InputStream caInput = new ByteArrayInputStream(caCertificate)) {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      return cf.generateCertificate(caInput);
    }
  }

  private SSLContext buildSslContext(final byte[] caCertificate) {
    try {
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null, null);
      keyStore.setCertificateEntry("ca", generateCertificate(caCertificate));

      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(keyStore);

      SSLContext context = SSLContext.getInstance("TLSv1.2");
      context.init(null, tmf.getTrustManagers(), null);
      return context;
    } catch (KeyStoreException | KeyManagementException | IOException
            | NoSuchAlgorithmException | CertificateException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public String toString() {
    return "Client{" + "apiTarget=" + apiTarget + '}';
  }

}
