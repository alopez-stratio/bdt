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

import io.fabric8.kubernetes.api.model.PodBuilder;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@code connect} and the singleton accessors of {@link KubernetesClient}: the production code
 * only exposes a kubeconfig path as an entry point, so this is the test that proves the generated
 * kubeconfig really drives the client at the mock server.
 */
public class KubernetesClientConnectionTest extends K8sMockServerTestBase {

    @Override
    protected void seed() {
        mockClient.pods().inNamespace(NAMESPACE).resource(new PodBuilder()
                .withNewMetadata().withName("connect-pod").withNamespace(NAMESPACE).endMetadata()
                .build()).create();
    }

    @Test
    public void connectBuildsAClientPointingAtTheKubeConfigServer() {
        assertThat(client.getKubernetesClient()).isNotNull();
        assertThat(client.getKubernetesClient().getMasterUrl().toString()).isEqualTo(mockServerUrl());
    }

    @Test
    public void getPodsReturnsSeededPods() {
        assertThat(client.getPods(NAMESPACE).getItems())
                .extracting(pod -> pod.getMetadata().getName())
                .containsExactly("connect-pod");
    }

    @Test
    public void getNamespacePodsSerialisesPodNames() {
        assertThat(client.getNamespacePods(NAMESPACE)).contains("connect-pod");
    }

    @Test
    public void getInstanceAlwaysReturnsTheSameSingleton() {
        assertThat(KubernetesClient.getInstance()).isSameAs(client);
        assertThat(KubernetesClient.getInstance()).isSameAs(KubernetesClient.getInstance());
    }

    @Test
    public void getLoggerIsAvailable() {
        assertThat(KubernetesClient.getLogger()).isNotNull();
    }

    @Test
    public void connectExportsTheKubeConfigPathAsASystemProperty() {
        assertThat(System.getProperty(io.fabric8.kubernetes.client.Config.KUBERNETES_KUBECONFIG_FILE))
                .isEqualTo(kubeConfigPath().toString());
        assertThat(System.getProperty(io.fabric8.kubernetes.client.Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY))
                .isEqualTo("false");
    }
}
