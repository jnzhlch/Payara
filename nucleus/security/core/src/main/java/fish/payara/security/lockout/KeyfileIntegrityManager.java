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
import java.nio.file.Files;

/**
 * HMAC-SHA256 integrity protection for the admin keyfile. verify before load
 * (fail-closed on tamper), sign after persist, auto-migrate on first enable.
 * Shares the same HMAC key as the lockout state (spec section 10).
 */
public final class KeyfileIntegrityManager {

    private final File keyfile;
    private final File macFile;
    private final byte[] key;
    private final boolean enabled;
    private final boolean firstHardeningEnable;

    public KeyfileIntegrityManager(File keyfile, File macFile, byte[] key,
            boolean enabled, boolean firstHardeningEnable) {
        this.keyfile = keyfile;
        this.macFile = macFile;
        this.key = key;
        this.enabled = enabled;
        this.firstHardeningEnable = firstHardeningEnable;
    }

    /** @return true if keyfile is authentic (or freshly migrated / disabled / not-yet-present). */
    public boolean verifyOrMigrate() throws IOException {
        if (!enabled) {
            return true;
        }
        if (!macFile.exists()) {
            if (!keyfile.exists()) {
                return true;   // nothing to verify yet
            }
            if (firstHardeningEnable) {
                sign();        // migrate on first enable
                return true;
            }
            return false;      // .mac missing on EXISTING deployment -> tamper suspect
        }
        return HmacStore.verifyFile(key, keyfile, macFile);
    }

    /** Recompute and persist the keyfile MAC. Call after every persist. */
    public void sign() throws IOException {
        if (!enabled) {
            return;
        }
        File parent = macFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.write(macFile.toPath(), HmacStore.sign(key, Files.readAllBytes(keyfile.toPath())));
    }
}
