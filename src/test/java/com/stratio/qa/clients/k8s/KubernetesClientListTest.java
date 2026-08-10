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
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the {@code getXList} / {@code getX} / {@code describeX} family of {@link KubernetesClient}.
 *
 * Every one of those methods walks a resource list and joins the names with a newline, returning an
 * empty string when the list is empty, so both branches are asserted: one against a namespace holding
 * a seeded resource and one against an empty namespace.
 */
public class KubernetesClientListTest extends K8sMockServerTestBase {

    private static final String EMPTY_NAMESPACE = "bdt-empty";

    @Override
    protected void seed() {
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "bdt");

        mockClient.namespaces().resource(K8sFixtures.namespace(NAMESPACE)).create();
        mockClient.namespaces().resource(K8sFixtures.namespace(EMPTY_NAMESPACE)).create();

        mockClient.pods().inNamespace(NAMESPACE).resource(K8sFixtures.pod("pod-a", NAMESPACE, labels)).create();
        mockClient.services().inNamespace(NAMESPACE).resource(K8sFixtures.service("svc-a", NAMESPACE, labels)).create();
        mockClient.apps().deployments().inNamespace(NAMESPACE)
                .resource(K8sFixtures.deployment("deploy-a", NAMESPACE, "stratio/app:1.2.3", "LOG_LEVEL", "DEBUG")).create();
        mockClient.apps().replicaSets().inNamespace(NAMESPACE).resource(K8sFixtures.replicaSet("rs-a", NAMESPACE, labels)).create();
        mockClient.apps().statefulSets().inNamespace(NAMESPACE).resource(K8sFixtures.statefulSet("sts-a", NAMESPACE, labels)).create();
        mockClient.configMaps().inNamespace(NAMESPACE)
                .resource(K8sFixtures.configMap("cm-a", NAMESPACE, Collections.singletonMap("key", "value"))).create();
        mockClient.secrets().inNamespace(NAMESPACE).resource(K8sFixtures.secret("secret-a", NAMESPACE)).create();
        mockClient.serviceAccounts().inNamespace(NAMESPACE).resource(K8sFixtures.serviceAccount("sa-a", NAMESPACE)).create();
        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE).resource(K8sFixtures.pvc("pvc-a", NAMESPACE, "1Gi")).create();
        mockClient.rbac().roles().inNamespace(NAMESPACE).resource(K8sFixtures.role("role-a", NAMESPACE)).create();
        mockClient.rbac().roleBindings().inNamespace(NAMESPACE).resource(K8sFixtures.roleBinding("rb-a", NAMESPACE)).create();
        mockClient.rbac().clusterRoles().resource(K8sFixtures.clusterRole("cr-a")).create();
        mockClient.rbac().clusterRoleBindings().resource(K8sFixtures.clusterRoleBinding("crb-a")).create();
        mockClient.network().v1().ingresses().inNamespace(NAMESPACE).resource(K8sFixtures.ingress("ing-a", NAMESPACE, labels)).create();
        mockClient.network().networkPolicies().inNamespace(NAMESPACE).resource(K8sFixtures.networkPolicy("np-a", NAMESPACE)).create();
        mockClient.batch().v1().jobs().inNamespace(NAMESPACE).resource(K8sFixtures.job("job-a", NAMESPACE)).create();
        mockClient.resourceQuotas().inNamespace(NAMESPACE).resource(K8sFixtures.resourceQuota("rq-a", NAMESPACE)).create();
        mockClient.apiextensions().v1().customResourceDefinitions()
                .resource(K8sFixtures.customResourceDefinition("pgclusters", "postgres.stratio.com", "PgCluster", "v1")).create();
        mockClient.storage().v1().storageClasses().resource(K8sFixtures.storageClass("sc-standard", false)).create();
        mockClient.storage().v1().storageClasses().resource(K8sFixtures.storageClass("sc-default", true)).create();
        mockClient.v1().events().inNamespace(NAMESPACE)
                .resource(K8sFixtures.event("event-a", NAMESPACE, "Back-off restarting failed container", "BackOff", "Pod", "pod-a")).create();
    }

    @Test
    public void getNamespacePodsJoinsNamesWithNewline() {
        assertThat(client.getNamespacePods(NAMESPACE)).isEqualTo("pod-a");
        assertThat(client.getNamespacePods(EMPTY_NAMESPACE)).isEmpty();
    }

    @Test
    public void getNamespacePodsWithNullNamespaceLooksInEveryNamespace() {
        assertThat(client.getNamespacePods(null)).contains("pod-a");
    }

    @Test
    public void getAllNamespacesListsSeededNamespaces() {
        assertThat(client.getAllNamespaces().split("\n")).contains(NAMESPACE, EMPTY_NAMESPACE);
    }

    @Test
    public void getPodReturnsThePodAndDefaultsToTheKubeConfigNamespace() {
        assertThat(client.getPod("pod-a", NAMESPACE).getMetadata().getName()).isEqualTo("pod-a");
        assertThat(client.getPod("pod-a", null).getMetadata().getName()).isEqualTo("pod-a");
        assertThat(client.getPod("does-not-exist", NAMESPACE)).isNull();
    }

    @Test
    public void getPodsReturnsThePodList() {
        assertThat(client.getPods(NAMESPACE).getItems()).hasSize(1);
        assertThat(client.getPods(EMPTY_NAMESPACE).getItems()).isEmpty();
    }

    @Test
    public void getDeploymentReturnsTheDeployment() {
        assertThat(client.getDeployment("deploy-a", NAMESPACE).getMetadata().getName()).isEqualTo("deploy-a");
    }

    @Test
    public void getDeploymentEnvVarReturnsTheValue() throws Exception {
        assertThat(client.getDeploymentEnvVar("LOG_LEVEL", "deploy-a", NAMESPACE)).isEqualTo("DEBUG");
    }

    @Test
    public void getDeploymentEnvVarFailsWhenTheVariableIsMissing() {
        assertThatThrownBy(() -> client.getDeploymentEnvVar("NOPE", "deploy-a", NAMESPACE))
                .isInstanceOf(Exception.class)
                .hasMessage("Environment variable NOPE not found in deployment deploy-a");
    }

    @Test
    public void getDeploymentVersionReturnsTheImageTag() {
        assertThat(client.getDeploymentVersion("deploy-a", NAMESPACE)).isEqualTo("1.2.3");
    }

    @Test
    public void getContainerReturnsTheMatchingContainerOnly() {
        assertThat(client.getContainer(NAMESPACE, "deploy-a", "main")).isNotNull();
        assertThat(client.getContainer(NAMESPACE, "deploy-a", "sidecar")).isNull();
    }

    @Test
    public void getPersistentVolumeClaimsReturnsThePvcWithAndWithoutNamespace() {
        assertThat(client.getPersistentVolumeClaims("pvc-a", NAMESPACE).getMetadata().getName()).isEqualTo("pvc-a");
        assertThat(client.getPersistentVolumeClaims("pvc-a", null).getMetadata().getName()).isEqualTo("pvc-a");
    }

    @Test
    public void listMethodsReturnSeededNames() {
        assertThat(client.getConfigMapList(NAMESPACE)).isEqualTo("cm-a");
        assertThat(client.getSecretsList(NAMESPACE)).isEqualTo("secret-a");
        assertThat(client.getServiceAccountList(NAMESPACE)).isEqualTo("sa-a");
        assertThat(client.getReplicaSetList(NAMESPACE)).isEqualTo("rs-a");
        assertThat(client.getStateFulSetList(NAMESPACE)).isEqualTo("sts-a");
        assertThat(client.getDeploymentList(NAMESPACE)).isEqualTo("deploy-a");
        assertThat(client.getServiceList(NAMESPACE)).isEqualTo("svc-a");
        assertThat(client.getRoleList(NAMESPACE)).isEqualTo("role-a");
        assertThat(client.getRoleBindingList(NAMESPACE)).isEqualTo("rb-a");
        assertThat(client.getIngressList(NAMESPACE)).isEqualTo("ing-a");
        assertThat(client.getPersistentVolumeClaimsList(NAMESPACE)).isEqualTo("pvc-a");
        assertThat(client.getClusterRoleList().split("\n")).contains("cr-a");
        assertThat(client.getClusterRoleBindingList().split("\n")).contains("crb-a");
        assertThat(client.getCustomResourceDefinitionList().split("\n")).contains("pgclusters.postgres.stratio.com");
        assertThat(client.getStorageClasses().split("\n")).contains("sc-standard", "sc-default");
    }

    @Test
    public void listMethodsReturnAnEmptyStringForAnEmptyNamespace() {
        assertThat(client.getConfigMapList(EMPTY_NAMESPACE)).isEmpty();
        assertThat(client.getSecretsList(EMPTY_NAMESPACE)).isEmpty();
        assertThat(client.getServiceAccountList(EMPTY_NAMESPACE)).isEmpty();
        assertThat(client.getReplicaSetList(EMPTY_NAMESPACE)).isEmpty();
        assertThat(client.getStateFulSetList(EMPTY_NAMESPACE)).isEmpty();
        assertThat(client.getDeploymentList(EMPTY_NAMESPACE)).isEmpty();
        assertThat(client.getServiceList(EMPTY_NAMESPACE)).isEmpty();
        assertThat(client.getRoleList(EMPTY_NAMESPACE)).isEmpty();
        assertThat(client.getRoleBindingList(EMPTY_NAMESPACE)).isEmpty();
        assertThat(client.getIngressList(EMPTY_NAMESPACE)).isEmpty();
        assertThat(client.getPersistentVolumeClaimsList(EMPTY_NAMESPACE)).isEmpty();
    }

    @Test
    public void getterMethodsReturnTheSeededObjects() {
        assertThat(client.getConfigMap("cm-a", NAMESPACE).getData()).containsEntry("key", "value");
        assertThat(client.getConfigMapKey("cm-a", NAMESPACE, "key")).isEqualTo("value");
        assertThat(client.getSecret("secret-a", NAMESPACE).getMetadata().getName()).isEqualTo("secret-a");
        assertThat(client.getServiceAccount("sa-a", NAMESPACE).getMetadata().getName()).isEqualTo("sa-a");
        assertThat(client.getReplicaSet("rs-a", NAMESPACE).getMetadata().getName()).isEqualTo("rs-a");
        assertThat(client.getStateFulSet("sts-a", NAMESPACE).getMetadata().getName()).isEqualTo("sts-a");
        assertThat(client.getRole("role-a", NAMESPACE).getMetadata().getName()).isEqualTo("role-a");
        assertThat(client.getRoleBinding("rb-a", NAMESPACE).getMetadata().getName()).isEqualTo("rb-a");
        assertThat(client.getClusterRole("cr-a").getMetadata().getName()).isEqualTo("cr-a");
        assertThat(client.getClusterRoleBinding("crb-a").getMetadata().getName()).isEqualTo("crb-a");
        assertThat(client.getIngress("ing-a", NAMESPACE).getMetadata().getName()).isEqualTo("ing-a");
        assertThat(client.getNetworkPolicy("np-a", NAMESPACE).getMetadata().getName()).isEqualTo("np-a");
        assertThat(client.getJob("job-a", NAMESPACE).getMetadata().getName()).isEqualTo("job-a");
        assertThat(client.getResourceQuota("rq-a", NAMESPACE).getMetadata().getName()).isEqualTo("rq-a");
        assertThat(client.getIngressListItems(NAMESPACE)).hasSize(1);
        assertThat(client.getStorageClassesList()).hasSize(2);
    }

    @Test
    public void describeMethodsSerialiseToYaml() throws Exception {
        assertThat(client.describePodYaml("pod-a", NAMESPACE)).contains("name: \"pod-a\"");
        assertThat(client.describeServiceYaml("svc-a", NAMESPACE)).contains("name: \"svc-a\"");
        assertThat(client.describeDeploymentYaml("deploy-a", NAMESPACE)).contains("name: \"deploy-a\"");
        assertThat(client.describeReplicaSet("rs-a", NAMESPACE)).contains("name: \"rs-a\"");
        assertThat(client.describeStateFulSet("sts-a", NAMESPACE)).contains("name: \"sts-a\"");
        assertThat(client.describeServiceAccount("sa-a", NAMESPACE)).contains("name: \"sa-a\"");
        assertThat(client.describeSecret("secret-a", NAMESPACE)).contains("name: \"secret-a\"");
        assertThat(client.describeRole("role-a", NAMESPACE)).contains("name: \"role-a\"");
        assertThat(client.describeRoleBinding("rb-a", NAMESPACE)).contains("name: \"rb-a\"");
        assertThat(client.describeClusterRole("cr-a")).contains("name: \"cr-a\"");
        assertThat(client.describeClusterRoleBinding("crb-a")).contains("name: \"crb-a\"");
        assertThat(client.describeIngress("ing-a", NAMESPACE)).contains("name: \"ing-a\"");
        assertThat(client.describeNetworkPolicyYaml("np-a", NAMESPACE)).contains("name: \"np-a\"");
        assertThat(client.describeJobYaml("job-a", NAMESPACE)).contains("name: \"job-a\"");
        assertThat(client.describeResourceQuotaYaml("rq-a", NAMESPACE)).contains("name: \"rq-a\"");
        assertThat(client.describePersistentVolumeClaims("pvc-a", NAMESPACE)).contains("name: \"pvc-a\"");
        assertThat(client.describeConfigMap("cm-a", NAMESPACE)).isEqualTo("{key=value}");
    }

    @Test
    public void describeServiceYamlRejectsANullNamespace() {
        assertThatThrownBy(() -> client.describeServiceYaml("svc-a", null))
                .isInstanceOf(Exception.class)
                .hasMessage("Namespace is mandatory");
    }

    @Test
    public void checkEventNamespaceMatchesOnMessageReasonTypeAndName() {
        assertThat(client.checkEventNamespace(null, NAMESPACE, "Pod", "pod-a", "BackOff", "Back-off restarting")).isTrue();
        assertThat(client.checkEventNamespace(null, NAMESPACE, null, null, null, "Back-off restarting")).isTrue();
        assertThat(client.checkEventNamespace(null, NAMESPACE, "Pod", "pod-a", "OtherReason", "Back-off restarting")).isFalse();
        assertThat(client.checkEventNamespace(null, NAMESPACE, "Service", "pod-a", "BackOff", "Back-off restarting")).isFalse();
        assertThat(client.checkEventNamespace(null, NAMESPACE, "Pod", "other-pod", "BackOff", "Back-off restarting")).isFalse();
        assertThat(client.checkEventNamespace(null, NAMESPACE, null, null, null, "nothing like this")).isFalse();
    }

    @Test
    public void checkEventNamespaceNegatesTheMessageMatchWhenNotIsSet() {
        assertThat(client.checkEventNamespace("not", NAMESPACE, null, null, null, "nothing like this")).isTrue();
        assertThat(client.checkEventNamespace("not", NAMESPACE, null, null, null, "Back-off restarting")).isFalse();
    }

    @Test
    public void getDefaultStorageClassReturnsTheAnnotatedOne() throws Exception {
        assertThat(client.getDefaultStorageClass()).isEqualTo("sc-default");
    }
}
