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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * OAuth2 settings of a Crossdata JDBC connection: the driver's {@code AUTH_MODE} and the {@code OAUTH2_*}
 * connection properties that go with it. A connection without them authenticates as it always did (basic).
 * <p>Supported modes: {@code token_passthrough}, where the access token is given and the driver only uses the
 * issuer and the client id to renew it with the refresh token, and {@code u2m}, where the driver opens the browser
 * for the user to log in. In U2M the BDT plays that browser ({@link CrossdataOAuth2Browser}) with the credentials
 * kept here, which are not part of the connection string.
 */
public final class CrossdataOAuth2Settings {

    private static final Pattern SECRETS = Pattern.compile(
            "(?i)([;?](?:PWD|KEYSTORE_PWD|TRUSTSTORE_PWD|OAUTH2_ACCESS_TOKEN|OAUTH2_REFRESH_TOKEN|OAUTH2_CLIENT_SECRET)=)[^;]*");

    private static final String DRIVER_SETTINGS_PREFIX = "crossdata-driver.oauth2.";

    private static final Set<String> DRIVER_SETTINGS = new HashSet<>();

    private final Map<String, String> properties = new LinkedHashMap<>();

    private U2MLogin u2mLogin;

    /**
     * User the fake browser logs in with in the U2M flow.
     */
    public static final class U2MLogin {

        private final String userName;

        private final String password;

        private final String tenant;

        private final boolean verifyHost;

        U2MLogin(String userName, String password, String tenant, boolean verifyHost) {
            this.userName = userName;
            this.password = password;
            this.tenant = tenant;
            this.verifyHost = verifyHost;
        }

        public String userName() {
            return userName;
        }

        public String password() {
            return password;
        }

        public String tenant() {
            return tenant;
        }

        public boolean verifyHost() {
            return verifyHost;
        }
    }

    private CrossdataOAuth2Settings(String authMode) {
        properties.put("AUTH_MODE", authMode);
    }

    /**
     * U2M: the driver opens the authorization URL in the browser and exchanges the code it gets back. It finds the
     * authorization and token endpoints in the discovery document of the issuer, unless both are given.
     *
     * @param issuer                OIDC issuer, or null when both endpoints are given
     * @param clientId              OIDC public client of the driver, mandatory
     * @param scope                 scopes to ask for, or null for the driver's default (none)
     * @param authorizationEndpoint authorization endpoint, or null to discover it
     * @param tokenEndpoint         token endpoint, or null to discover it
     * @param userName              user the fake browser logs in with
     * @param password              its password
     * @param tenant                tenant to choose in the login form, or null
     * @param verifyHost            whether the fake browser verifies the certificate of the identity server
     * @return the settings
     */
    public static CrossdataOAuth2Settings u2m(String issuer, String clientId, String scope, String authorizationEndpoint,
                                              String tokenEndpoint, String userName, String password, String tenant,
                                              boolean verifyHost) {
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalArgumentException("The client id is mandatory for OAuth2 U2M");
        }
        if (issuer == null && (authorizationEndpoint == null || tokenEndpoint == null)) {
            throw new IllegalArgumentException("OAuth2 U2M needs the issuer, or both the authorization and the token endpoints");
        }
        if (userName == null || password == null) {
            throw new IllegalArgumentException("OAuth2 U2M needs the user and the password to log in with");
        }
        CrossdataOAuth2Settings settings = new CrossdataOAuth2Settings("u2m");
        settings.put("OAUTH2_ISSUER", issuer);
        settings.put("OAUTH2_CLIENT_ID", clientId);
        settings.put("OAUTH2_SCOPE", scope);
        settings.put("OAUTH2_AUTH_ENDPOINT", authorizationEndpoint);
        settings.put("OAUTH2_TOKEN_ENDPOINT", tokenEndpoint);
        settings.u2mLogin = new U2MLogin(userName, password, tenant, verifyHost);
        return settings;
    }

    /**
     * @return the user the fake browser logs in with, or null when the mode is not U2M
     */
    public U2MLogin u2mLogin() {
        return u2mLogin;
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
     * Sets an OAuth2 setting of the driver that the connection string cannot carry, such as {@code cache.path},
     * {@code cache.enabled}, {@code browser-timeout-sec}, {@code expiry-skew-sec} or {@code redirect-port}. It is
     * the system property {@code crossdata-driver.oauth2.<key>}, which the driver reads on every new connection.
     *
     * @param key   setting under crossdata-driver.oauth2, e.g. cache.path
     * @param value its value
     */
    public static synchronized void setDriverSetting(String key, String value) {
        System.setProperty(DRIVER_SETTINGS_PREFIX + key, value);
        DRIVER_SETTINGS.add(key);
    }

    /**
     * @param key setting under crossdata-driver.oauth2, e.g. cache.path
     * @return its value, or null when it is not set
     */
    public static String driverSetting(String key) {
        return System.getProperty(DRIVER_SETTINGS_PREFIX + key);
    }

    /**
     * Removes the driver settings set with {@link #setDriverSetting(String, String)}.
     */
    public static synchronized void clearDriverSettings() {
        DRIVER_SETTINGS.forEach(key -> System.clearProperty(DRIVER_SETTINGS_PREFIX + key));
        DRIVER_SETTINGS.clear();
    }

    /**
     * The driver's U2M token cache of the tests: the cache.path setting of the scenario or, without it,
     * {@code target/crossdata-oauth2/tokencache}, where the U2M step makes the driver keep it. It does not depend on
     * the order of the steps, and it is never the user's own cache (the driver's default, in the home directory).
     *
     * @return the file
     */
    public static Path tokenCacheFile() {
        String path = driverSetting("cache.path");
        return path != null && !path.isEmpty() ? Paths.get(path)
                : Paths.get("target", "crossdata-oauth2", "tokencache").toAbsolutePath();
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
