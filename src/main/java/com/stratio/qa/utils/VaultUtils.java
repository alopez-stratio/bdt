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

package com.stratio.qa.utils;

import com.stratio.qa.specs.CommonG;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;


public class VaultUtils {

    private final Logger logger = LoggerFactory.getLogger(VaultUtils.class);

    private final Random random = new Random();

    private CommonG comm = new CommonG();

    private String host;

    private String token;

    private String protocol;

    private String port;

    private String userlandBasePath = "/userland";

    private String peopleBasePath = "/people";

    private String servicesBasePath = "/services";

    private String basePath;

    private String caBasePath = "/ca-trust";

    private int numCaTrust = 1;

    private String certificatesPath = "/certificates/";

    private String kerberosPath = "/kerberos/";

    private String passwordsPath = "/passwords/";

    private String vaultData;

    private String baseOutputSecretPath = "target/test-classes/";

    private String certPubReplacements = "sed 's/-----BEGIN CERTIFICATE-----/-----BEGIN CERTIFICATE-----\\n/g' | sed 's/-----END CERTIFICATE-----/\\n-----END CERTIFICATE-----/g' | sed 's/-----END CERTIFICATE----------BEGIN CERTIFICATE-----/-----END CERTIFICATE-----\\n-----BEGIN CERTIFICATE-----/g'";

    private String certKeyReplacements = "sed 's/-----BEGIN RSA PRIVATE KEY-----/-----BEGIN RSA PRIVATE KEY-----\\n/g' | sed 's/-----END RSA PRIVATE KEY-----/\\n-----END RSA PRIVATE KEY-----/g'";

    public VaultUtils() {
        this.basePath = this.userlandBasePath;
        this.host = System.getProperty("VAULT_HOST") != null ? System.getProperty("VAULT_HOST") : ThreadProperty.get("VAULT_HOST") != null ? ThreadProperty.get("VAULT_HOST") : "";
        this.token = System.getProperty("VAULT_TOKEN") != null ? System.getProperty("VAULT_TOKEN") : ThreadProperty.get("VAULT_TOKEN") != null ? ThreadProperty.get("VAULT_TOKEN") : "";
        this.protocol = System.getProperty("VAULT_PROTOCOL") != null ? "http://" : "https://";
        this.port = System.getProperty("EOS_VAULT_PORT") != null ? System.getProperty("EOS_VAULT_PORT") : ThreadProperty.get("EOS_VAULT_PORT") != null ? ThreadProperty.get("EOS_VAULT_PORT") : "8200";
        ;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Deprecated
    public void setBasePathToPeople() {
        this.basePath = this.peopleBasePath;
    }

    public void setBasePath(String people) {
        if (people != null) {
            if (people.contains("people")) {
                this.basePath = this.peopleBasePath;
            } else {
                this.basePath = this.servicesBasePath;
            }
        } else {
            this.basePath = this.userlandBasePath;
        }
    }

    private void getDataFromPath(String dataPath, String filter) throws Exception {
        String command = "curl -X GET -fskL --tlsv1.2 -H \"X-Vault-Token:" + token + "\" \"" + protocol + host + ":" + port + "/v1" + dataPath + "\" | " + filter;
        logger.debug("Retrieving data from Vault: " + command);
        comm.runLocalCommand(command);
        vaultData = comm.getCommandResult();
    }

    private void getPublicCertificate(String certPath, String certValue) throws Exception {
        String certificatePath = basePath + certificatesPath + certPath;
        String filter = "jq -r '.data.\"" + certValue + "_crt\"'";
        this.getDataFromPath(certificatePath, filter);

        // Write out public part (.pem format)
        String commandGetCertPublic = "echo " + vaultData + " | " + certPubReplacements + " | fold -64 > " + baseOutputSecretPath + certValue + ".pem";
        logger.debug("Getting certificate public part (_crt): " + commandGetCertPublic);
        comm.runLocalCommand(commandGetCertPublic);
    }

    private void getPrivateCertificate(String certPath, String certValue) throws Exception {
        String certificatePath = basePath + certificatesPath + certPath;
        String filter = "jq -r '.data.\"" + certValue + "_key\"'";
        this.getDataFromPath(certificatePath, filter);

        // Write out private part (.key format)
        String commandGetCertPrivate = "echo " + vaultData + " | " + certKeyReplacements + " | fold -64 > " + baseOutputSecretPath + certValue + ".key";
        logger.debug("Getting certificate private part (_key): " + commandGetCertPrivate);
        comm.runLocalCommand(commandGetCertPrivate);
    }

    private void getFullCertificate(String certPath, String certValue) throws Exception {
        // Get whole certificate
        this.getPublicCertificate(certPath, certValue);
        this.getPrivateCertificate(certPath, certValue);
    }

    public void getCABundle(boolean truststore) throws Exception {
        String certificate = "";
        String filter = "";
        String certificatePath = "";
        String commandGetCABundle = "";

        String caTrustListPath = caBasePath + certificatesPath;
        String command = "curl -X LIST -fskL --tlsv1.2 -H \"X-Vault-Token:" + token + "\" \"" + protocol + host + ":" + port + "/v1" + caTrustListPath + "\" | jq -cM .data.keys | jq '. | length'";
        comm.runLocalCommand(command);

        Assertions.assertThat(comm.getCommandResult()).isNotEmpty();

        numCaTrust = Integer.parseInt(comm.getCommandResult());
        for (int i = 0; i < numCaTrust; i++) {
            command = "curl -X LIST -fskL --tlsv1.2 -H \"X-Vault-Token:" + token + "\" \"" + protocol + host + ":" + port + "/v1" + caTrustListPath + "\" | jq -cMr .data.keys[" + i + "]";
            comm.runLocalCommand(command);
            certificate = comm.getCommandResult();

            certificatePath = caBasePath + certificatesPath + certificate;
            filter = "jq -r '.data.\"" + certificate + "_crt\"'";
            this.getDataFromPath(certificatePath, filter);

            commandGetCABundle = "echo " + vaultData + " | " + certPubReplacements + " | fold -64 >> " + baseOutputSecretPath + "ca.crt";
            logger.debug("Getting CA Bundle from '" + certificate + "': " + commandGetCABundle);
            comm.runLocalCommand(commandGetCABundle);

            if (truststore) {
                commandGetCABundle = "echo " + vaultData + " | " + certPubReplacements + " | fold -64 > " + baseOutputSecretPath + "caForTruststore_" + i + ".crt";
                comm.runLocalCommand(commandGetCABundle);
            }
        }
    }

    public void getPEMCertificate(String certPath, String certValue) throws Exception {
        this.getPublicCertificate(certPath, certValue);
    }

    public void getKEYCertificate(String certPath, String certValue) throws Exception {
        this.getPrivateCertificate(certPath, certValue);
    }

    public void getPEMKEYCertificate(String certPath, String certValue) throws Exception {
        this.getFullCertificate(certPath, certValue);
    }

    public void getPKCS12Certificate(String certPath, String certValue, String passVarName) throws Exception {
        this.getFullCertificate(certPath, certValue);

        String pub = baseOutputSecretPath + certValue + ".pem";
        String key = baseOutputSecretPath + certValue + ".key";
        String pass = this.createRandomPassword();

        String commandConvertToP12 = "openssl pkcs12 -export -inkey " + key + " -in " + pub + " -passout pass:" + pass + " -out " + baseOutputSecretPath + certValue + ".p12";
        logger.debug("Converting certificate to P12: " + commandConvertToP12);
        comm.runLocalCommand(commandConvertToP12);

        ThreadProperty.set(passVarName, pass);
    }

    public void getPKCS8Certificate(String certPath, String certValue) throws Exception {
        this.getFullCertificate(certPath, certValue);

        String key = baseOutputSecretPath + certValue + ".key";

        String commandConvertToPK8 = "openssl pkcs8 -topk8 -inform PEM -outform DER -in " + key + " -out " + baseOutputSecretPath + certValue + ".pk8 -nocrypt";
        logger.debug("Converting certificate to PK8: " + commandConvertToPK8);
        comm.runLocalCommand(commandConvertToPK8);
    }

    public void getTruststoreCABundle(String passVarName) throws Exception {
        this.getCABundle(true);

        String ca = "";
        String commandCreateTruststore = "";
        String pass = this.createRandomPassword();

        for (int i = 0; i < numCaTrust; i++) {
            ca = baseOutputSecretPath + "caForTruststore_" + i + ".crt";
            commandCreateTruststore = "keytool -import -noprompt -alias ca" + i + " -keystore " + baseOutputSecretPath + "truststore.jks -storepass " + pass + " -file " + ca + " 2>&1";
            logger.debug("Creating Truststore with CA Bundle from '" + ca + "': " + commandCreateTruststore);
            comm.runLocalCommand(commandCreateTruststore);
        }

        ThreadProperty.set(passVarName, pass);
    }

    public void getKeystore(String certPath, String certValue, String passVarName) throws Exception {
        this.getPKCS12Certificate(certPath, certValue, passVarName);

        String p12 = baseOutputSecretPath + certValue + ".p12";
        String pass = ThreadProperty.get(passVarName);

        String commandCreateKeystore = "keytool -noprompt -importkeystore -srckeystore " + p12 + " -srcstorepass " + pass + " -srcstoretype PKCS12 -destkeystore " + baseOutputSecretPath + certValue + ".jks -deststorepass " + pass + " 2>&1";
        logger.debug("Creating Keystore with certificate '" + certValue + "': " + commandCreateKeystore);
        comm.runLocalCommand(commandCreateKeystore);
    }

    private String createRandomPassword() {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;

        String generatedString = random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        return generatedString;
    }

    private void getKeytabKerberos(String krbPath, String krbValue) throws Exception {
        String keytabPath = basePath + kerberosPath + krbPath;
        String filter = "jq -r '.data.\"" + krbValue + "_keytab\"'";
        this.getDataFromPath(keytabPath, filter);

        // Write out keytab (.keytab format)
        String commandGetKeytab = "echo " + vaultData + " | base64 -d > " + baseOutputSecretPath + krbValue + ".keytab";
        logger.debug("Getting Keytab part (_keytab): " + commandGetKeytab);
        comm.runLocalCommand(commandGetKeytab);
    }

    private String getPrincipalKerberos(String krbPath, String krbValue) throws Exception {
        String principalPath = basePath + kerberosPath + krbPath;
        String filter = "jq -r '.data.\"" + krbValue + "_principal\"'";
        this.getDataFromPath(principalPath, filter);

        // Return 'principal'
        return vaultData;
    }

    public void getKeytabKrb(String krbPath, String krbValue) throws Exception {
        this.getKeytabKerberos(krbPath, krbValue);
    }

    public void getPrincipalKrb(String krbPath, String krbValue, String principalVarName) throws Exception {
        String ppal = this.getPrincipalKerberos(krbPath, krbValue);

        ThreadProperty.set(principalVarName, ppal);
    }

    private String getPassPassword(String passPath) throws Exception {
        String passwordPath = basePath + passwordsPath + passPath;
        String filter = "jq -r '.data.\"pass\"'";
        this.getDataFromPath(passwordPath, filter);

        // Return 'pass'
        return vaultData;
    }

    private String getUserPassword(String passPath) throws Exception {
        String passwordPath = basePath + passwordsPath + passPath;
        String filter = "jq -r '.data.\"user\"'";
        this.getDataFromPath(passwordPath, filter);

        // Return 'user'
        return vaultData;
    }

    private String getSecret(String passPath) throws Exception {
        String passwordPath = basePath + passwordsPath + passPath;
        String filter = "jq -r";
        this.getDataFromPath(passwordPath, filter);

        // Return 'secret'
        return vaultData;
    }

    public void getPass(String passPath, String passVarName) throws Exception {
        String pass = this.getPassPassword(passPath);

        ThreadProperty.set(passVarName, pass);
    }

    public void getUser(String passPath, String passVarName) throws Exception {
        String user = this.getUserPassword(passPath);

        ThreadProperty.set(passVarName, user);
    }

    public void getSecret(String passPath, String passVarName) throws Exception {
        String secret = this.getSecret(passPath);

        ThreadProperty.set(passVarName, secret);
    }

}
