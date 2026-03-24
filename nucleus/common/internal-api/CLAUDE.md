# CLAUDE.md - Internal API

This file provides guidance for working with the `internal-api` module - private/internal APIs for Payara.

## Module Overview

The `internal-api` module provides **private** APIs used internally by Payara components. These are NOT part of the public API contract and may change between releases.

**Key areas:**
- Deployment infrastructure (sniffers, deployers, application lifecycle)
- Embedded server APIs
- Class loading and class loader hierarchy
- Internal service APIs
- Payara-specific features (notification, hot deploy)

## Build Commands

```bash
# Build this module only
mvn -DskipTests clean package -f nucleus/common/internal-api/pom.xml

# Build with tests
mvn clean package -f nucleus/common/internal-api/pom.xml

# Run specific test
mvn test -Dtest=DeploymentOrderTest -f nucleus/common/internal-api/pom.xml
```

## Deployment Infrastructure

### Sniffer - Application Type Detection

```java
@Service
public class MySniffer implements Sniffer {

    @Override
    public boolean handles(DeploymentContext context) {
        // Detect if this archive is our type
        ArchiveType type = context.getArchiveHandler().getArchiveType();
        return type == ArchiveType.WAR;
    }

    @Override
    public String[] getURLPatterns() {
        return new String[]{"*.mypattern"};
    }

    @Override
    public Container[] getContainers() {
        return new Container[]{
            habitat.getService(MyContainer.class)
        };
    }
}
```

### Deployer - Application Deployment

```java
@Service
public class MyDeployer implements Deployer<Object, MyContainer> {

    @Override
    public Metadata loadMetadata(DeploymentContext context) {
        // Load deployment descriptors
        return new MyMetadata(...);
    }

    @Override
    public boolean prepare(DeploymentContext context) {
        // Validate and prepare deployment
        return true;
    }

    @Override
    public MyContainer load(Container container, DeploymentContext context) {
        // Load application into container
        return (MyContainer) container;
    }

    @Override
    public void unload(MyContainer container, DeploymentContext context) {
        // Unload application
    }

    @Override
    public void clean(DeploymentContext context) {
        // Clean up resources
    }
}
```

### ApplicationLifecycleInterceptor

```java
// Intercept deployment lifecycle
@Service
public class MyDeploymentInterceptor implements ApplicationLifecycleInterceptor {

    @Override
    public void preDeploy(DeploymentContext context) {
        // Before deployment
    }

    @Override
    public void postDeploy(DeploymentContext context) {
        // After deployment
    }

    @Override
    public void preUndeploy(DeploymentContext context) {
        // Before undeployment
    }

    @Override
    public void postUndeploy(DeploymentContext context) {
        // After undeployment
    }
}
```

### Deployment Order

```java
// Control application startup order
@DeploymentOrder(value = 100)
public class MyApplicationLifecycle {

    // Lower values load earlier
}
```

## Application Registry

### ApplicationInfo

```java
// Get application metadata
ApplicationRegistry registry = habitat.getService(ApplicationRegistry.class);
ApplicationInfo appInfo = registry.get("myapp");

// Application details
String appName = appInfo.getName();
File source = appInfo.getSource();
ApplicationLifecycle lifecycle = appInfo.getAppLifecycle();

// Get modules
List<ModuleInfo> modules = appInfo.getModuleInfos();
```

### ModuleInfo

```java
// Module information
ModuleInfo module = ...;
String moduleName = module.getName();
EngineInfo engineInfo = module.getEngineInfo();
```

### EngineInfo

```java
// Container integration
EngineInfo engine = ...;
Container container = engine.getContainer();
ApplicationContainer appContainer = engine.getApplicationContainer();

// Start/stop
engine.start(getClassLoader());
engine.stop();
```

## Embedded Server APIs

### Embedded Lifecycle

```java
// Build and start embedded server
Server.Builder builder = new Server.Builder("my-server");
builder.httpPort(8080);
builder.build();
Server server = builder.lookup();

// Or use DomBuilder
DomBuilder domBuilder = new DomBuilder();
Server server = domBuilder.build(domainXmlFile);
```

### EmbeddedContainer

```java
// Create specific containers
ContainerBuilder<MyContainer> cb = ContainerBuilder.create();
MyContainer container = cb.container(habitat.getService(MyContainer.class))
    .build();

// Start container
cb.start();
```

### ScatteredArchive

```java
// Deploy scattered (exploded) archive
ScatteredArchive archive = new ScatteredArchive("myapp", Type.WAR);
archive.addClassPath(new File("target/classes"));

EmbeddedDeployer deployer = server.getDeployer();
deployer.deploy(archive.toURI());
```

## Class Loading

### ClassLoaderHierarchy

```java
// Get class loader service
ClassLoaderHierarchy clh = habitat.getService(ClassLoaderHierarchy.class);

// Create class loader
ClassLoader commonCL = clh.getCommonClassLoader();
ClassLoader containerCL = clh.createContainerClassLoader(...);
```

### DelegatingClassLoader

```java
// Class loader with delegation
DelegatingClassLoader dcl = new DelegatingClassLoader(parent);
dcl.addDelegate(delegateClassLoader);
```

### ConnectorClassLoaderService

```java
// Resource adapter class loading
ConnectorClassLoaderService ccs = habitat.getService(ConnectorClassLoaderService);
ClassLoader connectorCL = ccs.createConnectorClassLoader(rarLocation);
```

## Internal Services

### Globals

```java
// Access global habitat/service locator
ServiceLocator habitat = Globals.getDefaultHabitat();

// Static access to common services
ServerContext serverContext = Globals.get(ServerContext.class);
```

### ServerContext

```java
// Server context information
ServerContext ctx = habitat.getService(ServerContext.class);

File installRoot = ctx.getInstallRoot();
File instanceRoot = ctx.getInstanceRoot();
String instanceName = ctx.getInstanceName();
```

### RunLevel Services

```java
// Custom run level service
@Service
@RunLevel(value = 10) // Runs at run level 10
public class MyStartupService implements PostConstruct {

    @Override
    public void postConstruct() {
        // Startup logic
    }
}
```

## Payara-Specific Features

### Notification Framework

```java
// Payara notification system (fish.payara.internal.notification)

// Notification event
PayaraNotification notification = new PayaraNotificationBuilder()
    .setSubject("Test Subject")
    .setMessage("Test Message")
    .setEventLevel(EventLevel.WARNING)
    .build();

// Notifier configuration
@Configured
public class MyNotifierConfiguration implements PayaraNotifierConfiguration {
    @Attribute
    private boolean enabled;

    @Attribute(defaultValue = "INFO")
    private String notificationLevel;
}

// Notifier implementation
@Service
public class MyNotifier implements PayaraNotifier {
    @Override
    public void notify(PayaraNotification notification) {
        // Send notification
    }

    @Override
    public void configure(MyNotifierConfiguration config) {
        // Apply configuration
    }
}

// Admin commands
@Service
@Scoped(PerLookup.class)
public class SetMyNotifierConfiguration extends BaseSetNotifierConfigurationCommand {
    // Subclass provides configuration implementation
}
```

### Hot Deploy

```java
// Hot deploy service (fish.payara.nucleus.hotdeploy)

@Service
public class HotDeployService {

    // Check for application changes
    public ApplicationState checkForChanges(ApplicationInfo appInfo) {
        // Returns CHANGED, UNCHANGED, etc.
    }

    // Get annotation processor state
    public AnnotationProcessorState getAnnotationProcessorState() {
        // Tracks annotation processing for hot reload
    }
}
```

### Application Deployment Time

```java
// Track deployment time (fish.payara.api.admin.config)

@Configured
public interface ApplicationDeploymentTime {
    @Attribute(defaultValue = "-1")
    long getDeploymentTime();

    void setDeploymentTime(long time);

    // For measuring deployment duration
}
```

## Deployment Tracing

### DeploymentTracing

```java
// Trace deployment for debugging
@Service
public class MyDeploymentTracer implements DeploymentTracing {

    @Override
    public void traceStart(String span) {
        // Start trace span
    }

    @Override
    public void traceEnd(String span) {
        // End trace span
    }
}
```

### StructuredDeploymentTracing

```java
// Structured tracing with parent-child relationships
StructuredDeploymentTracing tracing = ...;
TraceContext context = tracing.newTrace("deployment-phase");

try (TraceContext.Span span = context.start("sub-task")) {
    // Do work
}
```

## Private/Public Annotations

```java
// API visibility control
@Public  // Public API (stable)
@Private // Private API (may change)
```

## Testing

### Test Support

```java
// Server directories for testing
ServerDirs testDirs = ServerDirs.makeTestServerDirs();

// Create test deployment context
DeploymentContext context = ...;
ExtendedDeploymentContext extContext =
    new DeploymentContextImpl(testDirs, ...);
```

## Architecture Patterns

**Package organization:**
- `org.glassfish.internal.api.*` - Internal service APIs
- `org.glassfish.internal.deployment.*` - Deployment infrastructure
- `org.glassfish.internal.data.*` - Application registry
- `org.glassfish.internal.embedded.*` - Embedded server
- `org.glassfish.flashlight.*` - Monitoring/flashlight
- `fish.payara.internal.*` - Payara-specific features

## Dependencies

**Runtime:**
- `glassfish-api` - Public APIs
- `common-util` - Utilities
- `admin/config-api` - Configuration backend
- HK2 `hk2-core`, `hk2-config` - Dependency injection
- `payara-api` - Payara public APIs

## Important Notes

**These are PRIVATE APIs:**
- May change between releases without notice
- Not covered by API compatibility guarantees
- Use at your own risk
- Prefer `glassfish-api` for stable public interfaces

## Related Modules

- `glassfish-api` - Public APIs
- `admin/cli` - CLI implementation
- `admin/rest` - REST implementation
- `deployment/*` - Deployment implementations
- `core/kernel` - HK2 integration

## Common Patterns

### 1. Custom Sniffer

```java
@Service
public class CustomSniffer implements Sniffer {
    @Override
    public boolean handles(DeploymentContext ctx) {
        return ctx.getSource().getArchive()
            .exists("META-INF/custom-extension.xml");
    }
}
```

### 2. Custom Deployer

```java
@Service
public class CustomDeployer implements Deployer<X, CustomContainer> {
    @Override
    public X load(DeploymentContext ctx) {
        // Parse custom descriptor
        return parseCustomConfig(ctx.getSource());
    }
}
```

### 3. Deployment Interceptor

```java
@Service
@DeploymentOrder(100)
public class CustomInterceptor implements ApplicationLifecycleInterceptor {
    @Override
    public void postDeploy(DeploymentContext ctx) {
        // Custom post-deployment logic
    }
}
```

### 4. Access Application Registry

```java
ApplicationRegistry registry = habitat.getService(ApplicationRegistry.class);
for (ApplicationInfo app : registry.getAll()) {
    System.out.println("App: " + app.getName());
}
```
