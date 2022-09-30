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

import com.stratio.qa.clients.cct.DeployApiTest;
import com.stratio.qa.specs.CommonG;
import com.stratio.qa.utils.ThreadProperty;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

public abstract class BaseClientTest {

    protected final Logger logger = LoggerFactory
            .getLogger(DeployApiTest.class);

    protected ClientAndServer mockServer;

    protected int port;

    protected CommonG commong;

    protected void startMockServer() throws Exception {
        ConfigurationProperties.logLevel("ERROR");
        port  = PortFactory.findFreePort();
        logger.info("Starting mock server...");
        mockServer = ClientAndServer.startClientAndServer(port);
        logger.info("Mock sever started.");
    }

    protected void setHTTPClient() {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        commong = new CommonG();
        commong.setClient(asyncHttpClient(config().setUseInsecureTrustManager(true).setKeepAlive(false).build()));
    }

    protected abstract <T extends BaseClient> T getClient();

    protected void stopMockServer() {
        logger.info("Stopping mock server...");
        mockServer.stop();
        logger.info("Mock sever stopped.");
    }
}
