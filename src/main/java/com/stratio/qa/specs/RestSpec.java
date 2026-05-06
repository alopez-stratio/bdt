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

import com.stratio.qa.utils.HttpResponse;
import org.apache.commons.io.FileUtils;
import org.asynchttpclient.Response;
import com.stratio.qa.assertions.Assertions;
import com.stratio.qa.utils.ThreadProperty;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.datatable.DataTable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.stratio.qa.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Generic API Rest Specs.
 *
 * @see <a href="RestSpec-annotations.html">REST Steps &amp; Matching Regex</a>
 */
public class RestSpec extends BaseGSpec {

    /**
     * Generic constructor.
     *
     * @param spec object
     */
    public RestSpec(CommonG spec) {
        this.commonspec = spec;

    }

    /**
     * Send requests to {@code restHost @code restPort}.
     *
     * @param restHost host where api is running
     * @param restPort port where api is running
     */
    @Given("^I( securely)? send requests to '([^:]+?)(:.+?)?'$")
    public void setupRestClient(String isSecured, String restHost, String restPort) {
        String restProtocol = "http://";

        if (isSecured != null) {
            restProtocol = "https://";
        }


        if (restHost == null) {
            restHost = "localhost";
        }

        if (restPort == null) {
            if (isSecured == null) {
                restPort = ":80";
            } else {
                restPort = ":443";
            }
        }

        commonspec.setRestProtocol(restProtocol);
        commonspec.setRestHost(restHost);
        commonspec.setRestPort(restPort);
    }

    /**
     * Send a request of the type specified but in this case, the response is checked until it contains the expected value
     *
     * @param requestType   type of request to be sent. Possible values:
     *                      GET|DELETE|POST|PUT|CONNECT|PATCH|HEAD|OPTIONS|REQUEST|TRACE
     * @param timeout
     * @param wait
     * @param responseVal
     * @param endPoint      end point to be used
     * @param baseData      path to file containing the schema to be used
     * @param type          element to read from file (element should contain a json)
     * @param modifications DataTable containing the modifications to be done to the
     *                      base schema element. Syntax will be:
     *                      {@code
     *                      | <key path> | <type of modification> | <new value> |
     *                      }
     *                      where:
     *                      key path: path to the key to be modified
     *                      type of modification: DELETE|ADD|UPDATE
     *                      new value: in case of UPDATE or ADD, new value to be used
     *                      for example:
     *                      if the element read is {"key1": "value1", "key2": {"key3": "value3"}}
     *                      and we want to modify the value in "key3" with "new value3"
     *                      the modification will be:
     *                      | key2.key3 | UPDATE | "new value3" |
     *                      being the result of the modification: {"key1": "value1", "key2": {"key3": "new value3"}}
     * @throws Exception
     */
    @Given("^in less than '(\\d+)' seconds, checking each '(\\d+)' seconds, I send a '(.+?)' request to '(.+?)' so that the response( does not)? contains '(.+?)' based on '([^:]+?)'( as '(json|string|gov|servicegov|textplain)')? with:$")
    public void sendRequestDataTableTimeout(Integer timeout, Integer wait, String requestType, String endPoint, String contains, String responseVal, String baseData, String type, DataTable modifications) throws Exception {
        // Retrieve data
        String retrievedData = commonspec.retrieveData(baseData, type);

        // Modify data
        commonspec.getLogger().debug("Modifying data {} as {}", retrievedData, type);
        String modifiedData = commonspec.modifyData(retrievedData, type, modifications);

        Boolean searchUntilContains;
        if (contains == null || contains.isEmpty()) {
            searchUntilContains = Boolean.TRUE;
        } else {
            searchUntilContains = Boolean.FALSE;
        }
        Boolean found = !searchUntilContains;
        AssertionError ex = null;

        Future<Response> response;

        Pattern pattern = CommonG.matchesOrContains(responseVal);

        for (int i = 0; (i <= timeout); i += wait) {
            if (found && searchUntilContains) {
                break;
            }
            try {
                commonspec.getLogger().debug("Generating request {} to {} with data {} as {}", requestType, endPoint, modifiedData, type);
                response = commonspec.generateRequest(requestType, false, null, null, endPoint, modifiedData, type);
                commonspec.getLogger().debug("Saving response");
                commonspec.setResponse(requestType, response.get());
                commonspec.getLogger().debug("Response body: \n" + commonspec.getResponse());
                commonspec.getLogger().debug("Checking response value");

                if (searchUntilContains) {
                    assertThat(commonspec.getResponse().getResponse()).containsPattern(pattern);
                    found = true;
                    timeout = i;
                } else {
                    assertThat(commonspec.getResponse().getResponse()).doesNotContain(responseVal);
                    found = false;
                    timeout = i;
                }
            } catch (AssertionError | Exception e) {
                if (!found) {
                    commonspec.getLogger().info("Response value not found after " + i + " seconds");
                } else {
                    commonspec.getLogger().info("Response value found after " + i + " seconds");
                }
                Thread.sleep(wait * 1000);
                if (e instanceof AssertionError) {
                    ex = (AssertionError) e;
                }
            }
            if (!found && !searchUntilContains) {
                break;
            }
        }
        if ((!found && searchUntilContains) || (found && !searchUntilContains)) {
            throw (ex);
        }
        if (searchUntilContains) {
            commonspec.getLogger().info("Success! Response value found after " + timeout + " seconds");
        } else {
            commonspec.getLogger().info("Success! Response value not found after " + timeout + " seconds");
        }
    }

    /**
     * Send a request of the type specified
     *
     * @param requestType   type of request to be sent. Possible values:
     *                      GET|DELETE|POST|PUT|CONNECT|PATCH|HEAD|OPTIONS|REQUEST|TRACE
     * @param endPoint      end point to be used
     * @param baseData      path to file containing the schema to be used
     * @param type          element to read from file (element should contain a json)
     * @param modifications DataTable containing the modifications to be done to the
     *                      base schema element. Syntax will be:
     *                      {@code
     *                      | <key path> | <type of modification> | <new value> |
     *                      }
     *                      where:
     *                      key path: path to the key to be modified
     *                      type of modification: DELETE|ADD|UPDATE
     *                      new value: in case of UPDATE or ADD, new value to be used
     *                      for example:
     *                      if the element read is {"key1": "value1", "key2": {"key3": "value3"}}
     *                      and we want to modify the value in "key3" with "new value3"
     *                      the modification will be:
     *                      | key2.key3 | UPDATE | "new value3" |
     *                      being the result of the modification: {"key1": "value1", "key2": {"key3": "new value3"}}
     * @throws Exception
     */
    @When("^I send a '(.+?)' request to '(.+?)'( with user and password '(.+:.+?)')? based on '([^:]+?)'( as '(json|string|gov|scim|servicegov|textplain)')? with:$")
    public void sendRequest(String requestType, String endPoint, String loginInfo, String baseData, String type, DataTable modifications) throws Exception {
        // Retrieve data
        String retrievedData = commonspec.retrieveData(baseData, type);

        // Modify data
        commonspec.getLogger().debug("Modifying data {} as {}", retrievedData, type);
        String modifiedData = commonspec.modifyData(retrievedData, type, modifications);

        String user = null;
        String password = null;
        if (loginInfo != null) {
            user = loginInfo.substring(0, loginInfo.indexOf(':'));
            password = loginInfo.substring(loginInfo.indexOf(':') + 1);
        }


        commonspec.getLogger().debug("Generating request {} to {} with data {} as {}", requestType, endPoint, modifiedData, type);
        Future<Response> response = commonspec.generateRequest(requestType, false, user, password, endPoint, modifiedData, type, "");

        // Save response
        commonspec.getLogger().debug("Saving response");
        commonspec.setResponse(requestType, response.get());
        commonspec.getLogger().debug("Response body: \n" + commonspec.getResponse().getResponse());
    }

    /**
     * Same sendRequest, but in this case, we do not receive a data table with modifications.
     * Besides, the data and request header are optional as well.
     * In case we want to simulate sending a json request with empty data, we just to avoid baseData
     *
     * @param requestType
     * @param endPoint
     * @param baseData
     * @param type
     * @throws Exception
     */
    @When("^I send a '(.+?)' request to '(.+?)'( with user and password '(.+:.+?)')?( based on '([^:]+?)')?( as '(json|string|gov|scim|servicegov|textplain)')?$")
    public void sendRequestNoDataTable(String requestType, String endPoint, String loginInfo, String baseData, String type) throws Exception {
        Future<Response> response;
        String user = null;
        String password = null;

        if (loginInfo != null) {
            user = loginInfo.substring(0, loginInfo.indexOf(':'));
            password = loginInfo.substring(loginInfo.indexOf(':') + 1);
        }

        if (baseData != null) {
            // Retrieve data
            String retrievedData = commonspec.retrieveData(baseData, type);
            // Generate request
            response = commonspec.generateRequest(requestType, false, user, password, endPoint, retrievedData, type);
        } else {
            // Generate request
            response = commonspec.generateRequest(requestType, false, user, password, endPoint, "", type);
        }

        // Save response
        commonspec.setResponse(requestType, response.get());
        commonspec.getLogger().debug("Response body: \n" + commonspec.getResponse().getResponse());
    }

    /**
     * Same sendRequest, but in this case, the response is checked until it contains the expected value
     *
     * @param timeout
     * @param wait
     * @param requestType
     * @param endPoint
     * @param responseVal
     * @throws Exception
     */
    @When("^in less than '(\\d+)' seconds, checking each '(\\d+)' seconds, I send a '(.+?)' request to '(.+?)'( as '(json|string|gov|scim|servicegov|textplain)')? so that the response( does not)? contains '(.+?)'$")
    public void sendRequestTimeout(Integer timeout, Integer wait, String requestType, String endPoint, String type, String contains, String responseVal) throws Exception {
        AssertionError ex = null;
        Future<Response> response;

        if (responseVal != null) {
            Boolean searchUntilContains;
            if (contains == null || contains.isEmpty()) {
                searchUntilContains = Boolean.TRUE;
            } else {
                searchUntilContains = Boolean.FALSE;
            }
            Boolean found = !searchUntilContains;

            Pattern pattern = CommonG.matchesOrContains(responseVal);
            for (int i = 0; (i <= timeout); i += wait) {
                if (found && searchUntilContains) {
                    break;
                }
                try {
                    response = commonspec.generateRequest(requestType, false, null, null, endPoint, "", type, "");
                    commonspec.setResponse(requestType, response.get());
                    commonspec.getLogger().debug("Checking response value");
                    commonspec.getLogger().debug("Response body: \n" + commonspec.getResponse().getResponse());

                    if (searchUntilContains) {
                        assertThat(commonspec.getResponse().getResponse()).containsPattern(pattern);
                        found = true;
                        timeout = i;
                    } else {
                        assertThat(commonspec.getResponse().getResponse()).doesNotContain(responseVal);
                        found = false;
                        timeout = i;
                    }
                } catch (AssertionError | Exception e) {
                    if (!found) {
                        commonspec.getLogger().info("Response value not found after " + i + " seconds");
                    } else {
                        commonspec.getLogger().info("Response value found after " + i + " seconds");
                    }
                    Thread.sleep(wait * 1000);
                    if (e instanceof AssertionError) {
                        ex = (AssertionError) e;
                    }
                }
                if (!found && !searchUntilContains) {
                    break;
                }
            }
            if ((!found && searchUntilContains) || (found && !searchUntilContains)) {
                throw (ex);
            }
            if (searchUntilContains) {
                commonspec.getLogger().info("Success! Response value found after " + timeout + " seconds");
            } else {
                commonspec.getLogger().info("Success! Response value not found after " + timeout + " seconds");
            }
        } else {

            for (int i = 0; (i <= timeout); i += wait) {
                try {
                    response = commonspec.generateRequest(requestType, false, null, null, endPoint, "", type, "");
                    commonspec.setResponse(requestType, response.get());
                    commonspec.getLogger().debug("Checking response value");
                    commonspec.getLogger().debug("Response body: \n" + commonspec.getResponse().getResponse());

                    assertThat(commonspec.getResponse().getResponse());
                    timeout = i;
                } catch (AssertionError | Exception e) {
                    Thread.sleep(wait * 1000);
                    if (e instanceof AssertionError) {
                        ex = (AssertionError) e;
                    }
                }
            }
        }
    }

    @When("^in less than '(\\d+)' seconds, checking each '(\\d+)' seconds, I send a '(.+?)' request to '(.+?)'( as '(json|string|gov|scim|servicegov|textplain)')? so that the field '(.+?)' is( not)? equals to( this regex)? '(.+?)'$")
    public void sendRequestTimeoutExactValue(Integer timeout, Integer wait, String requestType, String endPoint, String type, String expression, String contains, String regex, String result) throws Exception {
        AssertionError ex = null;
        Future<Response> response;

        if (result != null) {
            Boolean searchUntilContains;
            if (contains == null || contains.isEmpty()) {
                searchUntilContains = Boolean.TRUE;
            } else {
                searchUntilContains = Boolean.FALSE;
            }
            Boolean found = !searchUntilContains;

            for (int i = 0; (i <= timeout); i += wait) {
                if (found && searchUntilContains) {
                    break;
                }
                try {
                    response = commonspec.generateRequest(requestType, false, null, null, endPoint, "", type, "");
                    commonspec.setResponse(requestType, response.get());
                    commonspec.getLogger().debug("Checking response value");
                    commonspec.getLogger().debug("Response body: \n" + commonspec.getResponse().getResponse());
                    String value = commonspec.getJSONPathString(commonspec.getResponse().getResponse(), expression, null);

                    if (searchUntilContains) {
                        if (regex == null || regex.isEmpty()) {
                            assertThat(value).as("Evaluate JSONPath does not match with proposed value").isEqualTo(result);
                        } else {
                            assertThat(value).containsPattern(result);
                        }
                        found = true;
                        timeout = i;
                    } else {
                        if (regex == null || regex.isEmpty()) {
                            assertThat(value).as("Evaluate JSONPath match with proposed value").isNotEqualTo(result);
                        } else {
                            assertThat(value).doesNotContainPattern(result);
                        }
                        found = false;
                        timeout = i;
                    }
                } catch (AssertionError | Exception e) {
                    if (!found) {
                        commonspec.getLogger().info("Response value not found after " + i + " seconds");
                    } else {
                        commonspec.getLogger().info("Response value found after " + i + " seconds");
                    }
                    Thread.sleep(wait * 1000);
                    if (e instanceof AssertionError) {
                        ex = (AssertionError) e;
                    }
                }
                if (!found && !searchUntilContains) {
                    break;
                }
            }
            if ((!found && searchUntilContains) || (found && !searchUntilContains)) {
                throw (ex);
            }
            if (searchUntilContains) {
                commonspec.getLogger().info("Success! Response value found after " + timeout + " seconds");
            } else {
                commonspec.getLogger().info("Success! Response value not found after " + timeout + " seconds");
            }
        } else {

            for (int i = 0; (i <= timeout); i += wait) {
                try {
                    response = commonspec.generateRequest(requestType, false, null, null, endPoint, "", type, "");
                    commonspec.setResponse(requestType, response.get());
                    commonspec.getLogger().debug("Checking response value");
                    commonspec.getLogger().debug("Response body: \n" + commonspec.getResponse().getResponse());

                    assertThat(commonspec.getResponse().getResponse());
                    timeout = i;
                } catch (AssertionError | Exception e) {
                    Thread.sleep(wait * 1000);
                    if (e instanceof AssertionError) {
                        ex = (AssertionError) e;
                    }
                }
            }
        }
    }

    @Then("^the service response must contain the text '(.*?)'$")
    public void assertResponseMessage(String expectedText) throws SecurityException, IllegalArgumentException {
        Pattern pattern = CommonG.matchesOrContains(expectedText);
        try {
            assertThat(commonspec.getResponse().getResponse()).containsPattern(pattern);
        } catch (AssertionError e) {
            commonspec.getLogger().warn("Response: {}", commonspec.getResponse().getResponse());
            throw e;
        }
    }

    @Then("^the service response must not contain the text '(.*?)'$")
    public void assertNegativeResponseMessage(String expectedText) throws ClassNotFoundException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        try {
            assertThat(commonspec.getResponse().getResponse()).doesNotContain(expectedText);
        } catch (AssertionError e) {
            commonspec.getLogger().warn("Response: {}", commonspec.getResponse().getResponse());
            throw e;
        }
    }

    /**
     * Checks if the service response's status code is the expected one, and optionally checks the response length and content
     *
     * @param expectedStatus  expected status code
     * @param sExpectedLength expected JsonArray response length
     * @param expectedText    expected text to be present in response
     */
    @Then("^the service response status must be '(\\d+)'( and its response length must be '(\\d+)')?( and its response must contain the text '(.*?)')?$")
    public void assertResponseStatusLength(Integer expectedStatus, String sExpectedLength, String expectedText) {
        HttpResponse response = commonspec.getResponse();

        // Assert status code
        assertThat(response.getStatusCode())
                .withFailMessage("\nStatus code is expected to be '%d' but was '%d'.\nResponse: %s",
                        expectedStatus,
                        response.getStatusCode(),
                        response.getResponse())
                .isEqualTo(expectedStatus);

        // Assert Json Array response length
        if (sExpectedLength != null) {
            Integer expectedLength = Integer.parseInt(sExpectedLength);
            assertThat(new JSONArray(response.getResponse()).length())
                    .withFailMessage("\nResponse length is expected to be '%d' but was '%d'.\nResponse: '%s'",
                            expectedLength,
                            new JSONArray(response.getResponse()).length(),
                            response.getResponse())
                    .isEqualTo(expectedLength);
        }

        // Assert response contains text
        if (expectedText != null) {
            Pattern pattern = CommonG.matchesOrContains(expectedText);
            assertThat(response.getResponse())
                    .withFailMessage("\nResponse is expected to contain the text '%s' but it does not.\nResponse: '%s'",
                            pattern,
                            response.getResponse())
                    .containsPattern(pattern);
        }
    }

    /**
     * Checks if the service response's status code is one of the expected status codes, separated by comma (,)
     *
     * @param expectedStatus list of expected status codes, separated by comma (ex.: 201,409)
     */
    @Then("^the service response status must be one of '(.*)'$")
    public void assertResponseStatus(String expectedStatus) {
        HttpResponse response = commonspec.getResponse();
        List<Integer> expectedStatusList = Arrays.stream(expectedStatus.replace(" ", "").split(","))
                .map(Integer::parseInt).collect(Collectors.toList());

        assertThat(response.getStatusCode())
                .withFailMessage("Status code is expected to be one of '%s' but was '%d'.\nResponse: %s",
                        expectedStatusList,
                        response.getStatusCode(),
                        response.getResponse())
                .isIn(expectedStatusList);
    }

    @Then("^I save service response( in environment variable '(.*?)')?( in file '(.*?)')?( using encoding '(.*?)')?$")
    public void saveResponseInEnvironmentVariableFile(String envVar, String fileName, String encoding) throws Exception {

        if (envVar != null || fileName != null) {
            String value = commonspec.getResponse().getResponse();

            Charset usableCharset = StandardCharsets.UTF_8;
            if (encoding != null) {
                try {
                    usableCharset = Charset.forName(encoding);
                } catch (Exception e) {
                    commonspec.getLogger().warn("Provided encoding {} is not valid:\n{}", encoding, e.getMessage());
                    commonspec.getLogger().warn("Using UTF-8 encoding instead");
                }
            }

            if (envVar != null) {
                ThreadProperty.set(envVar, value);
            }

            if (fileName != null) {
                // Create file (temporary) and set path to be accessible within test
                File tempDirectory = new File(System.getProperty("user.dir") + "/target/test-classes/");
                String absolutePathFile = tempDirectory.getAbsolutePath() + "/" + fileName;
                commonspec.getLogger().debug("Creating file {} in 'target/test-classes'", absolutePathFile);
                // Note that this Writer will delete the file if it exists
                try (Writer out = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(absolutePathFile)), usableCharset))) {
                    out.write(value);
                } catch (Exception e) {
                    commonspec.getLogger().error("Custom file {} hasn't been created:\n{}", absolutePathFile, e.toString());
                }

                Assertions.assertThat(new File(absolutePathFile).isFile());
            }
        } else {
            fail("No environment variable neither file defined");
        }
    }

    @Then("^I save file '(.*?)' from response$")
    public void saveFileFromResponse(String fileName) throws Exception {
        FileUtils.copyInputStreamToFile(commonspec.getResponse().getInputStream(), new File(fileName));
    }

    @Then("^I save service response headers( in environment variable '(.*?)')?( in file '(.*?)')?$")
    public void saveResponseHeadersInEnvironmentVariableFile(String envVar, String fileName) throws Exception {

        if (envVar != null || fileName != null) {
            String value = commonspec.getResponse().getResponseHeaders().toString();

            if (envVar != null) {
                ThreadProperty.set(envVar, value);
            }

            if (fileName != null) {
                // Create file (temporary) and set path to be accessible within test
                File tempDirectory = new File(System.getProperty("user.dir") + "/target/test-classes/");
                String absolutePathFile = tempDirectory.getAbsolutePath() + "/" + fileName;
                commonspec.getLogger().debug("Creating file {} in 'target/test-classes'", absolutePathFile);
                // Note that this Writer will delete the file if it exists
                Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(absolutePathFile), StandardCharsets.UTF_8));
                try {
                    out.write(value);
                } catch (Exception e) {
                    commonspec.getLogger().error("Custom file {} hasn't been created:\n{}", absolutePathFile, e.toString());
                } finally {
                    out.close();
                }

                Assertions.assertThat(new File(absolutePathFile).isFile());
            }
        } else {
            fail("No environment variable neither file defined");
        }
    }

    @Then("^I save service response header '(.*?)' value( in environment variable '(.*?)')?( in file '(.*?)')?$")
    public void saveResponseHeaderValueInEnvironmentVariableFile(String header, String envVar, String fileName) throws Exception {
        try {
            if (envVar != null || fileName != null) {
                String value = commonspec.getResponse().getResponseHeaders().get(header);
                if (envVar != null) {
                    ThreadProperty.set(envVar, value);
                }

                if (fileName != null) {
                    // Create file (temporary) and set path to be accessible within test
                    File tempDirectory = new File(System.getProperty("user.dir") + "/target/test-classes/");
                    String absolutePathFile = tempDirectory.getAbsolutePath() + "/" + fileName;
                    commonspec.getLogger().debug("Creating file {} in 'target/test-classes'", absolutePathFile);
                    // Note that this Writer will delete the file if it exists
                    Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(absolutePathFile), StandardCharsets.UTF_8));
                    try {
                        out.write(value);
                    } catch (Exception e) {
                        commonspec.getLogger().error("Custom file {} hasn't been created:\n{}", absolutePathFile, e.toString());
                    } finally {
                        out.close();
                    }

                    Assertions.assertThat(new File(absolutePathFile).isFile());
                }
            } else {
                fail("No environment variable neither file defined");
            }
        } catch (Exception e) {
            fail("Header {} was not found on {} response", header, envVar);
        }
    }

    /**
     * Parses the SSE (Server-Sent Events) response and saves the events in a specified variable.
     *
     * @param envVar Variable name where to store the parsed events
     * @return A JSONObject containing the parsed events.
     */
    @Given("^I parse the SSE response events and save them in environment variable '(.*)'$")
    public JSONObject parseSSEEvents(String envVar) {
        List<Map<String, Object>> events = new ArrayList<>();
        Scanner scanner = new Scanner(this.commonspec.getResponse().getResponse());
        String eventType = null;
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.startsWith("event:")) {
                eventType = line.split(": ")[1];
            } else if (line.startsWith("data:") && eventType != null) {
                String eventData = line.split(": ", 2)[1];
                Map<String, Object> event = new HashMap<>();
                event.put("event_type", eventType);
                try {
                    event.put("event_value", new JSONObject(eventData).toMap());
                } catch (Exception e) {
                    event.put("event_value", eventData.replaceAll("^\"|\"$", ""));
                }
                events.add(event);
                eventType = null;
            } else if (eventType != null) { // Empty event
                Map<String, Object> event = new HashMap<>();
                event.put("event_type", eventType);
                event.put("event_value", "_EMPTY_EVENT_");
                events.add(event);
                eventType = null;
            }
        }
        scanner.close();
        Map<String, List<Map<String, Object>>> eventsJson = new HashMap<>();
        eventsJson.put("events", events);
        ThreadProperty.set(envVar, new JSONObject(eventsJson).toString());
        return new JSONObject(eventsJson);
    }

    /**
     * Clear all stored cookies.
     */
    @Given("^I clear the cookies$")
    public void clearStoredCookies() {
        commonspec.clearCookies();
    }

    /**
     * Clear all stored headers.
     */
    @Given("^I clear the headers$")
    public void clearStoredHeaders() {
        commonspec.clearHeaders();
    }

    /**
     * Add a new cookie with a specified name and value.
     * NOTE: If the cookie key already exists, the value will be appended to the existing value, not replaced.
     *
     * @param name  the name of the cookie to be added
     * @param value the value of the cookie to be added
     */
    @Given("^I add a new cookie with name '(.*)' and value '(.*)'$")
    public void addNewCookie(String name, String value) {
        commonspec.addCookie(name, value);
    }

    /**
     * Replaces a cookie with a specified name and value.
     *
     * @param name  the name of the cookie to be replaced
     * @param value the value of the cookie to be set
     */
    @Given("^I replace the cookie with name '(.*)' to value '(.*)'$")
    public void replaceCookie(String name, String value) {
        commonspec.deleteCookie(name);
        commonspec.addCookie(name, value);
    }

    /**
     * Delete a cookie with a specified name.
     *
     * @param name the name of the cookie to be deleted
     */
    @Given("^I delete the cookie with name '(.*)'$")
    public void deleteCookie(String name) {
        commonspec.deleteCookie(name);
    }

    @Given("^I add a new header with name '(.*)' and value '(.*)'$")
    public void addNewHeader(String name, String value) {
        this.commonspec.addHeader(name, value);
    }

    @Given("^I replace the header with name '(.*)' to value '(.*)'$")
    public void replaceHeader(String name, String value) {
        this.commonspec.deleteHeader(name);
        this.commonspec.addHeader(name, value);
    }

    @Given("^I delete the header with name '(.*)'$")
    public void deleteHeader(String name) {
        this.commonspec.deleteHeader(name);
    }
}
