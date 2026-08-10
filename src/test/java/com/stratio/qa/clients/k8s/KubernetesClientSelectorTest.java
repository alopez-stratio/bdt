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

/**
 * Covers the {@code getXFilteredByLabel} / {@code getPodsFilteredByField} /
 * {@code getXFilteredByOwnerReferenceName} families of {@link KubernetesClient}.
 *
 * Two resources of each kind are seeded, only one of them carrying the label or owner reference under
 * test, so a filter that silently ignored its argument would fail the assertions.
 */
public class KubernetesClientSelectorTest extends K8sMockServerTestBase {

    private static final String OWNER = "owner-rs";

    private static final Map<String, String> MATCHING = new HashMap<>();

    private static final Map<String, String> NOT_MATCHING = new HashMap<>();

    static {
        MATCHING.put("app", "bdt");
        MATCHING.put("tier", "backend");
        NOT_MATCHING.put("app", "other");
    }

    @Override
    protected void seed() {
        mockClient.pods().inNamespace(NAMESPACE).resource(K8sFixtures.pod("pod-match", NAMESPACE, MATCHING)).create();
        mockClient.pods().inNamespace(NAMESPACE).resource(K8sFixtures.pod("pod-nomatch", NAMESPACE, NOT_MATCHING)).create();
        mockClient.pods().inNamespace(NAMESPACE).resource(K8sFixtures.podOwnedBy("pod-owned", NAMESPACE, OWNER)).create();

        mockClient.apps().deployments().inNamespace(NAMESPACE)
                .resource(labelled(K8sFixtures.deployment("deploy-match", NAMESPACE, "img:1", "K", "V"), MATCHING)).create();
        mockClient.apps().deployments().inNamespace(NAMESPACE)
                .resource(labelled(K8sFixtures.deployment("deploy-nomatch", NAMESPACE, "img:1", "K", "V"), NOT_MATCHING)).create();

        mockClient.apps().replicaSets().inNamespace(NAMESPACE).resource(K8sFixtures.replicaSet("rs-match", NAMESPACE, MATCHING)).create();
        mockClient.apps().replicaSets().inNamespace(NAMESPACE).resource(K8sFixtures.replicaSet("rs-nomatch", NAMESPACE, NOT_MATCHING)).create();

        mockClient.apps().statefulSets().inNamespace(NAMESPACE).resource(K8sFixtures.statefulSet("sts-match", NAMESPACE, MATCHING)).create();
        mockClient.apps().statefulSets().inNamespace(NAMESPACE).resource(K8sFixtures.statefulSet("sts-nomatch", NAMESPACE, NOT_MATCHING)).create();

        mockClient.services().inNamespace(NAMESPACE).resource(K8sFixtures.service("svc-match", NAMESPACE, MATCHING)).create();
        mockClient.services().inNamespace(NAMESPACE).resource(K8sFixtures.service("svc-nomatch", NAMESPACE, NOT_MATCHING)).create();

        mockClient.configMaps().inNamespace(NAMESPACE)
                .resource(labelled(K8sFixtures.configMap("cm-match", NAMESPACE, Collections.singletonMap("k", "v")), MATCHING)).create();
        mockClient.configMaps().inNamespace(NAMESPACE)
                .resource(labelled(K8sFixtures.configMap("cm-nomatch", NAMESPACE, Collections.singletonMap("k", "v")), NOT_MATCHING)).create();

        mockClient.serviceAccounts().inNamespace(NAMESPACE)
                .resource(labelled(K8sFixtures.serviceAccount("sa-match", NAMESPACE), MATCHING)).create();
        mockClient.serviceAccounts().inNamespace(NAMESPACE)
                .resource(labelled(K8sFixtures.serviceAccount("sa-nomatch", NAMESPACE), NOT_MATCHING)).create();

        mockClient.rbac().roles().inNamespace(NAMESPACE).resource(labelled(K8sFixtures.role("role-match", NAMESPACE), MATCHING)).create();
        mockClient.rbac().roles().inNamespace(NAMESPACE).resource(labelled(K8sFixtures.role("role-nomatch", NAMESPACE), NOT_MATCHING)).create();

        mockClient.rbac().roleBindings().inNamespace(NAMESPACE)
                .resource(labelled(K8sFixtures.roleBinding("rb-match", NAMESPACE), MATCHING)).create();
        mockClient.rbac().roleBindings().inNamespace(NAMESPACE)
                .resource(labelled(K8sFixtures.roleBinding("rb-nomatch", NAMESPACE), NOT_MATCHING)).create();

        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE)
                .resource(labelled(K8sFixtures.pvc("pvc-match", NAMESPACE, "1Gi"), MATCHING)).create();
        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE)
                .resource(labelled(K8sFixtures.pvc("pvc-nomatch", NAMESPACE, "1Gi"), NOT_MATCHING)).create();

        mockClient.secrets().inNamespace(NAMESPACE).resource(ownedBy(K8sFixtures.secret("secret-owned", NAMESPACE))).create();
        mockClient.secrets().inNamespace(NAMESPACE).resource(K8sFixtures.secret("secret-free", NAMESPACE)).create();

        // one owned resource of every remaining kind, so each ownerReference filter has something to match
        mockClient.apps().deployments().inNamespace(NAMESPACE)
                .resource(ownedBy(K8sFixtures.deployment("deploy-owned", NAMESPACE, "img:1", "K", "V"))).create();
        mockClient.apps().replicaSets().inNamespace(NAMESPACE)
                .resource(ownedBy(K8sFixtures.replicaSet("rs-owned", NAMESPACE, Collections.emptyMap()))).create();
        mockClient.apps().statefulSets().inNamespace(NAMESPACE)
                .resource(ownedBy(K8sFixtures.statefulSet("sts-owned", NAMESPACE, Collections.emptyMap()))).create();
        mockClient.configMaps().inNamespace(NAMESPACE)
                .resource(ownedBy(K8sFixtures.configMap("cm-owned", NAMESPACE, Collections.singletonMap("k", "v")))).create();
        mockClient.persistentVolumeClaims().inNamespace(NAMESPACE)
                .resource(ownedBy(K8sFixtures.pvc("pvc-owned", NAMESPACE, "1Gi"))).create();

        mockClient.network().v1().ingresses().inNamespace(NAMESPACE)
                .resource(K8sFixtures.ingress("ing-match", NAMESPACE, MATCHING)).create();
        mockClient.network().v1().ingresses().inNamespace(NAMESPACE)
                .resource(K8sFixtures.ingress("ing-nomatch", NAMESPACE, NOT_MATCHING)).create();
    }

    private static <T extends io.fabric8.kubernetes.api.model.HasMetadata> T labelled(T resource, Map<String, String> labels) {
        resource.getMetadata().setLabels(new HashMap<>(labels));
        return resource;
    }

    private static <T extends io.fabric8.kubernetes.api.model.HasMetadata> T ownedBy(T resource) {
        resource.getMetadata().setOwnerReferences(Collections.singletonList(
                new io.fabric8.kubernetes.api.model.OwnerReferenceBuilder()
                        .withApiVersion("apps/v1").withKind("ReplicaSet").withName(OWNER).withUid("uid-" + OWNER)
                        .build()));
        return resource;
    }

    @Test
    public void singleLabelSelectorFiltersEveryResourceKind() {
        assertThat(client.getPodsFilteredByLabel("app=bdt", NAMESPACE)).isEqualTo("pod-match");
        assertThat(client.getDeploymentsFilteredByLabel("app=bdt", NAMESPACE)).isEqualTo("deploy-match");
        assertThat(client.getReplicaSetsFilteredByLabel("app=bdt", NAMESPACE)).isEqualTo("rs-match");
        assertThat(client.getStateFulSetsFilteredByLabel("app=bdt", NAMESPACE)).isEqualTo("sts-match");
        assertThat(client.getServicesFilteredByLabel("app=bdt", NAMESPACE)).isEqualTo("svc-match");
        assertThat(client.getConfigMapsFilteredByLabel("app=bdt", NAMESPACE)).isEqualTo("cm-match");
        assertThat(client.getServiceAccountsFilteredByLabel("app=bdt", NAMESPACE)).isEqualTo("sa-match");
        assertThat(client.getRolesFilteredByLabel("app=bdt", NAMESPACE)).isEqualTo("role-match");
        assertThat(client.getRolesBindingsFilteredByLabel("app=bdt", NAMESPACE)).isEqualTo("rb-match");
        assertThat(client.getPersistentVolumeClaimsFilteredByLabel("app=bdt", NAMESPACE)).isEqualTo("pvc-match");
        assertThat(client.getIngressFilteredByLabel("app=bdt", NAMESPACE))
                .extracting(ingress -> ingress.getMetadata().getName())
                .containsExactly("ing-match");
    }

    @Test
    public void commaSeparatedSelectorsAreAndedTogether() {
        assertThat(client.getPodsFilteredByLabel("app=bdt,tier=backend", NAMESPACE)).isEqualTo("pod-match");
        assertThat(client.getPodsFilteredByLabel("app=bdt,tier=frontend", NAMESPACE)).isEmpty();
    }

    @Test
    public void aSelectorWithoutAValueIsAcceptedAndDefaultsToAnEmptyValue() {
        // getLabelSelector turns a bare "key" into key -> "", which must not blow up on the split
        assertThat(client.getPodsFilteredByLabel("app", NAMESPACE)).isNotNull();
        assertThat(client.getPodsFilteredByLabel("app=bdt,tier", NAMESPACE)).isNotNull();
    }

    @Test
    public void labelSelectorWithNullNamespaceSearchesEveryNamespace() {
        assertThat(client.getPodsFilteredByLabel("app=bdt", null)).contains("pod-match");
        assertThat(client.getDeploymentsFilteredByLabel("app=bdt", null)).contains("deploy-match");
        assertThat(client.getReplicaSetsFilteredByLabel("app=bdt", null)).contains("rs-match");
        assertThat(client.getStateFulSetsFilteredByLabel("app=bdt", null)).contains("sts-match");
        assertThat(client.getServicesFilteredByLabel("app=bdt", null)).contains("svc-match");
        assertThat(client.getConfigMapsFilteredByLabel("app=bdt", null)).contains("cm-match");
        assertThat(client.getServiceAccountsFilteredByLabel("app=bdt", null)).contains("sa-match");
        assertThat(client.getRolesFilteredByLabel("app=bdt", null)).contains("role-match");
        assertThat(client.getRolesBindingsFilteredByLabel("app=bdt", null)).contains("rb-match");
        assertThat(client.getPersistentVolumeClaimsFilteredByLabel("app=bdt", null)).contains("pvc-match");
    }

    @Test
    public void fieldSelectorFiltersPodsByName() {
        assertThat(client.getPodsFilteredByField("metadata.name=pod-match", NAMESPACE)).isEqualTo("pod-match");
        assertThat(client.getPodsFilteredByField("metadata.name=pod-match", null)).contains("pod-match");
        assertThat(client.getPodsFilteredByField("metadata.name=nope", NAMESPACE)).isEmpty();
    }

    @Test
    public void ownerReferenceFilterOnlyReturnsOwnedResourcesOfEveryKind() {
        assertThat(client.getPodsFilteredByOwnerReferenceName(OWNER, NAMESPACE)).isEqualTo("pod-owned");
        assertThat(client.getSecretsFilteredByOwnerReferenceName(OWNER, NAMESPACE)).isEqualTo("secret-owned");
        assertThat(client.getDeploymentsFilteredByOwnerReferenceName(OWNER, NAMESPACE)).isEqualTo("deploy-owned");
        assertThat(client.getReplicaSetsFilteredByOwnerReferenceName(OWNER, NAMESPACE)).isEqualTo("rs-owned");
        assertThat(client.getStateFulSetsFilteredByOwnerReferenceName(OWNER, NAMESPACE)).isEqualTo("sts-owned");
        assertThat(client.getConfigMapsFilteredByOwnerReferenceName(OWNER, NAMESPACE)).isEqualTo("cm-owned");
        assertThat(client.getPersistentVolumeClaimsFilteredByOwnerReferenceName(OWNER, NAMESPACE)).isEqualTo("pvc-owned");
    }

    @Test
    public void ownerReferenceFilterReturnsEmptyForAnUnknownOwner() {
        assertThat(client.getPodsFilteredByOwnerReferenceName("nobody", NAMESPACE)).isEmpty();
        assertThat(client.getSecretsFilteredByOwnerReferenceName("nobody", NAMESPACE)).isEmpty();
        assertThat(client.getDeploymentsFilteredByOwnerReferenceName("nobody", NAMESPACE)).isEmpty();
        assertThat(client.getReplicaSetsFilteredByOwnerReferenceName("nobody", NAMESPACE)).isEmpty();
        assertThat(client.getStateFulSetsFilteredByOwnerReferenceName("nobody", NAMESPACE)).isEmpty();
        assertThat(client.getConfigMapsFilteredByOwnerReferenceName("nobody", NAMESPACE)).isEmpty();
        assertThat(client.getPersistentVolumeClaimsFilteredByOwnerReferenceName("nobody", NAMESPACE)).isEmpty();
    }

    @Test
    public void ownerReferenceFilterWithNullNamespaceSearchesEveryNamespace() {
        assertThat(client.getPodsFilteredByOwnerReferenceName(OWNER, null)).contains("pod-owned");
        assertThat(client.getSecretsFilteredByOwnerReferenceName(OWNER, null)).contains("secret-owned");
        assertThat(client.getDeploymentsFilteredByOwnerReferenceName(OWNER, null)).contains("deploy-owned");
        assertThat(client.getReplicaSetsFilteredByOwnerReferenceName(OWNER, null)).contains("rs-owned");
        assertThat(client.getStateFulSetsFilteredByOwnerReferenceName(OWNER, null)).contains("sts-owned");
        assertThat(client.getConfigMapsFilteredByOwnerReferenceName(OWNER, null)).contains("cm-owned");
        assertThat(client.getPersistentVolumeClaimsFilteredByOwnerReferenceName(OWNER, null)).contains("pvc-owned");
    }
}
