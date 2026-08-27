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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import okhttp3.mockwebserver.MockWebServer;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Turns a {@link MockWebServer} into an HTTPS one.
 *
 * <p>Every CCT and Gosec client hardcodes {@code https://} into the URL it builds, so a plaintext
 * mock cannot stand in for them. okhttp-tls is not on the classpath, but Bouncy Castle is, so the
 * certificate is minted here and handed straight to the server. Callers point an
 * {@code AsyncHttpClient} configured with {@code setUseInsecureTrustManager(true)} at it -- which
 * is what {@code CommonG} builds for the real thing anyway.
 */
public final class MockHttpsServer {

    private static final String PASSWORD = "bdt-it";

    private MockHttpsServer() {
    }

    /**
     * Start {@code server} with TLS, using a freshly minted self-signed certificate.
     */
    public static void enableTls(MockWebServer server) throws Exception {
        server.useHttps(socketFactory(), false);
    }

    private static SSLSocketFactory socketFactory() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        long now = 1735689600000L; // 2025-01-01, fixed: scripts must not depend on the wall clock
        X500Name subject = new X500Name("CN=localhost");
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(
                        subject,
                        BigInteger.valueOf(1),
                        new Date(now),
                        new Date(now + 3650L * 24 * 60 * 60 * 1000),
                        subject,
                        keyPair.getPublic())
                        .build(signer(keyPair)));

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("mock", keyPair.getPrivate(), PASSWORD.toCharArray(),
                new X509Certificate[] {certificate});

        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, PASSWORD.toCharArray());

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, new SecureRandom());
        return context.getSocketFactory();
    }

    private static ContentSigner signer(KeyPair keyPair) throws Exception {
        return new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
    }
}
