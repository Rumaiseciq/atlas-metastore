/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas.web.security;

import org.apache.atlas.ApplicationProperties;
import org.apache.atlas.keycloak.client.AtlasKeycloakClient;
import org.apache.commons.configuration.Configuration;
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationProvider;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.keycloak.representations.oidc.TokenMetadataRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class AtlasKeycloakAuthenticationProvider extends AtlasAbstractAuthenticationProvider {
  private final boolean groupsFromUGI;
  private final String groupsClaim;

  private final KeycloakAuthenticationProvider keycloakAuthenticationProvider;

  private final AtlasKeycloakClient atlasKeycloakClient;

  private static final Logger LOG = LoggerFactory.getLogger(AtlasKeycloakAuthenticationProvider.class);

  public AtlasKeycloakAuthenticationProvider() throws Exception {
    this.keycloakAuthenticationProvider = new KeycloakAuthenticationProvider();
    this.atlasKeycloakClient = AtlasKeycloakClient.getKeycloakClient();

    Configuration configuration = ApplicationProperties.get();
    this.groupsFromUGI = configuration.getBoolean("atlas.authentication.method.keycloak.ugi-groups", true);
    this.groupsClaim = configuration.getString("atlas.authentication.method.keycloak.groups_claim");
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    authentication = keycloakAuthenticationProvider.authenticate(authentication);

    if (groupsFromUGI) {
      List<GrantedAuthority> groups = getAuthoritiesFromUGI(authentication.getName());
      KeycloakAuthenticationToken token = (KeycloakAuthenticationToken) authentication;

      authentication = new KeycloakAuthenticationToken(token.getAccount(), token.isInteractive(), groups);
    } else if (groupsClaim != null) {
      KeycloakAuthenticationToken token = (KeycloakAuthenticationToken)authentication;
      Map<String, Object> claims = token.getAccount().getKeycloakSecurityContext().getToken().getOtherClaims();
      if (claims.containsKey(groupsClaim)) {
        List<String> membership = (List<String>)claims.get(groupsClaim);
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        for (String group : membership) {
          grantedAuthorities.add(new SimpleGrantedAuthority(group));
        }
        authentication = new KeycloakAuthenticationToken(token.getAccount(), token.isInteractive(), grantedAuthorities);
      }
    }

    // Introspect token online with keycloak server if request is made by service account
    if (authentication.getName().startsWith("service-account-apikey")) {
      LOG.info("Validating request for clientId: {}", authentication.getName().substring("service-account-".length()));
      try {
        KeycloakAuthenticationToken keycloakToken = (KeycloakAuthenticationToken) authentication;
        String bearerToken = keycloakToken.getAccount().getKeycloakSecurityContext().getTokenString();
        TokenMetadataRepresentation introspectToken = atlasKeycloakClient.introspectToken(bearerToken);
        if (Objects.nonNull(introspectToken) && introspectToken.isActive()) {
          authentication.setAuthenticated(true);
        } else {
          // if the client is inactive throw KeycloakAuthenticationException
          handleInvalidApiKey(authentication);
        }
      } catch (Exception e) {
        LOG.error("Keycloak Authentication failed : {}", e.getMessage());
        throw new KeycloakAuthenticationException("Keycloak Authentication failed", e.getCause());
      }
    }

    return authentication;
  }

  private void handleInvalidApiKey(Authentication authentication) {
    authentication.setAuthenticated(false);
    LOG.error("Invalid clientId: {}", authentication.getName().substring("service-account-".length()));
    throw new KeycloakAuthenticationException("Invalid ClientId");
  }

  @Override
  public boolean supports(Class<?> aClass) {
    return keycloakAuthenticationProvider.supports(aClass);
  }
}