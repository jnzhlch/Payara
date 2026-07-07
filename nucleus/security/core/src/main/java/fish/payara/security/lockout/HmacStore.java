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
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 sign/verify helper + key file management. Shared by the lockout,
 * keyfile-integrity and password-expiry state files (all self-protect with the
 * same key, per design spec section 10).
 */
public final class HmacStore {
    private static final String ALGO = "HmacSHA256";

    private HmacStore() { }

    public static byte[] sign(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(key, ALGO));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    public static boolean verify(byte[] key, byte[] data, byte[] mac) {
        if (mac == null) return false;
        byte[] expected = sign(key, data);
        return java.security.MessageDigest.isEqual(expected, mac);
    }

    public static boolean verifyFile(byte[] key, File dataFile, File macFile) throws IOException {
        if (!macFile.exists() || !dataFile.exists()) return false;
        byte[] data = Files.readAllBytes(dataFile.toPath());
        byte[] mac = Files.readAllBytes(macFile.toPath());
        return verify(key, data, mac);
    }

    /** Load existing key, or generate+persist a fresh 32-byte key. */
    public static byte[] loadOrCreateKey(File keyFile) throws IOException {
        if (keyFile.exists()) {
            return Files.readAllBytes(keyFile.toPath());
        }
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        File parent = keyFile.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.write(keyFile.toPath(), key);
        trySetOwnerOnly(keyFile);
        return key;
    }

    private static void trySetOwnerOnly(File f) {
        try {
            f.setReadable(false, false); f.setReadable(true, true);
            f.setWritable(false, false); f.setWritable(true, true);
        } catch (Exception ignored) { /* best-effort; POSIX perms are a bonus */ }
    }
}
