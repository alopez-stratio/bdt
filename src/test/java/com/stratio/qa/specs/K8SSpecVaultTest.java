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
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the Vault steps of {@link K8SSpec}.
 *
 * They forward the {@code vault} service of {@code keos-core} to local port 8200 and then curl
 * {@code https://127.0.0.1:8200}. fabric8 binds the local socket eagerly and only opens the tunnel when
 * something connects, so seeding the service and a matching pod is enough for the forward to be set up
 * against the mock; the curl that follows fails, which is what drives the steps through their error
 * handling and their {@code finally} block.
 *
 * The port is hardcoded in the production step, so it cannot be randomised here. If something else on
 * the machine already holds 8200 the tests skip instead of failing.
 */
public class K8SSpecVaultTest extends K8sMockServerTestBase {

    private static final String VAULT_NAMESPACE = "keos-core";

    private static final int VAULT_LOCAL_PORT = 8200;

    private K8SSpec spec;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "startMockServer")
    public void buildSpec() {
        ThreadProperty.set("class", getClass().getCanonicalName());
        CommonG commonG = new CommonG();
        commonG.initKubernetesClient();
        spec = new K8SSpec(commonG);

        if (!isLocalPortFree(VAULT_LOCAL_PORT)) {
            throw new SkipException("Local port " + VAULT_LOCAL_PORT + " is in use; the Vault steps hardcode it");
        }
    }

    @Override
    protected void seed() {
        Map<String, String> selector = Collections.singletonMap("app", "vault");

        mockClient.namespaces().resource(K8sFixtures.namespace(VAULT_NAMESPACE)).create();
        mockClient.pods().inNamespace(VAULT_NAMESPACE).resource(K8sFixtures.pod("vault-0", VAULT_NAMESPACE, selector)).create();
        mockClient.services().inNamespace(VAULT_NAMESPACE).resource(new ServiceBuilder()
                .withNewMetadata().withName("vault").withNamespace(VAULT_NAMESPACE).endMetadata()
                .withNewSpec()
                .withSelector(selector)
                .addNewPort().withPort(VAULT_LOCAL_PORT).withTargetPort(new IntOrString(VAULT_LOCAL_PORT))
                .withProtocol("TCP").endPort()
                .endSpec()
                .build()).create();
    }

    private static boolean isLocalPortFree(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * The step writes the parameters to secret.json, curls Vault and removes the file in its finally
     * block. It does not assert on the HTTP status, so a failing curl still leaves it green — what is
     * pinned down here is that the port forward is set up and torn down and the temporary file is gone.
     */
    @Test
    public void createSecretVaultForwardsTheServiceAndCleansUpItsTemporaryFile() throws Exception {
        DataTable params = DataTable.create(Arrays.asList(
                Arrays.asList("user", "admin"),
                Arrays.asList("password", "stratio")));

        spec.createSecretVault("/secret/bdt", params);

        assertThat(new File(System.getProperty("user.dir") + "/target/test-classes/secret.json")).doesNotExist();
        assertThat(isLocalPortFree(VAULT_LOCAL_PORT)).isTrue();
    }

    @Test
    public void deleteSecretVaultForwardsTheServiceAndClosesTheForward() throws Exception {
        spec.deleteSecretVault("/secret/bdt");

        assertThat(isLocalPortFree(VAULT_LOCAL_PORT)).isTrue();
    }

    /**
     * Unlike the two above, this one does assert on the HTTP status, so the failing curl surfaces as an
     * error — and it must still close the forward on the way out.
     */
    @Test
    public void hasSecretVaultFailsWhenVaultDoesNotAnswerWithA200() {
        assertThatThrownBy(() -> spec.hasSecretVault("/secret/bdt"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Secret not found in path /secret/bdt");

        assertThat(isLocalPortFree(VAULT_LOCAL_PORT)).isTrue();
    }

    @Test
    public void readSecretVaultChecksThatTheSecretExistsFirst() {
        assertThatThrownBy(() -> spec.readSecretVault("/secret/bdt", "VAULT_SECRET"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Secret not found in path /secret/bdt");
    }

    @Test
    public void setLocalPortForwardCoversBothTheServiceAndThePodBranches() throws Exception {
        spec.setLocalPortForward(VAULT_LOCAL_PORT, 18300, "service", "vault", VAULT_NAMESPACE, "vault-service");
        spec.closePortForward("vault-service");

        spec.setLocalPortForward(VAULT_LOCAL_PORT, 18301, "pod", "vault-0", VAULT_NAMESPACE, null);
        spec.closePortForward();
    }
}
