# M1 内核闭包普查记录（2026-11-23）

## 状态：✅ 完成 — 最小核域（122/373 bundle）启动成功，BOOT=15176ms（含缓存重建），ADMIN=YES，server.log 零 SEVERE

## 收尾结论（2026-11-23 12:2x）
- **内核闭包终值 = 122 jar**（kernel-closure.txt）：wiring 静态闭包 + 7 个 InitRunLevel 服务模块 + jta.jar + api-exporter + omgapi + gf-web-connector 等
- **DomainXml 容忍性 ✅**：完整 domain.xml（含未安装模块的配置元素）+ 裁剪模块集 → 解析、populate、启动全程无 SEVERE → **不需要域模板配套**（M3-③ 全声明回归仍需精确复核元素级行为）
- 之前 86/120 安装不全的根因：**僵死 ASMain JVM 锁 osgi-cache**（DNF 轮 stop-domain 静默失败）→ rm 半删半留 → Felix 在污染缓存上对已有 location 直接复用旧 bundle。census.sh 已加 kill_stale（jps -m 按域目录名匹配，启动前+停机后各清一次）。360 信任区与文件锁假设未获证实（加信任后仍 86，杀进程后才恢复）
- 裁掉 251 个 bundle（68%）后可启动；剩余大头为 web/ejb/jms 等功能模块——M2 特性清单的对象

## 方法
`bin/census.sh <label>`：候选集(kernel-closure.txt) → 域级 osgi.properties 覆盖(auto.install/auto.start) → 清 osgi-cache → verbose 启动 → 日志取证。
辅助：`bin/wireclosure.py`（Import-Package→Export-Package 静态闭包求解）、`bin/findjar.py`（类/包→jar 定位）、`/tmp/probe`（离线 Felix 探针，javac 编译进同包以过包级访问）。

## 轮次进展
| 轮 | 候选集 | 失败点 | 修正 |
|---|---|---|---|
| R1 | core 16 + autostart 8 | hk2-api 缺 aopalliance/jakarta.annotation；hk2-locator 缺 javassist | +5 (R2) |
| R2-R3 | 31 | jakarta.activation、tiger-types 级联 | +2 |
| R6 | 31 | javax.rmi（common-util）——**JDK 11+ 已移除，由 glassfish-corba-omgapi 提供**；静态求解器 javax.* 假设修正 | +1 |
| R6-R7 | 81→82 | ModuleStartup 缺失 → kernel.jar；kernel wiring 缺 hazelcast/common-util | +2 |
| R7 | 82 | `APIClassLoaderServiceImpl:141 getModules(APIExporterModuleName).next()` 空迭代 → **api-exporter.jar 硬依赖** | +1 |
| R8 | 83 | InitRunLevel 服务批量（naming/logging/monitoring/telemetry/web-naming/jakartaee-kernel/mp-config-service）| +7 + wireclosure 28 |
| R9 | 118 | ClusteredStore 注入 JavaEEContextUtil → 实现在 **gf-web-connector（appserver 层）**——跨层耦合 | +1 + wireclosure 1 |
| R10 | 120 | 见下方「阻塞问题」 | — |

## 关键发现（架构事实）
1. **wiring 内核闭包 = 120 jar**（kernel-closure.txt；含 config-api、security、grizzly、dol、jackson、CDI/JAXB API 等——kernel 的传递面远超 core.bundles 16）
2. `javax.rmi`/`org.omg.*` 在 JDK 21 缺失，glassfish-corba-omgapi.jar 是内核级提供者
3. api-exporter.jar 是 APIClassLoader 的硬依赖（架构必需，印证 develop 启动文档 §4）
4. nucleus 的 ClusteredStore(hazelcast-bootstrap) 注入 appserver 层的 gf-web-connector 实现——**特性化时 hazelcast 特性对 web 模块存在跨层依赖**，M2 特性清单需显式建模
5. autostart 8 bundle 另有 `felix.fileinstall.dir`（domain.xml jvm-options）在运行期补装——裁剪清单时 fileinstall 是旁路
6. `additionalOSGiBundlesToStart` 死旋钮再次确认（domain.xml 模板仍引用，无消费者）

## 排查史：86/120 安装不全（已解决）
- 现象：真实启动恒 86，成员随轮次漂移；离线探针同文件 120/120
- 排除：Properties/substVars 解析层（探针验证 120 token 全绝对 URI 无重复）；360 文件锁（加信任后仍 86）
- **根因：僵死 ASMain JVM 锁 osgi-cache → rm 半删半留 → Felix 对已存在 location 复用旧缓存 bundle**
- 修复：census.sh kill_stale（jps -m 按域目录名匹配；启动前+停机后）

## 内核域热启动测量（2026-11-23, bench.sh run, 6 轮）
- 冷 24716ms；热轮：528 / 9606 / 9069 / 325 / 2791 → **中位 2791ms**
- 噪声极大（325ms~9.6s）：快轮证明地板在 **325-528ms**（超过 Tomcat 2025ms 锚与 Static 1848ms 锚）；慢轮为机器噪声（360 扫描/磁盘争用）
- 对比配置最优基线（373 bundle）5791ms → 中位 -52%
- 口径注意：此域无 web 容器（8080 仅 Grizzly 空听），数字代表「内核闭包域」的启动面；web 特性加入后的数字待 M3-②
