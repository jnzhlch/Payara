# CLAUDE.md - Batch Database

This file provides guidance for working with the `batch-database` module - database schemas for Jakarta Batch.

## Module Overview

The batch-database module provides database initialization scripts (DDL) for Jakarta Batch job state persistence. These scripts create the necessary tables for batch job execution tracking.

## Module Type

- **Packaging**: `distribution-fragment`
- This module's content is extracted during distribution assembly

## Build Commands

```bash
# Build batch-database
mvn -DskipTests clean package -f appserver/batch/batch-database/pom.xml
```

## Database Scripts

### Supported Databases

| Database | Script |
|----------|--------|
| MySQL | `jsr352-mysql.sql` |
| Oracle | `jsr352-oracle.sql` |
| PostgreSQL | `jsr352-postgresql.sql` |
| H2 | `jsr352-h2.sql` |
| Microsoft SQL Server | `jsr352-mssqlserver.sql` |
| IBM DB2 | `jsr352-db2.sql` |
| Sybase ASE | `jsr352-sybase-ace.sql` |

### Oracle Specific Scripts

Located in `oracle/` subdirectory:

| Script | Purpose |
|--------|---------|
| `create-all-tables.sql` | Create all tables |
| `drop-all-tables.sql` | Drop all tables |
| `checkpoint_data_ddl.sql` | Checkpoint data table |
| `execution_instance_data_ddl.sql` | Execution instance table |
| `job_instance_data_ddl.sql` | Job instance table |
| `job_status_ddl.sql` | Job status table |
| `step_execution_instance_data_ddl.sql` | Step execution table |
| `step_status_ddl.sql` | Step status table |

## Script Location

Scripts are packaged at:
```
glassfish/lib/install/databases/
```

## Database Tables

The scripts create the core Jakarta Batch tables:

| Table | Purpose |
|-------|---------|
| `JOBINSTANCE` | Job instance metadata |
| `JOBSTATUS` | Job status tracking |
| `EXECUTIONINSTANCE` | Job execution records |
| `STEPINSTANCE` | Step execution records |
| `STEPSTATUS` | Step status tracking |
| `CHECKPOINTDATA` | Checkpoint data for chunk processing |

## Usage

### Database Setup

When configuring JDBC persistence for batch:

1. **Create database** (if not exists)
2. **Run appropriate DDL script** for your database
3. **Configure datasource** in Payara
4. **Set batch runtime configuration**:

```bash
asadmin create-jdbc-connection-pool \
    --datasourceclassname org.postgresql.xa.pg.PGXADataSource \
    --restype javax.sql.XADataSource \
    --property user=app \
    --property password=app \
    --property databaseName=batchdb \
    --property serverName=localhost \
    --property portNumber=5432 \
    batch-pool

asadmin set-batch-runtime-configuration \
    --datasource jndi/batch-pool
```

## Package Structure

```
src/main/resources/glassfish/lib/install/databases/
├── jsr352-mysql.sql
├── jsr352-oracle.sql
├── jsr352-postgresql.sql
├── jsr352-h2.sql
├── jsr352-mssqlserver.sql
�── jsr352-db2.sql
├── jsr352-sybase-ace.sql
└── oracle/
    ├── checkpoint_data_ddl.sql
    ├── create-all-tables.sql
    ├── drop-all-tables.sql
    ├── execution_instance_data_ddl.sql
    ├── job_instance_data_ddl.sql
    ├── job_status_ddl.sql
    ├── step_execution_instance_data_ddl.sql
    └── step_status_ddl.sql
```

## Distribution Integration

This module is a distribution fragment that gets extracted during Payara distribution assembly. The SQL files are placed in:
```
{payara-install}/glassfish/lib/install/databases/
```

## Related Modules

- `glassfish-batch-connector` - Persistence managers that use these schemas
- `jbatch-repackaged` - JBatch container
- `hazelcast-jbatch-store` - Alternative persistence (Hazelcast)
