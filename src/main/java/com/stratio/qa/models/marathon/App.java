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

package com.stratio.qa.models.marathon;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class App {

    public static class Deployment {
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    private String id;

    private String cmd;

    private List<String> args;

    private String user;

    private Integer instances;

    private Double cpus;

    private Double mem;

    private Double disk;

    private Double gpus;

    private Collection<String> uris;

    private List<List<String>> constraints;

    private Collection<String> acceptedResourceRoles;

    private Container container;

    private Map<String, Object> env;

    private Map<String, String> labels;

    private IpAddress ipAddress;

    private Residency residency;

    private Integer taskKillGracePeriodSeconds;

    private Map<String, SecretSource> secrets;

    private String executor;

    private List<Fetchable> fetch;

    private List<String> storeUrls;

    private List<Integer> ports;

    private List<PortDefinition> portDefinitions;

    private Boolean requirePorts;

    private Collection<String> dependencies;

    private Integer backoffSeconds;

    private Double backoffFactor;

    private Integer maxLaunchDelaySeconds;

    private Collection<Task> tasks;

    private AppVersionInfo versionInfo;

    private Integer tasksStaged;

    private Integer tasksRunning;

    private Integer tasksHealthy;

    private Integer tasksUnhealthy;

    private List<HealthCheck> healthChecks;

    private List<ReadinessCheck> readinessChecks;

    private UpgradeStrategy upgradeStrategy;

    private List<Network> networks;

    private List<Deployment> deployments;

    private TaskFailure lastTaskFailure;

    private String killSelection;

    @JsonIgnore
    private Map<String, Integer> unreachableStrategy;

    public String getId() {
        return id;
    }

    public String getCmd() {
        return cmd;
    }

    public List<String> getArgs() {
        return args;
    }

    public String getUser() {
        return user;
    }

    public Integer getInstances() {
        return instances;
    }

    public Double getCpus() {
        return cpus;
    }

    public Double getMem() {
        return mem;
    }

    public Double getDisk() {
        return disk;
    }

    public Double getGpus() {
        return gpus;
    }

    public Collection<String> getUris() {
        return uris;
    }

    public List<List<String>> getConstraints() {
        return constraints;
    }

    public Collection<String> getAcceptedResourceRoles() {
        return acceptedResourceRoles;
    }

    public Container getContainer() {
        return container;
    }

    public Map<String, Object> getEnv() {
        return env;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public IpAddress getIpAddress() {
        return ipAddress;
    }

    public Residency getResidency() {
        return residency;
    }

    public Integer getTaskKillGracePeriodSeconds() {
        return taskKillGracePeriodSeconds;
    }

    public Map<String, SecretSource> getSecrets() {
        return secrets;
    }

    public String getExecutor() {
        return executor;
    }

    public List<Fetchable> getFetch() {
        return fetch;
    }

    public List<String> getStoreUrls() {
        return storeUrls;
    }

    public List<Integer> getPorts() {
        return ports;
    }

    public List<PortDefinition> getPortDefinitions() {
        return portDefinitions;
    }

    public Boolean getRequirePorts() {
        return requirePorts;
    }

    public Collection<String> getDependencies() {
        return dependencies;
    }

    public Integer getBackoffSeconds() {
        return backoffSeconds;
    }

    public Double getBackoffFactor() {
        return backoffFactor;
    }

    public Integer getMaxLaunchDelaySeconds() {
        return maxLaunchDelaySeconds;
    }

    public Collection<Task> getTasks() {
        return tasks;
    }

    public AppVersionInfo getVersionInfo() {
        return versionInfo;
    }

    public Integer getTasksStaged() {
        return tasksStaged;
    }

    public Integer getTasksRunning() {
        return tasksRunning;
    }

    public Integer getTasksHealthy() {
        return tasksHealthy;
    }

    public Integer getTasksUnhealthy() {
        return tasksUnhealthy;
    }

    public List<HealthCheck> getHealthChecks() {
        return healthChecks;
    }

    public List<ReadinessCheck> getReadinessChecks() {
        return readinessChecks;
    }

    public UpgradeStrategy getUpgradeStrategy() {
        return upgradeStrategy;
    }

    public List<Network> getNetworks() {
        return networks;
    }

    public List<Deployment> getDeployments() {
        return deployments;
    }

    public TaskFailure getLastTaskFailure() {
        return lastTaskFailure;
    }

    public String getKillSelection() {
        return killSelection;
    }

    public Map<String, Integer> getUnreachableStrategy() {
        return unreachableStrategy;
    }

    public void setInstances(int instances) {
        this.instances = instances;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setEnv(Map<String, Object> env) {
        this.env = env;
    }

    public void setFetch(List<Fetchable> json) {
        this.fetch = json;
    }

}
