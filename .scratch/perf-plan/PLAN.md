# Payara 服务器性能优化方案(运行时 / 内存 / 部署速度)

> 2026-11-04 · 基于 Payara 7.2026.10-SNAPSHOT fork · 基线发行版:`appserver/distributions/payara-web`(Web Profile)
> 状态:方案完备(两轮评审修订),**暂缓执行**——按 §5 顺序实施的启动等待明确指令;目标数字在基线测量后冻结。

## 0. 决策记录(评审结论的固化)

| 决策点 | 结论 |
|---|---|
| 优化维度 | 运行时吞吐/延迟、内存/资源、部署速度;**启动优化排除,另行专项**;管理面响应后置 |
| 基线工件 | **必须来自源码构建**:全量构建 fork,从生成目录取最新 `payara-web.zip`;每次源码轮次重建并刷新 sha256 指纹,禁止使用陈旧产物 |
| 优化主体 | Payara 服务器本身;基线发行版 Web Profile |
| 生产形态 | Linux 生产机、单实例、从零搭建、无 HA/集群、无 Payara monitoring 需求 |
| 改动层级 | 配置/JVM 参数 + 发行版裁剪允许;fork 源码改动为**最后手段**(须火焰图证据驱动) |
| 成功标准 | 基线测量后按**分项 × 维度**定**相对目标**并冻结;每维度回归红线 |
| 负载 | 纯合成:样例应用(REST + JPA CRUD + 少量 JSP),独立进程 PostgreSQL;**测试按 Jakarta EE 功能分项**(Servlet / JSP / JAX-RS / CDI / EJB Lite / JPA·JTA / Bean Validation / WebSocket),每分项可含多个测试用例,另有跨功能综合负载(8:2) |
| 压测端 | 两台独立机器、千兆同局域网、wrk2 开放模型;WebSocket 分项用专用 WS harness |
| 数据收集 | **不使用 Payara 自身监控采集**;常驻层仅 GC 日志 + OS 采样;async-profiler/perf 火焰图四视图用于分析;JFR 仅兜底 |
| 浸泡 | 每阶段验收门挂 30min 标准浸泡;内存阶段另做 60min+ 泄漏专项 |
| 数据落地 | 每轮一个 run 目录 + manifest + A/B 对比脚本 |
| 外部参照 | 同负载 Tomcat 10.1,仅作参考坐标,不作验收门 |
| 冲突裁决 | 运行时吞吐/延迟优先;内存优化不得抬高 GC 停顿;任何优化不得以稳态吞吐为代价 |

## 1. 范围与排除

**包含**:服务器侧配置、JVM 参数、发行版构成、必要的 fork 源码改动(证据驱动)对三个维度的影响。

**排除**:
- 启动时间优化(另行专项成文;本方案任何改动只要求不恶化启动,不承诺改善)
- 管理面(控制台/REST API)响应速度
- 业务应用本身的代码调优(样例应用仅为测量载体)
- 真实业务应用验证(负载为纯合成)

## 2. 环境与机器需求清单

| 角色 | 规格 | 用途 |
|---|---|---|
| 被测机 | Linux x86_64(主流 LTS 发行版),≥8 核,≥16GB RAM,SSD | 运行 Payara + PostgreSQL(独立进程) |
| 压测机 | Linux x86_64,≥4 核,≥8GB RAM | 运行 wrk2 + 采集脚本 |

两台机器同局域网,千兆及以上。

**被测机软件要求**:
- JDK 21 LTS(建议 Temurin;项目要求 JDK 21)
- Maven 3.9+ 与 fork 源码树(源码构建用;构建在被测机执行,与运行时 JDK 同源)
- async-profiler 可运行或 `perf` 可用(`kernel.perf_event_paranoid ≤ 1` 或 root 权限)——火焰图采集的硬前提,机器到位后第一件事验证
- PostgreSQL(版本 ≥ 14),独立进程,装在被测机
- 压测机与被测机时钟对齐(NTP);防火墙放通 8080

**压测机软件要求**:
- wrk2(源码编译,需 OpenSSL 开发头文件)
- WS harness 及其运行时(WebSocket 分项用)

> 口径说明:PostgreSQL 与 Payara 同机,理由:(a) 压测端保持干净,不污染延迟与吞吐测量;(b) 单实例生产形态下应用与 DB 同机是常见部署。若最终生产为独立 DB 机,加第三台再校准口径即可,不影响方案结构。

**环境固定项**(记录进每轮 manifest):CPU 频率 governor、透明大页状态、swap 配置、内核参数基线。

## 3. 负载与基准设计

### 3.1 样例应用

一个可移植 WAR(不使用 Payara 专有 API),**按 Jakarta EE(Web Profile)功能分组暴露端点**:

- Servlet:同步简单端点、async 端点
- JSP:页面渲染(同时服务部署速度维度的 JSP 编译路径)
- JAX-RS:JSON 端点(读/写,负载 1-4KB)
- CDI:注入 + 拦截器密集的调用端点
- EJB Lite:无状态 bean 调用端点
- JPA + JTA:事务性 CRUD(PostgreSQL),读:写 = 8:2
- Bean Validation:校验密集端点
- WebSocket:回声端点

压测脚本随样例应用一并交付(wrk2 Lua,**每个功能分项一组用例脚本**;WebSocket 分项另交付专用 WS harness)。

### 3.2 分项测试原则(按 Jakarta EE 功能)

分项 = Jakarta EE(Web Profile)服务器提供的功能。**每分项独立场景、独立 run、独立基线数字与目标**,禁止只测混合负载;**每分项下测试用例可以多个**(新用例随时加入基线表,不改变方案结构):

| 功能分项 | 覆盖路径 | 初始测试用例 |
|---|---|---|
| Servlet | HTTP/Grizzly、过滤器链 | 同步简单请求;async 请求 |
| JSP | 视图渲染 | 页面渲染(首击触发编译属冷路径,分位取预热后;编译耗时归阶段 3 口径) |
| JAX-RS | REST 管线、JSON-B | JSON 读;JSON 写 |
| CDI | 上下文/拦截器链 | 注入+拦截器密集调用 |
| EJB Lite | EJB 容器、池化 | 无状态 bean 调用 |
| JPA + JTA | 持久化、事务、JDBC 连接池 | 事务性 CRUD 读;事务性 CRUD 写 |
| Bean Validation | 校验管线 | 校验密集端点 |
| WebSocket | WS 升级、帧处理 | 回声消息 |
| 综合 | 跨功能混合(8:2) | 混合画像(浸泡/泄漏/总体) |

火焰图采集与分项 run 对齐——每个分项单独抓,瓶颈归属到具体功能路径。

### 3.3 压测模型

**模型纪律:主口径一律 wrk2 恒速率开放模型,不与闭合并发模型混用**;拐点由 RPS 阶梯向上扫描定义——p99 延迟爆炸(如 ×3 跳变)或出现首个错误的码率即拐点。

**档位纪律:每分项先跑 2-3 档短容量探测,定出该分项自己的 RPS 阶梯再跑正式档**;各功能分项容量差数量级,禁止共用一套档位。示例档位(500/1000/2000/4000)仅适用于综合负载。

| 模式 | 方法 | 适用分项 |
|---|---|---|
| **RPS 阶梯(主)** | wrk2 固定 RPS 阶梯(每分项探测定档),每档 3min 取后 2min | 全部功能分项 + 综合 |
| **标准浸泡** | 固定中等 RPS 持续 30min | 综合 |
| **泄漏专项** | 中等负载 60min+,每 10min 强制 GC 并记录 heap | 综合(仅内存阶段) |

**WebSocket 分项例外**:wrk/wrk2 均为纯 HTTP 工具,WS 分项用专用 WS 客户端 harness(连接建立速率 + 回声 RTT),独立口径,不与 wrk2 数字混表。

产出(**每分项独立**):p50/p90/p99/p999 延迟曲线,拐点位置;**验收档位错误率必须为 0**(拐点探测扫到饱和区允许出错,首个错误即拐点证据之一)。

### 3.4 瓶颈归属

压测全程观察 PostgreSQL 侧(`pg_stat_statements`、`pg_stat_activity` 等待事件)——DB 瓶颈必须与服务器瓶颈区分,避免把数据库饱和误判为 Payara 问题。DB 观测仅用于归属判定,不属于验收口径。

### 3.5 数据播种与预热纪律

- **播种**:CRUD 表预播种生产量级行数(基线时定,建议 ≥10 万行)并建索引;空表基准全走缓存,数字不可信
- **预热**:每个正式 run 先跑预热段(连接池建立、首次查询、JIT 热身),计量取预热后;JSP 首击编译按 §3.2 归冷路径
- **写用例**:payload 唯一(避免热点行与重复键),由脚本生成
- **数据量控制**:浸泡/泄漏专项期间表持续增长,用清理任务或环形主键覆盖,防止后期分位被数据量漂移污染

## 4. 测量口径与数据收集

### 4.1 常驻采集层(所有验收 run 常开,开销可忽略)

| 项 | 内容 |
|---|---|
| GC 日志 | `-Xlog:gc*:file=<run>/gclog/gc.log` |
| OS 采样 | sar/pidstat 1s 间隔:RSS、CPU(整机+进程)、IO |
| 压测输出 | wrk2 / WS harness 每档原始输出 |

**口径纪律:验收数字一律只在"常驻层开启、Payara monitoring 关闭"下测。**

### 4.2 火焰图分析层(独立分析 run,不混入验收 run)

async-profiler 四视图,每视图短窗口(30-60s)采集:

| 视图 | 回答的问题 | 服务维度 |
|---|---|---|
| CPU | 时间花在哪个调用栈 | 运行时吞吐 |
| alloc | 谁在分配、分配压力在哪 | 内存 |
| lock | 锁竞争点在哪 | 运行时吞吐/延迟 |
| wall | 等待时间去哪(IO/阻塞) | 运行时延迟、部署速度 |

产出 folded 文本 + 火焰图 HTML 存入 run 目录 `flame/`。**JFR 仅在 async-profiler/perf 在目标机不可用时兜底**(机器到位后第一时间验证,见 §2)。

### 4.3 Payara 自身监控的处理

不用于任何数据采集。可选做一次"monitoring-service 开/关"A/B,量化其对吞吐/延迟的影响,仅为生产落地决策提供数据,不进入任何验收门。

### 4.4 run 目录纪律

```
runs/
  <时间戳>_<label>/
    run-manifest.json    # 见附录
    load/                # 每档原始输出(wrk2 或 WS harness)
    gclog/
    os/                  # sar/pidstat/free/mpstat CSV
    flame/               # 仅分析轮次:四视图 folded + HTML
    notes.md
```

每个 run 结束必须能脱离当时上下文被理解:manifest 完整记录参数集差异、构建指纹、机器状态。

## 5. 阶段计划

**实验成本两级口径**:**全矩阵**(全部功能分项 RPS 阶梯 + 30min 浸泡 + 部署口径,一轮约 4-5 小时)只在基线与各阶段收口时跑;**短口径**(仅相关分项 + 3 档 + 无浸泡,约 20 分钟)用于日常单变量实验——单变量实验一律短口径,阶段收口才全矩阵复测。

### 阶段 0:基线(先于一切优化)

0. **源码构建**:在被测机上全量构建 fork(`mvn -DskipTests clean package -PQuickBuild`),从生成目录 `appserver/distributions/payara-web/target/` 获取最新 `payara-web.zip` 作为基线工件,sha256 记入 manifest。**禁止使用陈旧产物**;此后任何涉及源码的轮次先重建、再取生成目录最新产物、再刷新指纹。
1. 部署基线工件 + 样例应用 + PostgreSQL。**基线为出厂默认配置**——domain.xml / JVM 参数均不预置任何优化(Hazelcast 关闭等都是阶段 1 实验项,不进基线)
2. 验证 async-profiler/perf 可用(不可用则立即调整,不影响其余基线)
3. 跑齐基准:**按功能分项(§3.2)各跑容量探测与 RPS 阶梯**,综合负载跑 30min 浸泡,部署速度口径(冷/热,见阶段 3);Tomcat 参照物在可比分项上同口径跑一轮
4. **产出基线数字表 + 各维度相对目标提案,与你确认后冻结**(冻结前不开始阶段 1)

**基线纪律**:每项实验单变量——**JVM 参数与域配置都是实验变量**,由 bench.sh 注入(JVM 参数文件 / asadmin 域配置补丁):每个 run 至多一项变更,manifest 记录与基线的 diff,run 结束自动回滚到基线配置;A/B 必须同天同窗口;每轮压测前重启 Payara 取干净状态;环境固定项进 manifest。

### 阶段 1:内存 / JVM

| 候选实验项 | 验证方式 |
|---|---|
| GC 选型:G1(默认)/ 分代 ZGC / Parallel × 堆阶梯 2/4/8G | GC 停顿 p99、吞吐、RSS 三轴对比 |
| 堆大小-RSS 关系曲线 | 定生产推荐堆与 `MaxMetaspaceSize` 实测约束 |
| Hazelcast 关闭(单机无 HA 已确认) | 常驻内存、分配速率、GC 压力前后对比 |
| EclipseLink shared-cache-mode(NONE vs ENABLE_SELECTIVE) | heap 占用与行为差异 |
| 线程栈:`-Xss` × http-thread-pool 上限组合 | 线程内存总量 = 线程数 × 栈深 |
| `-XX:+UseStringDeduplication`(G1) | heap 与 RSS 变化 |

验收:RSS/GC 停顿达到冻结目标,且吞吐/p99 红线不破;30min 浸泡通过。

### 阶段 2:运行时吞吐/延迟

方法:先以 CPU/lock/wall 火焰图定位阶段 1 优胜配置下的耗时大头,再按证据从下表选实验项;**大头若指向内核路径,才进入 fork 源码改动评审**。

| 候选实验项 | 层 |
|---|---|
| http-thread-pool / Grizzly worker 尺寸、keep-alive 参数 | 配置 |
| JDBC 池:steady/max-pool-size、statement-cache-size、connection-validation 方式 | 配置 |
| EclipseLink:batch writing、statement cache | 配置 |
| OS/TCP 内核参数(somaxconn、tw_reuse 等)、文件描述符上限 | OS |
| `ReservedCodeCacheSize`、JIT 编译线程数 | JVM |
| 火焰图定位出的内核路径改动 | 源码(最后手段,逐项评审) |

明确**不做**:C1-only JIT(`TieredStopAtLevel=1`)——伤稳态吞吐,归启动专项。

验收:吞吐拐点与 p99@固定 RPS 达冻结目标;内存/GC 红线不破;30min 浸泡通过。

### 阶段 3:部署速度

**口径**:同一份样例 WAR,冷部署(新域首次 deploy)与热重部署(同域 redeploy)的墙钟,≥5 轮取中位。只测 deploy 动作本身,不含服务器启动时长。

方法:先以 wall/CPU 火焰图定位部署链耗时分布(annotation 扫描、DOL 解析、OSGi 环节、JSP 编译),再选候选:

| 已知旋钮 | 备注 |
|---|---|
| `--precompilejsp` 开/关 | 样例含 JSP,此项直接可见 |
| `--force` redeploy vs undeploy+deploy | 口径二选一冻结 |
| archive 展开策略 | 实验 |
| 部署期 schema 校验 | 实验 |

验收:冷/热部署墙钟达冻结目标;运行时与内存红线不破。

## 6. 成功标准与回归红线

**目标**:基线(阶段 0)完成后**按分项 × 维度**提出相对目标(如读分项 p99@RPS -Y%、综合拐点 +X%、稳态 RSS -Z%、部署墙钟 -W%),**与你确认后冻结**,各阶段验收门挂在冻结目标上。

**回归红线**(默认建议值,基线后可调;任何一项改动破门即回退):

| 红线 | 默认值 |
|---|---|
| 验收 run 吞吐 | 相对当前最优 -3% |
| p99 延迟 | +5% |
| GC 停顿 p99 | +10% |
| 30min 浸泡 | 吞吐衰减 >3% 或错误增量 >0 |
| 60min 泄漏专项 | heap-after-GC 每小时增长 >5% 或 RSS 单调增长 |

红线按分项逐一检查(RPS 阶梯按分项跑,拐点随各分项阶梯扫描得出);30min 浸泡与泄漏专项挂在综合负载上。

## 7. 冲突裁决规则

1. **运行时吞吐/延迟优先于其他维度的收益**——冲突时保运行时。
2. **内存优化不得抬高 GC 停顿**(堆缩小的收益不能以停顿换)。
3. **任何优化不得以稳态吞吐为代价**(尤其禁止伤稳态换启动的参数混入本方案)。
4. 三个维度分别验收,但每阶段结束后全维度复测红线。

## 8. 外部参照物

**参照 WAR**:从样例应用同一代码库裁剪出仅含 Servlet / JSP / WebSocket 端点的子集 WAR(完整样例含 CDI / EJB Lite / JPA,Tomcat 无法整体部署),部署到 Tomcat 10.1.x(JDK 21,默认配置),跑一轮与基线相同口径的 RPS 阶梯。

- 作用:外部坐标,防止"内部优化了 40% 但绝对值仍不可接受"的盲区。
- **范围**:仅对比 Tomcat 原生提供的功能分项(Servlet / JSP / WebSocket);CDI、JAX-RS、JPA、EJB Lite 等 Tomcat 不原生提供,不可比。
- 局限(须记录在结果旁注):两容器默认实现不同,此对比**不是**受控 A/B,只是语境参照。

## 9. 风险与已知陷阱

| 风险 | 对策 |
|---|---|
| async-profiler/perf 在目标机不可用(内核权限、容器限制) | 机器到位后**第一时间**验证;兜底 JFR + 火焰图转换 |
| DB 饱和被误判为服务器问题 | `pg_stat_statements`/等待事件全程归属判定(§3.4) |
| 闭合/开放模型混用与 coordinated omission | 主口径固定 wrk2 恒速率,不混用;拐点由 RPS 阶梯扫描定义(§3.3) |
| 观测开销污染验收数字 | 三层划分 + 验收只开常驻层(§4.1) |
| 环境噪声(CPU 调频、后台任务、大页) | manifest 记录固定项;A/B 同天同窗口纪律 |
| 源码改动与上游合并成本 | 源码列为最后手段,须火焰图证据 + 单独评审 |
| 目标数字拍脑袋 | 目标一律基线后提出、冻结前不实施优化 |
| 陈旧构建产物被误测 | 每轮 manifest 记 sha256;源码变更后强制重建,一律从生成目录取最新产物(§5 阶段 0) |

## 10. 附录

### run-manifest.json 字段

```json
{
  "timestamp": "…", "label": "…", "phase": "baseline|mem|rt|deploy",
  "artifact": {"payara-web.zip sha256": "…", "sample-war sha256": "…"},
  "jvm": {"options-diff-vs-baseline": ["…"], "gc": "…", "heap": "…"},
  "domain-config-diff": ["…"],
  "machine": {"cpu": "…", "mem": "…", "kernel": "…", "governor": "…", "thp": "…", "swap": "…"},
  "db": {"pg-version": "…", "config-diff": ["…"]},
  "load": {"tool": "wrk2|ws-harness", "script-version": "…", "scenario": "<功能分项>-<用例>|mixed", "rps-ladder": []}
}
```

### 脚本骨架(实施阶段产出,放 `.scratch/perf-plan/bin/`)

- `bench.sh <label> <mode> [--jvm-opts <file>] [--config-patch <file>]`:编排一个完整 run——应用 JVM 参数注入/域配置补丁 → 重启 Payara → 等就绪 → 起常驻采集 → 按 mode 跑 wrk2 → 停采集 → 收拢入 run 目录 → 写 manifest(记 diff vs 基线)→ 回滚配置。**每个 run 至多一项变更(单变量纪律)**
- `flame.sh <label> <view>`:对运行中的 Payara 抓单视图火焰图(cpu/alloc/lock/wall),独立 run
- `compare.sh <runA> <runB>`:两 run 目录对比表(吞吐、各档延迟分位、GC、RSS)

### 执行安排

脚本交付后在测试机上执行(压测机跑压测、被测机跑服务与采集),run 目录整体回收后离线分析;每阶段由你确认验收数字后进入下一阶段。

### 后续工件

- 启动优化专项:独立成文(不含在本方案)
- 各阶段实施记录:阶段开始时在 `.scratch/perf-plan/` 下建对应笔记
