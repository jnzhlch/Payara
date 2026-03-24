# CLAUDE.md - Connectors Internal API

This file provides guidance for working with the `connectors-internal-api` module - internal APIs for JCA integration.

## Module Overview

The connectors-internal-api module defines internal APIs and SPIs used by other Payara modules to integrate with the JCA runtime.

## Build Commands

```bash
# Build connectors-internal-api
mvn -DskipTests clean package -f appserver/connectors/connectors-internal-api/pom.xml
```

## Core Interfaces

### ConnectorRuntime

Main runtime interface:

```java
public interface ConnectorRuntime {
    // Resource adapter lifecycle
    void createActiveResourceAdapter(String rarName);
    void destroyActiveResourceAdapter(String rarName);

    // Connection pooling
    void createConnectorConnectionPool(PoolInfo poolInfo,
        ConnectorDescriptor descriptor, ResourceAdapter ra);

    // Resource lookup
    Object lookupConnectionFactory(ResourceInfo resourceInfo);
}
```

### Admin Services

| Service | Purpose |
|----------|---------|
| `ResourceAdapterAdminService` | RA CRUD operations |
| `ConnectorConnectionPoolAdminService` - Pool CRUD operations |
| `ConnectorResourceAdminService` | Resource CRUD operations |
| `ConnectorAdminObjectAdminService` | Admin object operations |
| `ConnectorSecurityAdminService` | Security map operations |

### ClassLoader APIs

```java
public interface ConnectorsClassLoader {
    // Isolate RA classes
    Class<?> loadClass(String className) throws ClassNotFoundException;
}
```

## Utility APIs

### ConnectorsUtil

Utility methods for connector operations:

```java
public class ConnectorsUtil {
    // Parse pool configuration
    public static PoolInfo getPoolInfo(Object[] config);

    // Get resource adapter
    public static ResourceAdapter getResourceAdapter(String rarName);

    // Check if system RAR (built-in)
    public static boolean isSystemRar(String rarName);

    // Get MCF properties
    public static Set getConnectorDescriptorProperties(
        ConnectorDescriptor descriptor, String connectionDefName);
}
```

### Exceptions

| Exception | Purpose |
|-----------|---------|
| `ConnectorsException` | General connector errors |
| `PoolingException` - Pool-related errors |
| `ConnectorsRuntimeException` | Runtime exceptions |

## Naming SPI

### ConnectorNamingEventListener

Listens for naming events:

```java
public interface ConnectorNamingEventListener {
    void resourceAdded(ResourceInfo resourceInfo);
    void resourceRemoved(ResourceInfo resourceInfo);
}
```

## Package Structure

```
com.sun.appserv.connectors.internal.api/
├── ConnectorRuntime.java              # Runtime interface
├── ConnectorsClassLoader.java         # Classloader API
├── ConnectorsUtil.java                # Utilities
├── PoolingException.java              # Pool exceptions
├── ConnectorsException.java           # General exceptions
├── ResourceAdapterAdminService.java   # RA admin API
└── spi/
    └── ConnectorNamingEventListener.java  # Naming events
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `hk2-core` | Dependency injection |
| `config-api` | Configuration beans |
| `transaction-internal-api` | Transaction APIs |
| `admin-util` | Admin utilities |
| `internal-api` | Server internal APIs |
| `dol` | Deployment descriptors |
| `resources-connector` | Resource infrastructure |
| `payara-api` | Payara-specific APIs |

## Related Modules

- `connectors-runtime` - Implements these APIs
- All modules using JCA resources - Depend on these APIs
