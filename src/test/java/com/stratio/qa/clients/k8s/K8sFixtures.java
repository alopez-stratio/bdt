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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.api.model.storage.StorageClassBuilder;

import java.util.Collections;
import java.util.Map;

/**
 * Builders for the Kubernetes objects seeded into the fabric8 mock server. Kept in one place so the
 * different {@link KubernetesClient} test classes share the same shapes.
 */
public final class K8sFixtures {

    public static final String DEFAULT_STORAGE_CLASS_ANNOTATION = "storageclass.kubernetes.io/is-default-class";

    private K8sFixtures() {
    }

    public static Pod pod(String name, String namespace, Map<String, String> labels) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).withLabels(labels).endMetadata()
                .withNewSpec()
                .addNewContainer().withName("main").withImage("busybox:1.36").endContainer()
                .endSpec()
                .withNewStatus().withPhase("Running").endStatus()
                .build();
    }

    public static Pod podOwnedBy(String name, String namespace, String ownerName) {
        Pod pod = pod(name, namespace, Collections.emptyMap());
        pod.getMetadata().setOwnerReferences(Collections.singletonList(
                new io.fabric8.kubernetes.api.model.OwnerReferenceBuilder()
                        .withApiVersion("apps/v1").withKind("ReplicaSet").withName(ownerName).withUid("uid-" + ownerName)
                        .build()));
        return pod;
    }

    public static Deployment deployment(String name, String namespace, String image, String envKey, String envValue) {
        return new DeploymentBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).addToLabels("app", name).endMetadata()
                .withNewSpec()
                .withReplicas(1)
                .withNewSelector().addToMatchLabels("app", name).endSelector()
                .withNewTemplate()
                .withNewMetadata().addToLabels("app", name).endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("main")
                .withImage(image)
                .withImagePullPolicy("IfNotPresent")
                .addNewEnv().withName(envKey).withValue(envValue).endEnv()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    public static ReplicaSet replicaSet(String name, String namespace, Map<String, String> labels) {
        return new ReplicaSetBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).withLabels(labels).endMetadata()
                .withNewSpec().withReplicas(1).endSpec()
                .build();
    }

    public static StatefulSet statefulSet(String name, String namespace, Map<String, String> labels) {
        return new StatefulSetBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).withLabels(labels).endMetadata()
                .withNewSpec().withReplicas(1).withServiceName(name).endSpec()
                .build();
    }

    public static Service service(String name, String namespace, Map<String, String> labels) {
        return new ServiceBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).withLabels(labels).endMetadata()
                .withNewSpec().addNewPort().withPort(8080).withProtocol("TCP").endPort().endSpec()
                .build();
    }

    public static ConfigMap configMap(String name, String namespace, Map<String, String> data) {
        return new ConfigMapBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .withData(data)
                .build();
    }

    public static Secret secret(String name, String namespace) {
        return new SecretBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .addToData("password", "c3RyYXRpbw==")
                .build();
    }

    public static ServiceAccount serviceAccount(String name, String namespace) {
        return new ServiceAccountBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .build();
    }

    public static PersistentVolumeClaim pvc(String name, String namespace, String storage) {
        return new PersistentVolumeClaimBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withNewResources().addToRequests("storage", new Quantity(storage)).endResources()
                .endSpec()
                .build();
    }

    public static Role role(String name, String namespace) {
        return new RoleBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .addNewRule().withApiGroups("").withResources("pods").withVerbs("get").endRule()
                .build();
    }

    public static RoleBinding roleBinding(String name, String namespace) {
        return new RoleBindingBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .withNewRoleRef().withApiGroup("rbac.authorization.k8s.io").withKind("Role").withName("a-role").endRoleRef()
                .build();
    }

    public static ClusterRole clusterRole(String name) {
        return new ClusterRoleBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .addNewRule().withApiGroups("").withResources("pods").withVerbs("list").endRule()
                .build();
    }

    public static ClusterRoleBinding clusterRoleBinding(String name) {
        return new ClusterRoleBindingBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewRoleRef().withApiGroup("rbac.authorization.k8s.io").withKind("ClusterRole").withName("a-cluster-role").endRoleRef()
                .build();
    }

    public static Namespace namespace(String name) {
        return new NamespaceBuilder().withNewMetadata().withName(name).endMetadata().build();
    }

    public static Event event(String name, String namespace, String message, String reason, String kind, String involvedName) {
        return new EventBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .withMessage(message)
                .withReason(reason)
                .withNewInvolvedObject().withKind(kind).withName(involvedName).withNamespace(namespace).endInvolvedObject()
                .build();
    }

    public static Ingress ingress(String name, String namespace, Map<String, String> labels) {
        return new IngressBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).withLabels(labels).endMetadata()
                .withNewSpec()
                .addNewRule().withHost(name + ".stratio.com").endRule()
                .endSpec()
                .build();
    }

    public static NetworkPolicy networkPolicy(String name, String namespace) {
        return new NetworkPolicyBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .withNewSpec().withNewPodSelector().endPodSelector().endSpec()
                .build();
    }

    public static Job job(String name, String namespace) {
        return new JobBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer().withName("main").withImage("busybox:1.36").endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    public static StorageClass storageClass(String name, boolean isDefault) {
        StorageClassBuilder builder = new StorageClassBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withProvisioner("kubernetes.io/no-provisioner");
        if (isDefault) {
            builder.editMetadata().addToAnnotations(DEFAULT_STORAGE_CLASS_ANNOTATION, "true").endMetadata();
        }
        return builder.build();
    }

    public static ResourceQuota resourceQuota(String name, String namespace) {
        return new ResourceQuotaBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .withNewSpec().addToHard("pods", new Quantity("10")).endSpec()
                .build();
    }

    public static HorizontalPodAutoscaler horizontalPodAutoscaler(String name, String namespace, int min, int max) {
        return new HorizontalPodAutoscalerBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .withNewSpec()
                .withMinReplicas(min)
                .withMaxReplicas(max)
                .withNewScaleTargetRef().withApiVersion("apps/v1").withKind("Deployment").withName(name).endScaleTargetRef()
                .endSpec()
                .build();
    }

    public static CustomResourceDefinition customResourceDefinition(String plural, String group, String kind, String version) {
        return new CustomResourceDefinitionBuilder()
                .withNewMetadata().withName(plural + "." + group).endMetadata()
                .withNewSpec()
                .withGroup(group)
                .withNewNames().withKind(kind).withPlural(plural).withSingular(kind.toLowerCase()).endNames()
                .withScope("Namespaced")
                .addNewVersion().withName(version).withServed(true).withStorage(true).endVersion()
                .endSpec()
                .build();
    }
}
