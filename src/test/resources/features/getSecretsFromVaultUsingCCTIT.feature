@ignore @manual
Feature: Get secrets from Vault

  Scenario: Upload secrets to Vault (/userland)
    Given I run 'curl -k -H "X-Vault-Token:stratio" -H "Content-Type:application/json" -X POST -d "{\"type\":\"generic\",\"description\":\"testing\"}" http://${VAULT_URL:-vault.demo.stratio.com}:8200/v1/sys/mounts/userland' locally with exit status '0'
    And I run 'curl -k -H "X-Vault-Token:stratio" -H "Content-Type:application/json" -X POST -d "{\"type\":\"generic\",\"description\":\"testing\"}" http://${VAULT_URL:-vault.demo.stratio.com}:8200/v1/sys/mounts/ca-trust' locally with exit status '0'
    Then I run 'curl -k -H "X-Vault-Token:stratio" -H "Content-Type:application/json" -X POST -d "@src/test/resources/vault/test_cert.json" http://${VAULT_URL:-vault.demo.stratio.com}:8200/v1/userland/certificates/testcct' locally with exit status '0'
    And I run 'curl -k -H "X-Vault-Token:stratio" -H "Content-Type:application/json" -X POST -d "@src/test/resources/vault/test_ca.json" http://${VAULT_URL:-vault.demo.stratio.com}:8200/v1/ca-trust/certificates/cacct' locally with exit status '0'
    And I run 'curl -k -H "X-Vault-Token:stratio" -H "Content-Type:application/json" -X POST -d "@src/test/resources/vault/test_keytab.json" http://${VAULT_URL:-vault.demo.stratio.com}:8200/v1/userland/kerberos/testcct' locally with exit status '0'
    And I run 'curl -k -H "X-Vault-Token:stratio" -H "Content-Type:application/json" -X POST -d "@src/test/resources/vault/test_password.json" http://${VAULT_URL:-vault.demo.stratio.com}:8200/v1/userland/passwords/testcct/testqa' locally with exit status '0'

  Scenario: Upload secrets to Vault (/people)
    Given I run 'curl -k -H "X-Vault-Token:stratio" -H "Content-Type:application/json" -X POST -d "{\"type\":\"generic\",\"description\":\"testing\"}" http://${VAULT_URL:-vault.demo.stratio.com}:8200/v1/sys/mounts/people' locally with exit status '0'
    Then I run 'curl -k -H "X-Vault-Token:stratio" -H "Content-Type:application/json" -X POST -d "@src/test/resources/vault/test_cert.json" http://${VAULT_URL:-vault.demo.stratio.com}:8200/v1/people/certificates/testcct' locally with exit status '0'
    And I run 'curl -k -H "X-Vault-Token:stratio" -H "Content-Type:application/json" -X POST -d "@src/test/resources/vault/test_keytab.json" http://${VAULT_URL:-vault.demo.stratio.com}:8200/v1/people/kerberos/testcct' locally with exit status '0'
    And I run 'curl -k -H "X-Vault-Token:stratio" -H "Content-Type:application/json" -X POST -d "@src/test/resources/vault/test_password.json" http://${VAULT_URL:-vault.demo.stratio.com}:8200/v1/people/passwords/testcct/testqa' locally with exit status '0'

  Scenario: Get public part from certificate (/userland)
    Given I get certificate 'test-qa.qa' using deploy-api from path 'testcct' in PEM format
    When I save 'target/test-classes/test-qa.qa.pem' in variable 'pemFile'
    Then I run 'openssl x509 -in !{pemFile} -noout' locally with exit status '0'
    And I run 'openssl x509 -in !{pemFile} -noout -text | grep "Issuer: CN" | grep "nightlyforward"' locally
    And I run 'openssl x509 -in !{pemFile} -noout -text | grep "Subject" | grep "ES" | grep "Stratio" | grep "test"' locally
    And I run 'openssl x509 -in !{pemFile} -noout -text | grep "DNS" | grep "test" | grep "test.qa"' locally
    Then I run 'rm -f !{pemFile}' locally

  Scenario: Get public part from certificate (/people)
    Given I get certificate 'test-qa.qa' using deploy-api from path 'testcct' in PEM format in /people
    When I save 'target/test-classes/test-qa.qa.pem' in variable 'pemFile'
    Then I run 'openssl x509 -in !{pemFile} -noout' locally with exit status '0'
    And I run 'openssl x509 -in !{pemFile} -noout -text | grep "Issuer: CN" | grep "nightlyforward"' locally
    And I run 'openssl x509 -in !{pemFile} -noout -text | grep "Subject" | grep "ES" | grep "Stratio" | grep "test"' locally
    And I run 'openssl x509 -in !{pemFile} -noout -text | grep "DNS" | grep "test" | grep "test.qa"' locally
    Then I run 'rm -f !{pemFile}' locally

  Scenario: Get private part from certificate (/userland)
    Given I get certificate 'test-qa.qa' using deploy-api from path 'testcct' in KEY format
    When I save 'target/test-classes/test-qa.qa.key' in variable 'keyFile'
    Then I run 'openssl rsa -in !{keyFile} -check | grep "RSA key ok"' locally
    Then I run 'rm -f !{keyFile}' locally

  Scenario: Get private part from certificate (/people)
    Given I get certificate 'test-qa.qa' using deploy-api from path 'testcct' in KEY format in /people
    When I save 'target/test-classes/test-qa.qa.key' in variable 'keyFile'
    Then I run 'openssl rsa -in !{keyFile} -check | grep "RSA key ok"' locally
    Then I run 'rm -f !{keyFile}' locally

  Scenario: Get full certificate (/userland)
    Given I get certificate 'test-qa.qa' using deploy-api from path 'testcct' in PEM/KEY format
    When I save 'target/test-classes/test-qa.qa.pem' in variable 'pemFile'
    And I save 'target/test-classes/test-qa.qa.key' in variable 'keyFile'
    Then I run 'openssl x509 -in !{pemFile} -noout' locally with exit status '0'
    And I run 'openssl x509 -in !{pemFile} -noout -text | grep "Issuer: CN" | grep "nightlyforward"' locally
    And I run 'openssl x509 -in !{pemFile} -noout -text | grep "Subject" | grep "ES" | grep "Stratio" | grep "test"' locally
    And I run 'openssl x509 -in !{pemFile} -noout -text | grep "DNS" | grep "test" | grep "test.qa"' locally
    And I run 'openssl rsa -in !{keyFile} -check | grep "RSA key ok"' locally
    Then I run 'rm -f !{pemFile}' locally
    And I run 'rm -f !{keyFile}' locally

  Scenario: Get full certificate (/people)
    Given I get certificate 'test-qa.qa' using deploy-api from path 'testcct' in PEM/KEY format in /people
    When I save 'target/test-classes/test-qa.qa.pem' in variable 'pemFile'
    And I save 'target/test-classes/test-qa.qa.key' in variable 'keyFile'
    Then I run 'openssl x509 -in !{pemFile} -noout' locally with exit status '0'
    And I run 'openssl x509 -in !{pemFile} -noout -text | grep "Issuer: CN" | grep "nightlyforward"' locally
    And I run 'openssl x509 -in !{pemFile} -noout -text | grep "Subject" | grep "ES" | grep "Stratio" | grep "test"' locally
    And I run 'openssl x509 -in !{pemFile} -noout -text | grep "DNS" | grep "test" | grep "test.qa"' locally
    And I run 'openssl rsa -in !{keyFile} -check | grep "RSA key ok"' locally
    Then I run 'rm -f !{pemFile}' locally
    And I run 'rm -f !{keyFile}' locally

  Scenario: Get certificate in pkcs8 format (/userland)
    Given I get certificate 'test-qa.qa' using deploy-api from path 'testcct' in PK8 format
    When I save 'target/test-classes/test-qa.qa.pk8' in variable 'pk8File'
    And I save 'target/test-classes/test-qa.qa.key' in variable 'keyFile'
    And I save 'target/test-classes/test-qa.qa_new.pem' in variable 'newPemFile'
    Then I run 'openssl pkcs8 -in !{pk8File} -inform DER -out !{newPemFile} -nocrypt' locally
    And I run 'openssl rsa -in !{newPemFile} -noout -modulus' locally and save the value in environment variable 'modulusNew'
    And I run 'openssl rsa -in !{keyFile} -noout -modulus' locally and save the value in environment variable 'modulusOrig'
    And '!{modulusNew}' matches '!{modulusNew}'
    Then I run 'rm -f !{pemFile}' locally
    And I run 'rm -f !{keyFile}' locally
    And I run 'rm -f !{pk8File}' locally
    And I run 'rm -f !{newPemFile}' locally

  Scenario: Get certificate in pkcs8 format (/people)
    Given I get certificate 'test-qa.qa' using deploy-api from path 'testcct' in PK8 format in /people
    When I save 'target/test-classes/test-qa.qa.pk8' in variable 'pk8File'
    And I save 'target/test-classes/test-qa.qa.key' in variable 'keyFile'
    And I save 'target/test-classes/test-qa.qa_new.pem' in variable 'newPemFile'
    Then I run 'openssl pkcs8 -in !{pk8File} -inform DER -out !{newPemFile} -nocrypt' locally
    And I run 'openssl rsa -in !{newPemFile} -noout -modulus' locally and save the value in environment variable 'modulusNew'
    And I run 'openssl rsa -in !{keyFile} -noout -modulus' locally and save the value in environment variable 'modulusOrig'
    And '!{modulusNew}' matches '!{modulusNew}'
    Then I run 'rm -f !{pemFile}' locally
    And I run 'rm -f !{keyFile}' locally
    And I run 'rm -f !{pk8File}' locally
    And I run 'rm -f !{newPemFile}' locally

  Scenario: Get certificate in pkcs12 format (/userland)
    Given I get certificate 'test-qa.qa' using deploy-api from path 'testcct' in P12 format and save the password in environment variable 'passP12'
    When I save 'target/test-classes/test-qa.qa.pem' in variable 'pemFile'
    And I save 'target/test-classes/test-qa.qa.key' in variable 'keyFile'
    And I save 'target/test-classes/test-qa.qa.p12' in variable 'p12File'
    Then I run 'openssl pkcs12 -in !{p12File} -passin pass:!{passP12} -passout pass:!{passP12} -clcerts -nokeys | grep "Subject" | grep "ES" | grep "Stratio" | grep "test"' locally
    Then I run 'rm -f !{pemFile}' locally
    And I run 'rm -f !{keyFile}' locally
    And I run 'rm -f !{p12File}' locally

  Scenario: Get certificate in pkcs12 format (/people)
    Given I get certificate 'test-qa.qa' using deploy-api from path 'testcct' in P12 format and save the password in environment variable 'passP12' in /people
    When I save 'target/test-classes/test-qa.qa.pem' in variable 'pemFile'
    And I save 'target/test-classes/test-qa.qa.key' in variable 'keyFile'
    And I save 'target/test-classes/test-qa.qa.p12' in variable 'p12File'
    Then I run 'openssl pkcs12 -in !{p12File} -passin pass:!{passP12} -passout pass:!{passP12} -clcerts -nokeys | grep "Subject" | grep "ES" | grep "Stratio" | grep "test"' locally
    Then I run 'rm -f !{pemFile}' locally
    And I run 'rm -f !{keyFile}' locally
    And I run 'rm -f !{p12File}' locally

  Scenario: Get certificate in JKS (/userland)
    Given I get certificate 'test-qa.qa' using deploy-api from path 'testcct' in JKS and save the password in environment variable 'passJKS'
    When I save 'target/test-classes/test-qa.qa.pem' in variable 'pemFile'
    And I save 'target/test-classes/test-qa.qa.key' in variable 'keyFile'
    And I save 'target/test-classes/test-qa.qa.p12' in variable 'p12File'
    And I save 'target/test-classes/test-qa.qa.jks' in variable 'jksFile'
    Then I run 'keytool -list -v -keystore !{jksFile} -storepass !{passJKS} | grep "CN=test, O=Stratio, C=ES"' locally
    Then I run 'rm -f !{pemFile}' locally
    And I run 'rm -f !{keyFile}' locally
    And I run 'rm -f !{p12File}' locally
    And I run 'rm -f !{jksFile}' locally

  Scenario: Get certificate in JKS (/people)
    Given I get certificate 'test-qa.qa' using deploy-api from path 'testcct' in JKS and save the password in environment variable 'passJKS' in /people
    When I save 'target/test-classes/test-qa.qa.pem' in variable 'pemFile'
    And I save 'target/test-classes/test-qa.qa.key' in variable 'keyFile'
    And I save 'target/test-classes/test-qa.qa.p12' in variable 'p12File'
    And I save 'target/test-classes/test-qa.qa.jks' in variable 'jksFile'
    Then I run 'keytool -list -v -keystore !{jksFile} -storepass !{passJKS} | grep "CN=test, O=Stratio, C=ES"' locally
    Then I run 'rm -f !{pemFile}' locally
    And I run 'rm -f !{keyFile}' locally
    And I run 'rm -f !{p12File}' locally
    And I run 'rm -f !{jksFile}' locally

  Scenario: Get Truststore
    Given I get Truststore containing CA Bundle using deploy-api and save the password in environment variable 'passTruststore'
    When I save 'target/test-classes/ca.crt' in variable 'caFile'
    And I save 'target/test-classes/truststore.jks' in variable 'truststoreFile'
    Then I run 'keytool -list -v -keystore !{truststoreFile} -storepass !{passTruststore} | grep "CN=ca, O=Stratio, C=ES"' locally
    Then I run 'rm -f !{caFile}' locally
    And I run 'rm -f !{truststoreFile}' locally

  Scenario: Get CA bundle
    Given I get CA Bundle using deploy-api
    When I save 'target/test-classes/ca.crt' in variable 'caFile'
    Then I run 'openssl x509 -in !{caFile} -noout' locally with exit status '0'
    And I run 'openssl x509 -in !{caFile} -noout -text | grep "Issuer: CN" | grep "nightlyforward"' locally
    And I run 'openssl x509 -in !{caFile} -noout -text | grep "Subject" | grep "ES" | grep "Stratio" | grep "ca"' locally
    And I run 'openssl x509 -in !{caFile} -noout -text | grep "DNS" | grep "ca" | grep "ca.qa"' locally
    Then I run 'rm -f !{caFile}' locally

  Scenario: Get Keytab (/userland)
    Given I get keytab 'test-qa.qa' using deploy-api from path 'testcct'
    When I save 'target/test-classes/test-qa.qa.keytab' in variable 'keytabFile'
    Then I run 'strings !{keytabFile} | grep -vE "^(>|<)"' locally and save the value in environment variable 'keytabContent'
    And I run 'echo !{keytabContent} | grep "DEMO.STRATIO.COM"' locally
    And I run 'echo !{keytabContent} | grep "test"' locally
    And I run 'echo !{keytabContent} | grep "test.qa"' locally
    Then I run 'rm -f !{keytabFile}' locally

  Scenario: Get Keytab (/people)
    Given I get keytab 'test-qa.qa' using deploy-api from path 'testcct' in /people
    When I save 'target/test-classes/test-qa.qa.keytab' in variable 'keytabFile'
    Then I run 'strings !{keytabFile} | grep -vE "^(>|<)"' locally and save the value in environment variable 'keytabContent'
    And I run 'echo !{keytabContent} | grep "DEMO.STRATIO.COM"' locally
    And I run 'echo !{keytabContent} | grep "test"' locally
    And I run 'echo !{keytabContent} | grep "test.qa"' locally
    Then I run 'rm -f !{keytabFile}' locally

  Scenario: Get Principal (/userland)
    Given I get principal 'test-qa.qa' using deploy-api from path 'testcct' and save it in environment variable 'ppal'
    Then '!{ppal}' matches 'test/test.qa@DEMO.STRATIO.COM'

  Scenario: Get Principal (/people)
    Given I get principal 'test-qa.qa' using deploy-api from path 'testcct' and save it in environment variable 'ppal' in /people
    Then '!{ppal}' matches 'test/test.qa@DEMO.STRATIO.COM'

  Scenario: Get pass (/userland)
    Given I get password using deploy-api from path 'testcct/testqa' and save it in environment variable 'pass'
    Then '!{pass}' matches '654321'

  Scenario: Get pass (/people)
    Given I get password using deploy-api from path 'testcct/testqa' and save it in environment variable 'pass' in /people
    Then '!{pass}' matches '654321'

  Scenario: Get user (/userland)
    Given I get user using deploy-api from path 'testcct/testqa' and save it in environment variable 'user'
    Then '!{user}' matches 'test_qa'

  Scenario: Get user (/people)
    Given I get user using deploy-api from path 'testcct/testqa' and save it in environment variable 'user' in /people
    Then '!{user}' matches 'test_qa'

  Scenario: Try to get secret with wrong path
    Given I get certificate 'secretnonexistant' using deploy-api from path 'testcct' in PEM format
    When I save 'target/test-classes/secretnonexistant.pem' in variable 'secretnonexistantFile'
    Then I run 'cat !{secretnonexistantFile} | grep BEGIN' locally with exit status '1'
