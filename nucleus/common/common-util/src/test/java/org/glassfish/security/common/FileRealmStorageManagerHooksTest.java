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

package org.glassfish.security.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class FileRealmStorageManagerHooksTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File keyfile;

    /**
     * Subclass that records every hook invocation for assertion.
     * Uses a ThreadLocal to pass the list in, because beforeLoad() fires
     * during super() construction before any instance field initializer runs.
     */
    static class Recording extends FileRealmStorageManager {
        private static final ThreadLocal<List<String>> HOOK_CALLS = new ThreadLocal<>();
        final List<String> calls;

        static Recording create(String file) throws IOException {
            List<String> calls = new ArrayList<>();
            HOOK_CALLS.set(calls);
            return new Recording(file);
        }

        Recording(String file) throws IOException {
            super(file);
            calls = HOOK_CALLS.get();
        }

        @Override protected void onAuthSuccess(String u) { HOOK_CALLS.get().add("success:" + u); }
        @Override protected void onAuthFailure(String u, FailureReason r) { HOOK_CALLS.get().add("fail:" + u + ":" + r); }
        @Override protected void beforeLoad() { HOOK_CALLS.get().add("beforeLoad"); }
        @Override protected void afterPersist() { HOOK_CALLS.get().add("afterPersist"); }
    }

    @Before
    public void setup() throws IOException {
        keyfile = tmp.newFile("kf");
        // one valid user "alice" with a known SSHA line is hard to handcraft;
        // we instead assert hook wiring on the failure branches that need no valid hash.
        Files.writeString(keyfile.toPath(), "# comment\n");
    }

    @Test
    public void unknownUserTriggersUserNotFoundHookAndReturnsNull() throws IOException {
        Recording r = Recording.create(keyfile.getAbsolutePath()); // fresh load -> beforeLoad
        assertNull(r.authenticate("ghost", "pw".toCharArray()));
        assertTrue(r.calls.toString(), r.calls.contains("beforeLoad"));
        assertTrue(r.calls.contains("fail:ghost:USER_NOT_FOUND"));
    }

    @Test
    public void beforeLoadFiresOnConstruction() throws IOException {
        Recording r = Recording.create(keyfile.getAbsolutePath());
        assertTrue(r.calls.contains("beforeLoad"));
    }

    @Test
    public void afterPersistFiresAfterPersist() throws Exception {
        Recording r = Recording.create(keyfile.getAbsolutePath());
        r.addUser("bob", "password123".toCharArray(), new String[]{"g"});
        r.persist();
        assertTrue(r.calls.toString(), r.calls.contains("afterPersist"));
    }

    @Test
    public void defaultHooksAreNoOpForBaseClass() throws Exception {
        // plain FileRealmStorageManager must not throw / must behave unchanged
        FileRealmStorageManager base = new FileRealmStorageManager(keyfile.getAbsolutePath());
        base.addUser("bob", "password123".toCharArray(), new String[]{"g"});
        base.persist();          // afterPersist no-op
        assertNull(base.authenticate("ghost", "pw".toCharArray())); // onAuthFailure no-op
    }
}
