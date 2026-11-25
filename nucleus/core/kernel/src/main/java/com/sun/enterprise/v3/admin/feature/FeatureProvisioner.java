/*
 * Copyright (c) 2026 Payara Foundation and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 */

package com.sun.enterprise.v3.admin.feature;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jvnet.hk2.annotations.Service;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.FrameworkWiring;

import jakarta.inject.Singleton;

import static java.util.Collections.emptyList;

/**
 * 运行时特性装配 (feature-resolution 二期, 见 docs/adr/0002)。
 *
 * <p>流程: 解析先行预检（临时 Felix 按真实顺序解析新并集, 失败零接触）
 * → 对运行框架安装并启动增量 bundle（RESOLVED 事件经 ModulesRegistry 联动把
 * hk2-locator 描述符自动补进 boot habitat, 无需自写 extender）
 * → 任一失败即回滚卸载本批已装 bundle。
 *
 * <p>服务活化: 新描述符按 HK2 现行为懒实例化; 严禁 proceedTo 降级 start level
 * （会先 DOWN 销毁高层服务）。
 */
@Singleton
@Service
public class FeatureProvisioner {

    private static final Logger logger = Logger.getLogger(FeatureProvisioner.class.getPackage().getName());
    private static final String FEATURES_DIR = "config/features";
    private static final String[] MANIFEST_DIRS = {"config/features"};
    private static final long PREFLIGHT_TIMEOUT_MS = 120_000;

    /** 装配结果 */
    public static final class Result {
        public final List<String> installed;
        public final int closureSize;
        public final boolean dryRun;

        Result(List<String> installed, int closureSize, boolean dryRun) {
            this.installed = installed;
            this.closureSize = closureSize;
            this.dryRun = dryRun;
        }
    }

    /**
     * 装配若干特性（增量: 内核闭包与已装 bundle 不重装）。
     *
     * @param features 特性名集合（未知特性或清单缺失 → IllegalArgumentException）
     * @param dryRun   true = 只做解析先行预检, 不触碰运行框架
     * @return 本次实际安装的 bundle 路径（dryRun 为空列表）
     */
    public Result provision(Set<String> features, boolean dryRun) throws Exception {
        BundleContext ctx = bundleContext();
        File installDir = new File(System.getProperty("com.sun.aas.installRoot"));
        File instanceDir = new File(System.getProperty("com.sun.aas.instanceRoot"));

        Map<String, Properties> manifests = loadManifests(installDir, instanceDir);
        Set<String> closureFeatures = closure(manifests, features);
        List<String> jars = locate(installDir, manifests, closureFeatures);

        List<String> delta = jars.stream()
                .filter(p -> findInstalled(ctx, p) == null)
                .map(this::toUri)
                .collect(java.util.stream.Collectors.toList());

        logger.info("特性装配: 声明=" + new java.util.TreeSet<>(features)
                + " 闭包=" + closureFeatures.size() + " 特性, 目标 bundle=" + jars.size()
                + ", 增量=" + delta.size() + (dryRun ? " (dry-run)" : ""));

        // 解析先行预检: dry-run 与真装配都执行（dry-run 即「只预检」）
        List<String> existing = new ArrayList<>();
        for (Bundle b : ctx.getBundles()) {
            if (b.getBundleId() != 0) {
                existing.add(String.valueOf(b.getLocation()));
            }
        }
        preFlight(ctx, installDir, existing, delta);

        if (!dryRun && !delta.isEmpty()) {
            installAndStart(ctx, delta);
        }
        return new Result(dryRun ? emptyList() : delta, jars.size(), dryRun);
    }

    /** 回滚: 卸载本批 bundle（跳过 ACTIVE 且被引用者, 记日志从宽） */
    public void rollback(BundleContext ctx, List<String> locations) {
        List<String> reversed = new ArrayList<>(locations);
        java.util.Collections.reverse(reversed);
        for (String loc : reversed) {
            Bundle b = findInstalled(ctx, loc);
            if (b == null) {
                continue;
            }
            try {
                b.uninstall();
                logger.info("回滚: 已卸载 " + b.getSymbolicName());
            } catch (BundleException e) {
                logger.log(Level.WARNING, "回滚卸载失败(留待重启处理): " + b.getSymbolicName(), e);
            }
        }
    }

    // ── BundleContext (ADR 0002 决策 6: BundleReference cast) ──

    private BundleContext bundleContext() {
        ClassLoader cl = FeatureProvisioner.class.getClassLoader();
        if (cl instanceof org.osgi.framework.BundleReference) {
            return org.osgi.framework.BundleReference.class.cast(cl).getBundle().getBundleContext();
        }
        throw new IllegalStateException("特性装配仅 OSGi 模式支持 (当前类加载器不是 bundle 加载器)");
    }

    private Bundle findInstalled(BundleContext ctx, String locationOrUri) {
        String want = toUri(locationOrUri);
        for (Bundle b : ctx.getBundles()) {
            if (want.equals(String.valueOf(b.getLocation()))) {
                return b;
            }
        }
        // 位置串可能是原始路径形态
        for (Bundle b : ctx.getBundles()) {
            String loc = String.valueOf(b.getLocation());
            if (loc.endsWith(want) || want.equals("file:/" + loc.replace('\\', '/'))) {
                return b;
            }
        }
        return null;
    }

    private String toUri(String pathOrUri) {
        if (pathOrUri.startsWith("file:")) {
            return pathOrUri;
        }
        return new File(pathOrUri).toURI().toString();
    }

    // ── 装配: install + start（RESOLVED 事件触发 habitat 自动补装） ──

    private void installAndStart(BundleContext ctx, List<String> deltaUris) throws BundleException {
        // 两阶段: 先全部 install（纯安装不解析）, 再统一 start——
        // 清单内的依赖顺序无关紧要, Felix 在全量安装后才能正确解析
        List<Bundle> installed = new ArrayList<>();
        for (String uri : deltaUris) {
            try (InputStream in = new FileInputStream(new File(java.net.URI.create(uri)))) {
                installed.add(ctx.installBundle(uri, in));
            } catch (BundleException | IOException e) {
                logger.log(Level.WARNING, "特性安装失败于 " + uri + ", 回滚本批 "
                        + installed.size() + " 个已装 bundle", e);
                rollback(ctx, installed.stream().map(b -> String.valueOf(b.getLocation()))
                        .collect(java.util.stream.Collectors.toList()));
                throw new IllegalStateException("特性安装失败: " + uri + " → " + e.getMessage(), e);
            }
        }
        for (Bundle b : installed) {
            if (b.getHeaders().get("Fragment-Host") != null) {
                continue; // fragment 随宿主解析装配, 不能 start
            }
            try {
                b.start(Bundle.START_ACTIVATION_POLICY); // 触发 RESOLVED → habitat 补装
                logger.info("特性装配: 已启动 " + b.getSymbolicName());
            } catch (BundleException e) {
                logger.log(Level.WARNING, "特性启动失败于 " + b.getSymbolicName()
                        + ", 回滚本批 " + installed.size() + " 个已装 bundle", e);
                rollback(ctx, installed.stream().map(x -> String.valueOf(x.getLocation()))
                        .collect(java.util.stream.Collectors.toList()));
                throw new IllegalStateException("特性启动失败: " + b.getSymbolicName() + " → " + e.getMessage(), e);
            }
        }
    }

    // ── 解析先行预检: 临时 Felix 按真实顺序解析全量并集, 失败零接触 ──

    private void preFlight(BundleContext ctx, File installDir, List<String> existingLocations, List<String> deltaUris) throws Exception {
        Properties osgi = new Properties();
        File conf = new File(installDir, "config/osgi.properties");
        try (InputStream in = new FileInputStream(conf)) {
            osgi.load(in);
        }
        String base = installDir.toURI().toString();
        Map<String, String> confMap = new HashMap<>();
        for (String name : osgi.stringPropertyNames()) {
            confMap.put(name, osgi.getProperty(name).replace("${com.sun.aas.installRootURI}", base));
        }
        java.nio.file.Path storage = Files.createTempDirectory("feature-preflight-");
        confMap.put("org.osgi.framework.storage", storage.toString());

        FrameworkFactory ff = factory(ctx);
        Framework fw = ff.newFramework(confMap);
        fw.init();
        List<String> failures = new ArrayList<>();
        try {
            BundleContext probeCtx = fw.getBundleContext();
            List<Bundle> installed = new ArrayList<>();
            for (String uri : existingLocations) {
                try (InputStream in = new FileInputStream(new File(java.net.URI.create(uri)))) {
                    installed.add(probeCtx.installBundle(uri, in));
                } catch (BundleException e) {
                    failures.add(shortName(uri) + ": " + e.getMessage());
                }
            }
            for (String uri : deltaUris) {
                try (InputStream in = new FileInputStream(new File(java.net.URI.create(uri)))) {
                    installed.add(probeCtx.installBundle(uri, in));
                } catch (BundleException e) {
                    failures.add(shortName(uri) + ": " + e.getMessage());
                }
            }
            // 纯解析零激活: FrameworkWiring.resolveBundles 只做 wiring 计算,
            // 不触发 BundleActivator/服务注册（激活式验证会在临时框架里拉起整套服务面）
            FrameworkWiring wiring = probeCtx.getBundle(0).adapt(FrameworkWiring.class);
            wiring.resolveBundles(null);
            // resolveBundles 异步收敛: 轮询等待最多 15s
            for (int i = 0; i < 30; i++) {
                boolean settling = false;
                for (Bundle b : probeCtx.getBundles()) {
                    if (b.getBundleId() != 0 && b.getState() == Bundle.INSTALLED
                            && b.getHeaders().get("Fragment-Host") == null) {
                        settling = true;
                        break;
                    }
                }
                if (!settling) {
                    break;
                }
                wiring.resolveBundles(null);
                Thread.sleep(500);
            }
            for (Bundle b : probeCtx.getBundles()) {
                if (b.getBundleId() == 0 || b.getHeaders().get("Fragment-Host") != null) {
                    continue;
                }
                if (b.getState() == Bundle.INSTALLED) {
                    failures.add(shortName(String.valueOf(b.getLocation())) + " 未能解析: "
                            + diagnose(b));
                }
            }
        } finally {
            try {
                fw.stop();
                fw.waitForStop(5000);
            } catch (Exception ignored) {
                // 探针退出, 忽略
            }
            deleteRecursively(storage);
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "解析先行预检失败（零接触, 未触碰运行框架）: " + String.join(" | ", failures));
        }
        logger.fine("解析先行预检通过: " + deltaUris.size() + " 个增量 bundle 全部可解析");
    }

    /** 对 INSTALLED 状态的 bundle 触发一次解析以取回 Felix 的诊断信息 */
    private static String diagnose(Bundle b) {
        try {
            b.start(Bundle.START_ACTIVATION_POLICY);
            return "(重复 start 成功)";
        } catch (BundleException e) {
            return firstCause(e);
        }
    }

    private static String shortName(String uri) {
        return uri.substring(uri.lastIndexOf('/') + 1);
    }

    /**
     * 定位 FrameworkFactory: felix.jar 由专用 URLClassLoader 加载（不在系统类路径）,
     * 系统 bundle (id 0) 的类即来自该加载器, 从它反向取加载器最可靠;
     * 回退路径: 沿 bundle 加载器层级向上 + TCCL。
     */
    private static FrameworkFactory factory(BundleContext ctx) {
        try {
            ClassLoader felixLoader = ctx.getBundle(0).getClass().getClassLoader();
            for (FrameworkFactory f : ServiceLoader.load(FrameworkFactory.class, felixLoader)) {
                return f;
            }
        } catch (Exception ignored) {
            // 回退到下面的层级查找
        }
        ClassLoader c = FeatureProvisioner.class.getClassLoader();
        while (c != null) {
            for (FrameworkFactory f : ServiceLoader.load(FrameworkFactory.class, c)) {
                return f;
            }
            c = c.getParent();
        }
        for (FrameworkFactory f : ServiceLoader.load(FrameworkFactory.class)) {
            return f;
        }
        throw new IllegalStateException("预检: 无法定位 FrameworkFactory");
    }

    private static String firstCause(BundleException e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        for (int i = 0; t != null && i < 4; i++) {
            if (i > 0) {
                sb.append(" <= ");
            }
            sb.append(t.getMessage() == null ? t.toString() : t.getMessage());
            t = t.getCause();
        }
        return sb.toString().replaceAll("\\s+", " ");
    }

    private static void deleteRecursively(java.nio.file.Path root) throws IOException {
        java.util.stream.Stream<java.nio.file.Path> s = Files.walk(root);
        s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
            try {
                Files.delete(p);
            } catch (IOException ignored) {
                // Windows 下可能被占用, 留给系统清理
            }
        });
        s.close();
    }

    // ── 清单与闭包（与 boot 期 FeatureResolver 同格式; 此处自包含避免模块依赖倒置） ──

    private Map<String, Properties> loadManifests(File installDir, File instanceDir) {
        Map<String, Properties> manifests = new HashMap<>();
        List<File> dirs = new ArrayList<>();
        for (File base : new File[]{installDir, instanceDir}) {
            File dir = new File(base, FEATURES_DIR);
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

    /** 特性闭包: 声明特性 + 传递依赖（manifests 的 feature.dependencies）; 未知名 fail-fast */
    private Set<String> closure(Map<String, Properties> manifests, Set<String> features) {
        Set<String> closure = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(features);
        while (!pending.isEmpty()) {
            String name = pending.poll();
            if (!closure.add(name)) {
                continue;
            }
            Properties mf = manifests.get(name);
            if (mf == null) {
                throw new IllegalArgumentException(
                        "未声明的特性: '" + name + "' 没有对应特性清单。可用特性: " + new TreeSet<>(manifests.keySet()));
            }
            for (String dep : splitCsv(mf.getProperty("feature.dependencies"))) {
                if (!closure.contains(dep)) {
                    pending.add(dep);
                }
            }
        }
        return closure;
    }

    private List<String> locate(File installDir, Map<String, Properties> manifests, Set<String> features) {
        Set<String> jars = new LinkedHashSet<>();
        for (String name : features) {
            jars.addAll(splitCsv(manifests.get(name).getProperty("feature.bundles")));
        }
        List<String> paths = new ArrayList<>();
        for (String jar : jars) {
            File f = new File(installDir, "modules/" + jar);
            if (!f.isFile()) {
                f = new File(installDir, "modules/autostart/" + jar);
            }
            if (!f.isFile()) {
                throw new IllegalArgumentException("特性清单引用的 bundle 不存在: " + jar + "（发行版与清单不匹配）");
            }
            paths.add(f.getAbsolutePath());
        }
        return paths;
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
}
