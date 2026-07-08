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
 * file and include the License file at legal-OPEN-SOURCE-LICENSE.txt.
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
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, the
 * option applies only to the new code or if such option is made subject to
 * such option by the copyright holder.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */

package com.sun.enterprise.admin.servermgmt.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.sun.enterprise.admin.launcher.GFLauncher;
import com.sun.enterprise.admin.launcher.GFLauncherException;
import com.sun.enterprise.admin.launcher.GFLauncherFactory;
import com.sun.enterprise.admin.launcher.GFLauncherInfo;
import com.sun.enterprise.universal.xml.MiniXmlParserException;

import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.CommandException;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

/**
 * Offline command that re-signs the admin keyfile MAC after keyfile has been
 * tampered with or the .mac sidecar lost. Uses JDK javax.crypto.Mac directly
 * (same HmacSHA256 algorithm as HmacStore.sign in security/core).
 *
 * <p>Package-private {@link #resignKeyfile(File, File, byte[])} is the
 * testable core.
 *
 * Portions Copyright [2026] Payara Foundation and/or its affiliates
 */
@Service(name = "reset-integrity")
@PerLookup
@I18n("reset.integrity")
public class ResetIntegrityCommand extends LocalDomainCommand {

    private static final Logger LOGGER =
            Logger.getLogger(ResetIntegrityCommand.class.getName());

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String KEYFILE_NAME = "admin-keyfile.key";

    @Param(name = "domain_name", optional = true, primary = true)
    private String userArgDomainName;

    @Override
    protected void validate() throws CommandException {
        if (userArgDomainName != null) {
            setDomainName(userArgDomainName);
        }
        super.validate();
    }

    @Override
    protected int executeCommand() throws CommandException {
        try {
            String domainDir = getDomainsDir().getPath();
            String domainName = getDomainName();

            GFLauncher launcher = GFLauncherFactory.getInstance(RuntimeType.DAS);
            GFLauncherInfo info = launcher.getInfo();
            info.setDomainName(domainName);
            info.setDomainParentDir(domainDir);
            launcher.setup();

            String adminKeyFile = launcher.getAdminRealmKeyFile();
            if (adminKeyFile == null) {
                LOGGER.info("admin-realm is not a file realm; hardening not active, no reset needed");
                return 0;
            }

            File kfDir = new File(adminKeyFile).getAbsoluteFile().getParentFile();
            File keyFile = new File(kfDir, KEYFILE_NAME);

            if (!keyFile.exists()) {
                LOGGER.info("No admin keyfile found at " + keyFile
                        + "; hardening not enabled, no reset needed");
                return 0;
            }

            byte[] hmacKey = Files.readAllBytes(keyFile.toPath());
            resignKeyfile(new File(adminKeyFile), new File(adminKeyFile + ".mac"), hmacKey);

            LOGGER.warning("admin keyfile MAC re-signed by operator");
            return 0;
        } catch (MiniXmlParserException | GFLauncherException | IOException e) {
            throw new CommandException(e);
        }
    }

    /**
     * Core logic: delete old .mac, read keyfile bytes, compute HMAC-SHA256, write .mac.
     * Package-private for unit testing without GFLauncher.
     *
     * @param keyfile  the admin keyfile whose integrity is being re-signed
     * @param macFile  the .mac sidecar to write
     * @param key      32-byte HMAC-SHA256 key (from admin-keyfile.key)
     */
    static void resignKeyfile(File keyfile, File macFile, byte[] key) throws IOException {
        if (macFile.exists()) {
            macFile.delete();
        }

        byte[] data = Files.readAllBytes(keyfile.toPath());
        byte[] macBytes = computeMac(key, data);
        Files.write(macFile.toPath(), macBytes);
    }

    static byte[] computeMac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
