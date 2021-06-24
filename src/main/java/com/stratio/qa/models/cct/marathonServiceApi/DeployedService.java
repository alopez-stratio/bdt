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

package com.stratio.qa.models.cct.marathonServiceApi;

import com.stratio.qa.models.BaseResponse;
import com.stratio.qa.models.marathon.Network;

import java.util.List;
import java.util.Map;

public class DeployedService extends BaseResponse {

    private String id;

    private String tenant;

    private String service;

    private String model;

    private String version;

    private String release;

    private String serviceLabel;

    private Resources resources;

    private ServiceStatus status;

    private Healthiness healthiness;

    private List<DeployedServiceTask> tasks;

    private DeployedServiceExposition exposition;

    private Integer instances;

    private Integer totalTasks;

    private Integer totalHealthyTasks;

    private List<Network> networks;

    private List<External> external;

    private Map<String, Object> env;

    private Map<String, Object> labels;

    public String getId() {
        return this.id;
    }

    public String getTenant() {
        return tenant;
    }

    public String getRelease() {
        return release;
    }

    public String getServiceLabel() {
        return serviceLabel;
    }

    public Resources getResources() {
        return resources;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public Healthiness getHealthiness() {
        return healthiness;
    }

    public List<DeployedServiceTask> getTasks() {
        return tasks;
    }

    public DeployedServiceExposition getExposition() {
        return exposition;
    }

    public Integer getInstances() {
        return instances;
    }

    public Integer getTotalTasks() {
        return totalTasks;
    }

    public Integer getTotalHealthyTasks() {
        return totalHealthyTasks;
    }

    public List<Network> getNetworks() {
        return networks;
    }

    public List<External> getExternal() {
        return external;
    }

    public Map<String, Object> getEnv() {
        return env;
    }

    public Map<String, Object> getLabels() {
        return labels;
    }

    public String getService() {
        return this.service;
    }

    public String getModel() {
        return this.model;
    }

    public String getVersion() {
        return this.version;
    }
}
