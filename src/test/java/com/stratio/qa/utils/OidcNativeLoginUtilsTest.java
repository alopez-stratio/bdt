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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stratio.qa.specs.CommonG;
import com.stratio.qa.specs.KeosSpec;
import io.cucumber.java.en.Given;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Walks a fake SIS served over TLS by a {@link MockWebServer}: discovery, authorize, a CAS-like login form, a
 * consent page whose "Allow" is a link, the redirect to the loopback callback (which nobody listens on) and the
 * token endpoint, which checks the PKCE verifier.
 */
public class OidcNativeLoginUtilsTest {

    private static final String CLIENT_ID = "virtualizer-dbc";

    private static final String PASSWORD = "pass-" + System.nanoTime();

    private MockWebServer server;

    private FakeSis sis;

    private String wellKnown;

    @BeforeMethod
    public void setUp() throws Exception {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        sis = new FakeSis();
        server = new MockWebServer();
        server.setDispatcher(sis);
        MockHttpsServer.enableTls(server);
        server.start();
        sis.base = "https://" + server.getHostName() + ":" + server.getPort();
        wellKnown = sis.base + "/sso/oidc/.well-known";
    }

    @AfterMethod
    public void tearDown() throws Exception {
        server.shutdown();
        ThreadProperty.remove("OIDC_ACCESS");
        ThreadProperty.remove("OIDC_REFRESH");
        ThreadProperty.remove("OIDC_RESPONSE");
        ThreadProperty.remove("KEOS_SIS_HOST");
        ThreadProperty.remove("SIS_VHOST");
        ThreadProperty.remove("SIS_BASEPATH");
    }

    @Test
    public void challengeMatchesRfc7636Example() {
        assertThat(OidcNativeLoginUtils.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    public void loginReturnsTheTokens() throws Exception {
        JSONObject tokens = new OidcNativeLoginUtils(wellKnown, CLIENT_ID, null, null, false)
                .login("alice", PASSWORD, "t1");

        assertThat(tokens.keySet()).contains("access_token", "refresh_token", "id_token");
        JSONObject access = OidcNativeLoginUtils.claims(tokens.getString("access_token"));
        assertThat(access.getString("sub")).isEqualTo("alice");
        assertThat(access.getString("tenant")).isEqualTo("t1");
        assertThat(sis.requested("scope")).isEqualTo(OidcNativeLoginUtils.DEFAULT_SCOPE);
        assertThat(sis.requested("prompt")).isEqualTo("login");
        assertThat(sis.requested("code_challenge_method")).isEqualTo("S256");
        assertThat(sis.requested("redirect_uri")).isEqualTo(OidcNativeLoginUtils.DEFAULT_REDIRECT_URI);
    }

    @Test
    public void loginReturnsTheTokenResponseAsItCame() throws Exception {
        JSONObject tokens = new OidcNativeLoginUtils(wellKnown, CLIENT_ID, null, null, false)
                .login("alice", PASSWORD, "t1");

        assertThat(tokens.getInt("expires_in")).isEqualTo(3600);
        assertThat(tokens.getString("token_type")).isEqualTo("Bearer");
        assertThat(OidcNativeLoginUtils.claims(tokens.getString("id_token")).getString("nonce"))
                .isEqualTo(sis.requested("nonce"));
    }

    @Test
    public void webClientRedirectUriWithoutPortIsRecognised() throws Exception {
        String redirect = "https://reports.example.com/login/callback";

        JSONObject tokens = new OidcNativeLoginUtils(wellKnown, CLIENT_ID, null, redirect, false)
                .login("alice", PASSWORD, "t1");

        assertThat(tokens.has("access_token")).isTrue();
        assertThat(sis.requested("redirect_uri")).isEqualTo(redirect);
        assertThat(sis.exchangedRedirectUri).isEqualTo(redirect);
    }

    @Test
    public void webClientRedirectUriWithPortIsRecognised() throws Exception {
        String redirect = "https://pitservices.labs.stratio.com:8888/auth/callback";

        new OidcNativeLoginUtils(wellKnown, CLIENT_ID, null, redirect, false).login("alice", PASSWORD, "t1");

        assertThat(sis.exchangedRedirectUri).isEqualTo(redirect);
    }

    @Test
    public void callbackIsComparedByEffectiveSchemeHostPortAndPath() {
        OidcNativeLoginUtils https = new OidcNativeLoginUtils(wellKnown, CLIENT_ID, null,
                "https://Reports.example.com/login/callback", false);

        assertThat(https.isCallback(URI.create("https://reports.example.com/login/callback?code=c"))).isTrue();
        assertThat(https.isCallback(URI.create("https://reports.example.com:443/login/callback"))).isTrue();
        assertThat(https.isCallback(URI.create("https://reports.example.com:8443/login/callback"))).isFalse();
        assertThat(https.isCallback(URI.create("http://reports.example.com/login/callback"))).isFalse();
        assertThat(https.isCallback(URI.create("https://other.example.com/login/callback"))).isFalse();
        assertThat(https.isCallback(URI.create("https://reports.example.com/login/other"))).isFalse();
        assertThat(https.isCallback(URI.create("/login/callback"))).isFalse();

        OidcNativeLoginUtils loopback = new OidcNativeLoginUtils(wellKnown, CLIENT_ID, null, null, false);
        assertThat(loopback.isCallback(URI.create("http://127.0.0.1:8765/callback?code=c"))).isTrue();
        assertThat(loopback.isCallback(URI.create("http://127.0.0.1:8766/callback"))).isFalse();
        assertThat(loopback.isCallback(URI.create("http://127.0.0.1/callback"))).isFalse();
    }

    @Test
    public void redirectUriMustBeAbsolute() {
        assertThatThrownBy(() -> new OidcNativeLoginUtils(wellKnown, CLIENT_ID, null, "/callback", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
    }

    @Test
    public void scopeIsSentAsGiven() throws Exception {
        new OidcNativeLoginUtils(wellKnown, CLIENT_ID, "openid", null, false).login("alice", PASSWORD, "t1");

        assertThat(sis.requested("scope")).isEqualTo("openid");
    }

    @Test
    public void wrongPasswordIsRejectedWithoutLeakingIt() {
        assertThatThrownBy(() -> new OidcNativeLoginUtils(wellKnown, CLIENT_ID, null, null, false)
                .login("alice", "not-the-password", "t1"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Login rejected for user 'alice' and tenant 't1'")
                .hasMessageNotContaining("not-the-password");
    }

    @Test
    public void tokenEndpointFailureIsReported() {
        sis.rejectCode = true;

        assertThatThrownBy(() -> new OidcNativeLoginUtils(wellKnown, CLIENT_ID, null, null, false)
                .login("alice", PASSWORD, "t1"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("token endpoint rejected the code")
                .hasMessageContaining("invalid_grant");
    }

    @Test
    public void callbackWithAnotherStateIsRefused() {
        sis.forgeState = true;

        assertThatThrownBy(() -> new OidcNativeLoginUtils(wellKnown, CLIENT_ID, null, null, false)
                .login("alice", PASSWORD, "t1"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("state");
    }

    @Test
    public void idTokenWithAnotherNonceIsRefused() {
        sis.forgeNonce = true;

        assertThatThrownBy(() -> new OidcNativeLoginUtils(wellKnown, CLIENT_ID, null, null, false)
                .login("alice", PASSWORD, "t1"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("nonce");
    }

    @Test
    public void certificateIsVerifiedUnlessAskedNot() {
        assertThatThrownBy(() -> new OidcNativeLoginUtils(wellKnown, CLIENT_ID, null, null, true)
                .login("alice", PASSWORD, "t1"))
                .isInstanceOf(javax.net.ssl.SSLException.class);
    }

    @Test
    public void stepSavesTheTokensInVariables() throws Exception {
        KeosSpec keosSpec = new KeosSpec(new CommonG());

        keosSpec.getOidcToken(wellKnown, CLIENT_ID, "alice", PASSWORD, "t1", null, null,
                " without host name verification", "OIDC_ACCESS", "OIDC_REFRESH", null);

        assertThat(OidcNativeLoginUtils.claims(ThreadProperty.get("OIDC_ACCESS")).getString("sub")).isEqualTo("alice");
        assertThat(ThreadProperty.get("OIDC_REFRESH")).isEqualTo("refresh-of-alice");
        assertThat(ThreadProperty.get("OIDC_RESPONSE")).isNull();
    }

    @Test
    public void stepWithoutRefreshVariableLeavesItUnset() throws Exception {
        KeosSpec keosSpec = new KeosSpec(new CommonG());

        keosSpec.getOidcToken(wellKnown, CLIENT_ID, "alice", PASSWORD, "t1", null, null,
                " without host name verification", "OIDC_ACCESS", null, null);

        assertThat(ThreadProperty.get("OIDC_ACCESS")).isNotEmpty();
        assertThat(ThreadProperty.get("OIDC_REFRESH")).isNull();
    }

    @Test
    public void stepSavesTheWholeTokenResponseAsJson() throws Exception {
        KeosSpec keosSpec = new KeosSpec(new CommonG());

        keosSpec.getOidcToken(wellKnown, CLIENT_ID, "alice", PASSWORD, "t1", null, null,
                " without host name verification", null, null, "OIDC_RESPONSE");

        JSONObject response = new JSONObject(ThreadProperty.get("OIDC_RESPONSE"));
        assertThat(response.getInt("expires_in")).isEqualTo(3600);
        assertThat(response.getString("token_type")).isEqualTo("Bearer");
        assertThat(response.getString("refresh_token")).isEqualTo("refresh-of-alice");
        assertThat(OidcNativeLoginUtils.claims(response.getString("id_token")).getString("sub")).isEqualTo("alice");
        assertThat(ThreadProperty.get("OIDC_ACCESS")).isNull();
    }

    @Test
    public void stepAcceptsARedirectUri() throws Exception {
        KeosSpec keosSpec = new KeosSpec(new CommonG());

        keosSpec.getOidcToken(wellKnown, CLIENT_ID, "alice", PASSWORD, "t1", null,
                "https://reports.example.com/login/callback", " without host name verification", "OIDC_ACCESS", null, null);

        assertThat(sis.exchangedRedirectUri).isEqualTo("https://reports.example.com/login/callback");
    }

    @Test
    public void stepNeedsSomewhereToSaveTheResult() {
        KeosSpec keosSpec = new KeosSpec(new CommonG());

        assertThatThrownBy(() -> keosSpec.getOidcToken(wellKnown, CLIENT_ID, "alice", PASSWORD, "t1", null, null,
                " without host name verification", null, null, null))
                .hasMessageContaining("Say where to save the result");
    }

    @Test
    public void wellKnownUrlIsBuiltFromTheSisIngress() {
        assertThat(OidcNativeLoginUtils.sisWellKnownUrl("sis.k8s.romeo.labs.stratio.com", "/sso"))
                .isEqualTo("https://sis.k8s.romeo.labs.stratio.com/sso/oidc/.well-known");
        assertThat(OidcNativeLoginUtils.sisWellKnownUrl("sis.x", null)).isEqualTo("https://sis.x/sso/oidc/.well-known");
        assertThat(OidcNativeLoginUtils.sisWellKnownUrl("sis.x", "/sso/")).isEqualTo("https://sis.x/sso/oidc/.well-known");
        assertThat(OidcNativeLoginUtils.sisWellKnownUrl("sis.x", "/")).isEqualTo("https://sis.x/sso/oidc/.well-known");
        assertThat(OidcNativeLoginUtils.sisWellKnownUrl("sis.x", "auth")).isEqualTo("https://sis.x/auth/oidc/.well-known");
    }

    @Test
    public void stepWithoutDiscoveryUsesTheHostPublishedByKeos() throws Exception {
        ThreadProperty.set("KEOS_SIS_HOST", server.getHostName() + ":" + server.getPort());
        KeosSpec keosSpec = new KeosSpec(new CommonG());

        keosSpec.getOidcToken(null, CLIENT_ID, "alice", PASSWORD, "t1", null, null,
                " without host name verification", "OIDC_ACCESS", null, null);

        assertThat(OidcNativeLoginUtils.claims(ThreadProperty.get("OIDC_ACCESS")).getString("sub")).isEqualTo("alice");
    }

    @Test
    public void stepWithoutDiscoveryNorKeosHostSaysSo() {
        KeosSpec keosSpec = new KeosSpec(new CommonG());

        assertThatThrownBy(() -> keosSpec.getOidcToken(null, CLIENT_ID, "alice", PASSWORD, "t1", null, null,
                null, "OIDC_ACCESS", null, null))
                .hasMessageContaining("KEOS_SIS_HOST is not set");
    }

    /**
     * Cucumber hands the method only the top-level groups of the step regex, so a group nested in an optional one
     * is lost and the run fails with an arity mismatch. The unit tests above call the method directly and would
     * not notice.
     */
    @Test
    public void stepRegexGivesAsManyGroupsAsTheMethodHasParameters() throws Exception {
        Method method = Arrays.stream(KeosSpec.class.getMethods())
                .filter(m -> m.getName().equals("getOidcToken")).findFirst().orElseThrow();
        Pattern pattern = Pattern.compile(method.getAnnotation(Given.class).value());
        String head = "I get an OIDC token using discovery 'https://sis/.well-known' with client id 'c', user 'u', "
                + "password 'p' and tenant 't'";

        // 1 discovery, 2 client, 3 user, 4 password, 5 tenant, 6 scope, 7 redirect uri, 8 host verification,
        // 9 access token, 10 refresh token, 11 token response
        Matcher minimal = pattern.matcher(head + " and save the access token in 'A'");
        assertThat(minimal.matches()).isTrue();
        assertThat(minimal.groupCount()).isEqualTo(method.getParameterCount());
        assertThat(minimal.group(6)).isNull();
        assertThat(minimal.group(7)).isNull();
        assertThat(minimal.group(8)).isNull();
        assertThat(minimal.group(9)).isEqualTo("A");
        assertThat(minimal.group(10)).isNull();
        assertThat(minimal.group(11)).isNull();

        Matcher full = pattern.matcher(head + " and scope 'openid login_data' with redirect uri 'https://app/cb'"
                + " without host name verification and save the access token in 'A' and the refresh token in 'R'"
                + " and save the token response in 'J'");
        assertThat(full.matches()).isTrue();
        assertThat(full.group(1)).isEqualTo("https://sis/.well-known");
        assertThat(full.group(6)).isEqualTo("openid login_data");
        assertThat(full.group(7)).isEqualTo("https://app/cb");
        assertThat(full.group(8)).isNotNull();
        assertThat(full.group(9)).isEqualTo("A");
        assertThat(full.group(10)).isEqualTo("R");
        assertThat(full.group(11)).isEqualTo("J");

        Matcher responseOnly = pattern.matcher(head + " and save the token response in 'J'");
        assertThat(responseOnly.matches()).isTrue();
        assertThat(responseOnly.group(9)).isNull();
        assertThat(responseOnly.group(11)).isEqualTo("J");

        Matcher discovered = pattern.matcher("I get an OIDC token with client id 'c', user 'u', password 'p' and tenant 't'"
                + " and save the access token in 'A'");
        assertThat(discovered.matches()).isTrue();
        assertThat(discovered.groupCount()).isEqualTo(method.getParameterCount());
        assertThat(discovered.group(1)).isNull();
        assertThat(discovered.group(2)).isEqualTo("c");
    }

    private static String b64(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String jwt(JSONObject claims) {
        return b64("{\"alg\":\"RS512\"}") + "." + b64(claims.toString()) + "." + b64("signature");
    }

    private static Map<String, String> params(String query) {
        Map<String, String> result = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            for (String part : query.split("&")) {
                String[] kv = part.split("=", 2);
                result.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
        }
        return result;
    }

    private static MockResponse redirect(String location) {
        return new MockResponse().setResponseCode(302).setHeader("Location", location);
    }

    private static MockResponse html(String body) {
        return new MockResponse().setHeader("Content-Type", "text/html").setBody(body);
    }

    private static final class FakeSis extends Dispatcher {

        private static final String LOGIN_FORM = "<html><body><form method=\"post\" action=\"login\">"
                + "<input type=\"text\" name=\"username\"/><input type=\"password\" name=\"password\"/>"
                + "<input type=\"text\" name=\"customFields[tenant]\"/>"
                + "<input type=\"hidden\" name=\"execution\" value=\"e1s1\"/>"
                + "<input type=\"hidden\" name=\"_eventId\" value=\"submit\"/>"
                + "<input type=\"submit\" name=\"submit\" value=\"Login\"/></form></body></html>";

        String base;

        boolean rejectCode;

        boolean forgeState;

        boolean forgeNonce;

        String exchangedRedirectUri;

        private Map<String, String> authorizeParams = new HashMap<>();

        private String user;

        private String tenant;

        String requested(String name) {
            return authorizeParams.get(name);
        }

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            String path = request.getPath();
            String route = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
            String query = path.contains("?") ? path.substring(path.indexOf('?') + 1) : "";
            switch (route) {
                case "/sso/oidc/.well-known":
                    return new MockResponse().setHeader("Content-Type", "application/json").setBody(new JSONObject()
                            .put("issuer", base + "/sso/oidc")
                            .put("authorization_endpoint", base + "/sso/oidc/oidcAuthorize")
                            .put("token_endpoint", base + "/sso/oidc/oidcAccessToken").toString());
                case "/sso/oidc/oidcAuthorize":
                    if (query.contains("approved=1")) {
                        String state = forgeState ? "forged" : authorizeParams.get("state");
                        return redirect(authorizeParams.get("redirect_uri") + "?code=code-of-" + user + "&state=" + state);
                    }
                    authorizeParams = params(query);
                    return redirect(base + "/sso/login?service=" + base + "/sso/oidc/oidcAuthorize");
                case "/sso/login":
                    if ("POST".equals(request.getMethod())) {
                        return login(params(request.getBody().readUtf8()));
                    }
                    return html(LOGIN_FORM);
                case "/sso/oidc/oidcAccessToken":
                    return token(params(request.getBody().readUtf8()));
                default:
                    return new MockResponse().setResponseCode(404);
            }
        }

        private MockResponse login(Map<String, String> form) {
            if (!PASSWORD.equals(form.get("password")) || !"e1s1".equals(form.get("execution"))) {
                return html(LOGIN_FORM);
            }
            user = form.get("username");
            tenant = form.get("customFields[tenant]");
            return html("<html><body><form class=\"consent\" onsubmit=\"return false\">"
                    + "<a href=\"/sso/login\" id=\"cancel\">Deny</a>"
                    + "<a href=\"" + base + "/sso/oidc/oidcAuthorize?approved=1&amp;approvalKey=k\" id=\"allow\">Allow</a>"
                    + "</form></body></html>");
        }

        private MockResponse token(Map<String, String> form) {
            boolean verifierOk = OidcNativeLoginUtils.challenge(String.valueOf(form.get("code_verifier")))
                    .equals(authorizeParams.get("code_challenge"));
            exchangedRedirectUri = form.get("redirect_uri");
            boolean codeOk = ("code-of-" + user).equals(form.get("code"))
                    && authorizeParams.get("redirect_uri").equals(form.get("redirect_uri"))
                    && CLIENT_ID.equals(form.get("client_id"))
                    && "authorization_code".equals(form.get("grant_type"));
            if (rejectCode || !verifierOk || !codeOk) {
                return new MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
                        .setBody("{\"error\":\"invalid_grant\"}");
            }
            JSONObject access = new JSONObject().put("iss", base + "/sso/oidc").put("sub", user)
                    .put("tenant", tenant).put("client_id", CLIENT_ID);
            JSONObject id = new JSONObject().put("sub", user)
                    .put("nonce", forgeNonce ? "forged" : authorizeParams.get("nonce"));
            return new MockResponse().setHeader("Content-Type", "application/json").setBody(new JSONObject()
                    .put("access_token", jwt(access)).put("id_token", jwt(id))
                    .put("refresh_token", "refresh-of-" + user).put("token_type", "Bearer")
                    .put("expires_in", 3600).toString());
        }
    }
}
