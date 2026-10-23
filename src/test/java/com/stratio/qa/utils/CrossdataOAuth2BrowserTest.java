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

import com.sun.net.httpserver.HttpServer;
import okhttp3.mockwebserver.MockWebServer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The fake browser against the fake SIS of {@link OidcNativeLoginUtilsTest}, with a loopback listener in the role of
 * the driver. The URL is handed as the aspect does; {@link CrossdataOAuth2BrowserAspectIT} checks the weaving.
 */
public class CrossdataOAuth2BrowserTest {

    private MockWebServer server;

    private OidcNativeLoginUtilsTest.FakeSis sis;

    private HttpServer driver;

    private CompletableFuture<String> callback;

    private CrossdataOAuth2Browser browser;

    @BeforeMethod
    public void setUp() throws Exception {
        sis = new OidcNativeLoginUtilsTest.FakeSis();
        server = new MockWebServer();
        server.setDispatcher(sis);
        MockHttpsServer.enableTls(server);
        server.start();
        sis.base = "https://" + server.getHostName() + ":" + server.getPort();

        callback = new CompletableFuture<>();
        driver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        driver.createContext("/callback", exchange -> {
            byte[] body = "Authentication complete".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            callback.complete(exchange.getRequestURI().getQuery());
        });
        driver.start();
        browser = new CrossdataOAuth2Browser();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        browser.stop();
        driver.stop(0);
        server.shutdown();
    }

    private String authorizationRequest() {
        return sis.base + "/sso/oidc/oidcAuthorize?response_type=code&client_id=virtualizer-dbc"
                + "&redirect_uri=http%3A%2F%2F127.0.0.1%3A" + driver.getAddress().getPort() + "%2Fcallback"
                + "&state=driver-state&code_challenge=c&code_challenge_method=S256";
    }

    private static CrossdataOAuth2Settings.U2MLogin alice(String password) {
        return new CrossdataOAuth2Settings.U2MLogin("alice", password, "t1", false);
    }

    @Test
    public void theUrlOpenedByTheDriverEndsInItsCallbackWithTheCode() throws Exception {
        browser.start(alice(OidcNativeLoginUtilsTest.PASSWORD));

        assertThat(CrossdataOAuth2Browser.openInActiveBrowser(authorizationRequest())).isTrue();

        assertThat(callback.get(10, TimeUnit.SECONDS)).isEqualTo("code=code-of-alice&state=driver-state");
        assertThat(browser.requests()).isEqualTo(1);
        assertThat(browser.logins()).isEqualTo(1);
        assertThat(browser.errors()).isEmpty();
    }

    @Test
    public void outOfAnU2MConnectionTheUrlIsLeftToTheRealBrowser() {
        assertThat(CrossdataOAuth2Browser.openInActiveBrowser(authorizationRequest())).isFalse();

        browser.start(alice(OidcNativeLoginUtilsTest.PASSWORD));
        browser.stop();
        assertThat(CrossdataOAuth2Browser.openInActiveBrowser(authorizationRequest())).as("once the connection is open").isFalse();
        assertThat(browser.requests()).isZero();
        assertThat(callback.isDone()).isFalse();
    }

    @Test
    public void aRejectedLoginNeverReachesTheDriverAndIsReported() {
        browser.start(alice("wrong"));

        assertThat(CrossdataOAuth2Browser.openInActiveBrowser(authorizationRequest()))
                .as("a failed login is not thrown, the driver keeps waiting as with a real browser").isTrue();

        assertThat(callback.isDone()).isFalse();
        assertThat(browser.requests()).isEqualTo(1);
        assertThat(browser.logins()).isZero();
        assertThat(browser.errors()).hasSize(1);
        assertThat(browser.errors().get(0))
                .contains("OAuth2 browser login failed")
                .contains("Login rejected for user 'alice'")
                .doesNotContain("wrong");
    }

    @Test
    public void aDriverWithoutU2MIsReportedWhenTheLauncherIsMissing() {
        boolean launcherInClasspath;
        try {
            Class.forName(CrossdataOAuth2Browser.DRIVER_LAUNCHER + "$");
            launcherInClasspath = true;
        } catch (ClassNotFoundException e) {
            launcherInClasspath = false;
        }
        if (launcherInClasspath) {
            CrossdataOAuth2Browser.checkDriverLauncher();
        } else {
            try {
                CrossdataOAuth2Browser.checkDriverLauncher();
                throw new AssertionError("A driver without U2M should be reported");
            } catch (IllegalStateException e) {
                assertThat(e).hasMessageContaining(CrossdataOAuth2Browser.DRIVER_LAUNCHER + ".open(String)");
            }
        }
    }
}
