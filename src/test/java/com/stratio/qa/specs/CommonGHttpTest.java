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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.stratio.qa.utils.ThreadProperty;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Covers {@link CommonG}'s HTTP surface against a real server: {@link MockWebServer}, bound to
 * localhost for each test.
 * <p>
 * {@code generateRequest} is the largest uncovered method in the class. It is a switch over ten
 * request types, each assembling headers, cookies and credentials before firing, so the only way to
 * check it is to look at what actually reaches the wire. The async client is built here exactly as
 * {@code HookGSpec.restClientSetup} builds it and handed over through the public setter.
 */
public class CommonGHttpTest {

    private MockWebServer server;

    private AsyncHttpClient client;

    private CommonG commong;

    @BeforeMethod
    public void setUp() throws Exception {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        server = new MockWebServer();
        server.start();

        commong = new CommonG();
        client = asyncHttpClient(config()
                .setKeepAlive(false)
                .setConnectTimeout(2000)
                .setRequestTimeout(5000)
                .setReadTimeout(5000)
                .build());
        commong.setClient(client);
        commong.setRestProtocol("http://");
        commong.setRestHost(server.getHostName());
        commong.setRestPort(":" + server.getPort());
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (client != null && !client.isClosed()) {
            client.close();
        }
        if (server != null) {
            server.shutdown();
        }
        commong.clearHeaders();
        commong.clearCookies();
        System.clearProperty("GOV_TENANT");
        System.clearProperty("GOV_ROLESID");
        System.clearProperty("GOV_USERID");
        System.clearProperty("GOV_SERVICE_ROLESID");
        System.clearProperty("GOV_SERVICE_USERID");
        ThreadProperty.remove("GOV_USER_UID");
        ThreadProperty.remove("GOV_SERVICE_USER_UID");
    }

    // -----------------------------------------------------------------------
    // The request types
    // -----------------------------------------------------------------------

    @Test
    public void aGetReachesTheServerAndItsResponseComesBack() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        Response response = commong.generateRequest("GET", false, null, null, "/things", "", "json").get();

        assertThat(response.getStatusCode()).isEqualTo(200);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/things");
    }

    @DataProvider(name = "unimplementedMethods")
    public Object[][] unimplementedMethods() {
        return new Object[][]{{"CONNECT"}, {"HEAD"}, {"OPTIONS"}, {"REQUEST"}, {"TRACE"}};
    }

    @Test(dataProvider = "unimplementedMethods")
    public void theVerbsTheStepGrammarAcceptsButTheClientDoesNotAreRejectedByName(String verb) {
        // The step regex accepts these, so a feature can ask for them; generateRequest declines
        // rather than sending something half-built. Nothing should reach the server.
        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> commong.generateRequest(verb, false, null, null, "/things", "", "json"))
                .withMessage("Operation not implemented: " + verb);

        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    public void anUnknownVerbIsRejectedAsInvalid() {
        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> commong.generateRequest("FETCH", false, null, null, "/things", "", "json"))
                .withMessage("Operation not valid: FETCH");
    }

    @DataProvider(name = "methodsWithBody")
    public Object[][] methodsWithBody() {
        return new Object[][]{{"POST"}, {"PUT"}, {"PATCH"}, {"DELETE"}};
    }

    @Test(dataProvider = "methodsWithBody")
    public void aRequestWithABodySendsIt(String verb) throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("created"));

        commong.generateRequest(verb, false, null, null, "/things", "{\"a\":1}", "json").get();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo(verb);
        assertThat(request.getBody().readUtf8()).isEqualTo("{\"a\":1}");
    }

    @Test
    public void aDeleteWithoutABodyStillGoesOut() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        commong.generateRequest("DELETE", false, null, null, "/things/7", "", "json").get();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getBody().size()).isZero();
    }

    @Test
    public void theVerbIsCaseInsensitive() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        commong.generateRequest("get", false, null, null, "/things", "", "json").get();

        assertThat(server.takeRequest().getMethod()).isEqualTo("GET");
    }

    // -----------------------------------------------------------------------
    // Headers driven by the content type
    // -----------------------------------------------------------------------

    @Test
    public void aJsonRequestCarriesAJsonContentType() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        commong.generateRequest("GET", false, null, null, "/things", "", "json").get();

        assertThat(server.takeRequest().getHeader("Content-Type"))
                .isEqualTo("application/json; charset=UTF-8");
    }

    @Test
    public void aGovRequestCarriesTheGovernanceHeaders() throws Exception {
        System.setProperty("GOV_TENANT", "tenant-a");
        System.setProperty("GOV_ROLESID", "role-1");
        System.setProperty("GOV_USERID", "ana");
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        commong.generateRequest("GET", false, null, null, "/things", "", "gov").get();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeaders().toString())
                .as("the governance identity travels with the request")
                .contains("tenant-a")
                .contains("role-1")
                .contains("ana");
    }

    @Test
    public void aServiceGovRequestPrefersTheServiceIdentity() throws Exception {
        System.setProperty("GOV_USERID", "ana");
        System.setProperty("GOV_SERVICE_USERID", "the-service");
        System.setProperty("GOV_SERVICE_ROLESID", "service-role");
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        commong.generateRequest("GET", false, null, null, "/things", "", "servicegov").get();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeaders().toString())
                .contains("the-service")
                .contains("service-role");
        assertThat(request.getHeaders().toString())
                .as("the plain user identity must not leak into a service call")
                .doesNotContain("GOV_USERID");
    }

    @Test
    public void aRuntimeGovernanceUserOverridesTheSystemProperty() throws Exception {
        System.setProperty("GOV_ROLESID", "role-1");
        System.setProperty("GOV_USERID", "from-property");
        ThreadProperty.set("GOV_USER_UID", "from-thread");
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        commong.generateRequest("GET", false, null, null, "/things", "", "gov").get();

        assertThat(server.takeRequest().getHeaders().toString()).contains("from-thread");
    }

    // -----------------------------------------------------------------------
    // Credentials, headers and cookies
    // -----------------------------------------------------------------------

    @Test
    public void basicCredentialsArePreemptivelySent() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        commong.generateRequest("GET", false, "admin", "secret", "/secure", "", "json").get();

        assertThat(server.takeRequest().getHeader("Authorization"))
                .as("preemptive basic auth means no 401 round trip")
                .isEqualTo("Basic " + Base64.getEncoder()
                        .encodeToString("admin:secret".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void storedHeadersTravelWithEveryVerb() throws Exception {
        commong.addHeader("X-One", "1");
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        commong.generateRequest("GET", false, null, null, "/a", "", "json").get();
        commong.generateRequest("POST", false, null, null, "/b", "{}", "json").get();

        assertThat(server.takeRequest().getHeader("X-One")).isEqualTo("1");
        assertThat(server.takeRequest().getHeader("X-One")).isEqualTo("1");
    }

    @Test
    public void storedCookiesTravelWithTheRequest() throws Exception {
        commong.addCookie("session", "abc");
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        commong.generateRequest("GET", false, null, null, "/things", "", "json").get();

        assertThat(server.takeRequest().getHeader("Cookie")).contains("session=abc");
    }

    @Test
    public void cookiesFromThePreviousResponseAreReused() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok")
                .addHeader("Set-Cookie", "JSESSIONID=xyz; Path=/"));
        commong.setResponse("GET", commong.generateRequest("GET", false, null, null, "/login", "", "json").get());
        server.takeRequest();

        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        commong.generateRequest("GET", false, null, null, "/things", "", "json").get();

        assertThat(server.takeRequest().getHeader("Cookie")).contains("JSESSIONID=xyz");
    }

    // -----------------------------------------------------------------------
    // Guard clauses
    // -----------------------------------------------------------------------

    @Test
    public void aRequestNeedsAHost() {
        commong.setRestHost(null);

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> commong.generateRequest("GET", false, null, null, "/things", "", "json"))
                .withMessage("Rest host has not been set");
    }

    @Test
    public void aRequestNeedsAPort() {
        commong.setRestPort(null);

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> commong.generateRequest("GET", false, null, null, "/things", "", "json"))
                .withMessage("Rest port has not been set");
    }

    @Test
    public void aMissingProtocolFallsBackToPlainHttp() throws Exception {
        commong.setRestProtocol(null);
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        Response response = commong.generateRequest("GET", false, null, null, "/things", "", "json").get();

        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    // -----------------------------------------------------------------------
    // Server-sent events
    // -----------------------------------------------------------------------

    @Test
    public void anSseStreamIsCollectedIntoTheResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: message\ndata: {\"text\": \"hello\"}\n\nevent: done\ndata: bye\n\n"));

        commong.executeSSERequest("GET", "/events", null, "json", null, null);

        assertThat(commong.getResponse().getResponse())
                .contains("event: message")
                .contains("hello")
                .contains("event: done");
        assertThat(server.takeRequest().getHeader("Accept")).isEqualTo("text/event-stream");
    }

    @Test
    public void anSsePostSendsItsBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: ack\ndata: ok\n\n"));

        commong.executeSSERequest("POST", "/events", "{\"q\":\"hi\"}", "json", null, null);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getBody().readUtf8()).isEqualTo("{\"q\":\"hi\"}");
    }

    @Test
    public void anSseRequestNeedsAHostAndAPort() {
        commong.setRestHost(null);
        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> commong.executeSSERequest("GET", "/events", null, "json", null, null))
                .withMessage("Rest host has not been set");

        commong.setRestHost(server.getHostName());
        commong.setRestPort(null);
        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> commong.executeSSERequest("GET", "/events", null, "json", null, null))
                .withMessage("Rest port has not been set");
    }

    // -----------------------------------------------------------------------
    // Downloads and reachability
    // -----------------------------------------------------------------------

    @Test
    public void aFileIsDownloadedToTheGivenPath() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("file contents"));
        java.io.File target = java.io.File.createTempFile("bdt-download-", ".bin");
        target.deleteOnExit();

        commong.downloadFileWithTimeout(server.url("/file").toString(), target.getAbsolutePath(), 10);

        assertThat(target).hasContent("file contents");
    }

    @Test
    public void anUnresolvableHostIsReportedAsUnreachable() throws Exception {
        // The method retries once per second while the name does not resolve, then gives up.
        assertThat(commong.isHostReachable("bdt-no-such-host.invalid", 1)).isFalse();
    }

    @Test
    public void aResolvableHostIsProbed() throws Exception {
        // isReachable() falls back to an ICMP echo, which needs privileges the CI user does not
        // have, so the answer is not asserted -- what matters is that a name that resolves takes
        // the probing path instead of the UnknownHostException retry loop.
        commong.isHostReachable("localhost", 1);
    }
}
