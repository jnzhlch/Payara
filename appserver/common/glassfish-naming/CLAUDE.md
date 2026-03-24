# CLAUDE.md - GlassFish Naming

This file provides guidance for working with the `glassfish-naming` module - JNDI naming service implementation.

## Module Overview

The glassfish-naming module provides Payara's JNDI (Java Naming and Directory Interface) implementation, supporting local and remote lookups with RMI-IIOP.

## Build Commands

```bash
# Build glassfish-naming
mvn -DskipTests clean package -f appserver/common/glassfish-naming/pom.xml
```

## Core Components

### SerialContext

The main JNDI Context implementation:

```java
// Default JNDI context for GlassFish
InitialContext ic = new InitialContext();
// Uses SerialContext under the hood

// Features:
// - java: namespace handling
// - Local in-JVM lookups
// - Remote RMI-IIOP lookups
// - Load balancer stickiness
```

**Key Methods:**
- `lookup(String name)` - Resolve JNDI names
- `bind/rebind/unbind` - Namespace operations
- `createSubcontext` - Create context hierarchies

### Naming Modes

| Mode | Description |
|------|-------------|
| **Intra-server** | Local lookups without ORB overhead |
| **Remote** | RMI-IIOP based remote lookups |
| **Cluster** | Load-balanced with sticky sessions |

### SerialContextProvider

Backend naming service interface:

```
Local Mode:  LocalSerialContextProviderImpl
Remote Mode: RemoteSerialContextProviderImpl (via IIOP)
```

### TransientContext

In-memory namespace storage:

- Hierarchical context structure
- Supports federation
- Thread-safe operations

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                  Application                          │
│   new InitialContext().lookup("java:comp/env")       │
└──────────────────────────────────────────────────────┘
                         │
┌──────────────────────────────────────────────────────┐
│                  SerialContext                        │
│  - Handles java: URLs                                │
│  - Manages provider caching                          │
│  - Load balancer stickiness                          │
└──────────────────────────────────────────────────────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
    Local Provider              Remote Provider (IIOP)
         │                               │
    TransientContext              CORBA Naming Service
```

## Namespace Structure

### java: Namespace

| Namespace | Purpose |
|-----------|---------|
| `java:comp/env` | Component environment |
| `java:module` | Module scope |
| `java:app` | Application scope |
| `java:global` | Global namespace |

### Naming Object Proxies

Lazy initialization for resources:

```java
public interface NamingObjectProxy {
    Object create(SerialContext ctx) throws NamingException;
}
```

Implementations:
- `CommonResourceProxy` - JDBC, JMS, JCA resources
- `JndiLookupNotifier` - Lookup event notifications

## Configuration

### JNDI Environment Properties

| Property | Purpose |
|----------|---------|
| `java.naming.factory.initial` | `com.sun.enterprise.naming.SerialInitContextFactory` |
| `org.omg.CORBA.ORBInitialHost` | Remote server host |
| `org.omg.CORBA.ORBInitialPort` | Remote server port |
| `com.sun.enterprise.naming.SerialContextProvider` | Cluster endpoints |

### Client Setup

```java
// Remote JNDI client
Hashtable<String, String> env = new Hashtable<>();
env.put(Context.PROVIDER_URL, "iiop://localhost:3700");
env.put(Context.INITIAL_CONTEXT_FACTORY,
    "com.sun.enterprise.naming.SerialInitContextFactory");

InitialContext ic = new InitialContext(env);
```

## Cluster Support

### Load Balancing

Multiple endpoints for high availability:

```java
env.put(NamingClusterInfo.IIOP_URL_PROPERTY,
    "corbaloc::host1:3700,host2:3700,host3:3700/NameService");
```

### Sticky Contexts

Thread-local context caching:

```java
SerialContext.getStickyContext()  // Get thread's cached context
```

## Object Factories

### NamingObjectFactory SPI

Creates objects from JNDI references:

```
Reference in JNDI
       |
NamingManager.getObjectInstance()
       |
ObjectFactory.getObjectInstance()
       |
Actual Object (DataSource, EJB, etc.)
```

### Built-in Factories

| Factory | Purpose |
|---------|---------|
| `JndiNamingObjectFactory` - Standard JNDI objects |
| `CloningNamingObjectFactory` - Clones objects |
| `DelegatingNamingObjectFactory` - Delegates to sub-factories |
| `IIOPObjectFactory` - CORBA objects |

## Package Structure

```
com.sun.enterprise.naming/
├── impl/
│   ├── SerialContext.java              # Main Context
│   ├── SerialContextProvider.java      # Provider interface
│   ├── LocalSerialContextProviderImpl.java
│   ├── RemoteSerialContextProviderImpl.java
│   ├── TransientContext.java           # In-memory storage
│   └── SerialInitContextFactory.java   # JNDI factory
├── spi/
│   └── NamingObjectFactory.java        # Factory SPI
└── util/
    ├── IIOPObjectFactory.java
    ├── JndiNamingObjectFactory.java
    └── NamingUtilsImpl.java
```

## Dependencies

- `glassfish-api` - Public APIs
- `internal-api` - Internal APIs
- `common-util` - Utilities
- `hk2` - Dependency injection
- `glassfish-corba-orb` - RMI-IIOP support

## Related Modules

- `container-common` - Component naming integration
- `nucleus/naming` - Naming configuration
- All containers (Web, EJB, JMS) - Use naming for resource lookups
