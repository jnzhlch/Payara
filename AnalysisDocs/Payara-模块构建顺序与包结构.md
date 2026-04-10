
# Payara 模块构建顺序与包结构文档

> 版本: 7.2026.4-SNAPSHOT
> 生成日期: 2026-04-10
> 说明: 本文档按 Maven Reactor 实际构建顺序（依赖解析顺序）组织，数据来源于 `mvn` 构建日志

## 1. 顶层构建顺序

根 POM: `fish.payara.server:payara-aggregator`

```
payara-aggregator
├── 1. nucleus          (核心内核与基础设施)
├── 2. appserver        (Jakarta EE 完整应用服务器)
└── 3. api              (payara-bom 仅依赖管理，实际构建末尾)
```

> 注意: `api/payara-api` 并非最后构建，而是在 nucleus 阶段 config-api 之后即被构建（被 nucleus 多个模块依赖）；`api/payara-bom` 在最末尾。

---

## 2. Nucleus 模块 (`nucleus/`) + Payara API

**POM**: `fish.payara.server:payara-nucleus-parent`
**说明**: Payara 核心内核，提供 HK2 注入、OSGi 平台、管理 CLI、监控等基础服务
> `api/payara-api` 在 nucleus 阶段构建（位于 nucleus config-api 之后），其包结构如下：

**POM**: `fish.payara.server:payara-nucleus-parent`
**说明**: Payara 核心内核，提供 HK2 注入、OSGi 平台、管理 CLI、监控等基础服务

### 2.1 子模块实际构建顺序（Maven Reactor 依赖解析序列）

```
nucleus/
├── 1.  osgi-platforms       (OSGi 平台 - Apache Felix)
├── 2.  common               (共享工具类)
├── 3.  hk2                  (HK2 注入框架)
├── 4.  grizzly              (HTTP/NIO 网络层)
├── 5.  test-utils           (测试工具)
├── 6.  admin (前半)         (config-api, util, cli, launcher)
├── 7.  ★ api/payara-api     (公共 API — 在此阶段构建)
├── 8.  core                 (核心内核 - Bootstrap, Kernel, 日志)
├── 9.  security             (安全基础设施 - ssl, core, services)
├── 10. deployment           (应用部署基础)
├── 11. cluster              (Hazelcast 集群)
├── 12. flashlight           (诊断与监控探针)
├── 13. payara-modules       (Payara 特有功能模块)
├── 14. admin (后半)         (monitor, rest, server-mgmt, template)
├── 15. packager             (包装配)
├── 16. distributions        (最小分发包)
├── 17. diagnostics          (诊断工具)
└── 18. resources            (资源与模板)
```

### 2.2 osgi-platforms (OSGi 平台)

```
osgi-platforms/
├── felix                    → org.apache.felix
├── osgi-cli-remote          → org.apache.felix.gogo
├── osgi-cli-interactive     → org.apache.felix.gogo
├── osgi-container           → org.glassfish.osgi
├── osgi-cli-interactive-l10n
└── osgi-cli-remote-l10n
```

### 2.3 cluster (集群)

```
cluster/
├── common                   → org.glassfish.cluster.ssh
├── admin                    → com.sun.enterprise.cluster.sockets
├── cli                      → com.sun.enterprise.cluster.cli
├── ssh                      → org.glassfish.cluster.ssh
├── common-l10n
├── admin-l10n
├── cli-l10n
└── ssh-l10n
```

### 2.4 common (共享工具)

```
common/
├── simple-glassfish-api     → org.glassfish.api
├── internal-api             → com.sun.enterprise.util
├── common-util              → org.glassfish.common.util
├── glassfish-api            → org.glassfish.api
├── mbeanserver              → com.sun.enterprise.mbeanserver
├── scattered-archive-api    → org.glassfish.embeddable
├── amx-core                 → org.glassfish.admin.amx.core
├── common-util-l10n
├── internal-api-l10n
├── glassfish-api-l10n
└── mbeanserver-l10n
```

### 2.5 core (核心内核)

```
core/
├── bootstrap                → com.sun.enterprise.glassfish
│   └── com.sun.enterprise.glassfish.bootstrap
│   └── com.sun.enterprise.glassfish.bootstrap.osgi
├── kernel                   → com.sun.enterprise.v3
│   └── com.sun.enterprise.v3.common
│   └── com.sun.enterprise.v3.server
├── api-exporter             → org.glassfish.kernel.api
├── extra-jre-packages       → (JRE 扩展包)
├── logging                  → com.sun.enterprise.logging
│   └── com.sun.enterprise.logging.impl
│   └── com.sun.enterprise.util.logging
├── javassist-packages       → (Javassist 字节码操作)
├── logging-l10n
├── kernel-l10n
└── context-propagation      → org.glassfish.contextpropagation
    └── org.glassfish.contextpropagation.internal
    └── org.glassfish.contextpropagation.wire
```

### 2.6 admin (管理框架)

```
admin/
├── config-api               → org.glassfish.config.support
│   └── com.sun.enterprise.config.modularity
│   └── org.glassfish.config.api
├── server-mgmt              → com.sun.enterprise.admin.servermgmt
│   └── com.sun.enterprise.admin.servermgmt.domain
├── util                     → com.sun.enterprise.admin.util
├── cli                      → com.sun.enterprise.admin.cli
│   └── com.sun.enterprise.admin.cli.remote
│   └── com.sun.enterprise.admin.cli.embeddable
│   └── com.sun.enterprise.admin.cli.program
├── launcher                 → com.sun.enterprise.admin.launcher
├── monitor                  → org.glassfish.monitoring
│   └── org.glassfish.monitoring.jmx
│   └── com.sun.enterprise.admin.monitor
├── rest                     → org.glassfish.admin.rest
│   └── org.glassfish.admin.rest.resources
│   └── org.glassfish.admin.rest.composite
├── template                 → com.sun.enterprise.admin.config
├── (本地化模块省略...)
```

### 2.7 flashlight (诊断探针)

```
flashlight/
├── agent                    → org.glassfish.flashlight.agent
├── flashlight-extra-jdk-packages
├── framework                → org.glassfish.flashlight
│   └── org.glassfish.flashlight.impl
│   └── org.glassfish.flashlight.provider
└── framework-l10n
```

### 2.8 grizzly (HTTP/NIO 网络层)

```
grizzly/
├── config                   → org.glassfish.grizzly.config
│   └── org.glassfish.grizzly.config.dom
│   └── org.glassfish.grizzly.config.portunif
└── nucleus-grizzly-all      → org.glassfish.grizzly
```

### 2.9 deployment (部署基础)

```
deployment/
├── common                   → org.glassfish.deployment.common
├── admin                    → org.glassfish.deployment.admin
│   └── org.glassfish.deployment.admin.cli
├── autodeploy               → org.glassfish.deployment.autodeploy
├── dtds                     → (DTD 定义文件)
├── schemas                  → (XML Schema 定义)
├── common-l10n
├── admin-l10n
└── autodeploy-l10n
```

### 2.10 payara-modules (Payara 特有功能)

```
payara-modules/
├── asadmin-audit            → fish.payara.nucleus.audit
├── hazelcast-bootstrap      → fish.payara.nucleus.hazelcast
│   └── fish.payara.nucleus.hazelcast.admin
├── healthcheck-core         → fish.payara.nucleus.healthcheck
│   └── fish.payara.nucleus.healthcheck.admin
│   └── fish.payara.nucleus.healthcheck.core
├── healthcheck-cpool        → fish.payara.nucleus.healthcheck.cpool
├── healthcheck-stuck        → fish.payara.nucleus.healthcheck.stuck
├── phonehome-bootstrap      → fish.payara.nucleus.phonehome
├── requesttracing-core      → fish.payara.nucleus.requesttracing
│   └── fish.payara.nucleus.requesttracing.admin
│   └── fish.payara.nucleus.requesttracing.execution
├── notification-core        → fish.payara.nucleus.notification
│   └── fish.payara.nucleus.notification.admin
│   └── fish.payara.nucleus.notification.core
├── notification-eventbus-core    → fish.payara.nucleus.notification.eventbus
├── notification-cdi-eventbus-core → fish.payara.nucleus.notification.cdieventbus
├── asadmin-recorder         → fish.payara.nucleus.recorder
├── service-exemplar         → fish.payara.nucleus.exemplar
├── jsr107-repackaged        → (JSR-107 缓存重新打包)
├── payara-executor-service  → fish.payara.nucleus.executorservice
├── opentracing-adapter      → fish.payara.nucleus.opentracing
└── nucleus-microprofile     → fish.payara.nucleus.microprofile
    └── fish.payara.nucleus.microprofile.config
```

### 2.11 security (安全基础设施)

```
security/
├── core                     → com.sun.enterprise.security
│   └── com.sun.enterprise.security.auth
│   └── com.sun.enterprise.security.auth.login
│   └── com.sun.enterprise.security.auth.realm
│   └── com.sun.enterprise.security.cli
├── ssl-impl                 → com.sun.enterprise.security.ssl
├── services                 → com.sun.enterprise.security.jmac
└── (本地化模块省略...)
```

### 2.12 diagnostics (诊断工具)

```
diagnostics/
├── diagnostics-api          → org.glassfish.api.diag
└── context                  → com.sun.enterprise.diagnostics
```

### 2.13 hk2 (HK2 注入框架)

```
hk2/
├── hk2-config               → org.glassfish.hk2.configuration
├── config-generator         → org.jvnet.hk2.config
└── config-types             → org.glassfish.config.types
```

---

## 3. Appserver 模块 (`appserver/`)

**POM**: `fish.payara.server:payara-parent`
**说明**: 在 Nucleus 基础上构建完整 Jakarta EE 应用服务器

### 3.1 子模块实际构建顺序（Maven Reactor 依赖解析序列）

```
appserver/
├── 1.  common               (EE 共享工具)
├── 2.  deployment           (EE 部署 - DOL, JavaEE Core/Full)
├── 3.  transaction          (事务管理 - JTA, JTS)
├── 4.  resources            (资源管理)
├── 5.  connectors           (JCA 连接器)
├── 6.  ejb                  (EJB 容器)
├── 7.  orb                  (CORBA/IIOP)
├── 8.  security             (EE 安全)
├── 9.  admin                (EE 管理 - backup, cli, template)
├── 10. core                 (EE 内核 - jakartaee-kernel)
├── 11. persistence          (JPA 持久化)
├── 12. web                  (Web 容器)
├── 13. connectors (续)      (连接器打包)
├── 14. jms                  (JMS 消息服务)
├── 15. jdbc                 (JDBC 数据库连接)
├── 16. concurrent           (并发/托管线程)
├── 17. batch                (批处理)
├── 18. ha                   (高可用)
├── 19. payara-appserver-modules (前半 - Micro, REST, Tracing, Health)
├── 20. admingui             (管理控制台)
├── 21. appclient            (应用客户端)
├── 22. webservices          (Web 服务)
├── 23. extras               (Payara Micro/Embedded/Docker)
├── 24. data                 (Jakarta Data)
├── 25. payara-appserver-modules (后半 - MicroProfile 全系列)
├── 26. osgi-platforms       (EE OSGi 平台)
├── 27. packager             (打包)
├── 28. featuresets          (功能集定义)
├── 29. distributions        (分发包)
├── 30. security/tests       (安全集成/测试)
├── 31. flashlight           (EE 诊断)
├── 32. ant-tasks            (Ant 任务)
└── 33. ★ api/payara-bom     (BOM - 依赖管理，无源码)
```

### 3.2 common (EE 共享工具)

```
common/
├── stats77                  → com.sun.enterprise.admin.monitor.registry
│   └── org.glassfish.j2ee.statistics
├── amx-javaee               → org.glassfish.admin.amx.impl.j2ee
├── glassfish-ee-api         → com.sun.appserv
│   └── com.sun.appserv.connectors.spi
├── annotation-framework     → org.glassfish.apf
│   └── org.glassfish.apf.context
│   └── org.glassfish.apf.factory
├── container-common         → com.sun.enterprise.container.common
│   └── org.glassfish.javaee.services
│   └── org.glassfish.ha.common
├── glassfish-naming         → com.sun.enterprise.naming
│   └── com.sun.enterprise.naming.impl
│   └── com.sun.enterprise.naming.spi
└── (本地化模块省略...)
```

### 3.3 ha (高可用)

```
ha/
├── ha-file-store            → org.glassfish.ha.store.adapter.file
└── ha-hazelcast-store       → fish.payara.ha.hazelcast.store
```

### 3.4 deployment (EE 部署)

```
deployment/
├── dtds                     → (DTD 定义文件)
├── schemas                  → (XML Schema 定义)
├── dol                      → com.sun.enterprise.deployment
│   └── com.sun.enterprise.deployment.annotation
│   └── com.sun.enterprise.deployment.util
│   └── com.sun.enterprise.deployment.xml
├── client                   → org.glassfish.deployapi
│   └── org.glassfish.deployment.client
├── javaee-core              → org.glassfish.javaee.core.deployment
├── javaee-full              → org.glassfish.javaee.full.deployment
├── jsr88-jar                → (JSR-88 部署)
└── (本地化模块省略...)
```

### 3.5 admin (EE 管理)

```
admin/
├── backup                   → com.sun.enterprise.backup
├── cli                      → org.glassfish.admin.cli
├── cli-optional             → com.sun.enterprise.admin.cli.optional
├── admin-core               → com.sun.enterprise.admin
├── gf_template              → (管理模板)
├── gf_template_web          → (Web 管理模板)
└── (本地化模块省略...)
```

### 3.6 core (EE 内核)

```
core/
├── jakartaee-kernel         → org.glassfish.kernel.jakartaee
└── api-exporter-fragment    → (API 导出)
```

### 3.7 transaction (事务管理)

```
transaction/
├── internal-api             → com.sun.enterprise.transaction.api
├── jta                      → com.sun.enterprise.transaction
├── jta-xa                   → com.sun.enterprise.transaction.xa
├── jts                      → com.sun.enterprise.transaction.jts
│   └── com.sun.jts.CosTransactions
└── (本地化模块省略...)
```

### 3.8 web (Web 容器)

```
web/
├── web-core                 → org.apache.catalina
│   └── org.apache.catalina.connector
│   └── org.apache.catalina.core
│   └── org.apache.catalina.valves
│   └── com.sun.enterprise.web
├── war-util                 → org.glassfish.web.loader
│   └── org.glassfish.web.util
├── weld-integration         → org.glassfish.weld
│   └── org.glassfish.weld.services
│   └── org.glassfish.weld.ejb
├── gf-weld-connector        → org.glassfish.cdi
├── web-ha                   → org.glassfish.web.ha
├── web-naming               → org.glassfish.web.sniffer
├── web-sse                  → org.glassfish.web.sse
├── web-glue                 → org.glassfish.web
├── web-embed-api            → org.glassfish.embeddable.web
├── web-jsf                  → org.glassfish.jsf
└── guice-connector          → org.glassfish.guice
```

### 3.9 ejb (EJB 容器)

```
ejb/
├── ejb-container            → com.sun.ejb
│   └── com.sun.ejb.containers
│   └── com.sun.ejb.containers.interceptors
│   └── com.sun.ejb.codegen
│   └── com.sun.ejb.monitoring
├── ejb-full-container       → com.sun.ejb
├── ejb-http-remoting        → com.sun.ejb
├── ejb-timer-service-app    → com.sun.ejb.container
├── ejb-timer-databases      → com.sun.ejb
├── ejb-internal-api         → com.sun.ejb.spi
└── (其他子模块省略...)
```

### 3.10 resources (资源管理)

```
resources/
├── resources-runtime        → org.glassfish.resources
│   └── org.glassfish.resources.deployer
│   └── org.glassfish.resources.beans
├── javamail-runtime         → org.glassfish.resources.javamail
│   └── org.glassfish.resources.javamail.deployer
└── resources-connector      → (资源连接器配置)
```

### 3.11 connectors (JCA 连接器)

```
connectors/
├── connectors-runtime       → com.sun.enterprise.connectors
│   └── com.sun.enterprise.connectors.module
│   └── com.sun.enterprise.connectors.util
│   └── com.sun.enterprise.connectors.service
│   └── com.sun.enterprise.resource
├── connectors-inbound-runtime → com.sun.enterprise.connectors
└── work-management          → com.sun.enterprise.connectors.work
```

### 3.12 jms (JMS 消息服务)

```
jms/
├── jms-core                 → com.sun.enterprise.connectors.jms
│   └── com.sun.enterprise.connectors.jms.system
│   └── com.sun.enterprise.connectors.jms.inflow
├── jms-handlers             → com.sun.enterprise.connectors.jms.deployment
├── gf-jms-connector         → com.sun.enterprise.connectors.jms.config
└── gf-jms-injection         → org.glassfish.jms.injection
```

### 3.13 jdbc (JDBC 数据库连接)

```
jdbc/
├── jdbc-ra/jdbc-core        → com.sun.gjc
│   └── com.sun.gjc.spi
│   └── com.sun.gjc.spi.base
│   └── com.sun.gjc.monitoring
│   └── fish.payara.jdbc
├── jdbc-config              → org.glassfish.jdbc.config
└── jdbc-runtime             → org.glassfish.jdbc
    └── org.glassfish.jdbc.deployer
    └── org.glassfish.jdbc.pool.monitor
```

### 3.14 persistence (JPA 持久化)

```
persistence/
├── common                   → org.glassfish.persistence.common
├── jpa-container            → org.glassfish.persistence.jpa
├── entitybean-container     → org.glassfish.persistence.ejb.entitybean
├── gf-jpa-connector         → (JPA 连接器配置)
├── eclipselink-wrapper      → (EclipseLink 包装)
├── cmp/enhancer             → com.sun.jdo.api.persistence.enhancer
├── cmp/ejb-mapping          → com.sun.jdo.api.persistence.mapping
├── cmp/model                → com.sun.jdo.spi.persistence.support
└── (本地化模块省略...)
```

### 3.15 concurrent (并发/托管线程)

```
concurrent/
├── concurrent-connector     → org.glassfish.concurrent.config
├── concurrent-impl          → org.glassfish.concurrent.runtime
└── (本地化模块省略...)
```

### 3.16 batch (批处理)

```
batch/
├── batch-database           → (数据库 Schema)
├── glassfish-batch-connector → fish.payara.jbatch.persistence.rdbms
├── glassfish-batch-commands → org.glassfish.batch
├── hazelcast-jbatch-store   → fish.payara.jbatch.persistence.hazelcast
└── jbatch-repackaged        → com.ibm.jbatch.container
```

### 3.17 security (EE 安全)

```
security/
├── webintegration           → com.sun.web.security
├── core-ee                  → com.sun.enterprise.security
│   └── com.sun.enterprise.security.ee
├── jacc.provider.inmemory   → (JACC 内存提供者)
├── webservices.security     → (Web 服务安全)
├── ejb.security             → com.sun.enterprise.security.ee.ejb
├── appclient.security       → com.sun.enterprise.security.appclient
├── realm-stores             → fish.payara.security.realm.identitystores
└── (本地化模块省略...)
```

### 3.18 grizzly (EE Grizzly 扩展)

```
grizzly/
├── grizzly-container        → org.glassfish.extras.grizzly
└── glassfish-grizzly-extra-all → org.glassfish.grizzly
```

### 3.19 webservices (Web 服务)

```
webservices/
├── metro-fragments          → (Metro WS 片段)
├── metro-glue               → (Metro WS 粘合层)
├── connector                → org.glassfish.webservices
├── jsr109-impl              → com.sun.xml.rpc.wsdl
├── soap-tcp                 → (SOAP over TCP)
└── webservices-scripts      → (WS 脚本)
```

### 3.20 orb (CORBA/IIOP)

```
orb/
├── orb-enabler              → org.glassfish.orb.admin.config
├── orb-connector            → org.glassfish.enterprise.iiop.api
├── orb-iiop                 → org.glassfish.enterprise.iiop.impl
└── orb-connector-l10n
```

### 3.21 flashlight (EE 诊断)

```
flashlight/
├── btrace                   → (BTrace 探针)
└── client                   → org.glassfish.flashlight.client
```

### 3.22 admingui (管理控制台)

```
admingui/
├── dataprovider             → (数据提供者，无 Java 代码)
├── plugin-service           → org.glassfish.admingui.plugin
├── common                   → fish.payara.admingui.common
│   └── fish.payara.admingui.common.handlers
│   └── fish.payara.admingui.common.util
├── core                     → org.glassfish.admingui
│   └── org.glassfish.admingui.handlers
│   └── org.glassfish.admingui.theme
├── concurrent               → org.glassfish.concurrent.admingui
├── cluster                  → org.glassfish.cluster.admingui
├── payara-theme             → org.glassfish.admingui.customtheme
├── web                      → org.glassfish.web.admingui
├── gf-admingui-connector    → org.glassfish.admingui.connector
├── jdbc                     → org.glassfish.jdbc.admingui
├── jms-plugin               → org.glassfish.admingui.plugin.jms
├── jca                      → org.glassfish.jca.admingui
├── ejb                      → org.glassfish.ejb.admingui
├── ejb-lite                 → org.glassfish.ejb_lite.admingui
├── corba                    → org.glassfish.corba.admingui
├── jts                      → org.glassfish.jts.admingui
├── microprofile-console-plugin → org.glassfish.microprofile.admingui
├── healthcheck-service-console-plugin → fish.payara.healthcheck.admingui
├── eventbus-notifier-console-plugin → fish.payara.notification.eventbus.admingui
├── jmx-monitoring-plugin    → fish.payara.jmx.monitoring.admingui
├── jms-notifier-console-plugin → fish.payara.notification.jms.admingui
├── payara-console-extras    → fish.payara.admingui.extras
├── reference-manual         → org.glassfish.reference.admingui
├── war                      → (Admin GUI WAR 打包)
├── dist-fragment            → (Admin GUI 分发片段)
└── (各模块的 -l10n 本地化模块省略...)
```

### 3.23 appclient (应用客户端)

```
appclient/
├── server                   → com.sun.enterprise.appclient
├── client
│   ├── acc                  → org.glassfish.appclient.client
│   ├── acc-config           → org.glassfish.appclient.client.config
│   ├── acc-standalone       → org.glassfish.appclient.client.standalone
│   ├── acc-standalone-l10n
│   ├── acc-l10n
│   ├── jws                  → org.glassfish.appclient.jws
│   └── appclient-scripts    → (客户端脚本)
└── (本地化模块省略...)
```

### 3.24 extras (Payara Micro/Embedded)

```
extras/
├── javaee                   → fish.payara.server.appserver.javaee
├── appserv-rt               → fish.payara.server.appserver.runtime
├── payara-micro             → fish.payara.micro
│   └── fish.payara.micro.boot
│   └── fish.payara.micro.cmd
│   └── fish.payara.micro.impl
├── embedded                 → fish.payara.server.embedded
│   └── common/bootstrap
│   └── common/installroot-builder
│   └── common/instanceroot-builder
│   └── common/osgi-main
│   └── common/osgi-modules-uninstaller
│   └── glassfish-uber
│   └── shell
└── docker-images            → fish.payara.docker
```

### 3.25 payara-appserver-modules (前半: Payara 扩展)

> 在 admingui 之前构建，包含 Micro、REST 端点、追踪、健康检查等 Payara 特有模块

```
payara-appserver-modules/
├── payara-jsr107            → fish.payara.cdi.jsr107
├── hazelcast-ejb-timer      → fish.payara.ejb.timer.hazelcast
├── hazelcast-eclipselink-coordination → fish.payara.persistence.eclipselink.cache.coordination
├── jmx-monitoring-service   → fish.payara.jmx.monitoring
│   └── fish.payara.jmx.monitoring.admin
├── payara-micro-service     → fish.payara.appserver.micro
├── payara-micro-cdi         → fish.payara.appserver.micro.cdi
├── jaspic-servlet-utils     → fish.payara.security.jaspic
├── notification-jms-core    → fish.payara.notification.jms
├── environment-warning      → fish.payara.server.warning
├── payara-rest-endpoints    → fish.payara.appserver.rest.endpoints
├── cdi-auth-roles           → fish.payara.appserver.cdi.auth.roles
├── jaxrs-client-tracing     → fish.payara.requesttracing.jaxrs.client
├── payara-rest-endpoints-impl → fish.payara.rest.endpoints.impl
└── (其他 MicroProfile 模块见 3.30)
```

### 3.30 payara-appserver-modules (后半: MicroProfile 全系列)

> 在 admingui/appclient/webservices/extras/data 之后构建

```
payara-appserver-modules/microprofile/
├── config               → fish.payara.microprofile.config
│   └── fish.payara.microprofile.config.cdi
├── config-extensions    → fish.payara.microprofile.config.source
│   └── fish.payara.microprofile.config.source.aws
│   └── fish.payara.microprofile.config.source.azure
│   └── fish.payara.microprofile.config.source.gcp
│   └── fish.payara.microprofile.config.source.hashicorp
│   └── fish.payara.microprofile.config.source.ldap
│   └── fish.payara.microprofile.config.source.toml
│   └── fish.payara.microprofile.config.source.dynamodb
├── healthcheck          → fish.payara.microprofile.healthcheck
│   └── fish.payara.microprofile.healthcheck.cdi
│   └── fish.payara.microprofile.healthcheck.checks
│   └── fish.payara.microprofile.healthcheck.servlet
├── metrics              → fish.payara.microprofile.metrics
│   └── fish.payara.microprofile.metrics.impl
│   └── fish.payara.microprofile.metrics.rest
│   └── fish.payara.microprofile.metrics.jmx
├── fault-tolerance      → fish.payara.microprofile.faulttolerance
│   └── fish.payara.microprofile.faulttolerance.config
│   └── fish.payara.microprofile.faulttolerance.interceptor
│   └── fish.payara.microprofile.faulttolerance.state
├── jwt-auth             → fish.payara.microprofile.jwtauth
│   └── fish.payara.microprofile.jwtauth.cdi
│   └── fish.payara.microprofile.jwtauth.jwt
│   │   └── fish.payara.microprofile.jwtauth.eesecurity
│   ├── openapi              → fish.payara.microprofile.openapi
│   │   └── fish.payara.microprofile.openapi.impl
│   │   └── fish.payara.microprofile.openapi.rest
│   ├── rest-client          → fish.payara.microprofile.restclient
│   │   └── fish.payara.microprofile.restclient.cdi
│   │   └── fish.payara.microprofile.restclient.proxy
│   ├── rest-client-ssl      → fish.payara.microprofile.restclient.ssl
│   ├── telemetry            → fish.payara.microprofile.telemetry
│   │   └── fish.payara.microprofile.telemetry.api
│   │   └── fish.payara.microprofile.telemetry.spi
├── opentracing          → fish.payara.microprofile.opentracing
│   └── fish.payara.microprofile.opentracing.cdi
│   └── fish.payara.microprofile.opentracing.jaxrs
├── opentracing-jaxws    → fish.payara.microprofile.opentracing.jaxws
├── microprofile-common  → fish.payara.microprofile.common
├── microprofile-connector → fish.payara.microprofile.deployment
└── hazelcast-bootstrap  → fish.payara.server.hazelcast
```

### 3.26 data (Jakarta Data)

```
data/
└── data-core                → fish.payara.data.core
    └── fish.payara.data.core.activation
    └── fish.payara.data.core.cdi.extension
    └── fish.payara.data.core.connector
    └── fish.payara.data.core.querymethod
    └── fish.payara.data.core.util
```

### 3.27 osgi-platforms (EE OSGi 平台)

```
osgi-platforms/
├── felix-webconsole-extension → org.glassfish.osgi.felixwebconsoleextension
├── glassfish-osgi-console-plugin → org.glassfish.osgi.admingui
└── glassfish-osgi-console-plugin-l10n
```

### 3.28 ant-tasks (Ant 任务)

```
ant-tasks/
└── (Ant 构建任务，无 Java 包)
```

### 3.29 packager (打包)

```
packager/
├── external                 → (外部依赖)
├── legal                    → (许可证文件)
├── appserver-base           → (基础包)
├── appserver-core           → (核心包)
├── felix                    → (OSGi Felix 包)
├── glassfish-hk2            → (HK2 包)
├── glassfish-grizzly        → (Grizzly 包)
├── glassfish-nucleus        → (Nucleus 包)
├── glassfish-common         → (公共包)
├── glassfish-cluster        → (集群包)
├── glassfish-management     → (管理包)
├── glassfish-ha             → (HA 包)
├── glassfish-jca            → (JCA 包)
├── glassfish-jpa            → (JPA 包)
├── glassfish-jta            → (JTA 包)
├── glassfish-jsf            → (JSF 包)
├── glassfish-web            → (Web 包)
├── glassfish-jdbc           → (JDBC 包)
├── glassfish-gui            → (GUI 包)
├── glassfish-ejb-lite       → (EJB Lite 包)
├── glassfish-common-full    → (Full 公共包)
├── glassfish-jts            → (JTS 包)
├── glassfish-jms            → (JMS 包)
├── glassfish-ejb            → (EJB 包)
├── glassfish-cmp            → (CMP 包)
├── metro                    → (Metro WS 包)
├── glassfish-appclient      → (AppClient 包)
├── h2db                     → (H2 数据库包)
├── jersey                   → (Jersey 包)
├── glassfish-jcdi           → (CDI 包)
├── mq                       → (OpenMQ 包)
├── glassfish-corba          → (CORBA 包)
├── glassfish-corba-base     → (CORBA 基础包)
├── hazelcast                → (Hazelcast 包)
├── healthcheck              → (健康检查包)
├── requesttracing           → (请求追踪包)
├── notification             → (通知包)
├── notification-jms         → (JMS 通知包)
├── asadmin-recorder         → (命令录制包)
├── payara-api               → (Payara API 包)
├── microprofile-package     → (MicroProfile 包)
├── glassfish-jmx            → (JMX 包)
├── glassfish-entitybeans-container → (实体 Bean 包)
├── payara-executor-service  → (执行器服务包)
├── payara-micro             → (Payara Micro 包)
├── payara-rest-endpoints    → (REST 端点包)
├── docker                   → (Docker 包)
├── data                     → (Jakarta Data 包)
├── payara-mvc               → (MVC 包)
├── json                     → (JSON 包)
├── phonehome                → (Phone Home 包)
├── environment-warning      → (环境警告包)
├── cdi-auth-roles           → (CDI Auth Roles 包)
├── JMX-Monitoring           → (JMX 监控包)
├── opentracing-jaxws-package → (OpenTracing JAX-WS 包)
└── (各功能集 Profile 包及本地化包省略...)
    ├── glassfish-web-profile
    ├── glassfish-full-profile
    ├── glassfish-web-incorporation
    ├── glassfish-full-incorporation
    └── glassfish-osgi-feature-pack
```

### 3.31 featuresets (功能集)

> 在 packager 之后、distributions 之前构建

```
featuresets/
├── minnow                   → (最小功能集)
├── web                      → (Web Profile 功能集)
├── payara-web               → (Payara Web 功能集)
├── glassfish                → (GlassFish Full 功能集)
├── payara                   → (Payara Full 功能集)
├── minnow-ml                → (最小 ML 功能集)
├── payara-web-ml            → (Payara Web ML 功能集)
└── payara-ml                → (Payara Full ML 功能集)
```

### 3.32 distributions (分发包)

> 最后构建，生成 Payara Server / Web / ML 各分发包

```
distributions/
├── payara                   → (Payara Server Full)
├── payara-ml                → (Payara Server ML)
├── payara-web               → (Payara Server Web Profile)
└── payara-web-ml            → (Payara Server Web Profile ML)
```

```
distributions/
├── payara                   → (Payara Server Full)
├── payara-ml                → (Payara Server ML)
├── payara-web               → (Payara Server Web Profile)
└── payara-web-ml            → (Payara Server Web Profile ML)
```

---

## 4. API 模块 (`api/`)

**POM**: `fish.payara.api:api-parent`
**说明**: 公共 API 定义与依赖管理 (BOM)

| 子模块 | 实际构建位置 | 说明 |
|--------|-------------|------|
| `payara-api` | Nucleus 阶段, admin/config-api 之后 (序列 #7) | 被 nucleus/admin、payara-modules 等依赖 |
| `payara-bom` | Appserver 末尾 (序列 #33) | 仅依赖管理，无源码 |

```
api/
├── payara-api               → fish.payara.api
│   └── fish.payara.cdi
│       └── fish.payara.cdi.auth.roles
│       └── fish.payara.cdi.jsr107
│   └── fish.payara.cluster
│   └── fish.payara.jacc
│   └── fish.payara.micro.cdi
│   └── fish.payara.notification
│       └── fish.payara.notification.eventbus
│       └── fish.payara.notification.healthcheck
│       └── fish.payara.notification.requesttracing
│   └── fish.payara.security
│       └── fish.payara.security.api
│       └── fish.payara.security.client
│   └── fish.payara.sql
└── payara-bom               → (BOM - 无源码)
```

---

## 5. 包命名规范总结

| 前缀 | 说明 |
|------|------|
| `com.sun.enterprise.*` | 核心 GlassFish/Payara 功能 |
| `com.sun.appserv.*` | 应用服务编程接口 |
| `com.sun.ejb.*` | EJB 容器实现 |
| `com.sun.gjc.*` | JDBC 资源适配器 |
| `com.sun.jdo.*` | JDO/CMP 持久化 |
| `com.sun.jts.*` | JTS 事务服务 |
| `com.sun.web.security.*` | Web 安全集成 |
| `com.sun.enterprise.admin.*` | 管理框架 |
| `com.sun.enterprise.naming.*` | 命名服务 |
| `com.sun.enterprise.deployment.*` | 部署对象库 |
| `com.sun.enterprise.connectors.*` | JCA/JMS 连接器 |
| `com.sun.enterprise.security.*` | 安全框架 |
| `com.sun.enterprise.config.*` | 配置管理 |
| `org.glassfish.*` | GlassFish 扩展实现 |
| `org.glassfish.api.*` | GlassFish 公共 API |
| `org.glassfish.admin.*` | 管理扩展 |
| `org.glassfish.weld.*` | CDI (Weld) 集成 |
| `org.glassfish.grizzly.*` | HTTP/NIO 网络 |
| `org.glassfish.ha.*` | 高可用 |
| `org.glassfish.jpa.*` | JPA 持久化 |
| `org.glassfish.concurrent.*` | 并发管理 |
| `org.glassfish.jms.*` | JMS 注入 |
| `org.glassfish.admingui.*` | 管理控制台 |
| `org.apache.catalina.*` | Servlet 容器 (Tomcat 派生) |
| `fish.payara.*` | Payara 特有功能 |
| `fish.payara.nucleus.*` | Nucleus Payara 扩展 |
| `fish.payara.micro.*` | Payara Micro |
| `fish.payara.microprofile.*` | MicroProfile 实现 |
| `fish.payara.data.*` | Jakarta Data |
| `fish.payara.security.*` | Payara 安全扩展 |
| `fish.payara.ha.*` | Payara 高可用 |
| `fish.payara.requesttracing.*` | 请求追踪 |
| `fish.payara.notification.*` | 通知系统 |
| `fish.payara.healthcheck.*` | 健康检查 |
| `fish.payara.jmx.monitoring.*` | JMX 监控 |
| `fish.payara.jdbc.*` | JDBC 扩展 |
| `fish.payara.ejb.timer.*` | EJB 定时器 |
| `fish.payara.persistence.*` | 持久化扩展 |
| `fish.payara.docker.*` | Docker 支持 |
