Feature: Kubernetes steps against an in-JVM mock API server

  The cluster is started by K8SMockClusterIT, which also exposes its kubeconfig through the
  K8S_MOCK_KUBECONFIG system property. Nothing here needs a real cluster, Docker or network access.

  Background:
    When I connect to Kubernetes cluster using config file located at '${K8S_MOCK_KUBECONFIG}'

  Scenario: List the resources seeded in the namespace
    When I get pods in namespace 'k8s-it' and save it in environment variable 'IT_PODS'
    Then '!{IT_PODS}' is 'it-pod'
    When I get deployments in namespace 'k8s-it' and save it in environment variable 'IT_DEPLOYMENTS'
    Then '!{IT_DEPLOYMENTS}' is 'it-deploy'
    When I get services in namespace 'k8s-it' and save it in environment variable 'IT_SERVICES'
    Then '!{IT_SERVICES}' is 'it-svc'
    When I get statefulsets in namespace 'k8s-it' and save it in environment variable 'IT_STATEFULSETS'
    Then '!{IT_STATEFULSETS}' is 'it-sts'
    When I get configmaps in namespace 'k8s-it' and save it in environment variable 'IT_CONFIGMAPS'
    Then '!{IT_CONFIGMAPS}' is 'it-cm'
    When I get secrets in namespace 'k8s-it' and save it in environment variable 'IT_SECRETS'
    Then '!{IT_SECRETS}' is 'it-secret'
    When I get persistentVolumeClaims in namespace 'k8s-it' and save it in environment variable 'IT_PVCS'
    Then '!{IT_PVCS}' is 'it-pvc'
    When I get storageClasses and save it in environment variable 'IT_STORAGE_CLASSES'
    Then '!{IT_STORAGE_CLASSES}' is 'it-default-sc'

  Scenario: List every namespace and read the default storage class
    When I get all namespaces and save it in environment variable 'IT_NAMESPACES'
    Then '!{IT_NAMESPACES}' contains 'k8s-it'
    When I get default storageClass and save it in environment variable 'IT_DEFAULT_SC'
    Then '!{IT_DEFAULT_SC}' is 'it-default-sc'

  Scenario: Describe resources in yaml and json
    When I describe pod with name 'it-pod' in namespace 'k8s-it' and save it in environment variable 'IT_POD_YAML'
    Then '!{IT_POD_YAML}' contains 'name: "it-pod"'
    When I describe deployment with name 'it-deploy' in namespace 'k8s-it' in 'json' format and save it in environment variable 'IT_DEPLOY_JSON'
    Then '!{IT_DEPLOY_JSON}' contains 'it-deploy'
    When I describe configmap with name 'it-cm' in namespace 'k8s-it' and save it in environment variable 'IT_CM_DESCRIBE'
    Then '!{IT_CM_DESCRIBE}' contains 'origin=seed'
    When I describe persistentVolumeClaims with name 'it-pvc' in namespace 'k8s-it' and save it in file 'it-pvc.yaml'

  Scenario: Read a deployment environment variable
    When I get value of environment variable 'LOG_LEVEL' from deployment with name 'it-deploy' in namespace 'k8s-it' and save it in environment variable 'IT_LOG_LEVEL'
    Then '!{IT_LOG_LEVEL}' is 'INFO'

  Scenario: Filter resources by label and by field
    When I get pods using the following label filter 'app=it' in namespace 'k8s-it' and save it in environment variable 'IT_PODS_BY_LABEL'
    Then '!{IT_PODS_BY_LABEL}' is 'it-pod'
    When I get services using the following label filter 'app=it' in namespace 'k8s-it' and save it in environment variable 'IT_SVC_BY_LABEL'
    Then '!{IT_SVC_BY_LABEL}' is 'it-svc'
    When I get pods using the following field filter 'metadata.name=it-pod' in namespace 'k8s-it' and save it in environment variable 'IT_PODS_BY_FIELD'
    Then '!{IT_PODS_BY_FIELD}' is 'it-pod'
    When I get pods with ownerReference name 'nobody' in namespace 'k8s-it' and save it in environment variable 'IT_PODS_BY_OWNER'
    Then '!{IT_PODS_BY_OWNER}' is empty

  Scenario: Check a namespace event
    When I check that there is an event that contains the message 'Created container' in namespace 'k8s-it' with resource type 'Pod' with resource name 'it-pod' with reason 'Created'

  Scenario: Wait for a pod to reach the running state
    When in less than '5' seconds, checking each '1' seconds, pod with name 'it-pod' in namespace 'k8s-it' has 'running' status and 'ready' state
    When in less than '5' seconds, checking each '1' seconds, we have '1' pod/s with label filter 'app=it' in namespace 'k8s-it' having 'running' status

  Scenario: Create a namespace, a deployment and expose it
    When I create namespace with name 'k8s-it-created' and labels 'owner=bdt'
    When I get all namespaces and save it in environment variable 'IT_NEW_NAMESPACES'
    Then '!{IT_NEW_NAMESPACES}' contains 'k8s-it-created'
    When I create deployment with name 'it-new-deploy', in namespace 'k8s-it-created', with image 'stratio/app:1.0.0' and image pull policy 'Always'
    When I expose deployment with name 'it-new-deploy', in namespace 'k8s-it-created' in port '8080'
    When I get services in namespace 'k8s-it-created' and save it in environment variable 'IT_NEW_SERVICES'
    Then '!{IT_NEW_SERVICES}' is 'it-new-deploy'
    When I update image pull policy deployment with id 'it-new-deploy' in namespace 'k8s-it-created' for container 'it-new-deploy' with value 'IfNotPresent'
    When I delete deployment with name 'it-new-deploy' in namespace 'k8s-it-created'
    When I delete service with name 'it-new-deploy' in namespace 'k8s-it-created'

  Scenario: Create and modify a configmap
    When I create configmap with name 'it-created-cm' in namespace 'k8s-it' with key 'first' and value '1'
    When I add/modify variable 'second' with value '2' in configmap with name 'it-created-cm' in namespace 'k8s-it'
    When I get key 'second' in configmap with name 'it-created-cm' in namespace 'k8s-it' and save it in environment variable 'IT_CM_VALUE'
    Then '!{IT_CM_VALUE}' is '2'
    When I add/modify variables in configmap with name 'it-created-cm' in namespace 'k8s-it':
      | third  | 3 |
      | fourth | 4 |
    When I get key 'fourth' in configmap with name 'it-created-cm' in namespace 'k8s-it' and save it in environment variable 'IT_CM_FOURTH'
    Then '!{IT_CM_FOURTH}' is '4'
    When I delete configmap with name 'it-created-cm' in namespace 'k8s-it'

  Scenario: Create RBAC objects and a resource quota
    When I create service account with name 'it-sa' in namespace 'k8s-it'
    When I create role with name 'it-role' in namespace 'k8s-it' with resources 'pods,services' and verbs 'get,list'
    When I create role binding with name 'it-rb' with Role 'it-role' and ServiceAccount 'k8s-it:it-sa'
    When I create cluster role with name 'it-cluster-role' with resources 'pods' and verbs 'get'
    When I get roles in namespace 'k8s-it' and save it in environment variable 'IT_ROLES'
    Then '!{IT_ROLES}' contains 'it-role'
    When I get rolebindings in namespace 'k8s-it' and save it in environment variable 'IT_ROLEBINDINGS'
    Then '!{IT_ROLEBINDINGS}' contains 'it-rb'
    When I get clusterroles and save it in environment variable 'IT_CLUSTER_ROLES'
    Then '!{IT_CLUSTER_ROLES}' contains 'it-cluster-role'
    When I create resourcequota with name 'it-rq', in namespace 'k8s-it', with hard cpu '2', with hard memory '1Gi', with hard pods '10'
    When I describe resourcequota with name 'it-rq' in namespace 'k8s-it' and save it in environment variable 'IT_RQ'
    Then '!{IT_RQ}' contains 'name: "it-rq"'
    When I delete resourcequota with name 'it-rq' in namespace 'k8s-it'
    When I delete rolebinding with name 'it-rb' in namespace 'k8s-it'
    When I delete role with name 'it-role' in namespace 'k8s-it'
    When I delete serviceaccount with name 'it-sa' in namespace 'k8s-it'

  Scenario: Apply a manifest file and patch a persistent volume claim
    When I apply configuration file located at 'src/test/resources/k8s/configmap-to-apply.yaml' in namespace 'k8s-it'
    When I get key 'origin' in configmap with name 'applied-from-file' in namespace 'k8s-it' and save it in environment variable 'IT_APPLIED'
    Then '!{IT_APPLIED}' is 'file'
    When I delete configmap with name 'applied-from-file' in namespace 'k8s-it'
    When I patch persistentVolumeClaims with name 'it-pvc' in namespace 'k8s-it' with path '/spec/resources/requests/storage' and value '2Gi'
    When I describe persistentVolumeClaims with name 'it-pvc' in namespace 'k8s-it' and save it in environment variable 'IT_PVC_PATCHED'
    Then '!{IT_PVC_PATCHED}' contains '2Gi'
