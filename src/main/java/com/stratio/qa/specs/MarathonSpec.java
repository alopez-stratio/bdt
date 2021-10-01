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

import com.stratio.qa.models.marathon.*;
import com.stratio.qa.utils.ThreadProperty;

import static org.assertj.core.api.Assertions.assertThat;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.cucumber.datatable.DataTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class MarathonSpec extends BaseGSpec {

    private final Logger logger = LoggerFactory.getLogger(CCTSpec.class);

    public MarathonSpec(CommonG spec) {
        this.commonspec = spec;
    }

    @Then("^service with id '(.*)' has '(\\d+)' task[s]? in '(running|finished|failed|staging|starting|killed)' state in Marathon$")
    public void checkNumberOfTasksState(String appId, int numberOfTasks, String state) throws Exception {
        VersionedAppResponse app = this.commonspec.marathonClient.getApp(appId);
        Collection<Task> tasks = app.getApp().getTasks();

        String translatedState = MarathonConstants.statesDict.get(state);
        int count = (int) tasks.stream().filter(task -> task.getState().equals(translatedState)).count();

        assertThat(count)
                .as("Number of task in state " + translatedState + " for service " + appId + " does not match.")
                .isEqualTo(numberOfTasks);
    }

    @Then("^service with id '(.*)' has all tasks in '(running|finished|failed|staging|starting|killed)' state in Marathon$")
    public void checkAllTasksState(String appId, String state) throws Exception {
        VersionedAppResponse app = this.commonspec.marathonClient.getApp(appId);
        Collection<Task> tasks = app.getApp().getTasks();

        String translatedState = MarathonConstants.statesDict.get(state);
        int count = (int) tasks.stream().filter(task -> task.getState().equals(translatedState)).count();

        assertThat(count)
                .as("Number of task in state " + translatedState + " for service " + appId + " does not match.")
                .isEqualTo(tasks.size());
    }

    @Then("^I get environment variable '(.*)' for service with id '(.*)' and save the value in environment variable '(.+?)'$")
    public void saveServiceEnvVariable(String serviceEnvVar, String serviceId, String envVar) throws Exception {
        VersionedAppResponse app = this.commonspec.marathonClient.getApp(serviceId);
        assertThat(app.getApp().getEnv().get(serviceEnvVar))
                .as("Environment variable " + serviceEnvVar + " not found for service " + serviceId)
                .isNotNull();

        String value = app.getApp().getEnv().get(serviceEnvVar).toString();
        ThreadProperty.set(envVar, value);
    }

    @Then("^I get label '(.*)' for service with id '(.*)' and save the value in environment variable '(.+?)'$")
    public void saveServiceLabel(String label, String serviceId, String envVar) throws Exception {
        VersionedAppResponse app = this.commonspec.marathonClient.getApp(serviceId);
        assertThat(app.getApp().getLabels().get(label))
                .as("Label " + label + " not found for service " + serviceId)
                .isNotNull();

        String value = app.getApp().getLabels().get(label);
        ThreadProperty.set(envVar, value);
    }

    @Then("^in less than '(\\d+)' seconds, checking each '(\\d+)' seconds, service with id '(.*)' has '(\\d+)' task[s]? in '(running|finished|failed|staging|starting|killed)' state in Marathon$")
    public void checkNumberOfTasksStateWithPolling(int timeout, int pause, String appId, int numberOfTasks, String state) throws Exception {
        VersionedAppResponse app;
        Collection<Task> tasks;
        int count;
        String translatedState = MarathonConstants.statesDict.get(state);

        int time = 0;
        while (time < timeout) {
            app = this.commonspec.marathonClient.getApp(appId);
            if (app.getApp() != null) {
                tasks = app.getApp().getTasks();

                count = (int) tasks.stream().filter(task -> task.getState().equals(translatedState)).count();
                if (count == numberOfTasks) {
                    return;
                }
            }

            Thread.sleep(pause * 1000);
            time += pause;
        }

        assertThat(false)
                .as("Number of task in state " + translatedState + " for service " + appId + " does not match after " + timeout + " seconds.")
                .isTrue();
    }

    @Then("^in less than '(\\d+)' seconds, checking each '(\\d+)' seconds, service with id '(.*)' has all tasks in '(running|finished|failed|staging|starting|killed)' state in Marathon$")
    public void checkAllTasksStateWithPolling(int timeout, int pause, String appId, String state) throws Exception {
        VersionedAppResponse app;
        Collection<Task> tasks;

        String translatedState = MarathonConstants.statesDict.get(state);
        int count;

        int time = 0;
        while (time < timeout) {
            app = this.commonspec.marathonClient.getApp(appId);
            if (app.getApp() != null) {
                tasks = app.getApp().getTasks();

                count = (int) tasks.stream().filter(task -> task.getState().equals(translatedState)).count();
                if (count == tasks.size()) {
                    return;
                }
            }

            Thread.sleep(pause * 1000);
            time += pause;
        }

        assertThat(false)
                .as("Number of task in state " + translatedState + " for service " + appId + " does not match after " + timeout + " seconds.")
                .isTrue();
    }

    @Then("^in less than '(\\d+)' seconds, checking each '(\\d+)' seconds, service with id '(.*)' has '(\\d+)' '(healthy|unhealthy|staged|running)' task[s]? in Marathon$")
    public void checkNumberOfTasksHealthinessWithPolling(int timeout, int pause, String appId, int numberOfTasks, String state) throws Exception {
        VersionedAppResponse app;
        int count = 0;

        int time = 0;
        while (time < timeout) {
            app = this.commonspec.marathonClient.getApp(appId);

            if (app.getApp() != null) {
                switch (state) {
                    case "healthy":
                        count = app.getApp().getTasksHealthy();
                        break;
                    case "unhealthy":
                        count = app.getApp().getTasksUnhealthy();
                        break;
                    case "staged":
                        count = app.getApp().getTasksStaged();
                        break;
                    case "running":
                        count = app.getApp().getTasksRunning();
                        break;
                    default:
                        count = 0;
                }

                if (count == numberOfTasks) {
                    return;
                }
            }

            Thread.sleep(pause * 1000);
            time += pause;
        }

        assertThat(false)
                .as("Number of task(s) " + state + " for service " + appId + " does not match after " + timeout + " seconds.")
                .isTrue();
    }

    @Then("^in less than '(\\d+)' seconds, checking each '(\\d+)' seconds, service with id '(.*)' has all tasks '(healthy|unhealthy|unknown)' in Marathon$")
    public void checkAllTasksHealthinessWithPolling(int timeout, int pause, String appId, String state) throws Exception {
        VersionedAppResponse app;
        int count = 0;

        int time = 0;
        while (time < timeout) {
            app = this.commonspec.marathonClient.getApp(appId);
            if (app.getApp() != null) {
                switch (state) {
                    case "healthy":
                        count = app.getApp().getTasksHealthy();
                        break;
                    case "unhealthy":
                        count = app.getApp().getTasksUnhealthy();
                        break;
                    case "staged":
                        count = app.getApp().getTasksStaged();
                        break;
                    case "running":
                        count = app.getApp().getTasksRunning();
                        break;
                    default:
                        count = 0;
                }

                if (count == app.getApp().getTasks().size()) {
                    return;
                }
            }

            Thread.sleep(pause * 1000);
            time += pause;
        }

        assertThat(false)
                .as("Number of task(s) " + state + " for service " + appId + " does not match after " + timeout + " seconds.")
                .isTrue();
    }

    @When("^I get taskId for task '(.+?)' in service with id '(.+?)' from Marathon and save the value in environment variable '(.+?)'$")
    public void getTaskId(String taskName, String serviceId, String envVar) throws Exception {
        String taskId = this.commonspec.marathonUtils.getTaskId(taskName, serviceId);
        ThreadProperty.set(envVar, taskId);
    }

    @Then("^I get (service|container) port in position '(.*)' for service with id '(.*)' and save the value in environment variable '(.+?)'$")
    public void getServicePort(String serviceOrContainer, String position, String serviceId, String envVar) throws Exception {
        VersionedAppResponse app = this.commonspec.marathonClient.getApp(serviceId);
        Port portAux = app.getApp().getContainer().getPortMappings() != null ?
                new ArrayList<>(app.getApp().getContainer().getPortMappings()).get(Integer.parseInt(position)) :
                new ArrayList<>(app.getApp().getContainer().getDocker().getPortMappings()).get(Integer.parseInt(position));
        switch (serviceOrContainer) {
            case "service":     ThreadProperty.set(envVar, String.valueOf(portAux.getServicePort()));
                                break;
            case "container":   ThreadProperty.set(envVar, String.valueOf(portAux.getContainerPort()));
                                break;
            default:        throw new Exception("First param must be service or container");
        }
    }

    @When("^I get (internal )?host ip for task '(.+?)'( in position '(\\d+)')? in service with id '(.+?)' from Marathon and save the value in environment variable '(.+?)'$")
    public void getHostIp(String internalIP, String taskName, Integer position, String serviceId, String envVar) throws Exception {
        // Set REST connection
        commonspec.setCCTConnection(null, null);
        position = position != null ? position : 0;
        String ip = getHostIPFromMarathon(internalIP != null, serviceId, position);
        Assert.assertNotNull(ip, "Error obtaining IP");
        ThreadProperty.set(envVar, ip);
    }

    @When("^I get Marathon descriptor for service id '(.+?)'( and save the value in environment variable '(.+?)')?( and save it in file '(.*?)')?$")
    public void getServiceDescriptor(String appId, String envVar, String fileName) throws Exception {
        // Set REST connection
        commonspec.setCCTConnection(null, null);
        VersionedAppResponse app = this.commonspec.marathonClient.getApp(appId);
        assertThat(app.getHttpStatus()).as("Error obtaining marathon app descriptor: " + app.getHttpStatus()).isEqualTo(200);
        if (envVar != null) {
            ThreadProperty.set(envVar, app.getRawResponse());
        }
        if (fileName != null) {
            writeInFile(app.getRawResponse(), fileName);
        }
    }


    @When("^I stop Marathon service with id '(.+?)'$")
    public void stopService(String appId) throws Exception {
        // Set REST connection
        commonspec.setCCTConnection(null, null);

        //Check service exists and is running
        VersionedAppResponse app = this.commonspec.marathonClient.getApp(appId);
        assertThat(app.getHttpStatus()).as("No marathon app found by id: " + appId).isEqualTo(200);
        assertThat(app.getApp().getInstances()).as("Marathon app: " + appId + " already stopped").isGreaterThan(0);

        //Stop service
        App a = new App();
        a.setInstances(0);
        DeploymentResult response = this.commonspec.marathonClient.updateApp(appId, a, true);
        assertThat(response.getHttpStatus()).as("Error updating Marathon app: " + response.getHttpStatus()).isEqualTo(200);
    }

    @When("^I start Marathon service with id '(.+?)'( with '(.+?)' instances)?$")
    public void startService(String appId, String instances) throws Exception {
        // Set REST connection
        commonspec.setCCTConnection(null, null);

        //Check service exists and is stopped
        VersionedAppResponse app = this.commonspec.marathonClient.getApp(appId);
        assertThat(app.getHttpStatus()).as("No marathon app found by id: " + appId).isEqualTo(200);
        assertThat(app.getApp().getInstances()).as("Marathon app: " + appId + " already stopped").isEqualTo(0);

        int inst = instances == null ? 1 : Integer.parseInt(instances);

        //Start service
        App a = new App();
        a.setId(appId);
        a.setInstances(inst);
        DeploymentResult response = this.commonspec.marathonClient.updateApp(appId, a, true);
        assertThat(response.getHttpStatus()).as("Error starting Marathon app: " + response.getHttpStatus()).isEqualTo(200);
    }


    @When("^I update Marathon fetch section of service with id '(.+?)' with uri '(.+?)'$")
    public void updateAppFetch(String appId, String uri) throws Exception {
        // Set REST connection
        commonspec.setCCTConnection(null, null);

        // Check service exists and is running
        VersionedAppResponse app = this.commonspec.marathonClient.getApp(appId);
        assertThat(app.getHttpStatus()).as("No marathon app found by id: " + appId).isEqualTo(200);

        List fetchSection = app.getApp().getFetch() == null ? new ArrayList() : app.getApp().getFetch();

        Fetchable fetch = new Fetchable();
        fetch.setUri(uri);
        fetch.setCache(false);
        fetch.setExtract(true);
        fetch.setExecutable(false);
        fetch.setDestPath(null);

        fetchSection.add(fetch);

        //Update service
        App a = new App();
        a.setFetch(fetchSection);
        DeploymentResult response = this.commonspec.marathonClient.updateApp(appId, a, true);
        assertThat(response.getHttpStatus()).as("Error updating Marathon app: " + response.getHttpStatus()).isEqualTo(200);
    }


    @When("^I update Marathon service with id '(.+?)' with environment variables:$")
    public void updateAppEnvs(String appId, DataTable modifications) throws Exception {
        // Set REST connection
        commonspec.setCCTConnection(null, null);

        List<List<String>> datatable = modifications.asLists(String.class);

        //Check service exists and is running
        VersionedAppResponse app = this.commonspec.marathonClient.getApp(appId);
        assertThat(app.getHttpStatus()).as("No marathon app found by id: " + appId).isEqualTo(200);

        //Modify envs
        Map<String, Object> envs = app.getApp().getEnv();
        datatable.forEach(entry -> {
                String name = entry.get(0);
                String action = entry.get(1);
                String value = entry.get(2);
                switch (action) {
                    case "ADD": envs.put(name, value);
                        break;
                    case "DELETE": envs.remove(name);
                        break;
                    case "REPLACE": envs.replace(name, value);
                        break;
                    default: break;
                }
            }
        );

        //Update service
        App a = new App();
        a.setEnv(envs);
        DeploymentResult response = this.commonspec.marathonClient.updateApp(appId, a, true);
        assertThat(response.getHttpStatus()).as("Error updating Marathon app: " + response.getHttpStatus()).isEqualTo(200);
    }

    @When("^I update Marathon service with id '(.+?)' with parameters defined at '(.+?)' variable$")
    public void updateAppFromVar(String appId, String envVar) throws Exception {
        commonspec.setCCTConnection(null, null);

        //Check service exists and is running
        VersionedAppResponse app = this.commonspec.marathonClient.getApp(appId);
        assertThat(app.getHttpStatus()).as("No marathon app found by id: " + appId).isEqualTo(200);

        String data = ThreadProperty.get(envVar);
        DeploymentResult response = this.commonspec.marathonClient.updateAppFromString(appId, data, true);
        assertThat(response.getHttpStatus()).as("Error updating Marathon app: " + response.getHttpStatus()).isEqualTo(200);
    }

    @When("^I add new Marathon service based on file located at '(.+?)'$")
    public void newAppFromFile(String descriptorPath) throws Exception {
        commonspec.setCCTConnection(null, null);

        String descriptor = new String(Files.readAllBytes(Paths.get(descriptorPath)));
        AppResponse response = this.commonspec.marathonClient.addApp(descriptor);

        assertThat(response.getHttpStatus()).as("Error deploying new app in Marathon: " + response.getHttpStatus()).isEqualTo(201);
    }

    @When("^I add new Marathon service based on variable '(.+?)'$")
    public void newAppFromVar(String envVar) throws Exception {
        commonspec.setCCTConnection(null, null);

        String descriptor = ThreadProperty.get(envVar);
        AppResponse response = this.commonspec.marathonClient.addApp(descriptor);

        assertThat(response.getHttpStatus()).as("Error deploying new app in Marathon: " + response.getHttpStatus()).isEqualTo(201);
    }

    @When("^I remove Marathon service with id '(.+?)'( if it exists)?$")
    public void removeApp(String appId, String force) throws Exception {
        commonspec.setCCTConnection(null, null);

        DeploymentResult result = this.commonspec.marathonClient.removeApp(appId, true);
        if (force == null) {
            assertThat(result.getHttpStatus()).as("Error removing app in Marathon: " + result.getHttpStatus()).isEqualTo(200);
        }
    }

    @When("^I restart Marathon service with id '(.+?)'$")
    public void restartApp(String appId) throws Exception {
        commonspec.setCCTConnection(null, null);

        DeploymentResult result = this.commonspec.marathonClient.restartApp(appId, true);
        assertThat(result.getHttpStatus()).as("Error restarting app in Marathon: " + result.getHttpStatus()).isEqualTo(200);
    }

    private String getHostIPFromMarathon(boolean internalIp, String serviceId, int position) throws Exception {
        VersionedAppResponse app = this.commonspec.marathonClient.getApp(serviceId);
        Collection<Task> tasks = app.getApp().getTasks();
        Task task = tasks.stream()
                .filter(t -> t.getAppId().equals(serviceId))
                .filter(t -> t.getState().equals("TASK_RUNNING"))
                .skip(position)
                .findFirst()
                .orElse(null);
        return task != null ? internalIp ? task.getIpAddresses().stream().findFirst().get().getIpAddress() : task.getHost() : null;
    }

}
