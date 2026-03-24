# CLAUDE.md - Hazelcast JBatch Store

This file provides guidance for working with the `hazelcast-jbatch-store` module - Hazelcast-based persistence for batch jobs.

## Module Overview

The hazelcast-jbatch-store module provides distributed batch job state persistence using Hazelcast, enabling job state sharing across Payara cluster instances.

## Build Commands

```bash
# Build hazelcast-jbatch-store
mvn -DskipTests clean package -f appserver/batch/hazelcast-jbatch-store/pom.xml
```

## Key Components

### Hazelcast Persistence Service

Located in `fish.payara.jbatch.persistence.hazelcast`:

```java
@Service
public class HazelcastPersistenceService {
    // Store job state in Hazelcast distributed map
    // Provides distributed job state sharing
}
```

## Features

- **Distributed State Storage** - Job state stored in Hazelcast distributed maps
- **Cluster Integration** - Job state shared across Payara cluster
- **High Availability** - Job state survives server restarts (with proper Hazelcast configuration)
- **Persistence** - Job execution data persisted to distributed cache

## Architecture

```
┌────────────────────────────────────────────────────────────┐
│                   Batch Job Execution                         │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   HazelcastPersistenceService                 │
│  - Stores job checkpoints in Hazelcast                          │
│  - Retrieves job state across cluster                          │
│  - Manages distributed maps                                  │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   Hazelcast Cluster                           │
│  - Distributed maps for job state                            │
│  - Data replication across instances                          │
│  - Partitioned job data storage                               │
└────────────────────────────────────────────────────────────┘
```

## Configuration

### Enabling Hazelcast Persistence

Set the batch runtime datasource to use Hazelcast:

```bash
asadmin set-batch-runtime-configuration \
    --datasource hazelcast-batch-store
```

### Hazelcast Configuration

Hazelcast cluster configuration is managed separately:
- `nucleus/payara-modules/hazelcast-bootstrap` - Hazelcast initialization
- `domain.xml` - Hazelcast network configuration

## Package Structure

```
fish.payara.jbatch.persistence.hazelcast/
└── HazelcastPersistenceService.java   # Hazelcast persistence service
```

## Dependencies

- `hazelcast` - Hazelcast distributed cache
- `com.ibm.jbatch.spi` - JBatch SPI
- `com.ibm.jbatch.container` - JBatch container
- `persistence-common` - Persistence utilities
- `concurrent-connector` - Concurrency utilities
- `internal-api` - Internal APIs

## Use Cases

### Clustered Batch Processing

When running batch jobs in a Payara cluster:
1. Job checkpoints stored in Hazelcast distributed maps
2. Job state accessible from any cluster instance
3. Failed jobs can restart on different instances
4. Job execution history shared across cluster

### High Availability

With Hazelcast persistence:
- Job state survives server restarts
- Distributed state provides redundancy
- No single point of failure

## Benefits

| Feature | Benefit |
|---------|---------|
| Distributed state | Job state shared across cluster |
| High availability | Jobs survive server failures |
| Scalability | Job processing distributed across cluster |
| Fault tolerance | Jobs can restart on different instances |

## Related Modules

- `glassfish-batch-connector` - Persistence manager interface
- `jbatch-repackaged` - JBatch container
- `nucleus/payara-modules/hazelcast-bootstrap` - Hazelcast infrastructure
