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

/**
 * Storage manager wiring lockout and keyfile-integrity hooks into authentication.
 * Integrity verification happens in {@link AdminFileRealm#createStorageManager}
 * before construction (Java super-constructor ordering prevents beforeLoad from
 * accessing subclass fields). Signing happens in afterPersist.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */
public class AdminFileRealmStorageManager extends FileRealmStorageManager {

    private static final Logger LOG = Logger.getLogger(AdminFileRealmStorageManager.class.getName());
    private final LockStateManager locks;
    private final KeyfileIntegrityManager integrity;

    public AdminFileRealmStorageManager(String keyfile, LockStateManager locks,
            KeyfileIntegrityManager integrity) throws IOException {
        super(keyfile);
        this.locks = locks;
        this.integrity = integrity;
    }

    @Override
    public String[] authenticate(String username, char[] password) {
        if (locks.isLocked(username)) {
            LOG.warning("Authentication rejected: account locked " + username);
            return null; // short-circuit before SSHA work
        }
        return super.authenticate(username, password); // drives onAuthFailure/onAuthSuccess
    }

    @Override
    protected void onAuthFailure(String username, FailureReason reason) {
        if (reason == FailureReason.WRONG_PASSWORD) {
            locks.recordFailure(username);
            if (locks.isLocked(username)) {
                LOG.warning("Account locked after repeated failures: " + username);
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
    protected void afterPersist() {
        try {
            integrity.sign();
            locks.persist();
        } catch (IOException e) {
            LOG.warning("Failed to persist integrity/lockstate: " + e.getMessage());
        }
    }

    public void unlock(String username) {
        locks.unlock(username);
    }
}
