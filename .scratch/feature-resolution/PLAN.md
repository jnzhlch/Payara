# Payara 特性化启动（Feature Resolution）实施方案

> 2026-11-20 定稿 · 分支 architecture-optimization · 决策依据见 docs/adr/0001 与 GLOSSARY.md
> 目标不变式：启动功能面域级可变；启动成本 ∝ 声明特性；特性全声明 = 现状行为。

## 架构

```
构建期:  featureset/packager pom（现有"功能→jar"模型）
              ↓ manifest 生成器（新，构建期脚本/Maven 插件）
         feature manifests（每特性一文件：bundle 集 + 特性级依赖）
启动期:  domain.xml 系统属性 payara.feature.*（一期载体）
              ↓ MiniXmlParser 直读（habitat 建立前，无 HK2 依赖）
              ↓ 自研解析器：传递闭包 = 声明特性 ∪ 依赖特性 → bundle 并集 ∪ 内核闭包
              ↓ 重写 glassfish.osgi.auto.install / auto.start
         OSGiGlassFishRuntimeBuilder.build() Step1 既有 BundleProvisioner
              ↓
         osgi-adapter 按安装集聚 habitat（机制不变，面自然收缩）
```

挂载点：`OSGiGlassFishRuntimeBuilder.build()` Step1 之前（nucleus/core/bootstrap/.../OSGiGlassFishRuntimeBuilder.java:134-141）。

## 阶段

### M0 测量基础
- 重建 bench.sh（asadmin 口径起步，payara-web 载体，Tomcat 10.1 对照锚）；direct 启动参数集重建后补
- 构建 payara-web：`mvn -DskipTests clean package -pl appserver/distributions/payara-web -am`（注意：QuickBuild profile 只构建 Full 发行包，不含 payara-web）
- 外部对照锚：Tomcat 10.1.61 @ `D:\server\apache-tomcat-10.1.61`（用户确认可运行；develop 专项参照 974ms 同版本）
- 双基线：出厂配置 + 配置最优基线（Hazelcast disabled、去 flashlight-agent、裁 autostart gogo 系 4 bundle；AppCDS/direct 排除在外）
- 验证：两基线数字冻结进 manifest

**M0 基线（2026-11-23 08:32-08:55 同窗口冻结，payara-web @ 39eb263f89 构建，JDK 21.0.12.1）**

| 基线 | 热启动中位 | 冷启动 | run 目录 |
|---|---|---|---|
| 出厂配置 | **10783ms** | 17565ms | runs/base-factory |
| 配置最优（Hazelcast off + 去 flashlight + 裁 gogo 系 4 bundle） | **5791ms** | 5476ms | runs/base-tuned |
| Tomcat 10.1.61（外部对照） | **2025ms** | 1657ms | runs/20261123-085232-tomcat |

- 配置层收益 -4992ms（-46%）；对 Tomcat 差距 2.9×
- 本窗口噪声偏大（Tomcat 第 5 轮 7484ms 离群；develop 期参照 974ms）——中位数仍可用，窗口内可比；后续 A/B 实验建议避开系统负载高峰
- zip sha256 见各 run 目录 manifest.json

### M1 内核闭包普查（纯配置实验，零代码）
- 域级 osgi.properties 覆盖 auto.install 到 16 core bundle 起步，迭代补齐到能启动
- 逐轮记录：缺哪个（启动失败/服务缺失）→ 补哪个
- 产出：内核闭包清单（bundle 级）+ DomainXml 对无 bean 元素的容忍性结论
- **必验项**：容忍性不行则立项「域模板配套」

### M2 解析器 MVP（kernel 层代码，架构主线已批）
- manifest 生成器：从 featureset/packager pom 生成 feature manifests
- 解析器：闭包计算（特性级依赖显式声明）+ fail-fast（引用未声明特性依赖的清单错误）
- 挂载：OSGiGlassFishRuntimeBuilder 读 payara.feature.* → 重写 install/start 属性
- 验证：单变量、热启动中位 ≥5 轮同天同窗口 A/B

### M3 MVP 验收
1. 最小核域（内核闭包 only）启动、domain.xml 可读、asadmin 可达
2. +web 特性 → 部署 servlet 应用成功
3. 特性全声明 → 行为与现状完全一致（e2e 回归红线）

### 二期（按优先级）
featureManager 域元素（config-api bean）→ 运行时 habitat extender（HK2Populator 接线，osgi-adapter 缺的钩子）→ auto-feature（部署内容探测）→ RunLevel PostStartup 延迟实例化 → Static 对照复测

## 纪律

- 每实验单变量；M0 前的一切结论只信同天同窗口 A/B
- 变更层级：配置 → manifest/打包 → kernel 源码；M2 起的 kernel 改动属架构主线（ADR 0001 已批），其他 kernel 改动仍须证据驱动逐项评审
- 实验记录落本目录 run 子目录；有效结论沉淀 ADR/GLOSSARY

## M2 进展（2026-11-23）
- ✅ `FeatureResolver.java`（nucleus/core/bootstrap/osgi/）：双通道声明（域级 osgi.properties 键 + 服务器 JVM -D）、特性清单（domain/install 的 config/features/*.properties）、内核闭包资源（META-INF/payara/kernel-bundles.txt，可被 installRoot/config/kernel-bundles.txt 覆盖）、fail-fast（未知特性/缺失 bundle）、真实 URI 输出（substVars 已跑完，不能再写 ${} 占位符）
- ✅ 挂载：OSGiGlassFishRuntimeBuilder.build() 在 BUILDER_NAME 设置后调用 applyTo(props)
- ✅ 端到端验证：声明 census 特性（空 bundle 集）→ 安装 123=122 内核+system（默认全量被收缩）；Grizzly 正常
- ✅ 回归：无声明 → 344 = payara-web 全量默认，行为与现状一致（ADR 红线）
- 待办：manifest 生成器（featureset/packager pom → 特性清单）；web 特性清单内容（boot 迭代）；本地仓库已 install 核心上游（bootstrap 可 -o 快速构建）

## M3-② 进展（2026-11-23 晚）
- ✅ gen-features.py：featureset(type=pom)/packager(type=zip) 树遍历 → 26 份 per-packager 清单（glassfish-web=38 jar 等）
- ✅ web 特性 = glassfish-web packager 38 jar + feature.dependencies=cdi；cdi 特性 = glassfish-jcdi 11 jar + wiring 闭包追加 27（deployment-javaee-core、ejb-container、JSF、jersey、security-ee、orb 系、org.osgi.util.promise/function/component——weld 链路拉动 EJB 容器是重要特性建模事实）
- ✅ 内核闭包补 3 个 OSGi 组合包 jar（org.osgi.service.component/promise/function——scr 依赖链），kernel-bundles.txt 重建部署
- ✅ 声明 census+web+cdi 三特性启动：190 bundle 安装、Grizzly 全监听、零 wiring 错误（scr 链已解）
- ⏳ 部署验证带竞态：上轮部署在服务器 PostStartup 未完成时提交并被提前杀进程——下轮需「部署后轮询 /hello 直至成功或超时」的重测（预计一次通过）
- ⚠️ 已知良性问题：4848 GUI 访问触发 __admingui 自动部署 → ResolveError console-common——GUI 控制台需 glassfish-gui 特性（独立特性，CLI 用法不受影响）

## M3-② 阻塞点记录（2026-11-24，含同日更正）

### 已证实的事实
- deploy 经 asadmin 返回 `HTTP connection failed with code 202`；`POST /management/domain/deploy` 的 202 响应体是「Payara Server Administration Console」HTML 页
- **`rest-service.jar`（管理 REST 服务端）不在内核闭包，也不在 web/cdi 特性清单中**——内核闭包与三份清单 grep 命中数均为 0
- `asadmin version` 成功不代表管理协议可用（本地命令，不访问服务器）
- 控制台安装线程 `InstallerThread` 抛 `ExceptionInInitializerError`/`NoProviderFoundException`（`WriteableView.<clinit>` 找不到 Jakarta Validation provider）；hibernate-validator.jar 在已安装 bundle 中
- 域 `lib/` 放一份 hibernate-validator.jar 后 **NoProviderFound 依旧**——该实验未能修复，实验副本已删除

### 当前工作假设（证据支持，**尚未实验验证**）
- 202 的主因是管理 REST 服务缺失：`/management/*` 请求落到 admin listener 的控制台适配器（AdminConsoleAdapter.service 在 `isRestStarted` 为假时的路径），而非 REST 分发
- 已建 `admin-rest` 特性（rest-service.jar + gf-restadmin-connector.jar，依赖 jersey，wiring 追加 9 个）并在 domain.xml 声明；**待启动验证**

### 已被推翻的旧结论（勿再引用）
- ~~「hibernate-validator 经 TCCL 不可见 → InstallerThread 死 → 卡住全部管理请求」是 202 的根因~~ —— lib 实验证伪；InstallerThread 异常是控制台安装路径上的独立问题
- ~~「360 winsock 过滤层吞 localhost 连接」~~ —— 仅观察到一次 `list-applications` 报 `No buffer space available`，成因未查证（当时 REST 服务本就不在安装集）

### 对 M1 验收的更正
- M1 的「ADMIN=YES」只证明 **4848 端口可达**，未验证任何管理命令可执行——内核闭包不含管理 REST，最小核域**无法经 asadmin 远程管理**
- 架构含义：管理 REST 是可裁剪特性，需显式声明（无 gui 的 headless 域也必须声明 admin-rest）

### 下步（按序）
1. 启动 web+cdi+admin-rest 声明域 → `GET /management/domain` 非 202 → asadmin deploy hello.war → 轮询 `/hello` 至 200
2. InstallerThread 的 NoProviderFound 单独定位（仅在声明 gui 特性时才相关）
3. autodeploy 未拾取 WAR 单独查（域 autodeploy 目录里还留有实验用的 hello.war，下次启动前需移除，否则会污染部署验证）

### 环境
- `install/domains/bench/lib/` 是 create-domain 自带的域目录（含 databases/applibs/classes/warlibs），**不可删除**

## M3-② ✅ 完成（2026-11-24 上午）
- **端到端打通**：声明 web（+依赖 cdi）+ admin-rest → 218 bundle 启动 8.1s 零未解析零 SEVERE → `asadmin deploy` 成功 → **`GET /hello/hello` 返回 200 正文 `hello-feature-web`** → asadmin stop-domain 正常停机
- 最终特性清单：web = glassfish-web packager 36 jar（移除 jsf-connector/jersey-mvc-connector——属 JSF/MVC 特性）+ webservices-api-osgi（jakarta.xml.soap 提供者）；cdi = glassfish-jcdi 11 + wiring 25 − bcel（dist 无 commons-io，与全量同样不可解析，引用方为可选依赖）；admin-rest = nucleus-management(3) + nucleus-jersey(23) + wiring 4
- 内核闭包口径修正并终值 **135 jar**（= 产品 nucleus packager 31 + 原 wiring 闭包 − ldapbp）；deployment-admin/deployment-autodeploy 属 nucleus 基座（部署命令与自动部署服务），不含它们则 deploy 命令不存在、autodeploy 不工作
- **kernel 源码改动 1 处（hk2-config/WriteableView）**：Bean Validation 提供者显式解析（HK2ValidationProviderResolver），不再依赖 TCCL——Grizzly 工作线程 TCCL 是 bundle 加载器，ServiceLoader 找不到提供者；全量发行版靠初始化顺序侥幸工作，特性裁剪后必然暴露。参照 kernel 既有 HibernateValidationProviderResolver
- 修通的完整排障链：202(REST 缺失)→REST 500(Validation TCCL)→deploy not found(部署命令属 nucleus)→WebContainer 起不来(weld fragment+closure 缺口)→部署成功
- 验证工具沉淀：bin/verify-deploy.sh（本轮日志基线、双通道探测、127.0.0.1 固定、完整部署输出、REST+kernel 双停机通道）；work/probe 离线解析探针（忠实复刻 build() 顺序：install→逐个 start→framework.start→清点 INSTALLED）

## M3 全部门完成（2026-11-24）
- M3-① 内核域启动 ✅（更正：管理命令需 admin-rest 特性）
- M3-② web 特性部署 ✅（见上节）
- M3-③ 无声明回归 ✅：移除全部声明 → 344 bundle 全量默认、启动干净、零 SEVERE（补丁版 hk2-config 在默认路径行为不变）
- 测量（跨窗口，仅参考不作结论）：web 域（218, Hazelcast off + flashlight 移除）热启动中位 7586ms，轮间噪声 3993~11174ms；同窗口 A/B（调优全量 vs 调优 web 域）留待正式测量
- 域当前状态：声明 web+admin-rest（Hazelcast off、flashlight 移除），可直接用于后续实验

## 同窗口 A/B/A 测量（2026-11-24 下午）——噪声压倒信号，时序结论移交 Linux 测试机
- 设计：A=调优全量 344（base-tuned），B=web 特性域 218（m1-census），A→B→A；两侧运行时 jar 与 autostart 已对齐（补丁版 glassfish/hk2-config 同步至 A；gogo trim 因 bench tune 的 payara7 路径 bug 从未生效，两边 autostart 自然一致）
- 原始数据：A1(6轮) 中位 1537ms（热轮 228~11733）；B(10轮) 中位 5489ms（4004~14143，冷轮 DNF 一次）；A2(10轮) 中位 7248ms（4734~15657）
- **同配置 A1 vs A2 漂移 4.7 倍 > A/B 差异**——本机双峰噪声（快轮 ~300ms / 慢轮 ~10s+）下任何启动差异不可分辨
- 站得住：功能等价（M3-②③）+ 结构收益（安装面 344→218，-37%）
- **时序收益结论移交 Linux 被测机**（perf-plan 原设计；Windows 桌面机只承担功能验证）
- 环境备注：B 轮前清过一次残留 JVM；bench stop-domain 偶发失败（localhost 连接问题）——bench.sh 停机建议改 127.0.0.1（待改）

## dist 级特性清单 ✅（2026-11-24 下午）
- 新增 bin/emit-dist-features.py：合成 dist 清单（验证版优先：web/cdi/admin-rest 用 boot 验证内容并注入依赖边 web→cdi、admin-rest→jersey；其余用 packager 生成草稿并标注「未 boot 验证」；语义命名映射 30 项；剔除被 admin-rest 合并的 nucleus-management/jersey 源清单）
- 产出 .scratch/feature-resolution/dist-features/ 27 份 → 已发布到测试域 glassfish/config/features/（发行版级）
- 端到端验证：移除域级清单后，解析器从发行版级读取 → 声明 web+admin-rest → 221 bundle 启动、管理 REST 200、hello 应用（持久化登记）自动加载服务（/hello 301 重定向为 context root 正常行为）
- 语义命名映射表与依赖边在 emit-dist-features.py 内维护；新依赖边必须先过部署验证
