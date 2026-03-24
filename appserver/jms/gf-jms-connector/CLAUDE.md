# JMS Connector - JMS 连接器配置架构

## 概述

`gf-jms-connector` 模块提供 JMS 服务的配置基础设施，定义 JMS 服务、主机和可用性的配置接口。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    HK2 Configuration                           │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  @Configured Interfaces                                 │ │
│  │  ├─→ JmsService                                        │ │
│  │  ├─→ JmsHost                                           │ │
│  │  └─→ JmsAvailability                                   │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Configuration Sources                       │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  domain.xml                                              │ │
│  │  <jms-service type="EMBEDDED">                           │ │
│  │    <jms-host port="7676" />                              │ │
│  │  </jms-service>                                          │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  @CustomConfiguration                                   │ │
│  │  baseConfigurationFileName="jms-module-conf.xml"         │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    JMS Runtime                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  JmsProviderLifecycle                                    │ │
│  │  ├─→ 读取 JmsService 配置                               │ │
│  │  ├─→ 获取 JmsHost 列表                                  │ │
│  │  ├─→ 启动 Broker (EMBEDDED/LOCAL)                       │ │
│  │  └─→ 配置 JMX                                           │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## JmsService

### 配置接口

```java
@Configured
@HasCustomizationTokens
@CustomConfiguration(baseConfigurationFileName = "jms-module-conf.xml")
public interface JmsService extends ConfigExtension, PropertyBag, Container {

    /**
     * 启动超时 (秒)
     * 服务器实例等待 JMS 服务响应的时间
     * 默认: 60 秒
     */
    @Attribute(defaultValue="60")
    @Min(value=1)
    String getInitTimeoutInSeconds();

    /**
     * JMS 服务类型
     * EMBEDDED: 内嵌 Broker
     * LOCAL: 本地独立进程
     * REMOTE: 外部 Broker
     * DISABLED: 禁用 JMS
     */
    @Attribute
    @NotNull
    @Pattern(regexp="(LOCAL|EMBEDDED|REMOTE|DISABLED)")
    String getType();

    /**
     * Broker 启动参数
     * 传递给 JMS 服务实例的启动参数
     */
    @Attribute
    String getStartArgs();

    /**
     * 默认 JMS 主机
     * 当类型为 LOCAL 时启动的 jms-host 引用
     */
    @Attribute
    String getDefaultJmsHost();

    /**
     * 主 Broker
     * 用于集群协调的主 Broker URL
     */
    @Attribute
    String getMasterBroker();

    /**
     * 重连间隔 (秒)
     * 重连尝试之间的间隔时间
     * 默认: 5 秒
     */
    @Attribute(defaultValue="5")
    @Min(value=1)
    String getReconnectIntervalInSeconds();

    /**
     * 重连次数
     * 总重连尝试次数
     * 默认: 3
     */
    @Attribute(defaultValue="3")
    String getReconnectAttempts();

    /**
     * 启用重连
     * 启用 (true) 或禁用 (false) 重连功能
     * 默认: true
     */
    @Attribute(defaultValue="true", dataType=Boolean.class)
    String getReconnectEnabled();

    /**
     * 地址列表行为
     * random: 随机选择 Broker
     * priority: 按顺序选择 Broker
     * 默认: random
     */
    @Attribute(defaultValue="random")
    @Pattern(regexp="(random|priority)")
    String getAddresslistBehavior();

    /**
     * 地址列表迭代次数
     * 重连逻辑迭代 imqAddressList 的次数
     * 默认: 3
     */
    @Attribute(defaultValue="3")
    @Min(value=-1)
    @Max(value=Integer.MAX_VALUE)
    String getAddresslistIterations();

    /**
     * MQ 方案
     * 与 Broker 建立连接的方案
     * mq: 默认方案
     * http: 通过 HTTP 连接
     */
    @Attribute
    @Pattern(regexp="(mq||http)")
    String getMqScheme();

    /**
     * MQ 服务类型
     * jms: 标准 JMS 服务
     * ssljms: 支持 SSL 的 JMS 服务
     */
    @Attribute
    @Pattern(regexp="(ssljms||jms)")
    String getMqService();

    /**
     * JMS 主机列表
     */
    @Element
    List<JmsHost> getJmsHost();

    /**
     * 额外属性
     */
    @Element
    List<Property> getProperty();
}
```

### 配置属性

```java
@PropertiesDesc(props={
    @PropertyDesc(
        name="instance-name",
        defaultValue="imqbroker",
        description="完整的 Sun GlassFish Message Queue Broker 实例名称"),

    @PropertyDesc(
        name="instance-name-suffix",
        defaultValue="xxxxxxxxxxxxxxxxxx",
        description="附加到完整 Message Queue Broker 实例名称的后缀。后缀用下划线 (_) 分隔。例如，如果实例名称是 'imqbroker'，附加后缀 'xyz' 会将实例名称更改为 'imqbroker_xyz'"),

    @PropertyDesc(
        name="append-version",
        defaultValue="",
        description="如果为 true，在完整 Message Queue Broker 实例名称后附加主版本号和次版本号，前面加下划线字符。例如，如果实例名称是 'imqbroker'，附加版本号会将实例名称更改为 imqbroker_8_0"),

    @PropertyDesc(
        name="user-name",
        defaultValue="xxxxxxxxxxxxxxxxxx",
        description="指定用于创建 JMS 连接的用户名。仅在 Broker 中默认的 username/password (guest/guest) 不可用时需要"),

    @PropertyDesc(
        name="password",
        defaultValue="xxxxxxxxxxxxxxxxxx",
        description="指定用于创建 JMS 连接的密码。仅在 Broker 中默认的 username/password (guest/guest) 不可用时需要")
})
```

## JmsHost

### 配置接口

```java
@Configured
@RestRedirects({
    @RestRedirect(opType = RestRedirect.OpType.POST,
                  commandName = "create-jms-host"),
    @RestRedirect(opType = RestRedirect.OpType.DELETE,
                  commandName = "delete-jms-host")
})
public interface JmsHost extends ConfigExtension, PropertyBag, Payload {

    /**
     * 端口模式
     * 支持端口号和系统属性替换
     */
    final static String PORT_PATTERN =
        "\\$\\{[\\p{L}\\p{N}_][\\p{L}\\p{N}\\-_./;#]*\\}" +
        "|[1-9]|[1-9][0-9]|[1-9][0-9][0-9]|[1-9][0-9][0-9][0-9]" +
        "|[1-5][0-9][0-9][0-9][0-9]|6[0-4][0-9][0-9][0-9]" +
        "|65[0-4][0-9][0-9]|655[0-2][0-9]|6553[0-5]";

    /**
     * 主机名称 (键)
     */
    @Attribute(key=true)
    @NotNull
    String getName();

    /**
     * 主机地址
     * IPv4/IPv6 地址或主机名
     */
    @Attribute
    String getHost();

    /**
     * 端口号
     * JMS 服务使用的端口
     * 默认: 7676
     */
    @Attribute(defaultValue="7676")
    @Pattern(regexp=PORT_PATTERN,
             message="{port-pattern}",
             payload=JmsHost.class)
    String getPort();

    /**
     * 延迟初始化
     * 如果为 false，在服务器启动期间启动此监听器
     * 默认: true
     */
    @Attribute(defaultValue="true", dataType=Boolean.class)
    String getLazyInit();

    /**
     * 管理员用户名
     * 默认: admin
     */
    @Attribute(defaultValue="admin")
    String getAdminUserName();

    /**
     * 管理员密码
     * 默认: admin
     */
    @Attribute(defaultValue="admin")
    String getAdminPassword();

    /**
     * 额外属性
     */
    @Element
    List<Property> getProperty();
}
```

## JmsAvailability

### 配置接口

```java
@Configured
public interface JmsAvailability extends ConfigExtension, PropertyBag {

    /**
     * 启用 JMS 可用性
     * 启用集群的 JMS 可用性
     * 默认: false
     */
    @Attribute(defaultValue="false", dataType=Boolean.class)
    String getAvailabilityEnabled();

    /**
     * 主 Broker
     * 集群的主 Broker URL
     */
    @Attribute
    String getMasterBroker();

    /**
     * 对象内存大小
     * 消息存储大小限制
     */
    @Attribute
    String getObjectMemSize();

    /**
     * 关闭超时
     * 集群关闭超时
     */
    @Attribute
    String getShutdownTimeout();

    /**
     * 集群 ID
     * 集群标识符
     */
    @Attribute
    String getClusterId();

    /**
     * Broker ID
     * Broker 标识符
     */
    @Attribute
    String getBrokerId();

    /**
     * 数据库属性
     * 用于增强集群的数据库配置
     */
    @Attribute
    String getDbName();

    @Attribute
    String getDriverClass();

    @Attribute
    String getUser();

    @Attribute
    String getPassword();

    @Attribute
    String getServerList();
}
```

## 配置层次结构

```
domain.xml
├── <configs>
│   └── <config name="server-config">
│       └── <jms-service>
│           ├── type="EMBEDDED"
│           ├── init-timeout-in-seconds="60"
│           ├── default-jms-host="default_JMS_host"
│           ├── reconnect-enabled="true"
│           ├── reconnect-interval-in-seconds="5"
│           ├── reconnect-attempts="3"
│           ├── addresslist-behavior="random"
│           ├── addresslist-iterations="3"
│           │
│           ├── <jms-host>
│           │   ├── name="default_JMS_host"
│           │   ├── host="localhost"
│           │   ├── port="7676"
│           │   ├── lazy-init="true"
│           │   ├── admin-user-name="admin"
│           │   └── admin-password="admin"
│           │
│           └── <property>
│               ├── name="instance-name"
│               └── value="imqbroker"
│
└── <clusters>
    └── <cluster name="mycluster">
        └── <jms-availability>
            ├── availability-enabled="true"
            └── master-broker="master.example.com:7676"
```

## 配置解析

### ActiveJmsResourceAdapter 使用

```java
// 从配置中获取 JMS 服务设置
public void initializeFromConfig(JmsService jmsService) {

    // 1. 获取 Broker 类型
    String type = jmsService.getType();
    // EMBEDDED, LOCAL, REMOTE, DISABLED

    // 2. 获取默认 JMS 主机
    String defaultHost = jmsService.getDefaultJmsHost();
    List<JmsHost> jmsHosts = jmsService.getJmsHost();

    // 3. 配置连接属性
    String reconnectEnabled = jmsService.getReconnectEnabled();
    String reconnectInterval = jmsService.getReconnectIntervalInSeconds();
    String reconnectAttempts = jmsService.getReconnectAttempts();

    // 4. 配置地址列表行为
    String addresslistBehavior = jmsService.getAddresslistBehavior();
    String addresslistIterations = jmsService.getAddresslistIterations();

    // 5. 获取 MQ 方案和服务
    String mqScheme = jmsService.getMqScheme();
    String mqService = jmsService.getMqService();

    // 6. 配置主机属性
    for (JmsHost jmsHost : jmsHosts) {
        String host = jmsHost.getHost();
        String port = jmsHost.getPort();
        String adminUser = jmsHost.getAdminUserName();
        String adminPassword = jmsHost.getAdminPassword();
        String lazyInit = jmsHost.getLazyInit();

        // 创建 JmsHostWrapper
        JmsHostWrapper wrapper = new JmsHostWrapper(
            jmsHost.getName(), host, port,
            adminUser, adminPassword, lazyInit);

        // 添加到地址列表
        addressList.add(wrapper);
    }
}
```

## REST 端点

### JMSHost REST 重定向

```java
@RestRedirects({
    @RestRedirect(
        opType = RestRedirect.OpType.POST,
        commandName = "create-jms-host"
    ),
    @RestRedirect(
        opType = RestRedirect.OpType.DELETE,
        commandName = "delete-jms-host"
    )
})
public interface JmsHost {
    // REST 调用自动重定向到对应命令
}
```

### REST API 使用

```bash
# 创建 JMS 主机
POST http://localhost:4848/management/domain/resources/create-jms-host
{
    "name": "my-jms-host",
    "host": "localhost",
    "port": "7676"
}

# 删除 JMS 主机
DELETE http://localhost:4848/management/domain/resources/jms-host/my-jms-host

# 更新 JMS 服务配置
POST http://localhost:4848/management/domain/configs/config/server-config/jms-service
{
    "type": "EMBEDDED",
    "init-timeout-in-seconds": "60"
}
```

## 配置验证

### 端口验证

```java
@Pattern(regexp=PORT_PATTERN,
         message="{port-pattern}",
         payload=JmsHost.class)
String getPort();

// PORT_PATTERN 支持:
// 1. 字面端口号: 7676, 8080
// 2. 系统属性: ${JMS_PORT}, ${system.property}
// 3. 范围: 1-65535
```

### 类型验证

```java
@Pattern(regexp="(LOCAL|EMBEDDED|REMOTE|DISABLED)")
String getType();

// 只允许以下值:
// - LOCAL
// - EMBEDDED
// - REMOTE
// - DISABLED
```

## 配置示例

### EMBEDDED Broker

```xml
<jms-service type="EMBEDDED" init-timeout-in-seconds="60">
    <jms-host name="default_JMS_host"
              host="localhost"
              port="7676"
              lazy-init="true"
              admin-user-name="admin"
              admin-password="admin"/>
</jms-service>
```

### LOCAL Broker

```xml
<jms-service type="LOCAL" init-timeout-in-seconds="60">
    <jms-host name="default_JMS_host"
              host="localhost"
              port="7676"
              lazy-init="false"
              admin-user-name="admin"
              admin-password="admin"/>
</jms-service>
```

### REMOTE Broker

```xml
<jms-service type="REMOTE">
    <jms-host name="remote_broker"
              host="broker.example.com"
              port="7676"
              admin-user-name="admin"
              admin-password="admin"/>
</jms-service>
```

### 集群配置

```xml
<clusters>
    <cluster name="mycluster">
        <config name="mycluster-config">
            <jms-availability>
                <availability-enabled>true</availability-enabled>
                <master-broker>master.example.com:7676</master-broker>
            </jms-availability>
            <jms-service type="EMBEDDED">
                <jms-host name="default_JMS_host" port="7676"/>
            </jms-service>
        </config>
    </cluster>
</clusters>
```

### 自定义属性

```xml
<jms-service type="EMBEDDED">
    <property name="instance-name" value="myBroker"/>
    <property name="instance-name-suffix" value="prod"/>
    <property name="append-version" value="true"/>
    <property name="user-name" value="jmsUser"/>
    <property name="password" value="${ALIAS=jmsPasswordAlias}"/>
</jms-service>
```

## 相关模块

- `jms-core` - JMS 资源适配器核心实现
- `admin` - CLI 管理命令
- `nucleus/admin/config-api` - 配置 API 基础设施
- `nucleus/hk2` - 依赖注入和配置管理
