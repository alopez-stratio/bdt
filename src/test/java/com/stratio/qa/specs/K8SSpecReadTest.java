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
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the read-only steps of {@link K8SSpec} against the fabric8 mock API server.
 *
 * The steps are invoked directly rather than through Gherkin: the regular expressions are covered by
 * the {@code K8SSpecIT} feature, while these tests focus on the switch statements and the
 * environment-variable / file output branches of every step.
 */
public class K8SSpecReadTest extends K8sMockServerTestBase {

    private static final String GROUP = "postgres.stratio.com";

    private static final String PLURAL = "pgclusters";

    private static final String CRD_NAME = PLURAL + "." + GROUP;

    private K8SSpec spec;

    private CommonG commonG;

    private CustomResourceDefinitionContext context;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "startMockServer")
    public void buildSpec() {
        // CommonG resolves its logger from ThreadProperty("class"), the same way BaseGTest sets it up
        ThreadProperty.set("class", getClass().getCanonicalName());
        commonG = new CommonG();
        commonG.initKubernetesClient();
        spec = new K8SSpec(commonG);
    }

    @Override
    protected void seed() {
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "bdt");

        context = new CustomResourceDefinitionContext.Builder()
                .withGroup(GROUP).withVersion("v1").withPlural(PLURAL).withKind("PgCluster")
                .withName(CRD_NAME).withScope("Namespaced").build();
        server.expectCustomResource(context);

        mockClient.namespaces().resource(K8sFixtures.namespace(NAMESPACE)).create();
        mockClient.pods().inNamespace(NAMESPACE).resource(readyPod("pod-a", labels)).create();
        mockClient.services().inNamespace(NAMESPACE).resource(K8sFixtures.service("svc-a", NAMESPACE, labels)).create();
        mockClient.apps().deployments().inNamespace(NAMESPACE).resource(readyDeployment(labels)).create();
        mockClient.apps().replicaSets().inNamespace(NAMESPACE).resource(K8sFixtures.replicaSet("rs-a", NAMESPACE, labels)).create();
        mockClient.apps().statefulSets().inNamespace(NAMESPACE).resource(K8sFixtures.statefulSet("sts-a", NAMESPACE, labels)).create();
        mockClient.configMaps().inNamespace(NAMESPACE)
                .resource(K8sFixtures.configMap("cm-a", NAMESPACE, Collections.singletonMap("key", "value"))).create();
        mockClient.secrets().inNamespace(NAMESPACE).resource(K8sFixtures.secret("secret-a", NAMESPACE)).create();
        io.fabric8.kubernetes.api.model.ServiceAccount serviceAccount = K8sFixtures.serviceAccount("sa-a", NAMESPACE);
        serviceAccount.getMetadata().setLabels(new HashMap<>(labels));
        mockClient.serviceAccounts().inNamespace(NAMESPACE).resource(serviceAccount).create();
        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE).resource(K8sFixtures.pvc("pvc-a", NAMESPACE, "1Gi")).create();
        mockClient.rbac().roles().inNamespace(NAMESPACE).resource(K8sFixtures.role("role-a", NAMESPACE)).create();
        mockClient.rbac().roleBindings().inNamespace(NAMESPACE).resource(K8sFixtures.roleBinding("rb-a", NAMESPACE)).create();
        mockClient.rbac().clusterRoles().resource(K8sFixtures.clusterRole("cr-a")).create();
        mockClient.rbac().clusterRoleBindings().resource(K8sFixtures.clusterRoleBinding("crb-a")).create();
        mockClient.network().v1().ingresses().inNamespace(NAMESPACE).resource(K8sFixtures.ingress("ing-a", NAMESPACE, labels)).create();
        mockClient.network().networkPolicies().inNamespace(NAMESPACE).resource(K8sFixtures.networkPolicy("np-a", NAMESPACE)).create();
        mockClient.batch().v1().jobs().inNamespace(NAMESPACE).resource(K8sFixtures.job("job-a", NAMESPACE)).create();
        mockClient.resourceQuotas().inNamespace(NAMESPACE).resource(K8sFixtures.resourceQuota("rq-a", NAMESPACE)).create();
        mockClient.storage().v1().storageClasses().resource(K8sFixtures.storageClass("sc-default", true)).create();
        mockClient.apiextensions().v1().customResourceDefinitions()
                .resource(K8sFixtures.customResourceDefinition(PLURAL, GROUP, "PgCluster", "v1")).create();
        mockClient.v1().events().inNamespace(NAMESPACE)
                .resource(K8sFixtures.event("event-a", NAMESPACE, "Back-off restarting failed container", "BackOff", "Pod", "pod-a")).create();
        mockClient.genericKubernetesResources(context).inNamespace(NAMESPACE).resource(pgCluster("pg-one")).create();
    }

    private io.fabric8.kubernetes.api.model.Pod readyPod(String name, Map<String, String> labels) {
        return new PodBuilder(K8sFixtures.pod(name, NAMESPACE, labels))
                .editStatus()
                .addNewContainerStatus()
                .withName("main").withReady(true).withImage("busybox:1.36").withImageID("id").withRestartCount(0)
                .endContainerStatus()
                .endStatus()
                .build();
    }

    private io.fabric8.kubernetes.api.model.apps.Deployment readyDeployment(Map<String, String> labels) {
        io.fabric8.kubernetes.api.model.apps.Deployment deployment =
                K8sFixtures.deployment("deploy-a", NAMESPACE, "stratio/app:1.2.3", "LOG_LEVEL", "DEBUG");
        deployment.getMetadata().setLabels(new HashMap<>(labels));
        return new io.fabric8.kubernetes.api.model.apps.DeploymentBuilder(deployment)
                .withNewStatus().withReadyReplicas(1).withReplicas(1).endStatus()
                .build();
    }

    private GenericKubernetesResource pgCluster(String name) {
        Map<String, Object> globalStatus = new LinkedHashMap<>();
        globalStatus.put("status", "READY");
        globalStatus.put("description", "Cluster is up");
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("readyInstances", "2/2");
        status.put("globalStatus", globalStatus);
        Map<String, Object> additionalProperties = new LinkedHashMap<>();
        additionalProperties.put("spec", new LinkedHashMap<>(Collections.singletonMap("replicas", 2)));
        additionalProperties.put("status", status);

        return new GenericKubernetesResourceBuilder()
                .withApiVersion(GROUP + "/v1")
                .withKind("PgCluster")
                .withNewMetadata().withName(name).withNamespace(NAMESPACE).endMetadata()
                .withAdditionalProperties(additionalProperties)
                .build();
    }

    private String readTargetFile(String fileName) throws Exception {
        Path path = Paths.get(System.getProperty("user.dir"), "target", "test-classes", fileName);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void getListCoversEveryResourceTypeAndSavesTheResultInAVariable() throws Exception {
        assertListStep("pods", "pod-a");
        assertListStep("configmaps", "cm-a");
        assertListStep("serviceaccounts", "sa-a");
        assertListStep("replicasets", "rs-a");
        assertListStep("secrets", "secret-a");
        assertListStep("statefulsets", "sts-a");
        assertListStep("roles", "role-a");
        assertListStep("rolebindings", "rb-a");
        assertListStep("deployments", "deploy-a");
        assertListStep("services", "svc-a");
        assertListStep("ingress", "ing-a");
        assertListStep("persistentVolumeClaims", "pvc-a");
    }

    private void assertListStep(String type, String expected) throws Exception {
        spec.getList(type, NAMESPACE, "LIST_" + type, null);
        assertThat(ThreadProperty.get("LIST_" + type)).isEqualTo(expected);
    }

    @Test
    public void getListCoversTheClusterScopedResourceTypes() throws Exception {
        spec.getList("clusterroles", null, "CLUSTER_ROLES", null);
        spec.getList("clusterrolebindings", null, "CLUSTER_ROLE_BINDINGS", null);
        spec.getList("customresourcedefinitions", null, "CRDS", null);
        spec.getList("storageClasses", null, "STORAGE_CLASSES", null);

        assertThat(ThreadProperty.get("CLUSTER_ROLES").split("\n")).contains("cr-a");
        assertThat(ThreadProperty.get("CLUSTER_ROLE_BINDINGS").split("\n")).contains("crb-a");
        assertThat(ThreadProperty.get("CRDS").split("\n")).contains(CRD_NAME);
        assertThat(ThreadProperty.get("STORAGE_CLASSES").split("\n")).contains("sc-default");
    }

    @Test
    public void getListWritesTheResultToAFile() throws Exception {
        spec.getList("pods", NAMESPACE, null, "pods-list.txt");

        assertThat(readTargetFile("pods-list.txt")).isEqualTo("pod-a");
    }

    @Test
    public void getListIgnoresAnUnknownType() throws Exception {
        spec.getList("unknown", NAMESPACE, null, null);
    }

    /**
     * The switch has no branch for an unknown type, so the response stays null and
     * {@code ThreadProperty.set} rejects it. The step regular expression is the only guard against this.
     */
    @Test
    public void getListWithAnUnknownTypeFailsWhenAVariableIsRequested() {
        assertThatThrownBy(() -> spec.getList("unknown", NAMESPACE, "UNKNOWN_LIST", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void getAllNamespacesSavesTheNamespaceList() {
        spec.getAllNamespaces("ALL_NAMESPACES");

        assertThat(ThreadProperty.get("ALL_NAMESPACES").split("\n")).contains(NAMESPACE);
    }

    @Test
    public void checkEventNamespacePassesWhenTheEventIsPresent() {
        spec.checkEventNamespace(null, "Back-off restarting", NAMESPACE, "Pod", "pod-a", "BackOff");
    }

    @Test
    public void checkEventNamespaceFailsWhenTheEventIsMissing() {
        assertThatThrownBy(() -> spec.checkEventNamespace(null, "no such event", NAMESPACE, null, null, null))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("There aren't event that contains the message no such event");
    }

    @Test
    public void getDeploymentEnvVarSavesTheValue() throws Exception {
        spec.getDeploymentEnvVar("LOG_LEVEL", "deploy-a", NAMESPACE, "DEPLOY_LOG_LEVEL");

        assertThat(ThreadProperty.get("DEPLOY_LOG_LEVEL")).isEqualTo("DEBUG");
    }

    @Test
    public void describeResourceCoversEveryResourceType() throws Exception {
        assertDescribeStep("pod", "pod-a", "name: \"pod-a\"");
        assertDescribeStep("service", "svc-a", "name: \"svc-a\"");
        assertDescribeStep("deployment", "deploy-a", "name: \"deploy-a\"");
        assertDescribeStep("configmap", "cm-a", "{key=value}");
        assertDescribeStep("replicaset", "rs-a", "name: \"rs-a\"");
        assertDescribeStep("serviceaccount", "sa-a", "name: \"sa-a\"");
        assertDescribeStep("secret", "secret-a", "name: \"secret-a\"");
        assertDescribeStep("statefulset", "sts-a", "name: \"sts-a\"");
        assertDescribeStep("role", "role-a", "name: \"role-a\"");
        assertDescribeStep("rolebinding", "rb-a", "name: \"rb-a\"");
        assertDescribeStep("ingress", "ing-a", "name: \"ing-a\"");
        assertDescribeStep("persistentVolumeClaims", "pvc-a", "name: \"pvc-a\"");
        assertDescribeStep("job", "job-a", "name: \"job-a\"");
        assertDescribeStep("resourcequota", "rq-a", "name: \"rq-a\"");
        assertDescribeStep("networkpolicy", "np-a", "name: \"np-a\"");
    }

    private void assertDescribeStep(String type, String name, String expectedFragment) throws Exception {
        spec.describeResource(type, name, NAMESPACE, null, "DESCRIBE_" + type, null);
        assertThat(ThreadProperty.get("DESCRIBE_" + type)).contains(expectedFragment);
    }

    @Test
    public void describeResourceCoversTheClusterScopedTypes() throws Exception {
        spec.describeResource("clusterrole", "cr-a", null, null, "DESCRIBE_CR", null);
        spec.describeResource("clusterrolebinding", "crb-a", null, null, "DESCRIBE_CRB", null);

        assertThat(ThreadProperty.get("DESCRIBE_CR")).contains("name: \"cr-a\"");
        assertThat(ThreadProperty.get("DESCRIBE_CRB")).contains("name: \"crb-a\"");
    }

    @Test
    public void describeResourceConvertsToJsonAndWritesToFile() throws Exception {
        spec.describeResource("pod", "pod-a", NAMESPACE, "json", "DESCRIBE_JSON", "pod-describe.json");

        assertThat(ThreadProperty.get("DESCRIBE_JSON")).contains("\"name\" : \"pod-a\"");
        assertThat(readTargetFile("pod-describe.json")).contains("\"name\" : \"pod-a\"");
    }

    @Test
    public void describeResourceFailsForAnUnknownType() {
        assertThatThrownBy(() -> spec.describeResource("unknown", "pod-a", NAMESPACE, null, null, null))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Error obtaining unknown information");
    }

    @Test
    public void describeCustomResourceRendersYamlByDefault() throws Exception {
        spec.describeCustomResource(CRD_NAME, "pg-one", NAMESPACE, null, "pg-one.yaml");

        assertThat(readTargetFile("pg-one.yaml")).contains("pg-one");
    }

    @Test
    public void describeCustomResourceRendersEnrichedJson() throws Exception {
        spec.describeCustomResource(CRD_NAME, "pg-one", NAMESPACE, "enrichedJson", "pg-one-enriched.json");

        org.json.JSONObject enriched = new org.json.JSONObject(readTargetFile("pg-one-enriched.json"));
        // spec and status are lifted out of additionalProperties, and the wrapper keys are dropped
        assertThat(enriched.getJSONObject("spec").getInt("replicas")).isEqualTo(2);
        assertThat(enriched.getJSONObject("status").getString("readyInstances")).isEqualTo("2/2");
        assertThat(enriched.has("additionalProperties")).isFalse();
        assertThat(enriched.has("plural")).isFalse();
        assertThat(enriched.has("singular")).isFalse();
    }

    @Test
    public void describeCustomResourceFailsWhenTheItemDoesNotExist() {
        assertThatThrownBy(() -> spec.describeCustomResource(CRD_NAME, "missing", NAMESPACE, null, null))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Error obtaining");
    }

    @Test
    public void getCustomResourcesSavesTheItemNames() throws Exception {
        spec.getCustomResources(CRD_NAME, NAMESPACE, "PG_CLUSTERS");

        assertThat(ThreadProperty.get("PG_CLUSTERS").split("\n")).contains("pg-one");
    }

    @Test
    public void getPodsLabelSelectorCoversEveryResourceType() throws Exception {
        assertLabelSelectorStep("pods", "pod-a");
        assertLabelSelectorStep("deployments", "deploy-a");
        assertLabelSelectorStep("replicasets", "rs-a");
        assertLabelSelectorStep("services", "svc-a");
        assertLabelSelectorStep("statefulsets", "sts-a");
    }

    private void assertLabelSelectorStep(String type, String expected) throws Exception {
        spec.getPodsLabelSelector(type, "app=bdt", NAMESPACE, "LABELS_" + type, null);
        assertThat(ThreadProperty.get("LABELS_" + type)).isEqualTo(expected);
    }

    @Test
    public void getPodsLabelSelectorWritesTheResultToAFile() throws Exception {
        spec.getPodsLabelSelector("pods", "app=bdt", NAMESPACE, null, "pods-by-label.txt");

        assertThat(readTargetFile("pods-by-label.txt")).isEqualTo("pod-a");
    }

    /**
     * The step regular expression accepts {@code serviceacounts} (a single "c") but the switch matches
     * {@code serviceaccounts}, so the step silently returns an empty result for that alternative.
     */
    @Test
    public void getPodsLabelSelectorReturnsEmptyForTheMisspelledServiceAccountAlternative() throws Exception {
        spec.getPodsLabelSelector("serviceacounts", "app=bdt", NAMESPACE, "LABELS_SA_TYPO", null);
        spec.getPodsLabelSelector("serviceaccounts", "app=bdt", NAMESPACE, "LABELS_SA", null);

        assertThat(ThreadProperty.get("LABELS_SA_TYPO")).isEmpty();
        assertThat(ThreadProperty.get("LABELS_SA")).isEqualTo("sa-a");
    }

    @Test
    public void getPodsFieldSelectorSavesTheFilteredPods() {
        spec.getPodsFieldSelector("metadata.name=pod-a", NAMESPACE, "PODS_BY_FIELD");

        assertThat(ThreadProperty.get("PODS_BY_FIELD")).isEqualTo("pod-a");
    }

    @Test
    public void getK8sObjectByOwnerReferenceNameCoversEveryResourceType() throws Exception {
        for (String type : new String[]{"pods", "deployments", "replicasets", "statefulsets", "configmaps", "secrets", "persistentVolumeClaims"}) {
            spec.getK8sObjectByOwnerReferenceName(type, "no-such-owner", NAMESPACE, "OWNER_" + type, null);
            assertThat(ThreadProperty.get("OWNER_" + type)).isEmpty();
        }
    }

    @Test
    public void getK8sObjectByOwnerReferenceNameWritesTheResultToAFile() throws Exception {
        spec.getK8sObjectByOwnerReferenceName("pods", "no-such-owner", NAMESPACE, null, "owner-pods.txt");

        assertThat(readTargetFile("owner-pods.txt")).isEmpty();
    }

    @Test
    public void getKeyFromConfigMapSavesTheValueAndWritesTheFile() throws Exception {
        spec.getKeyFromConfigMap("key", "cm-a", NAMESPACE, "CM_KEY", "cm-key.txt");

        assertThat(ThreadProperty.get("CM_KEY")).isEqualTo("value");
        assertThat(readTargetFile("cm-key.txt")).isEqualTo("value");
    }

    @Test
    public void getKeyFromConfigMapFailsWhenTheKeyIsMissing() {
        assertThatThrownBy(() -> spec.getKeyFromConfigMap("missing", "cm-a", NAMESPACE, null, null))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Key doesn't exist in configmap");
    }

    @Test
    public void getDefaultStorageClassSavesTheAnnotatedClass() throws Exception {
        spec.getDefaultStorageClass("DEFAULT_SC");

        assertThat(ThreadProperty.get("DEFAULT_SC")).isEqualTo("sc-default");
    }

    @Test
    public void assertPodStatusSucceedsForARunningReadyPod() throws Exception {
        spec.assertPodStatus(1, 1, "pod-a", NAMESPACE, "running", "ready");
        spec.assertPodStatus(1, 1, "pod-a", NAMESPACE, "running", null);
    }

    @Test
    public void assertPodStatusFailsWhenTheStatusDoesNotMatch() {
        assertThatThrownBy(() -> spec.assertPodStatus(0, 1, "pod-a", NAMESPACE, "failed", null))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void assertPodStatusWithLabelFilterCountsTheMatchingPods() throws Exception {
        spec.assertPodStatusWithLabelFilter(1, 1, 1, "app=bdt", NAMESPACE, "running", "ready");
        spec.assertPodStatusWithLabelFilter(1, 1, 0, "app=nothing", NAMESPACE, "running", null);
    }

    @Test
    public void assertPodStatusWithLabelFilterFailsWhenTheCountDoesNotMatch() {
        assertThatThrownBy(() -> spec.assertPodStatusWithLabelFilter(0, 1, 5, "app=bdt", NAMESPACE, "running", null))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void assertDeploymentStatusComparesReadyReplicas() throws Exception {
        spec.assertDeploymentStatus(1, 1, "deploy-a", NAMESPACE, 1);
    }

    @Test
    public void assertDeploymentStatusFailsWhenReplicasDoNotMatch() {
        assertThatThrownBy(() -> spec.assertDeploymentStatus(0, 1, "deploy-a", NAMESPACE, 3))
                .isInstanceOf(AssertionError.class);
    }

    /**
     * Unlike {@code assertDeploymentStatus}, the statefulset variant dereferences
     * {@code getStatus().getReadyReplicas()} without a null guard, so an unpopulated status surfaces as
     * a NullPointerException rather than an assertion failure.
     */
    @Test
    public void assertStatefulsetStatusThrowsWhenTheStatusIsNotPopulated() {
        assertThatThrownBy(() -> spec.assertStatefulsetStatus(0, 1, "sts-a", NAMESPACE, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void assertCustomResourceStatusChecksReadyReplicas() throws Exception {
        spec.assertCustomResourceStatus(1, 1, CRD_NAME, "pg-one", NAMESPACE, 2);
    }

    @Test
    public void assertCustomResourceStatusChecksGlobalStatusAndDescription() throws Exception {
        spec.assertCustomResourceStatus(1, 1, CRD_NAME, "pg-one", NAMESPACE, "READY", "Cluster is up");
        spec.assertCustomResourceStatus(1, 1, CRD_NAME, "pg-one", NAMESPACE, "READY", null);
    }

    @Test
    public void assertCustomResourceStatusFailsWhenTheGlobalStatusDiffers() {
        assertThatThrownBy(() -> spec.assertCustomResourceStatus(0, 1, CRD_NAME, "pg-one", NAMESPACE, "NOT_READY", null))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void logIntoScenarioDoesNotFailWithoutAScenario() {
        spec.logIntoScenario("a message with no scenario attached");
    }
}
