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

package com.stratio.qa.clients.marathon;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.stratio.qa.clients.BaseClient;
import com.stratio.qa.models.marathon.*;
import com.stratio.qa.specs.CommonG;
import com.stratio.qa.utils.ThreadProperty;
import org.asynchttpclient.Response;

public class MarathonApiClient extends BaseClient {

    private static MarathonApiClient CLIENT;

    public static MarathonApiClient getInstance(CommonG common) {
        if (CLIENT == null || CLIENT.httpClient == null || CLIENT.httpClient.isClosed()) {
            CLIENT = new MarathonApiClient(common);
        }
        return CLIENT;
    }

    private MarathonApiClient(CommonG common) {
        super(common);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Volume.class, new Volume.VolumeDeserializer());
        mapper.registerModule(module);
    }

    public AppsResponse getApps() throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT")).concat(":" + getPort()).concat("/marathon/v2/apps");

        Response response = get(url);
        return map(response, AppsResponse.class);
    }

    public VersionedAppResponse getApp(String appId) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT")).concat(":" + getPort()).concat("/marathon/v2/apps/");
        url = url.concat(appId);

        Response response = get(url);
        return map(response, VersionedAppResponse.class);
    }

    public AppResponse addApp(String descriptor) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT")).concat(":" + getPort()).concat("/marathon/v2/apps");

        Response response = post(url, descriptor);
        return map(response, AppResponse.class);
    }

    public DeploymentResult updateApp(String appId, App app, boolean force) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT")).concat(":" + getPort()).concat("/marathon/v2/apps/");
        url = url.concat(appId);
        url = url.concat("?force=" + force);
        String data = mapper.writeValueAsString(app);

        Response response = put(url, data);
        return map(response, DeploymentResult.class);
    }

    public DeploymentResult updateAppFromString(String appId, String data, boolean force) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT")).concat(":" + getPort()).concat("/marathon/v2/apps/");
        url = url.concat(appId);
        url = url.concat("?force=" + force);

        Response response = put(url, data);
        return map(response, DeploymentResult.class);
    }

    public DeploymentResult removeApp(String appId, boolean force) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT")).concat(":" + getPort()).concat("/marathon/v2/apps/");
        url = url.concat(appId);
        url = url.concat("?force=" + force);

        Response response = delete(url);
        return map(response, DeploymentResult.class);
    }

    public DeploymentResult restartApp(String appId, boolean force) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT")).concat(":" + getPort()).concat("/marathon/v2/apps/");
        url = url.concat(appId).concat("/restart");
        String data = "{\"force\": " + force + "}";

        Response response = post(url, data);
        return map(response, DeploymentResult.class);
    }

}
