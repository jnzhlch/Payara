# JMS Admin - CLI 管理命令架构

## 概述

`admin` 模块提供 JMS 相关的 `asadmin` CLI 命令，用于管理 JMS 资源、主机和目标。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Admin Command Layer                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  JMS Resource Commands                                   │ │
│  │  ├─→ CreateJMSResource                                  │ │
│  │  ├─→ DeleteJMSResource                                  │ │
│  │  └─→ ListJMSResources                                   │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  JMS Host Commands                                      │ │
│  │  ├─→ CreateJMSHost                                      │ │
│  │  ├─→ DeleteJMSHost                                      │ │
│  │  └─→ ListJMSHosts                                       │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  JMS Destination Commands                               │ │
│  │  ├─→ CreateJMSDestination                               │ │
│  │  ├─→ DeleteJMSDestination                               │ │
│  │  ├─→ ListJMSDestinations                                │ │
│  │  └─→ FlushJMSDestination                                │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  JMS Management Commands                                │ │
│  │  ├─→ JMSPing                                            │ │
│  │  ├─→ ConfigureJMSCluster                                │ │
│  │  └─→ ChangeMasterBroker                                 │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Connector Runtime                           │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  create-connector-connection-pool                       │ │
│  │  create-connector-resource                               │ │
│  │  create-admin-object                                     │ │
│  │  delete-connector-connection-pool                       │ │
│  │  delete-connector-resource                               │ │
│  │  delete-admin-object                                     │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## CreateJMSResource

### 核心命令

```java
@Service(name="create-jms-resource")
@PerLookup
@I18n("create.jms.resource")
@ExecuteOn({RuntimeType.DAS})
@TargetType({
    CommandTarget.DAS,
    CommandTarget.STANDALONE_INSTANCE,
    CommandTarget.CLUSTER,
    CommandTarget.DOMAIN,
    CommandTarget.DEPLOYMENT_GROUP
})
@RestEndpoints({
    @RestEndpoint(
        configBean=Resources.class,
        opType=RestEndpoint.OpType.POST,
        path="create-jms-resource",
        description="create-jms-resource"
    )
})
public class CreateJMSResource implements AdminCommand {

    @Param(name="resType")
    String resourceType;

    @Param(name="jndi_name", primary=true)
    String jndiName;

    @Param(optional=true, defaultValue="true")
    Boolean enabled;

    @Param(name="property", optional=true, separator=':')
    Properties props;

    @Param(optional=true)
    String target = SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME;

    @Param(name="description", optional=true)
    String description;

    @Param(optional=true, defaultValue="false")
    Boolean force;
}
```

### 资源类型常量

```java
private static final String QUEUE = "jakarta.jms.Queue";
private static final String TOPIC = "jakarta.jms.Topic";
private static final String QUEUE_CF = "jakarta.jms.QueueConnectionFactory";
private static final String TOPIC_CF = "jakarta.jms.TopicConnectionFactory";
private static final String UNIFIED_CF = "jakarta.jms.ConnectionFactory";
private static final String DEFAULT_JMS_ADAPTER = "jmsra";
private static final String JNDINAME_APPENDER = "-Connection-Pool";
```

### 执行流程

```
create-jms-resource 命令
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│  验证参数                                                       │
│  ├─→ resType 是否有效?                                         │
│  ├─→ jndiName 是否提供?                                        │
│  └─→ 支持的类型: Queue, Topic, *ConnectionFactory           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  Force 模式处理                                                │
│  if (force) {                                                  │
│      ┌──────────────────────────────────────────────────────┐ │
│      │  检查资源是否存在                                      │ │
│      │  if (exists) {                                       │ │
│      │      delete-jms-resource                              │ │
│      │  }                                                    │ │
│      └──────────────────────────────────────────────────────┘ │
│  }                                                             │
└────────────────────────────┬────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
                    ▼                 ▼
             ConnectionFactory      Destination
                    │                 │
                    ▼                 ▼
┌─────────────────────────────────┐  ┌──────────────────────────┐
│  创建连接池                     │  │  创建管理对象           │
│  create-connector-connection-  │  │  create-admin-object     │
│  pool                           │  │                          │
│  ┌───────────────────────────┐ │  │  Name 属性              │
│  │  连接池名称                │ │  │  imqDestinationName    │
│  │  jndiName + "-Connection-  │ │  └──────────────────────────┘
│  │  Pool"                     │
│  └───────────────────────────┘ │
└──────────────┬──────────────────┘
               │
               ▼
┌─────────────────────────────────┐
│  创建连接资源                   │
│  create-connector-resource      │
│  ┌───────────────────────────┐ │
│  │  JNDI 名称                │ │
│  │  连接池引用               │ │
│  └───────────────────────────┘ │
└─────────────────────────────────┘
```

### 属性映射

```java
private void populateJmsRAMap() {
    mapping = new Hashtable();
    // MQ 属性到 RA 属性的映射
    mapping.put("imqDestinationName", "Name");
    mapping.put("imqDestinationDescription", "Description");
    mapping.put("imqConnectionURL", "ConnectionURL");
    mapping.put("imqDefaultUsername", "UserName");
    mapping.put("imqDefaultPassword", "Password");
    mapping.put("imqConfiguredClientID", "ClientId");
    mapping.put("imqAddressList", "AddressList");
    mapping.put("MessageServiceAddressList", "AddressList");
}

public String getMappedName(String key) {
    return (String) mapping.get(key);
}
```

### 连接池参数

```java
private ParameterMap populateConnectionPoolParameters() {
    ParameterMap parameters = new ParameterMap();

    // 默认连接池设置
    parameters.set("maxpoolsize", "250");
    parameters.set("steadypoolsize", "1");

    // 从属性中提取连接池配置
    if (props != null) {
        Enumeration keys = props.keys();
        while (keys.hasMoreElements()) {
            String propKey = (String) keys.nextElement();

            switch (propKey) {
                case "steady-pool-size":
                    parameters.set("steadypoolsize",
                        props.getProperty(propKey));
                    break;
                case "max-pool-size":
                    parameters.set("maxpoolsize",
                        props.getProperty(propKey));
                    break;
                case "pool-resize-quantity":
                    parameters.set("poolresize",
                        props.getProperty(propKey));
                    break;
                case "idle-timeout-in-seconds":
                    parameters.set("idletimeout",
                        props.getProperty(propKey));
                    break;
                case "max-wait-time-in-millis":
                    parameters.set("maxwait",
                        props.getProperty(propKey));
                    break;
                case "transaction-support":
                    parameters.set("transactionsupport",
                        props.getProperty(propKey));
                    break;
                default:
                    // 其他属性传递给 RA
                    tmpProps.setProperty(propKey,
                        props.getProperty(propKey));
                    break;
            }
        }
    }

    parameters.set(DEFAULT_OPERAND, jndiNameForConnectionPool);
    parameters.set("raname", DEFAULT_JMS_ADAPTER);
    parameters.set("connectiondefinition", resourceType);

    return parameters;
}
```

### 目标资源验证

```java
private Properties validateDestinationResourceProps(Properties props,
                                                   String jndiName)
        throws Exception {
    String providedDestinationName = null;

    if (props != null)
        providedDestinationName = getProvidedDestinationName(props);
    else
        props = new Properties();

    if (providedDestinationName != null) {
        // 验证用户提供的目标名称
        if (!isSyntaxValid(providedDestinationName)) {
            throw new Exception(
                "Destination Resource " + jndiName +
                " has an invalid destination name " +
                providedDestinationName);
        }
    } else {
        // 从 JNDI 名称计算有效的目标名称
        String newDestName = computeDestinationName(jndiName);
        props.put(NAME, newDestName);
    }

    return props;
}

private String computeDestinationName(String providedJndiName) {
    // 将 JNDI 名称中的无效标识符替换为下划线
    char[] jndiName = providedJndiName.toCharArray();
    char[] finalName = new char[jndiName.length];

    finalName[0] = Character.isJavaIdentifierStart(jndiName[0]) ?
        jndiName[0] : '_';

    for (int i = 1; i < jndiName.length; i++) {
        finalName[i] = Character.isJavaIdentifierPart(jndiName[i]) ?
            jndiName[i] : '_';
    }

    return new String(finalName);
}
```

## DeleteJMSResource

### 命令结构

```java
@Service(name="delete-jms-resource")
@PerLookup
@I18n("delete.jms.resource")
@ExecuteOn({RuntimeType.DAS})
@TargetType({
    CommandTarget.DAS,
    CommandTarget.STANDALONE_INSTANCE,
    CommandTarget.CLUSTER,
    CommandTarget.DOMAIN,
    CommandTarget.DEPLOYMENT_GROUP
})
public class DeleteJMSResource implements AdminCommand {

    @Param(primary=true)
    String jndiName;

    @Param(optional=true)
    String target = SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME;

    @Param(name="cascade", optional=true, defaultValue="false")
    Boolean cascade;
}
```

### 删除流程

```java
public void execute(AdminCommandContext context) {
    ActionReport report = context.getActionReport();

    // 1. 查找资源
    Resource res = getResource(jndiName);

    if (res == null) {
        report.setMessage("Resource not found");
        report.setActionExitCode(ActionReport.ExitCode.FAILURE);
        return;
    }

    // 2. 删除连接资源
    if (res instanceof ConnectorResource) {
        deleteConnectorResource(jndiName, target);

        // 3. 删除连接池 (如果 cascade=true)
        if (cascade) {
            String poolName =
                ((ConnectorResource) res).getPoolName();
            deleteConnectionPool(poolName, target);
        }
    }

    // 4. 删除管理对象
    else if (res instanceof AdminObjectResource) {
        deleteAdminObject(jndiName, target);
    }

    report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
}
```

## ListJMSResources

### 命令结构

```java
@Service(name="list-jms-resources")
@PerLookup
@I18n("list.jms.resources")
@ExecuteOn({RuntimeType.DAS})
@TargetType({
    CommandTarget.DAS,
    CommandTarget.STANDALONE_INSTANCE,
    CommandTarget.CLUSTER,
    CommandTarget.DEPLOYMENT_GROUP
})
public class ListJMSResources implements AdminCommand {

    @Param(optional=true)
    String target = SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME;
}
```

## CreateJMSHost

### 命令结构

```java
@Service(name="create-jms-host")
@PerLookup
@I18n("create.jms.host")
@ExecuteOn({RuntimeType.DAS})
@TargetType({
    CommandTarget.DAS,
    CommandTarget.STANDALONE_INSTANCE,
    CommandTarget.CLUSTER,
    CommandTarget.CONFIG
})
public class CreateJMSHost implements AdminCommand {

    @Param(name="jmsHost", primary=true)
    String jmsHostName;

    @Param(optional=true)
    String target = "server-config";

    @Param(optional=true, defaultValue="localhost")
    String mqhost;

    @Param(optional=true, defaultValue="7676")
    String mqport;

    @Param(optional=true, defaultValue="admin")
    String mquser;

    @Param(optional=true, defaultValue="admin")
    String mqpassword;
}
```

## CreateJMSDestination

### 命令结构

```java
@Service(name="create-jms-dest")
@PerLookup
@I18n("create.jms.dest")
@ExecuteOn({RuntimeType.DAS})
@TargetType({
    CommandTarget.DAS,
    CommandTarget.STANDALONE_INSTANCE,
    CommandTarget.CLUSTER
})
public class CreateJMSDestination implements AdminCommand {

    @Param(name="destName", primary=true)
    String destName;

    @Param(name="destType", acceptableValues="queue,topic")
    String destType;

    @Param(optional=true)
    String target = SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME;

    @Param(name="property", optional=true, separator=':')
    Properties props;

    @Param(optional=true, defaultValue="false")
    Boolean force;
}
```

### 物理目标创建

```java
public void execute(AdminCommandContext context) {
    ActionReport report = context.getActionReport();

    // 1. 获取 JMS 服务配置
    JmsService jmsService = getJmsService(target);

    // 2. 获取默认 JMS 主机
    JmsHost jmsHost = getDefaultJmsHost(jmsService);

    // 3. 创建 JMX 连接
    MQJMXConnectorInfo mqJMXConnectorInfo =
        createMQJMXConnector(jmsHost);

    // 4. 通过 JMX 创建物理目标
    MBeanServerConnection mbsc =
        mqJMXConnectorInfo.getMBeanServerConnection();

    ObjectName destManagerName =
        new ObjectName("com.sun.messaging.jms.server:type=Destination," +
                      "desttype=" + destType + ",name=" + destName);

    // 调用 MBean 操作创建目标
    mbsc.invoke(destManagerName, "create", null, null);

    report.setMessage("JMS Destination " + destName + " created.");
    report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
}
```

## FlushJMSDestination

### 命令结构

```java
@Service(name="flush-jms-dest")
@PerLookup
@I18n("flush.jms.dest")
@ExecuteOn({RuntimeType.DAS})
@TargetType({
    CommandTarget.DAS,
    CommandTarget.STANDALONE_INSTANCE,
    CommandTarget.CLUSTER
})
public class FlushJMSDestination implements AdminCommand {

    @Param(name="destName", primary=true)
    String destName;

    @Param(name="destType", acceptableValues="queue,topic")
    String destType;

    @Param(optional=true)
    String target = SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME;
}
```

## JMSPing

### 命令结构

```java
@Service(name="jms-ping")
@PerLookup
@I18n("jms.ping")
@ExecuteOn({RuntimeType.DAS, RuntimeType.INSTANCE})
@TargetType({
    CommandTarget.DAS,
    CommandTarget.STANDALONE_INSTANCE,
    CommandTarget.CLUSTER
})
public class JMSPing implements AdminCommand {

    @Param(optional=true)
    String target = SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME;
}
```

## ChangeMasterBroker

### 命令结构

```java
@Service(name="change-master-broker")
@PerLookup
@I18n("change.master.broker")
@ExecuteOn({RuntimeType.DAS})
@TargetType({
    CommandTarget.DAS,
    CommandTarget.STANDALONE_INSTANCE,
    CommandTarget.CLUSTER
})
public class ChangeMasterBrokerCommand implements AdminCommand {

    @Param(primary=true)
    String newMasterBroker;

    @Param(optional=true)
    String target = SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME;
}
```

## ConfigureJMSCluster

### 命令结构

```java
@Service(name="configure-jms-cluster")
@PerLookup
@I18n("configure.jms.cluster")
@ExecuteOn({RuntimeType.DAS})
@TargetType({CommandTarget.CLUSTER})
public class ConfigureJMSCluster implements AdminCommand {

    @Param(name="clustername", primary=true)
    String clusterName;

    @Param(optional=true, defaultValue="EMBEDDED")
    String jmsType;

    @Param(name="jmsintegrationtype", optional=true, defaultValue="embedded")
    String jmsIntegrationType;

    @Param(optional=true)
    String configstoretype;

    @Param(name="dbvendor", optional=true)
    String dbVendor;

    @Param(name="dburl", optional=true)
    String dbUrl;

    @Param(name="dbuser", optional=true)
    String dbUser;

    @Param(name="dbpassword", optional=true)
    String dbPassword;

    @Param(name="clustername", optional=true)
    String masterBroker;

    @Param(name="property", optional=true, separator=':')
    Properties properties;
}
```

## 命令示例

### 创建 JMS 资源

```bash
# 创建连接工厂
asadmin create-jms-resource \
    --restype jakarta.jms.ConnectionFactory \
    --property imqAddressList=mq://localhost:7676 \
    jms/MyConnectionFactory

# 创建队列
asadmin create-jms-resource \
    --restype jakarta.jms.Queue \
    --property Name=MyPhysicalQueue \
    jms/MyQueue

# 创建主题
asadmin create-jms-resource \
    --restype jakarta.jms.Topic \
    --property Name=MyPhysicalTopic \
    jms/MyTopic

# 创建带连接池设置的连接工厂
asadmin create-jms-resource \
    --restype jakarta.jms.ConnectionFactory \
    --property \
        imqAddressList=mq://localhost:7676:\
        steady-pool-size=5:\
        max-pool-size=30:\
        transaction-support=XATransaction \
    jms/MyPooledConnectionFactory
```

### 删除 JMS 资源

```bash
# 删除资源 (不删除连接池)
asadmin delete-jms-resource jms/MyConnectionFactory

# 删除资源和连接池
asadmin delete-jms-resource --cascade=true jms/MyConnectionFactory
```

### 列出资源

```bash
# 列出所有 JMS 资源
asadmin list-jms-resources

# 列出特定目标的资源
asadmin list-jms-resources --target mycluster
```

### 管理 JMS 主机

```bash
# 创建 JMS 主机
asadmin create-jms-host \
    --mqhost broker.example.com \
    --mqport 7676 \
    --mquser admin \
    --mqpassword admin \
    remote-host

# 删除 JMS 主机
asadmin delete-jms-host remote-host

# 列出 JMS 主机
asadmin list-jms-hosts
```

### 管理目标

```bash
# 创建队列
asadmin create-jms-dest \
    --desttype queue \
    MyQueue

# 创建主题
asadmin create-jms-dest \
    --desttype topic \
    MyTopic

# 列出目标
asadmin list-jms-dests

# 删除目标
asadmin delete-jms-dest \
    --desttype queue \
    MyQueue

# 清空队列
asadmin flush-jms-dest \
    --desttype queue \
    MyQueue
```

### JMS 管理命令

```bash
# Ping JMS 服务
asadmin jms-ping

# 更改主 Broker
asadmin change-master-broker \
    new-master.example.com:7676

# 配置 JMS 集群
asadmin configure-jms-cluster \
    --clustername mycluster \
    --jmstype EMBEDDED \
    --configstoretype jdbc \
    --dbvendor mysql \
    --dburl jdbc:mysql://localhost:3306/jmsdb \
    --dbuser jmsuser \
    --dbpassword jmspass \
    --masterbroker master.example.com:7676
```

## 目标类型

| 目标类型 | 说明 |
|---------|------|
| `DAS` | 域管理服务器 |
| `STANDALONE_INSTANCE` | 独立实例 |
| `CLUSTER` | 集群 |
| `DOMAIN` | 域 |
| `DEPLOYMENT_GROUP` | 部署组 |
| `CONFIG` | 配置 |

## 相关模块

- `jms-core` - JMS 资源适配器核心
- `gf-jms-connector` - JMS 连接器配置
- `nucleus/admin/command-framework` - 命令框架
- `nucleus/admin/config-api` - 配置 API
