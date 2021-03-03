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

import com.ning.http.client.cookie.Cookie;
import com.stratio.qa.utils.GosecSSOUtils;
import com.stratio.qa.utils.ThreadProperty;
import cucumber.api.java.en.Given;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

/**
 * Keos Specs.
 *
 * @see <a href="KeosSpec-annotations.html">Keos Steps</a>
 */
public class KeosSpec extends BaseGSpec {
    /**
     * Generic constructor.
     *
     * @param spec object
     */
    public KeosSpec(CommonG spec) {
        this.commonspec = spec;
    }

    /**
     * Generate token to authenticate in gosec SSO in Keos
     *
     * @param ssoHost  : current sso host
     * @param userName : username
     * @param password : password
     * @param tenant   : tenant
     * @throws Exception exception
     */
    @Given("^I set sso keos token using host '(.+?)' with user '(.+?)', password '(.+?)' and tenant '(.+?)'$")
    public void setGoSecSSOCookieKeos(String ssoHost, String userName, String password, String tenant) throws Exception {
        GosecSSOUtils ssoUtils = new GosecSSOUtils(ssoHost + "/ui/", userName, password, tenant, null);
        ssoUtils.setVerifyHost(false);
        HashMap<String, String> ssoCookies = ssoUtils.ssoTokenGenerator(false);
        String[] tokenList = {"_oauth2_proxy"};
        List<Cookie> cookiesAtributes = commonspec.addSsoToken(ssoCookies, tokenList);
        commonspec.setCookies(cookiesAtributes);
        RestSpec restSpec = new RestSpec(commonspec);
        restSpec.setupRestClient("securely", ssoHost, ":443");
        restSpec.sendRequestNoDataTable("GET", "/ui/", null, null, null);
        for (com.ning.http.client.cookie.Cookie cookie : commonspec.getResponse().getCookies()) {
            if (cookie.getName().equals("stratio-cookie")) {
                cookiesAtributes.add(cookie);
                break;
            }
        }
        this.commonspec.getLogger().debug("Cookies to set:");
        for (String cookie : tokenList) {
            this.commonspec.getLogger().debug("\t" + cookie + ":" + ssoCookies.get(cookie));
        }
        commonspec.setCookies(cookiesAtributes);
    }

    /**
     * Convert descriptor to k8s-json-schema
     *
     * @param descriptor : descriptor to be converted to k8s-json-schema
     * @param envVar     : environment variable where to store json
     * @throws Exception exception     *
     */
    @Given("^I convert descriptor '(.+?)' to k8s-json-schema( and save it in variable '(.+?)')?( and save it in file '(.+?)')?")
    public void convertDescriptorToK8sJsonSchema(String descriptor, String envVar, String fileName) throws Exception {
        JSONObject jsonSchema = new JSONObject();
        jsonSchema.put("descriptor", new JSONObject(descriptor));
        jsonSchema.put("deployment", commonspec.parseJSONSchema(new JSONObject(descriptor)));
        if (envVar != null) {
            ThreadProperty.set(envVar, jsonSchema.toString());
        }
        if (fileName != null) {
            File tempDirectory = new File(System.getProperty("user.dir") + "/target/test-classes/");
            String absolutePathFile = tempDirectory.getAbsolutePath() + "/" + fileName;
            commonspec.getLogger().debug("Creating file {} in 'target/test-classes'", absolutePathFile);
            // Note that this Writer will delete the file if it exists
            Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(absolutePathFile), StandardCharsets.UTF_8));
            try {
                out.write(jsonSchema.toString());
            } catch (Exception e) {
                commonspec.getLogger().error("Custom file {} hasn't been created:\n{}", absolutePathFile, e.toString());
            } finally {
                out.close();
            }
        }
    }
}
