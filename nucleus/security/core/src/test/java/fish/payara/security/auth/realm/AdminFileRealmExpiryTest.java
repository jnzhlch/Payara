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
 * TDD for password-expiry wiring: expiryStatus unit branches + end-to-end
 * integration via AdminFileRealm.
 */
public class AdminFileRealmExpiryTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    // ---- expiryStatus pure-function unit tests (fixed time, all branches) ----

    private static final long DAY_MS = 86_400_000L;

    @Test
    public void expiryStatus_freshPassword_returnsOK() {
        assertEquals(AdminFileRealmStorageManager.OK,
                AdminFileRealmStorageManager.expiryStatus(1000, 1000 + 50 * DAY_MS, 90, 7));
    }

    @Test
    public void expiryStatus_withinWarningWindow_returnsWARN() {
        long lastChanged = 1000;
        long now = lastChanged + 85 * DAY_MS;  // 90 - 7 = 83 days is warning boundary
        assertEquals(AdminFileRealmStorageManager.WARN,
                AdminFileRealmStorageManager.expiryStatus(lastChanged, now, 90, 7));
    }

    @Test
    public void expiryStatus_beyondMaxAge_returnsEXPIRED() {
        long lastChanged = 1000;
        long now = lastChanged + 91 * DAY_MS;
        assertEquals(AdminFileRealmStorageManager.EXPIRED,
                AdminFileRealmStorageManager.expiryStatus(lastChanged, now, 90, 7));
    }

    @Test
    public void expiryStatus_maxAgeZero_meansUnlimited_returnsOK() {
        assertEquals(AdminFileRealmStorageManager.OK,
                AdminFileRealmStorageManager.expiryStatus(1, Long.MAX_VALUE, 0, 7));
    }

    @Test
    public void expiryStatus_negativeMaxAge_returnsOK() {
        assertEquals(AdminFileRealmStorageManager.OK,
                AdminFileRealmStorageManager.expiryStatus(1, Long.MAX_VALUE, -5, 7));
    }

    @Test
    public void expiryStatus_lastChangedAtZero_returnsOK() {
        // never recorded → lazy-record path (caller will record)
        assertEquals(AdminFileRealmStorageManager.OK,
                AdminFileRealmStorageManager.expiryStatus(0, System.currentTimeMillis(), 90, 7));
    }

    @Test
    public void expiryStatus_withinMaxAgeButBeforeWarning_returnsOK() {
        long lastChanged = 1000;
        long now = lastChanged + 50 * DAY_MS;  // well before 83-day warning boundary
        assertEquals(AdminFileRealmStorageManager.OK,
                AdminFileRealmStorageManager.expiryStatus(lastChanged, now, 90, 7));
    }

    @Test
    public void expiryStatus_exactlyAtWarningBoundary_returnsWARN() {
        long lastChanged = 1000;
        long now = lastChanged + 83 * DAY_MS + 1;  // 90 - 7 = 83 days, +1ms over
        assertEquals(AdminFileRealmStorageManager.WARN,
                AdminFileRealmStorageManager.expiryStatus(lastChanged, now, 90, 7));
    }

    // ---- end-to-end integration tests ----

    private AdminFileRealm realm(long maxAgeDays) throws Exception {
        File kf = tmp.newFile("kf");
        Properties p = new Properties();
        p.setProperty("file", kf.getAbsolutePath());
        p.setProperty("jaas-context", "fileRealm");
        p.setProperty("integrityEnabled", "false");          // focus on expiry
        p.setProperty("lockoutEnabled", "false");
        p.setProperty("passwordExpirationEnabled", "true");
        p.setProperty("passwordMaxAgeDays", String.valueOf(maxAgeDays));
        p.setProperty("passwordExpireWarningDays", "7");
        p.setProperty("lockstateFile", new File(tmp.getRoot(), "ls").getAbsolutePath());
        p.setProperty("pwdstateFile", new File(tmp.getRoot(), "ps").getAbsolutePath());
        p.setProperty("integrityKeyFile", new File(tmp.getRoot(), "key").getAbsolutePath());
        AdminFileRealm r = new AdminFileRealm();
        r.init(p);
        r.addUser("alice", "pw".toCharArray(), new String[]{"g"});
        r.persist();
        return r;
    }

    @Test
    public void freshPasswordAuthenticatesAndLazilyRecords() throws Exception {
        AdminFileRealm r = realm(90);
        assertNotNull(r.authenticate("alice", "pw".toCharArray()));   // first success lazily records lastChangedAt=now
    }

    @Test
    public void disabledExpiryNeverRejects() throws Exception {
        File kf = tmp.newFile("kf2");
        Properties p = new Properties();
        p.setProperty("file", kf.getAbsolutePath());
        p.setProperty("jaas-context", "fileRealm");
        p.setProperty("integrityEnabled", "false");
        p.setProperty("lockoutEnabled", "false");
        p.setProperty("passwordExpirationEnabled", "false");
        p.setProperty("lockstateFile", new File(tmp.getRoot(), "ls2").getAbsolutePath());
        p.setProperty("pwdstateFile", new File(tmp.getRoot(), "ps2").getAbsolutePath());
        p.setProperty("integrityKeyFile", new File(tmp.getRoot(), "key2").getAbsolutePath());
        AdminFileRealm r = new AdminFileRealm();
        r.init(p);
        r.addUser("alice", "pw".toCharArray(), new String[]{"g"});
        r.persist();
        assertNotNull(r.authenticate("alice", "pw".toCharArray()));
    }

    @Test
    public void secondAuthDoesNotExpireWithinSameTick() throws Exception {
        // maxAge=0 means unlimited, so any number of auths should succeed
        AdminFileRealm r = realm(0);
        assertNotNull(r.authenticate("alice", "pw".toCharArray()));
        assertNotNull(r.authenticate("alice", "pw".toCharArray()));
    }

    @Test
    public void expiredPasswordRejectedEndToEnd() throws Exception {
        // Use maxAgeDays=1: after persist + setting lastChangedAt to epoch, any auth expires
        AdminFileRealm r = realm(1);
        // First auth records lastChangedAt lazily
        assertNotNull(r.authenticate("alice", "pw".toCharArray()));
        r.persist();  // writes pwdstate with now-based lastChangedAt

        // Now backdate alice's lastChangedAt so she's expired (>1 day ago, maxAge=1 day)
        long oneDayAgo = System.currentTimeMillis() - DAY_MS - 1000;
        r.adminStorageManager().pwdState().setLastChangedAt("alice", oneDayAgo);
        r.adminStorageManager().pwdState().persist();

        // Reload the realm to pick up the backdated state
        Properties p2 = new Properties();
        File kf = new File(r.getProperty("file"));
        p2.setProperty("file", kf.getAbsolutePath());
        p2.setProperty("jaas-context", "fileRealm");
        p2.setProperty("integrityEnabled", "false");
        p2.setProperty("lockoutEnabled", "false");
        p2.setProperty("passwordExpirationEnabled", "true");
        p2.setProperty("passwordMaxAgeDays", "1");
        p2.setProperty("passwordExpireWarningDays", "7");
        p2.setProperty("lockstateFile", new File(tmp.getRoot(), "ls").getAbsolutePath());
        p2.setProperty("pwdstateFile", new File(tmp.getRoot(), "ps").getAbsolutePath());
        p2.setProperty("integrityKeyFile", new File(tmp.getRoot(), "key").getAbsolutePath());
        AdminFileRealm r2 = new AdminFileRealm();
        r2.init(p2);

        // alice's password is expired → auth should return null
        assertNull(r2.authenticate("alice", "pw".toCharArray()));
    }

    @Test
    public void warningWindowAuthStillSucceeds() throws Exception {
        // maxAgeDays=1000, warningDays=900: after recording lastChangedAt, we're in warning
        AdminFileRealm r = realm(1000);
        assertNotNull(r.authenticate("alice", "pw".toCharArray()));
        r.persist();

        // Set lastChangedAt to 900 days ago (within warning window of 1000-900=100 days)
        long ago = System.currentTimeMillis() - 950 * DAY_MS;
        r.adminStorageManager().pwdState().setLastChangedAt("alice", ago);
        r.adminStorageManager().pwdState().persist();

        // Reload
        Properties p2 = new Properties();
        File kf = new File(r.getProperty("file"));
        p2.setProperty("file", kf.getAbsolutePath());
        p2.setProperty("jaas-context", "fileRealm");
        p2.setProperty("integrityEnabled", "false");
        p2.setProperty("lockoutEnabled", "false");
        p2.setProperty("passwordExpirationEnabled", "true");
        p2.setProperty("passwordMaxAgeDays", "1000");
        p2.setProperty("passwordExpireWarningDays", "900");
        p2.setProperty("lockstateFile", new File(tmp.getRoot(), "ls").getAbsolutePath());
        p2.setProperty("pwdstateFile", new File(tmp.getRoot(), "ps").getAbsolutePath());
        p2.setProperty("integrityKeyFile", new File(tmp.getRoot(), "key").getAbsolutePath());
        AdminFileRealm r2 = new AdminFileRealm();
        r2.init(p2);

        // WARN but not EXPIRED → auth succeeds
        assertNotNull(r2.authenticate("alice", "pw".toCharArray()));
    }

    @Test
    public void updateUserResetsLastChangedAt() throws Exception {
        AdminFileRealm r = realm(90);
        // First auth records lastChangedAt
        assertNotNull(r.authenticate("alice", "pw".toCharArray()));
        long firstRecord = r.adminStorageManager().pwdState().getLastChangedAt("alice");
        assertTrue("first record should be non-zero", firstRecord > 0);

        // Change password via updateUser (same name = no rename)
        r.updateUser("alice", "alice", "newpw".toCharArray(), new String[]{"g"});
        r.persist();

        // lastChangedAt should have been reset (new timestamp >= old)
        long afterUpdate = r.adminStorageManager().pwdState().getLastChangedAt("alice");
        assertTrue("after update should be >= first", afterUpdate >= firstRecord);

        // Can still authenticate with new password
        assertNotNull(r.authenticate("alice", "newpw".toCharArray()));
    }

    @Test
    public void updateUserWithRenameResetsLastChangedAtForNewName() throws Exception {
        AdminFileRealm r = realm(90);
        assertNotNull(r.authenticate("alice", "pw".toCharArray()));

        // Rename and change password
        r.updateUser("alice", "bob", "newpw".toCharArray(), new String[]{"g"});
        r.persist();

        // bob should have fresh timestamp
        long bobTs = r.adminStorageManager().pwdState().getLastChangedAt("bob");
        assertTrue("bob should have a fresh record", bobTs > 0);

        // Can auth as bob with new password
        assertNotNull(r.authenticate("bob", "newpw".toCharArray()));
    }

    @Test
    public void updateUserWithoutPasswordChangeDoesNotResetTimestamp() throws Exception {
        AdminFileRealm r = realm(90);
        assertNotNull(r.authenticate("alice", "pw".toCharArray()));
        long firstRecord = r.adminStorageManager().pwdState().getLastChangedAt("alice");

        // Update groups only (password=null, same name)
        r.updateUser("alice", "alice", (char[]) null, new String[]{"g", "admin"});

        // lastChangedAt unchanged
        assertEquals(firstRecord, r.adminStorageManager().pwdState().getLastChangedAt("alice"));
    }
}
