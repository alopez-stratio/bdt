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

package com.stratio.qa.specs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.asynchttpclient.AsyncHttpClient;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.stratio.qa.utils.CookiesUtils;
import com.stratio.qa.utils.MockHttpsServer;
import com.stratio.qa.utils.ThreadProperty;

import io.netty.handler.codec.http.cookie.Cookie;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Covers the Keos SSO login steps end to end: {@code GosecSSOUtils} walks a fake Gosec SSO served over
 * TLS by a {@link MockWebServer} (see {@link MockHttpsServer}), and {@code KeosSpec} stores what comes
 * back in the session, the thread properties and the cookies cache.
 * <p>
 * The fake SSO follows the CAS flow {@code GosecSSOUtils} expects: {@code /login} redirects to a form
 * carrying {@code lt} and {@code execution}, the credentials are posted to it, then the terms and
 * conditions are accepted and the SSO cookies are set. The {@code _oauth2_proxy} cookie is only handed
 * out to a request that is already authenticated, as oauth2-proxy does.
 * <p>
 * The cookies cache is static and cannot be cleared, so every test logs in with its own random user.
 */
public class KeosSpecSsoLoginTest {

    private static final String WITHOUT_HOST_VERIFICATION = " without host name verification";

    private static final String TENANT = "NONE";

    private static final List<String> THREAD_PROPERTIES = List.of("stratioCookie", "oauth2ProxyCookie", "user",
            "keosGovernanceAuthCookie", "isKeosEnv", "discovery_sso_cookie_name", "discovery-sso");

    private MockWebServer server;

    private FakeSso sso;

    private AsyncHttpClient client;

    private CommonG commong;

    private KeosSpec keosSpec;

    private String ssoHost;

    private String user;

    @BeforeMethod
    public void setUp() throws Exception {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        sso = new FakeSso();
        server = new MockWebServer();
        server.setDispatcher(sso);
        MockHttpsServer.enableTls(server);
        server.start();
        ssoHost = server.getHostName() + ":" + server.getPort();
        user = "user-" + UUID.randomUUID();

        commong = new CommonG();
        client = asyncHttpClient(config()
                .setKeepAlive(false)
                .setUseInsecureTrustManager(true)
                .setConnectTimeout(2000)
                .setRequestTimeout(5000)
                .setReadTimeout(5000)
                .build());
        commong.setClient(client);
        commong.setRestProtocol("https://");
        commong.setRestHost(server.getHostName());
        commong.setRestPort(":" + server.getPort());
        keosSpec = new KeosSpec(commong);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (client != null && !client.isClosed()) {
            client.close();
        }
        if (server != null) {
            server.shutdown();
        }
        commong.clearCookies();
        THREAD_PROPERTIES.forEach(ThreadProperty::remove);
    }

    private void login(String tokenType) throws Exception {
        keosSpec.setGoSecSSOCookieKeos(tokenType, ssoHost, user, sso.password, TENANT, null, WITHOUT_HOST_VERIFICATION, null);
    }

    private void loginFromCache(String tokenType) throws Exception {
        keosSpec.setGoSecSSOCookieKeosCache(tokenType, ssoHost, user, sso.password, TENANT, null, WITHOUT_HOST_VERIFICATION, null);
    }

    private Map<String, String> sessionCookies() {
        Map<String, String> cookies = new HashMap<>();
        commong.getCookies().forEach(cookie -> cookies.put(cookie.name(), cookie.value()));
        return cookies;
    }

    private List<Cookie> cached(String tokenType) {
        return CookiesUtils.getCookiesFromCache(ssoHost, user, TENANT, tokenType).getRight();
    }

    /**
     * Sends a request to the fake SSO host through CommonG and returns the Cookie header it carried
     */
    private String cookieHeaderOfARequest() throws Exception {
        commong.generateRequest("GET", false, null, null, "/api/things", "", "json").get();
        return sso.apiCookieHeaders.get(sso.apiCookieHeaders.size() - 1);
    }

    // -----------------------------------------------------------------------
    // Login
    // -----------------------------------------------------------------------

    @Test
    public void loginStoresTheSsoCookiesInTheSession() throws Exception {
        login(null);

        assertThat(commong.getCookies()).extracting(Cookie::name).as("Session cookie names")
                .containsExactly("user", "_oauth2_proxy", "stratio-cookie");
        assertThat(sessionCookies()).as("Session cookies")
                .containsEntry("stratio-cookie", sso.stratioCookie)
                .containsEntry("user", user)
                .containsEntry("_oauth2_proxy", "proxy-1");
    }

    @Test
    public void loginPublishesTheSsoCookiesAsThreadProperties() throws Exception {
        login(null);

        assertThat(ThreadProperty.get("stratioCookie")).as("stratioCookie").isEqualTo(sso.stratioCookie);
        assertThat(ThreadProperty.get("oauth2ProxyCookie")).as("oauth2ProxyCookie").isEqualTo("proxy-1");
        assertThat(ThreadProperty.get("user")).as("user").isEqualTo(user);
    }

    @Test
    public void loginPostsTheCredentialsAndTheTenant() throws Exception {
        login(null);

        assertThat(sso.credentials).as("Posted credentials")
                .containsEntry("username", user)
                .containsEntry("password", sso.password)
                .containsEntry("tenant", TENANT)
                .containsEntry("lt", "LT-1")
                .doesNotContainKey("ldap");
    }

    @Test
    public void loginPostsTheLdapWhenGiven() throws Exception {
        keosSpec.setGoSecSSOCookieKeos(null, ssoHost, user, sso.password, TENANT, "corp-ldap", WITHOUT_HOST_VERIFICATION, null);

        assertThat(sso.credentials).as("Posted credentials").containsEntry("ldap", "corp-ldap");
    }

    @Test
    public void loginStoresTheSessionInTheCacheWithTheTokenExpiration() throws Exception {
        login(null);

        assertThat(cached(null)).extracting(Cookie::name).as("Cached cookie names")
                .containsExactly("user", "_oauth2_proxy", "stratio-cookie");
        assertThat(CookiesUtils.getCookiesFromCache(ssoHost, user, TENANT, null).getLeft())
                .as("Cached expiration").isEqualTo(sso.stratioCookieExp);
    }

    @Test
    public void loginAlwaysRefreshesTheCache() throws Exception {
        login(null);
        sso.issueNewToken(3600);

        login(null);

        assertThat(sso.logins.get()).as("Logins against the SSO").isEqualTo(2);
        assertThat(cached(null)).extracting(Cookie::value).as("Cached cookie values").contains(sso.stratioCookie);
    }

    @Test
    public void aGovernanceLoginAlsoStoresTheGovernanceCookie() throws Exception {
        ThreadProperty.set("isKeosEnv", "true");

        login(" governance");

        assertThat(sessionCookies()).as("Session cookies").containsEntry("stratio-governance-auth", "governance-auth");
        assertThat(ThreadProperty.get("keosGovernanceAuthCookie")).as("keosGovernanceAuthCookie").isEqualTo("governance-auth");
    }

    @Test
    public void aDiscoveryLoginOnlyKeepsTheConfiguredCookie() throws Exception {
        ThreadProperty.set("discovery_sso_cookie_name", "discovery-sso");

        login(" discovery");

        assertThat(commong.getCookies()).extracting(Cookie::name).as("Session cookie names").containsExactly("discovery-sso");
        assertThat(ThreadProperty.get("discovery-sso")).as("discovery-sso").isEqualTo("discovery");
    }

    @Test
    public void aDiscoveryLoginWithoutTheCookieNameFails() {
        assertThatThrownBy(() -> login(" discovery"))
                .as("Discovery login without discovery_sso_cookie_name")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("discovery_sso_cookie_name");
    }

    @Test
    public void aFailedLoginIsNotCached() throws Exception {
        keosSpec.setGoSecSSOCookieKeos(null, ssoHost, user, "wrong-password", TENANT, null, WITHOUT_HOST_VERIFICATION, null);

        assertThat(commong.getCookies()).extracting(Cookie::value).as("Session cookie values").containsOnly("");
        assertThat(CookiesUtils.getCookiesFromCache(ssoHost, user, TENANT, null)).as("Cache entry after a failed login").isNull();
    }

    // -----------------------------------------------------------------------
    // Login from cache
    // -----------------------------------------------------------------------

    @Test
    public void aCachedLoginLogsInWhenNothingIsCached() throws Exception {
        loginFromCache(null);

        assertThat(sso.logins.get()).as("Logins against the SSO").isEqualTo(1);
        assertThat(sessionCookies()).as("Session cookies").containsEntry("stratio-cookie", sso.stratioCookie);
        assertThat(CookiesUtils.hasCookiesInCache(ssoHost, user, TENANT, null)).as("Cache hit after the login").isTrue();
    }

    @Test
    public void aCachedLoginReusesTheTokenWithoutCallingTheSso() throws Exception {
        login(null);
        String token = sso.stratioCookie;
        int requests = server.getRequestCount();
        commong.clearCookies();
        ThreadProperty.remove("stratioCookie");

        loginFromCache(null);

        assertThat(server.getRequestCount()).as("Requests to the SSO during the cached login").isEqualTo(requests);
        assertThat(sessionCookies()).as("Session cookies").containsEntry("stratio-cookie", token);
        assertThat(ThreadProperty.get("stratioCookie")).as("stratioCookie").isEqualTo(token);
    }

    @Test
    public void aCachedLoginLogsInAgainWhenTheTokenIsAboutToExpire() throws Exception {
        sso.issueNewToken(5 * 60);
        login(null);
        sso.issueNewToken(3600);

        loginFromCache(null);

        assertThat(sso.logins.get()).as("Logins against the SSO").isEqualTo(2);
        assertThat(sessionCookies()).as("Session cookies").containsEntry("stratio-cookie", sso.stratioCookie);
    }

    @Test
    public void aCachedLoginIsKeptApartPerTokenType() throws Exception {
        ThreadProperty.set("isKeosEnv", "true");
        login(null);

        loginFromCache(" governance");

        assertThat(sso.logins.get()).as("Logins against the SSO").isEqualTo(2);
        assertThat(sessionCookies()).as("Session cookies").containsKey("stratio-governance-auth");
    }

    @Test
    public void aCachedLoginIsNotServedFromAFailedLogin() throws Exception {
        keosSpec.setGoSecSSOCookieKeos(null, ssoHost, user, "wrong-password", TENANT, null, WITHOUT_HOST_VERIFICATION, null);

        loginFromCache(null);

        assertThat(sso.logins.get()).as("Logins against the SSO").isEqualTo(2);
        assertThat(sessionCookies()).as("Session cookies").containsEntry("stratio-cookie", sso.stratioCookie);
    }

    // -----------------------------------------------------------------------
    // Changing the session after a login
    // -----------------------------------------------------------------------

    @Test
    public void aCachedLoginRestoresACookieDeletedAfterTheLogin() throws Exception {
        login(null);
        commong.deleteCookie("stratio-cookie");
        assertThat(cookieHeaderOfARequest()).as("Cookie header after the delete").doesNotContain("stratio-cookie=");

        loginFromCache(null);

        assertThat(sso.logins.get()).as("Logins against the SSO").isEqualTo(1);
        assertThat(cookieHeaderOfARequest()).as("Cookie header after the cached login")
                .contains("stratio-cookie=" + sso.stratioCookie, "user=" + user, "_oauth2_proxy=proxy-1");
    }

    @Test
    public void aCachedLoginRestoresACookieDeletedAfterAPreviousCachedLogin() throws Exception {
        login(null);
        loginFromCache(null);
        commong.deleteCookie("stratio-cookie");

        loginFromCache(null);

        assertThat(sso.logins.get()).as("Logins against the SSO").isEqualTo(1);
        assertThat(cookieHeaderOfARequest()).as("Cookie header after the second cached login")
                .contains("stratio-cookie=" + sso.stratioCookie);
    }

    @Test
    public void aCachedLoginRestoresACookieReplacedAfterTheLogin() throws Exception {
        login(null);
        new RestSpec(commong).replaceCookie("stratio-cookie", "tampered");

        loginFromCache(null);

        assertThat(sessionCookies()).as("Session cookies").containsEntry("stratio-cookie", sso.stratioCookie);
    }

    @Test
    public void aCachedLoginDropsACookieAddedAfterTheLogin() throws Exception {
        login(null);
        commong.addCookie("extra", "value");
        assertThat(cookieHeaderOfARequest()).as("Cookie header after the add").contains("extra=value");

        loginFromCache(null);

        assertThat(commong.getCookies()).extracting(Cookie::name).as("Session cookie names")
                .containsExactly("user", "_oauth2_proxy", "stratio-cookie");
    }

    @Test
    public void aCachedLoginRestoresTheSessionAfterClearingTheCookies() throws Exception {
        login(null);
        commong.clearCookies();

        loginFromCache(null);

        assertThat(sso.logins.get()).as("Logins against the SSO").isEqualTo(1);
        assertThat(commong.getCookies()).extracting(Cookie::name).as("Session cookie names")
                .containsExactly("user", "_oauth2_proxy", "stratio-cookie");
    }

    /**
     * A Gosec SSO reduced to the requests {@code GosecSSOUtils} makes, plus any other path recorded as
     * a service call so a test can see which cookies it carried
     */
    private static final class FakeSso extends Dispatcher {

        final String password = "secret";

        final AtomicInteger logins = new AtomicInteger();

        final List<String> apiCookieHeaders = new CopyOnWriteArrayList<>();

        volatile Map<String, String> credentials = new HashMap<>();

        volatile String stratioCookie;

        volatile long stratioCookieExp;

        private final AtomicInteger proxyCookies = new AtomicInteger();

        private volatile String loggedUser;

        FakeSso() {
            issueNewToken(3600);
        }

        void issueNewToken(long secondsLeft) {
            stratioCookieExp = System.currentTimeMillis() / 1000 + secondsLeft;
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            stratioCookie = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                    + encoder.encodeToString(("{\"exp\":" + stratioCookieExp + "}").getBytes(StandardCharsets.UTF_8))
                    + "." + UUID.randomUUID().toString().replace("-", "");
        }

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            String path = request.getPath();
            String cookieHeader = request.getHeader("Cookie") == null ? "" : request.getHeader("Cookie");
            boolean authenticated = cookieHeader.contains("stratio-cookie=");

            if (path.equals("/login")) {
                logins.incrementAndGet();
                return new MockResponse().setResponseCode(302).setHeader("Location", "/sso/login?service=keos");
            }
            if (path.startsWith("/sso/login") && "GET".equals(request.getMethod())) {
                return page("<input name=\"lt\" value=\"LT-1\"/><input name=\"execution\" value=\"e1s1\"/>");
            }
            if (path.startsWith("/sso/login")) {
                Map<String, String> form = parseForm(request.getBody().readUtf8());
                if ("e1s1".equals(form.get("execution"))) {
                    credentials = form;
                    if (!password.equals(form.get("password"))) {
                        return page("Invalid credentials").setResponseCode(401);
                    }
                    loggedUser = form.get("username");
                    return page("<input name=\"execution\" value=\"e1s2\"/><input name=\"_eventId\" value=\"submit\"/>");
                }
                if ("e1s2".equals(form.get("execution"))) {
                    return page("Welcome")
                            .addHeader("Set-Cookie", "stratio-cookie=" + stratioCookie + "; Path=/")
                            .addHeader("Set-Cookie", "user=" + loggedUser + "; Path=/");
                }
                return page("Unexpected step").setResponseCode(400);
            }
            if (path.equals("/service/gosecmanagement")) {
                MockResponse response = page("Management");
                if (authenticated) {
                    response.addHeader("Set-Cookie", "_oauth2_proxy=proxy-" + proxyCookies.incrementAndGet() + "; Path=/")
                            .addHeader("Set-Cookie", "discovery-sso=discovery; Path=/");
                }
                return response;
            }
            if (path.endsWith("/dictionary/user/profile")) {
                MockResponse response = page("Profile");
                if (authenticated) {
                    response.addHeader("Set-Cookie", "_oauth2_proxy=proxy-" + proxyCookies.incrementAndGet() + "; Path=/")
                            .addHeader("Set-Cookie", "stratio-governance-auth=governance-auth; Path=/");
                }
                return response;
            }
            apiCookieHeaders.add(cookieHeader);
            return new MockResponse().setResponseCode(200).setBody("{}");
        }

        private static MockResponse page(String body) {
            return new MockResponse().setResponseCode(200).setHeader("Content-Type", "text/html")
                    .setBody("<html><body><form>" + body + "</form></body></html>");
        }

        private static Map<String, String> parseForm(String body) {
            Map<String, String> form = new HashMap<>();
            for (String pair : body.split("&")) {
                String[] parts = pair.split("=", 2);
                form.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
            }
            return form;
        }
    }
}
