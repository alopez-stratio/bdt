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
import static org.assertj.core.api.Assertions.tuple;
import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.asynchttpclient.AsyncHttpClient;
import org.json.JSONObject;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.stratio.qa.utils.ThreadProperty;

import io.netty.handler.codec.http.cookie.Cookie;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Covers the REST steps against a real HTTP server: {@link MockWebServer}, bound to localhost for
 * the duration of each test.
 * <p>
 * {@code HookGSpec.restClientSetup} builds the async client and hands it to {@link CommonG}; the
 * setter is public, so a test can do the same and point {@code setupRestClient} at the mock server's
 * port. Requests, responses, headers and body streams are then the real thing rather than stubs,
 * which matters for the steps that read a header off the response or copy its stream to a file.
 * <p>
 * {@code mockwebserver} is already on the test classpath, pulled in by {@code kubernetes-server-mock}
 * at the same okhttp version the project declares in {@code ok-http-client.version}. No new
 * dependency is needed.
 */
public class RestSpecHttpTest {

    private MockWebServer server;

    private AsyncHttpClient client;

    private CommonG commong;

    private RestSpec restSpec;

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
        restSpec = new RestSpec(commong);
        restSpec.setupRestClient(null, server.getHostName(), ":" + server.getPort());
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
    }

    // -----------------------------------------------------------------------
    // setupRestClient
    // -----------------------------------------------------------------------

    @Test
    public void anUnsecuredClientWithoutAPortDefaultsToPlainHttpOn80() {
        restSpec.setupRestClient(null, "example.local", null);

        assertThat(commong.getRestProtocol()).isEqualTo("http://");
        assertThat(commong.getRestHost()).isEqualTo("example.local");
        assertThat(commong.getRestPort()).isEqualTo(":80");
    }

    @Test
    public void aSecuredClientWithoutAPortDefaultsToHttpsOn443() {
        restSpec.setupRestClient(" securely", "example.local", null);

        assertThat(commong.getRestProtocol()).isEqualTo("https://");
        assertThat(commong.getRestPort()).isEqualTo(":443");
    }

    @Test
    public void anExplicitHostAndPortAreKeptAsGiven() {
        restSpec.setupRestClient(null, "example.local", ":8080");

        assertThat(commong.getRestHost()).isEqualTo("example.local");
        assertThat(commong.getRestPort()).isEqualTo(":8080");
    }

    @Test
    public void aMissingHostDefaultsToLocalhost() {
        restSpec.setupRestClient(null, null, ":8080");

        assertThat(commong.getRestHost()).isEqualTo("localhost");
    }

    // -----------------------------------------------------------------------
    // Sending a request
    // -----------------------------------------------------------------------

    @Test
    public void aRequestReachesTheServerAndItsResponseIsStored() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"id\":\"7\"}"));

        restSpec.sendRequestNoDataTable("POST", "/things", null, null, "json");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/things");
        assertThat(commong.getResponse().getStatusCode()).isEqualTo(201);
        assertThat(commong.getResponse().getResponse()).isEqualTo("{\"id\":\"7\"}");
    }

    @Test
    public void basicCredentialsAreSplitOnTheFirstColon() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        restSpec.sendRequestNoDataTable("GET", "/secure", "admin:pa:ss", null, "json");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Authorization"))
                .as("the password keeps every colon after the first")
                .isEqualTo("Basic " + java.util.Base64.getEncoder()
                        .encodeToString("admin:pa:ss".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void aRequestBodyIsReadFromTheGivenBaseDataFile() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        restSpec.sendRequestNoDataTable("POST", "/things", null, "schemas/simple1.json", "json");

        assertThat(server.takeRequest().getBody().readUtf8()).isEqualTo("{\"a\":true}");
    }

    // -----------------------------------------------------------------------
    // Response assertions
    // -----------------------------------------------------------------------

    @Test
    public void theExpectedStatusCodeIsAccepted() throws Exception {
        respondWith(200, "[]");

        restSpec.assertResponseStatusLength(200, null, null);
    }

    @Test
    public void anUnexpectedStatusCodeIsReportedWithTheBody() throws Exception {
        respondWith(500, "boom");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> restSpec.assertResponseStatusLength(200, null, null))
                .withMessageContaining("Status code is expected to be '200' but was '500'")
                .withMessageContaining("boom");
    }

    @Test
    public void theResponseLengthIsCheckedAgainstTheJsonArray() throws Exception {
        respondWith(200, "[{\"a\":1},{\"a\":2}]");

        restSpec.assertResponseStatusLength(200, "2", null);

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> restSpec.assertResponseStatusLength(200, "3", null))
                .withMessageContaining("Response length is expected to be '3' but was '2'");
    }

    @Test
    public void theResponseTextIsCheckedWithTheContainsOrMatchesConvention() throws Exception {
        respondWith(200, "the service is RUNNING");

        restSpec.assertResponseStatusLength(200, null, "RUNNING");
        restSpec.assertResponseStatusLength(200, null, "regex:.*RUNN.*");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> restSpec.assertResponseStatusLength(200, null, "STOPPED"))
                .withMessageContaining("Response is expected to contain the text");
    }

    @Test
    public void aStatusCodeIsAcceptedWhenItIsOneOfSeveral() throws Exception {
        respondWith(409, "conflict");

        restSpec.assertResponseStatus("201, 409");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> restSpec.assertResponseStatus("200,201"))
                .withMessageContaining("Status code is expected to be one of");
    }

    @Test
    public void theResponseTextIsAssertedOnItsOwn() throws Exception {
        respondWith(200, "the service is RUNNING");

        restSpec.assertResponseMessage("RUNNING");
        restSpec.assertNegativeResponseMessage("STOPPED");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> restSpec.assertResponseMessage("STOPPED"));
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> restSpec.assertNegativeResponseMessage("RUNNING"));
    }

    // -----------------------------------------------------------------------
    // Polling until the response says what we want
    // -----------------------------------------------------------------------

    @Test
    public void pollingStopsAsSoonAsTheResponseContainsTheValue() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("STARTING"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("RUNNING"));

        restSpec.sendRequestTimeout(5, 1, "GET", "/status", "json", null, "RUNNING");

        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    public void pollingGivesUpWithTheLastAssertionErrorWhenTheValueNeverArrives() {
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("STARTING"));
        }

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> restSpec.sendRequestTimeout(1, 1, "GET", "/status", "json", null, "RUNNING"));
    }

    @Test
    public void pollingForTheAbsenceOfAValueStopsOnTheFirstResponseWithoutIt() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("STARTING"));

        restSpec.sendRequestTimeout(5, 1, "GET", "/status", "json", " does not", "RUNNING");

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    public void pollingOnAJsonFieldStopsWhenItReachesTheExpectedValue() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"STARTING\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"RUNNING\"}"));

        restSpec.sendRequestTimeoutExactValue(5, 1, "GET", "/status", "json", "$.status", null, null, "RUNNING");

        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    public void pollingOnAJsonFieldAcceptsARegexInsteadOfAnExactValue() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"RUNNING\"}"));

        restSpec.sendRequestTimeoutExactValue(5, 1, "GET", "/status", "json", "$.status", null, " this regex", "RUNN.*");
    }

    @Test
    public void pollingOnAJsonFieldCanWaitForItToStopMatching() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"STOPPED\"}"));

        restSpec.sendRequestTimeoutExactValue(5, 1, "GET", "/status", "json", "$.status", " not", null, "RUNNING");

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    public void pollingOnAJsonFieldGivesUpWhenTheValueNeverArrives() {
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"STARTING\"}"));
        }

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> restSpec.sendRequestTimeoutExactValue(
                        1, 1, "GET", "/status", "json", "$.status", null, null, "RUNNING"));
    }

    @Test
    public void pollingWithADataTableAppliesTheModificationsToTheRequestBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("RUNNING"));

        restSpec.sendRequestDataTableTimeout(5, 1, "POST", "/things", null, "RUNNING",
                "schemas/simple1.json", "json",
                io.cucumber.datatable.DataTable.create(java.util.Arrays.asList(
                        java.util.Arrays.asList("$.a", "UPDATE", "false"))));

        assertThat(server.takeRequest().getBody().readUtf8())
                .as("the modification must reach the wire")
                .contains("false");
    }

    // -----------------------------------------------------------------------
    // Saving the response
    // -----------------------------------------------------------------------

    @Test
    public void theResponseBodyIsSavedToAVariableAndToAFile() throws Exception {
        respondWith(200, "the body");

        restSpec.saveResponseInEnvironmentVariableFile("savedBody", "restspec-body.txt", null);

        assertThat(ThreadProperty.get("savedBody")).isEqualTo("the body");
        assertThat(testClassesFile("restspec-body.txt")).hasContent("the body");
    }

    @Test
    public void anInvalidEncodingFallsBackToUtf8() throws Exception {
        respondWith(200, "the body");

        restSpec.saveResponseInEnvironmentVariableFile(null, "restspec-encoding.txt", "not-a-charset");

        assertThat(testClassesFile("restspec-encoding.txt")).hasContent("the body");
    }

    @Test
    public void savingTheResponseNeedsAVariableOrAFile() throws Exception {
        respondWith(200, "the body");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> restSpec.saveResponseInEnvironmentVariableFile(null, null, null))
                .withMessageContaining("No environment variable neither file defined");
    }

    @Test
    public void theResponseStreamIsCopiedToAFile() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("downloaded"));
        restSpec.sendRequestNoDataTable("GET", "/file", null, null, "json");

        File target = new File(System.getProperty("user.dir") + "/target/restspec-download.bin");
        try {
            restSpec.saveFileFromResponse(target.getAbsolutePath());

            assertThat(target).hasContent("downloaded");
        } finally {
            Files.deleteIfExists(target.toPath());
        }
    }

    @Test
    public void theResponseHeadersAreSavedToAVariableAndToAFile() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok").setHeader("X-Trace", "abc123"));
        restSpec.sendRequestNoDataTable("GET", "/headers", null, null, "json");

        restSpec.saveResponseHeadersInEnvironmentVariableFile("savedHeaders", "restspec-headers.txt");

        assertThat(ThreadProperty.get("savedHeaders")).contains("X-Trace");
        assertThat(readFile(testClassesFile("restspec-headers.txt"))).contains("X-Trace");
    }

    @Test
    public void savingTheHeadersNeedsAVariableOrAFile() throws Exception {
        respondWith(200, "ok");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> restSpec.saveResponseHeadersInEnvironmentVariableFile(null, null))
                .withMessageContaining("No environment variable neither file defined");
    }

    @Test
    public void oneResponseHeaderIsSavedToAVariableAndToAFile() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok").setHeader("X-Trace", "abc123"));
        restSpec.sendRequestNoDataTable("GET", "/headers", null, null, "json");

        restSpec.saveResponseHeaderValueInEnvironmentVariableFile("X-Trace", "savedHeader", "restspec-header.txt");

        assertThat(ThreadProperty.get("savedHeader")).isEqualTo("abc123");
        assertThat(testClassesFile("restspec-header.txt")).hasContent("abc123");
    }

    @Test
    public void aMissingResponseHeaderIsReported() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        restSpec.sendRequestNoDataTable("GET", "/headers", null, null, "json");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> restSpec.saveResponseHeaderValueInEnvironmentVariableFile(
                        "X-Missing", "savedHeader", null))
                .withMessageContaining("was not found");
    }

    // -----------------------------------------------------------------------
    // Server-sent events
    // -----------------------------------------------------------------------

    @Test
    public void sseEventsAreParsedIntoTypeAndValuePairs() throws Exception {
        respondWith(200, String.join("\n",
                "event: message",
                "data: {\"text\": \"hello\"}",
                "",
                "event: note",
                "data: \"plain text\"",
                "",
                // An event with no data line is only emitted once a further line closes it, so it
                // cannot be the last thing in the stream -- see the test below.
                "event: done",
                "",
                "event: ignored"));

        JSONObject parsed = restSpec.parseSSEEvents("sseEvents");

        assertThat(parsed.getJSONArray("events").length()).isEqualTo(3);
        assertThat(parsed.getJSONArray("events").getJSONObject(0).getString("event_type")).isEqualTo("message");
        assertThat(parsed.getJSONArray("events").getJSONObject(0).getJSONObject("event_value").getString("text"))
                .isEqualTo("hello");
        assertThat(parsed.getJSONArray("events").getJSONObject(1).getString("event_value"))
                .as("data that is not JSON is stored unquoted")
                .isEqualTo("plain text");
        assertThat(parsed.getJSONArray("events").getJSONObject(2).getString("event_value"))
                .as("an event with no data line is marked as empty")
                .isEqualTo("_EMPTY_EVENT_");
        assertThat(ThreadProperty.get("sseEvents")).contains("event_type");
    }

    @Test
    public void anSseEventWithNoDataIsDroppedWhenItEndsTheStream() throws Exception {
        respondWith(200, String.join("\n",
                "event: message",
                "data: {\"text\": \"hello\"}",
                "",
                "event: done"));

        JSONObject parsed = restSpec.parseSSEEvents("sseTrailing");

        // The empty-event branch only fires on the line *after* the event, so a stream that ends
        // right on an "event:" line loses it. Documents current behaviour.
        assertThat(parsed.getJSONArray("events").length()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Cookies and headers
    // -----------------------------------------------------------------------

    @Test
    public void headersAreAddedReplacedAndDeleted() throws Exception {
        restSpec.addNewHeader("X-One", "1");
        restSpec.addNewHeader("X-Two", "2");
        assertThat(commong.getHeaders()).containsEntry("X-One", "1").containsEntry("X-Two", "2");

        restSpec.replaceHeader("X-One", "one");
        assertThat(commong.getHeaders()).containsEntry("X-One", "one");

        restSpec.deleteHeader("X-Two");
        assertThat(commong.getHeaders()).doesNotContainKey("X-Two");

        restSpec.clearStoredHeaders();
        assertThat(commong.getHeaders()).isEmpty();
    }

    @Test
    public void anAddedHeaderTravelsWithTheRequest() throws Exception {
        restSpec.addNewHeader("X-One", "1");
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        restSpec.sendRequestNoDataTable("GET", "/things", null, null, "json");

        assertThat(server.takeRequest().getHeader("X-One")).isEqualTo("1");
    }

    @Test
    public void cookiesAreAddedReplacedAndDeleted() {
        restSpec.clearStoredCookies();
        restSpec.addNewCookie("session", "abc");
        restSpec.addNewCookie("other", "xyz");
        assertThat(commong.getCookies()).extracting(Cookie::name).containsExactly("session", "other");

        restSpec.replaceCookie("session", "def");
        assertThat(commong.getCookies())
                .as("replacing deletes the old cookie first, so it moves to the end")
                .extracting(Cookie::name, Cookie::value)
                .contains(tuple("session", "def"));

        restSpec.deleteCookie("other");
        assertThat(commong.getCookies()).extracting(Cookie::name).doesNotContain("other");

        restSpec.clearStoredCookies();
        assertThat(commong.getCookies()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Performs a real round trip so the stored response carries genuine headers and body stream. */
    private void respondWith(int status, String body) throws Exception {
        server.enqueue(new MockResponse().setResponseCode(status).setBody(body));
        restSpec.sendRequestNoDataTable("GET", "/", null, null, "json");
    }

    private String readFile(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private File testClassesFile(String name) {
        File file = Paths.get(System.getProperty("user.dir"), "target", "test-classes", name).toFile();
        file.deleteOnExit();
        return file;
    }
}
