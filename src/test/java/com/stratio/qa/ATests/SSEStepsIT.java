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
package com.stratio.qa.ATests;

import com.stratio.qa.cucumber.testng.CucumberFeatureWrapper;
import com.stratio.qa.cucumber.testng.PickleEventWrapper;
import com.stratio.qa.utils.BaseGTest;
import cucumber.api.CucumberOptions;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@CucumberOptions(plugin = "json:target/cucumber-sse-steps.json",
        features = {"src/test/resources/features/sse.feature"})
public class SSEStepsIT extends BaseGTest {

    private static ClientAndServer mockServer;

    @BeforeClass
    public static void startMockServer() {
        ConfigurationProperties.logLevel("ERROR");
        int port = PortFactory.findFreePort();
        mockServer = ClientAndServer.startClientAndServer(port);
        System.setProperty("SSE_MOCK_PORT", String.valueOf(port));

        new org.mockserver.client.MockServerClient("localhost", port)
                .when(request().withPath("/stream"))
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "text/event-stream; charset=utf-8")
                        .withBody("event: token\ndata: {\"token\":\"Hello\"}\n\nevent: end\ndata: {\"end\":true}\n\n"));
    }

    @AfterClass
    public static void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Test(dataProvider = "scenarios")
    public void run(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
        runScenario(pickleWrapper, featureWrapper);
    }
}
