# Payara 特性化启动（Feature Resolution）使用手册

> 版本: 2026-11-24 · 分支: `architecture-optimization` · 决策依据: [ADR 0001](adr/0001-feature-resolution-startup-architecture.md) · 词汇表: [GLOSSARY.md](../GLOSSARY.md)
>
> 状态: MVP 已交付并全链路验证（内核闭包启动 / web 特性部署 / 无声明回归 三门全过）。实验记录与工具链在 `.scratch/feature-resolution/`。

---

## 1. 这是什么

按 OpenLiberty 的「启动特性」思路改造 Payara 启动：**发行版自带全部模块，但域只装载它声明的特性**。

- 不声明任何特性 → 行为与原版完全一致（全量 344 bundle），**零向后兼容风险**
- 声明特性 → 安装/激活集收缩为「内核闭包(135 jar) + 声明特性及其依赖的 bundle 并集」，habitat 随之收缩
- 已验证: 声明 `web`(+cdi) + `admin-rest` → **218 bundle**（-37%），启动、部署、服务、停机全链路正常

三个结构性收益: 启动装载面随声明收缩；habitat/攻击面同步收缩；为未来 CRaC checkpoint 体积缩小铺路。

## 2. 快速上手

```bash
# 1) 在 domain.xml 的 <jvm-options> 里声明特性（每行一个）
<jvm-options>-Dpayara.feature.web=true</jvm-options>
<jvm-options>-Dpayara.feature.admin-rest=true</jvm-options>

# 2) 正常启动
asadmin start-domain

# 3) 验证
asadmin deploy myapp.war
```

就这么简单。`web` 会自动拉入 `cdi` 依赖；`admin-rest` 会自动拉入 `jersey`。

**头号注意: 声明了 `web` 就必须同时声明 `admin-rest`**（headless 域也不例外）——管理 REST 服务端（`rest-service.jar`）是可裁剪特性，不声明则 4848 端口虽然监听，但所有管理命令（包括 `deploy`、`stop-domain`）会得到 202 + 控制台 HTML。

## 3. 声明方式（三种载体等价，可叠加）

| 载体 | 写法 | 适用 |
|---|---|---|
| **domain.xml 元素**（推荐） | `<server name="server">` 下加 `<feature-manager features="web,admin-rest"/>` | 声明随域配置持久化，asadmin set 可管理 |
| **JVM 系统属性** | `domain.xml` 的 `<jvm-options>-Dpayara.feature.web=true</jvm-options>` | 临时实验/覆盖 |
| **域级 osgi.properties** | `<domainDir>/config/osgi.properties` 里加一行 `payara.feature.web=true` | 批量脚本/容器镜像场景 |

三者取并集。值语义：`true` 生效，其它值视为未声明。`feature-manager` 元素默认作用于 DAS 实例 `server`（解析发生在 habitat 建立前，此时实例名尚未注入；多实例部署暂用 -D 通道指定）。

## 4. 内置特性清单

随发行版发布于 `glassfish/config/features/*.properties`。**boot 验证通过**的特性：

| 特性名 | 内容 | bundle 数 | 依赖 |
|---|---|---|---|
| `web` | Servlet/JSP 容器（web-core/web-glue/war-util/wasp/EL/JSTL/websocket/soteria） | 36 | cdi |
| `cdi` | Weld CDI 实现 + javaee 部署支撑 | 36 | — |
| `admin-rest` | asadmin 远程命令/REST 管理协议服务端 | 30 | jersey |
| `jersey` | JAX-RS 运行时（Jersey + Grizzly 容器提供者） | 9 | — |

**生成草稿**（清单已随发行版发布，但尚未逐个 boot 验证，使用前需自行验证）：`jpa` `ejb-lite` `jsf` `jdbc` `jta` `jts` `ha` `hazelcast` `healthcheck` `requesttracing` `notification` `json` `gui` `jmx-monitoring` `payara-rest-endpoints` `payara-executor-service` `asadmin-recorder` `phonehome` `environment-warning` `payara-micro` `h2db` `cluster` `corba-base` `grizzly-full` `management` `common` `common-web` `appserver-core` `glassfish-jca` `glassfish-nucleus` 等 27 份。

清单文件格式：

```properties
# 特性: web (boot 验证通过)
feature.dependencies=cdi          ← 依赖的其他特性（自动并入闭包）
feature.bundles=web-core.jar,web-glue.jar,...   ← 本特性的模块 jar 名（相对 modules/）
```

**内核闭包**（135 jar，任何声明组合下都强制安装）来自 bootstrap 资源 `META-INF/payara/kernel-bundles.txt`，含 HK2 内核、config-api、security、grizzly、部署命令、自动部署服务等 nucleus 基座。可用 `<installRoot>/config/kernel-bundles.txt` 覆盖。

## 5. 语义与行为细则

- **依赖自动并入**：声明 `web` 时 `cdi` 不必声明，依赖边（清单的 `feature.dependencies`）自动拉入
- **fail-fast**：声明了不存在的特性、清单引用的 jar 缺失 → 启动即失败并给出可用特性清单，绝不静默降级
- **清单查找顺序**：`<domainDir>/config/features/` 优先覆盖 `<installRoot>/config/features/`——同一特性的域级定制无需改发行版
- **诊断日志**：解析结果有一条 INFO 日志（声明/闭包/总数），搜 `特性解析`
- **解析器源码**：`nucleus/core/bootstrap/.../osgi/FeatureResolver.java`（提交 `8fb79dc9c6`）

## 6. 运维须知（踩过坑的都在这）

| 症状 | 原因 | 处置 |
|---|---|---|
| 管理命令返回 `202` + 控制台 HTML 页 | 未声明 `admin-rest` | 加声明；或用内核自带通道 `curl http://host:4848/__asadmin/stop-domain` 停机 |
| `deploy` 报 `Command deploy not found` | 同上（部署命令在 `deployment-admin.jar`，属内核闭包，但其 REST 入口依赖 admin-rest） | 加 `admin-rest` 声明 |
| REST 请求 500 + 日志 `No container provider supports HttpHandler` | `jersey` 未被拉入（admin-rest 会自动拉） | 检查 jersey.properties 是否在 features 目录 |
| 域启动 fail-fast 说「清单 jar 不存在」 | 特性清单与发行版不匹配（如手删了 modules 下的 jar） | 恢复 jar 或修正清单 |
| GUI 控制台（4848 网页）不可用 | `__admingui` 尚未纳入特性清单系统（已知限制 §9） | 当前版本 CLI/REST 管理；GUI 需求暂用全量域 |
| 本地 `stop-domain` 说 "No domains are currently running" 但端口在监听 | 本地判定要连管理端口；REST 不可用时判定失败 | 用 `__asadmin/stop-domain` 兜底，或修复 admin-rest 声明 |
| Windows/360 环境 `asadmin` 偶发 `No buffer space available` | 安全软件过滤层间歇吞连接 | CLI 用 `--host 127.0.0.1`；重试 |

**日志判读**：`server.log` 跨启动累积，分析某次启动必须按字节偏移切片（参考 `bin/verify-deploy.sh` 的 logbase 手法）。GBK 控制台下中文日志会乱码，grep 时用英文子串。

## 7. 工具链手册（`.scratch/feature-resolution/bin/`）

> 工具链是实验/验证设施，非发行版内容。所有脚本在 Git Bash (MSYS) 下运行，需 JDK 21 + curl + python。

| 工具 | 用途 | 要点 |
|---|---|---|
| `bench.sh` | 启动基准（setup/tune/run/tomcat 四命令） | `run <label> [rounds]`：1 冷 + N 热、热轮中位；口径=启动命令→8080 首响应。已知坑：`tune` 的 gogo 裁剪对 payara7 根目录的 run 已修复（用 gf_bin 定位）；对特性声明域不要跑 tune 3/3（删 jar 会触发解析 fail-fast） |
| `census.sh` | 内核闭包普查驱动 | 候选集→域级 osgi.properties 覆盖→清缓存→启动→取证；**自带 kill_stale 清理僵死 JVM（勿删）** |
| `wireclosure.py` | OSGi wiring 静态闭包求解 | Import-Package→Export-Package 反查；`--write` 追加回候选集 |
| `findjar.py` | 类/包 → jar 定位 | 新增清单 jar 时的第一排查工具 |
| `gen-features.py` | featureset/packager pom → 特性清单 | featureset 引 packager 用 `type=zip`；packager 的 zip 若指向另一 packager 会下钻（如 glassfish-management→nucleus-management） |
| `emit-dist-features.py` | 合成 dist 级清单 | 验证版优先 + 语义命名映射 + 已验证依赖边；**新依赖边必须先过部署验证再加** |
| `verify-deploy.sh` | 端到端部署验证 | 启动等待→管理面双通道探测→部署→轮询端点→本轮 SEVERE 统计→双通道停机 |
| `work/probe`（离线解析探针） | 不起服务器即可列出「装了但解析不了」的 bundle | **必须复刻真实顺序**：install→逐个 start→framework.start()→清点 INSTALLED；framework.start 前的 start() 只登记不解析（假阳性源头） |

## 8. 架构实现摘要（给维护者）

```
构建期:  featureset/packager pom ──gen-features.py──> packager 清单
              ↓ emit-dist-features.py（验证版优先+语义命名+依赖边）
         glassfish/config/features/  （随发行版）
启动期:  payara.feature.* 声明（domain.xml jvm-options 或域级 osgi.properties）
              ↓ OSGiGlassFishRuntimeBuilder.build() 在 BundleProvisioner 之前
         FeatureResolver: 内核闭包(资源) ∪ 声明特性 ∪ 依赖特性 → 重写 auto.install/auto.start
              ↓ BundleProvisioner（既有机制不变）
         osgi-adapter 按安装集聚合 habitat（面自然收缩）
```

两处内核改动（均已提交）：

| 提交 | 内容 |
|---|---|
| `8fb79dc9c6` | FeatureResolver + 挂载点 + 内核闭包资源 |
| `b2c3a68f05` | `WriteableView` 的 Bean Validation 提供者显式解析（原实现依赖 TCCL 的 ServiceLoader；Grizzly 工作线程 TCCL=bundle 加载器导致找不到提供者——**裁剪域必现**，全量发行版靠启动线程先初始化的顺序侥幸） |
| `c35ca697c3` | `feature-manager` 域元素（config-api bean + 解析器 StAX 读取，二期声明载体） |

**为什么 habitat 不需要运行时扩展**：现有启动时序是「先装 bundle → 后建 habitat」，安装前裁剪即收缩 habitat。运行中动态加特性需要自写 bundle extender（`HK2Populator.populate` API 现成），为二期。

## 9. 已知限制与待办

1. **GUI 控制台未纳入**：`__admingui` 是 system-application 且其 war 由安装器按需部署；声明裁剪域下该链路会因缺 admingui 模块报 ResolveError（不影响 CLI/REST 管理，但控制台页面不可用）。待办：`gui` 特性 + InstallerThread 在无 gui 声明时短路
2. **部分特性清单未 boot 验证**（§4 草稿清单）：每个特性启用前需 census 式迭代验证（方法与工具齐备，每个约 30-60 分钟）
3. **bcel/ldapbp 类 JDK/发行版缺口**：`bcel.jar`（无 commons-io 提供者）与 `ldapbp-repackaged.jar`（JDK 17+ 无 java.security.acl）在发行版中本就不可解析，清单已剔除，引用它们的组件走可选依赖路径
4. **启动时间收益数字待 Linux 测试机**：Windows 桌面机双峰噪声（同配置漂移 4.7 倍）无法分辨启动差异（A/B/A 实测记录见 PLAN.md）；结构收益（安装面 -37%）已实锤
5. **二期路线**：featureManager 域元素（替代 -D 载体）、运行时 habitat extender（启动后加特性）、auto-feature（按部署内容自动补特性）、RunLevel PostStartup 延迟实例化

## 10. 里程碑记录

| 里程碑 | 内容 | 结果 |
|---|---|---|
| M0 | 基线与工具链 | 出厂 10783ms / 调优 5791ms / Tomcat 对照 2025ms（跨窗口数字仅参考） |
| M1 | 内核闭包普查 | 135 jar（口径=nucleus packager + wiring）；最小核域启动 |
| M2 | 解析器 MVP | FeatureResolver + 挂载；无声明回归通过 |
| M3 | 三验收门 | 内核域 ✓ / web 部署 200 ✓ / 无声明回归 344 ✓ |

排障链与工具坑的完整记录：`.scratch/feature-resolution/PLAN.md` 与 `runs/m1-census/census.md`。
