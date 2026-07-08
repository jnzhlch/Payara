/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2026 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 only,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/gpl-2.0.txt.
 */

package fish.payara.security.auth.realm;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * End-to-end TDD: keyfile HMAC integrity wired into admin-realm load/persist.
 * First init migrates and succeeds; tampered keyfile blocks reload with ISE.
 */
public class AdminFileRealmIntegrityTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File newTempFile(String name) {
        return new File(tmp.getRoot(), name);
    }

    private AdminFileRealm realmWithIntegrity() throws Exception {
        File keyfile = newTempFile("kf");
        File stateFile = newTempFile("lockstate");
        File keyFile = newTempFile("key");
        File macFile = newTempFile("kf.mac");
        Properties p = new Properties();
        p.setProperty("file", keyfile.getAbsolutePath());
        p.setProperty("jaas-context", "fileRealm");
        p.setProperty("integrityEnabled", "true");
        p.setProperty("integrityMacFile", macFile.getAbsolutePath());
        p.setProperty("lockstateFile", stateFile.getAbsolutePath());
        p.setProperty("integrityKeyFile", keyFile.getAbsolutePath());
        AdminFileRealm r = new AdminFileRealm();
        r.init(p);
        return r;
    }

    @Test
    public void firstInitMigratesAndSucceeds() throws Exception {
        AdminFileRealm r = realmWithIntegrity();
        r.addUser("alice", "pw".toCharArray(), new String[]{"g"});
        r.persist();
        assertNotNull(r.authenticate("alice", "pw".toCharArray()));
    }

    @Test(expected = IllegalStateException.class)
    public void tamperedKeyfileBlocksReload() throws Exception {
        AdminFileRealm r = realmWithIntegrity();
        r.addUser("alice", "pw".toCharArray(), new String[]{"g"});
        r.persist();
        File keyfile = new File(r.getProperty("file"));
        Files.writeString(keyfile.toPath(), "TAMPERED");
        // re-init same keyfile -> verify fails -> IllegalStateException
        Properties p2 = new Properties();
        p2.setProperty("file", keyfile.getAbsolutePath());
        p2.setProperty("jaas-context", "fileRealm");
        p2.setProperty("integrityEnabled", "true");
        p2.setProperty("integrityMacFile",
                new File(keyfile.getParent(), "kf.mac").getAbsolutePath());
        p2.setProperty("lockstateFile",
                new File(keyfile.getParent(), "lockstate").getAbsolutePath());
        p2.setProperty("integrityKeyFile",
                new File(keyfile.getParent(), "key").getAbsolutePath());
        AdminFileRealm r2 = new AdminFileRealm();
        r2.init(p2); // should throw
    }

    @Test(expected = IllegalStateException.class)
    public void macDeletionOnExistingDeploymentBlocksReload() throws Exception {
        // Phase 1: full init + persist (establishes HMAC key + .mac)
        AdminFileRealm r = realmWithIntegrity();
        r.addUser("alice", "pw".toCharArray(), new String[]{"g"});
        r.persist();
        File keyfile = new File(r.getProperty("file"));
        File macFile = new File(keyfile.getParent(), "kf.mac");
        assertTrue("precondition: .mac exists after persist", macFile.exists());

        // Phase 2: attacker tampers keyfile + deletes .mac
        Files.writeString(keyfile.toPath(), "TAMPERED");
        macFile.delete();
        assertFalse("precondition: .mac deleted", macFile.exists());

        // Phase 3: re-init on existing deployment (keyFile already exists -> firstHardeningEnable=false)
        //         -> .mac missing + keyfile exists -> verifyOrMigrate returns false -> ISE
        Properties p2 = new Properties();
        p2.setProperty("file", keyfile.getAbsolutePath());
        p2.setProperty("jaas-context", "fileRealm");
        p2.setProperty("integrityEnabled", "true");
        p2.setProperty("integrityMacFile", macFile.getAbsolutePath());
        p2.setProperty("lockstateFile",
                new File(keyfile.getParent(), "lockstate").getAbsolutePath());
        p2.setProperty("integrityKeyFile",
                new File(keyfile.getParent(), "key").getAbsolutePath());
        AdminFileRealm r2 = new AdminFileRealm();
        r2.init(p2); // should throw IllegalStateException
    }

    @Test
    public void integrityDisabledSkipsVerify() throws Exception {
        File keyfile = newTempFile("kf2");
        File stateFile = newTempFile("lockstate2");
        File keyFile = newTempFile("key2");
        File macFile = newTempFile("kf2.mac");
        Properties p = new Properties();
        p.setProperty("file", keyfile.getAbsolutePath());
        p.setProperty("jaas-context", "fileRealm");
        p.setProperty("integrityEnabled", "false");
        p.setProperty("integrityMacFile", macFile.getAbsolutePath());
        p.setProperty("lockstateFile", stateFile.getAbsolutePath());
        p.setProperty("integrityKeyFile", keyFile.getAbsolutePath());
        AdminFileRealm r = new AdminFileRealm();
        r.init(p);
        r.addUser("bob", "pass".toCharArray(), new String[]{"g"});
        r.persist();
        // Tamper the keyfile — since integrity is disabled, reload should succeed
        Files.writeString(keyfile.toPath(), "TAMPERED");
        Properties p2 = new Properties();
        p2.setProperty("file", keyfile.getAbsolutePath());
        p2.setProperty("jaas-context", "fileRealm");
        p2.setProperty("integrityEnabled", "false");
        p2.setProperty("integrityMacFile",
                new File(keyfile.getParent(), "kf2.mac").getAbsolutePath());
        p2.setProperty("lockstateFile",
                new File(keyfile.getParent(), "lockstate2").getAbsolutePath());
        p2.setProperty("integrityKeyFile",
                new File(keyfile.getParent(), "key2").getAbsolutePath());
        AdminFileRealm r2 = new AdminFileRealm();
        r2.init(p2); // should NOT throw
    }
}
