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

import java.io.IOException;
import java.util.logging.Logger;

import org.glassfish.security.common.FailureReason;
import org.glassfish.security.common.FileRealmStorageManager;

import fish.payara.security.lockout.KeyfileIntegrityManager;
import fish.payara.security.lockout.LockStateManager;
import fish.payara.security.lockout.PasswordStateManager;

/**
 * Storage manager wiring lockout, keyfile-integrity, and password-expiry hooks
 * into authentication. Integrity verification happens in
 * {@link AdminFileRealm#createStorageManager} before construction (Java
 * super-constructor ordering prevents beforeLoad from accessing subclass fields).
 * Signing happens in afterPersist.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */
public class AdminFileRealmStorageManager extends FileRealmStorageManager {

    private static final Logger LOG = Logger.getLogger(AdminFileRealmStorageManager.class.getName());

    static final int OK = 0, WARN = 1, EXPIRED = 2;

    private final LockStateManager locks;
    private final KeyfileIntegrityManager integrity;
    private final PasswordStateManager pwdState;
    private final boolean pwdEnabled;
    private final long maxAgeDays;
    private final long warningDays;
    private final boolean forceChangeOnFirstLogin;

    public AdminFileRealmStorageManager(String keyfile, LockStateManager locks,
            KeyfileIntegrityManager integrity, PasswordStateManager pwdState,
            boolean pwdEnabled, long maxAgeDays, long warningDays,
            boolean forceChangeOnFirstLogin) throws IOException {
        super(keyfile);
        this.locks = locks;
        this.integrity = integrity;
        this.pwdState = pwdState;
        this.pwdEnabled = pwdEnabled;
        this.maxAgeDays = maxAgeDays;
        this.warningDays = warningDays;
        this.forceChangeOnFirstLogin = forceChangeOnFirstLogin;
    }

    /**
     * Pure decision function: given a recorded lastChangedAt and the current
     * clock, return whether the password is expired, within the warning
     * window, or OK.
     */
    static int expiryStatus(long lastChangedAt, long nowMs, long maxAgeDays, long warningDays) {
        if (maxAgeDays <= 0) return OK;               // 0 = unlimited
        if (lastChangedAt == 0) return OK;            // not yet recorded → lazy-record path
        long ageMs = nowMs - lastChangedAt;
        long maxMs = maxAgeDays * 86_400_000L;
        if (ageMs > maxMs) return EXPIRED;
        if (ageMs > maxMs - warningDays * 86_400_000L) return WARN;
        return OK;
    }

    @Override
    public String[] authenticate(String username, char[] password) {
        if (locks.isLocked(username)) {
            LOG.warning("Authentication rejected: account locked " + username);
            return null; // short-circuit before SSHA work
        }
        String[] groups = super.authenticate(username, password); // drives onAuthFailure/onAuthSuccess
        if (groups != null && pwdEnabled) {
            if (forceChangeOnFirstLogin && pwdState.getMustChange(username)) {
                LOG.warning("Authentication rejected: password change required for " + username);
                return null;
            }
            long now = System.currentTimeMillis();
            long last = pwdState.getLastChangedAt(username);
            int st = expiryStatus(last, now, maxAgeDays, warningDays);
            if (last == 0) {
                pwdState.setLastChangedAt(username, now);    // lazy first-record
            }
            if (st == EXPIRED) {
                LOG.warning("Authentication rejected: password expired for " + username);
                return null;
            }
            if (st == WARN) {
                LOG.info("Password expiring soon for " + username);
            }
        }
        return groups;
    }

    @Override
    protected void onAuthFailure(String username, FailureReason reason) {
        if (reason == FailureReason.WRONG_PASSWORD) {
            locks.recordFailure(username);
            if (locks.isLocked(username)) {
                LOG.warning("Account locked after repeated failures: " + username);
                try {
                    locks.persist(); // anti-restart-bypass, spec §5.2
                } catch (IOException e) {
                    LOG.warning("Failed to persist lockstate on lock: " + e.getMessage());
                }
            }
        }
        // USER_NOT_FOUND etc. intentionally not counted (avoids enumeration amplification)
    }

    @Override
    protected void onAuthSuccess(String username) {
        locks.resetFailures(username);
    }

    @Override
    protected void beforeLoad() {
        // Integrity verified in AdminFileRealm.createStorageManager (before super-ctor).
        // Cannot use subclass fields here: Java runs super-ctor before subclass field init.
    }

    @Override
    public synchronized void updateUser(String name, String newName, char[] password, String[] groups) {
        super.updateUser(name, newName, password, groups);
        String effectiveName = newName != null ? newName : name;
        if (password != null && pwdEnabled) {
            pwdState.setLastChangedAt(effectiveName, System.currentTimeMillis());
            pwdState.setMustChange(effectiveName, false);
        }
    }

    @Override
    public synchronized void addUser(String username, char[] password, String[] groupList) {
        super.addUser(username, password, groupList);
        if (forceChangeOnFirstLogin && pwdEnabled) {
            pwdState.setMustChange(username, true);
        }
    }

    @Override
    protected void afterPersist() {
        try {
            integrity.sign();
            locks.persist();
            if (pwdEnabled) {
                pwdState.persist();
            }
        } catch (IOException e) {
            LOG.warning("Failed to persist integrity/lockstate/pwdstate: " + e.getMessage());
        }
    }

    public void unlock(String username) {
        locks.unlock(username);
    }

    /** Package-private for testing: direct access to password state. */
    PasswordStateManager pwdState() {
        return pwdState;
    }
}
