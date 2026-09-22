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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins down how kubernetes-client actually talks to the API server.
 *
 * kubernetes-client does not implement HTTP itself: it picks an {@link HttpClient.Factory} off the
 * classpath through the ServiceLoader, and which one wins is decided by what happens to be on it.
 * The default changed from OkHttp to Vert.x in 7.0.0, and that swap is invisible to the compiler —
 * nothing in this project names either of them. Everything it can affect is behaviour at the socket:
 * TLS, timeouts, the websocket behind exec, port forwarding.
 *
 * So this asserts the transport by name. Not because Vert.x is better than the alternatives, but
 * because a silent change of transport is exactly the kind of thing that turns into an intermittent
 * failure nobody can place. If a future upgrade or a stray dependency changes it, this says so
 * rather than leaving it to be discovered in an integration run.
 */
public class KubernetesClientTransportTest extends K8sMockServerTestBase {

    @Override
    protected void seed() {
        mockClient.resource(K8sFixtures.pod("transport-pod", NAMESPACE, null)).create();
    }

    @Test
    public void theHttpTransportIsTheOneThisProjectExpects() {
        HttpClient.Factory factory = HttpClientUtils.getHttpClientFactory();

        assertThat(factory).isNotNull();
        assertThat(factory.getClass().getName())
                .as("kubernetes-client resolves its HTTP transport off the classpath; "
                        + "if this is no longer Vert.x, the socket-level behaviour has changed with it")
                .isEqualTo("io.fabric8.kubernetes.client.vertx.VertxHttpClientFactory");
    }

    @Test
    public void thatTransportActuallyReachesTheApiServer() {
        // The factory being right is not the same as it working: this drives a real request through
        // whichever transport was resolved above.
        List<Pod> pods = client.getKubernetesClient().pods().inNamespace(NAMESPACE).list().getItems();

        assertThat(pods).extracting(p -> p.getMetadata().getName()).contains("transport-pod");
    }
}
