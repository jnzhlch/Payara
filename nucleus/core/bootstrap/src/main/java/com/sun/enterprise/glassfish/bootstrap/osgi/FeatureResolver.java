/*
 * Copyright (c) 2026 Payara Foundation and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * Feature-resolution provisioning — see docs/adr/0001 in the fork repository.
 */
package com.sun.enterprise.glassfish.bootstrap.osgi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * 特性化启动解析器（一期）。
 *
 * <p>声明载体：系统属性 {@code payara.feature.<name>=true}。未声明任何特性时不做任何干预
 * （安装/启动清单保持现状——全量），保证向后兼容（ADR 0001 回归红线 M3-③）。
 *
 * <p>声明了特性时：安装/启动清单改写为「内核闭包 + 声明特性及其依赖特性的 bundle 并集」。
 *
 * <p>特性清单（feature manifest）为 properties 文件，位于
 * {@code <domainDir>/config/features/<name>.properties}（优先）与
 * {@code <installRoot>/config/features/<name>.properties}，键：
 * <pre>
 *   feature.bundles=web-core.jar,web-glue.jar,...   （模块 jar 名，相对 modules/）
 *   feature.dependencies=cdi,jdbc                    （依赖的其他特性，自动并入闭包）
 * </pre>
 *
 * <p>内核闭包来自 bootstrap 资源 {@code META-INF/payara/kernel-bundles.txt}
 * （一行一个 jar 名，可被 {@code <installRoot>/config/kernel-bundles.txt} 覆盖）。
 *
 * <p>fail-fast：声明的特性不存在、bundle 文件缺失 → 直接抛异常终止启动并给出可用清单。
 */
class FeatureResolver {

    static final String FEATURE_PROP_PREFIX = "payara.feature.";
    private static final String KERNEL_RESOURCE = "/META-INF/payara/kernel-bundles.txt";
    private static final String KERNEL_OVERRIDE_FILE = "config/kernel-bundles.txt";
    private static final String FEATURES_DIR = "config/features";
    private static final String AUTO_INSTALL_PROP = Constants.AUTO_INSTALL_PROP;
    private static final String AUTO_START_PROP = Constants.AUTO_START_PROP;
    private static final String MANIFEST_BUNDLES_KEY = "feature.bundles";
    private static final String MANIFEST_DEPS_KEY = "feature.dependencies";

    private static final Logger logger = Logger.getLogger(FeatureResolver.class.getPackage().getName());

    private FeatureResolver() {
    }

    /**
     * 若声明了任何 payara.feature.*，改写 props 中的安装/启动清单；否则原样返回。
     * 声明来源三通道合并：domain.xml 的 feature-manager 元素、props 键（域级 osgi.properties）、
     * 服务器 JVM 系统属性（domain.xml jvm-options）。
     */
    static void applyTo(Properties props) {
        Set<String> declared = new LinkedHashSet<>();
        File domainDir = dirFromUri(props.getProperty(
                com.sun.enterprise.glassfish.bootstrap.Constants.INSTANCE_ROOT_URI_PROP_NAME));
        if (domainDir != null) {
            declared.addAll(declaredFromDomainXml(new File(domainDir, "config/domain.xml")));
        }
        declared.addAll(collectDeclaredFeatures(props));
        declared.addAll(collectDeclaredFeatures(System.getProperties()));
        if (declared.isEmpty()) {
            return; // 未声明特性：维持现状（全量安装）
        }

        File installDir = dirFromUri(props.getProperty(
                com.sun.enterprise.glassfish.bootstrap.Constants.INSTALL_ROOT_URI_PROP_NAME));

        Map<String, Properties> manifests = loadManifests(installDir, domainDir);

        // 特性闭包：声明特性 + 传递依赖；未知名 fail-fast
        Set<String> closure = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(declared);
        while (!pending.isEmpty()) {
            String name = pending.poll();
            if (!closure.add(name)) {
                continue;
            }
            Properties mf = manifests.get(name);
            if (mf == null) {
                throw new IllegalArgumentException(
                        "未声明的特性: '" + name + "' 没有对应特性清单。可用特性: "
                                + new TreeSet<>(manifests.keySet())
                                + "。声明方式: -Dpayara.feature.<name>=true");
            }
            for (String dep : splitCsv(mf.getProperty(MANIFEST_DEPS_KEY))) {
                if (!closure.contains(dep)) {
                    pending.add(dep);
                }
            }
        }

        // bundle 并集 = 内核闭包 + 各特性 bundle；文件存在性 fail-fast
        Set<String> bundles = new LinkedHashSet<>();
        bundles.addAll(loadKernelBundles(installDir));
        for (String name : closure) {
            for (String b : splitCsv(manifests.get(name).getProperty(MANIFEST_BUNDLES_KEY))) {
                bundles.add(b);
            }
        }
        List<String> located = locateBundles(installDir, bundles);

        String joined = String.join(" ", located);
        props.setProperty(AUTO_INSTALL_PROP, joined);
        props.setProperty(AUTO_START_PROP, joined);
        logger.logp(Level.INFO, FeatureResolver.class.getName(), "applyTo",
                "特性解析: 声明={0}, 闭包={1}, bundle 总数={2} (内核闭包含内)",
                new Object[]{new TreeSet<>(declared), new TreeSet<>(closure), located.size()});
    }

    private static Set<String> collectDeclaredFeatures(Properties props) {
        Set<String> declared = new LinkedHashSet<>();
        Enumeration<?> names = props.propertyNames();
        while (names.hasMoreElements()) {
            String key = (String) names.nextElement();
            if (key.startsWith(FEATURE_PROP_PREFIX)
                    && Boolean.parseBoolean(props.getProperty(key))) {
                declared.add(key.substring(FEATURE_PROP_PREFIX.length()));
            }
        }
        return declared;
    }

    /**
     * 从 domain.xml 读取目标实例的 {@code <feature-manager features="..."/>} 声明。
     * 自带 StAX 解析（habitat 未建立，config bean 不可用）；目标实例默认 DAS 的
     * {@code server}（com.sun.aas.instanceName 在此阶段尚未设置）。
     */
    private static Set<String> declaredFromDomainXml(File domainXml) {
        Set<String> declared = new LinkedHashSet<>();
        if (!domainXml.isFile()) {
            return declared;
        }
        String instance = System.getProperty("com.sun.aas.instanceName", "server");
        try (InputStream in = new FileInputStream(domainXml)) {
            XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(in);
            int depth = 0;
            int serverDepth = -1;
            int fmDepth = -1;
            boolean inTargetServer = false;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    String local = r.getLocalName();
                    if (local.equals("server") && serverDepth == -1) {
                        serverDepth = depth;
                        inTargetServer = instance.equals(r.getAttributeValue(null, "name"));
                    } else if (inTargetServer && local.equals("feature-manager")) {
                        fmDepth = depth;
                        String features = r.getAttributeValue(null, "features");
                        if (features != null) {
                            for (String f : features.split("[,\\s]+")) {
                                if (!f.isEmpty()) {
                                    declared.add(f);
                                }
                            }
                        }
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    if (fmDepth == depth) {
                        fmDepth = -1;
                    } else if (serverDepth == depth) {
                        serverDepth = -1;
                        inTargetServer = false;
                    }
                    depth--;
                }
            }
            r.close();
        } catch (Exception e) {
            // domain.xml 缺失或不可解析时按无声明处理（保持全量装载），不做 fail-fast
            Logger.getLogger(FeatureResolver.class.getName()).log(Level.FINE,
                    "读取 domain.xml 特性声明失败, 按无声明处理: " + domainXml, e);
        }
        return declared;
    }

    /**
     * 把 bundle jar 名解析为绝对 URI 列表（modules/ 或 modules/autostart/），缺失即 fail-fast。
     * 注意：此时 Util.substVars 已执行完毕，写入的值必须是可直接解析的真实 URI。
     */
    private static List<String> locateBundles(File installDir, Set<String> bundles) {
        String base = installDir.toURI().toString(); // 以 '/' 结尾
        List<String> result = new ArrayList<>();
        for (String jar : bundles) {
            File f = new File(installDir, "modules/" + jar);
            String sub = "modules/";
            if (!f.isFile()) {
                f = new File(installDir, "modules/autostart/" + jar);
                sub = "modules/autostart/";
            }
            if (!f.isFile()) {
                throw new IllegalArgumentException(
                        "特性清单引用的 bundle 不存在: " + jar + "（发行版与清单不匹配）");
            }
            result.add(base + sub + jar);
        }
        return result;
    }

    private static Set<String> loadKernelBundles(File installDir) {
        // 覆盖文件优先: <installRoot>/config/kernel-bundles.txt
        File override = new File(installDir, KERNEL_OVERRIDE_FILE);
        if (override.isFile()) {
            try (InputStream in = new FileInputStream(override)) {
                return readLines(in);
            } catch (IOException e) {
                throw new IllegalStateException("读取 " + override + " 失败", e);
            }
        }
        InputStream in = FeatureResolver.class.getResourceAsStream(KERNEL_RESOURCE);
        if (in == null) {
            throw new IllegalStateException("内核闭包资源缺失: " + KERNEL_RESOURCE);
        }
        try (InputStream is = in) {
            return readLines(is);
        } catch (IOException e) {
            throw new IllegalStateException("读取内核闭包资源失败", e);
        }
    }

    private static Set<String> readLines(InputStream in) throws IOException {
        Set<String> lines = new LinkedHashSet<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private static Map<String, Properties> loadManifests(File installDir, File domainDir) {
        Map<String, Properties> manifests = new HashMap<>();
        List<File> dirs = new ArrayList<>();
        if (installDir != null) {
            dirs.add(new File(installDir, FEATURES_DIR));
        }
        if (domainDir != null) {
            dirs.add(new File(domainDir, FEATURES_DIR)); // 域级覆盖
        }
        for (File dir : dirs) {
            File[] files = dir.listFiles((d, n) -> n.endsWith(".properties"));
            if (files == null) {
                continue;
            }
            for (File f : files) {
                Properties mf = new Properties();
                try (InputStream in = new FileInputStream(f)) {
                    mf.load(in);
                } catch (IOException e) {
                    throw new IllegalStateException("特性清单解析失败: " + f, e);
                }
                manifests.put(f.getName().substring(0, f.getName().length() - ".properties".length()), mf);
            }
        }
        return manifests;
    }

    private static List<String> splitCsv(String value) {
        List<String> out = new ArrayList<>();
        if (value != null) {
            for (String s : value.split("[,\\s]+")) {
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static File dirFromUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        try {
            return new File(new URI(uri));
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("非法目录 URI: " + uri, e);
        }
    }
}
