# CLAUDE.md - Concurrent Implementation

This file provides guidance for working with the `concurrent-impl` module - implementation of Jakarta EE Concurrency Utilities.

## Module Overview

The concurrent-impl module provides the runtime implementation of Jakarta EE Concurrency Utilities, including managed executor services, context propagation, and admin commands.

## Build Commands

```bash
# Build concurrent-impl
mvn -DskipTests clean package -f appserver/concurrent/concurrent-impl/pom.xml

# Run tests
mvn test -f appserver/concurrent/concurrent-impl/pom.xml
```

## Core Runtime

### ConcurrentRuntime

Main runtime service managing all concurrency resources:

```java
@Service
@Singleton
public class ConcurrentRuntime {
    // Get or create executor service
    public AbstractManagedExecutorService getManagedExecutorService(
        ResourceInfo resourceInfo,
        ManagedExecutorServiceConfig config);

    // Get or create scheduled executor
    public AbstractManagedExecutorService getManagedScheduledExecutorService(...);

    // Get or create context service
    public ContextServiceImpl getContextService(...);

    // Get or create thread factory
    public ManagedThreadFactoryImpl getManagedThreadFactory(...);
}
```

### Context Propagation

#### ContextSetupProviderImpl

Captures and restores context for async tasks:

```java
public class ContextSetupProviderImpl implements ContextSetupProvider {
    // Capture current context
    public ContextHandle saveContext(Map<String, String> contextObj);

    // Restore context before execution
    public ContextHandle setup(ContextHandle contextHandle);

    // Reset context after execution
    public void reset(ContextHandle contextHandle);
}
```

#### Context Types

| Context | Description |
|---------|-------------|
| `Classloader` | Application classloader |
| `JNDI` | java:comp/env namespace |
| `Security` | JAAS security context |
| `WorkArea` | Transaction association |

#### InvocationContext

Captured context snapshot:

```java
public class InvocationContext implements ContextHandle {
    private ClassLoader classLoader;
    private String componentId;
    private String appName;
    private String moduleName;
    private SecurityContext securityContext;
    private Transaction transaction;
    // Payara-specific
    private RequestTracingService requestTracing;
    private Tracer openTracing;
}
```

## Resource Deployers

### Deployer Pattern

All deployers follow the same pattern:

```
1. DeployResource() called with config
2. Create config wrapper (ManagedExecutorServiceConfig, etc.)
3. Publish JNDI reference with ConcurrentObjectFactory
4. Register monitoring
```

### Deployer Classes

| Deployer | Resource |
|----------|----------|
| `ContextServiceDeployer` | ContextService |
| `ManagedExecutorServiceDeployer` | ManagedExecutorService |
| `ManagedScheduledExecutorServiceDeployer` | ManagedScheduledExecutorService |
| `ManagedThreadFactoryDeployer` - ManagedThreadFactory |

### Annotation Handlers

Processes `@*Definition` annotations:

| Handler | Annotation |
|---------|------------|
| `ContextServiceDefinitionHandler` | `@ContextServiceDefinition` |
| `ManagedExecutorDefinitionHandler` | `@ManagedExecutorDefinition` |
| `ManagedScheduledExecutorDefinitionHandler` | `@ManagedScheduledExecutorDefinition` |
| `ManagedThreadFactoryDefinitionHandler` | `@ManagedThreadFactoryDefinition` |

## Admin Commands

### Create Commands

```java
@CommandName("create-managed-executor-service")
public class CreateManagedExecutorService {
    @Param(name = "maximumpoolsize")
    String maximumPoolSize;

    @Param(name = "taskqueuecapacity")
    String taskQueueCapacity;

    @Param(name = "contextinfo")
    String contextInfo;

    @Param(name = "hungtaskthreshold")
    String hungTaskThreshold;

    @Param(name = "useforkjoinpool")
    Boolean useForkJoinPool;

    @Param(name = "usevirtualthreads")
    Boolean useVirtualThreads;
}
```

### List Commands

```bash
# List all ManagedExecutorServices
asadmin list-managed-executor-services

# List all ContextServices
asadmin list-context-services
```

### Delete Commands

```bash
asadmin delete-managed-executor-service concurrent/myExecutor
asadmin delete-context-service concurrent/myContext
```

## Object Factory

### ConcurrentObjectFactory

Creates actual instances from JNDI lookups:

```java
public class ConcurrentObjectFactory implements ObjectFactory {
    public Object getObjectInstance(Object obj, Name name, ...) {
        Reference ref = (Reference) obj;
        BaseConfig config = (BaseConfig) ref.get(0).getContent();
        ResourceInfo resourceInfo = (ResourceInfo) ref.get(1).getContent();

        switch(config.getType()) {
            case MANAGED_EXECUTOR_SERVICE:
                return getRuntime().getManagedExecutorService(resourceInfo, config);
            case CONTEXT_SERVICE:
                return getRuntime().getContextService(resourceInfo, config);
            // ... other cases
        }
    }
}
```

## Default Resources

Server provides default resources if not configured:

```java
@Service
public class DefaultManagedExecutorService {
    public static final String DEFAULT_JNDI_NAME =
        "java:comp/DefaultManagedExecutorService";
}

@Service
public class DefaultContextService {
    public static final String DEFAULT_JNDI_NAME =
        "java:comp/DefaultContextService";
}
```

## Monitoring

### Statistics Providers

```java
@Service
public class ManagedExecutorServiceStatsProvider {
    private long activeCount;           // Currently active tasks
    private long queuedTaskCount;       // Tasks in queue
    private long completedTaskCount;    // Total completed
    private long hungTaskCount;         // Hung tasks
}
```

### Monitoring Integration

- Registered with `monitoring-core` service
- Provides JMX MBeans
- Integrates with Payara monitoring

## Transaction Context

### TransactionSetupProviderImpl

Handles transaction propagation:

```java
public class TransactionSetupProviderImpl {
    // Captures transaction context
    public ContextHandle saveContext(Map<String, String> context);

    // Suspends/resumes transactions as needed
    public ContextHandle setup(ContextHandle contextHandle);

    // Resets transaction after execution
    public void reset(ContextHandle contextHandle);
}
```

### Transaction Behavior

| Configuration | Transaction Behavior |
|---------------|---------------------|
| `WorkArea` context | Transaction suspended before async, available inside task |
| No `WorkArea` | No transaction propagation |

## Payara Extensions

### Request Tracing Integration

```java
// Captures request tracing context
RequestTracingService requestTracing;

// Propagates to async tasks
// Continues request trace in async execution
```

### OpenTracing Integration

```java
// Distributed tracing support
OpenTracingService openTracing;
Tracer tracer;

// Creates span for async task execution
```

### Stuck Thread Detection

```java
// Hung task detection
StuckThreadsStore stuckThreadsStore;

// Tracks tasks exceeding hung-task-threshold
```

## Virtual Threads

### Virtual Thread Support

```java
if (config.getUseVirtualThreads()) {
    return new VirtualThreadsManagedExecutorService(
        config, contextSetupProvider);
}
```

### Implementation Classes

| Class | Purpose |
|-------|---------|
| `VirtualThreadsManagedExecutorService` | MES with virtual threads |
| `VirtualThreadsManagedScheduledExecutorService` - MSES with virtual threads |
| `VirtualThreadsManagedThreadFactory` | MTF with virtual threads |

## Package Structure

```
org.glassfish.concurrent.runtime/
├── ConcurrentRuntime.java              # Main runtime
├── ContextSetupProviderImpl.java       # Context propagation
├── TransactionSetupProviderImpl.java   # Transaction handling
├── InvocationContext.java             # Captured context
└── TransactionHandleImpl.java

org.glassfish.concurrent.runtime.deployer/
├── ConcurrentObjectFactory.java        # JNDI factory
├── ContextServiceDeployer.java
├── ManagedExecutorServiceDeployer.java
├── ManagedScheduledExecutorServiceDeployer.java
├── ManagedThreadFactoryDeployer.java
├── *Config.java                        # Config wrappers
└── Default*Service.java                # Default resources

org.glassfish.concurrent.admin/
├── Create*Service.java                 # Create commands
├── Delete*Service.java                 # Delete commands
├── List*Services.java                  # List commands
└── *Manager.java                       # Resource managers

fish.payara.concurrent.monitoring/
├── ManagedExecutorServiceStatsProvider.java
└── ConcurrentMonitoringUtils.java

org.glassfish.concurrent.runtime.deployment.annotation.handlers/
├── *DefinitionHandler.java             # Annotation processors
└── *DefinitionListHandler.java         # List annotation processors
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.enterprise.concurrent-api` - Concurrency Utilities API |
| `concurrent-connector` | Configuration beans |
| `concurro` | Core implementation library |
| `config-api` | Configuration access |
| `nucleus-resources` | Resource infrastructure |
| `container-common` | Container integration |
| `jts` | Transaction support |
| `monitoring-core` | Monitoring service |
| `requesttracing-core` - Request tracing |
| `healthcheck-stuck` | Stuck thread detection |
| `opentracing-adapter` | Distributed tracing |

## Related Modules

- `concurrent-connector` - Configuration beans
- `nucleus/resources` - Resource infrastructure
- `appserver/common/container-common` - Integration points
