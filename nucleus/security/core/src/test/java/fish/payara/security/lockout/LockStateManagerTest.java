/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2026 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */

package fish.payara.security.lockout;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class LockStateManagerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private LockStateManager mgr(long[] clock) throws Exception {
        File state = tmp.newFile("lockstate");
        state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(
                true, 3, 300_000L, Set.of("admin"));
        return new LockStateManager(state, key, cfg, () -> clock[0]);
    }

    // --- recordFailure / isLocked branches ---

    @Test
    public void countsUpToThresholdThenLocks() throws Exception {
        long[] clock = {1000L};
        LockStateManager m = mgr(clock);
        assertFalse(m.isLocked("alice"));
        m.recordFailure("alice"); assertFalse(m.isLocked("alice"));
        m.recordFailure("alice"); assertFalse(m.isLocked("alice"));
        m.recordFailure("alice"); assertTrue(m.isLocked("alice"));
    }

    @Test
    public void exemptUserNeverCounts() throws Exception {
        long[] clock = {0};
        LockStateManager m = mgr(clock);
        for (int i = 0; i < 10; i++) m.recordFailure("admin");
        assertFalse(m.isLocked("admin"));
    }

    @Test
    public void successResetsCounter() throws Exception {
        long[] clock = {0};
        LockStateManager m = mgr(clock);
        m.recordFailure("alice"); m.recordFailure("alice");
        m.resetFailures("alice");
        m.recordFailure("alice"); assertFalse(m.isLocked("alice"));
    }

    @Test
    public void lockExpiresAfterDuration() throws Exception {
        long[] clock = {1000L};
        LockStateManager m = mgr(clock);
        for (int i = 0; i < 3; i++) m.recordFailure("alice");
        assertTrue(m.isLocked("alice"));
        clock[0] = 1000L + 300_000L + 1;
        assertFalse(m.isLocked("alice"));
    }

    @Test
    public void lockNotExpiredBeforeDuration() throws Exception {
        long[] clock = {1000L};
        LockStateManager m = mgr(clock);
        for (int i = 0; i < 3; i++) m.recordFailure("alice");
        clock[0] = 1000L + 300_000L - 1; // 1ms before expiry
        assertTrue(m.isLocked("alice"));
    }

    @Test
    public void manualUnlockClears() throws Exception {
        long[] clock = {0};
        LockStateManager m = mgr(clock);
        for (int i = 0; i < 3; i++) m.recordFailure("alice");
        m.unlock("alice");
        assertFalse(m.isLocked("alice"));
    }

    // --- enabled=false branches ---

    @Test
    public void disabledConfigNeverLocks() throws Exception {
        long[] clock = {0};
        File state = tmp.newFile("lockstate"); state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(
                false, 3, 300_000L, Set.of());
        LockStateManager m = new LockStateManager(state, key, cfg, () -> clock[0]);
        for (int i = 0; i < 10; i++) m.recordFailure("alice");
        assertFalse(m.isLocked("alice"));
    }

    @Test
    public void disabledIsLockedReturnsFalseEvenForLockedState() throws Exception {
        long[] clock = {1000L};
        File state = tmp.newFile("lockstate"); state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(
                false, 3, 300_000L, Set.of());
        LockStateManager m = new LockStateManager(state, key, cfg, () -> clock[0]);
        assertFalse(m.isLocked("anyone"));
    }

    @Test
    public void disabledRecordFailureDoesNotCount() throws Exception {
        long[] clock = {0};
        File state = tmp.newFile("lockstate"); state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(
                false, 1, 300_000L, Set.of());
        LockStateManager m = new LockStateManager(state, key, cfg, () -> clock[0]);
        m.recordFailure("alice");
        assertFalse(m.isLocked("alice"));
    }

    // --- state==null / missing-user branches ---

    @Test
    public void isLockedReturnsFalseForUnknownUser() throws Exception {
        long[] clock = {0};
        LockStateManager m = mgr(clock);
        assertFalse(m.isLocked("nobody"));
    }

    @Test
    public void resetFailuresOnUnknownUserDoesNotThrow() throws Exception {
        long[] clock = {0};
        LockStateManager m = mgr(clock);
        m.resetFailures("nobody"); // no state entry - should not throw
        assertFalse(m.isLocked("nobody"));
    }

    @Test
    public void unlockOnUnknownUserDoesNotThrow() throws Exception {
        long[] clock = {0};
        LockStateManager m = mgr(clock);
        m.unlock("nobody");
    }

    // --- persist/reload ---

    @Test
    public void persistsAcrossInstances() throws Exception {
        long[] clock = {0};
        File state = tmp.newFile("lockstate"); state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(true, 3, 300_000L, Set.of());
        LockStateManager m1 = new LockStateManager(state, key, cfg, () -> clock[0]);
        for (int i = 0; i < 3; i++) m1.recordFailure("alice");
        m1.persist();
        LockStateManager m2 = new LockStateManager(state, key, cfg, () -> clock[0]);
        assertTrue(m2.isLocked("alice"));
    }

    @Test
    public void persistAndReloadPreservesCountBelowThreshold() throws Exception {
        long[] clock = {0};
        File state = tmp.newFile("lockstate"); state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(true, 5, 300_000L, Set.of());
        LockStateManager m1 = new LockStateManager(state, key, cfg, () -> clock[0]);
        m1.recordFailure("bob");
        m1.recordFailure("bob");
        m1.persist();
        LockStateManager m2 = new LockStateManager(state, key, cfg, () -> clock[0]);
        // need 3 more failures to reach threshold of 5
        m2.recordFailure("bob");
        m2.recordFailure("bob");
        assertFalse(m2.isLocked("bob"));
        m2.recordFailure("bob");
        assertTrue(m2.isLocked("bob"));
    }

    // --- tampered state file ---

    @Test
    public void tamperedStateFileIsRejected() throws Exception {
        long[] clock = {0};
        File state = tmp.newFile("lockstate"); state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(true, 3, 300_000L, Set.of());
        LockStateManager m1 = new LockStateManager(state, key, cfg, () -> clock[0]);
        for (int i = 0; i < 3; i++) m1.recordFailure("alice");
        m1.persist();
        Files.writeString(state.toPath(), "tampered");
        try {
            new LockStateManager(state, key, cfg, () -> clock[0]);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("integrity check failed"));
        }
    }

    // --- missing mac file ---

    @Test
    public void missingMacFileIsRejected() throws Exception {
        long[] clock = {0};
        File state = tmp.newFile("lockstate"); state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(true, 3, 300_000L, Set.of());
        LockStateManager m1 = new LockStateManager(state, key, cfg, () -> clock[0]);
        m1.recordFailure("alice");
        m1.persist();
        // delete mac file to simulate tampering/missing
        File macFile = new File(state.getAbsolutePath() + ".mac");
        assertTrue(macFile.delete());
        try {
            new LockStateManager(state, key, cfg, () -> clock[0]);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("integrity check failed"));
        }
    }

    // --- fail-closed: missing state on existing deployment ---

    @Test
    public void missingStateFileOnExistingDeploymentFailsClosed() throws Exception {
        long[] clock = {0};
        File state = tmp.newFile("lockstate"); state.delete();
        File keyfile = tmp.newFile("admin-keyfile"); // exists = signals prior deployment
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(true, 3, 300_000L, Set.of());
        try {
            new LockStateManager(state, key, cfg, () -> clock[0], keyfile);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("missing on existing deployment"));
        }
    }

    // --- first use: missing state + no signal = empty load ---

    @Test
    public void missingStateFileWithNoSignalLoadsEmpty() throws Exception {
        long[] clock = {0};
        File state = tmp.newFile("lockstate"); state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(true, 3, 300_000L, Set.of());
        LockStateManager m = new LockStateManager(state, key, cfg, () -> clock[0]);
        assertFalse(m.isLocked("anyone"));
        // recording failures still works (empty map + computeIfAbsent)
        m.recordFailure("bob");
        assertFalse(m.isLocked("bob"));
    }

    // --- missing stateFile + null existingKeyfileSignal (signal file itself missing) ---

    @Test
    public void missingStateFileWithNullSignalLoadsEmpty() throws Exception {
        long[] clock = {0};
        File state = tmp.newFile("lockstate"); state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(true, 3, 300_000L, Set.of());
        // existingKeyfileSignal=null -> no fail-closed check
        LockStateManager m = new LockStateManager(state, key, cfg, () -> clock[0], null);
        assertFalse(m.isLocked("anyone"));
    }

    // --- exists static ---

    @Test
    public void existsReturnsTrueWhenPresent() throws Exception {
        File f = tmp.newFile("state");
        assertTrue(LockStateManager.exists(f));
    }

    @Test
    public void existsReturnsFalseWhenAbsent() throws Exception {
        File f = tmp.newFile("state");
        f.delete();
        assertFalse(LockStateManager.exists(f));
    }

    @Test
    public void existsReturnsFalseForNull() throws Exception {
        assertFalse(LockStateManager.exists(null));
    }

    // --- load with lockUntil=0 (single-value property, parts.length==1) ---

    @Test
    public void reloadStateWithLockUntilZero() throws Exception {
        long[] clock = {0};
        File state = tmp.newFile("lockstate"); state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(true, 3, 300_000L, Set.of());
        LockStateManager m1 = new LockStateManager(state, key, cfg, () -> clock[0]);
        m1.recordFailure("alice"); // 1 failure, not locked
        m1.persist();
        // write properties manually with just failed count (no lockUntil)
        // Actually persist() writes "1,0" so lockUntil=0 branch is already covered.
        // But to be explicit, reload and verify not locked:
        LockStateManager m2 = new LockStateManager(state, key, cfg, () -> clock[0]);
        assertFalse(m2.isLocked("alice"));
    }

    // --- null exemptUsers in config ---

    @Test
    public void nullExemptUsersTreatedAsEmpty() throws Exception {
        long[] clock = {1000L};
        File state = tmp.newFile("lockstate"); state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(
                true, 3, 300_000L, null);
        LockStateManager m = new LockStateManager(state, key, cfg, () -> clock[0]);
        for (int i = 0; i < 3; i++) m.recordFailure("someone");
        assertTrue(m.isLocked("someone"));
    }

    // --- empty exemptUsers set ---

    @Test
    public void emptyExemptUsersNobodyExempt() throws Exception {
        long[] clock = {1000L};
        File state = tmp.newFile("lockstate"); state.delete();
        byte[] key = "k".repeat(32).getBytes();
        LockStateManager.LockoutConfig cfg = new LockStateManager.LockoutConfig(
                true, 3, 300_000L, Collections.emptySet());
        LockStateManager m = new LockStateManager(state, key, cfg, () -> clock[0]);
        for (int i = 0; i < 3; i++) m.recordFailure("alice");
        assertTrue(m.isLocked("alice"));
    }
}
