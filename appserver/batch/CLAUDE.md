# CLAUDE.md - Batch

This file provides guidance for working with the `appserver/batch` module - Jakarta Batch (JSR 352) implementation in Payara Server.

## Module Overview

The batch module provides Jakarta Batch (JSR 352) support for Payara Server, including batch job processing, persistence layers, and admin commands. It integrates IBM JBatch implementation with Payara infrastructure.

## Sub-Modules

| Module | Purpose |
|--------|---------|
| `jbatch-repackaged` | IBM JBatch container repackaged as OSGi bundle |
| `glassfish-batch-connector` | GlassFish integration layer and persistence managers |
| `hazelcast-jbatch-store` | Hazelcast-based distributed batch job persistence |
| `batch-database` | Database schema for batch job persistence |
| `glassfish-batch-commands` | Admin commands for batch job management |

## Build Commands

```bash
# Build entire batch module
mvn -DskipTests clean package -f appserver/batch/pom.xml

# Build specific sub-module
mvn -DskipTests clean package -f appserver/batch/glassfish-batch-commands/pom.xml
mvn -DskipTests clean package -f appserver/batch/hazelcast-jbatch-store/pom.xml
```

## Architecture

### Batch Processing Architecture

```
┌────────────────────────────────────────────────────────────┐
│                   Batch Application                        │
│  (Batchlet, Chunk, Batch Artifacts - JSL/XML)               │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   Jakarta Batch API                         │
│  (JobOperator, JobContext, Step, Chunk, etc.)              │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   IBM JBatch Container                      │
│  (jbatch-repackaged)                                        │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   GlassFish Batch Connector                   │
│  - Job Operator/Repository                                 │
│  - Persistence Managers (JDBC, Hazelcast)                   │
│  - Security Helper                                         │
│  - Runtime Configuration                                   │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   Persistence Layers                          │
├────────────────────────────────────────────────────────────┤
│  JDBC Persistence  │  Hazelcast Persistence  │  In-Memory   │
│  (MySQL, Oracle,     │  (Distributed job       │  (Embedded    │
│   PostgreSQL,       │   state storage)      │  storage)     │
│   DB2, SQL Server)  │                        │              │
└────────────────────────────────────────────────────────────┘
```

## Key Components

### Admin Commands

Located in `org.glassfish.batch`:

| Command | Purpose |
|---------|---------|
| `ListBatchJobs` | List all batch jobs |
| `ListBatchJobSteps` | List steps in a job |
| `ListBatchJobExecutions` | List job executions |
| `ListBatchRuntimeConfiguration` | Show batch runtime config |
| `SetBatchRuntimeConfiguration` | Configure batch runtime |
| `CleanJbatchRepository` | Clean up batch repository |
| `PurgeJbatchRepository` | Purge job execution data |

### Persistence Managers

Located in `fish.payara.jbatch.persistence.rdbms`:

| Manager | Database |
|---------|----------|
| `MySqlPersistenceManager` | MySQL |
| `OraclePersistenceManager` | Oracle |
| `PostgresPersistenceManager` | PostgreSQL |
| `DB2PersistenceManager` | IBM DB2 |
| `SQLServerPersistenceManager` | Microsoft SQL Server |
| `NullPersistenceManager` | In-memory (no database) |
| `LazyBootPersistenceManager` | Lazy initialization |

### Hazelcast Persistence

Located in `fish.payara.jbatch.persistence.hazelcast`:

- `HazelcastPersistenceService` - Distributed job state storage using Hazelcast

### Runtime Configuration

Located in `org.glassfish.batch.spi.impl`:

| Class | Purpose |
|-------|---------|
| `BatchRuntimeConfiguration` | Runtime configuration settings |
| `BatchRuntimeHelper` | Runtime helper utilities |
| `GlassFishBatchSecurityHelper` | Security integration |
| `ManagedServiceActivator` | Service activation |

## Package Structure

```
appserver/batch/
├── jbatch-repackaged/          # IBM JBatch OSGi bundle
├── glassfish-batch-connector/   # GlassFish integration
│   ├── persistence/rdbms/        # JDBC persistence managers
│   └── spi/impl/                # Runtime services
├── hazelcast-jbatch-store/       # Hazelcast persistence
├── batch-database/               # Database schemas
└── glassfish-batch-commands/    # Admin commands
```

## Admin Commands

```bash
# List batch jobs
asadmin list-batch-jobs

# List job steps
asadmin list-batch-job-steps --jobname myJob

# List job executions
asadmin list-batch-job-executions --jobname myJob

# Configure batch runtime
asadmin set-batch-runtime-configuration
    --datasource jndi/batch-datasource

# Clean repository
asadmin clean-jbatch-repository

# Purge specific execution
asadmin purge-jbatch-repository --jobexecid 12345
```

## Persistence Configuration

### Database Persistence

Batch job state can be persisted to a database. Supported databases:
- MySQL
- Oracle
- PostgreSQL
- IBM DB2
- Microsoft SQL Server

Configuration via `datasource` in `domain.xml`:
```xml
<batch-runtime-configuration>
    <datasource-jndi>jndi/batch-datasource</datasource-jndi>
</batch-runtime-configuration>
```

### Hazelcast Persistence

For distributed batch processing, job state can be stored in Hazelcast:
- Provides distributed job state storage
- Enables job state sharing across cluster
- Useful for clustered batch processing

## Integration Points

The batch module integrates with:
- **Jakarta Batch API** - Standard batch programming interface
- **CDI/Weld** - Dependency injection
- **JDBC** - Database persistence
- **Hazelcast** - Distributed persistence
- **Admin CLI** - Management commands
- **Deployment** - Batch application deployment

## Dependencies

Key dependencies:
- `jakarta.batch-api` - Jakarta Batch API
- `com.ibm.jbatch.*` - IBM JBatch implementation
- `hazelcast` - Distributed caching
- `persistence-common` - Persistence utilities
- `concurrent-connector` - Concurrency utilities
- `internal-api` - Internal APIs
- `weld` - CDI integration

## Batch Application Development

### Batch Job Definition

```java
@Batchlet("myBatchlet")
public class MyBatchlet implements Batchlet {
    @Override
    public String process() throws Exception {
        // Batch processing logic
        return "COMPLETED";
    }
}
```

```xml
<job id="myJob" xmlns="http://xmlns.jcp.org/xml/ns/javaee" version="2.0">
    <step id="step1" >
        <batchlet ref="myBatchlet"/>
    </step>
</job>
```

### Job Operator Injection

```java
@Inject
JobOperator jobOperator;

public long startJob() {
    return jobOperator.start("myJob", new Properties());
}
```

## Related Modules

- `appserver/ejb` - EJB integration for batch
- `appserver/persistence` - JPA integration
- `nucleus/payara-modules/hazelcast-bootstrap` - Hazelcast infrastructure
