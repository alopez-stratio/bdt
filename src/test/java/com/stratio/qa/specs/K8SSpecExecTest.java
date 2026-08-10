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
import io.cucumber.datatable.DataTable;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the {@link K8SSpec} steps that run commands inside a pod. They all go through
 * {@code execCommand}, whose websocket upgrade is scripted on the mock server.
 */
public class K8SSpecExecTest extends K8sMockServerTestBase {

    private static final String POD = "exec-spec-pod";

    private static final String KEOS_NAMESPACE = "keos-ops";

    private static final String KEOS_POD = "keos-operator-abc123";

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
        Map<String, String> keosLabels = new HashMap<>();
        keosLabels.put("app.kubernetes.io/instance", "keos-operator");
        keosLabels.put("app.kubernetes.io/name", "keos-operator");

        mockClient.namespaces().resource(K8sFixtures.namespace(NAMESPACE)).create();
        mockClient.namespaces().resource(K8sFixtures.namespace(KEOS_NAMESPACE)).create();
        mockClient.pods().inNamespace(NAMESPACE).resource(K8sFixtures.pod(POD, NAMESPACE, Collections.emptyMap())).create();
        // the keos-operator steps target a container named after the operator, so the pod must expose it
        io.fabric8.kubernetes.api.model.Pod keosPod = new io.fabric8.kubernetes.api.model.PodBuilder()
                .withNewMetadata().withName(KEOS_POD).withNamespace(KEOS_NAMESPACE).withLabels(keosLabels).endMetadata()
                .withNewSpec().addNewContainer().withName("keos-operator").withImage("stratio/keos-operator:1").endContainer().endSpec()
                .build();
        mockClient.pods().inNamespace(KEOS_NAMESPACE).resource(keosPod).create();
    }

    private void expectExec(String namespace, String pod, String container, String... command) {
        StringBuilder path = new StringBuilder("/api/v1/namespaces/" + namespace + "/pods/" + pod + "/exec?");
        for (String c : command) {
            path.append("command=").append(encode(c)).append("&");
        }
        path.append("container=").append(container).append("&stdout=true&stderr=true");
        server.expect().get().withPath(path.toString()).andUpgradeToWebSocket().open().done().always();
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String readTargetFile(String fileName) throws Exception {
        return new String(Files.readAllBytes(
                Paths.get(System.getProperty("user.dir"), "target", "test-classes", fileName)), StandardCharsets.UTF_8);
    }

    @Test
    public void runCommandInPodStoresStdoutAndStderrInVariablesAndFiles() throws Exception {
        expectExec(NAMESPACE, POD, "main", "echo", "hello");

        spec.runCommandInPod("echo hello", POD, NAMESPACE, null, null,
                "EXEC_OUT", "EXEC_ERR", "exec-out.txt", "exec-err.txt", 5);

        assertThat(ThreadProperty.get("EXEC_OUT")).isNotNull();
        assertThat(ThreadProperty.get("EXEC_ERR")).isNotNull();
        assertThat(readTargetFile("exec-out.txt")).isEmpty();
        assertThat(readTargetFile("exec-err.txt")).isEmpty();
    }

    @Test
    public void runCommandInPodDatatableSplitsTheTableIntoCommandArguments() throws Exception {
        expectExec(NAMESPACE, POD, "main", "sh", "-c", "true");

        DataTable command = DataTable.create(Arrays.asList(
                Collections.singletonList("sh"),
                Collections.singletonList("-c"),
                Collections.singletonList("true")));

        spec.runCommandInPodDatatable(POD, NAMESPACE, null, null, "TABLE_OUT", "TABLE_ERR",
                "table-out.txt", "table-err.txt", 5, null, command);

        assertThat(ThreadProperty.get("TABLE_OUT")).isNotNull();
        assertThat(readTargetFile("table-out.txt")).isEmpty();
    }

    @Test
    public void deprecatedRunCommandInPodDatatableDelegatesToTheCurrentOverload() throws Exception {
        expectExec(NAMESPACE, POD, "main", "sh", "-c", "false");

        DataTable command = DataTable.create(Arrays.asList(
                Collections.singletonList("sh"),
                Collections.singletonList("-c"),
                Collections.singletonList("false")));

        spec.runCommandInPodDatatable(POD, NAMESPACE, null, null, "DEPRECATED_OUT", null, null, null, 5, command);

        assertThat(ThreadProperty.get("DEPRECATED_OUT")).isNotNull();
    }

    @Test
    public void executeCommandInKeosOperatorPodResolvesThePodByLabel() {
        expectExec(KEOS_NAMESPACE, KEOS_POD, "keos-operator", "keos", "version");

        spec.executeCommandInKeosOperatorPod("keos version", null, "KEOS_OUT", null, null, null, 5);

        assertThat(ThreadProperty.get("keos-operator-pod")).isEqualTo(KEOS_POD);
        assertThat(ThreadProperty.get("KEOS_OUT")).isNotNull();
    }

    @Test
    public void runCommandInKeosOperatorDatatableResolvesThePodByLabel() throws Exception {
        expectExec(KEOS_NAMESPACE, KEOS_POD, "keos-operator", "keos", "status");

        DataTable command = DataTable.create(Arrays.asList(
                Collections.singletonList("keos"),
                Collections.singletonList("status")));

        spec.runCommandInKeosOperatorDatatable(null, "KEOS_TABLE_OUT", null, null, null, 5, null, command);

        assertThat(ThreadProperty.get("KEOS_TABLE_OUT")).isNotNull();
    }

    @Test
    public void keosOperatorStepsWrapFailuresInARuntimeException() {
        // no expectation is registered for this command, so the exec upgrade never happens
        assertThatThrownBy(() -> spec.executeCommandInKeosOperatorPod("unstubbed command", null, null, null, null, null, 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error executing command on keos-operator pod");
    }

    @Test
    public void createSecretKeosOperatorFailsWhenTheCommandOutputHasNoSuccessMarker() {
        expectExec(KEOS_NAMESPACE, KEOS_POD, "keos-operator", "sh", "-c", "keos vault write /secret/foo user='admin'");

        DataTable params = DataTable.create(Collections.singletonList(Arrays.asList("user", "admin")));

        assertThatThrownBy(() -> spec.createSecretKeosOperator("/secret/foo", params))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void deleteSecretKeosOperatorFailsWhenTheCommandOutputHasNoSuccessMarker() {
        expectExec(KEOS_NAMESPACE, KEOS_POD, "keos-operator", "sh", "-c", "keos vault delete /secret/foo");

        assertThatThrownBy(() -> spec.deleteSecretKeosOperator("/secret/foo"))
                .isInstanceOf(AssertionError.class);
    }
}
