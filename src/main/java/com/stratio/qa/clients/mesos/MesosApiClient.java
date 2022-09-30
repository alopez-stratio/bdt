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

package com.stratio.qa.clients.mesos;

import com.stratio.qa.clients.BaseClient;
import com.stratio.qa.models.mesos.Log;
import com.stratio.qa.models.mesos.MesosStateSummary;
import com.stratio.qa.models.mesos.MesosTasksResponse;
import com.stratio.qa.specs.CommonG;
import com.stratio.qa.utils.ThreadProperty;
import org.asynchttpclient.Response;

import java.util.Map;

public class MesosApiClient extends BaseClient {

    private static MesosApiClient CLIENT;

    public static MesosApiClient getInstance(CommonG common) {
        if (CLIENT == null || CLIENT.httpClient == null || CLIENT.httpClient.isClosed()) {
            CLIENT = new MesosApiClient(common);
        }
        return CLIENT;
    }

    private MesosApiClient(CommonG common) {
        super(common);
    }

    public Log getLogs(String path, String logType, Map<String, String> queryParams) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT")).concat(":" + getPort());
        String endpoint = url.concat(path).concat(logType);

        if (queryParams != null) {
            for (Map.Entry<String, String> param: queryParams.entrySet()) {
                endpoint = endpoint.concat("&").concat(param.getKey()).concat("=").concat(param.getValue());
            }
        }

        Response response = get(endpoint);
        return map(response, Log.class);
    }

    public MesosStateSummary getStateSummary() throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT")).concat(":" + getPort()).concat("/mesos/state-summary");

        Response response = get(url);
        return map(response, MesosStateSummary.class);
    }

    public MesosTasksResponse getMesosTask(String taskId) throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT")).concat(":" + getPort()).concat("/mesos/tasks?task_id=");
        url = url.concat(taskId);

        Response response = get(url);
        return map(response, MesosTasksResponse.class);
    }

    public MesosTasksResponse getMesosTasks() throws Exception {
        String url = "https://".concat(ThreadProperty.get("EOS_ACCESS_POINT")).concat(":" + getPort()).concat("/mesos/tasks");
        Response response = get(url);
        return map(response, MesosTasksResponse.class);
    }
}
