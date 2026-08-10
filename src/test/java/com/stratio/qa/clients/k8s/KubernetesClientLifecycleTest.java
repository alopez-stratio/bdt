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

import io.cucumber.datatable.DataTable;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the write side of {@link KubernetesClient}: create, update, patch and delete.
 *
 * Each test uses its own resource names so the class is independent of TestNG method ordering.
 */
public class KubernetesClientLifecycleTest extends K8sMockServerTestBase {

    private static final String CONFIGMAP_FILE = "src/test/resources/k8s/configmap-to-apply.yaml";

    @Override
    protected void seed() {
        mockClient.namespaces().resource(K8sFixtures.namespace(NAMESPACE)).create();
    }

    @Test
    public void createDeploymentBuildsASingleReplicaDeploymentWithTheGivenImage() {
        client.createDeployment("created-deploy", NAMESPACE, "stratio/app:2.0.0", null);

        assertThat(client.getDeployment("created-deploy", NAMESPACE)).isNotNull();
        assertThat(client.getDeploymentVersion("created-deploy", NAMESPACE)).isEqualTo("2.0.0");
        assertThat(client.getContainer(NAMESPACE, "created-deploy", "created-deploy").getImagePullPolicy())
                .isEqualTo("IfNotPresent");
    }

    @Test
    public void createDeploymentHonoursAnExplicitImagePullPolicy() {
        client.createDeployment("created-deploy-always", NAMESPACE, "stratio/app:2.0.0", "Always");

        assertThat(client.getContainer(NAMESPACE, "created-deploy-always", "created-deploy-always").getImagePullPolicy())
                .isEqualTo("Always");
    }

    @Test
    public void exposeDeploymentCreatesAServiceSelectingTheDeployment() {
        client.createDeployment("exposed", NAMESPACE, "stratio/app:1", null);
        client.exposeDeployment("exposed", "exposed-svc", "ClusterIP", NAMESPACE, 8080, false);

        assertThat(client.getServiceList(NAMESPACE).split("\n")).contains("exposed-svc");
        assertThat(mockClient.services().inNamespace(NAMESPACE).withName("exposed-svc").get().getSpec().getSelector())
                .containsEntry("app", "exposed");
    }

    @Test
    public void exposeDeploymentUsesTheCctSelectorAndTheDeploymentNameWhenNoServiceNameIsGiven() {
        client.createDeployment("cct-app", NAMESPACE, "stratio/app:1", null);
        client.exposeDeployment("cct-app", null, "ClusterIP", NAMESPACE, 9090, true);

        assertThat(mockClient.services().inNamespace(NAMESPACE).withName("cct-app").get().getSpec().getSelector())
                .containsEntry("cct.stratio.com/application_id", "cct-app");
    }

    @Test
    public void updateDeploymentEnvVarsReplacesTheEnvironmentOfTheTargetContainerOnly() {
        mockClient.apps().deployments().inNamespace(NAMESPACE)
                .resource(K8sFixtures.deployment("env-deploy", NAMESPACE, "img:1", "OLD", "old-value")).create();

        List<EnvVar> envVars = Collections.singletonList(new EnvVarBuilder().withName("NEW").withValue("new-value").build());
        client.updateDeploymentEnvVars(envVars, NAMESPACE, "env-deploy", "main");

        assertThat(client.getContainer(NAMESPACE, "env-deploy", "main").getEnv())
                .extracting(EnvVar::getName)
                .containsExactly("NEW");
    }

    @Test
    public void updatePullPolicyImageDeploymentChangesThePolicy() {
        mockClient.apps().deployments().inNamespace(NAMESPACE)
                .resource(K8sFixtures.deployment("policy-deploy", NAMESPACE, "img:1", "K", "V")).create();

        client.updatePullPolicyImageDeployment("policy-deploy", NAMESPACE, "main", "Always");

        assertThat(client.getContainer(NAMESPACE, "policy-deploy", "main").getImagePullPolicy()).isEqualTo("Always");
    }

    @Test
    public void createOrReplaceResourceAppliesAManifestFile() throws Exception {
        client.createOrReplaceResource(CONFIGMAP_FILE, NAMESPACE);

        assertThat(client.getConfigMapKey("applied-from-file", NAMESPACE, "origin")).isEqualTo("file");
    }

    @Test
    public void configMapCanBeCreatedReplacedAndExtended() {
        client.createOrReplaceConfigMap("cm-lifecycle", NAMESPACE, "first", "1");
        assertThat(client.getConfigMapKey("cm-lifecycle", NAMESPACE, "first")).isEqualTo("1");

        client.addValueInConfigMap("cm-lifecycle", NAMESPACE, "second", "2");
        assertThat(client.getConfigMap("cm-lifecycle", NAMESPACE).getData())
                .containsEntry("first", "1")
                .containsEntry("second", "2");

        client.deleteConfigMap("cm-lifecycle", NAMESPACE);
        assertThat(client.getConfigMap("cm-lifecycle", NAMESPACE)).isNull();
    }

    @Test
    public void configMapCanBeCreatedAndExtendedFromADataTable() {
        DataTable creation = DataTable.create(Arrays.asList(
                Arrays.asList("alpha", "a"),
                Arrays.asList("beta", "b")));
        client.createOrReplaceConfigMap("cm-datatable", NAMESPACE, creation);

        assertThat(client.getConfigMap("cm-datatable", NAMESPACE).getData())
                .containsEntry("alpha", "a")
                .containsEntry("beta", "b");

        DataTable extension = DataTable.create(Arrays.asList(
                Arrays.asList("gamma", "c"),
                Arrays.asList("alpha", "overwritten")));
        client.addValuesInConfigMap("cm-datatable", NAMESPACE, extension);

        assertThat(client.getConfigMap("cm-datatable", NAMESPACE).getData())
                .containsEntry("alpha", "overwritten")
                .containsEntry("beta", "b")
                .containsEntry("gamma", "c");
    }

    @Test
    public void createNamespaceIsIdempotentAndSupportsLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("owner", "bdt");

        client.createNamespace("ns-with-labels", labels);
        assertThat(mockClient.namespaces().withName("ns-with-labels").get().getMetadata().getLabels())
                .containsEntry("owner", "bdt");

        client.createNamespace("ns-without-labels", null);
        assertThat(mockClient.namespaces().withName("ns-without-labels").get()).isNotNull();

        // second call must be a no-op instead of a 409
        client.createNamespace("ns-without-labels", null);
        assertThat(client.getAllNamespaces().split("\n")).contains("ns-without-labels");
    }

    @Test
    public void rbacObjectsCanBeCreatedAndDeleted() {
        client.createServiceAccount("sa-lifecycle", NAMESPACE);
        client.createRole("role-lifecycle", "pods,services", "get,list", "", NAMESPACE);
        client.createClusterRole("cr-lifecycle", "pods", "get", null);
        client.createRoleBinding("rb-lifecycle", "role-lifecycle", NAMESPACE + ":sa-lifecycle");
        client.createClusterRoleBinding("crb-lifecycle", "cr-lifecycle", NAMESPACE + ":sa-lifecycle");

        assertThat(client.getServiceAccount("sa-lifecycle", NAMESPACE)).isNotNull();
        assertThat(client.getRole("role-lifecycle", NAMESPACE).getRules().get(0).getVerbs()).containsExactly("get", "list");
        assertThat(client.getClusterRole("cr-lifecycle").getRules().get(0).getApiGroups()).containsExactly("");
        assertThat(client.getRoleBinding("rb-lifecycle", NAMESPACE).getSubjects().get(0).getName()).isEqualTo("sa-lifecycle");
        assertThat(client.getRoleBinding("rb-lifecycle", NAMESPACE).getSubjects().get(0).getNamespace()).isEqualTo(NAMESPACE);
        // a cluster-scoped binding must round-trip through the cluster-scoped collection
        assertThat(client.getClusterRoleBinding("crb-lifecycle").getRoleRef().getName()).isEqualTo("cr-lifecycle");
        assertThat(client.getClusterRoleBinding("crb-lifecycle").getMetadata().getNamespace()).isNull();
        assertThat(client.getClusterRoleBinding("crb-lifecycle").getSubjects().get(0).getNamespace()).isEqualTo(NAMESPACE);

        client.deleteRoleBinding("rb-lifecycle", NAMESPACE);
        client.deleteRole("role-lifecycle", NAMESPACE);
        client.deleteServiceAccount("sa-lifecycle", NAMESPACE);

        assertThat(client.getRoleBinding("rb-lifecycle", NAMESPACE)).isNull();
        assertThat(client.getRole("role-lifecycle", NAMESPACE)).isNull();
        assertThat(client.getServiceAccount("sa-lifecycle", NAMESPACE)).isNull();
    }

    @Test
    public void resourceQuotaCanBeCreatedDescribedAndDeleted() {
        client.createResourceQuota("rq-lifecycle", NAMESPACE, "2", "1Gi", "10");

        assertThat(client.getResourceQuota("rq-lifecycle", NAMESPACE).getSpec().getHard())
                .containsKeys("cpu", "memory", "pods");
        assertThat(client.describeResourceQuotaYaml("rq-lifecycle", NAMESPACE)).contains("name: \"rq-lifecycle\"");

        client.deleteResourceQuota("rq-lifecycle", NAMESPACE);
        assertThat(client.getResourceQuota("rq-lifecycle", NAMESPACE)).isNull();
    }

    @Test
    public void resourceQuotaSkipsTheQuantitiesThatAreNotProvided() {
        client.createResourceQuota("rq-partial", NAMESPACE, null, null, "5");

        assertThat(client.getResourceQuota("rq-partial", NAMESPACE).getSpec().getHard())
                .containsOnlyKeys("pods");
    }

    @Test
    public void horizontalAutoscalerMaxAndMinReplicasCanBeUpdated() {
        mockClient.autoscaling().v1().horizontalPodAutoscalers().inNamespace(NAMESPACE)
                .resource(K8sFixtures.horizontalPodAutoscaler("hpa-lifecycle", NAMESPACE, 1, 3)).create();

        client.updateHorizontalAutoscaler(NAMESPACE, "hpa-lifecycle", 7);
        assertThat(hpaSpec("hpa-lifecycle").getMaxReplicas()).isEqualTo(7);
        assertThat(hpaSpec("hpa-lifecycle").getMinReplicas()).isEqualTo(1);

        client.updateHorizontalAutoscaler(NAMESPACE, "hpa-lifecycle", 2, 9);
        assertThat(hpaSpec("hpa-lifecycle").getMinReplicas()).isEqualTo(2);
        assertThat(hpaSpec("hpa-lifecycle").getMaxReplicas()).isEqualTo(9);
    }

    @Test
    public void patchPersistVolumeClaimResizesTheStorageRequest() throws Exception {
        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE)
                .resource(K8sFixtures.pvc("pvc-patch", NAMESPACE, "1Gi")).create();

        assertThat(client.patchPersistVolumeClaim("pvc-patch", NAMESPACE, "/spec/resources/requests/storage", "5Gi"))
                .isZero();
        assertThat(client.getPersistentVolumeClaims("pvc-patch", NAMESPACE)
                .getSpec().getResources().getRequests().get("storage").toString()).isEqualTo("5Gi");
    }

    @Test
    public void patchPersistVolumeClaimRejectsUnsupportedPaths() {
        assertThatThrownBy(() -> client.patchPersistVolumeClaim("pvc-patch", NAMESPACE, "/spec/accessModes", "ReadWriteMany"))
                .isInstanceOf(Exception.class)
                .hasMessage("Patch not supported in path /spec/accessModes for PVC");
    }

    @Test
    public void deleteMethodsRemoveTheResources() {
        mockClient.pods().inNamespace(NAMESPACE).resource(K8sFixtures.pod("pod-del", NAMESPACE, Collections.emptyMap())).create();
        mockClient.services().inNamespace(NAMESPACE).resource(K8sFixtures.service("svc-del", NAMESPACE, Collections.emptyMap())).create();
        mockClient.apps().deployments().inNamespace(NAMESPACE)
                .resource(K8sFixtures.deployment("deploy-del", NAMESPACE, "img:1", "K", "V")).create();
        mockClient.apps().statefulSets().inNamespace(NAMESPACE)
                .resource(K8sFixtures.statefulSet("sts-del", NAMESPACE, Collections.emptyMap())).create();
        mockClient.secrets().inNamespace(NAMESPACE).resource(K8sFixtures.secret("secret-del", NAMESPACE)).create();
        mockClient.batch().v1().jobs().inNamespace(NAMESPACE).resource(K8sFixtures.job("job-del", NAMESPACE)).create();
        mockClient.network().v1().ingresses().inNamespace(NAMESPACE)
                .resource(K8sFixtures.ingress("ing-del", NAMESPACE, Collections.emptyMap())).create();
        mockClient.network().networkPolicies().inNamespace(NAMESPACE)
                .resource(K8sFixtures.networkPolicy("np-del", NAMESPACE)).create();
        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE)
                .resource(K8sFixtures.pvc("pvc-del", NAMESPACE, "1Gi")).create();
        mockClient.apiextensions().v1().customResourceDefinitions()
                .resource(K8sFixtures.customResourceDefinition("deletables", "bdt.stratio.com", "Deletable", "v1")).create();

        client.deletePod("pod-del", NAMESPACE);
        client.deleteService("svc-del", NAMESPACE);
        client.deleteDeployment("deploy-del", NAMESPACE);
        client.deleteStatefulset("sts-del", NAMESPACE);
        client.deleteSecret("secret-del", NAMESPACE);
        client.deleteJob("job-del", NAMESPACE);
        client.deleteIngress("ing-del", NAMESPACE);
        client.deleteNetworkPolicy("np-del", NAMESPACE);
        client.deletePersistentVolumeClaims("pvc-del", NAMESPACE);
        client.deleteCRD("deletables.bdt.stratio.com");

        assertThat(client.getPod("pod-del", NAMESPACE)).isNull();
        assertThat(client.getDeployment("deploy-del", NAMESPACE)).isNull();
        assertThat(client.getStateFulSet("sts-del", NAMESPACE)).isNull();
        assertThat(client.getSecret("secret-del", NAMESPACE)).isNull();
        assertThat(client.getJob("job-del", NAMESPACE)).isNull();
        assertThat(client.getIngress("ing-del", NAMESPACE)).isNull();
        assertThat(client.getNetworkPolicy("np-del", NAMESPACE)).isNull();
        assertThat(client.getPersistentVolumeClaims("pvc-del", NAMESPACE)).isNull();
        assertThat(client.getServiceList(NAMESPACE).split("\n")).doesNotContain("svc-del");
        assertThat(client.getCustomResourceDefinitionList().split("\n")).doesNotContain("deletables.bdt.stratio.com");
    }

    @Test
    public void deletePersistentVolumeClaimsWithLabelRemovesEveryMatchingClaim() {
        Map<String, String> labels = new HashMap<>();
        labels.put("group", "batch");
        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE)
                .resource(withLabels(K8sFixtures.pvc("pvc-labelled-1", NAMESPACE, "1Gi"), labels)).create();
        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE)
                .resource(withLabels(K8sFixtures.pvc("pvc-labelled-2", NAMESPACE, "1Gi"), labels)).create();
        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE)
                .resource(K8sFixtures.pvc("pvc-unlabelled", NAMESPACE, "1Gi")).create();

        client.deletePersistentVolumeClaimsWithLabel("group=batch", NAMESPACE);

        assertThat(client.getPersistentVolumeClaimsList(NAMESPACE).split("\n")).contains("pvc-unlabelled");
        assertThat(client.getPersistentVolumeClaims("pvc-labelled-1", NAMESPACE)).isNull();
        assertThat(client.getPersistentVolumeClaims("pvc-labelled-2", NAMESPACE)).isNull();
    }

    @Test
    public void getKeosReportWritesTheSystemPropertiesAndPodImages() throws Exception {
        mockClient.pods().inNamespace(NAMESPACE).resource(K8sFixtures.pod("report-pod", NAMESPACE, Collections.emptyMap())).create();

        client.getKeosReport();

        java.nio.file.Path report = java.nio.file.Paths.get(System.getProperty("user.dir"), "target", "test-classes", "keosReport.txt");
        assertThat(report).exists();
        String contents = new String(java.nio.file.Files.readAllBytes(report), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(contents).contains("SYSTEM PROPERTIES:").contains("POD IMAGES:").contains("busybox:1.36");
    }

    private io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscalerSpec hpaSpec(String name) {
        return mockClient.autoscaling().v1().horizontalPodAutoscalers().inNamespace(NAMESPACE).withName(name).get().getSpec();
    }

    private static <T extends io.fabric8.kubernetes.api.model.HasMetadata> T withLabels(T resource, Map<String, String> labels) {
        resource.getMetadata().setLabels(new HashMap<>(labels));
        return resource;
    }
}
