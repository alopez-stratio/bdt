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
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the custom resource surface of {@link KubernetesClient}: reading the CRD definition, listing
 * and describing its items, and the {@code patchCRD} / {@code patchCRDMultipleFields} path walkers.
 */
public class KubernetesClientCustomResourceTest extends K8sMockServerTestBase {

    /** Mixed mode: everything is served by the CRUD store except the responses scripted per test. */
    @Override
    protected boolean crudMode() {
        return false;
    }


    private static final String GROUP = "postgres.stratio.com";

    private static final String PLURAL = "pgclusters";

    private static final String VERSION = "v1";

    private static final String CRD_NAME = PLURAL + "." + GROUP;

    private static final String CR_FILE = "src/test/resources/k8s/pgcluster-to-apply.yaml";

    private CustomResourceDefinitionContext context;

    @Override
    protected void seed() {
        context = new CustomResourceDefinitionContext.Builder()
                .withGroup(GROUP)
                .withVersion(VERSION)
                .withPlural(PLURAL)
                .withKind("PgCluster")
                .withName(CRD_NAME)
                .withScope("Namespaced")
                .build();
        server.expectCustomResource(context);

        mockClient.apiextensions().v1().customResourceDefinitions()
                .resource(K8sFixtures.customResourceDefinition(PLURAL, GROUP, "PgCluster", VERSION)).create();

        createItem("pg-one", 2, "2/2", "READY", "Cluster is up");
        createItem("pg-two", 1, "0/1", "NOT_READY", "Still starting");
    }

    private void createItem(String name, int replicas, String readyInstances, String status, String description) {
        Map<String, Object> server0 = new LinkedHashMap<>();
        server0.put("port", 1);
        Map<String, Object> server1 = new LinkedHashMap<>();
        server1.put("port", 2);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("replicas", replicas);
        spec.put("nodes", new ArrayList<>(Arrays.asList("node-a", "node-b")));
        spec.put("servers", new ArrayList<>(Arrays.asList(server0, server1)));

        Map<String, Object> globalStatus = new LinkedHashMap<>();
        globalStatus.put("status", status);
        globalStatus.put("description", description);

        Map<String, Object> statusMap = new LinkedHashMap<>();
        statusMap.put("readyInstances", readyInstances);
        statusMap.put("globalStatus", globalStatus);

        Map<String, Object> additionalProperties = new LinkedHashMap<>();
        additionalProperties.put("spec", spec);
        additionalProperties.put("status", statusMap);

        GenericKubernetesResource resource = new GenericKubernetesResourceBuilder()
                .withApiVersion(GROUP + "/" + VERSION)
                .withKind("PgCluster")
                .withNewMetadata()
                .withName(name)
                .withNamespace(NAMESPACE)
                .withLabels(new HashMap<>(Collections.singletonMap("existing", "label")))
                .withAnnotations(new HashMap<>(Collections.singletonMap("existing", "annotation")))
                .endMetadata()
                .withAdditionalProperties(additionalProperties)
                .build();

        mockClient.genericKubernetesResources(context).inNamespace(NAMESPACE).resource(resource).create();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> specOf(String name) {
        GenericKubernetesResource resource = mockClient.genericKubernetesResources(context)
                .inNamespace(NAMESPACE).withName(name).get();
        return (Map<String, Object>) resource.getAdditionalProperties().get("spec");
    }

    @Test
    public void getCustomResourceListsTheItemNames() throws Exception {
        assertThat(client.getCustomResource(CRD_NAME, NAMESPACE).split("\n"))
                .containsExactlyInAnyOrder("pg-one", "pg-two");
    }

    @Test
    public void describeCustomResourceJsonReturnsTheMatchingItemAndNullOtherwise() {
        assertThat(client.describeCustomResourceJson(CRD_NAME, "pg-one", NAMESPACE)).contains("pg-one");
        assertThat(client.describeCustomResourceJson(CRD_NAME, "does-not-exist", NAMESPACE)).isNull();
    }

    @Test
    public void getReadyReplicasCustomResourceReadsTheNumeratorOfReadyInstances() throws Exception {
        assertThat(client.getReadyReplicasCustomResource(CRD_NAME, "pg-one", NAMESPACE)).isEqualTo(2);
        assertThat(client.getReadyReplicasCustomResource(CRD_NAME, "pg-two", NAMESPACE)).isZero();
        assertThat(client.getReadyReplicasCustomResource(CRD_NAME, "does-not-exist", NAMESPACE)).isZero();
    }

    @Test
    public void globalStatusAndDescriptionAreReadFromTheStatusBlock() throws Exception {
        assertThat(client.getGlobalStatusCustomResource(CRD_NAME, "pg-one", NAMESPACE)).isEqualTo("READY");
        assertThat(client.getGlobalStatusDescriptionCustomResource(CRD_NAME, "pg-one", NAMESPACE)).isEqualTo("Cluster is up");
        assertThat(client.getGlobalStatusCustomResource(CRD_NAME, "does-not-exist", NAMESPACE)).isEmpty();
        assertThat(client.getGlobalStatusDescriptionCustomResource(CRD_NAME, "does-not-exist", NAMESPACE)).isEmpty();
    }

    @Test
    public void createOrReplaceCustomResourceAppliesAManifestFile() throws Exception {
        client.createOrReplaceCustomResource(CR_FILE, NAMESPACE, VERSION, PLURAL, "PgCluster", CRD_NAME, "Namespaced", GROUP);

        assertThat(client.getCustomResource(CRD_NAME, NAMESPACE).split("\n")).contains("pg-from-file");

        client.deleteCustomResourceItem(CRD_NAME, "pg-from-file", NAMESPACE);
        assertThat(client.getCustomResource(CRD_NAME, NAMESPACE).split("\n")).doesNotContain("pg-from-file");
    }

    @Test
    public void patchCRDUpdatesAnIntegerFieldInTheSpec() throws Exception {
        createItem("pg-patch-int", 1, "1/1", "READY", "ok");

        assertThat(client.patchCRD(CRD_NAME, "pg-patch-int", NAMESPACE, "/spec/replicas", "5", "integer", null)).isZero();
        assertThat(specOf("pg-patch-int")).containsEntry("replicas", 5);
    }

    @Test
    public void patchCRDSupportsStringBooleanJsonAndUntypedValues() throws Exception {
        createItem("pg-patch-types", 1, "1/1", "READY", "ok");

        assertThat(client.patchCRD(CRD_NAME, "pg-patch-types", NAMESPACE, "/spec/flavour", "premium", "string", null)).isZero();
        assertThat(client.patchCRD(CRD_NAME, "pg-patch-types", NAMESPACE, "/spec/enabled", "true", "boolean", null)).isZero();
        assertThat(client.patchCRD(CRD_NAME, "pg-patch-types", NAMESPACE, "/spec/extra", "{\"a\":\"b\"}", "json", null)).isZero();
        assertThat(client.patchCRD(CRD_NAME, "pg-patch-types", NAMESPACE, "/spec/plain", "raw", null, null)).isZero();

        Map<String, Object> spec = specOf("pg-patch-types");
        assertThat(spec).containsEntry("flavour", "premium");
        assertThat(spec).containsEntry("enabled", Boolean.TRUE);
        assertThat(spec).containsEntry("plain", "raw");
        assertThat(spec.get("extra")).isInstanceOf(Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void patchCRDInsertsIntoAListWhenTheFinalElementIsAnArray() throws Exception {
        createItem("pg-patch-list", 1, "1/1", "READY", "ok");

        assertThat(client.patchCRD(CRD_NAME, "pg-patch-list", NAMESPACE, "/spec/nodes/0", "node-zero", "string", null)).isZero();

        assertThat((List<Object>) specOf("pg-patch-list").get("nodes")).containsExactly("node-zero", "node-a", "node-b");
    }

    @Test
    public void patchCRDUpdatesLabelsAndAnnotations() throws Exception {
        createItem("pg-patch-meta", 1, "1/1", "READY", "ok");

        client.patchCRD(CRD_NAME, "pg-patch-meta", NAMESPACE, "/metadata/labels/team", "qa", null, null);
        client.patchCRD(CRD_NAME, "pg-patch-meta", NAMESPACE, "/metadata/annotations/owner", "bdt", null, null);

        // patchMetadata mutates the in-memory object without persisting it, so the stored object is unchanged
        assertThat(mockClient.genericKubernetesResources(context).inNamespace(NAMESPACE).withName("pg-patch-meta").get()
                .getMetadata().getLabels()).containsEntry("existing", "label");
    }

    @Test
    public void patchCRDRejectsAnUnsupportedMetadataSection() {
        assertThatThrownBy(() -> client.patchCRD(CRD_NAME, "pg-one", NAMESPACE, "/metadata/finalizers/first", "x", null, null))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Path /metadata/finalizers not supported");
    }

    @Test
    public void patchCRDRejectsAFinalElementThatIsNotAMapOrAList() {
        assertThatThrownBy(() -> client.patchCRD(CRD_NAME, "pg-one", NAMESPACE, "/status/readyInstances/whatever", "1", null, null))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Final element is not a map");
    }

    @Test
    public void patchCRDFailsWhenTheCrdDoesNotExist() {
        assertThatThrownBy(() -> client.patchCRD("missing.stratio.com", "pg-one", NAMESPACE, "/spec/replicas", "1", "integer", null))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("CRD definition missing.stratio.com not found");
    }

    @Test
    public void patchCRDReturnsOneWhenNoItemMatchesTheName() throws Exception {
        assertThat(client.patchCRD(CRD_NAME, "does-not-exist", NAMESPACE, "/spec/replicas", "1", "integer", null)).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void patchCRDMultipleFieldsAppliesEveryModificationInOneUpdate() throws Exception {
        createItem("pg-multi", 1, "1/1", "READY", "ok");

        DataTable modifications = DataTable.create(Arrays.asList(
                Arrays.asList("/spec/replicas", "UPDATE", "4", "integer"),
                Arrays.asList("/spec/newField", "ADD", "added", "string"),
                Arrays.asList("/spec/nodes/0", "DELETE", "", "string")));

        assertThat(client.patchCRDMultipleFields(CRD_NAME, "pg-multi", NAMESPACE, modifications, null)).isZero();

        Map<String, Object> spec = specOf("pg-multi");
        assertThat(spec).containsEntry("replicas", 4);
        assertThat(spec).containsEntry("newField", "added");
        assertThat((List<Object>) spec.get("nodes")).containsExactly("node-b");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void patchCRDWalksThroughAListWhenThePathCrossesAnArrayIndex() throws Exception {
        createItem("pg-deep", 1, "1/1", "READY", "ok");

        assertThat(client.patchCRD(CRD_NAME, "pg-deep", NAMESPACE, "/spec/servers/1/port", "8080", "integer", null))
                .isZero();

        List<Object> servers = (List<Object>) specOf("pg-deep").get("servers");
        assertThat((Map<String, Object>) servers.get(1)).containsEntry("port", 8080);
        assertThat((Map<String, Object>) servers.get(0)).containsEntry("port", 1);
    }

    @Test
    public void patchCRDRejectsANonNumericIndexWhenThePathCrossesAnArray() {
        assertThatThrownBy(() -> client.patchCRD(CRD_NAME, "pg-one", NAMESPACE, "/spec/servers/first/port", "1", "integer", null))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void patchCRDInsertsEveryValueTypeIntoAList() throws Exception {
        createItem("pg-list-types", 1, "1/1", "READY", "ok");

        assertThat(client.patchCRD(CRD_NAME, "pg-list-types", NAMESPACE, "/spec/nodes/0", "42", "integer", null)).isZero();
        assertThat(client.patchCRD(CRD_NAME, "pg-list-types", NAMESPACE, "/spec/nodes/0", "true", "boolean", null)).isZero();
        assertThat(client.patchCRD(CRD_NAME, "pg-list-types", NAMESPACE, "/spec/nodes/0", "{\"a\":\"b\"}", "json", null)).isZero();
        assertThat(client.patchCRD(CRD_NAME, "pg-list-types", NAMESPACE, "/spec/nodes/0", "appended", null, null)).isZero();

        List<Object> nodes = (List<Object>) specOf("pg-list-types").get("nodes");
        assertThat(nodes.get(0)).isInstanceOf(Map.class);
        assertThat(nodes).contains(Boolean.TRUE, 42, "node-a", "node-b");
        // an untyped value is appended instead of inserted at the index
        assertThat(nodes.get(nodes.size() - 1)).isEqualTo("appended");
    }

    @Test
    public void patchCRDMultipleFieldsUpdatesMetadataAsWell() throws Exception {
        createItem("pg-multi-meta", 1, "1/1", "READY", "ok");

        DataTable modifications = DataTable.create(Arrays.asList(
                Arrays.asList("/metadata/labels/team", "UPDATE", "qa", "string"),
                Arrays.asList("/metadata/annotations/owner", "UPDATE", "bdt", "string"),
                Arrays.asList("/spec/replicas", "UPDATE", "3", "integer")));

        assertThat(client.patchCRDMultipleFields(CRD_NAME, "pg-multi-meta", NAMESPACE, modifications, null)).isZero();
        assertThat(specOf("pg-multi-meta")).containsEntry("replicas", 3);
    }

    @Test
    public void patchCRDMultipleFieldsRejectsAFinalElementThatIsNotAMapOrAList() {
        DataTable modifications = DataTable.create(Collections.singletonList(
                Arrays.asList("/status/readyInstances/whatever", "UPDATE", "1", "string")));

        assertThatThrownBy(() -> client.patchCRDMultipleFields(CRD_NAME, "pg-one", NAMESPACE, modifications, null))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Final element is not a map");
    }

    /**
     * When the API server rejects the update, patchCRD must report 1 instead of propagating the
     * exception, and expose the error message through the environment variable when one is given.
     */
    @Test
    public void patchCRDReportsAFailedUpdateThroughTheEnvironmentVariable() throws Exception {
        createItem("pg-conflict", 1, "1/1", "READY", "ok");
        server.expect().put()
                .withPath("/apis/" + GROUP + "/" + VERSION + "/namespaces/" + NAMESPACE + "/" + PLURAL + "/pg-conflict")
                .andReturn(422, "{\"kind\":\"Status\",\"message\":\"the object is invalid\"}")
                .always();

        assertThat(client.patchCRD(CRD_NAME, "pg-conflict", NAMESPACE, "/spec/replicas", "9", "integer", "CRD_PATCH_ERROR"))
                .isEqualTo(1);
        assertThat(com.stratio.qa.utils.ThreadProperty.get("CRD_PATCH_ERROR")).isNotNull();
    }

    @Test
    public void patchCRDMultipleFieldsReportsAFailedUpdateThroughTheEnvironmentVariable() throws Exception {
        createItem("pg-conflict-multi", 1, "1/1", "READY", "ok");
        server.expect().put()
                .withPath("/apis/" + GROUP + "/" + VERSION + "/namespaces/" + NAMESPACE + "/" + PLURAL + "/pg-conflict-multi")
                .andReturn(422, "{\"kind\":\"Status\",\"message\":\"the object is invalid\"}")
                .always();

        DataTable modifications = DataTable.create(Collections.singletonList(
                Arrays.asList("/spec/replicas", "UPDATE", "9", "integer")));

        assertThat(client.patchCRDMultipleFields(CRD_NAME, "pg-conflict-multi", NAMESPACE, modifications, "CRD_MULTI_ERROR"))
                .isEqualTo(1);
        assertThat(com.stratio.qa.utils.ThreadProperty.get("CRD_MULTI_ERROR")).isNotNull();
    }

    @Test
    public void patchCRDMultipleFieldsRejectsAnUnknownOperation() {
        DataTable modifications = DataTable.create(Collections.singletonList(
                Arrays.asList("/spec/replicas", "REPLACE", "4", "integer")));

        assertThatThrownBy(() -> client.patchCRDMultipleFields(CRD_NAME, "pg-one", NAMESPACE, modifications, null))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Operation REPLACE not supported");
    }

    @Test
    public void patchCRDMultipleFieldsFailsWhenTheCrdDoesNotExist() {
        DataTable modifications = DataTable.create(Collections.singletonList(
                Arrays.asList("/spec/replicas", "UPDATE", "4", "integer")));

        assertThatThrownBy(() -> client.patchCRDMultipleFields("missing.stratio.com", "pg-one", NAMESPACE, modifications, null))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("CRD definition missing.stratio.com not found");
    }
}
