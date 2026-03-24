# CLAUDE.md - Concurrent Connector

This file provides guidance for working with the `concurrent-connector` module - configuration beans for Concurrency Utilities resources.

## Module Overview

The concurrent-connector module defines the configuration beans (HK2 config) for Jakarta EE Concurrency Utilities resources that are managed by the server.

## Build Commands

```bash
# Build concurrent-connector
mvn -DskipTests clean package -f appserver/concurrent/concurrent-connector/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Purpose**: Configuration beans only (no implementation)

## Configuration Beans

### ConcurrencyResource

Base interface for all concurrency resources:

```java
public interface ConcurrencyResource extends ConfigBeanProxy {
    String getJndiName();
    String getContextInfo();
    String getContextInfoRemaining();
}
```

### ContextService

Configuration for ContextService:

```java
@Configured
public interface ContextService extends ConcurrencyResource {
    @Attribute
    String getContextInfo();              // e.g., "Classloader,JNDI,Security"

    @Attribute(defaultValue="false")
    String getContextInfoRemaining();     // Capture all remaining contexts
}
```

### ManagedExecutorService

Configuration for ManagedExecutorService:

```java
@Configured
public interface ManagedExecutorService extends ManagedExecutorServiceBase {
    @Attribute(defaultValue="" + Integer.MAX_VALUE)
    String getMaximumPoolSize();          // Max threads

    @Attribute
    String getTaskQueueCapacity();         // Queue capacity

    @Attribute(defaultValue="false")
    String getUseForkJoinPool();          // Use ForkJoinPool

    @Attribute(defaultValue="false")
    String getUseVirtualThreads();        // Use virtual threads
}
```

### ManagedExecutorServiceBase

Base configuration for executor services:

```java
public interface ManagedExecutorServiceBase extends ConcurrencyResource {
    @Attribute(defaultValue="0")
    String getHungTaskThreshold();        // Task hung detection (ms)

    @Attribute
    String getLongRunningTasks();         // Optimize for long-running tasks

    @Attribute(defaultValue="600")
    String getThreadLifetimeSeconds();    // Thread idle timeout
}
```

### ManagedScheduledExecutorService

Configuration for scheduled executor:

```java
@Configured
public interface ManagedScheduledExecutorService
    extends ManagedExecutorServiceBase {
    // Inherits configuration from ManagedExecutorServiceBase
    // Adds scheduling-specific behavior
}
```

### ManagedThreadFactory

Configuration for thread factory:

```java
@Configured
public interface ManagedThreadFactory extends ConcurrencyResource {
    @Attribute
    String getContext();                  // Associated context service

    @Attribute(defaultValue="false")
    String getThreadPriority();           // Thread priority
}
```

## Context Info Values

### Standard Context Types

| Value | Description |
|-------|-------------|
| `Classloader` | Application classloader |
| `JNDI` | JNDI naming context |
| `Security` | Security context (principal) |
| `WorkArea` | Transaction context |

### Usage

```xml
<context-info>Classloader,JNDI,Security,WorkArea</context-info>
```

## Admin Command Mapping

Configuration beans map to admin commands via `@ResourceConfigCreator`:

| Config Bean | Create Command | Delete Command |
|-------------|----------------|----------------|
| `ContextService` | `create-context-service` | `delete-context-service` |
| `ManagedExecutorService` | `create-managed-executor-service` | `delete-managed-executor-service` |
| `ManagedScheduledExecutorService` | `create-managed-scheduled-executor-service` | `delete-managed-scheduled-executor-service` |
| `ManagedThreadFactory` | `create-managed-thread-factory` | `delete-managed-thread-factory` |

## REST Endpoints

Configuration beans expose REST endpoints via `@RestRedirects`:

```
POST   /resources/managed-executor-service        -> create-managed-executor-service
DELETE /resources/managed-executor-service/{id}  -> delete-managed-executor-service
```

## Package Structure

```
org.glassfish.concurrent.config/
├── ConcurrencyResource.java              # Base interface
├── ContextService.java                   # ContextService config
├── ManagedExecutorService.java           # MES config
├── ManagedExecutorServiceBase.java       # Base executor config
├── ManagedScheduledExecutorService.java  # MSES config
└── ManagedThreadFactory.java             # MTF config
```

## Domain Configuration

Resources are stored in `domain.xml`:

```xml
<resources>
    <context-service jndi-name="concurrent/defaultContextService">
        <context-info>Classloader,JNDI,Security,WorkArea</context-info>
    </context-service>
    <managed-executor-service jndi-name="concurrent/defaultManagedExecutorService">
        <context-info>Classloader,JNDI,Security,WorkArea</context-info>
        <maximum-pool-size>200</maximum-pool-size>
        <task-queue-capacity>10000</task-queue-capacity>
    </managed-executor-service>
</resources>
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `config-api` | Configuration infrastructure |
| `hk2` | Dependency injection |
| `glassfish-api` - Public APIs |
| `nucleus-resources` | Resource base types |

## Related Modules

- `concurrent-impl` - Implementation using these configs
- `nucleus/admin/config-api` - Configuration infrastructure
