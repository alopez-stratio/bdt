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

import com.stratio.qa.clients.BaseClient;
import com.stratio.qa.models.cct.marathonServiceApi.DeployedService;
import com.stratio.qa.models.cct.marathonServiceApi.DeployedServicesResponse;
import com.stratio.qa.models.cct.marathonServiceApi.TaskLogsResponse;
import com.stratio.qa.specs.CommonG;
import com.stratio.qa.utils.ThreadProperty;
import org.asynchttpclient.Response;

import java.util.HashMap;
import java.util.Map;

public class CctMarathonServiceApiClient extends BaseClient {

    private static CctMarathonServiceApiClient CLIENT;

    public static CctMarathonServiceApiClient getInstance(CommonG common) {
        if (CLIENT == null || CLIENT.httpClient == null || CLIENT.httpClient.isClosed()) {
            CLIENT = new CctMarathonServiceApiClient(common);
        }
        return CLIENT;
    }

    private CctMarathonServiceApiClient(CommonG common) {
        super(common);
    }

    public DeployedService getService(String serviceId) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat("/service/")
                .concat(ThreadProperty.get("cct-marathon-services_id")).concat("/v1/services");
        url = url.concat(serviceId);

        Response response = get(url);
        return map(response, DeployedService.class);
    }

    public DeployedService getService(String serviceId, int tpage, int tsize) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat("/service/")
                .concat(ThreadProperty.get("cct-marathon-services_id")).concat("/v1/services/");

        url = url.concat(serviceId);

        Map<String, String> queryParams = new HashMap<String, String>() { {
                put("tpage", Integer.toString(tpage));
                put("tsize", Integer.toString(tsize));
            } };

        Response response = get(url, queryParams);
        return map(response, DeployedService.class);
    }

    public TaskLogsResponse getLogPaths(String taskId) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat("/service/")
                .concat(ThreadProperty.get("cct-marathon-services_id")).concat("/v1/services/tasks/" + taskId + "/logs");

        Response response = get(url);
        return map(response, TaskLogsResponse.class);
    }

    public DeployedServicesResponse getDeployedServices(String tenant) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT"))
                .concat(":" + getPort()).concat("/service/")
                .concat(ThreadProperty.get("cct-marathon-services_id")).concat("/v1/services?tenant=");
        url = url.concat(tenant);

        Response response = get(url);
        return map(response, DeployedServicesResponse.class);
    }

}
