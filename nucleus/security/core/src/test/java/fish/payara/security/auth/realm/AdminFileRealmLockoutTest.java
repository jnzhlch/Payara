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
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * End-to-end TDD: verifies AdminFileRealm wires lockout into the full
 * authenticate path via AdminFileRealmStorageManager hooks.
 */
public class AdminFileRealmLockoutTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Creates an AdminFileRealm with lockout enabled (maxAttempts=2),
     * adds user "alice" with password "secret" in group "g", and persists.
     */
    private File newTempFile(String name) {
        return new File(tmp.getRoot(), name);
    }

    private AdminFileRealm realmWithLockout() throws Exception {
        File keyfile = newTempFile("kf");
        File stateFile = newTempFile("lockstate");
        File keyFile = newTempFile("key");
        Properties props = new Properties();
        props.setProperty("file", keyfile.getAbsolutePath());
        props.setProperty("jaas-context", "fileRealm");
        props.setProperty("lockoutEnabled", "true");
        props.setProperty("maxLoginAttempts", "2");
        props.setProperty("lockoutDurationSec", "300");
        props.setProperty("lockoutExemptUsers", "admin");
        props.setProperty("lockstateFile", stateFile.getAbsolutePath());
        props.setProperty("integrityKeyFile", keyFile.getAbsolutePath());
        AdminFileRealm r = new AdminFileRealm();
        r.init(props);
        r.addUser("alice", "secret".toCharArray(), new String[]{"g"});
        r.persist();
        return r;
    }

    @Test
    public void wrongPasswordTwiceLocksThirdBlockedEvenWithCorrectPassword() throws Exception {
        AdminFileRealm r = realmWithLockout();
        assertNull(r.authenticate("alice", "wrong".toCharArray())); // fail 1
        assertNull(r.authenticate("alice", "wrong".toCharArray())); // fail 2 -> locked
        assertNull(r.authenticate("alice", "secret".toCharArray())); // locked, correct pwd still blocked
    }

    @Test
    public void correctPasswordResetsAndAuthenticates() throws Exception {
        AdminFileRealm r = realmWithLockout();
        assertNull(r.authenticate("alice", "wrong".toCharArray())); // fail 1
        String[] groups = r.authenticate("alice", "secret".toCharArray()); // success -> reset
        assertNotNull(groups);
        assertArrayEquals(new String[]{"g"}, groups);
    }

    @Test
    public void exemptUserNeverLocks() throws Exception {
        AdminFileRealm r = realmWithLockout();
        r.addUser("admin", "pw".toCharArray(), new String[]{"asadmin"});
        r.persist();
        for (int i = 0; i < 5; i++) {
            assertNull(r.authenticate("admin", "wrong".toCharArray()));
        }
        // still authenticatable with correct password despite 5 failures
        assertNotNull(r.authenticate("admin", "pw".toCharArray()));
    }

    @Test
    public void nonExistentUserReturnsNullWithoutCounting() throws Exception {
        AdminFileRealm r = realmWithLockout();
        assertNull(r.authenticate("nobody", "any".toCharArray()));
        // alice should still be fully authenticatable (no side-effect lockouts)
        assertNotNull(r.authenticate("alice", "secret".toCharArray()));
    }

    @Test
    public void createStorageManagerDefaultsWhenPropsMissing() throws Exception {
        // Only required props; lockout defaults should kick in
        File keyfile = newTempFile("kf2");
        File stateFile = newTempFile("lockstate2");
        File keyFile = newTempFile("key2");
        Properties props = new Properties();
        props.setProperty("file", keyfile.getAbsolutePath());
        props.setProperty("jaas-context", "fileRealm");
        props.setProperty("lockstateFile", stateFile.getAbsolutePath());
        props.setProperty("integrityKeyFile", keyFile.getAbsolutePath());
        AdminFileRealm r = new AdminFileRealm();
        r.init(props);
        r.addUser("bob", "pass".toCharArray(), new String[]{"g"});
        r.persist();
        // Default maxAttempts=5, so 2 failures should NOT lock
        assertNull(r.authenticate("bob", "wrong".toCharArray()));
        assertNull(r.authenticate("bob", "wrong".toCharArray()));
        assertNotNull(r.authenticate("bob", "pass".toCharArray()));
    }

    @Test
    public void lockoutDisabledAllowsUnlimitedFailures() throws Exception {
        File keyfile = newTempFile("kf3");
        File stateFile = newTempFile("lockstate3");
        File keyFile = newTempFile("key3");
        Properties props = new Properties();
        props.setProperty("file", keyfile.getAbsolutePath());
        props.setProperty("jaas-context", "fileRealm");
        props.setProperty("lockoutEnabled", "false");
        props.setProperty("maxLoginAttempts", "2");
        props.setProperty("lockoutDurationSec", "300");
        props.setProperty("lockoutExemptUsers", "");
        props.setProperty("lockstateFile", stateFile.getAbsolutePath());
        props.setProperty("integrityKeyFile", keyFile.getAbsolutePath());
        AdminFileRealm r = new AdminFileRealm();
        r.init(props);
        r.addUser("carol", "pass".toCharArray(), new String[]{"g"});
        r.persist();
        for (int i = 0; i < 5; i++) {
            assertNull(r.authenticate("carol", "wrong".toCharArray()));
        }
        // lockout disabled -> should still work
        assertNotNull(r.authenticate("carol", "pass".toCharArray()));
    }

    @Test
    public void firstHardeningEnableWithExistingKeyfileDoesNotFailClosed() throws Exception {
        // Simulates real domain: admin-keyfile already exists on disk (pre-populated),
        // but lockstate and HMAC key do not (first hardening enable).
        // Old code: signal=new File(file) → exists → ISE on missing lockstate → server won't start.
        // Fixed code: signal=HMAC key file → absent → no ISE → normal first-use init.
        File keyfile = tmp.newFile("admin-keyfile"); // empty, already on disk
        File stateFile = newTempFile("lockstate-first");
        File keyFile = newTempFile("key-first");
        Properties props = new Properties();
        props.setProperty("file", keyfile.getAbsolutePath());
        props.setProperty("jaas-context", "fileRealm");
        props.setProperty("lockstateFile", stateFile.getAbsolutePath());
        props.setProperty("integrityKeyFile", keyFile.getAbsolutePath());
        AdminFileRealm r = new AdminFileRealm();
        r.init(props); // must NOT throw ISE
        r.addUser("alice", "secret".toCharArray(), new String[]{"g"});
        r.persist();
        assertNotNull(r.authenticate("alice", "secret".toCharArray()));
    }

    @Test
    public void lockPersistedAcrossRestart() throws Exception {
        // Shared file paths for two realm lifecycles (simulates server restart)
        File keyfile = newTempFile("kf-restart");
        File stateFile = newTempFile("lockstate-restart");
        File keyFile = newTempFile("key-restart");
        Properties props = new Properties();
        props.setProperty("file", keyfile.getAbsolutePath());
        props.setProperty("jaas-context", "fileRealm");
        props.setProperty("lockoutEnabled", "true");
        props.setProperty("maxLoginAttempts", "2");
        props.setProperty("lockoutDurationSec", "300");
        props.setProperty("lockoutExemptUsers", "admin");
        props.setProperty("lockstateFile", stateFile.getAbsolutePath());
        props.setProperty("integrityKeyFile", keyFile.getAbsolutePath());

        // First lifecycle: create realm, add user, trigger lockout
        AdminFileRealm r1 = new AdminFileRealm();
        r1.init(props);
        r1.addUser("alice", "secret".toCharArray(), new String[]{"g"});
        r1.persist();
        assertNull(r1.authenticate("alice", "wrong".toCharArray())); // fail 1
        assertNull(r1.authenticate("alice", "wrong".toCharArray())); // fail 2 -> locked

        // Second lifecycle (simulates restart): same files, new realm instance
        AdminFileRealm r2 = new AdminFileRealm();
        r2.init(props);
        // alice should still be locked after restart
        assertNull("lock should survive restart", r2.authenticate("alice", "secret".toCharArray()));
    }

    @Test
    public void unlockFileUserUnlocksLockedAccount() throws Exception {
        AdminFileRealm r = realmWithLockout();
        assertNull(r.authenticate("alice", "wrong".toCharArray()));
        assertNull(r.authenticate("alice", "wrong".toCharArray())); // locked
        assertNull(r.authenticate("alice", "secret".toCharArray())); // still locked

        r.unlockFileUser("alice");

        assertNotNull(r.authenticate("alice", "secret".toCharArray())); // unlocked now
    }
}
