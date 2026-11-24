# ADR 0001: 启动架构采用特性化解析（Liberty 式），OSGi 长期保留

- 状态: 已接受
- 日期: 2026-11-20
- 分支: architecture-optimization

## 背景

Payara（GlassFish 血统）启动时把发行版 modules/ 全目录 373 个 bundle 全部安装（`glassfish.osgi.auto.install`），仅 24 个被 OSGi 启动（16 core @level1 + 8 autostart @level2）；HK2 habitat 在安装之后一次性聚合全部已安装模块的 hk2-locator 描述符。功能面因此在打包时固定，域级无法缩小；启动成本与目录大小成比例，而非与声明功能成比例。

实测对照（develop 启动优化专项，2026-10）：Felix 模式调优后热启动 2374ms，Static 模式（`GlassFish_Platform=Static`，完全绕过 OSGi）1848ms——整个 OSGi 层 ≈0.5s。两条路已证伪：`Bundle-ActivationPolicy: lazy` 铺开（模块本就不被 start，且多数无 Activator）；`org.glassfish.additionalOSGiBundlesToStart`（死旋钮，仓库与发行版 jar 二进制双重验证）。

Open Liberty 证明 OSGi-native 可以快：极小内核 + 特性清单 + 启动时解析闭包，只装载被解析的 bundle，启动成本 ∝ 声明特性。

## 决策

1. **OSGi 长期保留**。Static 模式为正式备选路线（其 1848ms 为对照锚）。翻转条件：上游 merge 策略变化，或特性化解析后启动收益不达预期。
2. **启动架构改为特性化解析**：构建期从既有 featureset/packager 元数据自动生成 feature manifests（bundle 集 + 特性级依赖显式声明）；启动期在安装 bundle 之前（`OSGiGlassFishRuntimeBuilder.build()` Step1 之前）用 MiniXmlParser 直读域声明，自研解析器计算传递闭包，重写 `glassfish.osgi.auto.install/auto.start` 交既有 BundleProvisioner 执行；habitat 随安装集自然收缩，不做运行时扩展。
3. **一期 fail-fast**：部署依赖未声明特性的应用以明确错误失败并提示补声明。auto-feature（按部署内容补特性）为二期。
4. **自研解析器**：不采用 OSGi Subsystem 规范实现（Apache Aries subsystems 停滞）；manifest 头格式借其风格、不绑规范。
5. **声明载体一期用系统属性** `payara.feature.<name>=true`（零 schema 改动、无 HK2 依赖可直读），二期升级为 domain.xml featureManager 元素。

## 备选与否定理由

- **Static 为终局**：收益 ~0.5s + 复杂度消失，代价是模块隔离失效、与 AppCDS 不兼容（`-XX:ArchiveClassesAtExit` 组合挂死）、偏离上游。保留为备选。
- **部署期按需 provision**（GlassFish 3 的 OBR 未竟之路，胚胎机制 MinimalBundleProvisioner/OBR 仍在）：habitat 运行时突变脆弱，上游已放弃；特性级 fail-fast 替代。
- **手写 feature manifests**：与 featureset/packager 双份事实源漂移。构建期自动生成。
- **bundle 级闭包推导**（从 Import-Package/Require-Bundle 算特性依赖）：脆弱；特性级依赖显式声明，bundle 级交给 OSGi resolver（resolver 失败 = 清单错误信号）。

## 后果

- 正面：启动功能面域级可变；启动成本 ∝ 声明特性；habitat 面/攻击面/内存随解析集收缩；为 CRaC checkpoint 体积缩小铺路。
- 代价/风险：内核闭包必须静态算清（存活服务 @Inject 指向被裁模块会在实例化时失败）；裁剪集 = domain.xml 可解析元素集，需域模板配套或容忍性验证（MVP 必验项）；解析器与 manifest 生成器是新增维护面。
- 验收（MVP）：① 最小核域（仅内核闭包）可启动、asadmin 可达；② +web 特性可部署 servlet 应用；③ 特性全声明时行为与现状完全一致（回归红线）。
