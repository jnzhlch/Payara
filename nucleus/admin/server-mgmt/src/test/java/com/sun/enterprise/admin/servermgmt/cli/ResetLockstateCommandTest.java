/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2026 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/main/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at legal/OPEN-SOURCE-LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */
package com.sun.enterprise.admin.servermgmt.cli;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Properties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests the package-private core of ResetLockstateCommand --
 * {@link ResetLockstateCommand#rebuildEmptyLockstate(File, byte[])} and
 * {@link ResetLockstateCommand#computeMac(byte[], byte[])}.
 *
 * <p>No domain.xml or HK2 assembly needed; the testable core is a static
 * method operating on a temp directory.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */
public class ResetLockstateCommandTest {

    private static final String HMAC_ALGO = "HmacSHA256";

    private File tempDir;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("resetlock-test").toFile();
    }

    @AfterMethod
    public void tearDown() {
        if (tempDir != null) {
            deleteRecursively(tempDir);
        }
    }

    private static void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursively(c);
                }
            }
        }
        f.delete();
    }

    @Test
    public void computeMac_producesHmacSHA256() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        byte[] data = "hello world".getBytes();

        byte[] mac = ResetLockstateCommand.computeMac(key, data);

        // Verify with independent Mac instance
        Mac ref = Mac.getInstance(HMAC_ALGO);
        ref.init(new SecretKeySpec(key, HMAC_ALGO));
        byte[] expected = ref.doFinal(data);
        assertEquals(expected, mac, "MAC must match independent HmacSHA256");
    }

    @Test
    public void computeMac_deterministic() {
        byte[] key = new byte[32];
        byte[] data = "test".getBytes();
        assertEquals(
                ResetLockstateCommand.computeMac(key, data),
                ResetLockstateCommand.computeMac(key, data));
    }

    @Test
    public void rebuildEmptyLockstate_replacesOldFiles() throws IOException {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        // Pre-existing lockstate with a user entry
        File oldState = new File(tempDir, "admin-keyfile.lockstate");
        Files.writeString(oldState.toPath(), "someuser=3,9999999999999\n");
        File oldMac = new File(tempDir, "admin-keyfile.lockstate.mac");
        Files.writeString(oldMac.toPath(), "garbage-mac");

        byte[] oldStateBytes = Files.readAllBytes(oldState.toPath());

        ResetLockstateCommand.rebuildEmptyLockstate(tempDir, key);

        // New state must be different (no user entries)
        byte[] newStateBytes = Files.readAllBytes(oldState.toPath());
        assertFalse(java.util.Arrays.equals(oldStateBytes, newStateBytes),
                "Lockstate content should have changed");

        // New state must be empty Properties (no user entries)
        Properties p = new Properties();
        p.load(new StringReader(new String(newStateBytes)));
        assertTrue(p.stringPropertyNames().isEmpty(),
                "New lockstate should have no user entries");
    }

    @Test
    public void rebuildEmptyLockstate_macVerifiesWithIndependentHmac() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        ResetLockstateCommand.rebuildEmptyLockstate(tempDir, key);

        File stateFile = new File(tempDir, "admin-keyfile.lockstate");
        File macFile = new File(tempDir, "admin-keyfile.lockstate.mac");

        assertTrue(stateFile.exists(), "lockstate file must exist");
        assertTrue(macFile.exists(), "mac sidecar must exist");

        byte[] stateBytes = Files.readAllBytes(stateFile.toPath());
        byte[] macBytes = Files.readAllBytes(macFile.toPath());

        // Independent verify
        Mac ref = Mac.getInstance(HMAC_ALGO);
        ref.init(new SecretKeySpec(key, HMAC_ALGO));
        byte[] expected = ref.doFinal(stateBytes);
        assertEquals(expected, macBytes,
                "MAC sidecar must verify with independent HmacSHA256");
    }

    @Test
    public void rebuildEmptyLockstate_createsFromScratch() throws IOException {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        // No pre-existing files
        ResetLockstateCommand.rebuildEmptyLockstate(tempDir, key);

        File stateFile = new File(tempDir, "admin-keyfile.lockstate");
        File macFile = new File(tempDir, "admin-keyfile.lockstate.mac");
        assertTrue(stateFile.exists());
        assertTrue(macFile.exists());
        assertTrue(Files.size(stateFile.toPath()) > 0);
        assertTrue(Files.size(macFile.toPath()) > 0);
    }
}
