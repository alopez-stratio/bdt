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

import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers {@code execCommand}, which upgrades the connection to a websocket. The mock server can script
 * that upgrade, so the whole method body — listener wiring, latch handling and result map — is exercised
 * without a cluster.
 */
public class KubernetesClientExecTest extends K8sMockServerTestBase {

    private static final String POD = "exec-pod";

    @Override
    protected boolean crudMode() {
        return false;
    }

    @Override
    protected void seed() {
        mockClient.namespaces().resource(K8sFixtures.namespace(NAMESPACE)).create();
        mockClient.pods().inNamespace(NAMESPACE).resource(K8sFixtures.pod(POD, NAMESPACE, Collections.emptyMap())).create();
    }

    private void expectExec(String... command) {
        StringBuilder path = new StringBuilder("/api/v1/namespaces/" + NAMESPACE + "/pods/" + POD + "/exec?");
        for (String c : command) {
            path.append("command=").append(c).append("&");
        }
        path.append("container=main&stdout=true&stderr=true");
        server.expect().get().withPath(path.toString())
                .andUpgradeToWebSocket().open().done().always();
    }

    @Test
    public void execCommandReturnsTheCollectedStreams() throws Exception {
        expectExec("ls");

        Map<String, String> result = client.execCommand(POD, NAMESPACE, null, 5, new String[]{"ls"}, null);

        assertThat(result).containsKeys("stdout", "stderr", "timeout");
        assertThat(result.get("timeout")).isEqualTo("false");
    }

    @Test
    public void execCommandAcceptsAnExplicitContainer() throws Exception {
        expectExec("pwd");

        Map<String, String> result = client.execCommand(POD, NAMESPACE, "main", 5, new String[]{"pwd"}, null);

        assertThat(result.get("timeout")).isEqualTo("false");
    }

    @Test
    public void execCommandFailsWhenAFailureReasonIsExpectedButNoStatusArrives() {
        expectExec("whoami");

        assertThatThrownBy(() -> client.execCommand(POD, NAMESPACE, null, 5, new String[]{"whoami"}, "NonZeroExitCode"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Expected failureReason is NonZeroExitCode but status returned is null");
    }
}
