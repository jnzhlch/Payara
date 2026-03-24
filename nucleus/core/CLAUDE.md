# CLAUDE.md - Nucleus Core

This file provides guidance for working with the Nucleus Core modules - the kernel and foundational services of Payara.

## Module Overview

The core module provides the kernel, HK2 injection framework, OSGi platform integration, logging, and server bootstrap.

## Sub-Modules

### Bootstrap (`bootstrap/`)
Server startup and initialization.

**Key responsibilities:**
- Main entry point (`ASMain.java`)
- Platform initialization
- Module system startup (OSGi/HK2)
- Domain initialization

### Kernel (`kernel/`)
The heart of Payara - HK2-based kernel.

**Key packages:**
- `org.glassfish.kernel.*` - Core kernel services
- `com.sun.enterprise.v3.*` - Server V3 architecture
- `fish.payara.enterprise.*` - Payara-specific kernel extensions

**Components:**
- **ApplicationLifecycle** - Application deployment lifecycle
- **ServerContext** - Server runtime context
- **Connector** - Integration points (JMX, monitoring)
- **Embedded** - Embedded Payara support

### API Exporter (`api-exporter/`)
Exports internal APIs as OSGi services.

### Logging (`logging/`)
Logging infrastructure based on JUL (java.util.logging).

**Configuration:**
- `logging.properties` - Per-domain logging config
- Console and file handlers
- Formatters and log levels

### Context Propagation (`context-propagation/`)
Thread context propagation for async operations.

## Build Commands

```bash
# Build all core modules
mvn -DskipTests clean package -f nucleus/core/pom.xml

# Build specific core component
mvn -DskipTests clean package -f nucleus/core/kernel/pom.xml
```

## HK2 Service Pattern

Core uses HK2 (not CDI) for dependency injection:

```java
@Service // Register as HK2 service
@Scoped(Singleton.class) // Scope
public class MyService {

    @Inject
    private Habitat habitat; // HK2 service registry

    public void doSomething() {
        // Service implementation
    }
}
```

## Key HK2 Concepts

- **Habitat** - The HK2 service registry
- **Service** - `@Service` annotation registers a service
- **Injection** - `@Inject` for constructor/field injection
- **Scope** - `@Scoped` defines lifecycle (Singleton, PerLookup, etc.)
- **Descriptor** - `META-INF/hk2-locator/*.descriptor` files

## Server Bootstrap Sequence

1. `ASMain.main()` - Entry point
2. Platform setup (classpath, properties)
3. HK2 initialization
4. Module system startup (OSGi)
5. Kernel startup
6. Domain initialization
7. Application deployment

## Kernel Events

Event-driven architecture using `org.glassfish.kernel.event`:

- **Events** - Application startup/shutdown, module load
- **Listeners** - Subscribe to kernel events
- **EventTypes** - Defined in `Events` enum

## Architecture Notes

1. **V3 Architecture** - Modular, OSGi-based design
2. **HK2-first** - Core uses HK2, not CDI
3. **Module isolation** - Each module is an OSGi bundle
4. **Service orientation** - Everything is a service
