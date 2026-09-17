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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.CountingOutputStream;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the commons-compress version that reaches the classpath.
 *
 * kubernetes-client tars a directory up before pushing it through the exec subresource, but it
 * declares commons-compress {@code <optional>}, so nothing about depending on kubernetes-client
 * guarantees a compatible version is present. Whatever else drags commons-compress in decides,
 * and on this classpath hadoop-common 2.10.0 offers 1.19.
 *
 * That combination fails at runtime rather than at build time, in two different ways depending on
 * which version wins:
 *
 * <ul>
 *   <li>1.19 through 1.24 — {@code NoSuchMethodError} on
 *       {@code TarArchiveOutputStream.putArchiveEntry(TarArchiveEntry)}. The covariant overload
 *       kubernetes-client compiles against only exists from 1.25.0; before that there is nothing
 *       but the {@code ArchiveEntry} signature.</li>
 *   <li>1.26 and later — {@code NoClassDefFoundError} on {@code org.apache.commons.lang3.SystemProperties},
 *       because 1.26 needs commons-lang3 3.14 and this classpath is on 3.7.</li>
 * </ul>
 *
 * The mock API server cannot catch either: it does not implement the exec subresource, so
 * {@code copyFileToPod} fails while setting the transfer up and never reaches the tar. This test
 * exercises the tar writing on its own instead, which needs no cluster and no mock.
 *
 * The calls below are every commons-compress member {@code PodUpload} references, read off its
 * bytecode. If a kubernetes-client upgrade changes that set, re-read it and update this test:
 *
 * <pre>
 * javap -p -c -cp &lt;kubernetes-client.jar&gt; io.fabric8.kubernetes.client.dsl.internal.uploadable.PodUpload \
 *   | grep -oE "org/apache/commons/compress/[A-Za-z0-9/]+\.[A-Za-z0-9_&lt;&gt;]+:\([^)]*\)[^ ]*" | sort -u
 * </pre>
 */
public class PodUploadCompatibilityTest {

    @Test
    public void commonsCompressOnTheClasspathSupportsThePodUploadTarFlow() throws IOException {
        Path source = Files.createTempFile("bdt-pod-upload", ".txt");
        Files.write(source, "payload".getBytes(StandardCharsets.UTF_8));

        try {
            CountingOutputStream counting = new CountingOutputStream(new ByteArrayOutputStream());

            try (TarArchiveOutputStream tar = new TarArchiveOutputStream(counting)) {
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);

                // The covariant overload, which is the one that goes missing on commons-compress < 1.25.0.
                TarArchiveEntry entry = new TarArchiveEntry(source.toFile(), "payload.txt");
                tar.putArchiveEntry(entry);
                Files.copy(source, tar);
                tar.closeArchiveEntry();
            }

            assertThat(counting.getBytesWritten())
                    .as("tar written through the same calls PodUpload makes")
                    .isPositive();
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    public void putArchiveEntryAcceptsATarArchiveEntryDirectly() throws NoSuchMethodException {
        // Asserted separately so the failure names the missing signature rather than surfacing as
        // a NoSuchMethodError from the flow above.
        assertThat(TarArchiveOutputStream.class.getMethod("putArchiveEntry", TarArchiveEntry.class))
                .as("kubernetes-client calls putArchiveEntry(TarArchiveEntry); commons-compress < 1.25.0 lacks it")
                .isNotNull();
    }

    @Test
    public void tarEntryCreationDoesNotNeedANewerCommonsLang() throws IOException {
        // TarArchiveEntry(File, String) is where commons-compress 1.26 reaches for
        // commons-lang3 SystemProperties, which 3.7 does not have.
        File source = Files.createTempFile("bdt-pod-upload-entry", ".txt").toFile();
        try {
            assertThat(new TarArchiveEntry(source, "payload.txt").getName()).isEqualTo("payload.txt");
        } finally {
            Files.deleteIfExists(source.toPath());
        }
    }
}
