# ADR 0002: 运行时特性装配——add-only、命令式触发、解析先行

- 状态: 已接受（设计定稿；实现细节待机制核查收尾）
- 日期: 2026-11-24
- 关联: [ADR 0001](0001-feature-resolution-startup-architecture.md) · [使用手册](../feature-resolution.md)

## 背景

一期特性化启动只覆盖 boot 期装配：声明在 habitat 建立前由 FeatureResolver 读取并改写安装清单，运行中无法增加特性。运行时增加特性需要补上 habitat 一次性聚合假设的缺口——bundle 在框架启动后安装时，其 hk2-locator 描述符不会进入已有 habitat（osgi-adapter 无此钩子，`HK2Populator.populate` 公开 API 是补装的正路）。

Open Liberty 的对应机制：server.xml 声明式触发（文件监视 + Config Admin）、运行时 add/remove 皆支持（remove 带 quiesce 有序静默）、解析先行（新特性集对 bundle 缓存整体解析，失败则保持旧特性集并响亮报错，从不部分安装）。

## 决策

1. **触发：显式命令 `payara-add-feature <name>...`（`--dry-run` 只跑预检）**。命令内先装配、成功后再写配置——失败则配置不变，无中间态。声明式触发（ConfigListener 监听 feature-manager 元素变更）为三期方向：需要「配置提交 ↔ 装配结果」的一致性协议（UnprocessedChangeEvents 重试语义），本期待建。
2. **变更语义：运行时 add-only**。移除特性一律「改声明 + 重启生效」——运行中卸载被引用 bundle 需要服务静默（quiesce）机制，不做（见下方偏离声明）。
3. **解析先行预检**：装配前，新特性并集（内核闭包 ∪ 已装配 ∪ 新特性闭包）先在临时 Felix 上按真实顺序解析预检（复用离线探针）；预检通过才对运行框架装配增量集合；预检失败零接触并报缺失清单。
4. **失败即回滚**：预检通过但运行框架装配中途失败（预检后环境变化）→ 卸载本批已装 bundle，配置不落盘。
5. **持久化写回**：装配成功后 `ConfigSupport.apply` 更新 `servers.server.server.feature-manager.features`——运行时状态与声明恒等，重启一致。
6. **framework 通道**：kernel 侧装配服务经 `BundleReference.class.cast(getClass().getClassLoader()).getBundle().getBundleContext()` 获取（kernel.jar 在 OSGi 模式由 bundle loader 加载；仓库先例 OSGiDeployer/OSGiShellCommand/EmbeddedOSGiGlassFishRuntimeBuilder）。~~boot 时自注册 BundleContext 为 OSGi 服务~~（已否决：`NonHK2ServiceFilter` 会排除系统 bundle 自注册的服务，桥接不进 habitat）。static 模式判型失败即报「特性装配仅 OSGi 模式支持」。
7. **服务活化语义**：habitat 补装**已隐式存在**——bundle RESOLVED 事件 → `OSGiModulesRegistryImpl.bundleChanged` → `AbstractModulesRegistryImpl.add()` 遍历已登记 habitat 逐个 `parseInhabitants`（hk2-core:401-432），无需自写 extender。新描述符按 HK2 现行为懒实例化（level ≤ 20 的 getService 立即创建）；**严禁 `proceedTo(10)`**——会先 DOWN 销毁 11~20 层服务。P2-3 验收 = bundle ACTIVE + 描述符在册 + 相关管理命令可执行。
8. **新特性清单的 wiring 闭包在创作期烘焙**（wireclosure.py 对「当前域并集 ∪ 新特性」离线计算），运行时预检仅兜底校验。

## 与 Liberty 的有意偏离（避免被误读为遗漏）

| 维度 | Liberty | 本方案 | 偏离理由 |
|---|---|---|---|
| 移除特性 | 运行时可移除，带 quiesce 静默机制 | add-only，移除重启生效 | quiesce 是服务停止级机制，工程量年为单位；单机 web 服务形态下收益不成比例 |
| 触发 | 声明式（server.xml 监视 + Config Admin） | 命令式（一期） | config 先提交后装配的一致性协议未建；命令式在「装配成功才落盘」下语义最干净。三期补声明式 |
| 服务活化 | OSGi DS 原生 | habitat 补装 + HK2 懒实例化 | Payara 特有的 HK2/OSGi 双层结构，Liberty 无此层，无先例可抄 |

与 Liberty 等价的部分：解析先行预检（精神一致，工具为临时 Felix 探针）、持久化语义（声明即持久）、幂等重放安全。

## 后果

- 正面：单机服务域可运行中扩展能力（如运行中加 jpa），声明与运行时状态恒等，预检保证装配确定性
- 代价/风险：预检与真实框架的解析环境差异需要预检后仍保留回滚兜底（两者已并存）；`add-only` 意味着收缩仍需重启；命令与声明式两条触发路径的并存需要在三期统一
- 验收：web+cdi+admin-rest 域运行中 `payara-add-feature jpa` → jpa bundle ACTIVE + habitat 描述符在册 + 重启后 jpa 仍装配
