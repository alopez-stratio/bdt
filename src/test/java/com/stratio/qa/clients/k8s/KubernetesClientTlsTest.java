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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Pod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the client against a mock API server speaking TLS.
 *
 * Every other class in this suite talks plain HTTP to the mock, while every real cluster this
 * connects to is HTTPS — so the TLS path had no coverage at all. That path does not belong to
 * kubernetes-client itself but to whichever HTTP transport it resolves off the classpath, and that
 * transport has already changed once (OkHttp to Vert.x in 7.0.0). A handshake that works on one and
 * not the other would show up nowhere until an integration run against a real cluster.
 *
 * The kubeconfig written for this class carries {@code insecure-skip-tls-verify}, since the mock
 * serves a self-signed certificate. That exercises the handshake and the encrypted exchange, which
 * is the part that moves with the transport; it does not cover certificate validation itself, which
 * needs a CA the client is told to trust and is a separate concern from this upgrade.
 */
public class KubernetesClientTlsTest extends K8sMockServerTestBase {

    @Override
    protected boolean useHttps() {
        return true;
    }

    @Override
    protected void seed() {
        mockClient.resource(K8sFixtures.pod("tls-pod", NAMESPACE, null)).create();
    }

    @Test
    public void theClientConnectsOverTls() {
        assertThat(client.getKubernetesClient().getMasterUrl().getProtocol())
                .as("the mock is serving HTTPS, so the client must have been pointed at it over TLS")
                .isEqualTo("https");
    }

    @Test
    public void readingGoesThroughTheTlsConnection() {
        List<Pod> pods = client.getKubernetesClient().pods().inNamespace(NAMESPACE).list().getItems();

        assertThat(pods).extracting(p -> p.getMetadata().getName()).contains("tls-pod");
    }

    @Test
    public void writingGoesThroughTheTlsConnection() {
        // A read only proves the handshake; a write pushes a body through the encrypted connection
        // and reads the server's answer back, which is where a transport swap is more likely to show.
        client.createOrReplaceConfigMap("tls-configmap", NAMESPACE, "key", "value");

        ConfigMap written = client.getConfigMap("tls-configmap", NAMESPACE);

        assertThat(written).isNotNull();
        assertThat(written.getData()).isEqualTo(Collections.singletonMap("key", "value"));
    }
}
