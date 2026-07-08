/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2026 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */

package fish.payara.security.lockout;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PasswordStateManagerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private PasswordStateManager mgr(long[] clock) throws Exception {
        File f = new File(tmp.getRoot(), "pwdstate");
        return new PasswordStateManager(f, "k".repeat(32).getBytes(), () -> clock[0]);
    }

    @Test
    public void unknownUserReturnsZero() throws Exception {
        long[] c = {1000L};
        assertEquals(0L, mgr(c).getLastChangedAt("alice"));
    }

    @Test
    public void setAndGet() throws Exception {
        long[] c = {1000L};
        PasswordStateManager m = mgr(c);
        m.setLastChangedAt("alice", 500L);
        assertEquals(500L, m.getLastChangedAt("alice"));
    }

    @Test
    public void persistsAcrossInstances() throws Exception {
        long[] c = {1000L};
        File f = new File(tmp.getRoot(), "pwdstate");
        PasswordStateManager m1 = new PasswordStateManager(f, "k".repeat(32).getBytes(), () -> c[0]);
        m1.setLastChangedAt("alice", 500L);
        m1.persist();
        PasswordStateManager m2 = new PasswordStateManager(f, "k".repeat(32).getBytes(), () -> c[0]);
        assertEquals(500L, m2.getLastChangedAt("alice"));
    }

    @Test
    public void tamperedStateRejected() throws Exception {
        long[] c = {0};
        File f = new File(tmp.getRoot(), "pwdstate");
        PasswordStateManager m1 = new PasswordStateManager(f, "k".repeat(32).getBytes(), () -> c[0]);
        m1.setLastChangedAt("alice", 1L);
        m1.persist();
        Files.writeString(f.toPath(), "tampered");
        try {
            new PasswordStateManager(f, "k".repeat(32).getBytes(), () -> c[0]);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("integrity check failed"));
        }
    }

    @Test
    public void missingStateLoadsEmptyNotFailClosed() throws Exception {
        long[] c = {0};
        File f = new File(tmp.getRoot(), "absent");
        PasswordStateManager m = new PasswordStateManager(f, "k".repeat(32).getBytes(), () -> c[0]);
        assertEquals(0L, m.getLastChangedAt("alice"));
    }

    @Test
    public void missingMacFileRejected() throws Exception {
        long[] c = {0};
        File f = new File(tmp.getRoot(), "pwdstate");
        PasswordStateManager m1 = new PasswordStateManager(f, "k".repeat(32).getBytes(), () -> c[0]);
        m1.setLastChangedAt("alice", 1L);
        m1.persist();
        File macFile = new File(f.getAbsolutePath() + ".mac");
        assertTrue(macFile.delete());
        try {
            new PasswordStateManager(f, "k".repeat(32).getBytes(), () -> c[0]);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("integrity check failed"));
        }
    }

    @Test
    public void overwriteUpdatesValue() throws Exception {
        long[] c = {1000L};
        PasswordStateManager m = mgr(c);
        m.setLastChangedAt("alice", 500L);
        m.setLastChangedAt("alice", 999L);
        assertEquals(999L, m.getLastChangedAt("alice"));
    }

    @Test
    public void persistsMultipleUsers() throws Exception {
        long[] c = {2000L};
        File f = new File(tmp.getRoot(), "pwdstate");
        PasswordStateManager m1 = new PasswordStateManager(f, "k".repeat(32).getBytes(), () -> c[0]);
        m1.setLastChangedAt("alice", 100L);
        m1.setLastChangedAt("bob", 200L);
        m1.persist();
        PasswordStateManager m2 = new PasswordStateManager(f, "k".repeat(32).getBytes(), () -> c[0]);
        assertEquals(100L, m2.getLastChangedAt("alice"));
        assertEquals(200L, m2.getLastChangedAt("bob"));
        assertEquals(0L, m2.getLastChangedAt("carol"));
    }

    @Test
    public void persistOnEmptyStateWritesFile() throws Exception {
        long[] c = {0};
        File f = new File(tmp.getRoot(), "pwdstate");
        PasswordStateManager m1 = new PasswordStateManager(f, "k".repeat(32).getBytes(), () -> c[0]);
        m1.persist();
        assertTrue(f.exists());
        assertTrue(new File(f.getAbsolutePath() + ".mac").exists());
        // reload from empty persist → still empty
        PasswordStateManager m2 = new PasswordStateManager(f, "k".repeat(32).getBytes(), () -> c[0]);
        assertEquals(0L, m2.getLastChangedAt("anyone"));
    }
}
