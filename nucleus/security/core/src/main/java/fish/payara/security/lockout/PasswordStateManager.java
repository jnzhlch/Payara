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
import java.util.Map;
import java.util.Properties;
import java.util.function.LongSupplier;

/**
 * Per-user password lastChangedAt, persisted in a properties file self-protected
 * with HMAC-SHA256 (.mac sidecar).
 *
 * <p>Missing state file semantics (spec 7.1 deviation): loads an empty map
 * instead of fail-closing, because password age is a soft policy — a missing
 * pwdstate simply means "no recorded change time yet".
 *
 * <p>Tampered or stale state file (exists but HMAC mismatch) still throws
 * IllegalStateException (fail-closed).
 */
public final class PasswordStateManager {

    private final File stateFile;
    private final byte[] hmacKey;
    private final LongSupplier clock;
    private final Map<String, Long> states = new HashMap<>();

    public PasswordStateManager(File stateFile, byte[] hmacKey, LongSupplier clock) throws IOException {
        this.stateFile = stateFile;
        this.hmacKey = hmacKey;
        this.clock = clock;
        load();
    }

    private synchronized void load() throws IOException {
        if (!stateFile.exists()) {
            return; // missing → empty (not fail-closed)
        }
        File macFile = macFile();
        if (!HmacStore.verifyFile(hmacKey, stateFile, macFile)) {
            throw new IllegalStateException("pwdstate integrity check failed (tampered or stale): " + stateFile);
        }
        Properties p = new Properties();
        try (StringReader r = new StringReader(Files.readString(stateFile.toPath()))) {
            p.load(r);
        }
        for (String user : p.stringPropertyNames()) {
            states.put(user, Long.parseLong(p.getProperty(user)));
        }
    }

    public synchronized long getLastChangedAt(String user) {
        return states.getOrDefault(user, 0L);
    }

    public synchronized void setLastChangedAt(String user, long ts) {
        states.put(user, ts);
    }

    public synchronized void persist() throws IOException {
        Properties p = new Properties();
        for (Map.Entry<String, Long> e : states.entrySet()) {
            p.setProperty(e.getKey(), String.valueOf(e.getValue()));
        }
        StringWriter w = new StringWriter();
        p.store(w, null);
        byte[] bytes = w.toString().getBytes();
        File parent = stateFile.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.write(stateFile.toPath(), bytes);
        Files.write(macFile().toPath(), HmacStore.sign(hmacKey, bytes));
    }

    private File macFile() {
        return new File(stateFile.getAbsolutePath() + ".mac");
    }
}
