# CLAUDE.md - GlassFish Batch Connector

This file provides guidance for working with the `glassfish-batch-connector` module - GlassFish integration layer for Jakarta Batch.

## Module Overview

The glassfish-batch-connector module provides the integration between IBM JBatch and GlassFish/Payara infrastructure, including persistence managers, security integration, and runtime configuration.

## Build Commands

```bash
# Build glassfish-batch-connector
mvn -DskipTests clean package -f appserver/batch/glassfish-batch-connector/pom.xml
```

## Key Components

### Persistence Managers

Located in `fish.payara.jbatch.persistence.rdbms`:

| Manager | Database | Purpose |
|---------|----------|---------|
| `MySqlPersistenceManager` | MySQL | MySQL-specific persistence |
| `OraclePersistenceManager` | Oracle | Oracle-specific persistence |
| `PostgresPersistenceManager` | PostgreSQL | PostgreSQL persistence |
| `DB2PersistenceManager` | IBM DB2 | DB2 persistence |
| `SQLServerPersistenceManager` | SQL Server | SQL Server persistence |
| `JBatchJDBCPersistenceManager` | Generic | Generic JDBC persistence |
| `NullPersistenceManager` | N/A | In-memory persistence |
| `LazyBootPersistenceManager` | N/A | Lazy initialization wrapper |

### JDBC Constants

Each database has corresponding JDBC constants:
- `MySQLJDBCConstants`
- `OracleJDBCConstants`
- `PostgresJDBCConstants`
- `DB2JDBCConstants`
- `SQLServerJDBCConstants`
- `JDBCQueryConstants`

### Runtime Services

Located in `org.glassfish.batch.spi.impl`:

| Class | Purpose |
|-------|---------|
| `BatchRuntimeConfiguration` | Runtime configuration management |
| `BatchRuntimeHelper` | Runtime helper utilities |
| `GlassFishBatchSecurityHelper` | Security integration |
| `ManagedServiceActivator` | Service lifecycle management |
| `GlassFishBatchValidationException` | Validation exception |

## Package Structure

```
fish.payara.jbatch.persistence.rdbms/
├── MySqlPersistenceManager.java
├── OraclePersistenceManager.java
├── PostgresPersistenceManager.java
├── DB2PersistenceManager.java
├── SQLServerPersistenceManager.java
├── JBatchJDBCPersistenceManager.java
├── NullPersistenceManager.java
├── LazyBootPersistenceManager.java
└── [Database]JDBCConstants.java

org.glassfish.batch.spi.impl/
├── BatchRuntimeConfiguration.java
├── BatchRuntimeHelper.java
├── GlassFishBatchSecurityHelper.java
├── ManagedServiceActivator.java
└── GlassFishBatchValidationException.java
```

## Persistence Manager Selection

Persistence managers are selected based on:
1. Database type (MySQL, Oracle, PostgreSQL, etc.)
2. JDBC driver availability
3. Configuration in `domain.xml`

## Security Integration

The `GlassFishBatchSecurityHelper` integrates with:
- Payara security context
- JAAS authentication
- Role-based authorization

## Runtime Configuration

Batch runtime configuration includes:
- Data source JNDI name for JDBC persistence
- Job executor thread pool settings
- Security context propagation

## Dependencies

- `com.ibm.jbatch.spi` - JBatch SPI
- `com.ibm.jbatch.container` - JBatch container
- `jakarta.batch-api` - Jakarta Batch API
- `internal-api` - Internal APIs
- `common-util` - Common utilities

## Related Modules

- `jbatch-repackaged` - JBatch container
- `hazelcast-jbatch-store` - Hazelcast persistence
- `batch-database` - Database schemas
