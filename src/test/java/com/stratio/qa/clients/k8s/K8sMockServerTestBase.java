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

import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockWebServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import static io.fabric8.kubernetes.client.Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_KUBECONFIG_FILE;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_TRYNAMESPACE_PATH_SYSTEM_PROPERTY;

/**
 * Base class for tests that exercise {@link KubernetesClient} against an in-JVM fabric8 mock API server.
 *
 * The mock server runs in CRUD mode, so create/get/list/update/delete behave like a real API server
 * backed by an in-memory store. No Kubernetes cluster, no Docker and no network access are required,
 * which makes these tests safe to run inside a CI pod that is itself running on Kubernetes.
 *
 * The production {@link KubernetesClient} is a singleton that only exposes {@code connect(kubeConfigPath)}
 * as an entry point, so instead of injecting a client we generate a throwaway kubeconfig pointing at the
 * mock server and go through the real {@code connect} path.
 */
public abstract class K8sMockServerTestBase {

    protected static final String NAMESPACE = "bdt-test";

    protected KubernetesMockServer server;

    /** Client used to seed the in-memory store. Bypasses the class under test on purpose. */
    protected NamespacedKubernetesClient mockClient;

    /** Class under test. */
    protected KubernetesClient client;

    private Path kubeConfigPath;

    private String previousKubeConfigFile;

    private String previousTryServiceAccount;

    private String previousTryNamespacePath;

    @BeforeClass(alwaysRun = true)
    public void startMockServer() throws IOException {
        previousKubeConfigFile = System.getProperty(KUBERNETES_KUBECONFIG_FILE);
        previousTryServiceAccount = System.getProperty(KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY);
        previousTryNamespacePath = System.getProperty(KUBERNETES_TRYNAMESPACE_PATH_SYSTEM_PROPERTY);

        // The dispatcher and the server must share the same expectations map for server.expect() to work
        Map<ServerRequest, Queue<ServerResponse>> responses = new HashMap<>();
        Dispatcher dispatcher = crudMode()
                ? new KubernetesCrudDispatcher()
                : new KubernetesMixedDispatcher(responses);
        server = new KubernetesMockServer(new Context(), new MockWebServer(), responses, dispatcher, false);
        server.init();
        mockClient = server.createClient();

        kubeConfigPath = writeKubeConfig();
        client = KubernetesClient.getInstance();
        client.connect(kubeConfigPath.toString());

        seed();
    }

    @AfterClass(alwaysRun = true)
    public void stopMockServer() throws IOException {
        if (mockClient != null) {
            mockClient.close();
        }
        if (client != null && client.getKubernetesClient() != null) {
            client.getKubernetesClient().close();
        }
        if (server != null) {
            server.destroy();
        }
        if (kubeConfigPath != null) {
            Files.deleteIfExists(kubeConfigPath);
        }
        // connect() mutates global system properties; leaving them set would leak into other test classes
        restoreProperty(KUBERNETES_KUBECONFIG_FILE, previousKubeConfigFile);
        restoreProperty(KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, previousTryServiceAccount);
        restoreProperty(KUBERNETES_TRYNAMESPACE_PATH_SYSTEM_PROPERTY, previousTryNamespacePath);
    }

    /**
     * Hook for subclasses to populate the in-memory store before the tests run.
     */
    protected void seed() {
    }

    /**
     * CRUD mode backs the mock server with an in-memory store only, so create/get/list/update/delete
     * behave like a real API server and nothing else is served.
     *
     * Returning false switches to the mixed dispatcher, which answers from the {@code server.expect()}
     * expectations first and falls back to the same CRUD store. Use it for subresources the CRUD
     * dispatcher does not implement (pod logs, scale, rolling restart) or to script error responses.
     */
    protected boolean crudMode() {
        return true;
    }

    /** Path of the throwaway kubeconfig pointing at the mock server. */
    protected Path kubeConfigPath() {
        return kubeConfigPath;
    }

    protected String mockServerUrl() {
        return "http://" + server.getHostName() + ":" + server.getPort() + "/";
    }

    private Path writeKubeConfig() throws IOException {
        Path target = Paths.get("target", "k8s-mock-kubeconfig-" + getClass().getSimpleName() + ".yaml")
                .toAbsolutePath();
        Files.createDirectories(target.getParent());
        String kubeConfig = "apiVersion: v1\n"
                + "kind: Config\n"
                + "clusters:\n"
                + "- name: mock\n"
                + "  cluster:\n"
                + "    server: " + mockServerUrl() + "\n"
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

    private void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
