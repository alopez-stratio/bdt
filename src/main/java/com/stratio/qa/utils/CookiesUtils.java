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
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.json.JSONObject;

import java.util.*;

public class CookiesUtils {


    private static List<Cookie> cookies = new ArrayList<>();

    private static Map<String, ImmutablePair<Long, List<Cookie>>> cookiesCache = new HashMap<>();

    private static final int CACHE_DEFAULT_EXPIRATION_TIME = 60 * 60; // 60 min

    private static final int CACHE_REQUIRED_EXPIRATION_LEFT = 10 * 60; // 10 min

    private static final CommonG commonSpec = new CommonG();

    public static List<Cookie> getCookies() {
        return cookies;
    }

    /**
     * Sets the cookies of the current session. The list is copied, so that adding or deleting a cookie
     * afterwards never reaches the list it came from, such as an entry of the cookies cache
     */
    public static void setCookies(List<Cookie> newCookies) {
        cookies = newCookies == null ? new ArrayList<>() : new ArrayList<>(newCookies);
    }

    /**
     * Stores a cookie in the cookies cache, using ssoHost, userName, tenant and token type as key and the value of cookiesList
     * It will be given a expiration date parsed from the base64token, or 1 hour by default if null
     * The cache keeps an unmodifiable copy of cookiesList, so later changes to the session do not alter it
     */
    public static void addCookiesToCache(String ssoHost, String userName, String tenant, String token_type, List<Cookie> cookiesList, String base64token) {
        addCookiesToCache(genKey(ssoHost, userName, tenant, token_type), cookiesList, base64token);
    }

    /**
     * Retrieves the previously stored cookie from the cache, using ssoHost, userName, tenant and token type as key
     * The return value will include the expiration date (in epoch millis) and the stored cookie value
     */
    public static ImmutablePair<Long, List<Cookie>> getCookiesFromCache(String ssoHost, String userName, String tenant, String token_type) {
        return getCookiesFromCache(genKey(ssoHost, userName, tenant, token_type));
    }

    /**
     * Returns true if a cookie is found in cache and if its not near expiration, false otherwise
     */
    public static boolean hasCookiesInCache(String ssoHost, String userName, String tenant, String token_type) {
        ImmutablePair<Long, List<Cookie>> cookieCache = cookiesCache.get(genKey(ssoHost, userName, tenant, token_type));
        return cookieCache != null && (cookieCache.getLeft() - System.currentTimeMillis() / 1000) >= CACHE_REQUIRED_EXPIRATION_LEFT;
    }

    private static void addCookiesToCache(String key, List<Cookie> cookiesList, String base64token) {
        if (cookiesList.stream().allMatch(cookie -> cookie.value().isEmpty())) {
            commonSpec.getLogger().warn("The cookie to store is empty and it will not be saved");
        } else {
            long exp;
            try {
                // Get expiration date from BASE64 token
                String[] chunks = base64token.split("\\.");
                Base64.Decoder decoder = Base64.getUrlDecoder();
                JSONObject payload = new JSONObject(new String(decoder.decode(chunks[1])));
                exp = payload.getLong("exp");
            } catch (Exception e) {
                // Set expiration to 1 hour if unknown
                exp = (System.currentTimeMillis() / 1000) + CACHE_DEFAULT_EXPIRATION_TIME;
            }
            commonSpec.getLogger().debug(String.format("NEW cookie stored with key: %s; expiration: %d; value: %s", key, exp, cookiesList));
            cookiesCache.put(key, new ImmutablePair<>(exp, List.copyOf(cookiesList)));
        }
    }

    private static ImmutablePair<Long, List<Cookie>> getCookiesFromCache(String key) {
        commonSpec.getLogger().debug(String.format("READ cookie from cache with key: %s; value: %s", key, cookiesCache.get(key)));
        return cookiesCache.get(key);
    }

    private static String genKey(String ssoHost, String userName, String tenant, String token_type) {
        return String.format("%s_%s_%s_%s",
                ssoHost,
                userName,
                tenant,
                Objects.requireNonNullElse(token_type, "default"));
    }

    /**
     * Retrieves the value of a cookie by its name.
     *
     * @param cookieName the name of the cookie to retrieve
     */
    public static String getCookieValueByName(String cookieName) {
        for (Cookie cookie : cookies) {
            if (cookie.name().equals(cookieName)) {
                return cookie.value();
            }
        }
        return null;
    }

    /**
     * Checks if a cookie with the specified name exists.
     *
     * @param cookieName the name of the cookie to check
     */
    public static boolean hasCookieWithName(String cookieName) {
        return getCookieValueByName(cookieName) != null;
    }
}
