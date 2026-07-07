/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2026 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
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

package com.sun.enterprise.security.auth.realm.file;

import java.io.File;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * Regression guard: a plain {@link FileRealm} (no lockout properties) must have
 * zero lockout side-effects after many failed authentications. The base-class
 * hooks (onAuthFailure / onAuthSuccess / isLocked) are no-ops on FileRealm,
 * so business applications that use plain FileRealm are unaffected by the
 * P1-A lockout hardening in AdminFileRealm.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */
public class FileRealmIsolationRegressionTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void plainFileRealmHasNoLockoutSideEffects() throws Exception {
        File kf = tmp.newFile("kf");
        FileRealm r = new FileRealm();
        Properties p = new Properties();
        p.setProperty("file", kf.getAbsolutePath());
        p.setProperty("jaas-context", "fileRealm");
        r.init(p);
        r.addUser("u", "pw".toCharArray(), new String[]{"g"});
        r.persist();
        // many failures never throw / never lock (hooks are no-op on base class)
        for (int i = 0; i < 20; i++) {
            assertNull(r.authenticate("u", "wrong".toCharArray()));
        }
        // correct password still works
        assertNotNull(r.authenticate("u", "pw".toCharArray()));
    }
}
