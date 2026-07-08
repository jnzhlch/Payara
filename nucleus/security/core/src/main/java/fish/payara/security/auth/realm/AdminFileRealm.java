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
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

import org.glassfish.security.common.FileRealmStorageManager;

import com.sun.enterprise.security.auth.realm.BadRealmException;
import com.sun.enterprise.security.auth.realm.NoSuchRealmException;
import com.sun.enterprise.security.auth.realm.file.FileRealm;

import fish.payara.security.lockout.HmacStore;
import fish.payara.security.lockout.LockStateManager;

/**
 * FileRealm subclass for the admin-realm: injects login-failure lockout via a
 * hardening storage manager. Business-app FileRealm behavior is unchanged.
 *
 * <p>Override {@code init} to copy lockout-related properties into the realm's
 * contextProperties so that {@link #createStorageManager} can read them via
 * {@code getProperty()}. Then override {@code createStorageManager} to return an
 * {@link AdminFileRealmStorageManager} wired with lockout logic.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */
public class AdminFileRealm extends FileRealm {

    private static final String[] PROP_KEYS = {
        "lockoutEnabled", "maxLoginAttempts", "lockoutDurationSec",
        "lockoutExemptUsers", "lockstateFile", "integrityKeyFile"
    };

    @Override
    protected void init(Properties props) throws BadRealmException, NoSuchRealmException {
        // Copy lockout properties into contextProperties before super.init
        // so createStorageManager can read them via getProperty().
        // AbstractStatefulRealm.init only copies known keys, not custom ones.
        for (String key : PROP_KEYS) {
            String val = props.getProperty(key);
            if (val != null) {
                setProperty(key, val);
            }
        }
        super.init(props); // calls createStorageManager(file) -> our override
    }

    @Override
    protected FileRealmStorageManager createStorageManager(String file) throws IOException {
        boolean enabled = bool("lockoutEnabled", true);
        int maxAttempts = ints("maxLoginAttempts", 5);
        long durationMs = ints("lockoutDurationSec", 300) * 1000L;
        HashSet<String> exempt = new HashSet<>(Arrays.asList(
                str("lockoutExemptUsers", "admin").split(",")));
        File parent = new File(file).getAbsoluteFile().getParentFile();
        String dir = (parent != null) ? parent.getAbsolutePath() : ".";
        File stateFile = new File(str("lockstateFile", dir + "/admin-keyfile.lockstate"));
        File keyFile = new File(str("integrityKeyFile", dir + "/admin-keyfile.key"));
        boolean firstHardeningEnable = !keyFile.exists();
        byte[] hmacKey = HmacStore.loadOrCreateKey(keyFile);
        File signal = firstHardeningEnable ? null : keyFile;
        LockStateManager.LockoutConfig cfg =
                new LockStateManager.LockoutConfig(enabled, maxAttempts, durationMs, exempt);
        LockStateManager locks = new LockStateManager(stateFile, hmacKey, cfg,
                System::currentTimeMillis, signal);
        return new AdminFileRealmStorageManager(file, locks);
    }

    /** Forwarded by the unlock-file-user command (Task 6). */
    public void unlockFileUser(String username) {
        if (storageManager() instanceof AdminFileRealmStorageManager a) {
            a.unlock(username);
        }
    }

    private boolean bool(String k, boolean def) {
        String v = getProperty(k);
        return v == null ? def : Boolean.parseBoolean(v);
    }
    private int ints(String k, int def) {
        try { return Integer.parseInt(getProperty(k)); } catch (Exception e) { return def; }
    }
    private String str(String k, String def) {
        String v = getProperty(k);
        return (v == null || v.isBlank()) ? def : v;
    }
}
