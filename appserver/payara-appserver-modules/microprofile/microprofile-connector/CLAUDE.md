# CLAUDE.md - MicroProfile Connector

This file provides guidance for working with the `microprofile/microprofile-connector` module - Deployment infrastructure for MicroProfile applications.

## Module Overview

The microprofile-connector module provides the deployment infrastructure for MicroProfile applications in Payara. It defines the abstract base classes for sniffers, containers, and deployers that handle MicroProfile application deployment.

**Key Features:**
- **Sniffer Infrastructure** - Detects MicroProfile applications
- **Container Management** - Manages MicroProfile application lifecycle
- **Deployment Framework** - Abstract base classes for deployment

**Note:** This module contains abstract base classes. Concrete implementations are provided by specific MicroProfile modules (config, healthcheck, metrics, etc.).

## Build Commands

```bash
# Build microprofile-connector module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/microprofile-connector/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/microprofile-connector/pom.xml
```

## Architecture

### Deployment Flow

```
Application Deployment
       │
       ▼
[MicroProfileSniffer] → Detects MP application (annotations)
       │
       ▼
[MicroProfileDeployer] → Prepares and loads application
       │
       ▼
[MicroProfileContainer] → Runtime container for application
       │
       ▼
[MicroProfileApplicationContainer] → Application instance
       │
       ▼
[start/stop/suspend/resume]
```

### Component Relationships

```java
// Each MicroProfile module implements these abstract classes

// 1. Sniffer - Detects applications
public class MyModuleSniffer extends MicroProfileSniffer {
    @Override
    public boolean handles(ReadableArchive archive) {
        // Check for annotations or markers
    }
}

// 2. Container - Runtime container
public class MyModuleContainer extends MicroProfileContainer {
    @Override
    public Class<?> getConfigurationClass() {
        return MyModuleConfiguration.class;
    }
}

// 3. Deployer - Handles deployment
public class MyModuleDeployer extends MicroProfileDeployer<MyModuleContainer, MyModuleApplicationContainer> {
    @Override
    public MyModuleApplicationContainer load(MyModuleContainer container, DeploymentContext ctx) {
        // Load application
    }
}

// 4. Application Container - Application instance
public class MyModuleApplicationContainer extends MicroProfileApplicationContainer {
    @Override
    public boolean start(ApplicationContext appContext) {
        // Start application
    }
}
```

## Core Components

### MicroProfileSniffer

Abstract base class for detecting MicroProfile applications:

```java
@Contract
public abstract class MicroProfileSniffer implements Sniffer {

    @Override
    public boolean handles(DeploymentContext context) {
        final ReadableArchive archive = context.getSource();

        // Ignore system applications
        final String archivePath = archive.getURI().getPath();
        if (archivePath.contains("glassfish/lib/install") ||
            archivePath.contains("h2db/bin") ||
            archivePath.contains("mq/lib")) {
            return false;
        }

        return handles(archive);
    }

    protected abstract Class<?> getContainersClass();

    @Override
    public boolean supportsArchiveType(ArchiveType type) {
        // Supports WAR files
        return WarType.ARCHIVE_EXTENSION.equals(type.getExtension());
    }

    @Override
    public String[] getIncompatibleSnifferTypes() {
        return new String[]{"connector"};  // Incompatible with connectors
    }

    @Override
    public boolean isJavaEE() {
        return false;  // Not full Java EE
    }
}
```

**Purpose:** Detects MicroProfile applications based on annotations or markers.

### MicroProfileContainer

Abstract base class for runtime containers:

```java
public abstract class MicroProfileContainer implements Container {

    @Override
    public void start() {
        // Start container
    }

    @Override
    public void stop() {
        // Stop container
    }

    public abstract Class<?> getConfigurationClass();

}
```

**Purpose:** Manages the runtime container for a MicroProfile feature.

### MicroProfileDeployer

Abstract base class for deployment:

```java
public abstract class MicroProfileDeployer<T extends MicroProfileContainer, U extends MicroProfileApplicationContainer>
        implements Deployer<T, U> {

    @Override
    public MetaData getMetaData() {
        return null;  // No metadata required
    }

    @Override
    public <V> V loadMetaData(Class<V> clazz, DeploymentContext ctx) {
        return null;  // No metadata loading
    }

    @Override
    public boolean prepare(DeploymentContext ctx) {
        return true;  // Always prepared
    }

    @Override
    public void clean(DeploymentContext ctx) {
        // Clean up resources
    }
}
```

**Purpose:** Handles the deployment lifecycle of MicroProfile applications.

### MicroProfileApplicationContainer

Abstract base class for application instances:

```java
public abstract class MicroProfileApplicationContainer implements ApplicationContainer<Object> {

    protected final DeploymentContext ctx;
    protected final ClassLoader appClassLoader;
    protected final String appName;

    public MicroProfileApplicationContainer(DeploymentContext ctx) {
        this.ctx = ctx;
        this.appClassLoader = ctx.getFinalClassLoader();
        this.appName = ctx.getArchiveHandler()
                .getDefaultApplicationName(ctx.getSource(), ctx);
    }

    @Override
    public ClassLoader getClassLoader() {
        return appClassLoader;
    }

    @Override
    public Object getDescriptor() {
        return ctx.getModuleMetaData(Object.class);
    }

    @Override
    public boolean start(ApplicationContext appContext) throws Exception {
        return true;  // Override in subclass
    }

    @Override
    public boolean stop(ApplicationContext appContext) {
        return true;  // Override in subclass
    }

    @Override
    public boolean suspend() {
        return true;  // Override in subclass
    }

    @Override
    public boolean resume() throws Exception {
        return true;  // Override in subclass
    }
}
```

**Purpose:** Represents a deployed MicroProfile application instance.

## Package Structure

```
microprofile/microprofile-connector/
└── src/main/java/fish/payara/microprofile/connector/
    ├── MicroProfileSniffer.java               # Abstract sniffer
    ├── MicroProfileContainer.java             # Abstract container
    ├── MicroProfileDeployer.java              # Abstract deployer
    └── MicroProfileApplicationContainer.java  # Abstract app container
```

## Implementation Pattern

Each MicroProfile module extends these abstract classes:

```java
// Example: Config Module

// 1. Sniffer
@Service(name = "mp-config-sniffer")
public class ConfigSniffer extends MicroProfileSniffer {
    @Override
    protected Class<?> getContainersClass() {
        return ConfigContainer.class;
    }

    @Override
    public boolean handles(ReadableArchive archive) {
        // Check for @ConfigProperty or config sources
        return archive.exists("META-INF/microprofile-config.properties");
    }

    @Override
    public Class<?>[] getAnnotationTypes() {
        return new Class[]{ConfigProperty.class};
    }
}

// 2. Container
@Service(name = "mp-config-container")
public class ConfigContainer extends MicroProfileContainer {
    @Override
    public Class<?> getConfigurationClass() {
        return ConfigSourceConfiguration.class;
    }
}

// 3. Deployer
@Service(name = "mp-config-deployer")
public class ConfigDeployer extends MicroProfileDeployer<ConfigContainer, ConfigApplicationContainer> {
    @Override
    public ConfigApplicationContainer load(ConfigContainer container, DeploymentContext ctx) {
        return new ConfigApplicationContainer(ctx);
    }
}

// 4. Application Container
public class ConfigApplicationContainer extends MicroProfileApplicationContainer {
    @Override
    public boolean start(ApplicationContext appContext) {
        // Initialize config for this application
        return true;
    }
}
```

## Sniffer Detection

Sniffers detect applications by:

1. **Annotation Scanning** - Check for MicroProfile annotations
2. **File Markers** - Check for descriptor files
3. **Class Path** - Check for MicroProfile classes

```java
@Override
public boolean handles(ReadableArchive archive) {
    // Check for specific annotations
    if (archive.containsAnnotation(ConfigProperty.class)) {
        return true;
    }

    // Check for marker files
    if (archive.exists("META-INF/microprofile-config.properties")) {
        return true;
    }

    return false;
}
```

## Archive Type Support

MicroProfile applications are typically deployed as WAR files:

```java
@Override
public boolean supportsArchiveType(ArchiveType type) {
    String extension = type.getExtension();
    switch (extension) {
        case WarType.ARCHIVE_EXTENSION:  // .war
            return true;
        default:
            return false;
    }
}
```

**Supported:**
- WAR files (primary)
- JAR files (some modules)

**Not Supported:**
- EAR files
- RAR files (connector modules are incompatible)

## ClassLoader Management

Each application gets its own ClassLoader:

```java
protected final ClassLoader appClassLoader;

public MicroProfileApplicationContainer(DeploymentContext ctx) {
    this.appClassLoader = ctx.getFinalClassLoader();
}

// Use for loading application-specific classes
Class<?> clazz = appClassLoader.loadClass("com.example.MyClass");
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | GlassFish public APIs (Sniffer, Container, Deployer) |
| `deployment-common` | Deployment infrastructure |
| `internal-api` | Internal GlassFish APIs |

## Usage by MicroProfile Modules

This module is extended by:

| Module | Extends |
|--------|---------|
| `config` | ConfigSniffer, ConfigContainer, ConfigDeployer |
| `healthcheck` | HealthCheckSniffer, HealthCheckContainer |
| `metrics` | MetricsSniffer, MetricsContainer |
| `fault-tolerance` | FaultToleranceSniffer, FaultToleranceContainer |
| `jwt-auth` | JWTAuthSniffer, JWTAuthContainer |
| `openapi` | OpenAPISniffer, OpenAPIContainer |
| `rest-client` | RestClientSniffer, RestClientContainer |

## Notes

- **Abstract Base Classes** - This module only provides abstract classes
- **Implementation Required** - Each MicroProfile module implements these abstract classes
- **WAR Deployment** - MicroProfile apps are typically deployed as WAR files
- **No Java EE** - Marked as `isJavaEE() = false` for lightweight deployment
- **Incompatible with Connectors** - Cannot be used with Java EE connectors
