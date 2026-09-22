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
package com.stratio.qa.ATests;

import com.stratio.qa.clients.k8s.K8sFixtures;
import com.stratio.qa.cucumber.testng.CucumberFeatureWrapper;
import com.stratio.qa.cucumber.testng.PickleEventWrapper;
import com.stratio.qa.utils.BaseGTest;
import cucumber.api.CucumberOptions;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Runs the Kubernetes feature against an in-JVM fabric8 mock API server.
 *
 * The unit tests call the {@link com.stratio.qa.specs.K8SSpec} methods directly; this suite is what
 * exercises the Gherkin layer — the step regular expressions and the {@code ${}} / {@code !{}}
 * replacements — end to end. No cluster, no Docker and no network access are needed, so it runs the
 * same way inside a CI pod as it does locally.
 */
@CucumberOptions(plugin = "json:target/cucumber-k8s-mock-cluster.json", features = {
        "src/test/resources/features/k8sMockCluster.feature"
})
public class K8SMockClusterIT extends BaseGTest {

    /** Kept in sync with the namespace used in the feature file. */
    private static final String NAMESPACE = "k8s-it";

    /** System property the feature reads through ${K8S_MOCK_KUBECONFIG}. */
    private static final String KUBECONFIG_PROPERTY = "K8S_MOCK_KUBECONFIG";

    private KubernetesMockServer server;

    private NamespacedKubernetesClient seedClient;

    private Path kubeConfigPath;

    @BeforeClass(alwaysRun = true)
    public void startK8sMockCluster() throws IOException {
        server = new KubernetesMockServer(new Context(), new MockWebServer(), new HashMap<>(),
                new KubernetesCrudDispatcher(), false);
        server.init();
        seedClient = server.createClient();

        seed();

        kubeConfigPath = writeKubeConfig();
        System.setProperty(KUBECONFIG_PROPERTY, kubeConfigPath.toString());
    }

    @AfterClass(alwaysRun = true)
    public void stopK8sMockCluster() throws IOException {
        if (seedClient != null) {
            seedClient.close();
        }
        if (server != null) {
            server.destroy();
        }
        if (kubeConfigPath != null) {
            Files.deleteIfExists(kubeConfigPath);
        }
        System.clearProperty(KUBECONFIG_PROPERTY);
        // KubernetesClient.connect mutates these globally; other IT classes share this JVM
        System.clearProperty(io.fabric8.kubernetes.client.Config.KUBERNETES_KUBECONFIG_FILE);
        System.clearProperty(io.fabric8.kubernetes.client.Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY);
        System.clearProperty(io.fabric8.kubernetes.client.Config.KUBERNETES_TRYNAMESPACE_PATH_SYSTEM_PROPERTY);
    }

    private void seed() {
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "it");

        seedClient.namespaces().resource(K8sFixtures.namespace(NAMESPACE)).create();
        seedClient.pods().inNamespace(NAMESPACE).resource(runningPod(labels)).create();
        seedClient.services().inNamespace(NAMESPACE).resource(K8sFixtures.service("it-svc", NAMESPACE, labels)).create();
        seedClient.apps().deployments().inNamespace(NAMESPACE)
                .resource(K8sFixtures.deployment("it-deploy", NAMESPACE, "stratio/app:4.5.6", "LOG_LEVEL", "INFO")).create();
        seedClient.apps().statefulSets().inNamespace(NAMESPACE)
                .resource(K8sFixtures.statefulSet("it-sts", NAMESPACE, labels)).create();
        seedClient.configMaps().inNamespace(NAMESPACE)
                .resource(K8sFixtures.configMap("it-cm", NAMESPACE, Collections.singletonMap("origin", "seed"))).create();
        seedClient.secrets().inNamespace(NAMESPACE).resource(K8sFixtures.secret("it-secret", NAMESPACE)).create();
        seedClient.persistentVolumeClaims().inNamespace(NAMESPACE)
                .resource(K8sFixtures.pvc("it-pvc", NAMESPACE, "1Gi")).create();
        seedClient.storage().v1().storageClasses().resource(K8sFixtures.storageClass("it-default-sc", true)).create();
        seedClient.v1().events().inNamespace(NAMESPACE)
                .resource(K8sFixtures.event("it-event", NAMESPACE, "Created container main", "Created", "Pod", "it-pod")).create();
    }

    private io.fabric8.kubernetes.api.model.Pod runningPod(Map<String, String> labels) {
        return new PodBuilder(K8sFixtures.pod("it-pod", NAMESPACE, labels))
                .editStatus()
                .addNewContainerStatus()
                .withName("main").withReady(true).withImage("busybox:1.36").withImageID("id").withRestartCount(0)
                .endContainerStatus()
                .endStatus()
                .build();
    }

    private Path writeKubeConfig() throws IOException {
        Path target = Paths.get("target", "k8s-mock-cluster-kubeconfig.yaml").toAbsolutePath();
        Files.createDirectories(target.getParent());
        String kubeConfig = "apiVersion: v1\n"
                + "kind: Config\n"
                + "clusters:\n"
                + "- name: mock\n"
                + "  cluster:\n"
                + "    server: http://" + server.getHostName() + ":" + server.getPort() + "/\n"
                + "contexts:\n"
                + "- name: mock\n"
                + "  context:\n"
                + "    cluster: mock\n"
                + "    user: mock\n"
                + "    namespace: " + NAMESPACE + "\n"
                + "current-context: mock\n"
                + "users:\n"
                + "- name: mock\n"
                + "  user: {}\n";
        Files.write(target, kubeConfig.getBytes(StandardCharsets.UTF_8));
        return target;
    }

    @Test(dataProvider = "scenarios")
    public void run(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
        runScenario(pickleWrapper, featureWrapper);
    }
}
