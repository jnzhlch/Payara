# CLAUDE.md - Connectors Runtime

This file provides guidance for working with the `connectors-runtime` module - Jakarta EE Connector Architecture runtime implementation.

## Module Overview

The connectors-runtime module provides the core implementation of Jakarta EE Connector Architecture (JCA 1.7), including connection pooling, transaction management, security mapping, and resource adapter lifecycle management.

## Build Commands

```bash
# Build connectors-runtime
mvn -DskipTests clean package -f appserver/connectors/connectors-runtime/pom.xml

# Run tests
mvn test -f appserver/connectors/connectors-runtime/pom.xml
```

## Core Runtime

### ConnectorRuntime

Main service for JCA operations:

```java
@Service
@Singleton
public class ConnectorRuntime {
    // Resource adapter lifecycle
    public void createActiveResourceAdapter(String rarName);
    public void destroyActiveResourceAdapter(String rarName);

    // Pool management
    public void createConnectorConnectionPool(PoolInfo poolInfo,
        ConnectorDescriptor descriptor, ResourceAdapter ra);

    // Connection factory lookup
    public Object lookupConnectionFactory(ResourceInfo resourceInfo);

    // Admin services
    public ResourceAdapterAdminService getResourceAdapterAdminService();
    public ConnectorConnectionPoolAdminService getConnectorConnectionPoolAdminService();
}
```

### ActiveResourceAdapter

Represents a deployed resource adapter:

```java
public class ActiveResourceAdapter {
    private ResourceAdapter resourceAdapter;  // RA instance
    private String rarName;
    private ClassLoader rarClassLoader;
    private BootstrapContextImpl bootstrapContext;

    // Lifecycle
    public void start() throws ResourceException;
    public void stop() throws ResourceException;
    public void destroy();
}
```

## Connection Pooling

### Connection Pool Architecture

```
Application Request
         │
    ConnectionManagerImpl
         │
    ┌────┴────┐
    │   Pool  │
    └────┬────┘
         │
    ┌────┴────────────┐
    │                 │
┌───▼────┐    ┌─────▼──────┐
│  Free  │    │  Used (Set) │
│  Queue │    │             │
└────────┘    └─────────────┘
    │                 │
    └────────┬────────┘
             │
    ConnectorAllocator
             │
       ResourceAdapter
```

### Connection Managers

| Manager | Purpose |
|---------|---------|
| `ConnectionManagerImpl` | Standard connection management |
| `LazyAssociatableConnectionManagerImpl` | Lazy association for RARs |
| `LazyEnlistableConnectionManagerImpl` | Lazy transaction enlistment |

### ConnectorConnectionPool

Represents a JCA connection pool:

```java
public class ConnectorConnectionPool {
    private PoolInfo poolInfo;
    private ResourceAdapter resourceAdapter;
    private ManagedConnectionFactory mcf;
    private ConnectionManager connectionManager;

    // Pool properties
    private int maxPoolSize;
    private int minPoolSize;
    private long maxWaitTime;
    private boolean connectionValidationRequired;

    // Pool operations
    public ResourceHandle getResource(ResourceSpec spec,
        ClientSecurityInfo info, boolean shareable);
}
```

## Transaction Support

### Allocator Types

| Allocator | Transaction Support |
|-----------|---------------------|
| `NoTxConnectorAllocator` | No transaction support |
| `LocalTxConnectorAllocator` | Local transactions only |
| `XATerminatorProxy` | XA transactions |

### XA Support

```java
public class XATerminatorProxy {
    // Wraps XAResource from resource adapter
    // Integrates with JavaEETransactionManager
    // Handles transaction completion and recovery
}
```

### Connection Association Types

| Type | Description |
|------|-------------|
| **Shareable** | Connection can be shared across components |
| **Unshareable** - Connection exclusive to component |
| **Lazy Associable** | Connection associated lazily (on first use) |

## Resource Adapter Deployment

### Deployment Flow

```
RAR File (.rar)
       │
   RarDetector
       │
   ConnectorDeployer
       │
   ConnectorArchivist (parse ra.xml)
       │
   ConnectorDescriptor
       │
   ActiveResourceAdapter
       │
   BootstrapContextImpl
       │
   ResourceAdapter.start()
```

### RAR Components

| Component | Description |
|-----------|-------------|
| `ManagedConnectionFactory` | Creates connection factories |
| `ConnectionFactory` | Application-visible connection factory |
| `ManagedConnection` | Physical connection to EIS |
| `ConnectionManager` | Pooling and transaction management |
| `ResourceAdapter` - RA bootstrap and lifecycle |

### ra.xml Parsing

```java
@Service
public class ConnectorArchivist {
    public ConnectorDescriptor read(Object source) {
        // Parse ra.xml
        // Extract connection definitions
        // Extract admin objects
        // Extract config properties
    }
}
```

## Security Mapping

### Authentication Flow

```
Application Request (with Principal)
         │
   AuthenticationService
         │
   Container Callback Handler
         │
   RuntimeSecurityMap
         │
   ┌────┴────────────┐
   │                 │
Backend Principal   Password
```

### Security Components

| Component | Purpose |
|-----------|---------|
| `AuthenticationService` | Container-managed authentication |
| `BasicPasswordAuthenticationService` | Password credential mapping |
| `ConnectorSecurityMap` | Configuration of principal mapping |
| `RuntimeSecurityMap` | Runtime security mapping |
| `EisBackendPrincipal` | EIS principal representation |

### Security Map Configuration

```xml
<security-map>
    <principal>appUser</principal>
    <backend-principal>
        <user-name>eisUser</user-name>
        <password>eisPassword</password>
    </backend-principal>
</security-map>
```

## Annotation Handlers

Processes JCA annotations during deployment:

| Handler | Annotation |
|---------|------------|
| `ConnectionFactoryDefinitionHandler` | `@ConnectionFactoryDefinition` |
| `ConnectionFactoryDefinitionsHandler` | `@ConnectionFactoryDefinitions` |
| `AdministeredObjectDefinitionHandler` | `@AdministeredObjectDefinition` |
| `AdministeredObjectDefinitionsHandler` | `@AdministeredObjectDefinitions` |
| `ConnectionDefinitionHandler` | `@ConnectionDefinition` |
| `ConnectionDefinitionsHandler` | `@ConnectionDefinitions` |

## Resource Deployers

### Deployer Classes

| Deployer | Resource |
|----------|----------|
| `ConnectorConnectionPoolDeployer` | Connection pool |
| `ConnectorResourceDeployer` | Connection factory |
| `AdminObjectResourceDeployer` | Admin object |
| `ResourceAdapterDeployer` | Resource adapter |

### Deployment Pattern

```java
public class ConnectorConnectionPoolDeployer {
    public void deployResource(Object resource) {
        // 1. Parse configuration
        // 2. Create PoolInfo
        // 3. Get ActiveResourceAdapter
        // 4. Create pool via ConnectorRuntime
        // 5. Publish to JNDI
    }
}
```

## Admin Services

### SPI Implementations

| Service | Purpose |
|---------|---------|
| `ConnectorAdminServicesFactory` | Creates admin services |
| `ConnectorConnectionPoolAdminServiceImpl` - Pool CRUD operations |
| `ConnectorResourceAdminServiceImpl` | Resource CRUD operations |
| `AdminObjectResourceDeployer` - Admin object operations |
| `ConnectorSecurityAdminServiceImpl` | Security map operations |

## Naming and JNDI

### JNDI Integration

```
JNDI Lookup
       │
   ResourceNamingService
       │
   ConnectorResourceNamingEventNotifier
       │
   ConnectorConnectionPool
       │
   ConnectionManager
       │
   ConnectionFactory (from RA)
```

### ConnectorNamingEventNotifier

Notifies components of naming events:

```java
public class ConnectorResourceNamingEventNotifier {
    public void resourceAdded(ResourceInfo resourceInfo);
    public void resourceRemoved(ResourceInfo resourceInfo);
}
```

## Connection Validation

### Validation Methods

| Method | Description |
|--------|-------------|
| **Table** | Validate with SQL query |
| **Custom Validation** - User-provided validation |
| **Auto Commit** | Test auto-commit mode |

### Validation Configuration

```xml
<connector-connection-pool>
    <connection-validation-required>true</connection-validation-required>
    <validation-method>table</validation-method>
    <validation-table-name>SYSTABLES</validation-table-name>
</connector-connection-pool>
```

## Pool Reconfiguration

### Dynamic Reconfiguration

```java
public class ConnectionPoolReconfigHelper {
    public void reconfigureConnectorConnectionPool(
        ConnectorConnectionPool existingPool,
        ConnectorConnectionPool newPoolConfig);
}
```

Supports dynamic changes to:
- Max/min pool size
- Idle timeout
- Validation settings
- Statement caching

## Monitoring

### Probe Providers

```java
@ProbeProvider(moduleName="connector-pool",
    probeProviderName="connector-pool")
public class ConnectorConnPoolProbeProvider {
    @Probe
    public void connectionAcquired(String poolName, int numFree, int numUsed);
}
```

### Monitoring Statistics

| Statistic | Probe Event |
|-----------|-------------|
| Connection acquired | `connectionAcquired` |
| Connection released | `connectionReleased` |
| Connection created | `connectionCreated` |
| Connection destroyed | `connectionDestroyed` |
| Connection failed | `connectionCreationFailed` |
| Connection timeout | `connectionRequestTimedOut` |

## Package Structure

```
com.sun.enterprise.connectors/
├── ConnectorRuntime.java              # Main runtime
├── ActiveResourceAdapter.java         # Active RA
├── ActiveRAFactory.java               # RA factory
├── ConnectorRegistry.java             # RA registry
├── ConnectorConnectionPool.java       # Connection pool
├── ConnectionManagerFactory.java
├── ConnectionManagerImpl.java
├── LazyAssociatableConnectionManagerImpl.java
├── LazyEnlistableConnectionManagerImpl.java
├── BootstrapContextImpl.java
├── PoolMetaData.java                  # Pool metadata
├── DeferredResourceConfig.java        # Deferred config
├── XATerminatorProxy.java             # XA wrapper
│
├── authentication/
│   ├── AuthenticationService.java
│   ├── BasicPasswordAuthenticationService.java
│   ├── ConnectorSecurityMap.java
│   ├── EisBackendPrincipal.java
│   └── RuntimeSecurityMap.java
│
├── deployment/
│   ├── ConnectorArchivist.java        # RAR parsing
│   ├── ConnectorValidator.java
│   └── annotation/handlers/           # Annotation processors
│
├── module/
│   ├── ConnectorApplication.java      # RAR as application
│   ├── ConnectorContainer.java        # Container for RA
│   ├── ConnectorDeployer.java
│   └── RarHandler.java
│
├── naming/
│   ├── ConnectorInternalObjectsProxy.java
│   ├── ConnectorNamingEventNotifier.java
│   └── ConnectorResourceNamingEventNotifier.java
│
├── service/
│   ├── ConnectorService.java
│   ├── *AdminServiceImpl.java         # Admin SPI impl
│   └── ConnectorConfigurationParserServiceImpl.java
│
└── util/
    ├── ClassLoadingUtility.java
    ├── ConnectionPoolObjectsUtils.java
    ├── ConnectionPoolReconfigHelper.java
    ├── ConnectorClassLoader.java
    ├── ConnectorConfigParser.java
    ├── ConnectorConfigParserFactory.java
    ├── ConnectorRARClassLoader.java
    ├── RARUtils.java
    └── SecurityMapUtils.java

com.sun.enterprise.resource/
├── ResourceHandle.java                # Pooled connection wrapper
├── ResourceSpec.java                  # Resource specification
├── ResourceState.java                 # Resource state
├── ConnectorXAResource.java           # XA wrapper
├── XAResourceWrapper.java
├── AssocWithThreadResourceHandle.java # Thread association
├── ClientSecurityInfo.java            # Security info
├── DynamicallyReconfigurableResource.java
├── ResourceConverter.java
│
├── allocator/
│   ├── ResourceAllocator.java         # Allocator interface
│   ├── AbstractConnectorAllocator.java
│   ├── ConnectorAllocator.java
│   ├── NoTxConnectorAllocator.java
│   └── LocalTxConnectorAllocator.java
│
└── deployer/
    ├── ConnectorConnectionPoolDeployer.java
    ├── ConnectorResourceDeployer.java
    └── AdminObjectResourceDeployer.java
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.resource-api` | JCA 1.7 API |
| `jakarta.authentication-api` | JAAS authentication |
| `connectors-internal-api` | Internal APIs |
| `resources-connector` | Resource infrastructure |
| `deployment-common` | Deployment integration |
| `dol` | Deployment descriptor |
| `transaction-internal-api` | Transaction integration |
| `security` / `security-ee` | Security context |
| `orb-connector` | CORBA/IIOP support |
| `monitoring-core` | Monitoring service |
| `flashlight-framework` | Flashlight/probe framework |

## Related Modules

- `connectors-connector` - RAR deployment detection
- `connectors-inbound-runtime` - Inbound messaging
- `work-management` - JCA Work Management
- `admin` - Admin commands
