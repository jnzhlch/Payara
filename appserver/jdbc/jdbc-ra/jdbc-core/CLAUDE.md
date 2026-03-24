# JDBC Core - Resource Adapter Architecture

## 概述

`jdbc-core` 是 JDBC 资源适配器的核心实现，提供 JCA 1.7 规范的连接池管理和物理数据库连接。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    ManagedConnectionFactoryImpl                  │
│                    (MCF - 工厂类)                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  createConnectionFactory()                                │ │
│  │  createManagedConnection()                               │ │
│  │  matchManagedConnections()                               │ │
│  │  getInvalidConnections()                                 │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│CPManaged       │  │DSManaged       │  │XAManaged       │
│ConnectionFactory│  │ConnectionFactory│  │ConnectionFactory│
│(ConnectionPool)│  │(DataSource)    │  │(XADataSource)  │
└────────┬───────┘  └────────┬───────┘  └────────┬───────┘
         │                   │                   │
         └───────────────────┼───────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ManagedConnectionImpl                         │
│                    (MC - 物理连接)                               │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  ConnectionHolder (逻辑连接)                             │ │
│  │  PreparedStatementWrapper (语句包装)                     │ │
│  │  StatementCache (语句缓存)                               │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## 核心类详解

### 1. ManagedConnectionFactoryImpl

**职责**: JCA ManagedConnectionFactory 的抽象实现

**关键方法**:
```java
// 创建连接工厂
public Object createConnectionFactory()
public Object createConnectionFactory(ConnectionManager cxManager)

// 创建物理连接
public abstract ManagedConnection createManagedConnection(Subject, ConnectionRequestInfo)

// 连接匹配 - 用于连接池复用
public ManagedConnection matchManagedConnections(Set, Subject, ConnectionRequestInfo)

// 连接验证
public Set getInvalidConnections(Set connectionSet)
void isValid(ManagedConnectionImpl mc)

// 事务隔离级别管理
protected void setIsolation(ManagedConnectionImpl mc)
void resetIsolation(ManagedConnectionImpl mc, int tranIsol)
```

**连接验证方法**:
- `custom-validation` - 自定义验证类
- `auto-commit` - 通过 AutoCommit 测试
- `meta-data` - 通过 DatabaseMetaData 测试
- `table` - 通过执行 SQL 查询测试

### 2. ManagedConnectionImpl

**职责**: 管理物理数据库连接和连接句柄

**生命周期状态**:
```java
protected boolean isDestroyed = false;      // 已销毁
protected boolean isUsable = true;         // 可用
protected boolean transactionInProgress = false; // 事务中
protected boolean isClean = true;          // 已清理
```

**连接类型**:
```java
public static final int ISNOTAPOOLEDCONNECTION = 0;  // 非池化连接
public static final int ISPOOLEDCONNECTION = 1;      // 池化连接
public static final int ISXACONNECTION = 2;          // XA 连接
```

**核心功能**:

**1. 连接管理**
```java
// 获取逻辑连接
public Object getConnection(Subject sub, ConnectionRequestInfo cxReqInfo)

// 关联连接句柄
public void associateConnection(Object connection)

// 清理连接
public void cleanup()

// 销毁连接
public void destroy()
```

**2. 语句缓存**
```java
// 准备缓存的 PreparedStatement
public PreparedStatement prepareCachedStatement(
    ConnectionWrapper conWrapper, String sql,
    int resultSetType, int resultSetConcurrency)

// 语句缓存配置
private void tuneStatementCaching(PoolInfo poolInfo,
    int statementCacheSize, String statementCacheType)
```

**3. 语句泄漏检测**
```java
// 配置语句泄漏跟踪
private void tuneStatementLeakTracing(PoolInfo poolInfo,
    long statementLeakTimeout, boolean statementLeakReclaim)

// 获取泄漏检测器
public StatementLeakDetector getLeakDetector()
```

**4. 事务管理**
```java
// 事务开始
void transactionStarted()

// 事务完成
void transactionCompleted()

// 检查事务状态
public boolean isTransactionInProgress()
```

### 3. ConnectionManagerImplementation

**职责**: 简单的连接管理器实现

```java
public class ConnectionManagerImplementation implements ConnectionManager {
    public Object allocateConnection(ManagedConnectionFactory mcf,
                                     ConnectionRequestInfo info) {
        ManagedConnection mc = mcf.createManagedConnection(null, info);
        return mc.getConnection(null, info);
    }
}
```

### 4. 具体的 MCF 实现

#### CPManagedConnectionFactory
```java
@Service
@ConnectionDefinition(
    connectionFactory = javax.sql.DataSource.class,
    connection = java.sql.Connection.class
)
public class CPManagedConnectionFactory extends ManagedConnectionFactoryImpl {
    private javax.sql.ConnectionPoolDataSource cpDataSourceObj;

    @Override
    public ManagedConnection createManagedConnection(...) {
        PooledConnection pc = cpDataSourceObj.getPooledConnection();
        return constructManagedConnection(pc, null, passCred, this);
    }
}
```

#### DSManagedConnectionFactory
```java
@Service
public class DSManagedConnectionFactory extends ManagedConnectionFactoryImpl {
    private javax.sql.DataSource dataSourceObj;

    @Override
    public ManagedConnection createManagedConnection(...) {
        Connection sqlCon = dataSourceObj.getConnection();
        return constructManagedConnection(null, sqlCon, passCred, this);
    }
}
```

#### XAManagedConnectionFactory
```java
@Service
public class XAManagedConnectionFactory extends ManagedConnectionFactoryImpl {
    private javax.sql.XADataSource xaDataSourceObj;

    @Override
    public ManagedConnection createManagedConnection(...) {
        XAConnection xaCon = xaDataSourceObj.getXAConnection();
        return constructManagedConnection(xaCon, null, passCred, this);
    }

    @Override
    public XAResource getXAResource() throws ResourceException {
        return new XAResourceImpl(((XAConnection) pc).getXAResource(), this);
    }
}
```

## 语句缓存架构

### 缓存类型

```
┌─────────────────────────────────────────────────────────────────┐
│                       CacheFactory                               │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  getDataStructure(poolInfo, cacheType, cacheSize)        │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│   FIXED        │  │   LRU          │  │  THREADED      │
│   CacheImpl    │  │   CacheImpl    │  │  Cache         │
│(固定大小缓存)  │  │(LRU淘汰策略)   │  │(线程本地缓存)  │
└────────────────┘  └────────────────┘  └────────────────┘
```

### 缓存键

```java
public class CacheObjectKey {
    private String sql;                    // SQL 语句
    private int statementType;             // 语句类型
    private int resultSetType;             // 结果集类型
    private int resultSetConcurrency;      // 结果集并发性

    // 构造方法
    public CacheObjectKey(String sql, int statementType,
                         int resultSetType, int resultSetConcurrency)
}
```

### 缓存操作流程

```
prepareStatement(sql)
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│  检查是否启用语句缓存                                            │
│  if (statementCaching) {                                        │
└────────────────────────────┬────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
                    ▼                 ▼
              缓存命中           缓存未命中
                    │                 │
                    ▼                 ▼
            检查语句是否空闲      创建新 PreparedStatement
                    │                 │
            ┌───────┴────────┐       │
            │                │       │
            ▼                ▼       ▼
        语句有效          语句忙碌  加入缓存
        (复用)           (创建新的)
```

## 连接验证机制

### 验证方法实现

```java
// 1. 自定义验证
protected void isValidByCustomValidation(Connection con, String className) {
    Class validationClass = loadClass(className);
    ConnectionValidation validator = (ConnectionValidation) validationClass.newInstance();
    if (!validator.isConnectionValid(con, statementTimeout)) {
        throw new ResourceException("Invalid connection");
    }
}

// 2. AutoCommit 验证
protected void isValidByAutoCommit(Connection con) {
    boolean ac = con.getAutoCommit();
    if (ac) {
        con.setAutoCommit(false);
    } else {
        con.rollback();
        con.setAutoCommit(true);
    }
    con.setAutoCommit(ac);
}

// 3. Metadata 验证
protected void isValidByMetaData(Connection con) {
    con.getMetaData(); // 如果连接无效会抛出异常
}

// 4. 表查询验证
protected void isValidByTableQuery(Connection con, String tableName) {
    String sql = "SELECT COUNT(*) FROM " + tableName;
    PreparedStatement stmt = con.prepareStatement(sql);
    stmt.setQueryTimeout(statementTimeout);
    ResultSet rs = stmt.executeQuery();
}
```

## SQL 跟踪与监控

### SQLTraceDelegator

```java
public class SQLTraceDelegator {
    private List<SQLTraceListener> listeners;

    public void registerSQLTraceListener(SQLTraceListener listener) {
        listeners.add(listener);
    }

    public void sqlTrace(SQLTrace sqlTrace) {
        for (SQLTraceListener listener : listeners) {
            listener.sqlTrace(sqlTrace);
        }
    }
}
```

### 监控探针

```java
@ProbeProvider(moduleProviderName="glassfish",
               moduleName="jdbc",
               probeProviderName="connection-pool")
public class JdbcRAConstants {
    @Probe(name="connectionCreatedEvent")
    void connectionCreatedEvent(@ProbeParam("poolName") String poolName);

    @Probe(name="connectionAcquiredEvent")
    void connectionAcquiredEvent(@ProbeParam("poolName") String poolName);

    @Probe(name="connectionReleasedEvent")
    void connectionReleasedEvent(@ProbeParam("poolName") String poolName);
}
```

### 统计提供者

```java
@Service
public class JdbcStatsProvider {
    private FrequentSQLTraceCache freqSqlTraceCache;
    private SlowSqlTraceCache slowSqlTraceCache;

    // 注册统计信息
    public void mcfCreated() {
        StatsProviderManager.register(
            "jdbc-connection-pool",
            PluginPoint.SERVER,
            poolMonitoringSubTreeRoot,
            this
        );
    }
}
```

## 语句泄漏检测

### StatementLeakDetector

```java
public class StatementLeakDetector {
    private final long statementLeakTimeout;
    private final boolean statementLeakReclaim;

    // 启动泄漏检测任务
    public void startLeakDetect(PreparedStatementWrapper statementWrapper) {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (!statementWrapper.isClosed()) {
                    // 记录泄漏
                    logStatementLeak(statementWrapper);
                    // 可选：回收语句
                    if (statementLeakReclaim) {
                        statementWrapper.close();
                    }
                }
            }
        };
        timer.schedule(task, statementLeakTimeout);
    }
}
```

## 连接包装器层次

```
┌─────────────────────────────────────────────────────────────────┐
│                    ConnectionHolder40                            │
│                    (JDBC 4.0 特定功能)                           │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │                    ConnectionHolder                       │ │
│  │  ┌────────────────────────────────────────────────────┐ │ │
│  │  │              ConnectionWrapper                      │ │
│  │  │  管理实际的 java.sql.Connection                   │ │
│  │  └────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## 连接生命周期

```
创建阶段
       │
       ├─→ createManagedConnection()
       │   └─→ 构造 ManagedConnectionImpl
       │       ├─→ 初始化语句缓存
       │       ├─→ 初始化泄漏检测
       │       └─→ 获取物理连接
       │
获取阶段
       │
       ├─→ getConnection()
       │   └─→ 创建 ConnectionHolder
       │       ├─→ 设置 active=true
       │       ├─→ 执行 Init SQL
       │       └─→ 注册 SQL 跟踪
       │
使用阶段
       │
       ├─→ 执行 SQL 语句
       │   ├─→ prepareCachedStatement()
       │   ├─→ execute()
       │   └─→ 处理 ResultSet
       │
清理阶段
       │
       ├─→ cleanup()
       │   ├─→ 重置 AutoCommit
       │   ├─→ 重置事务隔离级别
       │   └─→ 设置 isClean=true
       │
销毁阶段
       │
       └─→ destroy()
           ├─→ 清空语句缓存
           ├─→ 取消泄漏检测任务
           └─→ 关闭物理连接
```

## 事务隔离级别

```java
private int getTransactionIsolationInt(String tranIsolation) {
    if (tranIsolation.equalsIgnoreCase("read-uncommitted")) {
        return Connection.TRANSACTION_READ_UNCOMMITTED;
    } else if (tranIsolation.equalsIgnoreCase("read-committed")) {
        return Connection.TRANSACTION_READ_COMMITTED;
    } else if (tranIsolation.equalsIgnoreCase("repeatable-read")) {
        return Connection.TRANSACTION_REPEATABLE_READ;
    } else if (tranIsolation.equalsIgnoreCase("serializable")) {
        return Connection.TRANSACTION_SERIALIZABLE;
    } else if (tranIsolation.equalsIgnoreCase("snapshot")) {
        return fish.payara.sql.Connection.TRANSACTION_SNAPSHOT;
    }
}
```

## Lazy Association 支持

```java
public class ManagedConnectionImpl implements DissociatableManagedConnection {
    @Override
    public void dissociateConnections() {
        if (myLogicalConnection != null) {
            myLogicalConnection.dissociateConnection();
            myLogicalConnection = null;
        }
    }
}
```

## 相关模块

- `jdbc-ra/jdbc40` - JDBC 4.0 特定包装器
- `jdbc-config` - 连接池配置
- `jdbc-runtime` - 部署和运行时管理
- `appserver/connectors` - JCA 连接器运行时
