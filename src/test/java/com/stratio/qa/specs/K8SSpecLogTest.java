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

import com.stratio.qa.clients.k8s.K8sFixtures;
import com.stratio.qa.clients.k8s.K8sMockServerTestBase;
import com.stratio.qa.utils.ThreadProperty;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the log-oriented steps of {@link K8SSpec}. Pod logs are a subresource the CRUD dispatcher does
 * not implement, so the mixed dispatcher is used: the {@code /log} endpoints are scripted while the rest
 * of the API still goes through the in-memory store.
 */
public class K8SSpecLogTest extends K8sMockServerTestBase {

    private K8SSpec spec;

    @Override
    protected boolean crudMode() {
        return false;
    }

    @BeforeClass(alwaysRun = true, dependsOnMethods = "startMockServer")
    public void buildSpec() {
        ThreadProperty.set("class", getClass().getCanonicalName());
        CommonG commonG = new CommonG();
        commonG.initKubernetesClient();
        spec = new K8SSpec(commonG);
    }

    @Override
    protected void seed() {
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "logger");

        mockClient.namespaces().resource(K8sFixtures.namespace(NAMESPACE)).create();
        mockClient.pods().inNamespace(NAMESPACE).resource(K8sFixtures.pod("logger-pod", NAMESPACE, labels)).create();
        mockClient.pods().inNamespace(NAMESPACE)
                .resource(K8sFixtures.pod("deploy-logger-pod", NAMESPACE, Collections.singletonMap("app", "deploylogger"))).create();
        mockClient.apps().deployments().inNamespace(NAMESPACE)
                .resource(K8sFixtures.deployment("restart-deploy", NAMESPACE, "img:1", "K", "V")).create();
        mockClient.apps().statefulSets().inNamespace(NAMESPACE)
                .resource(K8sFixtures.statefulSet("restart-sts", NAMESPACE, Collections.emptyMap())).create();
    }

    private void expectLog(String pod, String body) {
        server.expect().get()
                .withPath("/api/v1/namespaces/" + NAMESPACE + "/pods/" + pod + "/log?pretty=false")
                .andReturn(200, body)
                .always();
    }

    private void expectTailedLog(String pod, int tailLines, String body) {
        server.expect().get()
                .withPath("/api/v1/namespaces/" + NAMESPACE + "/pods/" + pod + "/log?pretty=false&tailLines=" + tailLines)
                .andReturn(200, body)
                .always();
    }

    private String readTargetFile(String fileName) throws Exception {
        return new String(Files.readAllBytes(
                Paths.get(System.getProperty("user.dir"), "target", "test-classes", fileName)), StandardCharsets.UTF_8);
    }

    @Test
    public void getLogPodSavesTheLogInAVariableAndInAFile() throws Exception {
        expectLog("logger-pod", "line one\nline two");

        spec.getLogPod("logger-pod", NAMESPACE, "POD_LOG", "logger-pod.log");

        assertThat(ThreadProperty.get("POD_LOG")).isEqualTo("line one\nline two");
        assertThat(readTargetFile("logger-pod.log")).isEqualTo("line one\nline two");
    }

    @Test
    public void readLogsInLessEachFromPodFindsTheExpectedFragment() throws Exception {
        expectLog("wait-ok", "starting\nservice is up");

        spec.readLogsInLessEachFromPod(1, 1, "wait-ok", NAMESPACE, "service is up");
    }

    @Test
    public void readLogsInLessEachFromPodFailsWhenTheFragmentNeverAppears() {
        expectLog("wait-ko", "starting");

        assertThatThrownBy(() -> spec.readLogsInLessEachFromPod(1, 1, "wait-ko", NAMESPACE, "service is up"))
                .isInstanceOf(AssertionError.class);
    }

    /**
     * When the API rejects the log request the step must keep retrying and rethrow the API error once
     * the timeout is reached, rather than reporting a missing log line.
     */
    @Test
    public void readLogsInLessEachFromPodRethrowsWhenTheApiRejectsTheLogRequest() {
        server.expect().get()
                .withPath("/api/v1/namespaces/" + NAMESPACE + "/pods/rejected/log?pretty=false")
                .andReturn(404, "")
                .always();

        assertThatThrownBy(() -> spec.readLogsInLessEachFromPod(1, 1, "rejected", NAMESPACE, "anything"))
                .isInstanceOf(io.fabric8.kubernetes.client.KubernetesClientException.class);
    }

    /**
     * With a polling interval longer than the timeout the loop exits without ever reaching the rethrow
     * guard, so the step ends on the trailing assertion over the log contents.
     */
    @Test
    public void readLogsInLessEachFromPodFailsTheAssertionWhenThePollingWindowIsExhausted() {
        expectLog("never-matches", "nothing of interest here");

        assertThatThrownBy(() -> spec.readLogsInLessEachFromPod(1, 2, "never-matches", NAMESPACE, "anything"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void getLogDeploymentPrefixesTheVariableAndFileWithThePodName() throws Exception {
        expectLog("deploy-logger-pod", "deployment log line");

        spec.getLogDeployment("app=deploylogger", NAMESPACE, "DEPLOY_LOG", "deploy.log");

        assertThat(ThreadProperty.get("deploy-logger-pod_DEPLOY_LOG")).isEqualTo("deployment log line");
        assertThat(readTargetFile("deploy-logger-pod_deploy.log")).isEqualTo("deployment log line");
    }

    @Test
    public void getNewPodLogsReturnsOnlyTheLinesAddedSinceTheCapture() throws Exception {
        expectTailedLog("incremental", 1, "line3");
        expectTailedLog("incremental", 1000, "line1\nline2\nline3\nline4\nline5");

        spec.startCapturingPodLogs("incremental", NAMESPACE);
        spec.getNewPodLogs("incremental", NAMESPACE, "NEW_LOGS", "incremental.log");

        assertThat(ThreadProperty.get("NEW_LOGS")).isEqualTo("line4\nline5\n");
        assertThat(readTargetFile("incremental.log")).isEqualTo("line4\nline5\n");
    }

    @Test
    public void getNewPodLogsReportsNoChangesWhenTheLogIsIdentical() throws Exception {
        expectTailedLog("unchanged", 1, "same line");
        expectTailedLog("unchanged", 1000, "same line");

        spec.startCapturingPodLogs("unchanged", NAMESPACE);
        spec.getNewPodLogs("unchanged", NAMESPACE, "UNCHANGED_LOGS", null);

        assertThat(ThreadProperty.get("UNCHANGED_LOGS")).isEqualTo("--- No new logs found since initial capture. ---");
    }

    @Test
    public void getNewPodLogsFallsBackToTheWholeLogWhenTheAnchorLineIsGone() throws Exception {
        expectTailedLog("rotated", 1, "old anchor");
        expectTailedLog("rotated", 1000, "brand new content");

        spec.startCapturingPodLogs("rotated", NAMESPACE);
        spec.getNewPodLogs("rotated", NAMESPACE, "ROTATED_LOGS", null);

        assertThat(ThreadProperty.get("ROTATED_LOGS"))
                .startsWith("--- Changes in logs detected. Showing current logs ---")
                .contains("brand new content");
    }

    @Test
    public void getNewPodLogsReportsNoChangesWhenTheCurrentLogIsEmpty() throws Exception {
        expectTailedLog("emptied", 1, "something");
        expectTailedLog("emptied", 1000, "");

        spec.startCapturingPodLogs("emptied", NAMESPACE);
        spec.getNewPodLogs("emptied", NAMESPACE, "EMPTIED_LOGS", null);

        assertThat(ThreadProperty.get("EMPTIED_LOGS")).isEqualTo("--- No new logs found since initial capture. ---");
    }

    @Test
    public void getNewPodLogsRequiresAPreviousCapture() {
        assertThatThrownBy(() -> spec.getNewPodLogs("never-captured", NAMESPACE, null, null))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("No initial logs found for pod 'never-captured'");
    }

    @Test
    public void scaleK8sScalesTheDeployment() {
        spec.scaleK8s("restart-deploy", NAMESPACE, 3);
    }

    /**
     * A rolling restart is a strategic-merge PATCH, which the CRUD dispatcher answers with a 415, so the
     * two endpoints are scripted explicitly.
     */
    @Test
    public void restartResourceRestartsDeploymentsAndStatefulsets() throws Exception {
        expectPatch("/apis/apps/v1/namespaces/" + NAMESPACE + "/deployments/restart-deploy");
        expectPatch("/apis/apps/v1/namespaces/" + NAMESPACE + "/statefulsets/restart-sts");

        spec.restartResource("deployment", "restart-deploy", NAMESPACE);
        spec.restartResource("statefulset", "restart-sts", NAMESPACE);

        assertThat(server.getLastRequest().getMethod()).isEqualTo("PATCH");
    }

    private void expectPatch(String path) {
        server.expect().patch().withPath(path).andReturn(200, "{}").always();
    }
}
