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

public class KeyfileIntegrityManagerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private byte[] key() { return "k".repeat(32).getBytes(); }

    @Test public void disabledAlwaysPasses() throws Exception {
        File kf = tmp.newFile("kf"); Files.writeString(kf.toPath(), "users...");
        KeyfileIntegrityManager m = new KeyfileIntegrityManager(kf, new File(kf.getParent(), "kf.mac"), key(), false);
        assertTrue(m.verifyOrMigrate());
    }
    @Test public void firstUseMigratesBySigning() throws Exception {
        File kf = tmp.newFile("kf"); Files.writeString(kf.toPath(), "users...");
        File mac = new File(kf.getParent(), "kf.mac");
        KeyfileIntegrityManager m = new KeyfileIntegrityManager(kf, mac, key(), true);
        assertFalse(mac.exists());
        assertTrue(m.verifyOrMigrate());   // macFile missing + keyfile exists -> migrate
        assertTrue(mac.exists());          // signed
    }
    @Test public void validSignaturePasses() throws Exception {
        File kf = tmp.newFile("kf"); Files.writeString(kf.toPath(), "users...");
        File mac = new File(kf.getParent(), "kf.mac");
        KeyfileIntegrityManager m = new KeyfileIntegrityManager(kf, mac, key(), true);
        m.sign();                          // establish baseline
        assertTrue(m.verifyOrMigrate());
    }
    @Test public void tamperedKeyfileFails() throws Exception {
        File kf = tmp.newFile("kf"); Files.writeString(kf.toPath(), "users...");
        File mac = new File(kf.getParent(), "kf.mac");
        KeyfileIntegrityManager m = new KeyfileIntegrityManager(kf, mac, key(), true);
        m.sign();
        Files.writeString(kf.toPath(), "TAMPERED");
        assertFalse(m.verifyOrMigrate());
    }
    @Test public void missingMacWithNoKeyfilePasses() throws Exception {
        File kf = new File(tmp.getRoot(), "absent");
        File mac = new File(kf.getParent(), "kf.mac");
        KeyfileIntegrityManager m = new KeyfileIntegrityManager(kf, mac, key(), true);
        assertTrue(m.verifyOrMigrate());   // nothing to verify yet
    }
    @Test public void signIsNoOpWhenDisabled() throws Exception {
        File kf = tmp.newFile("kf"); Files.writeString(kf.toPath(), "users...");
        File mac = new File(kf.getParent(), "kf.mac");
        KeyfileIntegrityManager m = new KeyfileIntegrityManager(kf, mac, key(), false);
        m.sign();
        assertFalse(mac.exists());
    }
}
