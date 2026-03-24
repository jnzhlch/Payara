# CLAUDE.md - Appserver Concurrent

This file provides guidance for working with the `appserver/concurrent` module - Jakarta EE Concurrency Utilities implementation.

## Module Overview

The concurrent module implements Jakarta EE Concurrency Utilities (JSR 236), providing managed executor services, managed thread factories, and context services for asynchronous task execution in Java EE applications.

## Build Commands

```bash
# Build entire concurrent module
mvn -DskipTests clean package -f appserver/concurrent/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/concurrent/<submodule>/pom.xml
```

## Module Structure

| Submodule | Purpose |
|-----------|---------|
| `concurrent-connector` | Configuration beans for concurrency resources |
| `concurrent-impl` | Implementation of Concurrency Utilities |
| `concurrent-connector-l10n` | Localization for connector |
| `concurrent-impl-l10n` | Localization for implementation |

## Core Architecture

### Concurrency Resources

| Resource Type | Description | API |
|---------------|-------------|-----|
| `ManagedExecutorService` | Async task execution | `jakarta.enterprise.concurrent.ManagedExecutorService` |
| `ManagedScheduledExecutorService` - Scheduled tasks | `jakarta.enterprise.concurrent.ManagedScheduledExecutorService` |
| `ManagedThreadFactory` | Managed thread creation | `jakarta.enterprise.concurrent.ManagedThreadFactory` |
| `ContextService` | Context propagation | `jakarta.enterprise.concurrent.ContextService` |

### Component Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│                    ConcurrentRuntime                         │
│  - Manages all concurrency resources                         │
│  - Creates executor services, thread factories               │
│  - Context propagation setup                                 │
└─────────────────────────────────────────────────────────────┘
                              │
    ┌─────────────────────────┼─────────────────────────┐
    │                         │                         │
┌───▼────────┐    ┌──────────▼──────┐    ┌──────────▼──────┐
│  Deployers │    │ Admin Commands  │    │  ObjectFactory  │
│            │    │                 │    │                 │
│ - MES      │    │ create-*        │    │ JNDI lookup     │
│ - MSES     │    │ delete-*        │    │ creates actual  │
│ - MTF      │    │ list-*          │    │ instances       │
│ - CS       │    │                 │    │                 │
└────────────┘    └──────────────────┘    └─────────────────┘
    │                         │                         │
    ▼                         ▼                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    Concurro Implementation                   │
│  - AbstractManagedExecutorService                            │
│  - ManagedExecutorServiceImpl                                │
│  - ContextServiceImpl                                        │
└─────────────────────────────────────────────────────────────┘
```

## Context Propagation

### Context Types

| Context Type | Description | Constant |
|--------------|-------------|----------|
| **Classloader** | Application classloader | `CONTEXT_INFO_CLASSLOADER` |
| **JNDI** | java:comp/env namespace | `CONTEXT_INFO_JNDI` |
| **Security** | Security context (principal) | `CONTEXT_INFO_SECURITY` |
| **WorkArea** | Transaction context | `CONTEXT_INFO_WORKAREA` |

### ContextSetupProviderImpl

Captures and restores context for async tasks:

```java
// Capture context before task submission
ContextHandle handle = contextSetup.saveContext(context);

// Restore context before task execution
contextSetup.setup(context);
```

### Payara-Specific Contexts

- **Request Tracing** - Payara request tracing propagation
- **OpenTracing** - Distributed tracing support
- **Health Check Stuck Threads** - Stuck thread detection

## Resource Configuration

### ManagedExecutorService Configuration

```xml
<managed-executor-service>
    <jndi-name>concurrent/myExecutor</jndi-name>
    <maximum-pool-size>50</maximum-pool-size>
    <task-queue-capacity>10000</task-queue-capacity>
    <thread-lifetime-seconds>600</thread-lifetime-seconds>
    <context-info>Classloader,JNDI,Security,WorkArea</context-info>
    <hung-task-threshold>60000</hung-task-threshold>
    <use-fork-join-pool>false</use-fork-join-pool>
</managed-executor-service>
```

### ContextService Configuration

```xml
<context-service>
    <jndi-name>concurrent/myContext</jndi-name>
    <context-info>Classloader,JNDI,Security</context-info>
    <context-info-remaining>true</context-info-remaining>
</context-service>
```

## Admin Commands

### Create Resources

```bash
# Create ManagedExecutorService
asadmin create-managed-executor-service \
    --maximumpoolsize 50 \
    --taskqueuecapacity 10000 \
    --contextinfo Classloader,JNDI,Security,WorkArea \
    concurrent/myExecutor

# Create ManagedScheduledExecutorService
asadmin create-managed-scheduled-executor-service \
    --maximumpoolsize 10 \
    concurrent/myScheduledExecutor

# Create ContextService
asadmin create-context-service \
    --contextinfo Classloader,JNDI,Security \
    concurrent/myContext

# Create ManagedThreadFactory
asadmin create-managed-thread-factory \
    concurrent/myThreadFactory
```

### List and Delete

```bash
# List resources
asadmin list-managed-executor-services
asadmin list-managed-scheduled-executor-services
asadmin list-context-services
asadmin list-managed-thread-factories

# Delete resources
asadmin delete-managed-executor-service concurrent/myExecutor
```

## Deployment Integration

### Annotation-Based Definition

```java
@ContextServiceDefinition(
    name = "java:app/concurrent/myContext",
    context = "Classloader,JNDI,Security"
)
@ManagedExecutorDefinition(
    name = "java:app/concurrent/myExecutor",
    context = "java:app/concurrent/myContext",
    maxAsync = 50
)
@ManagedScheduledExecutorDefinition(
    name = "java:app/concurrent/myScheduler",
    context = "java:app/concurrent/myContext"
)
@ManagedThreadFactoryDefinition(
    name = "java:app/concurrent/myThreadFactory",
    context = "java:app/concurrent/myContext"
)
public class MyApp { }
```

### Deployers

| Deployer | Handles |
|----------|---------|
| `ContextServiceDeployer` | ContextService resources |
| `ManagedExecutorServiceDeployer` | ManagedExecutorService resources |
| `ManagedScheduledExecutorServiceDeployer` - ScheduledExecutor resources |
| `ManagedThreadFactoryDeployer` - ThreadFactory resources |
| `*DefinitionDeployer` - Annotation-based definitions |

## Package Structure

### concurrent-connector

```
org.glassfish.concurrent.config/
├── ConcurrencyResource.java          # Base interface
├── ContextService.java               # ContextService config bean
├── ManagedExecutorService.java       # MES config bean
├── ManagedExecutorServiceBase.java   # Base executor config
├── ManagedScheduledExecutorService.java
└── ManagedThreadFactory.java         # MTF config bean
```

### concurrent-impl

```
org.glassfish.concurrent.runtime/
├── ConcurrentRuntime.java            # Main runtime
├── ContextSetupProviderImpl.java     # Context propagation
├── TransactionSetupProviderImpl.java # Transaction context
├── InvocationContext.java            # Invocation context
└── TransactionHandleImpl.java

org.glassfish.concurrent.runtime.deployer/
├── ConcurrentObjectFactory.java      # JNDI object factory
├── ContextServiceDeployer.java
├── ManagedExecutorServiceDeployer.java
├── ManagedScheduledExecutorServiceDeployer.java
├── ManagedThreadFactoryDeployer.java
├── DefaultManagedExecutorService.java
├── DefaultContextService.java
└── *Config.java                      # Config wrappers

org.glassfish.concurrent.admin/
├── CreateManagedExecutorService.java
├── DeleteManagedExecutorService.java
├── ListManagedExecutorServices.java
├── ContextServiceManager.java
└── *Manager.java                     # Resource managers

fish.payara.concurrent.monitoring/
├── ManagedExecutorServiceStatsProvider.java
└── ConcurrentMonitoringUtils.java    # Monitoring support

org.glassfish.concurrent.runtime.deployment.annotation.handlers/
├── ContextServiceDefinitionHandler.java
├── ManagedExecutorDefinitionHandler.java
├── ManagedScheduledExecutorDefinitionHandler.java
└── ManagedThreadFactoryDefinitionHandler.java
```

## JNDI Lookup Pattern

```
JNDI Lookup
       │
InitialContext.lookup("concurrent/myExecutor")
       │
SerialContext (JNDI implementation)
       │
ConcurrentObjectFactory.getObjectInstance()
       │
ConcurrentRuntime.getManagedExecutorService()
       │
AbstractManagedExecutorService (from cache or new)
       │
Wrapped with ContextSetupProvider
       │
Return to application
```

## Monitoring

### Statistics Available

| Statistic | Description |
|-----------|-------------|
| `ActiveCount` | Current active task count |
| `CompletedTaskCount` | Total completed tasks |
| `QueuedTaskCount` | Tasks waiting in queue |
| `HungTaskCount` | Tasks exceeding hung threshold |
| `ThreadCount` - Current thread count |

### Monitoring Integration

- Payara Monitoring Service integration
- JMX MBeans for statistics
- Request Tracing integration
- Stuck thread detection

## Virtual Threads Support

Payara supports Project Loom virtual threads:

```xml
<managed-executor-service>
    <use-virtual-threads>true</use-virtual-threads>
</managed-executor-service>
```

Implementation classes:
- `VirtualThreadsManagedExecutorService`
- `VirtualThreadsManagedScheduledExecutorService`
- `VirtualThreadsManagedThreadFactory`

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.enterprise.concurrent-api` - Concurrency Utilities API |
| `concurro` - Core implementation library |
| `config-api` - Configuration beans |
| `nucleus-resources` - Resource infrastructure |
| `container-common` - Container integration |
| `transaction` - Transaction context |
| `resources-connector` - Resource connector |
| `monitoring-core` - Monitoring support |
| `requesttracing-core` - Request tracing |
| `healthcheck-stuck` - Stuck thread detection |
| `opentracing-adapter` - Distributed tracing |

## Usage Examples

### ManagedExecutorService

```java
@Resource(lookup = "concurrent/myExecutor")
ManagedExecutorService executor;

public void submitAsync() {
    executor.submit(() -> {
        // Runs with propagated context
        // Classloader, JNDI, Security, Transaction all available
        doWork();
    });
}
```

### ContextService

```java
@Resource(lookup = "concurrent/myContext")
ContextService contextService;

public void createContextualProxy() {
    Runnable runnable = () -> System.out.println("With context");
    Runnable contextualRunnable = contextService.createContextualProxy(runnable, Runnable.class);
    // Executes with context when run
}
```

### ManagedScheduledExecutorService

```java
@Resource(lookup = "concurrent/myScheduler")
ManagedScheduledExecutorService scheduler;

public void scheduleTask() {
    scheduler.scheduleAtFixedRate(() -> {
        // Periodic task with context
    }, 0, 1, TimeUnit.HOURS);
}
```

## Related Modules

- `nucleus/resources` - Resource infrastructure
- `appserver/common/container-common` - Container integration
- `appserver/transaction` - Transaction support
- Payara monitoring modules - Statistics integration
