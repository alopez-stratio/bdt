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
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the steps of {@link K8SSpec} that mutate cluster state, against the fabric8 mock API server.
 */
public class K8SSpecWriteTest extends K8sMockServerTestBase {

    private static final String GROUP = "postgres.stratio.com";

    private static final String PLURAL = "pgclusters";

    private static final String CRD_NAME = PLURAL + "." + GROUP;

    private K8SSpec spec;

    private CustomResourceDefinitionContext context;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "startMockServer")
    public void buildSpec() {
        ThreadProperty.set("class", getClass().getCanonicalName());
        CommonG commonG = new CommonG();
        commonG.initKubernetesClient();
        spec = new K8SSpec(commonG);
    }

    @Override
    protected void seed() {
        context = new CustomResourceDefinitionContext.Builder()
                .withGroup(GROUP).withVersion("v1").withPlural(PLURAL).withKind("PgCluster")
                .withName(CRD_NAME).withScope("Namespaced").build();
        server.expectCustomResource(context);

        mockClient.namespaces().resource(K8sFixtures.namespace(NAMESPACE)).create();
        mockClient.apiextensions().v1().customResourceDefinitions()
                .resource(K8sFixtures.customResourceDefinition(PLURAL, GROUP, "PgCluster", "v1")).create();
        mockClient.genericKubernetesResources(context).inNamespace(NAMESPACE).resource(pgCluster("pg-write")).create();
    }

    private GenericKubernetesResource pgCluster(String name) {
        Map<String, Object> additionalProperties = new LinkedHashMap<>();
        additionalProperties.put("spec", new LinkedHashMap<>(Collections.singletonMap("replicas", 1)));
        return new GenericKubernetesResourceBuilder()
                .withApiVersion(GROUP + "/v1")
                .withKind("PgCluster")
                .withNewMetadata().withName(name).withNamespace(NAMESPACE).endMetadata()
                .withAdditionalProperties(additionalProperties)
                .build();
    }

    @Test
    public void connectToK8sRebuildsTheClientFromAKubeConfigFile() throws Exception {
        spec.connectToK8s(kubeConfigPath().toString());

        assertThat(client.getKubernetesClient().getMasterUrl().toString()).isEqualTo(mockServerUrl());
    }

    @Test
    public void loadK8sConfigFromWorkspaceIsSkippedWhenThePathIsAlreadyKnown() throws Exception {
        ThreadProperty.set("CLUSTER_KUBE_CONFIG_PATH", kubeConfigPath().toString());
        try {
            spec.loadK8sConfigFromWorkspace(null);
        } finally {
            ThreadProperty.remove("CLUSTER_KUBE_CONFIG_PATH");
        }
    }

    @Test
    public void loadK8sConfigFromWorkspaceReturnsEarlyWithoutAClusterId() throws Exception {
        String previous = System.getProperty("KEOS_CLUSTER_ID");
        System.clearProperty("KEOS_CLUSTER_ID");
        try {
            spec.loadK8sConfigFromWorkspace(" forcefully");
        } finally {
            if (previous != null) {
                System.setProperty("KEOS_CLUSTER_ID", previous);
            }
        }
    }

    @Test
    public void createDeploymentAndExposeItThroughTheThreeOverloads() {
        spec.createDeployment("write-deploy", NAMESPACE, "stratio/app:3.0.0", "Always");
        assertThat(client.getDeployment("write-deploy", NAMESPACE)).isNotNull();

        spec.createDeployment("write-deploy", NAMESPACE, 8080);
        assertThat(client.getServiceList(NAMESPACE).split("\n")).contains("write-deploy");

        spec.createDeployment("write-deploy", NAMESPACE, 9090, "ClusterIP", "write-deploy-svc", " using CCT app selector");
        assertThat(client.getServiceList(NAMESPACE).split("\n")).contains("write-deploy-svc");
    }

    @Test
    public void runPodStepCreatesThePodWithTheGivenArguments() {
        DataTable arguments = DataTable.create(Arrays.asList(
                Collections.singletonList("-c"),
                Collections.singletonList("echo hello")));

        spec.runPod("spec-run-pod", NAMESPACE, "busybox:1.36", null, "Never", "default", null, "sh", arguments);

        assertThat(client.getPod("spec-run-pod", NAMESPACE)).isNotNull();
    }

    @Test
    public void runPodStepParsesTheEnvironmentVariableList() {
        DataTable arguments = DataTable.create(Collections.singletonList(Collections.singletonList("env")));

        spec.runPod("spec-run-pod-env", NAMESPACE, "busybox:1.36", "Always", "Never", "default",
                "LOG_LEVEL=DEBUG,MODE=test", "sh", arguments);

        assertThat(client.getPod("spec-run-pod-env", NAMESPACE)).isNotNull();
    }

    @Test
    public void createClusterRoleBindingStepCreatesTheBinding() {
        spec.createClusterRole("spec-crb-role", "pods", "get", null);

        spec.createClusterRoleBinding("spec-crb", "spec-crb-role", NAMESPACE + ":spec-sa");

        assertThat(client.getClusterRoleBinding("spec-crb").getRoleRef().getName()).isEqualTo("spec-crb-role");
    }

    @Test
    public void copyStepsSurfaceTheFailureWhenTheTransferCannotBeSetUp() {
        mockClient.pods().inNamespace(NAMESPACE)
                .resource(K8sFixtures.pod("copy-pod", NAMESPACE, Collections.emptyMap())).create();

        assertThatThrownBy(() -> spec.copyToRemoteFile("src/test/resources/k8s/configmap-value.txt",
                "/tmp/remote.txt", "copy-pod", NAMESPACE, null))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> spec.copyFromRemoteFile("/tmp/remote.txt", "target/local.txt", "copy-pod", NAMESPACE))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void setLocalPortForwardIgnoresAnUnknownResourceType() {
        // the switch has no branch for anything other than pod/service, so nothing is forwarded
        spec.setLocalPortForward(8080, 8080, "unknown", "whatever", NAMESPACE, null);
    }

    @Test
    public void portForwardBasedVaultStepsFailWhenTheServiceIsNotThere() {
        // every Vault step starts by forwarding the vault service in keos-core, outside its try block
        assertThatThrownBy(() -> spec.hasSecretVault("/secret/foo")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> spec.deleteSecretVault("/secret/foo")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> spec.readSecretVault("/secret/foo", "VAULT_OUT")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> spec.createSecretVault("/secret/foo",
                DataTable.create(Collections.singletonList(Arrays.asList("user", "admin")))))
                .isInstanceOf(Exception.class);
    }

    @Test
    public void updatePullPolicyAppChangesThePolicyOfTheContainer() {
        spec.createDeployment("policy-app", NAMESPACE, "stratio/app:1", null);

        spec.updatePullPolicyApp("policy-app", NAMESPACE, "policy-app", "Never");

        assertThat(client.getContainer(NAMESPACE, "policy-app", "policy-app").getImagePullPolicy()).isEqualTo("Never");
    }

    @Test
    public void updateAppEnvsAddsReplacesAndRemovesEnvironmentVariables() throws Exception {
        mockClient.apps().deployments().inNamespace(NAMESPACE)
                .resource(K8sFixtures.deployment("env-app", NAMESPACE, "img:1", "TO_REPLACE", "before")).create();

        DataTable modifications = DataTable.create(Arrays.asList(
                Arrays.asList("TO_REPLACE", "REPLACE", "after"),
                Arrays.asList("TO_ADD", "ADD", "added"),
                Arrays.asList("TO_REMOVE", "REMOVE", ""),
                Arrays.asList("TO_IGNORE", "UNKNOWN", "")));

        spec.updateAppEnvs("env-app", NAMESPACE, "main", modifications);

        assertThat(client.getContainer(NAMESPACE, "env-app", "main").getEnv())
                .extracting(EnvVar::getName, EnvVar::getValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("TO_REPLACE", "after"),
                        org.assertj.core.groups.Tuple.tuple("TO_ADD", "added"));
    }

    @Test
    public void applyConfigurationAppliesAManifestFile() throws Exception {
        spec.applyConfiguration("src/test/resources/k8s/configmap-to-apply.yaml", NAMESPACE);

        assertThat(client.getConfigMapKey("applied-from-file", NAMESPACE, "origin")).isEqualTo("file");
    }

    @Test
    public void applyConfigurationCustomResourceDefinitionAppliesTheManifest() throws Exception {
        spec.applyConfigurationCustomResourceDefinition("src/test/resources/k8s/pgcluster-to-apply.yaml",
                NAMESPACE, "v1", PLURAL, "PgCluster", CRD_NAME, "Namespaced", GROUP, null);

        assertThat(client.getCustomResource(CRD_NAME, NAMESPACE).split("\n")).contains("pg-from-file");
    }

    @Test
    public void applyConfigurationCustomResourceDefinitionSwallowsTheExpectedError() throws Exception {
        spec.applyConfigurationCustomResourceDefinition("src/test/resources/k8s/does-not-exist.yaml",
                NAMESPACE, "v1", PLURAL, "PgCluster", CRD_NAME, "Namespaced", GROUP, "does-not-exist.yaml");
    }

    @Test
    public void applyConfigurationCustomResourceDefinitionRethrowsWhenNoMessageIsExpected() {
        assertThatThrownBy(() -> spec.applyConfigurationCustomResourceDefinition("src/test/resources/k8s/does-not-exist.yaml",
                NAMESPACE, "v1", PLURAL, "PgCluster", CRD_NAME, "Namespaced", GROUP, null))
                .isInstanceOf(Exception.class);
    }

    @Test
    public void createNamespaceParsesTheLabelList() {
        spec.createNamespace("spec-ns-labels", "owner=bdt,tier=test");
        spec.createNamespace("spec-ns-plain", null);

        assertThat(mockClient.namespaces().withName("spec-ns-labels").get().getMetadata().getLabels())
                .containsEntry("owner", "bdt")
                .containsEntry("tier", "test");
        assertThat(mockClient.namespaces().withName("spec-ns-plain").get()).isNotNull();
    }

    @Test
    public void rbacStepsCreateTheirObjects() {
        spec.createServiceAccount("spec-sa", NAMESPACE);
        spec.createRole("spec-role", NAMESPACE, "pods", "get,list", "");
        spec.createClusterRole("spec-cluster-role", "pods", "get", null);
        spec.createRoleBinding("spec-rb", "spec-role", NAMESPACE + ":spec-sa");

        assertThat(client.getServiceAccount("spec-sa", NAMESPACE)).isNotNull();
        assertThat(client.getRole("spec-role", NAMESPACE)).isNotNull();
        assertThat(client.getClusterRole("spec-cluster-role")).isNotNull();
        assertThat(client.getRoleBinding("spec-rb", NAMESPACE)).isNotNull();
    }

    @Test
    public void configMapStepsCreateAndModifyEntries() {
        spec.createOrReplaceConfigMap("spec-cm", NAMESPACE, "one", "1");
        spec.addOrModifyConfigmap("two", "2", "spec-cm", NAMESPACE);

        assertThat(client.getConfigMap("spec-cm", NAMESPACE).getData())
                .containsEntry("one", "1")
                .containsEntry("two", "2");
    }

    @Test
    public void configMapStepsAcceptDataTables() {
        spec.createOrReplaceConfigMap("spec-cm-table", NAMESPACE,
                DataTable.create(Arrays.asList(Arrays.asList("a", "1"), Arrays.asList("b", "2"))));
        spec.addOrModifyMultipleConfigmap("spec-cm-table", NAMESPACE,
                DataTable.create(Collections.singletonList(Arrays.asList("c", "3"))));

        assertThat(client.getConfigMap("spec-cm-table", NAMESPACE).getData())
                .containsEntry("a", "1")
                .containsEntry("b", "2")
                .containsEntry("c", "3");
    }

    @Test
    public void configMapStepsReadValuesFromFiles() {
        spec.createOrReplaceConfigMapValueFile("spec-cm-file", NAMESPACE, "from-file", "k8s/configmap-value.txt");
        spec.addOrModifyConfigmapFile("added-from-file", "k8s/configmap-value.txt", "spec-cm-file", NAMESPACE);

        assertThat(client.getConfigMapKey("spec-cm-file", NAMESPACE, "from-file")).contains("value-loaded-from-file");
        assertThat(client.getConfigMapKey("spec-cm-file", NAMESPACE, "added-from-file")).contains("value-loaded-from-file");
    }

    @Test
    public void createResourceQuotaStepCreatesTheQuota() {
        spec.createResourceQuota("spec-rq", NAMESPACE, "1", "512Mi", "3");

        assertThat(client.getResourceQuota("spec-rq", NAMESPACE).getSpec().getHard()).containsKeys("cpu", "memory", "pods");
    }

    @Test
    public void horizontalAutoscalerStepsUpdateTheReplicaBounds() {
        mockClient.autoscaling().v1().horizontalPodAutoscalers().inNamespace(NAMESPACE)
                .resource(K8sFixtures.horizontalPodAutoscaler("spec-hpa", NAMESPACE, 1, 2)).create();

        spec.setMaxReplicas(6, "spec-hpa", NAMESPACE);
        assertThat(hpaMax()).isEqualTo(6);

        spec.setMinMaxReplicas(3, 8, "spec-hpa", NAMESPACE);
        assertThat(hpaMax()).isEqualTo(8);
        assertThat(mockClient.autoscaling().v1().horizontalPodAutoscalers().inNamespace(NAMESPACE)
                .withName("spec-hpa").get().getSpec().getMinReplicas()).isEqualTo(3);
    }

    private int hpaMax() {
        return mockClient.autoscaling().v1().horizontalPodAutoscalers().inNamespace(NAMESPACE)
                .withName("spec-hpa").get().getSpec().getMaxReplicas();
    }

    @Test
    public void executeCRDPatchAppliesASingleFieldPatch() throws Exception {
        spec.executeCRDPatch(CRD_NAME, "pg-write", NAMESPACE, "/spec/replicas", "4", "integer", null, null);

        assertThat(specReplicas("pg-write")).isEqualTo(4);
    }

    @Test
    public void executeCRDPatchAppliesADataTablePatch() throws Exception {
        mockClient.genericKubernetesResources(context).inNamespace(NAMESPACE).resource(pgCluster("pg-write-table")).create();

        spec.executeCRDPatch(CRD_NAME, "pg-write-table", NAMESPACE, null, null,
                DataTable.create(Collections.singletonList(Arrays.asList("/spec/replicas", "UPDATE", "7", "integer"))));

        assertThat(specReplicas("pg-write-table")).isEqualTo(7);
    }

    @Test
    public void executeCRDPatchFailsWhenTheExitStatusIsNotTheExpectedOne() {
        assertThatThrownBy(() -> spec.executeCRDPatch(CRD_NAME, "missing-item", NAMESPACE, "/spec/replicas", "4", "integer", "0", null))
                .isInstanceOf(AssertionError.class);
    }

    @SuppressWarnings("unchecked")
    private int specReplicas(String name) {
        GenericKubernetesResource resource = mockClient.genericKubernetesResources(context)
                .inNamespace(NAMESPACE).withName(name).get();
        return (Integer) ((Map<String, Object>) resource.getAdditionalProperties().get("spec")).get("replicas");
    }

    @Test
    public void executePatchResizesAPersistentVolumeClaim() throws Exception {
        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE)
                .resource(K8sFixtures.pvc("spec-pvc", NAMESPACE, "1Gi")).create();

        spec.executePatch("spec-pvc", NAMESPACE, "/spec/resources/requests/storage", "3Gi", null);

        assertThat(client.getPersistentVolumeClaims("spec-pvc", NAMESPACE)
                .getSpec().getResources().getRequests().get("storage").toString()).isEqualTo("3Gi");
    }

    @Test
    public void deleteResourceCoversEveryResourceType() throws Exception {
        mockClient.pods().inNamespace(NAMESPACE).resource(K8sFixtures.pod("d-pod", NAMESPACE, Collections.emptyMap())).create();
        mockClient.apps().deployments().inNamespace(NAMESPACE)
                .resource(K8sFixtures.deployment("d-deploy", NAMESPACE, "img:1", "K", "V")).create();
        mockClient.services().inNamespace(NAMESPACE).resource(K8sFixtures.service("d-svc", NAMESPACE, Collections.emptyMap())).create();
        mockClient.apps().statefulSets().inNamespace(NAMESPACE)
                .resource(K8sFixtures.statefulSet("d-sts", NAMESPACE, Collections.emptyMap())).create();
        mockClient.batch().v1().jobs().inNamespace(NAMESPACE).resource(K8sFixtures.job("d-job", NAMESPACE)).create();
        mockClient.network().v1().ingresses().inNamespace(NAMESPACE)
                .resource(K8sFixtures.ingress("d-ing", NAMESPACE, Collections.emptyMap())).create();
        mockClient.configMaps().inNamespace(NAMESPACE)
                .resource(K8sFixtures.configMap("d-cm", NAMESPACE, Collections.singletonMap("k", "v"))).create();
        mockClient.secrets().inNamespace(NAMESPACE).resource(K8sFixtures.secret("d-secret", NAMESPACE)).create();
        mockClient.resourceQuotas().inNamespace(NAMESPACE).resource(K8sFixtures.resourceQuota("d-rq", NAMESPACE)).create();
        mockClient.serviceAccounts().inNamespace(NAMESPACE).resource(K8sFixtures.serviceAccount("d-sa", NAMESPACE)).create();
        mockClient.rbac().roles().inNamespace(NAMESPACE).resource(K8sFixtures.role("d-role", NAMESPACE)).create();
        mockClient.rbac().roleBindings().inNamespace(NAMESPACE).resource(K8sFixtures.roleBinding("d-rb", NAMESPACE)).create();
        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE).resource(K8sFixtures.pvc("d-pvc", NAMESPACE, "1Gi")).create();
        mockClient.network().networkPolicies().inNamespace(NAMESPACE).resource(K8sFixtures.networkPolicy("d-np", NAMESPACE)).create();
        mockClient.apiextensions().v1().customResourceDefinitions()
                .resource(K8sFixtures.customResourceDefinition("temporals", "bdt.stratio.com", "Temporal", "v1")).create();

        spec.deleteResource("pod", "d-pod", NAMESPACE);
        spec.deleteResource("deployment", "d-deploy", NAMESPACE);
        spec.deleteResource("service", "d-svc", NAMESPACE);
        spec.deleteResource("statefulset", "d-sts", NAMESPACE);
        spec.deleteResource("job", "d-job", NAMESPACE);
        spec.deleteResource("ingress", "d-ing", NAMESPACE);
        spec.deleteResource("configmap", "d-cm", NAMESPACE);
        spec.deleteResource("secret", "d-secret", NAMESPACE);
        spec.deleteResource("resourcequota", "d-rq", NAMESPACE);
        spec.deleteResource("serviceaccount", "d-sa", NAMESPACE);
        spec.deleteResource("role", "d-role", NAMESPACE);
        spec.deleteResource("rolebinding", "d-rb", NAMESPACE);
        spec.deleteResource("pvc", "d-pvc", NAMESPACE);
        spec.deleteResource("networkpolicy", "d-np", NAMESPACE);
        spec.deleteResource("crd", "temporals.bdt.stratio.com", null);

        assertThat(client.getPod("d-pod", NAMESPACE)).isNull();
        assertThat(client.getDeployment("d-deploy", NAMESPACE)).isNull();
        assertThat(client.getStateFulSet("d-sts", NAMESPACE)).isNull();
        assertThat(client.getJob("d-job", NAMESPACE)).isNull();
        assertThat(client.getIngress("d-ing", NAMESPACE)).isNull();
        assertThat(client.getConfigMap("d-cm", NAMESPACE)).isNull();
        assertThat(client.getSecret("d-secret", NAMESPACE)).isNull();
        assertThat(client.getResourceQuota("d-rq", NAMESPACE)).isNull();
        assertThat(client.getServiceAccount("d-sa", NAMESPACE)).isNull();
        assertThat(client.getRole("d-role", NAMESPACE)).isNull();
        assertThat(client.getRoleBinding("d-rb", NAMESPACE)).isNull();
        assertThat(client.getPersistentVolumeClaims("d-pvc", NAMESPACE)).isNull();
        assertThat(client.getNetworkPolicy("d-np", NAMESPACE)).isNull();
        assertThat(client.getCustomResourceDefinitionList().split("\n")).doesNotContain("temporals.bdt.stratio.com");
    }

    @Test
    public void deleteResourceRequiresANamespaceForEverythingButCrds() {
        assertThatThrownBy(() -> spec.deleteResource("pod", "d-pod", null))
                .isInstanceOf(Exception.class)
                .hasMessage("Namespace must be defined for delete pod");
    }

    @Test
    public void deleteResourceIgnoresAnUnknownType() throws Exception {
        spec.deleteResource("unknown", "whatever", NAMESPACE);
    }

    @Test
    public void deletePersistentVolumeClaimsStepRemovesClaimsByLabel() throws Exception {
        io.fabric8.kubernetes.api.model.PersistentVolumeClaim pvc = K8sFixtures.pvc("labelled-pvc", NAMESPACE, "1Gi");
        pvc.getMetadata().setLabels(Collections.singletonMap("group", "spec"));
        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE).resource(pvc).create();

        spec.deletePersistentVolumeClaims("group=spec", NAMESPACE);

        assertThat(client.getPersistentVolumeClaims("labelled-pvc", NAMESPACE)).isNull();
    }

    @Test
    public void deleteCustomResourceStepRemovesTheItem() throws Exception {
        mockClient.genericKubernetesResources(context).inNamespace(NAMESPACE).resource(pgCluster("pg-to-delete")).create();

        spec.deleteCustomResource(CRD_NAME, "pg-to-delete", NAMESPACE);

        assertThat(client.getCustomResource(CRD_NAME, NAMESPACE).split("\n")).doesNotContain("pg-to-delete");
    }

    @Test
    public void restartResourceIgnoresAnUnknownType() throws Exception {
        spec.restartResource("unknown", "whatever", NAMESPACE);
    }

    @Test
    public void closePortForwardIsANoOpWhenNothingIsForwarded() throws Exception {
        spec.closePortForward();
        spec.closePortForward("unknown-id");
    }
}
