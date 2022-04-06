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
import com.stratio.qa.assertions.Assertions;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import org.testng.Assert;

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
    @Given("^I set sso( governance)? keos token using host '(.+?)' with user '(.+?)', password '(.+?)' and tenant '(.+?)'( without host name verification)?( without login path)?$")
    public void setGoSecSSOCookieKeos(String gov, String ssoHost, String userName, String password, String tenant, String hostVerifier, String pathWithoutLogin) throws Exception {
        GosecSSOUtils ssoUtils = new GosecSSOUtils(ssoHost, userName, password, tenant, gov);
        ssoUtils.setVerifyHost(hostVerifier == null);
        HashMap<String, String> ssoCookies = ssoUtils.ssoTokenGenerator(pathWithoutLogin == null);

        String[] tokenList = {"user", "_oauth2_proxy", "stratio-cookie"};
        if (gov != null) {
            tokenList = new String[]{"user", "_oauth2_proxy", "stratio-cookie", "stratio-governance-auth"};
        }

        List<Cookie> cookiesAtributes = commonspec.addSsoToken(ssoCookies, tokenList);
        commonspec.setCookies(cookiesAtributes);

        if (ssoCookies.get("stratio-governance-auth") != null) {
            ThreadProperty.set("keosGovernanceAuthCookie", ssoCookies.get("stratio-governance-auth"));
        }

        if (ssoCookies.get("_oauth2_proxy") != null) {
            ThreadProperty.set("oauth2ProxyCookie", ssoCookies.get("_oauth2_proxy"));
        }

        if (ssoCookies.get("stratio-cookie") != null) {
            ThreadProperty.set("stratioCookie", ssoCookies.get("stratio-cookie"));
        }

        if (ssoCookies.get("user") != null) {
            ThreadProperty.set("user", ssoCookies.get("user"));
        }

        this.commonspec.getLogger().debug("Cookies to set:");
        for (Cookie cookie : cookiesAtributes) {
            this.commonspec.getLogger().debug("\t" + cookie.getName() + ":" + cookie.getValue());
        }
    }

    /**
     * Convert descriptor to k8s-json-schema
     *
     * @param descriptor : descriptor to be converted to k8s-json-schema
     * @param envVar     : environment variable where to store json
     * @throws Exception exception     *
     */
    @Given("^I convert descriptor '(.+?)' to k8s-json-schema( and save it in variable '(.+?)')?( and save it in file '(.+?)')?")
    public void convertDescriptorToK8sJsonSchema(String descriptor, String descriptorAttrs, String envVar, String fileName) throws Exception {
        JSONObject jsonSchema = new JSONObject();
        jsonSchema.put("descriptor", new JSONObject(descriptorAttrs));
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


    @Given("^I download k8s universe version '(.+?)'")
    public void downloadK8sUniverse(String version) throws Exception {
        String command = "wget -P target/test-classes http://sodio.stratio.com/repository/paas/kubernetes-universe/descriptors/kubernetes-universe-descriptors-" + version + ".zip";

        // Execute command
        commonspec.runLocalCommand(command);

        Assertions.assertThat(commonspec.getCommandExitStatus()).as("Error downloading kubernetes universe version: " + version).isEqualTo(0);
    }


    @Given("^I upload k8s universe '(.+?)'")
    public void uploadK8sUniverse(String universeFile) throws Exception {
        // Check file exists
        File rules = new File(universeFile);
        Assertions.assertThat(rules.exists()).as("File: " + universeFile + " does not exist.").isTrue();

        // Obtain endpoint
        String endPointUpload = "/cct/cct-universe-service/v1/descriptors";

        // Obtain URL
        String restURL = "https://" + System.getProperty("KEOS_CLUSTER_NAME") + ":443" + endPointUpload;

        // Form query parameters
        String headers = "-H \"accept: */*\" -H \"Content-Type: multipart/form-data\"";
        String forms = "-F \"file=@" + universeFile + ";type=application/zip\"";

        String cookie = "-H \"Cookie:_oauth2_proxy=" + ThreadProperty.get("oauth2ProxyCookie") + "\"";
        String command = "curl -X PUT -k " + cookie + " \"" + restURL + "\" " + headers + " " + forms;

        // Execute command
        commonspec.runLocalCommand(command);

        Assertions.assertThat(commonspec.getCommandExitStatus()).isEqualTo(0);
        Assertions.assertThat(commonspec.getCommandResult()).as("Not possible to upload universe: " + commonspec.getCommandResult()).doesNotContain("Error");

    }
}
