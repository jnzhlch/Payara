# JDBC 4.0 - JDBC 4.0/4.1 特定实现

## 概述

`jdbc40` 模块提供 JDBC 4.0 和 JDBC 4.1 规范的特定功能实现，包括 Wrapper 接口和新增的 JDBC 方法。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    JDBC 4.0 Wrapper 层                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │                    DataSource40                            │ │
│  │  - unwrap(Class<T>)                                      │ │
│  │  - isWrapperFor(Class<?>)                                │ │
│  │  - getParentLogger() [JDBC 4.1]                          │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │                 ConnectionWrapper40                       │ │
│  │  - getNetworkTimeout() [JDBC 4.1]                        │ │
│  │  - setNetworkTimeout() [JDBC 4.1]                        │ │
│  │  - abort(Executor) [JDBC 4.1]                            │ │
│  │  - getSchema() [JDBC 4.1]                                │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │              PreparedStatementWrapper40                     │ │
│  │  - closeOnCompletion() [JDBC 4.1]                        │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │                 ResultSetWrapper40                         │ │
│  │  - getObject(Class<T>) [JDBC 4.1]                         │ │
│  │  - updateObject(String, Class<T>) [JDBC 4.1]             │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## DataSource40

### JDBC 4.0 Wrapper 支持

```java
public class DataSource40 extends AbstractDataSource {

    /**
     * 解包到指定的接口
     * 如果底层数据源实现该接口，返回底层对象
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        T result;
        try {
            Object cds = mcf.getDataSource();

            if (iface.isInstance(cds)) {
                // 底层数据源实现该接口
                result = iface.cast(cds);
            } else if (cds instanceof java.sql.Wrapper) {
                // 递归解包
                result = ((java.sql.Wrapper) cds).unwrap(iface);
            } else {
                throw new SQLException("无法解包到 " + iface.getName());
            }
        } catch (ResourceException e) {
            throw new SQLException(e);
        }
        return result;
    }

    /**
     * 检查是否包装了指定接口
     */
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        boolean result = false;
        try {
            Object cds = mcf.getDataSource();

            if (iface.isInstance(cds)) {
                result = true;
            } else if (cds instanceof java.sql.Wrapper) {
                result = ((java.sql.Wrapper) cds).isWrapperFor(iface);
            }
        } catch (ResourceException e) {
            throw new SQLException(e);
        }
        return result;
    }
}
```

### JDBC 4.1 getParentLogger 支持

```java
@Override
public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    if (DataSourceObjectBuilder.isJDBC41()) {
        try {
            // 通过反射调用底层数据源的 getParentLogger
            return (Logger) executor.invokeMethod(
                mcf.getDataSource().getClass(),
                "getParentLogger",
                null
            );
        } catch (ResourceException ex) {
            throw new SQLFeatureNotSupportedException(ex);
        }
    }
    throw new UnsupportedOperationException("此运行时不支持该操作");
}
```

## ConnectionHolder40

### 连接包装器

```java
public class ConnectionHolder40 extends ConnectionWrapper {

    /**
     * JDBC 4.1: 设置网络超时
     * @param executor 执行异步任务的执行器
     * @param milliseconds 超时时间（毫秒）
     */
    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds)
            throws SQLException {
        checkIfValid();
        if (actualConnection.isWrapperFor(java.sql.Connection.class)) {
            java.sql.Connection con = actualConnection.unwrap(java.sql.Connection.class);
            con.setNetworkTimeout(executor, milliseconds);
        }
    }

    /**
     * JDBC 4.1: 获取网络超时
     */
    @Override
    public int getNetworkTimeout() throws SQLException {
        checkIfValid();
        if (actualConnection.isWrapperFor(java.sql.Connection.class)) {
            java.sql.Connection con = actualConnection.unwrap(java.sql.Connection.class);
            return con.getNetworkTimeout();
        }
        return 0;
    }

    /**
     * JDBC 4.1: 中止连接
     * @param executor 执行中止任务的执行器
     */
    @Override
    public void abort(Executor executor) throws SQLException {
        checkIfValid();
        // 1. 标记连接为已中止
        setAborted(true);

        // 2. 异步关闭连接
        if (executor != null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        close();
                    } catch (SQLException e) {
                        // 记录错误但继续
                    }
                }
            });
        } else {
            close();
        }

        // 3. 通知连接池
        connectionErrorOccurred(new SQLException("Connection aborted"), this);
    }

    /**
     * JDBC 4.1: 获取 Schema
     */
    @Override
    public String getSchema() throws SQLException {
        checkIfValid();
        if (actualConnection.isWrapperFor(java.sql.Connection.class)) {
            java.sql.Connection con = actualConnection.unwrap(java.sql.Connection.class);
            return con.getSchema();
        }
        return null;
    }

    /**
     * JDBC 4.1: 设置 Schema
     */
    @Override
    public void setSchema(String schema) throws SQLException {
        checkIfValid();
        if (actualConnection.isWrapperFor(java.sql.Connection.class)) {
            java.sql.Connection con = actualConnection.unwrap(java.sql.Connection.class);
            con.setSchema(schema);
        }
    }
}
```

## PreparedStatementWrapper40

### JDBC 4.1 特定功能

```java
public class PreparedStatementWrapper40 extends PreparedStatementWrapper {

    /**
     * JDBC 4.1: 在结果集完成后关闭语句
     * 当所有关联的结果集关闭时自动关闭语句
     */
    @Override
    public void closeOnCompletion() throws SQLException {
        checkIfValid();
        if (actualStatement.isWrapperFor(PreparedStatement.class)) {
            PreparedStatement ps = actualStatement.unwrap(PreparedStatement.class);
            ps.closeOnCompletion();
        }
    }
}
```

## ResultSetWrapper40

### JDBC 4.1 对象访问

```java
public class ResultSetWrapper40 extends ResultSetWrapper {

    /**
     * JDBC 4.1: 获取指定类型的对象
     * @param columnLabel 列标签
     * @param type 目标类型
     */
    @Override
    public <T> T getObject(String columnLabel, Class<T> type)
            throws SQLException {
        checkIfValid();
        if (actualResultSet.isWrapperFor(java.sql.ResultSet.class)) {
            java.sql.ResultSet rs = actualResultSet.unwrap(java.sql.ResultSet.class);
            return rs.getObject(columnLabel, type);
        }
        return null;
    }

    /**
     * JDBC 4.1: 更新指定类型的对象
     */
    @Override
    public <T> void updateObject(String columnLabel, T value, Class<T> type)
            throws SQLException {
        checkIfValid();
        if (actualResultSet.isWrapperFor(java.sql.ResultSet.class)) {
            java.sql.ResultSet rs = actualResultSet.unwrap(java.sql.ResultSet.class);
            rs.updateObject(columnLabel, value, type);
        }
    }
}
```

## CallableStatementWrapper40

### JDBC 4.1 CallableStatement 支持

```java
public class CallableStatementWrapper40 extends CallableStatementWrapper {

    /**
     * JDBC 4.1: 注册 OUT 参数
     * @param parameterName 参数名
     * @param sqlType SQL 类型
     * @param className 类名 (对于用户定义类型)
     */
    @Override
    public void registerOutParameter(String parameterName, int sqlType, String className)
            throws SQLException {
        checkIfValid();
        if (actualStatement.isWrapperFor(CallableStatement.class)) {
            CallableStatement cs = actualStatement.unwrap(CallableStatement.class);
            cs.registerOutParameter(parameterName, sqlType, className);
        }
    }

    /**
     * JDBC 4.1: 获取对象
     */
    @Override
    public <T> T getObject(String parameterName, Class<T> type)
            throws SQLException {
        checkIfValid();
        if (actualStatement.isWrapperFor(CallableStatement.class)) {
            CallableStatement cs = actualStatement.unwrap(CallableStatement.class);
            return cs.getObject(parameterName, type);
        }
        return null;
    }
}
```

## DatabaseMetaDataWrapper40

### 元数据包装器

```java
public class DatabaseMetaDataWrapper40 extends DatabaseMetaDataWrapper {

    /**
     * JDBC 4.1: 获取生成的键总是返回 false
     */
    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        checkIfValid();
        if (meta.isWrapperFor(DatabaseMetaData.class)) {
            DatabaseMetaData md = meta.unwrap(DatabaseMetaData.class);
            return md.generatedKeyAlwaysReturned();
        }
        return false;
    }

    /**
     * JDBC 4.1: 获取最大逻辑 lob 大小
     */
    @Override
    public long getMaxLogicalLobSize() throws SQLException {
        checkIfValid();
        if (meta.isWrapperFor(DatabaseMetaData.class)) {
            DatabaseMetaData md = meta.unwrap(DatabaseMetaData.class);
            return md.getMaxLogicalLobSize();
        }
        return 0;
    }

    /**
     * JDBC 4.1: 支持 autoCommit 批量操作
     */
    @Override
    public boolean supportsAutoCommitByDefault() throws SQLException {
        checkIfValid();
        if (meta.isWrapperFor(DatabaseMetaData.class)) {
            DatabaseMetaData md = meta.unwrap(DatabaseMetaData.class);
            return md.supportsAutoCommitByDefault();
        }
        return false;
    }
}
```

## ProfiledConnectionWrapper40

### 性能分析连接包装器

```java
public class ProfiledConnectionWrapper40 extends ConnectionWrapper40 {

    /**
     * 带性能分析的连接包装器
     * 用于跟踪 SQL 执行时间和性能统计
     */
    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        long startTime = System.currentTimeMillis();

        try {
            PreparedStatement ps = super.prepareStatement(sql);

            // 记录准备时间
            long prepareTime = System.currentTimeMillis() - startTime;
            recordPrepareTime(sql, prepareTime);

            return ps;
        } catch (SQLException e) {
            // 记录错误
            recordError(sql, e);
            throw e;
        }
    }

    private void recordPrepareTime(String sql, long time) {
        // 将准备时间发送到统计监听器
        if (sqlTraceDelegator != null) {
            SQLTrace trace = new SQLTrace(sql);
            trace.setPreparationTime(time);
            sqlTraceDelegator.sqlTrace(trace);
        }
    }

    private void recordError(String sql, SQLException e) {
        // 将错误信息发送到统计监听器
        if (sqlTraceDelegator != null) {
            SQLTrace trace = new SQLTrace(sql);
            trace.setException(e);
            sqlTraceDelegator.sqlTrace(trace);
        }
    }
}
```

## JDBC 对象工厂

### Jdbc40ObjectsFactory

```java
public class Jdbc40ObjectsFactory extends JdbcObjectsFactory {

    /**
     * 创建 JDBC 4.0 连接
     */
    @Override
    public Connection getConnection(Connection actualConnection,
                                   ManagedConnectionImpl mc,
                                   ConnectionRequestInfo cxRequestInfo,
                                   boolean statementWrapping,
                                   SQLTraceDelegator sqlTraceDelegator) {
        ConnectionHolder40 conHolder = new ConnectionHolder40(
            actualConnection, mc, cxRequestInfo);

        // 配置语句包装
        conHolder.setStatementWrappingEnabled(statementWrapping);

        // 配置 SQL 跟踪
        conHolder.setSqlTraceDelegator(sqlTraceDelegator);

        return conHolder;
    }

    /**
     * 创建 JDBC 4.0 数据源
     */
    @Override
    public DataSource getDataSourceInstance(ManagedConnectionFactoryImpl mcf,
                                            ConnectionManager cm) {
        return new DataSource40(mcf, cm);
    }
}
```

## JDBC 版本检测

### DataSourceObjectBuilder

```java
public class DataSourceObjectBuilder {

    /**
     * 检测 JDBC 版本
     */
    public static boolean isJDBC41() {
        try {
            // 检查 JDBC 4.1 特定方法是否存在
            java.lang.reflect.Method method =
                java.sql.Connection.class.getMethod("abort", Executor.class);
            return method != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * 执行方法 (用于跨版本调用)
     */
    public static Object invokeMethod(Class<?> clazz,
                                     String methodName,
                                     Class<?>[] paramTypes,
                                     Object... args) throws ResourceException {
        try {
            Method method = clazz.getMethod(methodName, paramTypes);
            return method.invoke(args);
        } catch (NoSuchMethodException e) {
            throw new ResourceException("方法不存在: " + methodName, e);
        } catch (Exception e) {
            throw new ResourceException("方法调用失败: " + methodName, e);
        }
    }
}
```

## 连接池中的 JDBC 4.0/4.1 支持

### 生命周期集成

```
连接获取
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│  JdbcObjectsFactory.getConnection()                            │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  选择对象工厂                                            │ │
│  │  ├─→ isJDBC41()?                                       │ │
│  │  │   ├─→ Yes: Jdbc42ObjectsFactory                     │ │
│  │  │   └─→ No: Jdbc40ObjectsFactory                      │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  创建 ConnectionHolder40                                │ │
│  │  ├─→ 包装实际连接                                       │ │
│  │  ├─→ 配置语句包装                                       │ │
│  │  ├─→ 配置 SQL 跟踪                                      │ │
│  │  └─→ 返回给应用程序                                     │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## 使用示例

### 访问底层驱动

```java
// 应用代码
DataSource ds = (DataSource) ctx.lookup("jdbc/MyPool");
Connection conn = ds.getConnection();

// 解包到底层数据源
if (ds.isWrapperFor(OracleDataSource.class)) {
    OracleDataSource oracleDs = ds.unwrap(OracleDataSource.class);
    // 设置 Oracle 特定属性
    oracleDs.setImplicitCachingEnabled(true);
    oracleDs.setFastConnectionFailoverEnabled(true);
}

// 解包到底层连接
if (conn.isWrapperFor(OracleConnection.class)) {
    OracleConnection oracleConn = conn.unwrap(OracleConnection.class);
    // 使用 Oracle 特定功能
    oracleConn.setStatementCacheSize(100);
}
```

### JDBC 4.1 功能

```java
// 设置网络超时
conn.setNetworkTimeout(Executors.newSingleThreadExecutor(), 5000);

// 获取 Schema
String schema = conn.getSchema();
conn.setSchema("myschema");

// 中止连接
conn.abort(Executors.newSingleThreadExecutor());

// 语句完成后自动关闭
PreparedStatement ps = conn.prepareStatement(sql);
ps.closeOnCompletion();
```

## 相关模块

- `jdbc-ra/jdbc-core` - 核心 RA 实现
- `jdbc-ra/jdbc-base` - 基础包装器类
- `jdbc-config` - 连接池配置
- `jdbc-runtime` - 运行时管理
