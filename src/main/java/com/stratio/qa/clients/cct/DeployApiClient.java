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
import com.stratio.qa.models.cct.deployApi.DeployedApp;
import com.stratio.qa.models.cct.deployApi.SandboxItem;
import com.stratio.qa.models.cct.deployApi.ServiceStatusModel;
import com.stratio.qa.models.cct.deployApi.TearDownResponse;
import com.stratio.qa.specs.CommonG;
import com.stratio.qa.utils.ThreadProperty;

public class DeployApiClient extends BaseClient {

    private static DeployApiClient CLIENT;

    public static DeployApiClient getInstance(CommonG common) {
        if (CLIENT == null || CLIENT.httpClient == null || CLIENT.httpClient.isClosed()) {
            CLIENT = new DeployApiClient(common);
        }
        return CLIENT;
    }

    private DeployApiClient(CommonG common) {
        super(common);
    }

    public BaseResponseList<DeployedApp> getDeployedApps() throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort())
                .concat("/service/").concat(ThreadProperty.get("deploy_api_id")).concat("/deployments");

        Response response = get(url);
        return mapList(response, DeployedApp.class);
    }

    public DeployedApp getDeployedApp(String appId) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort())
                .concat("/service/").concat(ThreadProperty.get("deploy_api_id")).concat("/deployments/service?instanceName=");
        url = url.concat(appId);

        Response response = get(url);
        return map(response, DeployedApp.class);
    }

    public BaseResponseList<SandboxItem> getLogPaths(String taskId) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort())
                .concat("/service/").concat(ThreadProperty.get("deploy_api_id")).concat("/deployments/logs/");
        url = url.concat(taskId);

        Response response = get(url);
        return mapList(response, SandboxItem.class);
    }

    public TearDownResponse tearDown(String serviceId) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort())
                .concat("/service/").concat(ThreadProperty.get("deploy_api_id")).concat("/deploy/teardown?frameworkName=");
        url =  url.concat(serviceId);

        Response response = delete(url);
        return map(response, TearDownResponse.class);
    }

    public BaseResponseList<ServiceStatusModel> getDeployedServices() throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort())
                .concat("/service/").concat(ThreadProperty.get("deploy_api_id")).concat("/deploy/status/all");

        Response response = delete(url);
        return mapList(response, ServiceStatusModel.class);
    }

    public BaseResponse scale(int instances, String service) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort())
                .concat("/service/").concat(ThreadProperty.get("deploy_api_id")).concat("/deploy/scale?");
        url = url.concat("instances=").concat(Integer.toString(instances)).concat("&serviceName=").concat(service);

        Response response = put(url);
        return map(response);
    }
}
