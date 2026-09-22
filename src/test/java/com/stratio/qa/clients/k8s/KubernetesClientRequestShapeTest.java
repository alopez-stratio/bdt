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

import io.fabric8.kubernetes.api.model.autoscaling.v1.Scale;
import io.fabric8.kubernetes.api.model.autoscaling.v1.ScaleBuilder;
import io.fabric8.mockwebserver.http.RecordedRequest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the {@link KubernetesClient} calls that the CRUD dispatcher cannot emulate — pod logs, scale
 * and rolling restart subresources, and the cluster-scoped ClusterRoleBinding create — by scripting the
 * expected API responses instead of backing the server with an in-memory store.
 */
public class KubernetesClientRequestShapeTest extends K8sMockServerTestBase {

    @Override
    protected boolean crudMode() {
        return false;
    }

    @Test
    public void getPodLogReadsTheLogSubresource() {
        server.expect().get()
                .withPath("/api/v1/namespaces/" + NAMESPACE + "/pods/log-pod/log?pretty=false")
                .andReturn(200, "first line\nsecond line")
                .once();

        assertThat(client.getPodLog("log-pod", NAMESPACE)).isEqualTo("first line\nsecond line");
    }

    @Test
    public void getPodLogWithTailLinesAsksForTheRequestedNumberOfLines() {
        server.expect().get()
                .withPath("/api/v1/namespaces/" + NAMESPACE + "/pods/log-pod/log?pretty=false&tailLines=10")
                .andReturn(200, "tail line")
                .once();

        assertThat(client.getPodLog("log-pod", NAMESPACE, 10)).isEqualTo("tail line");
    }

    @Test
    public void scaleDeploymentPutsTheScaleSubresource() throws Exception {
        // Since 6.7.0 the client drives scale through the /scale subresource — the same request
        // kubectl scale makes — rather than reading the Deployment and writing the whole object back.
        String path = "/apis/apps/v1/namespaces/" + NAMESPACE + "/deployments/scalable";
        String scalePath = path + "/scale";
        Scale scale = new ScaleBuilder()
                .withNewMetadata().withName("scalable").withNamespace(NAMESPACE).endMetadata()
                .withNewSpec().withReplicas(3).endSpec()
                .build();

        server.expect().get().withPath(path)
                .andReturn(200, K8sFixtures.deployment("scalable", NAMESPACE, "img:1", "K", "V"))
                .always();
        server.expect().get().withPath(scalePath).andReturn(200, scale).always();
        server.expect().put().withPath(scalePath).andReturn(200, scale).always();
        server.expect().patch().withPath(scalePath).andReturn(200, scale).always();

        client.scaleDeployment("scalable", NAMESPACE, 3);

        // The client reads the Deployment back after scaling, so /scale is not the last request —
        // drain the recorded ones and assert it was written at all.
        List<String> requests = new ArrayList<>();
        for (RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
             recorded != null;
             recorded = server.takeRequest(1, TimeUnit.SECONDS)) {
            requests.add(recorded.getMethod() + " " + recorded.getPath());
        }
        assertThat(requests).contains("PUT " + scalePath);
    }

    @Test
    public void restartDeploymentPatchesTheDeployment() throws Exception {
        String path = "/apis/apps/v1/namespaces/" + NAMESPACE + "/deployments/restartable";
        server.expect().get().withPath(path)
                .andReturn(200, K8sFixtures.deployment("restartable", NAMESPACE, "img:1", "K", "V"))
                .always();
        server.expect().patch().withPath(path)
                .andReturn(200, K8sFixtures.deployment("restartable", NAMESPACE, "img:1", "K", "V"))
                .always();

        client.restartDeployment("restartable", NAMESPACE);

        assertThat(server.getLastRequest().getPath()).startsWith(path);
    }

    @Test
    public void restartStatefulsetPatchesTheStatefulset() throws Exception {
        String path = "/apis/apps/v1/namespaces/" + NAMESPACE + "/statefulsets/restartable-sts";
        server.expect().get().withPath(path)
                .andReturn(200, K8sFixtures.statefulSet("restartable-sts", NAMESPACE, java.util.Collections.emptyMap()))
                .always();
        server.expect().patch().withPath(path)
                .andReturn(200, K8sFixtures.statefulSet("restartable-sts", NAMESPACE, java.util.Collections.emptyMap()))
                .always();

        client.restartStatefulset("restartable-sts", NAMESPACE);

        assertThat(server.getLastRequest().getPath()).startsWith(path);
    }

    /**
     * A ClusterRoleBinding is cluster scoped. The namespace in the {@code <namespace>:<serviceaccount>}
     * argument identifies the subject, so it must end up on the subject and never on
     * {@code metadata.namespace} — a namespaced ClusterRoleBinding is not a valid object.
     */
    @Test
    public void createClusterRoleBindingKeepsTheNamespaceOnTheSubjectOnly() throws Exception {
        String path = "/apis/rbac.authorization.k8s.io/v1/clusterrolebindings";
        server.expect().post().withPath(path)
                .andReturn(201, K8sFixtures.clusterRoleBinding("crb-shape"))
                .always();
        server.expect().get().withPath(path + "/crb-shape")
                .andReturn(404, "")
                .always();

        client.createClusterRoleBinding("crb-shape", "a-cluster-role", NAMESPACE + ":a-service-account");

        assertThat(server.getLastRequest().getPath()).isEqualTo(path);
        org.json.JSONObject body = new org.json.JSONObject(server.getLastRequest().getBody().readUtf8());
        assertThat(body.getJSONObject("metadata").has("namespace")).isFalse();
        org.json.JSONObject subject = body.getJSONArray("subjects").getJSONObject(0);
        assertThat(subject.getString("kind")).isEqualTo("ServiceAccount");
        assertThat(subject.getString("name")).isEqualTo("a-service-account");
        assertThat(subject.getString("namespace")).isEqualTo(NAMESPACE);
    }

    @Test
    public void patchCRDFailsFastWhenTheCrdDefinitionDoesNotExist() {
        server.expect().get()
                .withPath("/apis/apiextensions.k8s.io/v1/customresourcedefinitions/missing.stratio.com")
                .andReturn(404, "")
                .always();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> client.patchCRD("missing.stratio.com", "item", NAMESPACE, "/spec/replicas", "2", "integer", null))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("CRD definition missing.stratio.com not found");
    }

    @Test
    public void closePortForwardIsANoOpWhenNothingWasForwarded() throws Exception {
        client.closePortForward(null);
        client.closePortForward("unknown-id");
    }
}
