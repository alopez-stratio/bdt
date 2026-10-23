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
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The aspect woven into the real driver: calls the driver's browser launcher, as its U2M login does, and checks that
 * the fake browser gets the URL. It needs the load-time weaver (failsafe runs with it) and a driver with U2M
 * (crossdata-drivers #531, -Dcrossdata-drivers.version=5.0.0-PR531-SNAPSHOT -Dkafka-scala.version=2.13); with an
 * older driver it is skipped.
 */
public class CrossdataOAuth2BrowserAspectIT {

    private MockWebServer server;

    private OidcNativeLoginUtilsTest.FakeSis sis;

    private HttpServer driver;

    private CompletableFuture<String> callback;

    private CrossdataOAuth2Browser browser;

    private Object launcher;

    private Method open;

    @BeforeMethod
    public void setUp() throws Exception {
        try {
            Class<?> launcherClass = Class.forName(CrossdataOAuth2Browser.DRIVER_LAUNCHER + "$");
            launcher = launcherClass.getField("MODULE$").get(null);
            open = launcherClass.getMethod("open", String.class);
        } catch (ClassNotFoundException e) {
            throw new SkipException("The Crossdata driver in the classpath has no OAuth2 U2M");
        }
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

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (browser != null) {
            browser.stop();
        }
        if (driver != null) {
            driver.stop(0);
        }
        if (server != null) {
            try {
                server.shutdown();
            } catch (java.io.IOException e) {
                // nothing left to release
            }
        }
    }

    private String authorizationRequest() {
        return sis.base + "/sso/oidc/oidcAuthorize?response_type=code&client_id=virtualizer-dbc"
                + "&redirect_uri=http%3A%2F%2F127.0.0.1%3A" + driver.getAddress().getPort() + "%2Fcallback"
                + "&state=driver-state&code_challenge=c&code_challenge_method=S256";
    }

    @Test
    public void theDriverOpensTheFakeBrowserDuringAnU2MConnection() throws Exception {
        browser.start(new CrossdataOAuth2Settings.U2MLogin("alice", OidcNativeLoginUtilsTest.PASSWORD, "t1", false));

        open.invoke(launcher, authorizationRequest());

        assertThat(callback.get(10, TimeUnit.SECONDS)).isEqualTo("code=code-of-alice&state=driver-state");
        assertThat(browser.requests()).isEqualTo(1);
        assertThat(browser.logins()).isEqualTo(1);
        assertThat(browser.errors()).isEmpty();
    }

    @Test
    public void outOfAnU2MConnectionTheDriverOpensTheRealBrowser() throws Exception {
        if (!GraphicsEnvironment.isHeadless()) {
            throw new SkipException("The JVM has a display: the real browser would open");
        }
        try {
            open.invoke(launcher, authorizationRequest());
        } catch (InvocationTargetException e) {
            assertThat(e.getCause()).as("the driver's own launcher ran").hasMessageContaining("headless");
            assertThat(browser.requests()).isZero();
            return;
        }
        throw new AssertionError("The driver's launcher should refuse to open a browser in a headless JVM");
    }
}
