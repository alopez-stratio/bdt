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
package com.stratio.qa.ATests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.stratio.qa.cucumber.testng.CucumberFeatureWrapper;
import com.stratio.qa.cucumber.testng.PickleEventWrapper;
import com.stratio.qa.utils.BaseGTest;
import cucumber.api.CucumberOptions;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Covers the parts of the SSH surface the existing features leave alone: named connections,
 * switching and closing them by id, the output assertions that are not "contains", the polling
 * variants for both the local and the remote command, and key-based authentication.
 *
 * <p>The key pair is generated here with JSch rather than shelled out to {@code ssh-keygen}, which
 * the CI image does not necessarily carry. The feature installs the public half over the password
 * connection and then reconnects with the private one, so nothing has to be committed and the
 * {@code using pem file} branch -- by far the most used SSH step across qa-unified-tests, 2547
 * occurrences against 0 for the tunnel steps -- gets exercised for real.
 */
@CucumberOptions(plugin = "json:target/cucumber-ssh-connections.json", features = {
        "src/test/resources/features/sshConnections.feature"
})
public class SshConnectionsIT extends BaseGTest {

    /** System properties the feature reads through ${SSH_PEM_FILE} and ${SSH_PUBLIC_KEY}. */
    private static final String PEM_PROPERTY = "SSH_PEM_FILE";

    private static final String PUBLIC_KEY_PROPERTY = "SSH_PUBLIC_KEY";

    private static Path privateKey;

    @BeforeClass(alwaysRun = true)
    public void generateKeyPair() throws JSchException, IOException {
        KeyPair pair = KeyPair.genKeyPair(new JSch(), KeyPair.RSA, 2048);

        privateKey = Paths.get("target", "bdt-it-ssh-key").toAbsolutePath();
        Files.createDirectories(privateKey.getParent());
        pair.writePrivateKey(privateKey.toString());
        // JSch will not touch a key the group or others can read
        privateKey.toFile().setReadable(false, false);
        privateKey.toFile().setReadable(true, true);
        privateKey.toFile().setWritable(false, false);

        ByteArrayOutputStream publicKey = new ByteArrayOutputStream();
        pair.writePublicKey(publicKey, "bdt-it");
        pair.dispose();

        System.setProperty(PEM_PROPERTY, privateKey.toString());
        System.setProperty(PUBLIC_KEY_PROPERTY,
                new String(publicKey.toByteArray(), StandardCharsets.UTF_8).trim());
    }

    @AfterClass(alwaysRun = true)
    public void removeKeyPair() throws IOException {
        if (privateKey != null) {
            Files.deleteIfExists(privateKey);
        }
        System.clearProperty(PEM_PROPERTY);
        System.clearProperty(PUBLIC_KEY_PROPERTY);
    }

    @Test(dataProvider = "scenarios")
    public void run(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
        runScenario(pickleWrapper, featureWrapper);
    }
}
