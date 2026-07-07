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

package com.sun.enterprise.security.cli;

import com.sun.enterprise.config.serverbeans.AuthRealm;
import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.SecurityService;
import com.sun.enterprise.security.auth.realm.BadRealmException;
import com.sun.enterprise.security.auth.realm.RealmsManager;
import com.sun.enterprise.security.auth.realm.file.FileRealm;
import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.enterprise.util.SystemPropertyConstants;

import fish.payara.security.auth.realm.AdminFileRealm;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.glassfish.api.ActionReport;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.*;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.security.common.RealmClassNames;
import org.jvnet.hk2.annotations.Service;

import static org.glassfish.config.support.CommandTarget.CLUSTER;
import static org.glassfish.config.support.CommandTarget.CONFIG;
import static org.glassfish.config.support.CommandTarget.DAS;
import static org.glassfish.config.support.CommandTarget.STANDALONE_INSTANCE;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.glassfish.config.support.AccessRequired;

@Service(name = "unlock-file-user")
@PerLookup
@I18n("unlock.file.user")
@ExecuteOn({ RuntimeType.INSTANCE, RuntimeType.DAS })
@TargetType({ DAS, STANDALONE_INSTANCE, CLUSTER, CONFIG, CommandTarget.DEPLOYMENT_GROUP })
@RestEndpoints({
        @RestEndpoint(configBean = AuthRealm.class, opType = RestEndpoint.OpType.POST,
                path = "unlock-user", description = "Unlock a locked file user", params = {
                        @RestParam(name = "authrealmname", value = "$parent") }) })
public class UnlockFileUser implements AdminCommand, AdminCommandSecurity.Preauthorization {

    final private static LocalStringManagerImpl localStrings =
            new LocalStringManagerImpl(UnlockFileUser.class);

    @Param(name = "authrealmname", optional = true)
    private String authRealmName;

    @Param(name = "target", optional = true,
            defaultValue = SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME)
    private String target;

    @Param(name = "username", primary = true)
    private String userName;

    @Inject
    @Named(ServerEnvironment.DEFAULT_INSTANCE_NAME)
    private Config config;

    @Inject
    private Domain domain;

    @Inject
    private RealmsManager realmsManager;

    @AccessRequired.To("update")
    private AuthRealm fileAuthRealm;

    private SecurityService securityService;

    @Override
    public boolean preAuthorization(AdminCommandContext context) {
        config = CLIUtil.chooseConfig(domain, target, context.getActionReport());
        if (config == null) {
            context.getActionReport().setActionExitCode(ActionReport.ExitCode.SUCCESS);
            return false;
        }

        securityService = config.getSecurityService();
        fileAuthRealm = CLIUtil.findRealm(securityService, authRealmName);
        if (fileAuthRealm == null) {
            ActionReport report = context.getActionReport();
            report.setMessage(localStrings.getLocalString("unlock.file.user.filerealmnotfound",
                    "File realm {0} does not exist", authRealmName));
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            return false;
        }
        authRealmName = fileAuthRealm.getName();
        return true;
    }

    @Override
    public void execute(AdminCommandContext context) {
        final ActionReport report = context.getActionReport();

        String fileRealmClassName = fileAuthRealm.getClassname();
        if (!RealmClassNames.isFileRealm(fileRealmClassName)) {
            report.setMessage(localStrings.getLocalString("unlock.file.user.realmnotsupported",
                    "Configured file realm {0} is not supported.", fileRealmClassName));
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
        }

        try {
            realmsManager.createRealms(config);
            CreateFileUser.refreshRealm(config.getName(), authRealmName);

            FileRealm fileRealm = (FileRealm) realmsManager.getFromLoadedRealms(
                    config.getName(), authRealmName);
            if (fileRealm instanceof AdminFileRealm) {
                ((AdminFileRealm) fileRealm).unlockFileUser(userName);
                fileRealm.persist();
            } else {
                report.setMessage(localStrings.getLocalString("unlock.file.user.notAdminRealm",
                        "Realm {0} is not an AdminFileRealm; lockout not applicable.",
                        authRealmName));
                report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                return;
            }

            report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
        } catch (BadRealmException e) {
            report.setMessage(localStrings.getLocalString("unlock.file.user.unlockFailed",
                    "Unlocking user {0} in file realm {1} failed.",
                    userName, authRealmName) + "  " + e.getLocalizedMessage());
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            report.setFailureCause(e);
        }
    }
}
