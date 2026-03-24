# CLAUDE.md - Appserver Common

This file provides guidance for working with the `appserver/common` module - shared infrastructure components for the Jakarta EE server.

## Module Overview

The common module provides foundational services shared across all Jakarta EE containers, including naming services, annotation processing, JSR 77 management, and container-common utilities.

## Build Commands

```bash
# Build entire common module
mvn -DskipTests clean package -f appserver/common/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/common/<submodule>/pom.xml
```

## Module Structure

| Submodule | Purpose |
|-----------|---------|
| `glassfish-naming` | JNDI naming service implementation |
| `annotation-framework` | Annotation processing framework |
| `container-common` | Shared container infrastructure |
| `amx-javaee` | JSR 77 Java EE management (AMX) |
| `stats77` | JSR 77 statistics support |
| `glassfish-ee-api` | Java EE public APIs |

## Core Architecture

### Naming Service (`glassfish-naming`)

Provides JNDI implementation with:

- **SerialContext** - Main JNDI Context implementation
  - Local lookups within the same JVM
  - Remote lookups via RMI-IIOP
  - `java:` URL namespace handling
- **SerialContextProvider** - Backend naming storage
- **TransientContext** - In-memory namespace hierarchy

```
Application JNDI Lookup
         |
    SerialContext
         |
    +----+----+
    |         |
Local     Remote (IIOP)
Provider   Provider
```

### Annotation Framework (`annotation-framework`)

Processes Jakarta EE annotations during deployment:

- **AnnotationProcessor** - Core annotation scanning engine
- **AnnotationHandler** - Per-annotation type handlers
- **ProcessingContext** - Processing configuration
- **Scanner** - Class file scanning

Workflow:
1. Scan classes for annotations
2. Match with registered handlers
3. Invoke handlers in appropriate order
4. Collect processing results

### Container Common (`container-common`)

Shared services across all containers:

- **InjectionManager** - CDI/@Resource dependency injection
- **ComponentEnvManager** - java:comp/env namespace management
- **EntityManagerWrapper** - JPA EntityManager proxying
- **ManagedBeanManager** - @ManagedBean lifecycle
- **CallFlowAgent** - Request tracking

### Interceptor System

Container-agnostic interceptor framework:

- **JavaEEInterceptorBuilder** - Builds interceptor chains
- **InterceptorInvoker** - Executes interceptor chains
- **InterceptorInfo** - Interceptor metadata

Supports:
- AroundInvoke methods
- AroundTimeout methods
- Lifecycle callbacks (@PostConstruct, @PreDestroy)

### JSR 77 Management (`amx-javaee`)

Standard Java EE management beans:

- **J2EEDomain** - Top-level management domain
- **J2EEServer** - Server instance
- **JVM** - JVM statistics
- **Resource management** - JDBC, JMS, JCA, JNDI resources
- **Application management** - Deployed applications

## Integration Points

### Used By Containers

| Container | Uses From Common |
|-----------|------------------|
| Web | Naming, Injection, Interceptors |
| EJB | Naming, Injection, ManagedBean |
| JPA | Injection, EntityManagerWrapper |
| JMS | Naming, Resource binding |

### Naming Integration

```java
// Application code
InitialContext ic = new InitialContext();
DataSource ds = (DataSource) ic.lookup("jdbc/__default");

// Flow: SerialContext -> SerialContextProvider -> TransientContext
```

### Annotation Processing

Containers register handlers for their annotations:

```java
processor.pushAnnotationHandler(new EJBAnnotationHandler());
processor.pushAnnotationHandler(new ResourceAnnotationHandler());
ProcessingResult result = processor.process(context);
```

## Key Patterns

### Proxy Pattern
- `EntityManagerWrapper` - Wraps EclipseLink EntityManagers
- `CommonResourceProxy` - Lazy resource initialization
- `NamingObjectProxy` - Deferred JNDI lookups

### ThreadLocal Patterns
- `SerialContext.stickyContext` - Load balancer affinity
- Injection context tracking

### Factory Pattern
- `JndiNamingObjectFactory` - Creates objects from JNDI
- `NamingObjectFactory` - Object factory SPI
- `GlassFishObjectInputStream` - Custom serialization

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | Public APIs |
| `internal-api` | Internal server APIs |
| `common-util` | Shared utilities |
| `hk2-core` | Dependency injection |
| `glassfish-corba-orb` | RMI-IIOP support |

## Related Modules

- `nucleus/common/glassfish-naming` - Core naming interfaces
- `appserver/deployment/dol` - Deployment descriptors
- `appserver/persistence` - JPA integration
