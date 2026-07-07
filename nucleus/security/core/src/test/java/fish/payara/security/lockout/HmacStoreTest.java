/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2026 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */

package fish.payara.security.lockout;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HmacStoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void roundTripSignVerify() throws Exception {
        byte[] key = "k".repeat(32).getBytes();
        byte[] data = "hello".getBytes();
        byte[] mac = HmacStore.sign(key, data);
        assertTrue(HmacStore.verify(key, data, mac));
        assertFalse(HmacStore.verify(key, "tampered".getBytes(), mac));
        assertFalse(HmacStore.verify("wrongkey".repeat(2).getBytes(), data, mac));
    }

    @Test
    public void verifyReturnsFalseForNullMac() throws Exception {
        byte[] key = "k".repeat(32).getBytes();
        assertFalse(HmacStore.verify(key, "data".getBytes(), null));
    }

    @Test
    public void loadOrCreateKeyGeneratesAndPersists() throws Exception {
        File kf = tmp.newFile("key");
        kf.delete(); // ensure absent so loadOrCreateKey generates fresh
        byte[] k1 = HmacStore.loadOrCreateKey(kf);
        assertEquals(32, k1.length);
        byte[] k2 = HmacStore.loadOrCreateKey(kf);
        assertArrayEquals(k1, k2);
    }

    @Test
    public void loadOrCreateKeyInNewSubdir() throws Exception {
        File dir = tmp.newFolder("subdir");
        File kf = new File(dir, "key");
        byte[] k1 = HmacStore.loadOrCreateKey(kf);
        assertEquals(32, k1.length);
        assertTrue(kf.exists());
    }

    @Test
    public void verifyFileDetectsTamperAndMissing() throws Exception {
        byte[] key = "k".repeat(32).getBytes();
        File data = tmp.newFile("d");
        Files.writeString(data.toPath(), "payload");
        File mac = tmp.newFile("d.mac");
        Files.write(mac.toPath(), HmacStore.sign(key, Files.readAllBytes(data.toPath())));
        assertTrue(HmacStore.verifyFile(key, data, mac));
        Files.writeString(data.toPath(), "payload-changed");
        assertFalse(HmacStore.verifyFile(key, data, mac));
        assertTrue(mac.delete());
        assertFalse(HmacStore.verifyFile(key, data, mac));
    }

    @Test
    public void verifyFileReturnsFalseWhenDataFileMissing() throws Exception {
        byte[] key = "k".repeat(32).getBytes();
        File data = tmp.newFile("d");
        assertTrue(data.delete());
        File mac = tmp.newFile("d.mac");
        assertFalse(HmacStore.verifyFile(key, data, mac));
    }

    @Test
    public void trySetOwnerOnlyDoesNotThrow() throws Exception {
        // just exercise the path; on Windows it may not actually change perms, that's fine
        File kf = tmp.newFile("key");
        HmacStore.loadOrCreateKey(kf); // calls trySetOwnerOnly internally
        assertTrue(kf.exists());
    }
}
