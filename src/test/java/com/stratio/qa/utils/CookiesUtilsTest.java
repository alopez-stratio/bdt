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

import com.stratio.qa.specs.CommonG;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

public class CookiesUtilsTest {

    private CommonG commonG;

    @BeforeMethod
    public void setUp() {
        // CommonG, which CookiesUtils also holds, names its logger after this property
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        commonG = new CommonG();
    }

    @AfterMethod
    public void cleanup() {
        CookiesUtils.setCookies(new ArrayList<>());
    }

    /**
     * Stores two SSO cookies the way KeosSpec does after a login: the same list becomes the session
     * and the cache entry. The user is random because the cache is static and has no way to be cleared
     */
    private String login() {
        String user = "user-" + UUID.randomUUID();
        List<Cookie> cookies = new ArrayList<>();
        cookies.add(new DefaultCookie("stratio-cookie", "sso-token"));
        cookies.add(new DefaultCookie("user", user));
        CookiesUtils.setCookies(cookies);
        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", "default", cookies, null);
        return user;
    }

    private static List<String> cachedNames(String user) {
        return CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", "default").getRight().stream()
                .map(Cookie::name).collect(Collectors.toList());
    }

    private static List<String> sessionNames() {
        return CookiesUtils.getCookies().stream().map(Cookie::name).collect(Collectors.toList());
    }

    @Test
    public void deletingACookieAfterLoginKeepsItInTheCache() {
        String user = login();

        commonG.deleteCookie("stratio-cookie");

        assertThat(sessionNames()).as("Session cookies after the delete").containsExactly("user");
        assertThat(cachedNames(user)).as("Cached cookies after the delete").containsExactly("stratio-cookie", "user");
    }

    @Test
    public void deletingACookieAfterReusingTheCacheKeepsItInTheCache() {
        String user = login();
        CookiesUtils.setCookies(CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", "default").getRight());

        commonG.deleteCookie("stratio-cookie");
        CookiesUtils.setCookies(CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", "default").getRight());

        assertThat(sessionNames()).as("Session cookies restored from the cache").containsExactly("stratio-cookie", "user");
    }

    @Test
    public void addingACookieAfterReusingTheCacheLeavesTheCacheUnchanged() {
        String user = login();
        CookiesUtils.setCookies(CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", "default").getRight());

        commonG.addCookie("extra", "value");

        assertThat(sessionNames()).as("Session cookies after the add").containsExactly("stratio-cookie", "user", "extra");
        assertThat(cachedNames(user)).as("Cached cookies after the add").containsExactly("stratio-cookie", "user");
    }

    @Test
    public void changingTheListPassedToSetCookiesLeavesTheSessionUnchanged() {
        List<Cookie> cookies = new ArrayList<>();
        cookies.add(new DefaultCookie("stratio-cookie", "sso-token"));
        CookiesUtils.setCookies(cookies);

        cookies.clear();

        assertThat(sessionNames()).as("Session cookies after clearing the original list").containsExactly("stratio-cookie");
    }

    @Test
    public void theCachedCookiesCannotBeModified() {
        String user = login();
        List<Cookie> cached = CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", "default").getRight();

        assertThatThrownBy(() -> cached.add(new DefaultCookie("extra", "value")))
                .as("Adding to the cached cookies")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void setCookiesWithNullLeavesAnEmptySession() {
        CookiesUtils.setCookies(null);

        assertThat(CookiesUtils.getCookies()).as("Session cookies after setting null").isEmpty();
    }
    // -----------------------------------------------------------------------
    // Cache keys
    // -----------------------------------------------------------------------

    private static String randomUser() {
        return "user-" + UUID.randomUUID();
    }

    private static List<Cookie> ssoCookies(String stratioCookie) {
        List<Cookie> cookies = new ArrayList<>();
        cookies.add(new DefaultCookie("stratio-cookie", stratioCookie));
        cookies.add(new DefaultCookie("user", "admin"));
        return cookies;
    }

    /**
     * A JWT whose payload only carries the exp claim, which is all the cache reads from it
     */
    static String jwtExpiringAt(long epochSeconds) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(("{\"exp\":" + epochSeconds + "}").getBytes(StandardCharsets.UTF_8)) + ".sig";
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    @Test
    public void anUnknownKeyIsNotInTheCache() {
        String user = randomUser();

        assertThat(CookiesUtils.hasCookiesInCache("sso.host", user, "tenant", null)).as("Cache hit for an unknown user").isFalse();
        assertThat(CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", null)).as("Cache entry for an unknown user").isNull();
    }

    @Test
    public void aNullTokenTypeSharesTheEntryOfTheDefaultOne() {
        String user = randomUser();
        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", null, ssoCookies("token"), null);

        assertThat(CookiesUtils.hasCookiesInCache("sso.host", user, "tenant", "default")).as("Cache hit for the default token type").isTrue();
    }

    @DataProvider
    public Object[][] otherKeys() {
        return new Object[][]{
            {"other.host", "", "tenant", null},
            {"sso.host", "-other", "tenant", null},
            {"sso.host", "", "other-tenant", null},
            {"sso.host", "", "tenant", " governance"},
        };
    }

    @Test(dataProvider = "otherKeys")
    public void entriesAreKeptApartByHostUserTenantAndTokenType(String host, String userSuffix, String tenant, String tokenType) {
        String user = randomUser();
        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", null, ssoCookies("token"), null);

        String lookedUpUser = user + userSuffix;
        assertThat(CookiesUtils.hasCookiesInCache(host, lookedUpUser, tenant, tokenType))
                .as("Cache hit for %s / %s / %s / %s", host, lookedUpUser, tenant, tokenType).isFalse();
    }

    @Test
    public void storingAgainReplacesTheEntry() {
        String user = randomUser();
        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", null, ssoCookies("first"), null);
        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", null, ssoCookies("second"), null);

        assertThat(CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", null).getRight())
                .extracting(Cookie::value).as("Cached cookie values").containsExactly("second", "admin");
    }

    // -----------------------------------------------------------------------
    // Expiration
    // -----------------------------------------------------------------------

    @Test
    public void theExpirationIsReadFromTheToken() {
        String user = randomUser();
        long exp = now() + 3600;
        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", null, ssoCookies("token"), jwtExpiringAt(exp));

        assertThat(CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", null).getLeft()).as("Cached expiration").isEqualTo(exp);
        assertThat(CookiesUtils.hasCookiesInCache("sso.host", user, "tenant", null)).as("Cache hit").isTrue();
    }

    @DataProvider
    public Object[][] tokensWithoutExpiration() {
        return new Object[][]{
            {null},
            {"not-a-jwt"},
            {"header.!!!.signature"},
            {Base64.getUrlEncoder().withoutPadding().encodeToString("{}".getBytes(StandardCharsets.UTF_8))
                    + "." + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"admin\"}".getBytes(StandardCharsets.UTF_8))
                    + ".sig"},
        };
    }

    @Test(dataProvider = "tokensWithoutExpiration")
    public void aTokenWithoutAReadableExpirationIsKeptForOneHour(String token) {
        String user = randomUser();
        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", null, ssoCookies("token"), token);

        assertThat(CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", null).getLeft())
                .as("Cached expiration for token %s", token).isCloseTo(now() + 3600, within(5L));
        assertThat(CookiesUtils.hasCookiesInCache("sso.host", user, "tenant", null)).as("Cache hit").isTrue();
    }

    @DataProvider
    public Object[][] expirationsTooClose() {
        return new Object[][]{
            {"expiring in 9 minutes", 9 * 60},
            {"expiring now", 0},
            {"already expired", -3600},
        };
    }

    @Test(dataProvider = "expirationsTooClose")
    public void aTokenWithLessThanTenMinutesLeftIsNotReused(String description, int secondsLeft) {
        String user = randomUser();
        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", null, ssoCookies("token"), jwtExpiringAt(now() + secondsLeft));

        assertThat(CookiesUtils.hasCookiesInCache("sso.host", user, "tenant", null)).as("Cache hit for a token %s", description).isFalse();
        assertThat(CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", null))
                .as("The entry is still stored, only no longer reused").isNotNull();
    }

    @Test
    public void aTokenWithJustOverTenMinutesLeftIsReused() {
        String user = randomUser();
        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", null, ssoCookies("token"), jwtExpiringAt(now() + 11 * 60));

        assertThat(CookiesUtils.hasCookiesInCache("sso.host", user, "tenant", null)).as("Cache hit").isTrue();
    }

    // -----------------------------------------------------------------------
    // Empty logins
    // -----------------------------------------------------------------------

    @Test
    public void cookiesThatAreAllEmptyAreNotStored() {
        String user = randomUser();
        List<Cookie> empty = List.of(new DefaultCookie("stratio-cookie", ""), new DefaultCookie("user", ""));

        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", null, empty, null);

        assertThat(CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", null)).as("Cache entry after an empty login").isNull();
    }

    @Test
    public void anEmptyLoginKeepsThePreviousEntry() {
        String user = randomUser();
        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", null, ssoCookies("token"), null);

        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", null, List.of(new DefaultCookie("stratio-cookie", "")), null);

        assertThat(CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", null).getRight())
                .extracting(Cookie::value).as("Cached cookie values").containsExactly("token", "admin");
    }

    @Test
    public void cookiesWithOnlySomeEmptyValuesAreStored() {
        String user = randomUser();
        List<Cookie> partial = List.of(new DefaultCookie("stratio-cookie", "token"), new DefaultCookie("_oauth2_proxy", ""));

        CookiesUtils.addCookiesToCache("sso.host", user, "tenant", null, partial, null);

        assertThat(CookiesUtils.getCookiesFromCache("sso.host", user, "tenant", null).getRight())
                .extracting(Cookie::name).as("Cached cookie names").containsExactly("stratio-cookie", "_oauth2_proxy");
    }

    // -----------------------------------------------------------------------
    // Lookup by name
    // -----------------------------------------------------------------------

    @Test
    public void aCookieIsLookedUpByNameInTheSession() {
        CookiesUtils.setCookies(ssoCookies("token"));

        assertThat(CookiesUtils.getCookieValueByName("stratio-cookie")).as("Value of stratio-cookie").isEqualTo("token");
        assertThat(CookiesUtils.hasCookieWithName("stratio-cookie")).as("Has stratio-cookie").isTrue();
        assertThat(CookiesUtils.getCookieValueByName("missing")).as("Value of a missing cookie").isNull();
        assertThat(CookiesUtils.hasCookieWithName("missing")).as("Has a missing cookie").isFalse();
    }

    @Test
    public void theFirstCookieWinsWhenANameIsRepeated() {
        commonG.addCookie("session", "first");
        commonG.addCookie("session", "second");

        assertThat(CookiesUtils.getCookieValueByName("session")).as("Value of the repeated cookie").isEqualTo("first");
    }

    @Test
    public void anEmptyCookieStillCountsAsPresent() {
        CookiesUtils.setCookies(List.of(new DefaultCookie("stratio-cookie", "")));

        assertThat(CookiesUtils.hasCookieWithName("stratio-cookie")).as("Has an empty stratio-cookie").isTrue();
    }

    @Test
    public void clearCookiesEmptiesTheSessionButNotTheCache() {
        String user = login();

        commonG.clearCookies();

        assertThat(CookiesUtils.getCookies()).as("Session cookies after clearing").isEmpty();
        assertThat(cachedNames(user)).as("Cached cookies after clearing").containsExactly("stratio-cookie", "user");
    }
}
