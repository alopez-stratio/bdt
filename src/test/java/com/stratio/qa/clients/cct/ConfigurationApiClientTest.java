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
package com.stratio.qa.clients.cct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

import com.stratio.qa.models.BaseResponse;
import com.stratio.qa.models.BaseResponseList;
import com.stratio.qa.models.cct.configurationApi.CalicoConfiguration;
import com.stratio.qa.specs.CommonG;
import com.stratio.qa.utils.MockHttpsServer;
import com.stratio.qa.utils.ThreadProperty;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.asynchttpclient.AsyncHttpClient;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Drives the CCT configuration API client against an HTTPS {@link MockWebServer}.
 *
 * <p>The client builds its URLs from {@code EOS_ACCESS_POINT} and the port it was given, so
 * pointing it at the mock is a matter of setting both.
 */
public class ConfigurationApiClientTest {

    private MockWebServer server;

    private AsyncHttpClient client;

    private ConfigurationApiClient api;

    @BeforeMethod
    public void setUp() throws Exception {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        server = new MockWebServer();
        MockHttpsServer.enableTls(server);
        server.start();

        CommonG commong = new CommonG();
        client = asyncHttpClient(config()
                .setKeepAlive(false)
                .setUseInsecureTrustManager(true)
                .setConnectTimeout(2000)
                .setRequestTimeout(5000)
                .setReadTimeout(5000)
                .build());
        commong.setClient(client);

        ThreadProperty.set("EOS_ACCESS_POINT", server.getHostName());
        api = ConfigurationApiClient.getInstance(commong);
        api.setPort(String.valueOf(server.getPort()));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        // getInstance hands back the cached client unless its http client is closed, so closing
        // here is what makes the next test get a fresh one pointed at its own server
        // the client is a singleton that outlives this class, so put the default port back
        api.setPort("443");
        if (client != null && !client.isClosed()) {
            client.close();
        }
        server.shutdown();
        ThreadProperty.remove("EOS_ACCESS_POINT");
    }

    // -----------------------------------------------------------------------
    // The plain responses
    // -----------------------------------------------------------------------

    @Test
    public void theCentralConfigurationIsFetchedAndHandedBackRaw() throws Exception {
        server.enqueue(json("{\"globals\":{\"ldap\":{\"userDn\":\"ou=People,dc=stratio,dc=com\"}}}"));

        BaseResponse response = api.getCentralConfiguration();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getRawResponse()).contains("ou=People");
        assertThat(request().getPath()).isEqualTo("/service/cct-configuration-api/central");
    }

    @Test
    public void aPathNarrowsTheCentralConfiguration() throws Exception {
        server.enqueue(json("{\"userDn\":\"ou=People\"}"));

        api.getCentralConfiguration("globals.ldap");

        assertThat(request().getPath())
                .isEqualTo("/service/cct-configuration-api/central?path=globals.ldap");
    }

    @Test
    public void theMesosConfigurationIsFetchedWithAndWithoutAPath() throws Exception {
        server.enqueue(json("{\"mesos\":{}}"));
        assertThat(api.getMesosConfiguration().getHttpStatus()).isEqualTo(200);
        assertThat(request().getPath()).isEqualTo("/service/cct-configuration-api/mesos");

        server.enqueue(json("{\"quota\":1}"));
        api.getMesosConfiguration("quota");
        assertThat(request().getPath()).isEqualTo("/service/cct-configuration-api/mesos?path=quota");
    }

    @Test
    public void anErrorStatusIsCarriedThroughRatherThanThrown() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("service unavailable"));

        BaseResponse response = api.getCentralConfiguration();

        assertThat(response.getHttpStatus()).isEqualTo(503);
        assertThat(response.getRawResponse()).isEqualTo("service unavailable");
    }

    @Test
    public void theSchemaEndpointBuildsAUrlWithOnlyOnePort() throws Exception {
        // It used to concat ":443" after the port it had already appended, so the URL came out as
        // https://host:443:443/service/... and the request could never leave the client.
        server.enqueue(json("{\"type\":\"object\"}"));

        BaseResponse response = api.getSchema();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(request().getPath())
                .isEqualTo("/service/cct-configuration-api/central/schema");
    }

    @Test
    public void oneNetworkIsFetchedByIdOverAWellFormedUrl() throws Exception {
        // Same defect as the schema endpoint above.
        server.enqueue(json("{\"networkName\":\"calico-1\"}"));

        CalicoConfiguration network = api.getNetwork("calico-1");

        assertThat(network.getNetworkName()).isEqualTo("calico-1");
        assertThat(request().getPath()).isEqualTo("/service/cct-configuration-api/network/calico-1");
    }

    // -----------------------------------------------------------------------
    // The mapped ones
    // -----------------------------------------------------------------------

    @Test
    public void aNetworkIsMappedOntoItsModel() throws Exception {
        server.enqueue(json("[{\"networkName\":\"calico-1\",\"ipPool\":\"10.0.0.0/16\","
                + "\"validIpPool\":true,\"runningContainers\":3,\"types\":[\"ingress\"]}]"));

        BaseResponseList<CalicoConfiguration> networks = api.getAllNetworks();

        assertThat(networks.getHttpStatus()).isEqualTo(200);
        assertThat(networks.getList()).hasSize(1);
        assertThat(networks.getList().get(0).getNetworkName()).isEqualTo("calico-1");
        assertThat(networks.getList().get(0).getIpPool()).isEqualTo("10.0.0.0/16");
        assertThat(networks.getList().get(0).isValidIpPool()).isTrue();
        assertThat(networks.getList().get(0).getRunningContainers()).isEqualTo(3);
        assertThat(request().getPath()).isEqualTo("/service/cct-configuration-api/network/all");
    }

    @Test
    public void aBodyThatDoesNotFitTheModelGivesAnEmptyOneRatherThanBlowingUp() throws Exception {
        server.enqueue(json("this is not json at all"));

        BaseResponseList<CalicoConfiguration> networks = api.getAllNetworks();

        assertThat(networks.getList()).as("mapList falls back to an empty list").isEmpty();
        assertThat(networks.getHttpStatus()).isEqualTo(200);
        assertThat(networks.getRawResponse()).isEqualTo("this is not json at all");
    }

    @Test
    public void aNetworkIsCreatedWithTheBodyItWasGiven() throws Exception {
        server.enqueue(json("{\"networkName\":\"calico-new\"}"));

        api.createNetwork("{\"name\":\"calico-new\"}");

        RecordedRequest recorded = request();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/service/cct-configuration-api/network");
        assertThat(recorded.getBody().readUtf8()).isEqualTo("{\"name\":\"calico-new\"}");
        assertThat(recorded.getHeader("Content-Type")).contains("application/json");
    }

    @Test
    public void aNetworkIsUpdatedWithPut() throws Exception {
        server.enqueue(json("{\"networkName\":\"calico-1\"}"));

        api.updateNetwork("{\"name\":\"calico-1\"}");

        RecordedRequest recorded = request();
        assertThat(recorded.getMethod()).isEqualTo("PUT");
        assertThat(recorded.getBody().readUtf8()).isEqualTo("{\"name\":\"calico-1\"}");
    }

    @Test
    public void aNetworkIsRemovedByIdWithDelete() throws Exception {
        server.enqueue(json("{}"));

        api.removeNetwork("calico-1");

        RecordedRequest recorded = request();
        assertThat(recorded.getMethod()).isEqualTo("DELETE");
        assertThat(recorded.getPath()).isEqualTo("/service/cct-configuration-api/network/calico-1");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private RecordedRequest request() throws InterruptedException {
        return server.takeRequest();
    }
}
