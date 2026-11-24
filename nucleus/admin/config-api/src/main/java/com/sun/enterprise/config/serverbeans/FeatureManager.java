/*
 * Copyright (c) 2026 Payara Foundation and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 */

package com.sun.enterprise.config.serverbeans;

import org.jvnet.hk2.config.Attribute;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.Configured;

/**
 * 特性化启动声明 (feature-resolution, 见 docs/adr/0001)。
 *
 * <p>位于 {@code <server>} 元素下, 声明该实例装配的特性, 逗号分隔:
 * <pre>{@code
 *   <feature-manager features="web,admin-rest"/>
 * }</pre>
 *
 * <p>由启动早期的 FeatureResolver 在装配 OSGi bundle 前读取; 与
 * {@code -Dpayara.feature.<name>=true} 声明通道等价并可叠加。
 */
@Configured
public interface FeatureManager extends ConfigBeanProxy {

    /**
     * 逗号分隔的特性名列表, 例如 {@code web,admin-rest}。
     * 空或未设置表示不启用特性化启动（全量装载）。
     */
    @Attribute
    String getFeatures();

    void setFeatures(String value);
}
