# JMS Core - 核心 JMS 实现架构

## 概述

`jms-core` 模块提供 JMS 2.0/2.1 的核心实现，包括资源适配器、Broker 生命周期管理和 MDB 容器集成。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                   ActiveJmsResourceAdapter                      │
│                   (活动 JMS 资源适配器)                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  ManagedConnectionFactory                               │ │
│  │  ├─→ JMSRAConnectionFactory                            │ │
│  │  ├─→ JMSRAManagedConnectionFactory                     │ │
│  │  └─→ ConnectionURL/AddressList 配置                     │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  ActivationSpec (for MDBs)                             │ │
│  │  ├─→ JMSRAActivationSpec                               │ │
│  │  ├─→ Destination 配置                                  │ │
│  │  ├─→ SubscriptionName                                │ │
│  │  ├─→ ClientID                                         │
│  │  └─→ 消息确认模式                                       │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Broker 生命周期管理                                    │ │
│  │  ├─→ startBroker()                                     │ │
│  │  ├─→ stopBroker()                                      │ │
│  │  └──→ 配置 JMX                                          │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      JMS Broker Types                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │EMBEDDED  │  │  LOCAL   │  │ REMOTE   │  │ DIRECT   │  │
│  │(内嵌)   │  │(本地)   │  │(远程)   │  │(直连)   │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## ActiveJmsResourceAdapter

### 核心职责

```java
@Service
@Singleton
@Named(ActiveJmsResourceAdapter.JMS_SERVICE)
public class ActiveJmsResourceAdapter
        extends ActiveInboundResourceAdapterImpl
        implements LazyServiceInitializer, PostConstruct {

    // RA JavaBean 属性
    public static final String CONNECTION_URL = "ConnectionURL";
    public static final String RECONNECTENABLED = "ReconnectEnabled";
    public static final String RECONNECTINTERVAL = "ReconnectInterval";
    public static final String RECONNECTATTEMPTS = "ReconnectAttempts";

    // 生命周期 RA 属性
    public static final String BROKERTYPE = "BrokerType";
    public static final String BROKERINSTANCENAME = "BrokerInstanceName";
    public static final String BROKERBINDADDRESS = "BrokerBindAddress";
    public static final String BROKERPORT = "BrokerPort";
    public static final String BROKERARGS = "BrokerArgs";
    public static final String ADMINUSERNAME = "AdminUsername";
    public static final String ADMINPASSWORD = "AdminPassword";
    public static final String MASTERBROKER = "MasterBroker";

    // MDB 配置属性
    public static final String DESTINATION = "destination";
    public static final String DESTINATION_TYPE = "destinationType";
    public static final String SUBSCRIPTION_NAME = "SubscriptionName";
    public static final String CLIENT_ID = "ClientID";
    public static final String MAXPOOLSIZE = "EndpointPoolMaxSize";
    public static final String MINPOOLSIZE = "EndpointPoolSteadySize";
    public static final String ADDRESSLIST = "AddressList";
}
```

### Broker 类型

```java
// 生命周期属性
public static final String EMBEDDED = "EMBEDDED";  // 内嵌 - 在同一 JVM 中运行
public static final String LOCAL = "LOCAL";          // 本地 - 独立进程，由服务器管理
public static final String REMOTE = "REMOTE";        // 远程 - 外部 Broker
public static final String DIRECT = "DIRECT";        // 直连 - 无 Broker
public static final String DISABLED = "DISABLED";    // 禁用 - JMS 禁用
```

### Broker 启动流程

```
JmsProviderLifecycle.postConstruct()
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│  检查是否需要立即启动                                       │
│  ├─→ BrokerType != DISABLED                                  │
│  ├─→ !lazyInit (对于 EMBEDDED)                                │
│  ├─→ LOCAL 类型总是立即启动                                    │
│  └─→ 检查系统属性 JMS_INITIALIZE_ON_DEMAND                 │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  initializeBroker()                                           │
│  ├─→ 创建 ActiveJmsResourceAdapter                             │ │
│  ├─→ 配置连接池属性                                           │
│  ├─→ 启动 Broker (EMBEDDED/LOCAL)                              │
│  ├─→ 配置 JMX                                                  │
│  └─→ 注册资源适配器                                           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  initializeLazyListener()                                     │
│  ├─→ 创建 Grizzly 网络代理                                   │
│  ├── 注册延迟初始化监听器                                     │
│  └── 首次连接时启动 Broker                                    │
└─────────────────────────────────────────────────────────────────┘
```

## Lazy Initialization (延迟初始化)

### Grizzly 网络代理

```java
public static final String GRIZZLY_PROXY_PREFIX = "JMS_PROXY_";

/**
 * 创建 Grizzly 网络代理用于延迟初始化
 * 当第一个客户端连接时才启动实际的 Broker
 */
public void initializeLazyListener(JmsService jmsService)
        throws JmsInitialisationException {

    // 1. 检查是否需要延迟初始化
    JmsHost defaultJmsHost = getDefaultJmsHost(jmsService);
    if (defaultJmsmsHost != null &&
        Boolean.parseBoolean(defaultJmsHost.getLazyInit())) {

        // 2. 创建 Grizzly 网络代理
        createGrizzlyProxy(defaultJmsHost);

        // 3. 注册到 Grizzly 服务
        GrizzlyService grizzlyService = habitat.getService(GrizzlyService.class);
        grizzlyService.createNetworkProxy(proxy);
    }
}

/**
 * Grizzly 代理工作流程：
 * 1. 接收客户端连接
 * 2. 启动 OpenMQ Broker (如果未启动)
 * 3. 转发连接到实际 Broker 端口
 * 4. 后续连接直接使用实际端口
 */
private void createGrizzlyProxy(JmsHost jmsHost) {
    String proxyName = GRIZZLY_PROXY_PREFIX + jmsHost.getName();
    int proxyPort = jmsHost.getPort();

    // 创建网络监听器
    NetworkListener listener = new NetworkListener();
    listener.setName(proxyName);
    listener.setPort(proxyPort);
    listener.setProtocol("jms-proxy");

    // 配置代理处理器
    listener.setJmxPrincipal(false);
    listener.setSecure(false);

    grizzlyService.createNetworkProxy(listener);
}
```

## ManagedConnectionFactory

### JMSRAConnectionFactory

```java
/**
 * JMS 资源适配器的 ManagedConnectionFactory
 * 负责创建和管理 JMS 连接
 */
public class JMSRAConnectionFactory
        extends ManagedConnectionFactoryImpl {

    /**
     * 创建 JMS 连接
     */
    @Override
    public Object createConnectionFactory(ConnectionManager cxManager) {
        // 1. 解析连接 URL
        MQAddressList addressList = parseConnectionURL(connectionURL);

        // 2. 创建 OpenMQ 连接工厂
        com.sun.messaging.ConnectionFactory mqCf =
            new com.sun.messaging.ConnectionFactory();

        // 3. 配置连接属性
        mqCf.setProperty(com.sun.messaging.ConnectionConfiguration.imqAddressList,
                          addressList.toString());

        // 4. 配置重连属性
        if (reconnectEnabled) {
            mqCf.setProperty(com.sun.messaging.ConnectionConfiguration.imqReconnectEnabled,
                              "true");
            mqCf.setProperty(com.sun.messaging.ConnectionConfiguration.imqReconnectInterval,
                              String.valueOf(reconnectInterval));
            mqCf.setProperty(com.sun.messaging.ConnectionConfiguration.imqReconnectAttempts,
                              String.valueOf(reconnectAttempts));
        }

        // 5. 包装为 DataSource
        return new JMSDataSource(mqCf, cxManager);
    }
}
```

### Connection URL 配置

```
Connection URL 格式:
mq://[host]:[port][/destName]?property=value

示例:
1. mq://localhost:7676                     // 基本连接
2. mq://localhost:7676/QueueA               // 指定目标
3. mq://localhost:7676?imqReconnect=true    // 启用重连
4. mq://host1:7676,host2:7676               // 集群地址列表

AddressList 行为:
- PRIORITY: 按优先级选择 (默认)
- RANDOM: 随机选择
- ROUNDROBIN: 轮询
```

## ActivationSpec (MDB 集成)

### JMSRAActivationSpec

```java
/**
 * MDB 激活配置
 * 将 @MessageDriven 配置映射到 JMS RA 属性
 */
public class JMSRAActivationSpec extends ActivationSpec {

    // 目标配置
    private String destination;
    private String destinationType;  // javax.jms.Queue 或 javax.jms.Topic

    // 订阅配置
    private String subscriptionName;    // 持久订阅名称
    private String clientId;            // 客户端标识符

    // 消息确认
    private String acknowledgeMode;      // Auto-acknowledge, Dups-ok-acknowledge

    // 端点池配置
    private String maxPoolSize;         // 最大端点数
    private String minPoolSize;         // 最小端点数
    private String resizeCount;          // 调整数量
    private String resizeTimeout;        // 调整超时

    // 重投递配置
    private String redeliveryCount;      // 异常重投递次数

    // 地址列表配置
    private String addressList;         // Broker 地址列表
    private String addressListBehaviour; // 地址列表行为

    /**
     * 从 @MessageDriven 创建 ActivationSpec
     */
    public void setupFromMessageBean(EjbMessageBeanDescriptor mdb) {
        // 1. 获取 activation-config 属性
        Set<NameValuePair> props = mdb.getActivationConfigValue();

        // 2. 映射到 ActivationSpec 属性
        for (NameValuePair prop : props) {
            String name = prop.getName();
            String value = prop.getValue();

            switch (name) {
                case "destinationLookup":
                    setDestination(value);
                    break;
                case "destinationType":
                    setDestinationType(value);
                    break;
                case "subscriptionName":
                    setSubscriptionName(value);
                    break;
                case "clientId":
                    setClientId(value);
                    break;
                case "acknowledgeMode":
                    setAcknowledgeMode(value);
                    break;
                case "maxSession":
                    setMaxPoolSize(value);
                    break;
                // ... 其他属性
            }
        }

        // 3. 设置默认值
        setDefaults();
    }
}
```

## MDB 容器集成

### MdbContainerProps

```java
/**
 * MDB 容器属性配置
 */
public class MdbContainerProps {

    /**
     * 创建 MDB 容器配置
     */
    public void createMDBContainer(EjbMessageBeanDescriptor mdb) {

        // 1. 创建 ActivationSpec
        ActivationSpec spec = createActivationSpec(mdb);

        // 2. 配置资源适配器
        String raName = "jmsra";
        ResourceAdapter ra = lookupResourceAdapter(raName);

        // 3. 配置端点池
        configureEndpointPool(spec);

        // 4. 创建 MDB 容器
        // (由 EJB 容器处理)
    }

    /**
     * 配置端点池大小
     * 根据 @MessageDriven 的 maxSession 属性
     */
    private void configureEndpointPool(JMSRAActivationSpec spec) {
        int maxPoolSize = parseMaxPoolSize(spec.getMaxPoolSize());
        int minPoolSize = parseMinPoolSize(spec.getMinPoolSize());

        // 设置端点池
        spec.setEndpointPoolMaxSize(maxPoolSize);
        spec.setEndpointPoolSteadySize(minPoolSize);
    }
}
```

## JMX 管理

### JMX 连接配置

```java
/**
 * 配置 JMX 连接器
 */
private void configureJMXConnector(JmsService jmsService) {

    // 1. 获取 JMX 服务 URL
    String jmxServiceURL = jmsService.getJmxConnectors();

    // 2. 配置 RMI 注册表
    int rmiRegistryPort = parseRmiRegistryPort(jmsService);
    boolean useExternalRmiRegistry =
        Boolean.parseBoolean(jmsService.getUseExternalRmiRegistry());

    // 3. 启动 RMI 注册表
    if (!useExternalRmiRegistry) {
        startRMIRegistry(rmiRegistryPort);
    }

    // 4. 配置 JMX 连接器环境
    String jmxConnectorEnv = jmsService.getJmxConnectorEnv();

    // 5. 启动 JMX 连接器
    startJMXConnector(jmxServiceURL, jmxConnectorEnv);
}

private void startRMIRegistry(int port) {
    try {
        java.rmi.registry.LocateRegistry.createRegistry(port);
    } catch (java.rmi.RemoteException e) {
        // 注册表已存在
    }
}
```

## 高可用性配置

### 集群模式

```java
private enum ClusterMode {
    ENHANCED,                      // 增强集群
    CONVENTIONAL_WITH_MASTER_BROKER,  // 传统集群 + 主 Broker
    CONVENTIONAL_OF_PEER_BROKERS      // 对等 Broker 集群
}

/**
 * 配置高可用性
 */
private void configureAvailability(JmsAvailability availability) {

    String clusterId = availability.getClusterId();
    String brokerId = availability.getBrokerId();

    switch (getClusterMode(availability)) {
        case ENHANCED:
            // 增强集群 - 使用 JDBC 存储
            configureEnhancedCluster(availability);
            break;

        case CONVENTIONAL_WITH_MASTER_BROKER:
            // 主 Broker 模式
            configureMasterBroker(availability.getMasterBroker());
            break;

        case CONVENTIONAL_OF_PEER_BROKERS:
            // 对等 Broker 集群
            configurePeerBrokerCluster(availability);
            break;
    }
}

/**
 * 配置 JDBC 集群存储
 */
private void configureEnhancedCluster(JmsAvailability availability) {
    // JDBC 属性前缀
    String DB_PROPS_PREFIX = "imq.persist.jdbc.";

    // 配置数据库连接
    String dbUrl = "jdbc:sun:hadb:" + availability.getDbName();

    Properties dbProps = new Properties();
    dbProps.put("hadb.driverClass", availability.getDriverClass());
    dbProps.put("hadb.user", availability.getUser());
    dbProps.put("hadb.password", availability.getPassword());
    dbProps.put("hadb.serverList", availability.getServerList());

    // 配置 JDBC 存储
    config.setProperty(DB_PROPS_PREFIX + "dbType", "hadb");
    config.setProperty(DB_PROPS_PREFIX + "jdbcUrl", dbUrl);
    config.setProperty(DB_PROPS_PREFIX + "dbProps", encodeProperties(dbProps));
}
```

## 相关模块

- `jms-handlers` - 注解处理器
- `gf-jms-connector` - 连接器运行时
- `gf-jms-injection` - CDI 注入支持
- `admin` - CLI 管理命令
- `appserver/connectors` - JCA 连接器运行时
- `appserver/ejb` - MDB 容器实现
