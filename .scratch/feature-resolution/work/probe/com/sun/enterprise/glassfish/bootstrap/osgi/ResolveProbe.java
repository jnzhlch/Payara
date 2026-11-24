package com.sun.enterprise.glassfish.bootstrap.osgi;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * 离线解析探针: 裸 Felix + BundleProvisioner, 按 OSGiGlassFishRuntimeBuilder.build() 的真实顺序执行
 * (init -> install -> 逐个 start(START_ACTIVATION_POLICY) -> framework.start()), 然后报告
 * 仍处于 INSTALLED 的 bundle 及其原因, 以及未挂上宿主的 fragment。
 *
 * 用法: ResolveProbe <osgi.properties> <out.txt> [felix.log.level] [顺序文件(每行一个 jar 名)]
 */
public class ResolveProbe {

    public static void main(String[] args) throws Exception {
        String propsPath = args[0];
        String outPath = args[1];
        PrintStream out = new PrintStream(new FileOutputStream(outPath), true);
        Logger.getLogger("").setLevel(Level.OFF);

        Properties props = new Properties();
        try (InputStream in = new FileInputStream(propsPath)) {
            props.load(in);
        }
        com.sun.enterprise.glassfish.bootstrap.Util.substVars(props);

        // 与 OSGiFrameworkLauncher.launchOSGiFrameWork 一致: 整份属性原样作为框架配置, 仅覆盖存储目录
        Path cache = Files.createTempDirectory("resolve-probe-");
        Map<String, String> conf = new HashMap<>();
        for (String name : props.stringPropertyNames()) {
            conf.put(name, props.getProperty(name));
        }
        conf.put("org.osgi.framework.storage", cache.toString());
        if (args.length > 2 && !args[2].isEmpty()) {
            conf.put("felix.log.level", args[2]);
        }

        FrameworkFactory ff = ServiceLoader.load(FrameworkFactory.class, ResolveProbe.class.getClassLoader()).iterator().next();
        Framework fw = ff.newFramework(conf);
        fw.init();
        try {
            BundleProvisioner bp = BundleProvisioner.createBundleProvisioner(fw.getBundleContext(), props);
            bp.installBundles();

            // Step 2 (build): framework 尚未 start 时逐个 start —— Felix 只登记, 不解析
            for (String uri : props.getProperty("glassfish.osgi.auto.start").trim().split("\\s+")) {
                Bundle b = find(fw, uri);
                if (b == null) {
                    out.println("NOT-INSTALLED " + uri.substring(uri.lastIndexOf('/') + 1));
                    continue;
                }
                if (b.getHeaders().get("Fragment-Host") != null) {
                    continue;
                }
                try {
                    b.start(Bundle.START_ACTIVATION_POLICY);
                } catch (BundleException e) {
                    out.println("START-FAILED " + b.getSymbolicName() + ": " + oneLine(reason(e), 700));
                }
            }

            // Step 3: framework.start() —— 推迟的启动在这里真正触发解析
            fw.start();
            Thread.sleep(3000);

            int active = 0;
            int lazyStarting = 0;
            List<String> unresolved = new ArrayList<>();
            List<String> unattached = new ArrayList<>();
            for (Bundle b : fw.getBundleContext().getBundles()) {
                if (b.getBundleId() == 0) {
                    continue;
                }
                boolean fragment = b.getHeaders().get("Fragment-Host") != null;
                int st = b.getState();
                if (fragment) {
                    if (st == Bundle.INSTALLED) {
                        // 让 Felix 给出未挂接的原因: 对 fragment 调 start() 会抛 "Fragment cannot be started",
                        // 所以改为解析其宿主, 取宿主方的原因
                        unattached.add(b.getSymbolicName() + " -> host " + b.getHeaders().get("Fragment-Host"));
                    }
                    continue;
                }
                if (st == Bundle.ACTIVE) {
                    active++;
                } else if (st == Bundle.STARTING) {
                    lazyStarting++;
                } else if (st == Bundle.INSTALLED || st == Bundle.RESOLVED) {
                    String why = "";
                    try {
                        b.start(Bundle.START_ACTIVATION_POLICY);
                        why = "(repeat start succeeded; state=" + stateName(b.getState()) + ")";
                    } catch (BundleException e) {
                        why = oneLine(reason(e), 700);
                    }
                    unresolved.add(b.getSymbolicName() + " [" + b.getBundleId() + "] state=" + stateName(st) + ": " + why);
                }
            }
            out.println("installed=" + (fw.getBundleContext().getBundles().length - 1)
                    + " active=" + active + " lazy-starting(resolved)=" + lazyStarting
                    + " unresolved=" + unresolved.size() + " unattached-fragments=" + unattached.size());
            for (String u : unresolved) {
                out.println("UNRESOLVED " + u);
            }
            for (String u : unattached) {
                out.println("UNATTACHED " + u);
            }
        } finally {
            try {
                fw.stop();
                fw.waitForStop(5000);
            } catch (Exception ignored) {
                // 探针退出, 忽略
            }
            try {
                deleteRecursively(cache);
            } catch (IOException ignored) {
                // Windows 上可能仍有文件被占用, 临时目录留给系统清理
            }
        }
        System.exit(0);
    }

    private static Bundle find(Framework fw, String uri) {
        for (Bundle b : fw.getBundleContext().getBundles()) {
            if (uri.equals(b.getLocation())) {
                return b;
            }
        }
        return null;
    }

    private static String reason(BundleException e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        for (int i = 0; t != null && i < 6; i++) {
            if (i > 0) {
                sb.append(" <= ");
            }
            sb.append(t.getMessage() == null ? t.toString() : t.getMessage());
            t = t.getCause();
        }
        return sb.toString();
    }

    private static String stateName(int st) {
        switch (st) {
            case Bundle.INSTALLED: return "INSTALLED";
            case Bundle.RESOLVED: return "RESOLVED";
            case Bundle.STARTING: return "STARTING";
            case Bundle.STOPPING: return "STOPPING";
            case Bundle.ACTIVE: return "ACTIVE";
            default: return String.valueOf(st);
        }
    }

    private static String oneLine(String s, int max) {
        if (s == null) {
            return "";
        }
        s = s.replaceAll("\\s+", " ");
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
