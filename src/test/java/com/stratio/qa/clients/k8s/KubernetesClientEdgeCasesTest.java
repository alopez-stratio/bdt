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
package com.stratio.qa.clients.k8s;

import com.stratio.qa.specs.CommonG;
import com.stratio.qa.utils.ThreadProperty;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the corners of {@link KubernetesClient} that the resource-oriented test classes do not reach:
 * {@code runPod}, the failure branches of {@code connect} and {@code getDefaultStorageClass}, the file
 * transfer entry points and {@code obtainJSONInfoAndExpose}.
 *
 * No storage class is seeded on purpose, so {@code getDefaultStorageClass} can be driven into its
 * "not found" branch.
 */
public class KubernetesClientEdgeCasesTest extends K8sMockServerTestBase {

    private CommonG commonG;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "startMockServer")
    public void buildCommonG() {
        ThreadProperty.set("class", getClass().getCanonicalName());
        commonG = new CommonG();
        commonG.initKubernetesClient();
    }

    @Override
    protected void seed() {
        Map<String, String> selector = Collections.singletonMap("app", "edge");

        mockClient.namespaces().resource(K8sFixtures.namespace(NAMESPACE)).create();
        mockClient.pods().inNamespace(NAMESPACE).resource(K8sFixtures.pod("edge-pod", NAMESPACE, Collections.emptyMap())).create();
        mockClient.pods().inNamespace(NAMESPACE).resource(K8sFixtures.pod("edge-forwarded", NAMESPACE, selector)).create();
        mockClient.services().inNamespace(NAMESPACE).resource(new io.fabric8.kubernetes.api.model.ServiceBuilder()
                .withNewMetadata().withName("edge-svc-forwarded").withNamespace(NAMESPACE).endMetadata()
                .withNewSpec()
                .withSelector(selector)
                .addNewPort().withPort(8200).withTargetPort(new io.fabric8.kubernetes.api.model.IntOrString(8200))
                .withProtocol("TCP").endPort()
                .endSpec()
                .build()).create();
    }

    /**
     * fabric8 binds the local socket eagerly and opens the tunnel only when something connects, so the
     * forward can be set up and torn down against the mock. High local ports are used on purpose: the
     * only place that needs a fixed one is the Vault steps, which hardcode 8200.
     */
    @Test
    public void portForwardCanBeOpenedAndClosedForAPodAndForAService() throws Exception {
        client.setLocalPortForwardPod(NAMESPACE, "edge-forwarded", 8200, 18200, "edge-pod-forward");
        client.closePortForward("edge-pod-forward");

        client.setLocalPortForwardService(NAMESPACE, "edge-svc-forwarded", 8200, 18201, "edge-svc-forward");
        client.closePortForward("edge-svc-forward");

        // closing twice must be a no-op rather than a failure
        client.closePortForward("edge-svc-forward");
    }

    @Test
    public void runPodCreatesThePodWithTheGivenCommandAndArguments() {
        client.runPod("run-pod", NAMESPACE, "busybox:1.36", null, "Never", "default",
                "sh", Arrays.asList("-c", "echo hello"));

        assertThat(client.getPod("run-pod", NAMESPACE)).isNotNull();
        assertThat(client.getNamespacePods(NAMESPACE).split("\n")).contains("run-pod");
    }

    @Test
    public void runPodAcceptsEnvironmentVariablesAndAnExplicitPullPolicy() {
        Map<String, String> env = new HashMap<>();
        env.put("LOG_LEVEL", "DEBUG");

        client.runPod("run-pod-env", NAMESPACE, "busybox:1.36", "Always", "Never", "default",
                env, "sh", Arrays.asList("-c", "env"));

        assertThat(client.getPod("run-pod-env", NAMESPACE)).isNotNull();
    }

    @Test
    public void getDefaultStorageClassFailsWhenNoClassIsAnnotatedAsDefault() {
        mockClient.storage().v1().storageClasses().resource(K8sFixtures.storageClass("edge-sc", false)).create();

        assertThatThrownBy(() -> client.getDefaultStorageClass())
                .isInstanceOf(Exception.class)
                .hasMessage("Default storageClass not found");
    }

    @Test
    public void obtainJSONInfoAndExposeStripsBracketsAndQuotesFromTheJqResult() {
        client.obtainJSONInfoAndExpose(commonG, "{\"cluster\":{\"name\":\"keos\"}}", "$.cluster.name", "EDGE_JQ", null);

        assertThat(ThreadProperty.get("EDGE_JQ")).isEqualTo("keos");
    }

    @Test
    public void copyFileToPodSurfacesTheFailureWhenTheTransferCannotBeSetUp() {
        // the mock server does not implement the exec subresource used by the file upload
        assertThatThrownBy(() -> client.copyFileToPod("edge-pod", NAMESPACE, null,
                "src/test/resources/k8s/configmap-value.txt", "/tmp/uploaded.txt"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void copyFileToPodSurfacesTheFailureWhenAContainerIsGiven() {
        assertThatThrownBy(() -> client.copyFileToPod("edge-pod", NAMESPACE, "main",
                "src/test/resources/k8s/configmap-value.txt", "/tmp/uploaded.txt"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void copyFileFromPodSurfacesTheFailureWhenTheTransferCannotBeSetUp() {
        assertThatThrownBy(() -> client.copyFileFromPod("edge-pod", NAMESPACE,
                "/tmp/remote.txt", "target/downloaded.txt"))
                .isInstanceOf(RuntimeException.class);
    }

    /**
     * The kubectl fallback shells out instead of using the API, so it cannot succeed here — kubectl is
     * not pointed at any cluster. What is asserted is that the command is built and delegated to the
     * local command runner, and that the non-zero exit status is recorded rather than swallowed.
     */
    @Test
    public void copyFileToPodKubectlBuildsAndRunsTheKubectlCommand() throws Exception {
        client.copyFileToPodKubectl("edge-pod", NAMESPACE, "main",
                "src/test/resources/k8s/configmap-value.txt", "/tmp/uploaded.txt", commonG);

        assertThat(commonG.getCommandExitStatus()).isNotZero();
    }

    /**
     * connect() swallows the IOException raised while reading the kubeconfig and hands an empty document
     * to Config.fromKubeconfig, which then fails. The client is rebuilt afterwards so the rest of the
     * suite keeps working — the singleton holds the fabric8 client in a static field.
     */
    @Test
    public void connectFailsWhenTheKubeConfigFileCannotBeRead() throws Exception {
        try {
            assertThatThrownBy(() -> client.connect("target/this-kubeconfig-does-not-exist.yaml"))
                    .isInstanceOf(Exception.class);
        } finally {
            client.connect(kubeConfigPath().toString());
        }

        assertThat(client.getKubernetesClient().getMasterUrl().toString()).isEqualTo(mockServerUrl());
    }
}
