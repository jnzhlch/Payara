# GLOSSARY — 特性化启动（architecture-optimization）

本分支领域词汇表。只收领域概念，不收实现细节。

## 特性 (Feature)
对外承诺的功能单元（如 servlet、ejb-lite、jms），是启动功能面的声明与计数单位。一个特性由一份特性清单描述其模块组成与特性依赖。
_Avoid_: 模块组、功能包、profile（见 特性档案）

## 特性清单 (Feature Manifest)
构建期生成的元数据文件：声明某特性的 bundle 组成与所依赖的其他特性。存在于发行版中，不进运行时，直到被解析。
_Avoid_: feature.xml、subsystem（未采用 OSGi Subsystem 规范）

## 特性声明 (Feature Declaration)
域对所需特性的声明列表。一期载体：系统属性 `payara.feature.<name>=true`；二期载体：domain.xml featureManager 元素。

## 特性解析 (Feature Resolution)
启动时从特性声明计算传递闭包的过程，产出解析集。解析发生在安装任何 bundle 之前；特性级依赖在清单中显式声明，bundle 级交给 OSGi resolver。
_Avoid_: 依赖推导（bundle 级推导已否决）、按需 provision（部署期路线已否决）

## 解析集 (Resolved Set)
特性闭包对应的 bundle 安装/启动集合——该域启动功能面在 OSGi 层的投影。安装集与激活集均由它决定。

## 内核闭包 (Kernel Closure)
任何特性组合下都必须安装的最小 bundle 集：HK2 内核 16 bundle + domain.xml 解析链 + InitRunLevel 服务闭包。

## 启动功能面 (Boot Feature Surface)
启动期装载的功能集合，分三层观测：安装集（bundle install）、激活集（bundle start / RunLevel 服务定义）、实例化集（RunLevel 服务实例）。本架构优化的目标不变式是缩小它；毫秒数是它的观测投影。
_Avoid_: 启动范围、加载范围（三义模糊）

## Fail-fast
部署依赖了未声明特性的应用时，以明确错误失败并提示补声明。一期的未声明需求行为；二期的 auto-feature（按部署内容自动补特性）是它的反面。

## 域模板配套 (Profile-Matched Domain Template)
域模板中的配置元素集与该域声明的特性一致——裁剪模块集等于裁剪 domain.xml 可解析元素集，二者必须同步，否则域解析失败。

## 特性档案 (Profile)
特性的预设集合（如 web-default）。一期不做独立档案机制，声明逐特性列出。

## 配置最优基线 (Tuned-Config Baseline)
复现既有配置层优化（Hazelcast disabled、去 flashlight-agent、裁 autostart 调试 bundle）后的基线，作为一切增量实验的归因参照。AppCDS 与 direct 启动排除在外（分别与类加载纠缠、属口径问题）。
