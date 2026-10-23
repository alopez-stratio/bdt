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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plays the user's browser for the U2M login of the Crossdata JDBC driver (AUTH_MODE=u2m).
 * <p>The driver opens its authorization URL with {@value #DRIVER_LAUNCHER}.open, which
 * {@code com.stratio.qa.aspects.CrossdataOAuth2BrowserAspect} intercepts while a U2M connection of the BDT is being
 * opened ({@link #start(CrossdataOAuth2Settings.U2MLogin)} ... {@link #stop()}). This browser then does what the user
 * would: log in and accept the consent in the identity server ({@link OidcNativeLoginUtils}) and land on the loopback
 * callback where the driver waits. No real browser, display nor environment is needed, and the credentials never
 * leave the JVM. Out of a U2M connection of the BDT the driver opens the real browser, as always.
 */
public class CrossdataOAuth2Browser {

    /**
     * Object of the driver that opens the browser, whose method open(String) the aspect intercepts.
     */
    public static final String DRIVER_LAUNCHER = "com.stratio.crossdata.driver.oauth.BrowserLauncher";

    private static volatile CrossdataOAuth2Browser active;

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    private final AtomicInteger requests = new AtomicInteger();

    private final AtomicInteger logins = new AtomicInteger();

    private final List<String> errors = new ArrayList<>();

    private volatile CrossdataOAuth2Settings.U2MLogin login;

    /**
     * Checks that the driver in the classpath has the browser launcher the aspect intercepts. Without it the driver
     * has no U2M, or it has changed the way it opens the browser and the aspect has to follow it.
     */
    public static void checkDriverLauncher() {
        try {
            Class.forName(DRIVER_LAUNCHER + "$").getMethod("open", String.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new IllegalStateException("The Crossdata driver has no " + DRIVER_LAUNCHER + ".open(String): it has no "
                    + "OAuth2 U2M, or it opens the browser some other way and CrossdataOAuth2BrowserAspect must follow it", e);
        }
    }

    /**
     * Called by the aspect instead of the driver's launcher.
     *
     * @param url authorization URL the driver wants to open
     * @return whether a U2M connection of the BDT took the URL; when not, the driver opens the real browser
     */
    public static boolean openInActiveBrowser(String url) {
        CrossdataOAuth2Browser browser = active;
        if (browser == null) {
            return false;
        }
        browser.open(URI.create(url));
        return true;
    }

    /**
     * Makes this the browser of the driver until {@link #stop()}.
     *
     * @param u2mLogin user to log in with
     */
    public void start(CrossdataOAuth2Settings.U2MLogin u2mLogin) {
        this.login = u2mLogin;
        active = this;
    }

    /**
     * The driver opens the real browser again.
     */
    public void stop() {
        if (active == this) {
            active = null;
        }
    }

    /**
     * @return how many times the driver has opened the browser
     */
    public int requests() {
        return requests.get();
    }

    /**
     * @return how many logins the fake browser has completed
     */
    public int logins() {
        return logins.get();
    }

    /**
     * @return the errors of the logins that failed, the latest last
     */
    public synchronized List<String> errors() {
        return new ArrayList<>(errors);
    }

    /**
     * What the user does in the browser: log in, accept the consent and land on the callback of the driver. A failed
     * login is recorded, not thrown, as with a real browser: the driver keeps waiting until its browser timeout.
     */
    void open(URI authorizationRequest) {
        requests.incrementAndGet();
        CrossdataOAuth2Settings.U2MLogin user = login;
        logger.info("OAuth2 browser login requested by the driver at {}://{}{}", authorizationRequest.getScheme(),
                authorizationRequest.getHost(), authorizationRequest.getPath());
        try {
            URI callback = OidcNativeLoginUtils.forAuthorizationRequest(authorizationRequest, user.verifyHost())
                    .completeAuthorization(authorizationRequest, user.userName(), user.password(), user.tenant());
            HttpURLConnection connection = (HttpURLConnection) callback.toURL().openConnection();
            try {
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                int status = connection.getResponseCode();
                if (status != 200) {
                    throw new IOException("the driver answered the callback with HTTP " + status);
                }
            } finally {
                connection.disconnect();
            }
            logins.incrementAndGet();
            logger.info("OAuth2 browser login completed for user {}", user.userName());
        } catch (IOException | RuntimeException e) {
            error("OAuth2 browser login failed: " + e.getMessage());
        }
    }

    private synchronized void error(String message) {
        logger.error(message);
        errors.add(message);
    }
}
