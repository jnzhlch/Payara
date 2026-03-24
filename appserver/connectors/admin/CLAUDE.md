# CLAUDE.md - Connectors Admin

This file provides guidance for working with the `admin` module - admin commands for JCA resources.

## Module Overview

The admin module provides asadmin commands for managing JCA resources, including connection pools, connector resources, admin objects, and resource adapters.

## Build Commands

```bash
# Build connectors admin
mvn -DskipTests clean package -f appserver/connectors/admin/pom.xml
```

## Admin Commands

### Connection Pool Commands

| Command | Purpose |
|---------|---------|
| `create-connector-connection-pool` | Create connection pool |
| `delete-connector-connection-pool` | Delete connection pool |
| `list-connector-connection-pools` | List all pools |
| `ping-connection-pool` | Test pool connectivity |

### Connector Resource Commands

| Command | Purpose |
|---------|---------|
| `create-connector-resource` | Create connector resource (CF) |
| `delete-connector-resource` | Delete connector resource |
| `list-connector-resources` | List all resources |

### Admin Object Commands

| Command | Purpose |
|---------|---------|
| `create-admin-object` | Create admin object |
| `delete-admin-object` | Delete admin object |
| `list-admin-objects` | List admin objects |

### Resource Adapter Commands

| Command | Purpose |
|---------|---------|
| `list-resource-adapters` | List deployed RARs |
| `create-resource-adapter-config` | Configure RA |
| `delete-resource-adapter-config` | Delete RA config |

### Security Map Commands

| Command | Purpose |
|---------|---------|
| `create-connector-security-map` | Create security map |
| `delete-connector-security-map` | Delete security map |
| `list-connector-security-maps` | List security maps |

### Pool Management Commands

| Command | Purpose |
|---------|---------|
| `flush-connection-pool` | Reclaim connections |
| `set-connection-pool-properties` | Update pool properties |

## Command Examples

### Creating a Connection Pool

```bash
asadmin create-connector-connection-pool \
    --raname jmsra \
    --connectiondefinition jakarta.jms.ConnectionFactory \
    --steadypoolsize 8 \
    --maxpoolsize 32 \
    --maxwait 60000 \
    --property UserName=admin:Password=secret \
    jms-pool
```

### Creating a Connector Resource

```bash
asadmin create-connector-resource \
    --poolname jms-pool \
    --objecttype jakarta.jms.ConnectionFactory \
    jms/myFactory
```

### Creating an Admin Object

```bash
asadmin create-admin-object \
    --raname jmsra \
    --restype jakarta.jms.Queue \
    --property Name=MyQueue \
    jms/myQueue
```

### Pool Operations

```bash
# Ping pool (test connectivity)
asadmin ping-connection-pool jms-pool

# Flush pool (reclaim connections)
asadmin flush-connection-pool jms-pool

# Update pool properties
asadmin set-connection-pool-properties \
    --steadyPoolSize=10 \
    --maxPoolSize=50 \
    jms-pool
```

## Command Configuration

### CreateConnectorConnectionPool

```java
@CommandName("create-connector-connection-pool")
public class CreateConnectorConnectionPool {
    @Param(name = "raname")
    String rarName;                     // Resource adapter name

    @Param(name = "connectiondefinition")
    String connectionDefinitionName;     // MCF interface

    @Param(name = "steadypoolsize", defaultValue = "8")
    Integer steadyPoolSize;

    @Param(name = "maxpoolsize", defaultValue = "32")
    Integer maxPoolSize;

    @Param(name = "maxwait", defaultValue = "60000")
    Long maxWaitTimeInMillis;

    @Param(name = "property")
    String properties;                  // RA config properties

    @Param(name = "pooling", defaultValue = "true")
    Boolean pooling;

    @Param(name = "ping")
    Boolean ping;                       // Ping pool after creation
}
```

### PingConnectionPoolCommand

```bash
asadmin ping-connection-pool jms-pool
```

Validates pool by:
1. Getting connection from pool
2. Testing connection validity
3. Returning connection to pool

## Security Map Configuration

### Creating Security Maps

```bash
asadmin create-connector-security-map \
    --poolname jms-pool \
    --principals appUser1,appUser2 \
    --usergroups appGroup1 \
    --mappedusername eisUser \
    --mappedpassword eisPassword \
    mySecurityMap
```

### Security Map Types

| Type | Description |
|------|-------------|
| **Principal Mapping** | Maps application principal to EIS principal |
| **Group Mapping** | Maps application group to EIS principal |

## Pool Properties

### Standard Properties

| Property | Description |
|----------|-------------|
| `steadyPoolSize` | Initial pool size |
| `maxPoolSize` | Maximum pool size |
| `poolResizeQuantity` | Connections to resize |
| `idleTimeout` | Idle timeout (seconds) |
| `maxWaitTime` | Max wait for connection |
| `connectionValidationRequired` | Validate connections |

### Transaction Support

| Value | Description |
|-------|-------------|
| `XATransaction` | XA transaction support |
| `LocalTransaction` - Local transaction |
| `NoTransaction` | No transaction support |

## Package Structure

```
org.glassfish.connectors.admin/
├── config/
│   └── connector-connection-pool/
│       └── connector-connection-pool-conf.xml  # Config schema
├── CreateConnectorConnectionPool.java
├── DeleteConnectorConnectionPool.java
├── ListConnectorConnectionPools.java
├── PingConnectionPoolCommand.java
├── FlushConnectionPool.java
├── SetConnectorConnectionPoolProperties.java
├── CreateConnectorResource.java
├── DeleteConnectorResource.java
├── ListConnectorResources.java
├── CreateAdminObject.java
├── DeleteAdminObject.java
├── ListAdminObjects.java
├── CreateConnectorSecurityMap.java
├── DeleteConnectorSecurityMap.java
├── ListConnectorSecurityMaps.java
├── ListResourceAdapters.java
└── ResourceAdapterConfig.java
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `hk2-core` | Dependency injection |
| `admin-util` | Admin command utilities |
| `config-api` | Configuration beans |
| `connectors-internal-api` | Connector APIs |
| `cluster-admin` | Cluster support |
| `jdbc-config` | JDBC config utilities |
| `admin-cli` | CLI infrastructure |
| `cluster-cli` | Cluster CLI commands |
| `glassfish-api` | Public APIs |

## Related Modules

- `connectors-runtime` - Implements commands via admin services
- `connectors-internal-api` - Admin service interfaces
