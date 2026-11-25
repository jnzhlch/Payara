/*
 * Copyright (c) 2026 Payara Foundation and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 */

package com.sun.enterprise.v3.admin.feature;

import java.beans.PropertyVetoException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import org.glassfish.api.ActionReport;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandLock;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.config.support.TargetType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.SingleConfigCode;
import org.jvnet.hk2.config.TransactionFailure;

import com.sun.enterprise.config.serverbeans.FeatureManager;
import com.sun.enterprise.config.serverbeans.Server;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.util.LocalStringManagerImpl;

import jakarta.inject.Inject;

import java.util.logging.Logger;

/**
 * 运行时装配特性 (feature-resolution 二期, 见 docs/adr/0002)。
 *
 * <pre>{@code
 *   payara-add-feature jpa
 *   payara-add-feature --dry-run jpa
 * }</pre>
 *
 * <p>流程: 解析先行预检 → 运行框架安装增量 bundle → habitat 自动补装 →
 * 成功后写回 {@code <feature-manager features="..."/>}（重启一致）。
 * 移除特性: 修改声明后重启生效（运行时不卸载）。
 */
@Service(name = "payara-add-feature")
@PerLookup
@I18n("payara.add.feature")
@CommandLock(CommandLock.LockType.NONE)
@TargetType(value = {org.glassfish.config.support.CommandTarget.DAS})
@RestEndpoints({
        @RestEndpoint(configBean = Domain.class,
                opType = RestEndpoint.OpType.POST,
                path = "payara-add-feature",
                description = "Provision a feature into the running server")
})
public class PayaraAddFeatureCommand implements AdminCommand {

    private static final Logger logger = Logger.getLogger(PayaraAddFeatureCommand.class.getPackage().getName());


    private static final LocalStringManagerImpl i18n = new LocalStringManagerImpl(PayaraAddFeatureCommand.class);

    @Inject
    private Domain domain;

    @Inject
    private FeatureProvisioner provisioner;

    @Param(primary = true, multiple = true)
    private String[] features;

    @Param(name = "dryrun", optional = true, defaultValue = "false")
    private boolean dryRun;

    @Override
    public void execute(AdminCommandContext context) {
        final ActionReport report = context.getActionReport();
        Set<String> names = new LinkedHashSet<>(Arrays.asList(features));

        // 幂等: 已在声明中的特性跳过（重复声明无害）
        Set<String> pending = new LinkedHashSet<>(names);

        try {
            FeatureProvisioner.Result result = provisioner.provision(pending, dryRun);

            if (dryRun) {
                report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
                report.setMessage(i18n.getLocalString("dryrun.ok",
                        "预检通过: 目标 bundle {0} 个 (dry-run, 未触碰运行框架)", result.closureSize));
                return;
            }

            persist(names);

            report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
            report.setMessage(i18n.getLocalString("ok",
                    "特性装配完成: 新装 bundle {0} 个, 特性闭包 {1} 个; 声明已写回 feature-manager",
                    result.installed.size(), result.closureSize));
        } catch (Exception e) {
            StringBuilder msg = new StringBuilder();
            Throwable t = e;
            for (int i = 0; t != null && i < 6; i++) {
                if (i > 0) {
                    msg.append(" <= ");
                }
                msg.append(t.toString());
                t = t.getCause();
            }
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            report.setMessage(msg.toString());
            report.setFailureCause(e);
            logger.log(java.util.logging.Level.SEVERE, "payara-add-feature 失败", e);
        }
    }

    /** 成功后写回 feature-manager（元素存在直写; 不存在则 createChild） */
    private void persist(Set<String> added) throws TransactionFailure {
        Server server = domain.getServerNamed("server");
        FeatureManager fm = server.getFeatureManager();
        if (fm != null) {
            ConfigSupport.apply(new SingleConfigCode<FeatureManager>() {
                @Override
                public Object run(FeatureManager proxy) throws PropertyVetoException {
                    String current = proxy.getFeatures() == null ? "" : proxy.getFeatures();
                    java.util.TreeSet<String> set = new java.util.TreeSet<>();
                    for (String s : current.split("[,\\s]+")) {
                        if (!s.isEmpty()) {
                            set.add(s);
                        }
                    }
                    set.addAll(added);
                    proxy.setFeatures(String.join(",", set));
                    return null;
                }
            }, fm);
            return;
        }
        ConfigSupport.apply(new SingleConfigCode<Server>() {
            @Override
            public Object run(Server proxy) throws PropertyVetoException, TransactionFailure {
                FeatureManager child = proxy.createChild(FeatureManager.class);
                child.setFeatures(String.join(",", added));
                proxy.setFeatureManager(child);
                return null;
            }
        }, server);
    }
}
