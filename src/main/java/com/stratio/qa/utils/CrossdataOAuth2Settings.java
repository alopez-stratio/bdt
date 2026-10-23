/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.qa.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * OAuth2 settings of a Crossdata JDBC connection: the driver's {@code AUTH_MODE} and the {@code OAUTH2_*}
 * connection properties that go with it. A connection without them authenticates as it always did (basic).
 * <p>Supported modes: {@code token_passthrough}, where the access token is given and the driver only uses the
 * issuer and the client id to renew it with the refresh token.
 */
public final class CrossdataOAuth2Settings {

    private static final Pattern SECRETS = Pattern.compile(
            "(?i)([;?](?:PWD|KEYSTORE_PWD|TRUSTSTORE_PWD|OAUTH2_ACCESS_TOKEN|OAUTH2_REFRESH_TOKEN|OAUTH2_CLIENT_SECRET)=)[^;]*");

    private final Map<String, String> properties = new LinkedHashMap<>();

    private CrossdataOAuth2Settings(String authMode) {
        properties.put("AUTH_MODE", authMode);
    }

    /**
     * Token passthrough: the driver sends the given access token. To renew it once it expires, the driver needs
     * the refresh token, the client id and either the issuer (whose discovery document gives the token endpoint)
     * or the token endpoint itself.
     *
     * @param accessToken   access token to send, mandatory
     * @param refreshToken  refresh token, or null
     * @param issuer        OIDC issuer, or null
     * @param clientId      OIDC client the tokens were issued to, or null
     * @param scope         scopes to ask for when renewing, or null
     * @param tokenEndpoint token endpoint, or null to discover it from the issuer
     * @return the settings
     */
    public static CrossdataOAuth2Settings tokenPassthrough(String accessToken, String refreshToken, String issuer,
                                                           String clientId, String scope, String tokenEndpoint) {
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IllegalArgumentException("The access token is mandatory for OAuth2 token passthrough");
        }
        if (refreshToken != null && (clientId == null || (issuer == null && tokenEndpoint == null))) {
            throw new IllegalArgumentException("To renew the access token with the refresh token the driver needs "
                    + "the client id and either the issuer or the token endpoint");
        }
        CrossdataOAuth2Settings settings = new CrossdataOAuth2Settings("token_passthrough");
        settings.put("OAUTH2_ACCESS_TOKEN", accessToken);
        settings.put("OAUTH2_REFRESH_TOKEN", refreshToken);
        settings.put("OAUTH2_ISSUER", issuer);
        settings.put("OAUTH2_CLIENT_ID", clientId);
        settings.put("OAUTH2_SCOPE", scope);
        settings.put("OAUTH2_TOKEN_ENDPOINT", tokenEndpoint);
        return settings;
    }

    private void put(String key, String value) {
        if (value == null) {
            return;
        }
        if (value.contains(";")) {
            throw new IllegalArgumentException(key + " cannot contain ';', it separates the connection properties");
        }
        properties.put(key, value);
    }

    /**
     * @return the driver's authentication mode, e.g. token_passthrough
     */
    public String authMode() {
        return properties.get("AUTH_MODE");
    }

    /**
     * @return the properties to append to the connection string, each one preceded by ';'
     */
    public String connectionProperties() {
        StringBuilder sb = new StringBuilder();
        properties.forEach((key, value) -> sb.append(';').append(key).append('=').append(value));
        return sb.toString();
    }

    /**
     * Hides the passwords and the OAuth2 tokens and secrets of a connection string, so it can be logged.
     *
     * @param connection connection string
     * @return the connection string with those values replaced by ***
     */
    public static String maskSecrets(String connection) {
        return connection == null ? null : SECRETS.matcher(connection).replaceAll("$1***");
    }
}
