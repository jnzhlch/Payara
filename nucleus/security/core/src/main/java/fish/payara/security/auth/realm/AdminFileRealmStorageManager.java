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

import fish.payara.security.lockout.LockStateManager;

/**
 * Storage manager wiring the lockout hook into authentication. Integrity-verify
 * (beforeLoad) and password-expiry hooks are no-ops here (P1-B/C).
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */
public class AdminFileRealmStorageManager extends FileRealmStorageManager {

    private static final Logger LOG = Logger.getLogger(AdminFileRealmStorageManager.class.getName());
    private final LockStateManager locks;

    public AdminFileRealmStorageManager(String keyfile, LockStateManager locks) throws IOException {
        super(keyfile);
        this.locks = locks;
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
        // P1-B: KeyfileIntegrityManager.verify() here
    }

    @Override
    protected void afterPersist() {
        try {
            locks.persist();
        } catch (IOException e) {
            LOG.warning("Failed to persist lockstate: " + e.getMessage());
        }
    }

    public void unlock(String username) {
        locks.unlock(username);
    }
}
