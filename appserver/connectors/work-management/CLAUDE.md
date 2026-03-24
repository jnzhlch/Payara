# CLAUDE.md - Work Management

This file provides guidance for working with the `work-management` module - JCA Work Management (JSR 237) implementation.

## Module Overview

The work-management module implements JCA Work Management, providing standardized asynchronous task execution for resource adapters in Jakarta EE.

## Build Commands

```bash
# Build work-management
mvn -DskipTests clean package -f appserver/connectors/work-management/pom.xml
```

## Work Management Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Resource Adapter                             │
│  - Submits Work to WorkManager                               │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  WorkManager                                 │
│  - Schedules work execution                                  │
│  - Manages work contexts                                     │
└─────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
    WorkExecutor        ThreadPool          ContextHandler
         │                    │                    │
    ┌────▼────┐        ┌────▼────┐        ┌────▼────┐
    │  Work   │        │ Threads │        │ Security│
    │Execution│        │         │        │    Tx   │
    └─────────┘        └─────────┘        └─────────┘
```

## Core Components

### WorkManager

Main work execution interface:

```java
public interface WorkManager {
    // Execute work immediately
    void doWork(Work work, WorkListener listener)
        throws WorkException;

    // Schedule work for later execution
    long scheduleWork(Work work, WorkListener listener)
        throws WorkException;

    // Schedule work with delay
    long startWork(Work work, WorkListener listener)
        throws WorkException;

    // Create WorkManager instance
    static WorkManager createWorkManager();
}
```

### WorkContext

Execution context for work:

| Context Type | Purpose |
|--------------|---------|
| `TransactionContext` | Transaction propagation |
| `SecurityContext` | Security context |
| `ConnectionContext` - Connection management |

### WorkListener

Callback for work completion:

```java
public interface WorkListener {
    void workAccepted(WorkEvent we);
    void workRejected(WorkEvent we);
    void workStarted(WorkEvent we);
    void workCompleted(WorkEvent we);
}
```

## Work Execution Flow

```
ResourceAdapter
       │
  submitWork(work, context)
       │
┌──────▼────────┐
│  WorkManager  │
│               │
│  1. Capture context (tx, security)
│  2. Queue work
│  3. Select thread
└──────┬────────┘
       │
┌──────▼────────┐
│ WorkExecutor  │
│               │
│  4. Restore context
│  5. Execute work
│  6. Cleanup context
└───────────────┘
```

## Work Context Handlers

### Context Setup Providers

| Handler | Context |
|---------|---------|
| `TransactionContextHandler` | JTA transaction |
| `SecurityContextHandler` - JAAS security |
| `ClassLoadingContextHandler` | Classloader |

### Context Propagation

```java
public class WorkContextHandler {
    // Capture context before submission
    public WorkContext capture(Object... context);

    // Setup context before execution
    public void setup(WorkContext context);

    // Cleanup context after execution
    public void cleanup(WorkContext context);
}
```

## Monitoring

### Probe Provider

```java
@ProbeProvider(moduleName="work-management",
    probeProviderName="work-management")
public class WorkManagementProbeProvider {
    @Probe
    public void workSubmitted(String workManagerName, String workType);

    @Probe
    public void workCompleted(String workManagerName, String workType);
}
```

### Monitoring Statistics

| Statistic | Description |
|-----------|-------------|
| `activeWorkCount` | Currently executing work |
| `submittedWorkCount` | Total submitted |
| `completedWorkCount` | Total completed |
| `rejectedWorkCount` | Rejected submissions |
| `failedWorkCount` - Failed executions |

## Package Structure

```
com.sun.enterprise.connectors.work/
├── WorkManager.java                   # Work manager interface
├── WorkManagerFactory.java            # Factory
├── WorkManagerFactoryImpl.java         # Factory implementation
├── WorkContextHandler.java             # Context handler
└── monitor/
    └── WorkManagementProbeProvider.java  # Monitoring
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.resource-api` | JCA 1.7 API |
| `jakarta.authentication-api` | Authentication |
| `connectors-internal-api` | Internal APIs |
| `transaction-internal-api` | Transaction support |
| `hk2-core` | Dependency injection |
| `common-util` | Utilities |
| `security` | Security context |
| `orb-connector` | CORBA support |
| `management-api` | JMX monitoring |
| `gmbal` | Monitoring framework |
| `epicyro` | JCA implementation |

## Related Modules

- `connectors-runtime` - Core JCA runtime
- `connectors-inbound-runtime` - Inbound messaging
- `transaction` - Transaction integration
