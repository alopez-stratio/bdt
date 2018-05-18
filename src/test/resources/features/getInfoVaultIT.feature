Feature: Get Info Vault

  Background: Get Vault Token
    Given I open a ssh connection to '${BOOTSTRAP_IP:-10.200.0.155}' with user '${BOOTSTRAP_USER:-root}' and password '${BOOTSTRAP_PASSWORD:-stratio}'
    And I run 'cat /stratio_volume/vault_response | jq '.' | grep "root_token" | awk '{print $2}'' in the ssh connection and save the value in environment variable 'vaultToken'
    And I run 'getent hosts gosec1.node.paas.labs.stratio.com | awk '{print $1}'' in the ssh connection and save the value in environment variable 'gosecMachineIP'
    And I run 'echo !{vaultToken}' locally

  Scenario: Get info dg-bootstrap crt
    Given I get '${TYPE:-crt}' from path '${PATH:-/userland/certificates/dg-bootstrap}' for value '${VALUE:-dg-bootstrap}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'dg-bootstrap_crt'
    And I run 'echo !{dg-bootstrap_crt}' locally

  Scenario: Get info dg-bootstrap key
    Given I get '${TYPE:-key}' from path '${PATH:-/userland/certificates/dg-bootstrap}' for value '${VALUE:-dg-bootstrap}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'dg-bootstrap_key'
    And I run 'echo !{dg-bootstrap_key}' locally

  Scenario: Get info pg_0003.postgrestls.mesos crt
    Given I get '${TYPE:-crt}' from path '${PATH:-/userland/certificates/postgrestls}' for value '${VALUE:-pg_0003.postgrestls.mesos}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'pg_0003.postgrestls.mesos_crt'
    And I run 'echo !{pg_0003.postgrestls.mesos_crt}' locally

  Scenario: Get info pg_0003.postgrestls.mesos crt
    Given I get '${TYPE:-key}' from path '${PATH:-/userland/certificates/postgrestls}' for value '${VALUE:-pg_0003.postgrestls.mesos}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'pg_0003.postgrestls.mesos_key'
    And I run 'echo !{pg_0003.postgrestls.mesos_key}' locally

  Scenario: Get info pg_0003-postgrestls.service.paas.labs.stratio.com crt
    Given I get '${TYPE:-crt}' from path '${PATH:-/userland/certificates/postgrestls}' for value '${VALUE:-pg_0003-postgrestls.service.paas.labs.stratio.com}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'pg_0003-postgrestls.service.paas.labs.stratio.com_crt'
    And I run 'echo !{pg_0003-postgrestls.service.paas.labs.stratio.com_crt}' locally

  Scenario: Get info pg_0003-postgrestls.service.paas.labs.stratio.com crt
    Given I get '${TYPE:-key}' from path '${PATH:-/userland/certificates/postgrestls}' for value '${VALUE:-pg_0003-postgrestls.service.paas.labs.stratio.com}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'pg_0003-postgrestls.service.paas.labs.stratio.com_key'
    And I run 'echo !{pg_0003-postgrestls.service.paas.labs.stratio.com_key}' locally

  Scenario: Get info stratio ca
    Given I get '${TYPE:-crt}' from path '${PATH:-/ca-trust/certificates/ca}' for value '${VALUE:-ca}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'ca-crt'
    And I run 'echo !{ca-crt}' locally
#
  Scenario: Get info dg-agent principal
    Given I get '${TYPE:-principal}' from path '${PATH:-/userland/kerberos/dg-agent}' for value '${VALUE:-dg-agent}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'dg-agent_principal'
    And I run 'echo !{dg-agent_principal} | cut -d'@' -f1' locally and save the value in environment variable 'content_principal'

  Scenario: Get info dg-agent keytab
    Given I open a ssh connection to '${BOOTSTRAP_IP:-10.200.0.155}' with user '${BOOTSTRAP_USER:-root}' and password '${BOOTSTRAP_PASSWORD:-stratio}'
    And I run ' cd /stratio_volume/ ; cat descriptor.json | jq '.security.kerberos' | grep "realm" | awk '{print $2}' | cut -d',' -f1 | awk '{print toupper($0)}' | sed 's/\"//g'' in the ssh connection and save the value in environment variable 'realm'
    And I get '${TYPE:-keytab}' from path '${PATH:-/userland/kerberos/dg-agent}' for value '${VALUE:-dg-agent}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'dg-agent_keytab_path'
    And I run 'echo !{dg-agent_keytab_path} > target/test-classes/management.keytab' locally
    And I run 'strings !{dg-agent_keytab_path}' locally and save the value in environment variable 'content_keytab'
    And I run 'echo !{content_keytab} | grep !{realm}' locally
    And I run 'echo !{content_keytab} | grep !{content_principal}' locally
    And I run 'rm -f dg-agent.keytab' locally

  Scenario: Get info management principal
    Given I get '${TYPE:-principal}' from path '${PATH:-/userland/kerberos/management}' for value '${VALUE:-management}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'management_principal'
    And I run 'echo !{management_principal} | cut -d'@' -f1' locally and save the value in environment variable 'content_principal'

  Scenario: Get info management keytab
    Given I open a ssh connection to '${BOOTSTRAP_IP:-10.200.0.155}' with user '${BOOTSTRAP_USER:-root}' and password '${BOOTSTRAP_PASSWORD:-stratio}'
    And I run ' cd /stratio_volume/ ; cat descriptor.json | jq '.security.kerberos' | grep "realm" | awk '{print $2}' | cut -d',' -f1 | awk '{print toupper($0)}' | sed 's/\"//g'' in the ssh connection and save the value in environment variable 'realm'
    And I get '${TYPE:-keytab}' from path '${PATH:-/userland/kerberos/management}' for value '${VALUE:-management}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'management_keytab_path'
    And I run 'echo !{management_keytab_path} > target/test-classes/management.keytab' locally
    And I run 'strings !{management_keytab_path}' locally and save the value in environment variable 'content_keytab'
    And I run 'echo !{content_keytab} | grep !{realm}' locally
    And I run 'echo !{content_keytab} | grep !{content_principal}' locally
    And I run 'rm -f management.keytab' locally

  Scenario: Get info paas.labs.stratio.com principal
    Given I get '${TYPE:-principal}' from path '${PATH:-/userland/kerberos/management}' for value '${VALUE:-paas.labs.stratio.com}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'paas.labs.stratio.com_principal'
    And I run 'echo !{paas.labs.stratio.com_principal} | cut -d'@' -f1 | cut -d'/' -f1' locally and save the value in environment variable 'content_principal1'
    And I run 'echo !{paas.labs.stratio.com_principal} | cut -d'@' -f1 | cut -d'/' -f2' locally and save the value in environment variable 'content_principal2'

  Scenario: Get info paas.labs.stratio.com keytab
    Given I open a ssh connection to '${BOOTSTRAP_IP:-10.200.0.155}' with user '${BOOTSTRAP_USER:-root}' and password '${BOOTSTRAP_PASSWORD:-stratio}'
    And I run ' cd /stratio_volume/ ; cat descriptor.json | jq '.security.kerberos' | grep "realm" | awk '{print $2}' | cut -d',' -f1 | awk '{print toupper($0)}' | sed 's/\"//g'' in the ssh connection and save the value in environment variable 'realm'
    And I get '${TYPE:-keytab}' from path '${PATH:-/userland/kerberos/management}' for value '${VALUE:-paas.labs.stratio.com}' with token '!{vaultToken}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'paas.labs.stratio.com_keytab_path'
    And I run 'echo !{paas.labs.stratio.com_keytab_path} > target/test-classes/paas.labs.stratio.com.keytab' locally
    And I run 'strings !{paas.labs.stratio.com_keytab_path}' locally and save the value in environment variable 'content_keytab'
    And I run 'echo !{content_keytab} | grep !{realm}' locally
    And I run 'echo !{content_keytab} | grep !{content_principal1}' locally
    And I run 'echo !{content_keytab} | grep !{content_principal2}' locally
    And I run 'rm -f paas.labs.stratio.com.keytab' locally

#  Scenario: Get info with wrong path
#    Given I get '${TYPE:-crt}' from path '${PATH:-/certificates/dg-bootstrap}' for value '${VALUE:-dg-bootstrap}' with token '${TOKEN:-c5c071f8-1b25-5ba9-5bf7-791158908573}' , gosec machine '!{gosecMachineIP}' and save the value in environment variable 'dg-bootstrap_crt'
#    And the service response must contain the text '"404 Not Found"'