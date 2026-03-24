# CLAUDE.md - Appserver JDBC

This file provides guidance for working with the `appserver/jdbc` module - JDBC connection pooling and resource adapter for Payara.

## Module Overview

The appserver/jdbc module provides JDBC connectivity, connection pooling, and resource management for Payara Server. It implements a JCA (Jakarta Connectors) Resource Adapter for JDBC, enabling applications to obtain database connections through JNDI-lookups or `@DataSourceDefinition` annotations.

**Key Components:**
- **jdbc-ra** - Generic JDBC Resource Adapter (JCA 1.7 compliant)
- **jdbc-config** - Configuration beans for JDBC resources and pools
- **jdbc-runtime** - Runtime deployment, monitoring, and services
- **admin** - Administration commands (`asadmin` CLI for JDBC)
- **templates** - Default configuration templates

## Build Commands

```bash
# Build entire jdbc module
mvn -DskipTests clean package -f appserver/jdbc/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/jdbc/jdbc-ra/pom.xml
mvn -DskipTests clean package -f appserver/jdbc/jdbc-config/pom.xml
mvn -DskipTests clean package -f appserver/jdbc/jdbc-runtime/pom.xml
mvn -DskipTests clean package -f appserver/jdbc/admin/pom.xml

# Run tests
mvn test -f appserver/jdbc/jdbc-runtime/pom.xml
```

## Architecture

### JDBC Resource Adapter Architecture

```
Application Layer
       │
       ├─→ JNDI Lookup (@Resource)
       │   or @DataSourceDefinition
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│                    jdbc-runtime                              │
│  ┌──────────────┐  ┌───────────────┐  ┌────────────────┐   │
│  │  Deployers   │  │   Services    │  │   Monitoring   │   │
│  └──────────────┘  └───────────────┘  └────────────────┘   │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                    Connectors Runtime                        │
│              (Resource Adapter Container)                     │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                      jdbc-ra (RAR)                           │
│  ┌────────────────────────────────────────────────────────┐ │
│  │           ManagedConnectionFactory                       │ │
│  │  ┌─────────────────┬─────────────────┬──────────────┐ │ │
│  │  │   CPManaged     │   DSManaged     │   XA Managed  │ │ │
│  │  │ ConnectionPool  │   DataSource    │   DataSource  │ │ │
│  │  │   Factory       │     Factory     │     Factory   │ │ │
│  │  └─────────────────┴─────────────────┴──────────────┘ │ │
│  └────────────────────────────────────────────────────────┘ │
│                              │                               │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              ManagedConnection                          │ │
│  │  ┌──────────────────────────────────────────────────┐ │ │
│  │  │  ConnectionHolder (wraps physical Connection)      │ │ │
│  │  │  ┌─────────────┬────────────┬─────────────────┐  │ │ │
│  │  │  │ Statement    │ ResultSet   │ PreparedStatement│  │ │ │
│  │  │  │ Wrappers     │ Wrappers    │ Wrappers        │  │ │ │
│  │  │  └─────────────┴────────────┴─────────────────┘  │ │ │
│  │  └──────────────────────────────────────────────────┘ │ │
│  └────────────────────────────────────────────────────────┘ │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
                    Physical Database
```

### Sub-Module Structure

| Submodule | Purpose | Packaging |
|-----------|---------|------------|
| `jdbc-ra/jdbc-core` | Core RA implementation | glassfish-jar |
| `jdbc-ra/jdbc40` | JDBC 4.0 specific support | glassfish-jar |
| `jdbc-ra/jdbc-ra-distribution` | RAR distribution | rar |
| `jdbc-config` | Configuration beans | glassfish-jar |
| `jdbc-runtime` | Runtime services & deployers | glassfish-jar |
| `admin` | CLI commands | glassfish-jar |
| `templates` | Default configurations | jar |

## Resource Types

### ManagedConnectionFactory Types

The JDBC RA supports three connection factory types:

#### 1. CPManagedConnectionFactory (ConnectionPoolDataSource)

```java
@Service
@ConnectionDefinition(
    connectionFactory = javax.sql.DataSource.class,
    connectionFactoryImpl = com.sun.gjc.spi.base.AbstractDataSource.class,
    connection = java.sql.Connection.class,
    connectionImpl = com.sun.gjc.spi.base.ConnectionHolder.class
)
public class CPManagedConnectionFactory extends ManagedConnectionFactoryImpl {
    private javax.sql.ConnectionPoolDataSource cpDataSourceObj;

    @Override
    public javax.sql.ConnectionPoolDataSource getDataSource() {
        // Returns vendor's ConnectionPoolDataSource
    }
}
```

**Use Case:** When JDBC driver provides `javax.sql.ConnectionPoolDataSource`

#### 2. DSManagedConnectionFactory (DataSource)

```java
@Service
@ConnectionDefinition(...)
public class DSManagedConnectionFactory extends ManagedConnectionFactoryImpl {
    private javax.sql.DataSource dataSourceObj;

    @Override
    public javax.sql.DataSource getDataSource() {
        // Returns vendor's DataSource
    }
}
```

**Use Case:** When JDBC driver provides `javax.sql.DataSource`

#### 3. XAManagedConnectionFactory (XADataSource)

```java
@Service
@ConnectionDefinition(...)
public class XAManagedConnectionFactory extends ManagedConnectionFactoryImpl {
    private javax.sql.XADataSource xaDataSourceObj;

    @Override
    public javax.sql.XADataSource getDataSource() {
        // Returns vendor's XADataSource
    }
}
```

**Use Case:** For distributed transactions (XA)

## Connection Pooling

### Pool Configuration (JdbcConnectionPool)

```java
@Configured
public interface JdbcConnectionPool extends Resource, ResourcePool {
    // Pool Sizing
    String getSteadyPoolSize();        // Default: 8 (min + initial)
    String getMaxPoolSize();            // Default: 32
    String getPoolResizeQuantity();     // Default: 2
    String getMaxWaitTimeInMillis();    // Default: 60000 (60 sec)

    // Idle Management
    String getIdleTimeoutInSeconds();   // Default: 300 (5 min)

    // Transaction Isolation
    String getTransactionIsolationLevel(); // read-uncommitted, read-committed,
                                            // repeatable-read, serializable, snapshot
    String getIsIsolationLevelGuaranteed(); // Default: true

    // Connection Validation
    String getIsConnectionValidationRequired(); // Default: false
    String getConnectionValidationMethod();     // auto-commit, meta-data,
                                                 // custom-validation, table
    String getValidationTableName();            // Required for "table" method
    String getValidationClassname();            // Required for "custom-validation"

    // Leak Detection
    String getConnectionLeakTimeoutInSeconds(); // Default: 0 (disabled)
    String getConnectionLeakReclaim();          // Default: false
    String getStatementLeakTimeoutInSeconds();  // Default: 0 (disabled)
    String getStatementLeakReclaim();            // Default: false

    // Statement Caching
    String getStatementCacheSize();      // Default: 0 (disabled)
    String getStatementCacheType();      // Cache implementation type

    // Connection Usage Limits
    String getMaxConnectionUsageCount(); // Default: 0 (unlimited)

    // SQL Tracing
    String getSqlTraceListeners();       // Comma-separated listener class names
    String getSlowQueryThresholdInSeconds(); // Default: -1 (disabled)

    // Other Settings
    String getInitSql();                 // SQL executed on new connections
    String getMatchConnections();        // Default: false
    String getPooling();                 // Default: true
    String getWrapJdbcObjects();         // Default: true
    String getLogJdbcCalls();            // Default: false
    String getStatementTimeoutInSeconds(); // Default: -1 (disabled)
}
```

### Connection Wrappers

All JDBC objects are wrapped to enable pooling and monitoring:

```java
// Connection wrapper
public class ConnectionHolder extends ConnectionWrapper {
    // Wraps physical Connection
    // Manages statement caching
    // Handles cleanup
}

// Statement wrappers
public class PreparedStatementWrapper extends PreparedStatementWrapper40 {
    // Enables statement caching
    // Tracks for leak detection
}

public class CallableStatementWrapper extends CallableStatementWrapper40 {
    // Same as PreparedStatement
}

public class StatementWrapper extends StatementWrapper40 {
    // Tracks execution for monitoring
}

// ResultSet wrapper
public class ResultSetWrapper extends ResultSetWrapper40 {
    // Ensures resources are freed
}
```

## Monitoring

### Probe Provider

```java
@ProbeProvider(moduleProviderName="glassfish",
               moduleName="jdbc",
               probeProviderName="connection-pool")
public class JdbcConnPoolProbeProvider {
    @Probe(name="connectionCreatedEvent")
    void connectionCreatedEvent(@ProbeParam("poolName") String poolName, ...);

    @Probe(name="connectionAcquiredEvent")
    void connectionAcquiredEvent(@ProbeParam("poolName") String poolName, ...);

    @Probe(name="connectionReleasedEvent")
    void connectionReleasedEvent(@ProbeParam("poolName") String poolName, ...);

    @Probe(name="connectionDestroyedEvent")
    void connectionDestroyedEvent(@ProbeParam("poolName") String poolName, ...);

    @Probe(name="connectionValidationFailedEvent")
    void connectionValidationFailedEvent(@ProbeParam("poolName") String poolName, ...);

    @Probe(name="connectionTimedOutEvent")
    void connectionTimedOutEvent(@ProbeParam("poolName") String poolName, ...);

    @Probe(name="potentialConnLeakEvent")
    void potentialConnLeakEvent(@ProbeParam("poolName") String poolName, ...);
}
```

### Stats Provider

```java
@Service
public class JdbcConnPoolStatsProvider {
    // Monitoring attributes:
    // - numConnUsed
    // - numConnFree
    // - numConnFailed
    // - numConnTimedOut
    // - numConnValidationFailed
    // - connRequestWaitTime
    // - averageConnWaitTime
    // - averageConnUsageTime
}
```

## SQL Tracing

### SQL Trace Listeners

```java
// Default listener (silent)
public class SilentSqlTraceListener implements SQLTraceListener {
    public void sqlTrace(SQLTrace sqlTrace) {
        // No-op by default
    }
}

// Custom listeners can be configured via:
// jdbc-connection-pool property: sqlTraceListeners
```

### SQL Trace Configuration

```xml
<jdbc-connection-pool name="MyPool" ...>
    <property name="sqlTraceListeners"
              value="com.example.MySqlTraceListener, fish.payara.jdbc.SlowSQLLogger"/>
    <property name="slowQueryThresholdInSeconds" value="5"/>
</jdbc-connection-pool>
```

## Deployment

### JdbcResourceDeployer

```java
@Service
@ResourceDeployerInfo(JdbcResource.class)
public class JdbcResourceDeployer implements ResourceDeployer {
    public void deployResource(Object resource, String appName, String modName) {
        JdbcResource jdbcRes = (JdbcResource) resource;
        PoolInfo poolInfo = new PoolInfo(jdbcRes.getPoolName(), appName, modName);
        ResourceInfo resourceInfo = new ResourceInfo(jdbcRes.getJndiName(), appName, modName);

        runtime.createConnectorResource(resourceInfo, poolInfo, null);

        // Also create __PM (Pool Managed) resource
        ResourceInfo pmResourceInfo = new ResourceInfo(
            ConnectorsUtil.getPMJndiName(jdbcRes.getJndiName()), appName, modName);
        runtime.createConnectorResource(pmResourceInfo, poolInfo, null);
    }
}
```

### DataSourceDefinitionDeployer

```java
@Service
public class DataSourceDefinitionDeployer {
    // Handles @DataSourceDefinition annotations
    // Creates pools and resources at deployment time
}
```

## Package Structure

```
appserver/jdbc/
├── jdbc-ra/                              # Resource Adapter
│   ├── jdbc-core/                        # Core implementation
│   │   └── com/sun/gjc/spi/
│   │       ├── CPManagedConnectionFactory.java    # ConnectionPool DS
│   │       ├── DSManagedConnectionFactory.java    # DataSource
│   │       ├── XAManagedConnectionFactory.java    # XADataSource
│   │       ├── ManagedConnectionImpl.java        # Managed connection
│   │       ├── ConnectionManagerImplementation.java
│   │       └── base/                             # Wrappers
│   │           ├── ConnectionHolder.java
│   │           ├── PreparedStatementWrapper.java
│   │           ├── StatementWrapper.java
│   │           └── ResultSetWrapper.java
│   ├── jdbc40/                           # JDBC 4.0 wrappers
│   │   └── com/sun/gjc/spi/jdbc40/
│   │       ├── ConnectionWrapper40.java
│   │       ├── PreparedStatementWrapper40.java
│   │       └── ResultSetWrapper40.java
│   └── jdbc-ra-distribution/             # RAR packaging
│
├── jdbc-config/                          # Configuration beans
│   └── org/glassfish/jdbc/config/
│       ├── JdbcConnectionPool.java      # Pool configuration
│       └── JdbcResource.java            # Resource configuration
│
├── jdbc-runtime/                         # Runtime services
│   └── org/glassfish/jdbc/
│       ├── deployer/
│       │   ├── JdbcResourceDeployer.java
│       │   ├── JdbcConnectionPoolDeployer.java
│       │   └── DataSourceDefinitionDeployer.java
│       ├── pool/monitor/
│       │   ├── JdbcConnPoolProbeProvider.java
│       │   └── JdbcConnPoolStatsProvider.java
│       └── util/
│           └── JdbcResourcesUtil.java
│
├── admin/                                # CLI commands
│   └── org/glassfish/jdbc/admin/
│       ├── CreateJdbcConnectionPool.java
│       ├── DeleteJdbcConnectionPool.java
│       ├── ListJdbcConnectionPools.java
│       ├── CreateJdbcResource.java
│       ├── DeleteJdbcResource.java
│       └── ListJdbcResources.java
│
└── templates/                            # Default configs
    └── default-jdbc-connection-pool.xml
```

## Installed Artifacts

```
$PAYARA_HOME/
├── glassfish/
│   ├── lib/
│   │   ├── install/
│   │   │   └── databases/              # JDBC driver templates
│   │   └── jdbc-ra/                    # JDBC RAR (rarely used directly)
│   └── modules/
│       ├── jdbc-core.jar
│       ├── jdbc40.jar
│       ├── jdbc-config.jar
│       ├── jdbc-runtime.jar
│       └── jdbc-admin.jar
```

## Admin Commands

```bash
# Create connection pool
asadmin create-jdbc-connection-pool \
    --datasourceclassname oracle.jdbc.pool.OracleDataSource \
    --property user=SCOTT:password=TIGER:serverName=localhost \
    MyPool

# Create JDBC resource
asadmin create-jdbc-resource --poolname=MyPool jdbc/MyDataSource

# List pools
asadmin list-jdbc-connection-pools

# List resources
asadmin list-jdbc-resources

# Ping pool
asadmin ping-connection-pool MyPool

# Test connection
asadmin test-connection-pool --poolName=MyPool

# Delete resource
asadmin delete-jdbc-resource jdbc/MyDataSource

# Delete pool
asadmin delete-jdbc-connection-pool MyPool
```

## Configuration Examples

### Oracle DataSource

```xml
<jdbc-connection-pool name="OraclePool"
    datasource-classname="oracle.jdbc.pool.OracleDataSource"
    res-type="javax.sql.DataSource">
    <property name="user" value="scott"/>
    <property name="password" value="tiger"/>
    <property name="serverName" value="localhost"/>
    <property name="portNumber" value="1521"/>
    <property name="databaseName" value="ORCL"/>
</jdbc-connection-pool>
```

### MySQL ConnectionPoolDataSource

```xml
<jdbc-connection-pool name="MySQLPool"
    datasource-classname="com.mysql.cj.jdbc.MysqlConnectionPoolDataSource"
    res-type="javax.sql.ConnectionPoolDataSource">
    <property name="user" value="root"/>
    <property name="password" value="password"/>
    <property name="serverName" value="localhost"/>
    <property name="port" value="3306"/>
    <property name="databaseName" value="mydb"/>
</jdbc-connection-pool>
```

### PostgreSQL XADataSource

```xml
<jdbc-connection-pool name="PostgresXAPool"
    datasource-classname="org.postgresql.xa.PGXADataSource"
    res-type="javax.sql.XADataSource">
    <property name="user" value="postgres"/>
    <property name="password" value="password"/>
    <property name="serverName" value="localhost"/>
    <property name="portNumber" value="5432"/>
    <property name="databaseName" value="mydb"/>
</jdbc-connection-pool>
```

## Statement Caching

The JDBC RA implements statement caching to improve performance:

```java
// Cache types:
// - FIXED: Fixed size cache
// - LRU: Least Recently Used eviction
// - THREADED: Thread-local cache

// Configuration:
<property name="statementCacheSize" value="50"/>
<property name="statementCacheType" value="LRU"/>
```

## Leak Detection

### Connection Leak Detection

```xml
<property name="connectionLeakTimeoutInSeconds" value="30"/>
<property name="connectionLeakReclaim" value="true"/>
```

When enabled, logs stack trace of connection acquisition and optionally reclaims leaked connections.

### Statement Leak Detection

```xml
<property name="statementLeakTimeoutInSeconds" value="60"/>
<property name="statementLeakReclaim" value="true"/>
```

Detects and reclaims statements that are not closed.

## Related Modules

- `appserver/connectors` - Connectors runtime (JCA container)
- `nucleus/admin/config-api` - Configuration infrastructure
- `appserver/resources` - Generic resources framework
- `appserver/deployment` - Deployment infrastructure

## Notes

- The JDBC RA implements JCA 1.7 specification
- Connection wrappers are transparent to applications (delegate to vendor objects)
- Statement caching is per-connection, not global
- Connection validation is expensive - use judiciously
- XA transactions require XADataSource and proper transaction manager configuration
