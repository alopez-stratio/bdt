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

import org.asynchttpclient.Response;
import com.stratio.qa.clients.BaseClient;
import com.stratio.qa.models.BaseResponse;
import com.stratio.qa.models.BaseResponseList;
import com.stratio.qa.models.cct.configurationApi.CalicoConfiguration;
import com.stratio.qa.specs.CommonG;
import com.stratio.qa.utils.ThreadProperty;

public class ConfigurationApiClient extends BaseClient {

    private static ConfigurationApiClient CLIENT;

    public static ConfigurationApiClient getInstance(CommonG common) {
        if (CLIENT == null || CLIENT.httpClient == null || CLIENT.httpClient.isClosed()) {
            CLIENT = new ConfigurationApiClient(common);
        }
        return CLIENT;
    }

    private ConfigurationApiClient(CommonG common) {
        super(common);
    }

    public BaseResponse getCentralConfiguration() throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat("/service/cct-configuration-api/central");
        Response response = get(url);
        return map(response);
    }

    public BaseResponse getCentralConfiguration(String path) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort())
                .concat("/service/cct-configuration-api/central?path=");
        url = url.concat(path);

        Response response = get(url);
        return map(response);
    }

    public BaseResponse getSchema() throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat(":443/service/cct-configuration-api/central/schema");

        Response response = get(url);
        return map(response);
    }

    public CalicoConfiguration getNetwork(String networkId) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat(":443/service/cct-configuration-api/network/");
        url = url.concat(networkId);

        Response response = get(url);
        return map(response, CalicoConfiguration.class);
    }

    public BaseResponseList<CalicoConfiguration> getAllNetworks() throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat("/service/cct-configuration-api/network/all");

        Response response = get(url);
        return mapList(response, CalicoConfiguration.class);
    }

    public BaseResponse getMesosConfiguration() throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat("/service/cct-configuration-api/mesos");
        Response response = get(url);
        return map(response);
    }

    public BaseResponse getMesosConfiguration(String path) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat("/service/cct-configuration-api/mesos?path=");
        url = url.concat(path);

        Response response = get(url);
        return map(response);
    }

    public CalicoConfiguration createNetwork(String data) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat("/service/cct-configuration-api/network");

        Response response = post(url, data);
        return map(response, CalicoConfiguration.class);
    }

    public CalicoConfiguration updateNetwork(String data) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat("/service/cct-configuration-api/network");

        Response response = put(url, data);
        return map(response, CalicoConfiguration.class);
    }

    public BaseResponse removeNetwork(String networkId) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat("/service/cct-configuration-api/network/");
        url = url.concat(networkId);

        Response response = delete(url);
        return map(response);
    }
}
