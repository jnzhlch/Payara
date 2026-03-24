# CLAUDE.md - GlassFish API

This file provides guidance for working with the `glassfish-api` module - core public APIs.

## Module Overview

The `glassfish-api` module provides the fundamental public APIs for Payara/GlassFish. This includes:

- **Admin Command Framework** - CLI and REST command infrastructure
- **Container APIs** - Application container integration points
- **Progress Tracking** - Long-running operation progress
- **Configuration APIs** - Domain configuration interfaces
- **REST Annotations** - REST endpoint configuration

This is a foundational module - most other modules depend on it.

## Build Commands

```bash
# Build this module only
mvn -DskipTests clean package -f nucleus/common/glassfish-api/pom.xml

# Build with tests
mvn clean package -f nucleus/common/glassfish-api/pom.xml

# Run specific test
mvn test -Dtest=ProgressStatusImplTest -f nucleus/common/glassfish-api/pom.xml
```

## Admin Command Framework

### Creating an Admin Command

```java
@Service // HK2 service annotation
@Scoped(PerLookup.class) // New instance per command execution
@ExecuteOn(RuntimeType.ALL) // Run on all instance types
public class MyCommand implements AdminCommand {

    @Inject
    private CommandRunner runner;

    @Param(name = "name", primary = true)
    private String name;

    @Param(name = "optional", alias = "o", optional = true, defaultValue = "default")
    private String optional;

    @Override
    public void execute(AdminCommandContext context) {
        ActionReport report = context.getActionReport();
        try {
            // Command logic
            report.setMessage("Command executed successfully");
            report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
        } catch (Exception e) {
            report.setMessage("Error: " + e.getMessage());
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            report.setFailureCause(e);
        }
    }
}
```

### Parameter Annotations

```java
@Param(name = "port", shortName = "p", defaultValue = "8080")
private int port;

@Param(name = "enabled", acceptableValues = "true,false", defaultValue = "true")
private boolean enabled;

@Param(name = "password", password = true)
private String password;

@Param(name = "targets", optional = true, multiple = true)
private List<String> targets;

@Param(optional = true)
private String paramWithDefaultCalculator;

@ParamDefaultCalculator  // Custom default value calculator
public String calculateDefault() {
    return System.getProperty("user.name");
}
```

### Command Execution

```java
// Programmatic command execution
CommandRunner runner = habitat.getService(CommandRunner.class);
ActionReport report = runner.getActionReport();

// Execute with ParameterMap
ParameterMap params = new ParameterMap();
params.set("name", "myResource");
params.set("port", "8080");

runner.getCommandInvocation("create-my-resource", report).parameters(params).execute();

// Check result
if (report.getActionExitCode() == ActionReport.ExitCode.SUCCESS) {
    // Success
}
```

### Command Metadata

```java
// CommandModel provides command metadata
CommandModel model = CommandModelProvider.class.cast(command).getModel();
for (CommandModel.ParamModel param : model.getParameters()) {
    String name = param.getName();
    boolean isOptional = param.getParam().optional();
    // ...
}
```

## Progress Tracking

### ProgressStatus

For long-running operations:

```java
public class LongRunningCommand implements AdminCommand {

    @Override
    public void execute(AdminCommandContext context) {
        ProgressStatus progress = context.getProgressStatus();

        // Set total steps
        progress.total(100);

        for (int i = 0; i < 100; i++) {
            // Do work
            doWork();

            // Update progress
            progress.progress(1, "Processing item " + i);
        }

        // Complete
        progress.complete("Done");
    }
}
```

### Job Management

```java
// Create managed job
JobManager jobManager = habitat.getService(JobManager.class);
Job job = jobManager.createJob();
job.setJobName("Long operation");
job.setCommandName("my-command");

// Save job state
job.registerForExecution();

// Query job status
JobInfo jobInfo = jobManager.getJob(jobId);
JobState state = jobInfo.getState(); // RUNNING, COMPLETED, etc.

// Cancel job
jobManager.cancelJob(jobId);
```

## Container APIs

### Container Integration

```java
// Container for application types
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

    // Container lifecycle
}
```

### Sniffer - Application Type Detection

```java
@Service
public class MySniffer implements Sniffer {

    @Override
    public String[] getURLPatterns() {
        return new String[]{"*.myext"};
    }

    @Override
    public boolean handles(DeploymentContext context) {
        // Detect if this is our application type
        return context.getSource().getArchive()
            .exists("META-INF/my-config.xml");
    }

    @Override
    public Container[] getContainers() {
        return new Container[]{habitat.getService(MyContainer.class)};
    }
}
```

## Configuration APIs

### Configuration Beans

```java
// Configuration beans use @Configured annotation
@Configured
public interface MyConfig {
    String getName();
    void setName(String name);

    @Attribute
    int getPort();

    @Element
    List<MySubConfig> getSubConfigs();

    @DuckTyped
    default String getDisplayName() {
        return "MyConfig: " + getName();
    }
}
```

### Domain Initialization

```java
// DomainInitializer runs during domain startup
@Service
public class MyDomainInitializer implements DomainInitializer {

    @Override
    public void initialize(DomainContext context) {
        // Custom initialization logic
    }

    @Override
    public int getPrecedence() {
        // Lower precedence runs earlier
        return 100;
    }
}
```

## REST Administration

### REST Endpoints

```java
@Service
@RestEndpoints({
    @RestEndpoint(configBean = MyConfig.class,
        domainPath = "/my-config",
        endpointType = EndpointType.CLASS,
        description = "My Configuration")
})
public class MyConfigRestResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<MyConfig> get() {
        // Return all configs
    }

    @GET
    @Path("{name}")
    public MyConfig get(@PathParam("name") String name) {
        // Return specific config
    }

    @POST
    public Response create(MyConfig config) {
        // Create new config
    }
}
```

### RestParam

For REST endpoint parameters:

```java
@RestParam(name = "id", updateOnly = true)
private String id;

@RestParam(ignore = true)
private transient String internalField;
```

## Command Aspects

### CommandExecutionAspect

```java
// Wrap command execution with custom logic
public class MyCommandAspect implements CommandAspect {

    @Override
    public void before(CommandInvocation inv) {
        // Before command execution
    }

    @Override
    public void after(CommandInvocation inv, ActionResult result) {
        // After command execution
    }
}
```

### Supplemental Commands

```java
// Additional commands that run with main command
@Service
public class MySupplementalCommand implements AdminCommand {

    @Override
    public void execute(AdminCommandContext context) {
        // Supplemental logic
    }
}

// Register supplemental
@Supplemental(TARGET_COMMAND)
@Supplemental(value = SUPPLEMENTAL_COMMAND, on = Supplemental.Timing.After)
public class TargetCommand implements AdminCommand { ... }
```

## Cluster/Instance Commands

### ExecuteOn

```java
// Control where command runs
@ExecuteOn(RuntimeType.DAS)  // Only on DAS
@ExecuteOn(RuntimeType.INSTANCE)  // Only on instances
@ExecuteOn(RuntimeType.ALL)  // Everywhere (default)

// Target specification
@ExecuteOn(value = RuntimeType.INSTANCE, ifRunning = IfRunning.ALWAYS)
```

### ClusterExecutor

```java
// Execute command across cluster
ClusterExecutor executor = habitat.getService(ClusterExecutor.class);

// Run on all instances
ClusterOperationResult result = executor.invoke(
    new MyClusterCommand(),
    new ParameterMap()
);

// Run on specific instances
InstanceCommandResult icr = executor.invokeOnInstance(
    "my-instance",
    "my-command",
    params
);
```

## Authorization

### AccessRequired

```java
// Command authorization
@AccessRequired(resource="my-resource", action="read")
public class MyCommand implements AdminCommand { ... }

// Multiple permissions
@AccessRequired({
    @AccessRequired.Resource(resource="domain", action="read"),
    @AccessRequired.Resource(resource="server", action="write")
})
public class AdminCommand { ... }
```

### Command Security

```java
@Service
public class MySecurityCommand implements AdminCommand {

    @Inject
    private AdminCommandSecurity security;

    @Override
    public void execute(AdminCommandContext context) {
        // Check authorization
        if (!security.isUserInRole("admin")) {
            throw new AuthorizationException();
        }
    }
}
```

## Server Environment

### ServerEnvironment

```java
// Get server environment
ServerEnvironment env = habitat.getService(ServerEnvironment.class);

ServerEnvironment.Status status = env.getStatus();
boolean isInstance = env.isInstance();
boolean isDas = env.isDas();

// Get paths
File installRoot = env.getInstallRoot();
File domainRoot = env.getDomainRoot();
File configDir = env.getConfigDir();
```

## Related Modules

- **internal-api** - Internal command execution APIs
- **admin/cli** - CLI implementation
- **admin/rest** - REST implementation
- **admin/config-api** - Configuration backend

## Common Patterns

### 1. Create Simple Command

```java
@Service @Scoped(PerLookup.class)
public class SimpleCommand implements AdminCommand {
    @Param(name = "message")
    private String msg;

    @Override
    public void execute(AdminCommandContext context) {
        context.getActionReport().setMessage("Echo: " + msg);
    }
}
```

### 2. Command with Progress

```java
public void execute(AdminCommandContext context) {
    ProgressStatus ps = context.getProgressStatus();
    ps.total(stages.size());

    for (Stage stage : stages) {
        ps.progress(1, "Executing: " + stage.getName());
        stage.execute();
    }
    ps.complete("All stages complete");
}
```

### 3. Access Configuration

```java
@Inject
private Domain domain;

@Inject @Named(ServerEnvironment.DEFAULT_INSTANCE_NAME)
private Server server;

public void execute(AdminCommandContext context) {
    // Access config
    MyConfig config = server.getExtensionByType(MyConfig.class);
    String value = config.getSomeProperty();
}
```

### 4. Subcommand Execution

```java
@Inject
private CommandRunner runner;

public void execute(AdminCommandContext context) {
    ParameterMap params = new ParameterMap();
    params.set("name", "value");

    ActionReport subReport = context.getActionReport().addSubActionsReport();
    CommandInvocation inv = runner.getCommandInvocation(
        "sub-command-name", subReport, context.getSubject()
    );
    inv.parameters(params).execute();
}
```
