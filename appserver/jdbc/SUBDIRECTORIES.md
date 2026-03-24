# JDBC 子目录架构文档索引

本目录包含 JDBC 模块各子目录的详细架构文档。

## 文档索引

| 子目录 | 文档位置 | 说明 |
|--------|----------|------|
| **jdbc-core** | [jdbc-core/CLAUDE.md](jdbc-ra/jdbc-core/CLAUDE.md) | 核心资源适配器实现 |
| **jdbc40** | [jdbc40/CLAUDE.md](jdbc-ra/jdbc40/CLAUDE.md) | JDBC 4.0/4.1 特定实现 |
| **jdbc-runtime** | [jdbc-runtime/CLAUDE.md](jdbc-runtime/CLAUDE.md) | 部署与监控架构 |
| **jdbc-config** | (使用主文档) | 连接池配置 Bean |
| **admin** | (使用主文档) | CLI 命令 |

## 架构层次

```
┌─────────────────────────────────────────────────────────────────┐
│                        应用层                                   │
│                    (JNDI / @Resource)                           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      jdbc-runtime                               │
│              (部署、监控、运行时管理)                             │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                       jdbc40                                    │
│              (JDBC 4.0/4.1 Wrapper 层)                          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                       jdbc-core                                 │
│          (JCA 资源适配器核心实现)                                │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      JDBC 驱动                                   │
│              (Oracle, PostgreSQL, MySQL 等)                     │
└─────────────────────────────────────────────────────────────────┘
```

## 快速参考

### 核心类位置

**jdbc-core (核心 RA 实现)**:
- `ManagedConnectionFactoryImpl` - MCF 抽象基类
- `ManagedConnectionImpl` - 物理连接管理
- `CPManagedConnectionFactory` - ConnectionPoolDataSource MCF
- `DSManagedConnectionFactory` - DataSource MCF
- `XAManagedConnectionFactory` - XADataSource MCF
- `ConnectionManagerImplementation` - 连接管理器

**jdbc40 (JDBC 4.0/4.1)**:
- `DataSource40` - 支持 unwrap/isWrapperFor
- `ConnectionHolder40` - JDBC 4.1 连接功能
- `PreparedStatementWrapper40` - closeOnCompletion
- `ResultSetWrapper40` - getObject(Class<T>)

**jdbc-runtime (部署与监控)**:
- `JdbcConnectionPoolDeployer` - 连接池部署器
- `JdbcResourceDeployer` - 资源部署器
- `DataSourceDefinitionDeployer` - 注解处理器
- `JdbcConnPoolProbeProvider` - 监控探针
- `JdbcConnPoolStatsProvider` - 统计提供者
- `JdbcAdminServiceImpl` - 管理服务

**jdbc-config (配置)**:
- `JdbcConnectionPool` - 连接池配置接口
- `JdbcResource` - 资源配置接口

**admin (CLI 命令)**:
- `CreateJdbcConnectionPool` - 创建连接池
- `DeleteJdbcConnectionPool` - 删除连接池
- `CreateJdbcResource` - 创建资源
- `DeleteJdbcResource` - 删除资源

### 关键流程

**连接获取流程**:
1. 应用通过 JNDI 或 @Resource 获取 DataSource
2. 调用 DataSource.getConnection()
3. ConnectorRuntime 分配 ManagedConnection
4. 创建 ConnectionHolder (jdbc40)
5. 返回给应用程序

**连接池部署流程**:
1. JdbcConnectionPoolDeployer.deployResource()
2. 创建 ConnectorConnectionPool
3. 配置 MCF 属性
4. 注册到 ConnectorRuntime
5. 创建实际的连接池

**监控流程**:
1. 连接池事件触发 Probe
2. JdbcConnPoolProbeProvider 发送探针事件
3. JdbcConnPoolStatsProvider 收集统计数据
4. 通过 JMX 暴露监控数据

## 相关资源

- [主 JDBC 文档](CLAUDE.md) - JDBC 模块总览
- [Payara Server 文档](https://docs.payara.fish) - 官方文档
- [Jakarta Connectors 规范](https://jakarta.ee/specifications/ connectors/) - JCA 规范
