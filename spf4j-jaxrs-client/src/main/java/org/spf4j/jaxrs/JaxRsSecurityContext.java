/*
 * Copyright 2020 SPF4J.
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
package org.spf4j.jaxrs;

import java.util.Properties;
import java.util.function.BiConsumer;
import javax.ws.rs.core.SecurityContext;
import org.spf4j.security.AbacSecurityContext;

public interface JaxRsSecurityContext extends SecurityContext, AbacSecurityContext {

  /**
   * Role that allow permission to operate (view logs, profiles, configuration...) a service.
   */
  String OPERATOR_ROLE = "operator";

  default boolean canAccess(final Properties resource, final Properties action, final Properties env) {
      return false;
  }

  default void initiateAuthentication(final BiConsumer<String, String> headers) {
  }

}
