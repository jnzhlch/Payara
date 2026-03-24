# JDBC Runtime - 部署与监控架构

## 概述

`jdbc-runtime` 模块负责 JDBC 资源和连接池的部署、运行时管理以及监控功能。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      Resource Deployers                         │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  JdbcConnectionPoolDeployer                              │ │
│  │  - 部署/取消部署连接池                                    │ │
│  │  - 动态重新配置                                          │ │
│  │  - 透明动态重新配置 (Transparent Dynamic Reconfig)       │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  JdbcResourceDeployer                                   │ │
│  │  - 部署/取消部署 JDBC 资源                               │ │
│  │  - 创建 __PM 资源                                       │ │
│  │  - 自动连接池管理                                       │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  DataSourceDefinitionDeployer                           │ │
│  │  - 处理 @DataSourceDefinition 注解                      │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Connector Runtime                          │
│                      (连接器运行时)                              │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Monitoring Layer                           │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  JdbcConnPoolProbeProvider                               │ │
│  │  - 连接生命周期事件探针                                   │ │
│  │  - 连接池统计事件探针                                     │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  JdbcConnPoolStatsProvider                               │ │
│  │  - JMX 统计数据提供者                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## JdbcConnectionPoolDeployer

### 部署流程

```
创建连接池请求
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│  deployResource(JdbcConnectionPool)                             │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  createConnectorConnectionPool(adminPool)                │ │
│  │  ├─→ 解析连接池属性                                    │ │
│  │  ├─→ 创建 ConnectorConnectionPool                       │ │
│  │  ├─→ 配置 MCF 属性                                      │ │
│  │  └─→ 注册到 ConnectorRuntime                            │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  注册透明动态重新配置                                          │
│  registerTransparentDynamicReconfigPool()                      │
└─────────────────────────────────────────────────────────────────┘
```

### 核心方法

```java
@Service
@ResourceDeployerInfo(JdbcConnectionPool.class)
public class JdbcConnectionPoolDeployer implements ResourceDeployer {

    // 部署连接池
    public void actualDeployResource(Object resource, PoolInfo poolInfo) {
        JdbcConnectionPool adminPool = (JdbcConnectionPool) resource;

        // 1. 创建 ConnectorConnectionPool
        ConnectorConnectionPool connConnPool =
            createConnectorConnectionPool(adminPool, poolInfo);

        // 2. 注册透明动态重新配置
        registerTransparentDynamicReconfigPool(poolInfo, adminPool);

        // 3. 创建连接池
        runtime.createConnectorConnectionPool(connConnPool);
    }

    // 创建 ConnectorConnectionPool
    public ConnectorConnectionPool createConnectorConnectionPool(
            JdbcConnectionPool adminPool, PoolInfo poolInfo) {

        // 获取 RA 名称 (jdbc-ra, jdbc-xa-ra)
        String moduleName = getRANameofJdbcConnectionPool(adminPool);

        // 获取事务支持级别
        int txSupport = getTxSupport(moduleName);

        // 创建连接池对象
        ConnectorConnectionPool conConnPool = new ConnectorConnectionPool(poolInfo);

        // 设置连接池属性
        setConnectorConnectionPoolAttributes(conConnPool, adminPool);

        // 配置 MCF 属性
        ConnectorConfigProperty[] mcfProps =
            getMCFConfigProperties(adminPool, conConnPool, connDesc);

        return conConnPool;
    }
}
```

### MCF 配置属性

```java
private ConnectorConfigProperty[] getMCFConfigProperties(
        JdbcConnectionPool adminPool,
        ConnectorConnectionPool conConnPool,
        ConnectorDescriptor connDesc) {

    // 基本属性
    propList.add(new ConnectorConfigProperty("ClassName",
        adminPool.getDatasourceClassname()));

    // 连接验证属性
    propList.add(new ConnectorConfigProperty("ConnectionValidationRequired",
        adminPool.getIsConnectionValidationRequired()));
    propList.add(new ConnectorConfigProperty("ValidationMethod",
        adminPool.getConnectionValidationMethod()));
    propList.add(new ConnectorConfigProperty("ValidationTableName",
        adminPool.getValidationTableName()));
    propList.add(new ConnectorConfigProperty("ValidationClassName",
        adminPool.getValidationClassname()));

    // 事务属性
    propList.add(new ConnectorConfigProperty("TransactionIsolation",
        adminPool.getTransactionIsolationLevel()));
    propList.add(new ConnectorConfigProperty("GuaranteeIsolationLevel",
        adminPool.getIsIsolationLevelGuaranteed()));

    // 语句缓存属性
    propList.add(new ConnectorConfigProperty("StatementCacheSize",
        adminPool.getStatementCacheSize()));
    propList.add(new ConnectorConfigProperty("StatementCacheType",
        adminPool.getStatementCacheType()));

    // 语句泄漏检测属性
    propList.add(new ConnectorConfigProperty("StatementLeakTimeoutInSeconds",
        adminPool.getStatementLeakTimeoutInSeconds()));
    propList.add(new ConnectorConfigProperty("StatementLeakReclaim",
        adminPool.getStatementLeakReclaim()));

    // SQL 跟踪属性
    propList.add(new ConnectorConfigProperty("SqlTraceListeners",
        adminPool.getSqlTraceListeners()));
    propList.add(new ConnectorConfigProperty("SlowQueryThresholdInSeconds",
        adminPool.getSlowQueryThresholdInSeconds()));

    // 其他属性
    propList.add(new ConnectorConfigProperty("StatementWrapping",
        adminPool.getWrapJdbcObjects()));
    propList.add(new ConnectorConfigProperty("LogJdbcCalls",
        adminPool.getLogJdbcCalls()));
    propList.add(new ConnectorConfigProperty("StatementTimeout",
        adminPool.getStatementTimeoutInSeconds()));
    propList.add(new ConnectorConfigProperty("InitSql",
        adminPool.getInitSql()));

    return propList.toArray(new ConnectorConfigProperty[propList.size()]);
}
```

### 动态重新配置

```java
public synchronized void redeployResource(Object resource) throws Exception {
    final JdbcConnectionPool adminPool = (JdbcConnectionPool) resource;
    PoolInfo poolInfo = ConnectorsUtil.getPoolInfo(adminPool);

    // 创建新的连接池配置
    final ConnectorConnectionPool connConnPool =
        createConnectorConnectionPool(adminPool, poolInfo);

    // 定义排除属性 (这些属性变化不需要重新创建连接池)
    Set<String> excludes = new HashSet<>();
    excludes.add("TransactionIsolation");
    excludes.add("GuaranteeIsolationLevel");
    excludes.add("ValidationTableName");
    excludes.add("ConnectionValidationRequired");
    excludes.add("ValidationMethod");
    excludes.add("StatementWrapping");
    excludes.add("StatementTimeout");
    excludes.add("ValidationClassName");
    excludes.add("StatementCacheSize");
    excludes.add("StatementCacheType");
    excludes.add("StatementLeakTimeoutInSeconds");
    excludes.add("StatementLeakReclaim");

    // 重新配置连接池
    boolean requirePoolRecreation =
        runtime.reconfigureConnectorConnectionPool(connConnPool, excludes);

    if (requirePoolRecreation) {
        handlePoolRecreation(connConnPool);
    }
}
```

### 透明动态重新配置流程

```
重新配置请求
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│  检查是否需要重新创建连接池                                     │
│  runtime.reconfigureConnectorConnectionPool(connConnPool, excludes)│
└────────────────────────────┬────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
                    ▼                 ▼
             需要重新创建        不需要重新创建
                    │                 │
                    ▼                 ▼
┌─────────────────────────────────┐   直接更新属性
│  检查动态重新配置等待超时       │
│  (Dynamic Reconfig Wait Timeout)│
└──────────────┬──────────────────┘
               │
       ┌───────┴────────┐
       │                │
       ▼                ▼
  启用超时         未启用/超时=0
       │                │
       ▼                ▼
┌─────────────────────┐   立即重新创建
│ 阻塞新请求          │
│ 等待现有连接释放   │
│ blockRequests()    │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ 轮询等待空闲       │
│ 等待队列为空       │
│ numConnUsed = 0    │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ 更新资源工厂版本   │
│ updateResourceInfoVersion() │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ 重新创建连接池     │
│ recreatePool()     │
└─────────────────────┘
```

## JdbcResourceDeployer

### 部署流程

```java
@Service
@ResourceDeployerInfo(JdbcResource.class)
public class JdbcResourceDeployer implements ResourceDeployer {

    public synchronized void deployResource(Object resource,
                                          String applicationName,
                                          String moduleName) {
        JdbcResource jdbcRes = (JdbcResource) resource;
        String jndiName = jdbcRes.getJndiName();
        String poolName = jdbcRes.getPoolName();

        PoolInfo poolInfo = new PoolInfo(poolName, applicationName, moduleName);
        ResourceInfo resourceInfo = new ResourceInfo(jndiName, applicationName, moduleName);

        // 创建连接器资源
        runtime.createConnectorResource(resourceInfo, poolInfo, null);

        // 创建 __PM 资源 (Pool Managed)
        if (ConnectorsUtil.getValidSuffix(jndiName) == null) {
            ResourceInfo pmResourceInfo = new ResourceInfo(
                ConnectorsUtil.getPMJndiName(jndiName),
                applicationName, moduleName);
            runtime.createConnectorResource(pmResourceInfo, poolInfo, null);
        }
    }
}
```

### PM 资源

```
JDBC 资源: jdbc/MyDataSource
       │
       ├─→ jdbc/MyDataSource (正常资源)
       │   └─→ 支持事务管理
       │
       └─→ jdbc/MyDataSource__PM (Pool Managed 资源)
           └─→ 不参与事务管理
```

### 取消部署流程

```java
private void deleteResource(JdbcResource jdbcResource, ResourceInfo resourceInfo) {
    // 删除资源
    runtime.deleteConnectorResource(resourceInfo);
    ConnectorRegistry.getInstance().removeResourceFactories(resourceInfo);

    // 删除 __PM 资源
    if (ConnectorsUtil.getValidSuffix(resourceInfo.getName()) == null) {
        String pmJndiName = ConnectorsUtil.getPMJndiName(resourceInfo.getName());
        ResourceInfo pmResourceInfo = new ResourceInfo(pmJndiName, ...);
        runtime.deleteConnectorResource(pmResourceInfo);
        ConnectorRegistry.getInstance().removeResourceFactories(pmResourceInfo);
    }

    // 检查并删除连接池 (如果没有资源引用)
    checkAndDeletePool(jdbcResource);
}

private void checkAndDeletePool(JdbcResource cr) {
    String poolName = cr.getPoolName();
    ResourceInfo resourceInfo = ConnectorsUtil.getResourceInfo(cr);
    PoolInfo poolInfo = new PoolInfo(poolName, ...);

    // 检查连接池是否仍在使用
    boolean poolReferred = isJdbcPoolReferredInServerInstance(poolInfo);

    if (!poolReferred) {
        // 删除连接池
        JdbcConnectionPool jcp = (JdbcConnectionPool)
            ConnectorsUtil.getResourceByName(resources, JdbcConnectionPool.class, poolName);
        runtime.getResourceDeployer(jcp).undeployResource(jcp);
    }
}
```

## 监控架构

### 探针事件

```java
@ProbeProvider(moduleProviderName="glassfish",
               moduleName="jdbc",
               probeProviderName="connection-pool")
public class JdbcConnPoolProbeProvider {

    // 连接创建事件
    @Probe(name="connectionCreatedEvent")
    public void connectionCreatedEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName);

    // 连接获取事件
    @Probe(name="connectionAcquiredEvent")
    public void connectionAcquiredEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName);

    // 连接释放事件
    @Probe(name="connectionReleasedEvent")
    public void connectionReleasedEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName);

    // 连接销毁事件
    @Probe(name="connectionDestroyedEvent")
    public void connectionDestroyedEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName);

    // 连接验证失败事件
    @Probe(name="connectionValidationFailedEvent")
    public void connectionValidationFailedEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName,
        @ProbeParam("increment") int increment);

    // 连接超时事件
    @Probe(name="connectionTimedOutEvent")
    public void connectionTimedOutEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName);

    // 潜在连接泄漏事件
    @Probe(name="potentialConnLeakEvent")
    public void potentialConnLeakEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName);

    // 连接使用事件
    @Probe(name="connectionUsedEvent")
    public void connectionUsedEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName);

    // 连接请求服务事件
    @Probe(name="connectionRequestServedEvent")
    public void connectionRequestServedEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName,
        @ProbeParam("timeTakenInMillis") long timeTakenInMillis);

    // 空闲连接增加事件
    @Probe(name="incrementNumConnFreeEvent")
    public void incrementNumConnFreeEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName,
        @ProbeParam("beingDestroyed") boolean beingDestroyed,
        @ProbeParam("steadyPoolSize") int steadyPoolSize);

    // 空闲连接减少事件
    @Probe(name="decrementNumConnFreeEvent")
    public void decrementNumConnFreeEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName);

    // 连接匹配事件
    @Probe(name="connectionMatchedEvent")
    public void connectionMatchedEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName);

    // 连接不匹配事件
    @Probe(name="connectionNotMatchedEvent")
    public void connectionNotMatchedEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName);

    // 连接请求排队事件
    @Probe(name="connectionRequestQueuedEvent")
    public void connectionRequestQueuedEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName);

    // 连接请求出队事件
    @Probe(name="connectionRequestDequeuedEvent")
    public void connectionRequestDequeuedEvent(
        @ProbeParam("poolName") String poolName,
        @ProbeParam("appName") String appName,
        @ProbeParam("moduleName") String moduleName);
}
```

### 统计数据

```
连接池统计
├── numConnUsed          // 当前使用的连接数
├── numConnFree          // 当前空闲的连接数
├── numConnFailed        // 失败的连接数
├── numConnTimedOut      // 超时的连接数
├── numConnValidationFailed   // 验证失败的连接数
├── connRequestWaitTime       // 请求等待时间
├── averageConnWaitTime       // 平均等待时间
├── averageConnUsageTime      // 平均使用时间
├── steadyPoolSize            // 稳态池大小
└── maxPoolSize               // 最大池大小
```

## DataSourceDefinitionDeployer

### 注解处理

```java
@Service
public class DataSourceDefinitionDeployer {

    // 处理 @DataSourceDefinition 注解
    public void deploy(DataSourceDefinition dsd,
                      String applicationName,
                      String moduleName) {

        // 创建 JdbcConnectionPool 配置
        JdbcConnectionPool pool = new JdbcConnectionPool();
        pool.setName(dsd.name() + "-pool");
        pool.setDatasourceClassname(dsd.className());
        pool.setMaxPoolSize(String.valueOf(dsd.maxPoolSize()));
        pool.setSteadyPoolSize(String.valueOf(dsd.minPoolSize()));
        // ... 其他属性

        // 创建 JdbcResource 配置
        JdbcResource resource = new JdbcResource();
        resource.setJndiName(dsd.name());
        resource.setPoolName(pool.getName());

        // 部署连接池
        JdbcConnectionPoolDeployer poolDeployer =
            habitat.getService(JdbcConnectionPoolDeployer.class);
        poolDeployer.deployResource(pool, applicationName, moduleName);

        // 部署资源
        this.deployResource(resource, applicationName, moduleName);
    }
}
```

### 注解示例

```java
@DataSourceDefinition(
    name = "java:global/jdbc/MyDataSource",
    className = "org.postgresql.xa.PGXADataSource",
    user = "myuser",
    password = "mypassword",
    databaseName = "mydb",
    serverName = "localhost",
    portNumber = 5432,
    initialPoolSize = 2,
    maxPoolSize = 10,
    minPoolSize = 2,
    properties = {
        @DataSourceProperty(name = "ssl", value = "true")
    }
)
public class MyApplication {
    // DataSource 可用: java:global/jdbc/MyDataSource
}
```

## 运行时服务

### JdbcAdminServiceImpl

```java
@Service
public class JdbcAdminServiceImpl {

    // 创建连接池
    public void createJdbcConnectionPool(JdbcConnectionPool config) {

        // 1. 验证连接池配置
        validateConfiguration(config);

        // 2. 部署连接池
        JdbcConnectionPoolDeployer deployer =
            habitat.getService(JdbcConnectionPoolDeployer.class);
        deployer.deployResource(config);

        // 3. 配置监听器
        JdbcServiceConfigListener listener =
            habitat.getService(JdbcServiceConfigListener.class);
        listener.configurationChanged(config);
    }

    // 删除连接池
    public void deleteJdbcConnectionPool(String poolName) {
        JdbcConnectionPool pool = getJdbcConnectionPool(poolName);

        // 取消部署连接池
        JdbcConnectionPoolDeployer deployer =
            habitat.getService(JdbcConnectionPoolDeployer.class);
        deployer.undeployResource(pool);
    }

    // Ping 连接池
    public boolean pingConnectionPool(String poolName) {
        try {
            PoolInfo poolInfo = new PoolInfo(poolName);
            return runtime.pingConnectionPool(poolInfo);
        } catch (Exception e) {
            return false;
        }
    }
}
```

### JdbcDataSource

```java
@Service
public class JdbcDataSource {

    // 获取数据源
    public DataSource getDataSource(String jndiName) throws NamingException {
        InitialContext ic = new InitialContext();
        return (DataSource) ic.lookup(jndiName);
    }

    // 获取 PM 数据源
    public DataSource getPMDataSource(String jndiName) throws NamingException {
        String pmJndiName = ConnectorsUtil.getPMJndiName(jndiName);
        InitialContext ic = new InitialContext();
        return (DataSource) ic.lookup(pmJndiName);
    }
}
```

## 相关模块

- `jdbc-config` - JDBC 配置 Bean
- `jdbc-ra/jdbc-core` - 核心 RA 实现
- `appserver/connectors` - JCA 连接器运行时
- `nucleus/admin/config-api` - 配置 API
- `nucleus/monitoring` - 监控框架
