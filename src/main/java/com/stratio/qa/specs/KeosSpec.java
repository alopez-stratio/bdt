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

import com.stratio.qa.aspects.RunOnTagAspect;
import com.stratio.qa.utils.CookiesUtils;
import cucumber.api.java.en.When;
import io.netty.handler.codec.http.cookie.Cookie;
import com.stratio.qa.utils.GosecSSOUtils;
import com.stratio.qa.utils.ThreadProperty;
import cucumber.api.java.en.Given;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.json.JSONObject;
import com.stratio.qa.assertions.Assertions;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.testng.Assert;

import java.util.Random;
import java.util.concurrent.Future;

import org.asynchttpclient.Response;


/**
 * Keos Specs.
 *
 * @see <a href="KeosSpec-annotations.html">Keos Steps</a>
 */
public class KeosSpec extends BaseGSpec {

    CommandExecutionSpec commandSpec;

    /**
     * Generic constructor.
     *
     * @param spec object
     */
    public KeosSpec(CommonG spec) {
        this.commonspec = spec;
        commandSpec = new CommandExecutionSpec(spec);
    }

    /**
     * Generate token to authenticate in gosec SSO in Keos
     *
     * @param token_type       : token type: [governance, discovery] or null
     * @param ssoHost          : current sso host
     * @param userName         : username
     * @param password         : password
     * @param tenant           : tenant
     * @param hostVerifier     : whether to verify host or not
     * @param pathWithoutLogin : whether to remove /login or keep it
     * @throws Exception exception
     */
    @Given("^I set sso( governance| discovery| intelligence| cosmos)? keos token using host '(.+?)' with user '(.+?)', password '(.+?)' and tenant '(.+?)'( and LDAP '(.+?)')?( without host name verification)?( without login path)?$")
    public void setGoSecSSOCookieKeos(String token_type, String ssoHost, String userName, String password, String tenant, String ldap, String hostVerifier, String pathWithoutLogin) throws Exception {
        GosecSSOUtils ssoUtils = new GosecSSOUtils(ssoHost, userName, password, tenant, token_type, ldap);
        ssoUtils.setVerifyHost(hostVerifier == null);
        HashMap<String, String> ssoCookies = ssoUtils.ssoTokenGenerator(pathWithoutLogin == null);

        String[] tokenList = {"user", "_oauth2_proxy", "stratio-cookie"};
        switch (String.valueOf(token_type).trim()) {
            case "governance":
                tokenList = new String[]{"user", "_oauth2_proxy", "stratio-cookie", "stratio-governance-auth"};
                break;
            case "discovery":
                Assert.assertTrue(ThreadProperty.has("discovery_sso_cookie_name"),
                        "Discovery SSO Cookie name must be setup first in envVar 'discovery_sso_cookie_name'");
                tokenList = new String[]{ThreadProperty.get("discovery_sso_cookie_name")};
                break;
            case "intelligence":
                tokenList = new String[]{"jupyterhub-session-id", "jupyterhub-hub-login"};
                break;
            case "cosmos":
                tokenList = new String[]{"user", "_oauth2_proxy", "stratio-cookie", "stratio-governance-auth", "dcos-acs-auth-cookie", "dcos-acs-info-cookie"};
                break;
            default:
                break;
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
            CookiesUtils.addCookiesToCache(ssoHost + "_" + userName + "_" + password + "_" + tenant + "_" + (token_type != null ? token_type : "default"), cookiesAtributes, ssoCookies.get("stratio-cookie"));
        }

        if (ssoCookies.get("user") != null) {
            ThreadProperty.set("user", ssoCookies.get("user"));
        }

        if ("discovery".equals(String.valueOf(token_type).trim())) {
            Assert.assertNotNull(ssoCookies.get(ThreadProperty.get("discovery_sso_cookie_name")),
                    "Discovery cookie was not found on SSO response");
            ThreadProperty.set(ThreadProperty.get("discovery_sso_cookie_name"),
                    ssoCookies.get(ThreadProperty.get("discovery_sso_cookie_name")));
        }

        if ("intelligence".equals(String.valueOf(token_type).trim())) {
            for (String cookie : tokenList) {
                Assert.assertNotNull(ssoCookies.get(cookie), "SSO cookie " + cookie + " is not defined after login");
                ThreadProperty.set(cookie, ssoCookies.get(cookie));
            }
        }

        if (ssoCookies.get("dcos-acs-auth-cookie") != null) {
            ThreadProperty.set("cosmosAuthCookie", ssoCookies.get("dcos-acs-auth-cookie"));
        }

        if (ssoCookies.get("dcos-acs-info-cookie") != null) {
            ThreadProperty.set("cosmosInfoAuthCookie", ssoCookies.get("dcos-acs-info-cookie"));
        }

        this.commonspec.getLogger().debug("Cookies to set:");
        for (Cookie cookie : cookiesAtributes) {
            this.commonspec.getLogger().debug("\t" + cookie.name() + ":" + cookie.value());
        }
    }

    @Given("^I get from cache, if exists, sso( governance| discovery| intelligence)? keos token using host '(.+?)' with user '(.+?)', password '(.+?)' and tenant '(.+?)'( and LDAP '(.+?)')?( without host name verification)?( without login path)?$")
    public void setGoSecSSOCookieKeosCache(String token_type, String ssoHost, String userName, String password, String tenant, String ldap, String hostVerifier, String pathWithoutLogin) throws Exception {
        long currentTime = System.currentTimeMillis() / 1000;
        ImmutablePair<Long, List<Cookie>> cookieCache = CookiesUtils.getCookiesFromCache(ssoHost + "_" + userName + "_" + password + "_" + tenant + "_" + (token_type != null ? token_type : "default"));
        if (cookieCache == null || (cookieCache.getLeft() - currentTime) < 60) {
            this.commonspec.getLogger().debug("Cookie expiration is imminent, I get cookie again");
            setGoSecSSOCookieKeos(token_type, ssoHost, userName, password, tenant, ldap, hostVerifier, pathWithoutLogin);
        } else {
            this.commonspec.getLogger().debug("Set cookies from cache");
            commonspec.setCookies(cookieCache.getRight());
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
        String command = "wget -P target/test-classes http://qa.int.stratio.com/repository/paas/kubernetes-universe/descriptors/kubernetes-universe-descriptors-" + version + ".zip";

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
        String endPointUpload = ThreadProperty.get("KEOS_CCT_UNIVERSE_SERVICE_INGRESS_PATH") + "/v1/descriptors";

        // Obtain URL
        String restURL = "https://" + ThreadProperty.get("KEOS_OAUTH2_PROXY_HOST") + ":443" + endPointUpload;

        // Form query parameters
        String headers = "-H \"accept: */*\" -H \"Content-Type: multipart/form-data\"";
        String forms = "-F \"file=@" + universeFile + ";type=application/zip\"";
        String cookie;

        ThreadProperty.set("keosVersion", System.getProperty("KEOS_VERSION"));
        if (System.getProperty("KEOS_VERSION").contains("-")) {
            ThreadProperty.set("keosVersion", System.getProperty("KEOS_VERSION").substring(0, System.getProperty("KEOS_VERSION").indexOf("-")));
        }
        RunOnTagAspect runOnTagAspect = new RunOnTagAspect();
        if (runOnTagAspect.checkParams(runOnTagAspect.getParams("@runOnEnv(keosVersion<1.1.0)"))) {
            cookie = "-H \"Cookie:_oauth2_proxy=" + ThreadProperty.get("oauth2ProxyCookie") + "\"";
        } else {
            cookie = "-H \"Cookie:stratio-cookie=" + ThreadProperty.get("stratioCookie") + "\"";
        }
        String command = "curl -X PUT -i -k " + cookie + " \"" + restURL + "\" " + headers + " " + forms + " > target/test-classes/outputK8sUniverseUpload.txt";

        // Execute command
        commonspec.runLocalCommand(command, 60);
        Assertions.assertThat(commonspec.getCommandExitStatus()).isEqualTo(0);

        commonspec.runLocalCommand("cat target/test-classes/outputK8sUniverseUpload.txt | grep \"HTTP/\"");
        Assertions.assertThat(commonspec.getCommandResult()).as("Upload universe response").contains("200");
    }

    /**
     * Obtain metabase id for the current user and set ups metabase token headers
     *
     * @param host : discovery host
     * @param path : discovery session endpoint
     * @throws Exception
     */
    @Given("^I obtain metabase id for current user in host '(.+?)' with endpoint '(.+?)'$")
    public void saveMetabaseCurrentUserCookieKeos(String host, String path) throws Exception {
        RestSpec restSpec = new RestSpec(commonspec);
        restSpec.setupRestClient("securely", host, ":443");
        Future<Response> response = commonspec.generateRequest("GET", false, null, null, path, "", null, "");

        String metabase_session = "";
        for (String header_value : response.get().getHeaders("Set-Cookie")) {
            if (header_value.startsWith("metabase.SESSION")) {
                metabase_session = header_value.split(";")[0].split("=")[1];
            }
        }
        Assert.assertNotEquals(metabase_session, "", "Error obtaining Metabase Session");

        Cookie cookie = new DefaultCookie("metabase.SESSION", metabase_session);
        cookie.setDomain("");
        cookie.setPath("");
        cookie.setWrap(false);
        cookie.setMaxAge(99999L);
        cookie.setSecure(false);
        cookie.setHttpOnly(false);
        List cookieList = new ArrayList();
        cookieList.add(cookie);
        cookieList.add(this.commonspec.getCookies().get(0));
        commonspec.setCookies(cookieList);

        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("X-Metabase-Session", metabase_session);
        commonspec.setHeaders(headers);
    }

    /**
     * Execute different playbooks
     *
     * @param selector : module's playbook to execute
     * @param file     : file to save command output in
     * @throws Exception
     */
    @When("^I run '(governance|intelligence|rocket)' playbook in keos-installer( and save output in file '(.+?)')?$")
    public void executePlaybook(String selector, String file) throws Exception {
        String command = null;
        switch (selector) {
            case "governance":
                command = " 'keos play /stratio/ansible/playbooks/system-services/governance.yml " + sedRemoveColors() + "'";
                break;
            case "intelligence":
                command = " 'keos play /stratio/ansible/playbooks/system-services/intelligence.yml -e intelligence_namespace=" + ThreadProperty.get("INTELLIGENCE_NAMESPACE") + " -e intelligence_service=" + ThreadProperty.get("INTELLIGENCE_INSTANCE") + " " + sedRemoveColors() + "'";
                break;
            case "rocket":
                command = " 'keos play /stratio/ansible/playbooks/system-services/rocket.yml -e rocket_namespace=" + ThreadProperty.get("ROCKET_NAMESPACE") + " -e rocket_service=" + ThreadProperty.get("ROCKET_INSTANCE") + " " + sedRemoveColors() + "'";
                break;
            default:
                break;
        }
        executeCommandInKeosInstaller(command, null, file);

    }

    /**
     * Execute different keos commands
     *
     * @param command : keos command to execute
     * @param envVar  : variable to save command output in
     * @param file    : file to save command output in
     * @throws Exception
     */
    @When("^I run keos '(.+?)' command in keos-installer( and save output in variable '(.+?)')?( and save output in file '(.+?)')?$")
    public void executeKeosCommand(String command, String envVar, String file) throws Exception {
        String finalCommand = " 'keos " + command + " " + sedRemoveColors() + "'";
        executeCommandInKeosInstaller(finalCommand, envVar, file);
    }

    /**
     * Execute different ansible commands
     *
     * @param command : ansible command to execute
     * @param nodes   : nodes where ansible command will be executed
     * @param file    : file to save command output in
     * @throws Exception
     */
    @When("^I run '(.+?)' ansible in nodes '(.+?)' in keos-installer( and save output in file '(.+?)')?$")
    public void executeAnsible(String command, String nodes, String file) throws Exception {
        String finalCommand = "i 'ansible -m shell -a \"" + command + "\" -b " + nodes + " " + sedRemoveColors() + "'";
        executeCommandInKeosInstaller(finalCommand, null, file);
    }

    /**
     * Add sed to command in order to clean colors in the commands output
     */
    private String sedRemoveColors() {
        return "| sed -r \"s/\\x1B\\[([0-9]{1,2}(;[0-9]{1,2})?)?[m|K]//g\"";
    }

    /**
     * run keos commands in local docker or bastion docker, depending on whether we are running locally or in jenkins
     *
     * @param command : command to execute
     * @param envVar  : var to save command output in
     * @param file    : file to save command output in
     * @throws Exception
     */
    private void executeCommandInKeosInstaller(String command, String envVar, String file) throws Exception {
        if (System.getProperty("KEOS_INSTALLER_DOCKER") != null) {
            commonspec.getLogger().debug("Jenkins execution detected, opening ssh connection to BASTION");
            commandSpec.openSSHConnection(System.getProperty("BASTION_IP"), null, System.getProperty("BASTION_USER"), System.getProperty("BASTION_PASS"), null, "tempConnection");
            String finalCommand = "docker exec -t " + System.getProperty("KEOS_INSTALLER_DOCKER") + " bash -c" + command;
            commandSpec.executeCommand(finalCommand, null, "0", envVar, null);
            commonspec.getLogger().debug("Closing ssh connection to BASTION");
            commandSpec.closeSSHConnection("tempConnection");
        } else {
            if (ThreadProperty.get("localDocker") == null) {
                commandSpec.executeLocalCommand("ls target/test-classes | grep keos-", "0", "clusterWorkspace");
                commandSpec.executeLocalCommand("cp -R target/test-classes/" + ThreadProperty.get("clusterWorkspace") + " target/test-classes/workspaceKeosInstaller", null, null, null);
                String randomName = ThreadProperty.get("clusterWorkspace") + "-" + new Random().nextInt(900000) + 100000;
                ThreadProperty.set("localDocker", randomName);
                commandSpec.executeLocalCommand("pwd", null, "pwd", null);
                commonspec.getLogger().debug("Local execution detected, creating docker container");
                commandSpec.executeLocalCommand("docker run -dt --name " + ThreadProperty.get("localDocker") + " --net host -e VAULT_MASTER_KEY=" + System.getProperty("KEOS_SECRETS_PASS") + " -v /var/run/docker.sock:/var/run/docker.sock -v " + ThreadProperty.get("pwd") + "/target/test-classes/workspaceKeosInstaller:/workspace:z qa.int.stratio.com/stratio/keos-installer:" + System.getProperty("KEOS_VERSION") + " > /dev/null 2>&1", "0", null, null);
                commandSpec.assertCommandExistsOnTimeOutLocal(100, 10, "docker logs " + ThreadProperty.get("localDocker"), "Starting interactive session", null);
            }
            String finalCommand = "docker exec -t " + ThreadProperty.get("localDocker") + " bash -c" + command;
            commandSpec.executeLocalCommand(finalCommand, "0", envVar, null);
        }
        if (file != null) {
            this.writeInFile(commonspec.getCommandResult(), file);
        }
    }
}
