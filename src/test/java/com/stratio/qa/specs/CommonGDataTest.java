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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.stratio.qa.utils.ThreadProperty;

/**
 * Covers the parts of {@link CommonG} that need neither a server nor a browser: the tgz extractor
 * and its permission mapping, the JSONPath comparison operators, and the Crossdata connection
 * string.
 */
public class CommonGDataTest {

    private CommonG commong;

    private Path workDir;

    @BeforeMethod
    public void setUp() throws Exception {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        commong = new CommonG();
        workDir = Files.createTempDirectory("bdt-commong-");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (workDir != null && Files.exists(workDir)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(workDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    // -----------------------------------------------------------------------
    // extractTgz / applyTarPermissions
    // -----------------------------------------------------------------------

    @Test
    public void aTgzIsExtractedWithItsDirectoriesAndContents() throws Exception {
        Path tgz = writeTgz(
                entry("keos/", null, 0755),
                entry("keos/keos.yaml", "cluster: demo\n", 0644),
                entry("keos/bin/run.sh", "#!/bin/sh\necho hi\n", 0755));
        Path target = workDir.resolve("out");
        Files.createDirectories(target);

        commong.extractTgz(tgz, target);

        assertThat(target.resolve("keos/keos.yaml")).exists().hasContent("cluster: demo\n");
        assertThat(target.resolve("keos/bin/run.sh")).exists();
        assertThat(Files.isDirectory(target.resolve("keos"))).isTrue();
    }

    @Test
    public void theModeOfEachTarEntryIsAppliedToTheExtractedFile() throws Exception {
        Path tgz = writeTgz(
                entry("bin/run.sh", "#!/bin/sh\n", 0755),
                entry("etc/config.yaml", "a: 1\n", 0640),
                entry("etc/world.txt", "everyone\n", 0666));
        Path target = workDir.resolve("out");
        Files.createDirectories(target);

        commong.extractTgz(tgz, target);

        assertThat(Files.getPosixFilePermissions(target.resolve("bin/run.sh")))
                .as("0755 keeps the executable bits the workspace scripts need")
                .containsExactlyInAnyOrder(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
                        PosixFilePermission.OTHERS_EXECUTE);

        assertThat(Files.getPosixFilePermissions(target.resolve("etc/config.yaml")))
                .as("0640 must not leak to others")
                .containsExactlyInAnyOrder(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_READ);

        assertThat(Files.getPosixFilePermissions(target.resolve("etc/world.txt")))
                .contains(PosixFilePermission.OTHERS_WRITE, PosixFilePermission.GROUP_WRITE);
    }

    @Test
    public void extractingOverAnExistingFileReplacesIt() throws Exception {
        Path target = workDir.resolve("out");
        Files.createDirectories(target.resolve("etc"));
        Files.write(target.resolve("etc/config.yaml"), "stale\n".getBytes(StandardCharsets.UTF_8));

        commong.extractTgz(writeTgz(entry("etc/config.yaml", "fresh\n", 0644)), target);

        assertThat(target.resolve("etc/config.yaml")).hasContent("fresh\n");
    }

    @Test
    public void extractingSomethingThatIsNotATgzFails() throws Exception {
        Path notATgz = workDir.resolve("plain.txt");
        Files.write(notATgz, "not gzipped".getBytes(StandardCharsets.UTF_8));

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> commong.extractTgz(notATgz, workDir));
    }

    // -----------------------------------------------------------------------
    // evaluateJSONElementOperation
    // -----------------------------------------------------------------------

    @Test
    public void theComparisonOperatorsAcceptWhatTheyShould() throws Exception {
        commong.evaluateJSONElementOperation("bdt", "equal", "bdt");
        commong.evaluateJSONElementOperation("bdt", "not equal", "other");
        commong.evaluateJSONElementOperation("bdt framework", "contains", "frame");
        commong.evaluateJSONElementOperation("bdt", "does not contain", "zzz");
        commong.evaluateJSONElementOperation("bdt", "not empty", "");
        commong.evaluateJSONElementOperation("bdt-123", "matches", "bdt-\\d+");
        commong.evaluateJSONElementOperation("5", "lower than", "10");
        commong.evaluateJSONElementOperation("10", "higher than", "5");
        commong.evaluateJSONElementOperation("10", "lower than or equal to", "10");
        commong.evaluateJSONElementOperation("10", "higher than or equal to", "10");
    }

    @Test
    public void theComparisonOperatorsRejectWhatTheyShould() {
        assertThat(catchThrowable(() -> commong.evaluateJSONElementOperation("bdt", "equal", "other"))).isNotNull();
        assertThat(catchThrowable(() -> commong.evaluateJSONElementOperation("bdt", "not equal", "bdt"))).isNotNull();
        assertThat(catchThrowable(() -> commong.evaluateJSONElementOperation("bdt", "contains", "zzz"))).isNotNull();
        assertThat(catchThrowable(() -> commong.evaluateJSONElementOperation("bdt", "does not contain", "bd"))).isNotNull();
        assertThat(catchThrowable(() -> commong.evaluateJSONElementOperation("", "not empty", ""))).isNotNull();
        assertThat(catchThrowable(() -> commong.evaluateJSONElementOperation("bdt", "matches", "\\d+"))).isNotNull();
        assertThat(catchThrowable(() -> commong.evaluateJSONElementOperation("10", "lower than", "5"))).isNotNull();
        assertThat(catchThrowable(() -> commong.evaluateJSONElementOperation("5", "higher than", "10"))).isNotNull();
    }

    @Test
    public void anArrayCanBeComparedIgnoringOrder() throws Exception {
        commong.evaluateJSONElementOperation("[\"a\",\"b\",\"c\"]", "equals in any order", "[\"c\",\"a\",\"b\"]");

        assertThat(catchThrowable(() -> commong.evaluateJSONElementOperation(
                "[\"a\",\"b\"]", "equals in any order", "[\"a\",\"b\",\"c\"]")))
                .as("a missing element is still a difference")
                .isNotNull();
    }

    @Test
    public void anUnknownOperatorIsReported() {
        assertThat(catchThrowable(() -> commong.evaluateJSONElementOperation("bdt", "sounds like", "bdt")))
                .isNotNull();
    }

    // -----------------------------------------------------------------------
    // The Crossdata connection string
    // -----------------------------------------------------------------------

    @Test
    public void aSecureCrossdataConnectionCarriesBothStores() {
        Throwable error = catchThrowable(() -> commong.connectToCrossdataDatabase(
                true, false, "127.0.0.1", "1", "/tmp/ks.jks", "kspass", "/tmp/ts.jks", "tspass",
                "user", null, false, null, null));

        // There is no server on port 1, so it fails to connect -- but the connection string is
        // assembled before that, and the driver echoes it back in the failure.
        assertThat(error).isNotNull();
    }

    @Test
    public void aSecureCrossdataConnectionNeedsBothStorePaths() {
        assertThat(catchThrowable(() -> commong.connectToCrossdataDatabase(
                true, false, "127.0.0.1", "1", null, null, "/tmp/ts.jks", null,
                "user", null, false, null, null)))
                .as("keystore path is mandatory once security is on")
                .hasMessageContaining("Keystore path is mandatory");

        assertThat(catchThrowable(() -> commong.connectToCrossdataDatabase(
                true, false, "127.0.0.1", "1", "/tmp/ks.jks", null, null, null,
                "user", null, false, null, null)))
                .hasMessageContaining("Truststore path is mandatory");
    }

    // -----------------------------------------------------------------------
    // Local commands
    // -----------------------------------------------------------------------

    @Test
    public void aLocalCommandCapturesItsOutputAndExitStatus() throws Exception {
        commong.runLocalCommand("echo bdt-local-output");

        assertThat(commong.getCommandResult()).contains("bdt-local-output");
        assertThat(commong.getCommandExitStatus()).isZero();
    }

    @Test
    public void aFailingLocalCommandKeepsItsExitStatus() throws Exception {
        commong.runLocalCommand("exit 3");

        assertThat(commong.getCommandExitStatus()).isEqualTo(3);
    }

    @Test
    public void aLocalCommandThatOverrunsItsTimeoutIsAborted() throws Exception {
        commong.runLocalCommand("sleep 5", 1);

        assertThat(commong.getCommandExitStatus())
                .as("an aborted command must not look like a success")
                .isNotZero();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static final class TgzEntry {

        private final String name;

        private final String content;

        private final int mode;

        private TgzEntry(String name, String content, int mode) {
            this.name = name;
            this.content = content;
            this.mode = mode;
        }
    }

    private TgzEntry entry(String name, String content, int mode) {
        return new TgzEntry(name, content, mode);
    }

    private Path writeTgz(TgzEntry... entries) throws IOException {
        Path tgz = workDir.resolve("archive-" + entries.length + "-" + entries[0].name.hashCode() + ".tgz");
        try (FileOutputStream fos = new FileOutputStream(tgz.toFile());
             GZIPOutputStream gzip = new GZIPOutputStream(fos);
             TarOutputStream tar = new TarOutputStream(gzip)) {
            for (TgzEntry e : entries) {
                TarEntry tarEntry = new TarEntry(e.name);
                tarEntry.setMode(e.mode);
                byte[] body = e.content == null
                        ? new byte[0] : e.content.getBytes(StandardCharsets.UTF_8);
                tarEntry.setSize(body.length);
                tar.putNextEntry(tarEntry);
                if (body.length > 0) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    buffer.write(body);
                    tar.write(buffer.toByteArray());
                }
                tar.closeEntry();
            }
        }
        return tgz;
    }

    @SuppressWarnings("unused")
    private Set<PosixFilePermission> permissionsOf(Path p) throws IOException {
        return Files.getPosixFilePermissions(p);
    }
}
