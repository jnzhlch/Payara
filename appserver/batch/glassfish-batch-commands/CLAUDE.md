# CLAUDE.md - GlassFish Batch Commands

This file provides guidance for working with the `glassfish-batch-commands` module - admin commands for batch job management.

## Module Overview

The glassfish-batch-commands module provides asadmin commands for managing batch jobs, including listing jobs, executions, steps, and managing the batch repository.

## Build Commands

```bash
# Build glassfish-batch-commands
mvn -DskipTests clean package -f appserver/batch/glassfish-batch-commands/pom.xml
```

## Admin Commands

### Job Management Commands

| Command | Purpose |
|---------|---------|
| `list-batch-jobs` | List all batch jobs |
| `list-batch-job-steps` | List steps for a specific job |
| `list-batch-job-executions` | List execution history for a job |

### Configuration Commands

| Command | Purpose |
|---------|---------|
| `list-batch-runtime-configuration` | Display batch runtime settings |
| `set-batch-runtime-configuration` | Configure batch runtime (datasource) |

### Repository Management Commands

| Command | Purpose |
|---------|---------|
| `clean-jbatch-repository` | Clean up batch repository data |
| `purge-jbatch-repository` | Purge specific execution data |

## Command Examples

```bash
# List all batch jobs
asadmin list-batch-jobs

# List steps for a job
asadmin list-batch-job-steps --jobname myJob

# List recent executions
asadmin list-batch-job-executions --jobname myJob

# Set runtime configuration
asadmin set-batch-runtime-configuration \
    --datasource jndi/batch-datasource

# Clean repository
asadmin clean-jbatch-repository

# Purge specific execution
asadmin purge-jbatch-repository --jobexecid 12345
```

## Package Structure

```
org.glassfish.batch/
├── BatchConstants.java              # Constants
├── AbstractListCommand.java           # Base for list commands
├── AbstractListCommandProxy.java     # Proxy for list commands
├── AbstractLongListCommand.java       # Base for long list commands
├── ListBatchJobs.java                # list-batch-jobs
├── ListBatchJobSteps.java            # list-batch-job-steps
├── ListBatchJobExecutions.java       # list-batch-job-executions
├── ListBatchRuntimeConfiguration.java  # list-batch-runtime-configuration
└── SetBatchRuntimeConfiguration.java  # set-batch-runtime-configuration

fish.payara.batch/
├── CleanJbatchRepository.java        # clean-jbatch-repository
└── PurgeJbatchRepository.java        # purge-jbatch-repository
```

## Dependencies

- `admin-util` - Admin command utilities
- `admin-cli` - CLI infrastructure
- `glassfish-api` - Public APIs

## Related Modules

- `glassfish-batch-connector` - Batch runtime services
- `jbatch-repackaged` - JBatch container
