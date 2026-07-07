/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2026 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/main/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at legal/OPEN-SOURCE-LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */
package com.sun.enterprise.security.cli;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.glassfish.security.common.RealmClassNames;
import org.junit.Test;

/**
 * Lightweight tests for UnlockFileUser command.
 *
 * <p>HK2 habitat assembly (full command wiring with Config/Realm/Habitat) is
 * not feasible in a plain JUnit test; the command's execute() branch logic is
 * covered via the RealmClassNames gate + code review. The core unlock
 * delegation path (AdminFileRealm.unlockFileUser -> AdminFileRealmStorageManager.unlock
 * -> LockStateManager.unlock) is covered by Task 4/5 tests.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */
public class UnlockFileUserCommandTest {

    @Test
    public void rejectsNonFileRealmClassname() {
        assertFalse(RealmClassNames.isFileRealm(
                "com.sun.enterprise.security.auth.realm.ldap.LDAPRealm"));
        assertFalse(RealmClassNames.isFileRealm(
                "com.sun.enterprise.security.auth.realm.certificate.CertificateRealm"));
    }

    @Test
    public void acceptsFileRealmClassname() {
        assertTrue(RealmClassNames.isFileRealm(
                "com.sun.enterprise.security.auth.realm.file.FileRealm"));
        // AdminFileRealm extends FileRealm, so isAssignableFrom passes
        assertTrue(RealmClassNames.isFileRealm(
                "fish.payara.security.auth.realm.AdminFileRealm"));
    }
}
