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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at legal/OPEN-SOURCE-LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * of the License that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */

package fish.payara.security.lockout;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * In-memory + persisted failure-counter and lock state for the admin-realm
 * lockout subsystem. Backed by a properties file self-protected with HMAC.
 *
 * Failure semantics (spec section 5.2):
 *  - tampered state file  -&gt; IllegalStateException (fail-closed)
 *  - missing state file when deployment is non-first-use -&gt; IllegalStateException (fail-closed)
 *  - missing on genuine first use (no keyfile signal) -&gt; empty in-memory state
 */
public final class LockStateManager {

    public static final class LockoutConfig {
        public final boolean enabled;
        public final int maxLoginAttempts;
        public final long lockoutDurationMs;
        public final Set<String> exemptUsers;

        public LockoutConfig(boolean enabled, int maxLoginAttempts, long lockoutDurationMs, Set<String> exemptUsers) {
            this.enabled = enabled;
            this.maxLoginAttempts = maxLoginAttempts;
            this.lockoutDurationMs = lockoutDurationMs;
            this.exemptUsers = exemptUsers == null ? new HashSet<>() : new HashSet<>(exemptUsers);
        }
    }

    private static final class State {
        int failed;
        long lockUntil;   // epoch ms; 0 = unlocked
    }

    private final File stateFile;
    private final byte[] hmacKey;
    private final LockoutConfig cfg;
    private final LongSupplier clock;
    private final Map<String, State> states = new HashMap<>();

    public LockStateManager(File stateFile, byte[] hmacKey, LockoutConfig cfg, LongSupplier clock) throws IOException {
        this(stateFile, hmacKey, cfg, clock, null);
    }

    public LockStateManager(File stateFile, byte[] hmacKey, LockoutConfig cfg, LongSupplier clock, File existingKeyfileSignal) throws IOException {
        this.stateFile = stateFile;
        this.hmacKey = hmacKey;
        this.cfg = cfg;
        this.clock = clock;
        load(existingKeyfileSignal);
    }

    private synchronized void load(File existingKeyfileSignal) throws IOException {
        if (!stateFile.exists()) {
            if (existingKeyfileSignal != null && existingKeyfileSignal.exists()) {
                throw new IllegalStateException("lockstate file missing on existing deployment: " + stateFile);
            }
            return; // genuine first use -&gt; empty
        }
        File macFile = macFile();
        if (!HmacStore.verifyFile(hmacKey, stateFile, macFile)) {
            throw new IllegalStateException("lockstate integrity check failed (tampered or stale): " + stateFile);
        }
        Properties p = new Properties();
        try (StringReader r = new StringReader(Files.readString(stateFile.toPath()))) {
            p.load(r);
        }
        for (String user : p.stringPropertyNames()) {
            State s = new State();
            String[] parts = p.getProperty(user).split(",");
            s.failed = Integer.parseInt(parts[0]);
            s.lockUntil = parts.length > 1 ? Long.parseLong(parts[1]) : 0L;
            states.put(user, s);
        }
    }

    public synchronized void recordFailure(String user) {
        if (!cfg.enabled || cfg.exemptUsers.contains(user)) return;
        State s = states.computeIfAbsent(user, k -> new State());
        s.failed++;
        if (s.failed >= cfg.maxLoginAttempts) {
            s.lockUntil = clock.getAsLong() + cfg.lockoutDurationMs;
        }
    }

    public synchronized boolean isLocked(String user) {
        if (!cfg.enabled) return false;
        State s = states.get(user);
        if (s == null || s.lockUntil == 0) return false;
        if (clock.getAsLong() >= s.lockUntil) {
            s.lockUntil = 0; s.failed = 0; // expired -&gt; auto-unlock
            return false;
        }
        return true;
    }

    public synchronized void resetFailures(String user) {
        State s = states.get(user);
        if (s != null) { s.failed = 0; s.lockUntil = 0; }
    }

    public synchronized void unlock(String user) { resetFailures(user); }

    public synchronized void persist() throws IOException {
        Properties p = new Properties();
        for (Map.Entry<String, State> e : states.entrySet()) {
            p.setProperty(e.getKey(), e.getValue().failed + "," + e.getValue().lockUntil);
        }
        StringWriter w = new StringWriter();
        p.store(w, null);
        byte[] bytes = w.toString().getBytes();
        File parent = stateFile.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.write(stateFile.toPath(), bytes);
        Files.write(macFile().toPath(), HmacStore.sign(hmacKey, bytes));
    }

    public static synchronized boolean exists(File stateFile) {
        return stateFile != null && stateFile.exists();
    }

    private File macFile() {
        return new File(stateFile.getAbsolutePath() + ".mac");
    }
}
