# CLAUDE.md - Kernel

This file provides guidance for working with the `kernel` module - the heart of Payara server.

## Module Overview

The `kernel` module contains the core server runtime services. It's responsible for:

- Application lifecycle (deployment/undeployment)
- Server startup and shutdown
- Command execution infrastructure
- Container management
- Class loader hierarchy
- Job management
- Admin adapters (REST, CLI)

## Build Commands

```bash
# Build kernel module
mvn -DskipTests clean package -f nucleus/core/kernel/pom.xml

# Build with tests
mvn clean package -f nucleus/core/kernel/pom.xml

# Run specific test
mvn test -Dtest=ApplicationLifecycleTest -f nucleus/core/kernel/pom.xml
```

## Architecture

### Server Startup

```
AppServerStartup (RunLevel service)
    │
    ├─→ DomainCreationStartup     # Domain initialization
    │
    ├─→ ApplicationLoaderService  # Load applications
    │
    ├─→ ContainerStarter          # Start containers
    │
    └─→ ServerLifecycle          # Server lifecycle
```

### Key Services

| Service | Package | Purpose |
|---------|---------|---------|
| `AppServerStartup` | `com.sun.enterprise.v3.server` | Server startup orchestrator |
| `ApplicationLifecycle` | `com.sun.enterprise.v3.server` | App deployment lifecycle |
| `CommandRunnerImpl` | `com.sun.enterprise.v3.admin` | Command execution |
| `ServerContext` | `com.sun.enterprise.v3.server` | Server context information |
| `ClassLoaderHierarchyImpl` | `com.sun.enterprise.v3.server` | Class loader hierarchy |

## Application Lifecycle

### Deployment Flow

```java
// ApplicationLifecycle deployment
ApplicationLifecycle lifecycle = habitat.getService(ApplicationLifecycle.class);

// Deploy application
ExtendedDeploymentContext context = ...;
ApplicationInfo info = lifecycle.deploy(context);

// Undeploy
lifecycle.undeploy(appInfo, context);
```

### Container Integration

```java
// Container registration
@Service
public class MyContainer implements Container {

    @Override
    public String getName() {
        return "MyContainer";
    }

    @Override
    public Class<? extends Sniffer> getSniffer() {
        return MySniffer.class;
    }
}
```

### Application Order

Control application startup order:

```java
@DeploymentOrder(value = 100)
public class MyApplicationLifecycle {
    // Lower values load earlier
}
```

## Command Execution

### Command Runner

```java
// Get CommandRunner service
CommandRunner runner = habitat.getService(CommandRunner.class);

// Execute command
ActionReport report = habitat.getService(ActionReport.class);
CommandInvocation inv = runner.getCommandInvocation("list-applications", report);
inv.parameters(new ParameterMap()).execute();

// Check result
if (report.getActionExitCode() == ActionReport.ExitCode.SUCCESS) {
    String output = report.getMessage();
}
```

### Admin Adapter Types

| Adapter | Package | Purpose |
|---------|---------|---------|
| `AdminAdapter` | `com.sun.enterprise.v3.admin` | HTTP/S admin requests |
| `PublicAdminAdapter` | `com.sun.enterprise.v3.admin` | Public REST endpoints |
| `PrivateAdminAdapter` | `com.sun.enterprise.v3.admin` | Private admin endpoints |

### Job Management

```java
// JobManagerService
JobManager jobManager = habitat.getService(JobManager.class);

// Create job
Job job = jobManager.createJob();
job.setCommandName("my-command");

// Persist job
job.registerForExecution();

// Query job
JobInfo info = jobManager.getJob(jobId);

// Complete job
job.setState(JobState.COMPLETED);
job.setJobResult("Success");
```

## Class Loading

### Class Loader Hierarchy

```
CommonClassLoader (shared libraries)
    │
    ├─→ APIClassLoader (public APIs)
    │
    ├─→ AppLibClassLoader (application libraries)
    │
    └─→ ConnectorClassLoader (resource adapters)
            │
            └─→ ApplicationClassLoader (per-application)
```

### Class Loader Services

```java
// Get class loader service
ClassLoaderHierarchy clh = habitat.getService(ClassLoaderHierarchy.class);

// Create application class loader
ClassLoader appCL = clh.createApplicationClassLoader(appName, parent);

// Common class loader
ClassLoader commonCL = clh.getCommonClassLoader();
```

## Server Context

### Server Information

```java
// ServerContext
ServerContext ctx = habitat.getService(ServerContext.class);

// Server details
String instanceName = ctx.getInstanceName();
File installRoot = ctx.getInstallRoot();
File instanceRoot = ctx.getInstanceRoot();

// Server state
boolean isDas = ctx.isDas();
boolean isInstance = ctx.isInstance();
```

### Runtime Status

```java
// RuntimeType
RuntimeType type = habitat.getService(RuntimeType.class);

// Check server type
if (type == RuntimeType.DAS) {
    // Running as DAS
} else if (type == RuntimeType.INSTANCE) {
    // Running as standalone instance
}
```

## Event System

### Kernel Events

```java
// Events service
Events events = habitat.getService(Events.class);

// Register listener
events.register(new EventListener() {
    @Override
    public void event(Event event) {
        if (event.is(EventTypes.PREPARE_SHUTDOWN)) {
            // Handle shutdown
        }
    }
});
```

### Event Types

| Event | Description |
|-------|-------------|
| `PREPARE_SHUTDOWN` | Server shutting down |
| `SERVER_READY` | Server ready for requests |
| `APPLICATION_DEPLOYED` | Application deployed |
| `APPLICATION_UNDEPLOYED` | Application undeployed |

## Admin Commands

### Kernel Commands

The kernel module provides core admin commands:

```bash
# Server management
asadmin start-domain
asadmin stop-domain
asadmin restart-domain

# Information
asadmin version
asadmin list-applications
asadmin list-containers
asadmin list-modules

# JVM
asadmin list-jvm-options
asadmin create-jvm-options
asadmin delete-jvm-options

# Logging (kernel-level)
asadmin list-loggers
asadmin set-log-level
```

### Custom Commands

```java
@Service @Scoped(PerLookup.class)
public class MyCommand implements AdminCommand {

    @Inject
    private ServiceLocator habitat;

    @Param(name = "name", primary = true)
    private String name;

    @Override
    public void execute(AdminCommandContext context) {
        ActionReport report = context.getActionReport();
        report.setMessage("Executed: " + name);
        report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
    }
}
```

## Dynamic Reload

### Dynamic Reload Service

```java
// DynamicReloadService
@Service
public class DynamicReloadService implements Monitor {

    @Override
    public void fileChanged(File changedFile) {
        // Trigger reload
        ApplicationLifecycle lifecycle = habitat.getService(...);
        lifecycle.reload(changedFile);
    }
}
```

## Monitoring Integration

### Probe Providers

```java
// Monitoring probes
@ProbeProvider(moduleName = "kernel",
               providerName = "server",
               eventType = "thread-pool")
public interface ThreadPoolProbeProvider {

    @Probe(name = "threadReturned")
    void threadReturnedEvent(
        @ProbeParam("threadName") String threadName,
        @ProbeParam("poolId") String poolId
    );
}
```

## HK2 Integration

### Service Injection

```java
@Service // Register as HK2 service
@Scoped(Singleton.class) // Scope
public class MyKernelService {

    @Inject
    private ServiceLocator habitat;

    @Inject
    private ApplicationLifecycle appLifecycle;

    @Inject @Named(ServerEnvironment.DEFAULT_INSTANCE_NAME)
    private Server server;

    public void doSomething() {
        // Service implementation
    }
}
```

## RunLevel Services

### Startup Ordering

```java
// RunLevel controls startup order
@Service
@RunLevel(mode = RunLevel.RUNLEVEL_MODE_NON_VALIDATING, value = 10)
public class MyStartupService implements PostConstruct {

    @Override
    public void postConstruct() {
        // Runs at run level 10
    }
}
```

### Run Level Values

| Level | Service Type |
|-------|--------------|
| 1 | Validation |
| 2-5 | System initialization |
| 6-9 | Core services |
| 10 | Post-startup |

## Related Modules

- **bootstrap** - Server startup
- **logging** - Logging infrastructure
- **admin/config-api** - Configuration
- **admin/cli** - CLI implementation
- **deployment** - Deployment infrastructure

## Common Patterns

### 1. Server Startup Hook

```java
@Service
@RunLevel(value = 10)
public class MyStartupService implements PostConstruct {

    @Override
    public void postConstruct() {
        // Called after server startup
    }
}
```

### 2. Application Lifecycle Hook

```java
@Service
public class MyLifecycleInterceptor implements ApplicationLifecycleInterceptor {

    @Override
    public void postDeploy(DeploymentContext ctx) {
        // After application deployed
    }
}
```

### 3. Get Server Context

```java
ServerContext ctx = Globals.get(ServerContext.class);
String instanceName = ctx.getInstanceName();
boolean isDas = ctx.isDas();
```

### 4. Execute Command Programmatically

```java
CommandRunner runner = habitat.getService(CommandRunner.class);
ActionReport report = habitat.getService(ActionReport.class);
runner.getCommandInvocation("list-applications", report)
    .execute();
```
