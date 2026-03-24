# CLAUDE.md - Appserver Connectors

This file provides guidance for working with the `appserver/connectors` module - Jakarta EE Connector Architecture (JCA) implementation.

## Module Overview

The connectors module implements Jakarta EE Connector Architecture (JCA/JCA 1.7), enabling Java EE applications to connect to Enterprise Information Systems (EIS) like ERP, CRM, legacy systems, and messaging providers.

## Build Commands

```bash
# Build entire connectors module
mvn -DskipTests clean package -f appserver/connectors/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/connectors/<submodule>/pom.xml
```

## Module Structure

| Submodule | Purpose |
|-----------|---------|
| `connectors-connector` | RAR deployment sniffer/detector |
| `connectors-runtime` | JCA runtime implementation |
| `connectors-internal-api` | Internal connector APIs |
| `connectors-inbound-runtime` | Inbound resource adapter support |
| `work-management` | JCA Work Management (JSR 237) |
| `admin` | Admin commands for connector resources |
| `descriptors` | RAR deployment descriptors |

## JCA Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Enterprise Application                      │
│  @ConnectionFactoryDefinition, @AdministeredObjectDefinition  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  JCA Container Runtime                         │
│  - Connection Pooling                                         │
│  - Transaction Management                                     │
│  - Security Mapping                                           │
│  - Work Management                                            │
└─────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
    Outbound             Inbound              Work Management
    Connections          (Message Driven)   (Async Tasks)
         │                    │                    │
    ┌────▼────┐          ┌────▼────┐        ┌────▼────┐
    │   EIS   │          │   JMS   │        │  Work   │
    └─────────┘          └─────────┘        └─────────┘
```

## Core Components

### ConnectorRuntime

Main runtime service for JCA:

```java
@Service
@Singleton
public class ConnectorRuntime {
    // Deploy resource adapter (RAR)
    public void createActiveResourceAdapter(RarName rarName);

    // Create connection pool
    public void createConnectorConnectionPool(PoolInfo poolInfo,
        ConnectorDescriptor descriptor, ResourceAdapter ra);

    // Get connection factory
    public Object lookupConnectionFactory(ResourceInfo resourceInfo);

    // Shutdown resource adapter
    public void destroyActiveResourceAdapter(String rarName);
}
```

### Connection Pooling

| Component | Purpose |
|-----------|---------|
| `ConnectorConnectionPool` | JCA connection pool |
| `ConnectionManagerImpl` | Connection lifecycle management |
| `ConnectionManagerFactory` | Creates appropriate connection manager |
| `LazyAssociatableConnectionManagerImpl` - Lazy association support |
| `LazyEnlistableConnectionManagerImpl` | Lazy enlistment support |

### Transaction Support

| Transaction Type | Allocator |
|------------------|-----------|
| **No Transaction** | `NoTxConnectorAllocator` |
| **Local Transaction** | `LocalTxConnectorAllocator` |
| **XA Transaction** | `XATerminatorProxy` with `ConnectorXAResource` |

### Resource Adapter Lifecycle

```
RAR Deployment
       │
   RarDetector (identifies .rar)
       │
   ConnectorDeployer
       │
   ActiveResourceAdapter
       │
   BootstrapContextImpl (bootstrap)
       │
   ResourceAdapter.start()
```

## Connection Factory Definitions

### Annotation-Based Configuration

```java
@ConnectionFactoryDefinition(
    name = "java:app/jms/myConnectionFactory",
    interfaceName = "jakarta.jms.ConnectionFactory",
    resourceAdapter = "jmsra",
    properties = {
        @ConnectionFactoryProperty(name = "UserName", value = "admin"),
        @ConnectionFactoryProperty(name = "Password", value = "secret")
    }
)
@AdministeredObjectDefinition(
    name = "java:app/jms/myQueue",
    interfaceName = "jakarta.jms.Queue",
    resourceAdapter = "jmsra",
    properties = {
        @AdministeredObjectProperty(name = "Name", value = "MyQueue")
    }
)
public class MyApp { }
```

### Deployers

| Deployer | Handles |
|----------|---------|
| `ConnectorConnectionPoolDeployer` | Connection pool resources |
| `AdminObjectResourceDeployer` | Admin objects (queues, topics) |
| `ConnectionFactoryDefinitionDeployer` | Annotation-based CF |
| `AdministeredObjectDefinitionDeployer` - Annotation-based AO |

## Security Mapping

### Principal Mapping

Maps application principals to EIS backend principals:

```java
@SecurityMap(
    principal = "appUser",
    backendPrincipal = @BackendPrincipal(
        userName = "eisUser",
        password = "eisPassword"
    )
)
```

### Security Components

| Component | Purpose |
|-----------|---------|
| `AuthenticationService` | Container authentication |
| `BasicPasswordAuthenticationService` | Password-based auth |
| `ConnectorSecurityMap` | Principal mapping configuration |
| `RuntimeSecurityMap` | Runtime security mapping |

## Inbound Connectivity (Message-Driven Beans)

### Message Listener Inflow

```
┌─────────────────────────────────────────────────────────────┐
│                    MDB (Message Driven Bean)                 │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                WorkManager (JCA Work Management)              │
│  - Delivers messages to MDBs                                │
│  - Executes with proper context                              │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│              ResourceAdapter (JMS Provider)                   │
│  - Creates MessageEndpoint                                   │
│  - Activates endpoint                                       │
└─────────────────────────────────────────────────────────────┘
```

## Work Management

### JSR 237 Work Management

Provides standardized work execution for resource adapters:

```java
// Work context
WorkContext workContext = ...;

// Submit work
WorkManager workManager = ...;
workManager.scheduleWork(work, context);
```

### Work Management Components

| Component | Purpose |
|-----------|---------|
| `WorkManager` | Schedules work execution |
| `WorkContext` | Execution context (security, tx) |
| `WorkListener` | Work completion callbacks |

## Admin Commands

### Resource Adapter Management

```bash
# List resource adapters
asadmin list-resource-adapters

# Create connector connection pool
asadmin create-connector-connection-pool \
    --raname jmsra \
    --connectiondefinition jakarta.jms.ConnectionFactory \
    --property UserName=admin:Password=secret \
    jms-pool

# Create connector resource
asadmin create-connector-resource \
    --poolname jms-pool \
    --objecttype jakarta.jms.ConnectionFactory \
    jms/myFactory

# Create admin object (queue/topic)
asadmin create-admin-object \
    --raname jmsra \
    --restype jakarta.jms.Queue \
    --property Name=MyQueue \
    jms/myQueue

# Ping connection pool
asadmin ping-connection-pool jms-pool

# Flush connection pool
asadmin flush-connection-pool jms-pool
```

### Security Mapping

```bash
# Create security map
asadmin create-connector-security-map \
    --poolname jms-pool \
    --principals appUser1,appUser2 \
    --mappedusername eisUser \
    --mappedpassword eisPassword \
    mySecurityMap

# List security maps
asadmin list-connector-security-maps jms-pool
```

## Pool Configuration

### Connection Pool Properties

| Property | Description |
|----------|-------------|
| `MaxPoolSize` | Maximum connections in pool |
| `MinPoolSize` | Minimum idle connections |
| `MaxWaitTime` | Max wait for connection (ms) |
| `PoolResizeQuantity` | Connections to add/remove |
| `IdleTimeout` | Idle connection timeout (seconds) |
| `ConnectionValidationRequired` | Validate connections |
| `ValidationMethod` | Validation (table, custom) |
| `FailAllConnections` | Close all connections on error |
| `TransactionSupport` | XA, LocalTx, NoTx |
| `ConnectionLeakTimeout` | Leak detection timeout |
| `ConnectionLeakReclaim` | Reclaim leaked connections |

## Monitoring

### Pool Statistics

| Statistic | Description |
|-----------|-------------|
| `numConnUsed` | Currently used connections |
| `numConnFree` | Available connections |
| `numConnFailed` | Failed connection attempts |
| `numConnTimedOut` | Connection timeouts |
| `averageConnWaitTime` | Average wait time |
| `waitQueueLength` | Requests waiting for connection |

### Probe Providers

- `ConnectorConnPoolProbeProvider` - Pool monitoring
- `ConnectorConnPoolAppProbeProvider` - Application-level monitoring

## RAR Deployment

### RAR Structure

```
my-resource-adapters.rar
├── META-INF/
│   ├── ra.xml                    # Deployment descriptor
│   └── MANIFEST.MF
├── mypackage/
│   ├── MyManagedConnectionFactory.class
│   ├── MyConnectionFactory.class
│   ├── MyConnection.class
│   └── MyInteractionSpec.class
└── lib/
    └── dependency.jar
```

### Deployment Detection

- `RarDetector` - Identifies RAR files by extension/structure
- `RarSniffer` - Determines application type
- `ConnectorDeployer` - Handles RAR deployment

## Package Structure

### connectors-runtime

```
com.sun.enterprise.connectors/
├── ConnectorRuntime.java              # Main runtime
├── ActiveResourceAdapter.java         # Active RA
├── ConnectorRegistry.java             # RA registry
├── ConnectorConnectionPool.java       # Connection pool
├── ConnectionManagerImpl.java
├── LazyAssociatableConnectionManagerImpl.java
├── LazyEnlistableConnectionManagerImpl.java
├── BootstrapContextImpl.java
├── authentication/
│   ├── AuthenticationService.java
│   └── ConnectorSecurityMap.java
├── deployment/
│   ├── ConnectorArchivist.java        # RAR parsing
│   └── ConnectorValidator.java
├── module/
│   ├── ConnectorDeployer.java
│   └── ConnectorContainer.java
├── service/
│   ├── ConnectorService.java
│   └── *AdminServiceImpl.java          # Admin SPI implementations
└── util/
    ├── ConnectorClassLoader.java
    ├── RARUtils.java
    └── ResourcesUtil.java

com.sun.enterprise.resource/
├── allocator/
│   ├── ConnectorAllocator.java
│   ├── LocalTxConnectorAllocator.java
│   ├── NoTxConnectorAllocator.java
│   └── XATerminatorProxy.java
├── ResourceHandle.java                # Pooled connection
├── ConnectorXAResource.java
└── deployer/
    └── *Deployer.java
```

### connectors-internal-api

```
com.sun.appserv.connectors.internal.api/
├── ConnectorRuntime.java              # Runtime API
├── ConnectorsClassLoader.java         # Classloader API
├── ConnectorsUtil.java                # Utilities
└── spi/
    └── ConnectorNamingEventListener.java

com.sun.appserv.connectors.internal.api.*
├── PoolingException.java
├── ConnectorsException.java
└── ResourceAdapterAdminService.java
```

### connectors-connector

```
com.sun.enterprise.connectors.connector.module/
├── RarDetector.java                   # Detects RAR files
├── RarSniffer.java                    # Application type detection
└── RarType.java                       # RAR type marker
```

### work-management

```
com.sun.enterprise.connectors.work/
├── WorkManager.java                   # Work execution
├── WorkManagerFactory.java
├── WorkContextHandler.java
└── monitor/
    └── WorkManagementProbeProvider.java
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.resource-api` - JCA 1.7 API |
| `jakarta.authentication-api` - JAAS authentication |
| `transaction-internal-api` | Transaction integration |
| `resources-connector` | Resource infrastructure |
| `epicyro` | Copy of JCA RA implementation |
| `orb-connector` | CORBA/IIOP support |
| `monitoring-core` | Monitoring integration |
| `deployment-javaee-core` | Deployment integration |

## RAR Descriptor Location

Located in `descriptors/src/main/resources/`:
- `connector_1_5.xsd`
- `connector_1_6.xsd`
- `connector_1_7.xsd`
- DTD files for validation

## Related Modules

- `nucleus/resources` - Resource infrastructure
- `appserver/ejb` - MDB support
- `appserver/transaction` - Transaction management
- `appserver/jms` - JMS integration (uses JCA)
