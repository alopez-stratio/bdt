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

import com.stratio.qa.specs.BaseGSpec;
import com.stratio.qa.specs.CommonG;
import com.stratio.qa.specs.RestSpec;
import java.util.Arrays;
import java.util.Random;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CCTUtils extends BaseGSpec {

    private final Logger logger = LoggerFactory.getLogger(VaultUtils.class);

    private final Random random = new Random();

    RestSpec restSpec;

    private String secretsBaseUrl = "/service/" + ThreadProperty.get("deploy_api_id") + "/secrets";

    private String secretsUserlandBasePath = "/userland";

    private String secretsPeopleBasePath = "/people";

    private String secretsBasePath;

    private String secretsCABasePath = "/ca-trust";

    private int secretsNumCaTrust = 1;

    private String secretsCertificatesPath = "/certificates/";

    private String secretsKerberosPath = "/kerberos/";

    private String secretsPasswordsPath = "/passwords/";

    private String secretData;

    private String secretsBaseOutputPath = "target/test-classes/";

    private String secretsCertPubReplacements = "sed 's/-----BEGIN CERTIFICATE-----/-----BEGIN CERTIFICATE-----\\n/g' | sed 's/-----END CERTIFICATE-----/\\n-----END CERTIFICATE-----/g' | sed 's/-----END CERTIFICATE----------BEGIN CERTIFICATE-----/-----END CERTIFICATE-----\\n-----BEGIN CERTIFICATE-----/g'";

    private String secretsCertKeyReplacements = "sed 's/-----BEGIN RSA PRIVATE KEY-----/-----BEGIN RSA PRIVATE KEY-----\\n/g' | sed 's/-----END RSA PRIVATE KEY-----/\\n-----END RSA PRIVATE KEY-----/g'";

    public CCTUtils(CommonG spec) {
        this.commonspec = spec;
        this.restSpec = new RestSpec(spec);
        this.secretsBasePath = this.secretsUserlandBasePath;
    }

    public void setSecretsBasePath(boolean people) {
        if (people) {
            this.secretsBasePath = this.secretsPeopleBasePath;
        } else {
            this.secretsBasePath = this.secretsUserlandBasePath;
        }
    }

    private void getSecretDataFromPath(String dataPath, String filter) throws Exception {
        // Set REST connection
        commonspec.setCCTConnection(null, null);

        String urlParams = "?path=" + dataPath.replaceAll("/", "%2F");
        logger.debug("Retrieving secret data from: " + secretsBaseUrl + urlParams);

        restSpec.sendRequestNoDataTable("GET", secretsBaseUrl + urlParams, null, null, null);

        secretData = commonspec.getJSONPathString(commonspec.getResponse().getResponse(), filter, null);
    }

    private void getPublicCertificate(String certPath, String certValue) throws Exception {
        String certificatePath = secretsBasePath + secretsCertificatesPath + certPath;
        String filter = "$.[\"" + certValue + "_crt\"]";

        getSecretDataFromPath(certificatePath, filter);

        // Write out public part (.pem format)
        String commandGetCertPublic = "echo " + secretData + " | " + secretsCertPubReplacements + " | fold -64 > " + secretsBaseOutputPath + certValue + ".pem";
        logger.debug("Getting certificate public part (_crt): " + commandGetCertPublic);
        commonspec.runLocalCommand(commandGetCertPublic);
    }

    private void getPrivateCertificate(String certPath, String certValue) throws Exception {
        String certificatePath = secretsBasePath + secretsCertificatesPath + certPath;
        String filter = "$.[\"" + certValue + "_key\"]";

        getSecretDataFromPath(certificatePath, filter);

        // Write out private part (.key format)
        String commandGetCertPrivate = "echo " + secretData + " | " + secretsCertKeyReplacements + " | fold -64 > " + secretsBaseOutputPath + certValue + ".key";
        logger.debug("Getting certificate private part (_key): " + commandGetCertPrivate);
        commonspec.runLocalCommand(commandGetCertPrivate);
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

        // Set REST connection
        commonspec.setCCTConnection(null, null);

        String caTrustListPath = secretsCABasePath + secretsCertificatesPath;
        String urlParams = "/directory?path=" + caTrustListPath.replaceAll("/", "%2F");

        restSpec.sendRequestNoDataTable("GET", secretsBaseUrl + urlParams, null, null, null);
        String caList = commonspec.getResponse().getResponse();

        filter = "$.length()";
        secretsNumCaTrust = Integer.parseInt(commonspec.getJSONPathString(caList, filter, null));

        Assertions.assertThat(secretsNumCaTrust).isGreaterThan(0);

        for (int i = 0; i < secretsNumCaTrust; i++) {
            filter = "$.[" + i + "]";
            certificate = commonspec.getJSONPathString(caList, filter, null).replaceAll("\"", "");

            certificatePath = caTrustListPath + certificate;
            filter = "$.[\"" + certificate + "_crt\"]";

            this.getSecretDataFromPath(certificatePath, filter);

            commandGetCABundle = "echo " + secretData + " | " + secretsCertPubReplacements + " | fold -64 >> " + secretsBaseOutputPath + "ca.crt";
            logger.debug("Getting CA Bundle from '" + certificate + "': " + commandGetCABundle);
            commonspec.runLocalCommand(commandGetCABundle);

            if (truststore) {
                commandGetCABundle = "echo " + secretData + " | " + secretsCertPubReplacements + " | fold -64 > " + secretsBaseOutputPath + "caForTruststore_" + i + ".crt";
                commonspec.runLocalCommand(commandGetCABundle);
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

        String pub = secretsBaseOutputPath + certValue + ".pem";
        String key = secretsBaseOutputPath + certValue + ".key";
        String pass = this.createRandomPassword();

        String commandConvertToP12 = "openssl pkcs12 -export -inkey " + key + " -in " + pub + " -passout pass:" + pass + " -out " + secretsBaseOutputPath + certValue + ".p12";
        logger.debug("Converting certificate to P12: " + commandConvertToP12);
        commonspec.runLocalCommand(commandConvertToP12);

        ThreadProperty.set(passVarName, pass);
    }

    public void getPKCS8Certificate(String certPath, String certValue) throws Exception {
        this.getFullCertificate(certPath, certValue);

        String key = secretsBaseOutputPath + certValue + ".key";

        String commandConvertToPK8 = "openssl pkcs8 -topk8 -inform PEM -outform DER -in " + key + " -out " + secretsBaseOutputPath + certValue + ".pk8 -nocrypt";
        logger.debug("Converting certificate to PK8: " + commandConvertToPK8);
        commonspec.runLocalCommand(commandConvertToPK8);
    }

    public void getTruststoreCABundle(String passVarName) throws Exception {
        this.getCABundle(true);

        String ca = "";
        String commandCreateTruststore = "";
        String pass = this.createRandomPassword();

        for (int i = 0; i < secretsNumCaTrust; i++) {
            ca = secretsBaseOutputPath + "caForTruststore_" + i + ".crt";
            commandCreateTruststore = "keytool -import -noprompt -alias ca" + i + " -keystore " + secretsBaseOutputPath + "truststore.jks -storepass " + pass + " -file " + ca + " 2>&1";
            logger.debug("Creating Truststore with CA Bundle from '" + ca + "': " + commandCreateTruststore);
            commonspec.runLocalCommand(commandCreateTruststore);
        }

        ThreadProperty.set(passVarName, pass);
    }

    public void getKeystore(String certPath, String certValue, String passVarName) throws Exception {
        this.getPKCS12Certificate(certPath, certValue, passVarName);

        String p12 = secretsBaseOutputPath + certValue + ".p12";
        String pass = ThreadProperty.get(passVarName);

        String commandCreateKeystore = "keytool -noprompt -importkeystore -srckeystore " + p12 + " -srcstorepass " + pass + " -srcstoretype PKCS12 -destkeystore " + secretsBaseOutputPath + certValue + ".jks -deststorepass " + pass + " 2>&1";
        logger.debug("Creating Keystore with certificate '" + certValue + "': " + commandCreateKeystore);
        commonspec.runLocalCommand(commandCreateKeystore);
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
        String keytabPath = secretsBasePath + secretsKerberosPath + krbPath;
        String filter = "$.[\"" + krbValue + "_keytab\"]";

        getSecretDataFromPath(keytabPath, filter);

        // Write out keytab (.keytab format)
        String commandGetKeytab = "echo " + secretData + " | base64 -d > " + secretsBaseOutputPath + krbValue + ".keytab";
        logger.debug("Getting Keytab part (_keytab): " + commandGetKeytab);
        commonspec.runLocalCommand(commandGetKeytab);
    }

    private String getPrincipalKerberos(String krbPath, String krbValue) throws Exception {
        String principalPath = secretsBasePath + secretsKerberosPath + krbPath;
        String filter = "$.[\"" + krbValue + "_principal\"]";

        getSecretDataFromPath(principalPath, filter);

        // Return 'principal'
        return secretData;
    }

    public void getKeytabKrb(String krbPath, String krbValue) throws Exception {
        this.getKeytabKerberos(krbPath, krbValue);
    }

    public void getPrincipalKrb(String krbPath, String krbValue, String principalVarName) throws Exception {
        String ppal = this.getPrincipalKerberos(krbPath, krbValue);

        ThreadProperty.set(principalVarName, ppal);
    }

    private String getPassPassword(String passPath) throws Exception {
        String passwordPath = secretsBasePath + secretsPasswordsPath + passPath;
        String filter = "$.[\"pass\"]";

        getSecretDataFromPath(passwordPath, filter);

        // Return 'pass'
        return secretData;
    }

    private String getUserPassword(String passPath) throws Exception {
        String passwordPath = secretsBasePath + secretsPasswordsPath + passPath;
        String filter = "$.[\"user\"]";

        getSecretDataFromPath(passwordPath, filter);

        // Return 'user'
        return secretData;
    }

    public void getPass(String passPath, String passVarName) throws Exception {
        String pass = this.getPassPassword(passPath);

        ThreadProperty.set(passVarName, pass);
    }

    public void getUser(String passPath, String passVarName) throws Exception {
        String user = this.getUserPassword(passPath);

        ThreadProperty.set(passVarName, user);
    }

}
