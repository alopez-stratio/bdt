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

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Gets OIDC tokens from SIS, without a browser, the way a native client (JDBC/ODBC driver) does:
 * authorization code with PKCE S256 and a loopback redirect URI.
 *
 * <p>The login and the consent pages are driven over HTTP (same approach as {@link GosecSSOUtils}).
 * Nobody listens on the loopback port: the {@code Location} header of the last redirect is read, not followed.
 * Public clients such as {@code virtualizer-dbc} have no password or client credentials grant, so this is the
 * only way to get a token for a user without a person in front of a browser.
 */
public class OidcNativeLoginUtils {

    public static final String DEFAULT_SCOPE = "openid login_data offline_access";

    /**
     * Loopback redirect URI of native clients. Web clients, such as Release Manager, register an https one.
     */
    public static final String DEFAULT_REDIRECT_URI = "http://127.0.0.1:8765/callback";

    private static final int MAX_REDIRECTS = 20;

    private static final int MAX_CONSENT_PAGES = 3;

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    private final String wellKnownUrl;

    private final String clientId;

    private final String scope;

    private final URI redirectUri;

    private final boolean verifyHost;

    private final SecureRandom random = new SecureRandom();

    /**
     * @param wellKnownUrl discovery URL of SIS
     * @param clientId     OIDC public client
     * @param scope        scopes to ask for, {@link #DEFAULT_SCOPE} when null
     * @param redirectUri  redirect URI registered for the client, {@link #DEFAULT_REDIRECT_URI} when null.
     *                     Nobody listens on it: the redirect to it is read, not followed
     * @param verifyHost   whether to verify the certificate of the server
     */
    public OidcNativeLoginUtils(String wellKnownUrl, String clientId, String scope, String redirectUri, boolean verifyHost) {
        this.wellKnownUrl = wellKnownUrl;
        this.clientId = clientId;
        this.scope = scope == null ? DEFAULT_SCOPE : scope;
        this.redirectUri = URI.create(redirectUri == null ? DEFAULT_REDIRECT_URI : redirectUri);
        if (this.redirectUri.getScheme() == null || this.redirectUri.getHost() == null) {
            throw new IllegalArgumentException("The redirect URI must be absolute, with scheme and host: " + redirectUri);
        }
        this.verifyHost = verifyHost;
    }

    /**
     * Log in and exchange the authorization code.
     *
     * @param userName user to log in with
     * @param password its password
     * @param tenant   tenant to choose in the login form, or null
     * @return the token endpoint response as it came: access_token, refresh_token, id_token, expires_in, token_type...
     * @throws IOException if the login is rejected or any step of the flow fails
     */
    public JSONObject login(String userName, String password, String tenant) throws IOException {
        try (CloseableHttpClient client = buildClient()) {
            HttpClientContext context = HttpClientContext.create();

            JSONObject discovery = new JSONObject(get(client, context, URI.create(wellKnownUrl)).body);

            String verifier = randomUrlSafe(48);
            String state = randomUrlSafe(16);
            String nonce = randomUrlSafe(16);

            URI authorize = URI.create(discovery.getString("authorization_endpoint") + "?" + form(List.of(
                    pair("response_type", "code"),
                    pair("client_id", clientId),
                    pair("redirect_uri", redirectUri.toString()),
                    pair("scope", scope),
                    pair("state", state),
                    pair("nonce", nonce),
                    pair("prompt", "login"),
                    pair("code_challenge_method", "S256"),
                    pair("code_challenge", challenge(verifier)))));

            Map<String, String> callback = queryOf(authorize(client, context, authorize, userName, password, tenant));
            if (!state.equals(callback.get("state"))) {
                throw new IOException("The state returned by the authorization endpoint does not match");
            }
            if (!callback.containsKey("code")) {
                throw new IOException("The authorization endpoint returned an error: " + callback.getOrDefault("error", "no code")
                        + " " + callback.getOrDefault("error_description", ""));
            }

            HttpPost tokenRequest = new HttpPost(discovery.getString("token_endpoint"));
            tokenRequest.setEntity(new UrlEncodedFormEntity(List.of(
                    pair("grant_type", "authorization_code"),
                    pair("client_id", clientId),
                    pair("code", callback.get("code")),
                    pair("redirect_uri", redirectUri.toString()),
                    pair("code_verifier", verifier)), StandardCharsets.UTF_8));
            Page tokenPage = send(client, context, tokenRequest);
            JSONObject token = new JSONObject(tokenPage.body);
            if (tokenPage.status != 200 || !token.has("access_token")) {
                throw new IOException("The token endpoint rejected the code: HTTP " + tokenPage.status + " "
                        + token.optString("error") + " " + token.optString("error_description"));
            }

            if (token.has("id_token") && !nonce.equals(claims(token.getString("id_token")).optString("nonce", null))) {
                throw new IOException("The nonce of the ID token does not match");
            }

            return token;
        }
    }

    /**
     * Utility to complete an authorization request built by someone else, such as the one the JDBC driver opens in
     * the browser for its U2M login. The redirect URI is the one of the request.
     *
     * @param authorizationRequest authorization endpoint URL with its query: client_id, redirect_uri, state...
     * @param verifyHost           whether to verify the certificate of the server
     * @return the utility, to call {@link #completeAuthorization(URI, String, String, String)}
     */
    public static OidcNativeLoginUtils forAuthorizationRequest(URI authorizationRequest, boolean verifyHost) {
        Map<String, String> query = queryOf(authorizationRequest);
        if (!query.containsKey("redirect_uri") || !query.containsKey("client_id")) {
            throw new IllegalArgumentException("Not an authorization request, it has no redirect_uri or client_id: "
                    + authorizationRequest.getScheme() + "://" + authorizationRequest.getHost() + authorizationRequest.getPath());
        }
        return new OidcNativeLoginUtils(null, query.get("client_id"), query.get("scope"), query.get("redirect_uri"), verifyHost);
    }

    /**
     * Does what the user would do in the browser with an authorization request: log in and accept the consent.
     * The code is not exchanged, that is up to whoever built the request (its PKCE verifier is not known here).
     *
     * @param authorizationRequest authorization endpoint URL with its query, as built by the client
     * @param userName             user to log in with
     * @param password             its password
     * @param tenant               tenant to choose in the login form, or null
     * @return the redirect URI the identity server sends the browser to, with the code and the state
     * @throws IOException if the login is rejected or any step of the flow fails
     */
    public URI completeAuthorization(URI authorizationRequest, String userName, String password, String tenant) throws IOException {
        try (CloseableHttpClient client = buildClient()) {
            return authorize(client, HttpClientContext.create(), authorizationRequest, userName, password, tenant);
        }
    }

    /**
     * Drives the login and the consent pages of an authorization request up to the redirect URI.
     */
    private URI authorize(CloseableHttpClient client, HttpClientContext context, URI authorizationRequest, String userName,
                          String password, String tenant) throws IOException {
        Page page = get(client, context, authorizationRequest);
        if (page.callback == null) {
            page = postLogin(client, context, page, userName, password, tenant);
        }
        for (int i = 0; page.callback == null && i < MAX_CONSENT_PAGES; i++) {
            page = allow(client, context, page);
        }
        if (page.callback == null) {
            throw new IOException("The OIDC flow did not reach the redirect URI. Last page: " + page.url);
        }
        return page.callback;
    }

    /**
     * Discovery URL of the SIS of a Keos cluster: the OIDC issuer is {@code <sis host><base path>/oidc}.
     *
     * @param host     host of the SIS ingress
     * @param basePath base path of SIS, '/sso' when null or empty
     * @return the discovery URL
     */
    public static String sisWellKnownUrl(String host, String basePath) {
        String path = basePath == null ? "" : basePath.trim();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            path = "/sso";
        }
        return "https://" + host + (path.startsWith("/") ? path : "/" + path) + "/oidc/.well-known";
    }

    /**
     * Claims of a JWT, without checking its signature.
     *
     * @param jwt compact JWT
     * @return its payload
     */
    public static JSONObject claims(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Not a JWT");
        }
        return new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    }

    static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String randomUrlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private Page postLogin(CloseableHttpClient client, HttpClientContext context, Page loginPage, String userName,
                           String password, String tenant) throws IOException {
        Element form = loginPage.document().select("form:has(input[name=password])").first();
        if (form == null) {
            throw new IOException("No login form found at " + loginPage.url);
        }
        List<NameValuePair> params = new ArrayList<>();
        for (Element input : form.select("input[name]")) {
            String name = input.attr("name");
            switch (name) {
                case "username":
                    params.add(pair(name, userName));
                    break;
                case "password":
                    params.add(pair(name, password));
                    break;
                case "customFields[tenant]":
                    params.add(pair(name, tenant == null ? input.attr("value") : tenant));
                    break;
                default:
                    if (!"submit".equals(input.attr("type"))) {
                        params.add(pair(name, input.attr("value")));
                    }
            }
        }
        HttpPost post = new HttpPost(loginPage.url.resolve(form.attr("action")));
        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        Page result = send(client, context, post);
        if (result.callback == null && result.document().select("form:has(input[name=password])").first() != null) {
            throw new IOException("Login rejected for user '" + userName + "'"
                    + (tenant == null ? "" : " and tenant '" + tenant + "'"));
        }
        return result;
    }

    private Page allow(CloseableHttpClient client, HttpClientContext context, Page consentPage) throws IOException {
        Element allow = consentPage.document().selectFirst("a#allow[href]");
        if (allow == null) {
            throw new IOException("No 'allow' link in the consent page at " + consentPage.url);
        }
        return get(client, context, consentPage.url.resolve(allow.attr("href")));
    }

    private CloseableHttpClient buildClient() throws IOException {
        HttpClientBuilder builder = HttpClientBuilder.create()
                .disableRedirectHandling()
                .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build());
        if (!verifyHost) {
            try {
                builder.setSSLSocketFactory(new SSLConnectionSocketFactory(
                        SSLContexts.custom().loadTrustMaterial((chain, authType) -> true).build(),
                        NoopHostnameVerifier.INSTANCE));
            } catch (java.security.GeneralSecurityException e) {
                throw new IOException(e);
            }
        }
        return builder.build();
    }

    private Page get(CloseableHttpClient client, HttpClientContext context, URI uri) throws IOException {
        return send(client, context, new HttpGet(uri));
    }

    /**
     * Executes the request and follows the redirects by hand, up to the loopback redirect URI.
     */
    private Page send(CloseableHttpClient client, HttpClientContext context, HttpUriRequest request) throws IOException {
        HttpUriRequest current = request;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            URI url = current.getURI();
            try (CloseableHttpResponse response = client.execute(current, context)) {
                int status = response.getStatusLine().getStatusCode();
                String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Header location = response.getFirstHeader("Location");
                if (status >= 300 && status < 400 && location != null) {
                    URI next = url.resolve(location.getValue());
                    if (isCallback(next)) {
                        return new Page(next, status, body, next);
                    }
                    logger.debug("OIDC redirect {} -> {}://{}{}", status, next.getScheme(), next.getHost(), next.getPath());
                    current = new HttpGet(next);
                    continue;
                }
                return new Page(url, status, body, null);
            }
        }
        throw new IOException("Too many redirects starting at " + request.getURI());
    }

    /**
     * Whether the URI is the redirect URI: same scheme, host, effective port and path. The port is -1 when the URL
     * has none, so 443 and 80 are taken for https and http.
     */
    boolean isCallback(URI uri) {
        return uri.getScheme() != null && uri.getHost() != null
                && redirectUri.getScheme().equalsIgnoreCase(uri.getScheme())
                && redirectUri.getHost().equalsIgnoreCase(uri.getHost())
                && effectivePort(redirectUri) == effectivePort(uri)
                && Objects.equals(redirectUri.getPath(), uri.getPath());
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static Map<String, String> queryOf(URI uri) {
        Map<String, String> query = new HashMap<>();
        if (uri.getRawQuery() != null) {
            for (String part : uri.getRawQuery().split("&")) {
                String[] kv = part.split("=", 2);
                query.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
        }
        return query;
    }

    private static NameValuePair pair(String name, String value) {
        return new BasicNameValuePair(name, value);
    }

    private static String form(List<NameValuePair> params) {
        return URLEncodedUtils.format(params, StandardCharsets.UTF_8);
    }

    private static final class Page {
        final URI url;

        final int status;

        final String body;

        final URI callback;

        Page(URI url, int status, String body, URI callback) {
            this.url = url;
            this.status = status;
            this.body = body;
            this.callback = callback;
        }

        Document document() {
            return Jsoup.parse(body, url.toString());
        }
    }
}
