# CLAUDE.md - Nucleus Common

This file provides guidance for working with the `nucleus/common` module - shared APIs, utilities, and infrastructure for Payara Nucleus.

## Module Overview

The `nucleus/common` module provides foundational APIs, utilities, and management infrastructure used across all of Payara. It contains public APIs, internal APIs, common utilities, JMX management support, and the AMX (Appserver Management Extensions) framework.

## Submodules

### API Modules

- **simple-glassfish-api** - Minimal public APIs for embedded GlassFish (`org.glassfish.embeddable.*`)
- **glassfish-api** - Core public APIs including admin command framework, progress tracking, container interfaces, and REST administration
- **internal-api** - Private/internal APIs for deployment, class loading, embedded server, and Payara-specific features (notification, hot deploy)

### Utility Modules

- **common-util** - Shared utilities including:
  - Caching implementations (LRU, bounded multi-LRU)
  - Logging infrastructure and configuration
  - I/O utilities (SmartFile, FileUtils)
  - Process management (ProcessManager, ClassRunner)
  - XML parsing (MiniXmlParser for domain.xml)
  - Security utilities (password handling, truststores)
  - Call flow monitoring
  - GlassFish system utilities (ASenvPropertyReader, GFSystem)

### Infrastructure Modules

- **mbeanserver** - JMX MBeanServer instantiation and support
- **scattered-archive-api** - Scattered archive deployment APIs
- **amx-core** - Appserver Management Extensions (AMX) - JMX-based management and monitoring framework

### Localization

Each major module has a corresponding `-l10n` module for internationalization:
- **common-util-l10n**, **internal-api-l10n**, **glassfish-api-l10n**, **mbeanserver-l10n**

## Build Commands

```bash
# Build entire nucleus/common module
mvn -DskipTests clean package -f nucleus/common/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f nucleus/common/common-util/pom.xml
mvn -DskipTests clean package -f nucleus/common/glassfish-api/pom.xml
mvn -DskipTests clean package -f nucleus/common/internal-api/pom.xml

# Build with tests
mvn clean package -f nucleus/common/pom.xml

# Run specific submodule tests
mvn test -f nucleus/common/common-util/pom.xml
mvn test -f nucleus/common/amx-core/pom.xml
```

## Architecture Patterns

### Admin Command Framework (glassfish-api)

Admin commands are the foundation of Payara's CLI and REST administration:

```java
@Service
@Scoped(PerLookup.class)
public class MyCommand implements AdminCommand {
    @Override
    public void execute(AdminCommandContext context) {
        // Command implementation
    }
}
```

**Key annotations:**
- `@Param` - Command parameters
- `@ExecuteOn` - Target execution (instance vs cluster)
- `@AccessRequired` - Authorization requirements

### Deployment APIs (internal-api)

The deployment infrastructure uses **Sniffers** to detect application types and **Deployers** to handle deployment:

- **Sniffer** - Detects application type (e.g., web, ejb, jax-rs)
- **Deployer** - Handles metadata loading and application container integration
- **ApplicationInfo** - Holds deployed application metadata
- **EngineInfo** - Container integration point

### AMX Management (amx-core)

AMX provides JMX MBeans for managing and monitoring Payara:

- **DomainRoot** - Root AMX MBean
- **Query** - MBean query support
- **Logging** - Log query and analysis
- **Monitoring** - Runtime monitoring MBeans

AMX can be enabled/disabled via `asadmin set-amx-enabled`.

### Utilities (common-util)

**Caching:**
- `LruCache` - Simple LRU cache
- `BoundedMultiLruCache` - Multi-key bounded cache
- All cache implementations have JMX monitoring variants

**Process Management:**
- `ProcessManager` - Execute and manage external processes
- `LocalAdminCommand` - Run asadmin commands programmatically

**XML Parsing:**
- `MiniXmlParser` - Lightweight domain.xml parser (used during startup)
- `TokenResolver` - Replace system property tokens in config

## Testing

```bash
# Run all common tests
mvn test -f nucleus/common/pom.xml

# Run specific test class
mvn test -Dtest=DurationTest -f nucleus/common/common-util/pom.xml
mvn test -Dtest=AMXConfigTest -f nucleus/common/amx-core/pom.xml

# Run tests in specific submodule
mvn test -f nucleus/common/internal-api/pom.xml
```

**Test locations:**
- `common-util/src/test/java/` - Utility tests
- `glassfish-api/src/test/java/` - API tests
- `internal-api/src/test/java/` - Internal API tests
- `amx-core/src/test/java/` - AMX tests

## Module Dependencies

**Dependency order (bottom to top):**
```
scattered-archive-api
    ↓
simple-glassfish-api
    ↓
glassfish-api → scattered-archive-api
    ↓
common-util → glassfish-api
    ↓
mbeanserver → glassfish-api, common-util
    ↓
internal-api → glassfish-api, common-util, config-api
    ↓
amx-core → internal-api, mbeanserver, kernel
```

## Payara-Specific Features

### Notification Framework (internal-api)

Located in `fish.payara.internal.notification`:

- `PayaraNotification` - Notification event representation
- `PayaraNotifier` - Notifier interface
- `PayaraNotifierConfiguration` - Configuration base class
- `EventLevel` - Notification severity levels

### Hot Deploy (internal-api)

Located in `fish.payara.nucleus.hotdeploy`:

- `HotDeployService` - Hot deployment detection and reload
- `ApplicationState` - Application state tracking

## Packaging Type

Most submodules use `glassfish-jar` packaging (custom Maven plugin), which:
- Generates OSGi manifests
- Handles module exports/imports
- IncludesHK2 component metadata

## Key Packages by Module

**common-util:**
- `com.sun.enterprise.util.*` - General utilities
- `com.sun.enterprise.universal.*` - Universal utilities (process, I/O, XML)
- `com.sun.common.util.logging.*` - Logging infrastructure
- `org.glassfish.common.util.*` - GlassFish-specific utilities

**glassfish-api:**
- `org.glassfish.api.admin.*` - Administration framework
- `org.glassfish.api.*` - Core container APIs
- `org.glassfish.api.invocation.*` - Invocation context

**internal-api:**
- `org.glassfish.internal.deployment.*` - Deployment infrastructure
- `org.glassfish.internal.data.*` - Application registry and metadata
- `org.glassfish.internal.embedded.*` - Embedded server APIs
- `org.glassfish.internal.api.*` - Internal service APIs
- `fish.payara.internal.*` - Payara-specific APIs

**amx-core:**
- `org.glassfish.admin.amx.*` - AMX public APIs
- `org.glassfish.admin.amx.impl.*` - AMX implementations
- `org.glassfish.admin.amx.logging.*` - Log query/analysis
