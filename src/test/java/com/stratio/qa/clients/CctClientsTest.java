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
package com.stratio.qa.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

import com.stratio.qa.clients.cct.CctMarathonServiceApiClient;
import com.stratio.qa.clients.marathon.MarathonApiClient;
import com.stratio.qa.clients.mesos.MesosApiClient;
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
 * The cct-marathon-services, Marathon and Mesos clients are thin: they assemble a URL from ThreadProperty values,
 * make one call and hand the body to Jackson. What is worth pinning down is therefore the URL, the
 * verb and the body sent -- and that an error status comes back as data instead of an exception.
 *
 * <p>All of them hardcode {@code https://}, so the server behind this is a TLS
 * {@link MockWebServer}; see {@link MockHttpsServer}.
 *
 * <p>{@code DeployApiClient} is deliberately left out: it already has {@code DeployApiTest}, which
 * sets the singleton's port once in {@code @BeforeTest}, and a second class moving that port
 * underneath it makes the other suite fail.
 */
public class CctClientsTest {

    private MockWebServer server;

    private AsyncHttpClient client;

    private CommonG commong;

    @BeforeMethod
    public void setUp() throws Exception {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        server = new MockWebServer();
        MockHttpsServer.enableTls(server);
        server.start();

        commong = new CommonG();
        client = asyncHttpClient(config()
                .setKeepAlive(false)
                .setUseInsecureTrustManager(true)
                .setConnectTimeout(2000)
                .setRequestTimeout(5000)
                .setReadTimeout(5000)
                .build());
        commong.setClient(client);

        ThreadProperty.set("EOS_ACCESS_POINT", server.getHostName());
        ThreadProperty.set("cct-marathon-services_id", "cct-marathon-services");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        // every getInstance caches its client until the http client is closed
        // these clients are singletons that outlive the class, so put the default port back
        CctMarathonServiceApiClient.getInstance(commong).setPort("443");
        MarathonApiClient.getInstance(commong).setPort("443");
        MesosApiClient.getInstance(commong).setPort("443");
        if (client != null && !client.isClosed()) {
            client.close();
        }
        server.shutdown();
        ThreadProperty.remove("EOS_ACCESS_POINT");
        ThreadProperty.remove("cct-marathon-services_id");
    }

    // -----------------------------------------------------------------------
    // cct-marathon-services
    // -----------------------------------------------------------------------

    @Test
    public void aServiceIsFetchedUnderItsOwnPathSegment() throws Exception {
        server.enqueue(json("{}"));

        cctMarathon().getService("my-service");

        assertThat(request().getPath())
                .as("the single-argument form used to concatenate straight onto /v1/services")
                .isEqualTo("/service/cct-marathon-services/v1/services/my-service");
    }

    @Test
    public void paginationIsSentAsQueryParameters() throws Exception {
        server.enqueue(json("{}"));

        cctMarathon().getService("my-service", 1, 50);

        String path = request().getPath();
        assertThat(path).startsWith("/service/cct-marathon-services/v1/services/my-service?");
        assertThat(path).contains("tpage=1").contains("tsize=50");
    }

    @Test
    public void logPathsAndTheTenantListing() throws Exception {
        CctMarathonServiceApiClient api = cctMarathon();

        server.enqueue(json("{}"));
        api.getLogPaths("task-1");
        assertThat(request().getPath())
                .isEqualTo("/service/cct-marathon-services/v1/services/tasks/task-1/logs");

        server.enqueue(json("{}"));
        api.getDeployedServices("nrt");
        assertThat(request().getPath())
                .isEqualTo("/service/cct-marathon-services/v1/services?tenant=nrt");
    }

    // -----------------------------------------------------------------------
    // Marathon
    // -----------------------------------------------------------------------

    @Test
    public void marathonReadsAppsOneByOneAndAsAList() throws Exception {
        MarathonApiClient api = marathon();

        server.enqueue(json("{\"apps\":[]}"));
        assertThat(api.getApps().getHttpStatus()).isEqualTo(200);
        assertThat(request().getPath()).isEqualTo("/marathon/v2/apps");

        server.enqueue(json("{\"app\":{}}"));
        api.getApp("/my-app");
        assertThat(request().getPath()).isEqualTo("/marathon/v2/apps//my-app");
    }

    @Test
    public void addingAnAppPostsTheDescriptorItWasGiven() throws Exception {
        server.enqueue(json("{}"));

        marathon().addApp("{\"id\":\"/my-app\"}");

        RecordedRequest recorded = request();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/marathon/v2/apps");
        assertThat(recorded.getBody().readUtf8()).isEqualTo("{\"id\":\"/my-app\"}");
    }

    @Test
    public void updatingRemovingAndRestartingCarryTheForceFlag() throws Exception {
        MarathonApiClient api = marathon();

        server.enqueue(json("{}"));
        api.updateAppFromString("my-app", "{\"instances\":2}", true);
        RecordedRequest update = request();
        assertThat(update.getMethod()).isEqualTo("PUT");
        assertThat(update.getPath()).isEqualTo("/marathon/v2/apps/my-app?force=true");
        assertThat(update.getBody().readUtf8()).isEqualTo("{\"instances\":2}");

        server.enqueue(json("{}"));
        api.removeApp("my-app", false);
        RecordedRequest remove = request();
        assertThat(remove.getMethod()).isEqualTo("DELETE");
        assertThat(remove.getPath()).isEqualTo("/marathon/v2/apps/my-app?force=false");

        server.enqueue(json("{}"));
        api.restartApp("my-app", true);
        RecordedRequest restart = request();
        assertThat(restart.getMethod()).isEqualTo("POST");
        assertThat(restart.getPath()).isEqualTo("/marathon/v2/apps/my-app/restart");
    }

    // -----------------------------------------------------------------------
    // Mesos
    // -----------------------------------------------------------------------

    @Test
    public void mesosReadsTheStateSummaryAndItsTasks() throws Exception {
        MesosApiClient api = mesos();

        server.enqueue(json("{\"frameworks\":[]}"));
        assertThat(api.getStateSummary().getHttpStatus()).isEqualTo(200);
        assertThat(request().getPath()).isEqualTo("/mesos/state-summary");

        server.enqueue(json("{\"tasks\":[]}"));
        api.getMesosTasks();
        assertThat(request().getPath()).isEqualTo("/mesos/tasks");

        server.enqueue(json("{\"tasks\":[]}"));
        api.getMesosTask("task-1");
        assertThat(request().getPath()).isEqualTo("/mesos/tasks?task_id=task-1");
    }

    @Test
    public void mesosLogsAppendEveryQueryParameterItWasGiven() throws Exception {
        server.enqueue(json("{\"data\":\"line\"}"));

        java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("offset", "0");
        params.put("length", "100");
        mesos().getLogs("/mesos/files/read?path=/var/log/", "stdout", params);

        String path = request().getPath();
        assertThat(path).contains("stdout").contains("offset=0").contains("length=100");
    }

    @Test
    public void anErrorStatusComesBackAsDataRatherThanAnException() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThat(marathon().getApps().getHttpStatus()).isEqualTo(500);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private CctMarathonServiceApiClient cctMarathon() {
        CctMarathonServiceApiClient api = CctMarathonServiceApiClient.getInstance(commong);
        api.setPort(String.valueOf(server.getPort()));
        return api;
    }

    private MarathonApiClient marathon() {
        MarathonApiClient api = MarathonApiClient.getInstance(commong);
        api.setPort(String.valueOf(server.getPort()));
        return api;
    }

    private MesosApiClient mesos() {
        MesosApiClient api = MesosApiClient.getInstance(commong);
        api.setPort(String.valueOf(server.getPort()));
        return api;
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private RecordedRequest request() throws InterruptedException {
        return server.takeRequest();
    }
}
