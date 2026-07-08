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
 * file and include the License file at legal-OPEN-SOURCE-LICENSE.txt.
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
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests the package-private core of ResetIntegrityCommand --
 * {@link ResetIntegrityCommand#resignKeyfile(File, File, byte[])}.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */
public class ResetIntegrityCommandTest {

    private static final String HMAC_ALGO = "HmacSHA256";

    private File tempDir;
    private File keyfile;
    private File macFile;
    private final byte[] key = "k".repeat(32).getBytes();

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("ri-test").toFile();
        keyfile = new File(tempDir, "kf");
        macFile = new File(tempDir, "kf.mac");
        Files.writeString(keyfile.toPath(), "users...");
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
    public void resignProducesValidMac() throws Exception {
        ResetIntegrityCommand.resignKeyfile(keyfile, macFile, key);

        Mac ref = Mac.getInstance(HMAC_ALGO);
        ref.init(new SecretKeySpec(key, HMAC_ALGO));
        byte[] expected = ref.doFinal(Files.readAllBytes(keyfile.toPath()));

        byte[] actual = Files.readAllBytes(macFile.toPath());
        assertNotEquals(actual.length, 0, ".mac must not be empty");
        assertEquals(actual, expected, "MAC must match independent HmacSHA256");
    }

    @Test
    public void resignOverwritesOldMac() throws Exception {
        Files.writeString(macFile.toPath(), "STALE");

        ResetIntegrityCommand.resignKeyfile(keyfile, macFile, key);

        Mac ref = Mac.getInstance(HMAC_ALGO);
        ref.init(new SecretKeySpec(key, HMAC_ALGO));
        byte[] expected = ref.doFinal(Files.readAllBytes(keyfile.toPath()));

        assertEquals(Files.readAllBytes(macFile.toPath()), expected,
                "MAC must be fresh, not stale");
    }

    @Test
    public void resignCreatesMacFromScratch() throws Exception {
        assertTrue(!macFile.exists(), "no .mac should exist before resign");

        ResetIntegrityCommand.resignKeyfile(keyfile, macFile, key);

        assertTrue(macFile.exists(), ".mac must be created");
        assertTrue(Files.size(macFile.toPath()) > 0, ".mac must not be empty");
    }
}
