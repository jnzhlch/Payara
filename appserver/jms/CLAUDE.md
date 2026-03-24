# CLAUDE.md - Appserver JMS

This file provides guidance for working with the `appserver/jms` module - JMS (Java Message Service) integration for Payara Server.

## 子目录文档索引 (Subdirectory Documentation)

| 子目录 | 文档位置 | 说明 |
|--------|----------|------|
| **jms-core** | [jms-core/CLAUDE.md](jms-core/CLAUDE.md) | 核心 JMS 实现架构 |
| **jms-handlers** | [jms-handlers/CLAUDE.md](jms-handlers/CLAUDE.md) | 注解处理器架构 |
| **gf-jms-connector** | [gf-jms-connector/CLAUDE.md](gf-jms-connector/CLAUDE.md) | 连接器配置架构 |
| **gf-jms-injection** | [gf-jms-injection/CLAUDE.md](gf-jms-injection/CLAUDE.md) | CDI 注入架构 |
| **admin** | [admin/CLAUDE.md](admin/CLAUDE.md) | CLI 管理命令 |

## 架构层次

```
┌─────────────────────────────────────────────────────────────────┐
│                        应用层                                   │
│                    (JNDI / @Resource / @Inject)                │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      jms-core                                   │
│              (核心 JMS 资源适配器实现)                           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
                    ▼                 ▼
┌────────────────────────┐  ┌────────────────────────────────────┐
│   jms-handlers         │  │   gf-jms-connector                 │
│ (注解处理器)           │  │ (JMS 连接器配置)                   │
└────────────────────────┘  └────────────────────────────────────┘
                    │                 │
                    └────────┬────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   gf-jms-injection                             │
│                   (CDI 注入支持)                                │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      admin                                     │
│                   (CLI 管理命令)                                │
└─────────────────────────────────────────────────────────────────┘
```

## Module Overview

The appserver/jms module provides JMS 2.0/2.1 support by integrating OpenMQ (GlassFish Message Queue) as the JMS provider. It implements a JCA (Jakarta Connectors) Resource Adapter for JMS, enabling:

- **JMS Resource Adapter** - JCA 1.7 compliant RA for JMS connectivity
- **MDB Container** - Message-Driven Bean activation and runtime
- **Annotation Support** - @JMSConnectionFactoryDefinition and @JMSDestinationDefinition handlers
- **CDI Injection** - Weld integration for @Inject JMS resources
- **Broker Management** - Embedded, local, and remote OpenMQ broker lifecycle
- **Cluster Support** - JMS availability and master broker configuration

**Key Components:**
- **jms-core** - Core JMS resource adapter and broker management
- **jms-handlers** - Annotation handlers for JMS definitions
- **gf-jms-connector** - JMS connector runtime
- **gf-jms-injection** - CDI/Weld injection support
- **admin** - Administration commands (`asadmin` CLI for JMS)
- **l10n/** - Localized message bundles

## Build Commands

```bash
# Build entire jms module
mvn -DskipTests clean package -f appserver/jms/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/jms/jms-core/pom.xml
mvn -DskipTests clean package -f appserver/jms/jms-handlers/pom.xml
mvn -DskipTests clean package -f appserver/jms/gf-jms-connector/pom.xml
mvn -DskipTests clean package -f appserver/jms/gf-jms-injection/pom.xml
mvn -DskipTests clean package -f appserver/jms/admin/pom.xml

# Run tests
mvn test -f appserver/jms/jms-core/pom.xml
```

## Architecture

### JMS Resource Adapter Architecture

```
Application Layer
       │
       ├─→ JNDI Lookup (@Resource)
       │   or @Inject (CDI)
       │   or @JMSConnectionFactoryDefinition
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│                      jms-core                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │      ActiveJmsResourceAdapter                            │ │
│  │  ┌────────────────────────────────────────────────────┐ │ │
│  │  │  JMS Resource Adapter (JCA 1.7)                   │ │ │
│  │  │  ┌──────────────────────────────────────────────┐ │ │ │
│  │  │  │  ManagedConnectionFactory                      │ │ │ │
│  │  │  │  ┌────────────────────────────────────────┐  │ │ │ │
│  │  │  │  │  JMSRAConnectionFactory                 │ │ │ │ │
│  │  │  │  └────────────────────────────────────────┘  │ │ │ │
│  │  │  └──────────────────────────────────────────────┘ │ │ │
│  │  │  ┌──────────────────────────────────────────────┐ │ │ │
│  │  │  │  ActivationSpec (for MDBs)                   │ │ │ │
│  │  │  │  ┌────────────────────────────────────────┐  │ │ │ │
│  │  │  │  │  JMSRAActivationSpec                   │ │ │ │ │
│  │  │  │  └────────────────────────────────────────┘  │ │ │ │
│  │  │  └──────────────────────────────────────────────┘ │ │ │
│  │  └────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│                    Connectors Runtime                          │
│              (Resource Adapter Container)                       │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│                      OpenMQ Broker                              │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Broker Types:                                           │ │
│  │  - EMBEDDED: In-JVM broker                               │ │
│  │  - LOCAL: Separate process, managed by server            │ │
│  │  - REMOTE: External broker (not managed)                 │ │
│  │  - DIRECT: Direct mode (no broker)                       │ │
│  │  - DISABLED: JMS disabled                                │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### Sub-Module Structure

| Submodule | Purpose | Packaging |
|-----------|---------|------------|
| `jms-core` | Core RA implementation and broker management | glassfish-jar |
| `jms-handlers` | JMS annotation definition handlers | glassfish-jar |
| `gf-jms-connector` | JMS connector runtime | glassfish-jar |
| `gf-jms-injection` | CDI injection support with Weld | glassfish-jar |
| `admin` | CLI commands | glassfish-jar |
| `l10n` | Localized messages | jar |

## Broker Types

### 1. EMBEDDED Broker

```java
// Default configuration
// Broker runs in the same JVM as Payara
// Fastest performance, lowest overhead

// Configuration in domain.xml:
<jms-service ... type="EMBEDDED">
    <jms-host ... />
</jms-service>
```

**Use Case:** Development, single-server deployments, embedded applications

### 2. LOCAL Broker

```java
// Broker runs as separate process
// Managed by Payara (started/stopped with server)

// Configuration in domain.xml:
<jms-service ... type="LOCAL">
    <jms-host port="7676" ... />
</jms-service>
```

**Use Case:** Production deployments, isolation from server JVM

### 3. REMOTE Broker

```java
// External broker (not managed by Payara)
// Requires explicit connection URL

// Configuration in domain.xml:
<jms-service ... type="REMOTE">
    <jms-host host="remote-host" port="7676" ... />
</jms-service>
```

**Use Case:** Dedicated JMS infrastructure, shared broker across servers

### 4. DIRECT Mode

```java
// No broker, direct peer-to-peer communication
// Limited functionality

// Configuration in domain.xml:
<jms-service ... type="DIRECT" />
```

**Use Case:** Specialized scenarios requiring direct mode

### 5. DISABLED

```java
// JMS disabled, no broker started
```

## JMS Service Configuration

### JmsService

```java
@Configured
public interface JmsService extends SystemPropertyResolver {
    // Broker type
    String getType();            // EMBEDDED, LOCAL, REMOTE, DIRECT, DISABLED

    // Timeout settings
    String getTimeout();         // Startup timeout in seconds

    // JMX management
    String getJmxConnectors();   // Comma-separated JMX connector names

    // Master broker (for cluster)
    String getMasterBroker();    // Master broker URL for cluster coordination

    // Availability
    String getAvailabilityEnabled(); // Enable JMS availability for clustering

    // Connection pool settings
    String getAttachPoolSize();  // Default: 5
    String getStealPoolSize();   // Default: 5
}
```

### JmsHost

```java
@Configured
public interface JmsHost {
    String getHost();                    // Default: localhost
    String getPort();                    // Default: 7676
    String getAdminUserName();           // Default: admin
    String getAdminPassword();           // Default: admin

    // Advanced settings
    String getName();                    // Host name (default: "default_JMS_host")
    String getNumTcpWorkers();           // TCP worker threads
    String getNumTcpNioAsyncThreads();   // NIO async threads

    // Lazy initialization
    String getLazyInit();                // Enable lazy initialization (default: true)

    // Thread pools
    String getMinThreadPool();           // Min thread pool size
    String getMaxThreadPool();           // Max thread pool size
}
```

### JmsAvailability

```java
@Configured
public interface JmsAvailability {
    String getAvailabilityEnabled();     // Enable JMS availability (default: false)
    String getMasterBroker();            // Master broker URL
    String getObjectMemSize();           // Message store size limit
    String getShutdownTimeout();         // Cluster shutdown timeout
}
```

## Grizzly Network Proxy

The JMS module uses Grizzly network listeners for lazy broker initialization:

```java
// ActiveJmsResourceAdapter constants
public static final String GRIZZLY_PROXY_PREFIX = "JMS_PROXY_";

// JMSConfigListener creates Grizzly proxy listeners
// When a client connects to the proxy, the broker is started on-demand
// Then the connection is forwarded to the actual broker port

// Benefits:
// - Faster server startup (broker starts on first JMS use)
// - Reduced memory footprint when JMS not used
// - Automatic broker initialization on first connection
```

## MDB Container Integration

### ActivationSpec Configuration

```java
// MDB deployment
@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "jms/MyQueue"),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class MyMDB implements MessageListener {
    public void onMessage(Message message) {
        // Process message
    }
}

// ActiveJmsResourceAdapter creates JMSRAActivationSpec
// Configuration properties:
// - destinationLookup: JNDI name of destination
// - destinationType: javax.jms.Queue or javax.jms.Topic
// - subscriptionName: Durable subscription name (for topics)
// - clientId: Client identifier for connections
// - acknowledgeMode: Auto-acknowledge, Dups-ok-acknowledge
// - messageSelector: JMS message selector
// - maxSession: Maximum concurrent sessions (default: 1)
```

## Annotation Handlers

### @JMSConnectionFactoryDefinition

```java
@JMSConnectionFactoryDefinition(
    name = "java:global/jms/MyConnectionFactory",
    interfaceName = "javax.jms.ConnectionFactory",
    resourceAdapter = "jmsra",
    properties = {
        "clientID=MyClient"
    }
)
public class MyApplication {
    // Connection factory available at: java:global/jms/MyConnectionFactory
}
```

### @JMSDestinationDefinition

```java
@JMSDestinationDefinition(
    name = "java:global/jms/MyQueue",
    interfaceName = "javax.jms.Queue",
    resourceAdapter = "jmsra",
    properties = {
        "Name=MyPhysicalQueue"
    }
)
public class MyApplication {
    // Queue available at: java:global/jms/MyQueue
}
```

## CDI Injection

### Weld Integration

```java
// gf-jms-injection provides CDI extension
// Enables @Inject for JMS resources

@Inject
@JMSConnectionFactory("java:global/jms/MyConnectionFactory")
private ConnectionFactory connectionFactory;

@Inject
@JMSDestination("java:global/jms/MyQueue")
private Queue queue;

// Custom qualifiers supported
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface MyJmsResource {
    String value();
}

@Inject
@MyJmsResource("java:global/jms/CustomQueue")
private Queue customQueue;
```

## Cluster Configuration

### Master Broker Pattern

```xml
<!-- In domain.xml for cluster setup -->
<clusters>
    <cluster name="mycluster">
        <config name="mycluster-config">
            <jms-service availability-enabled="true">
                <jms-host name="master" host="master.broker.com" port="7676"/>
            </jms-service>
        </config>
    </cluster>
</clusters>

<!-- Or via JmsAvailability -->
<jms-availability>
    <availability-enabled>true</availability-enabled>
    <master-broker>master.broker.com:7676</master-broker>
</jms-availability>
```

**Cluster Characteristics:**
- One master broker coordinates all instances
- JMS availability enabled for clustering
- Instances connect to master for JMS operations
- Master failure requires manual intervention (or use JMS bridge)

## Package Structure

```
appserver/jms/
├── jms-core/                                    # Core JMS implementation
│   └── src/main/java/com/sun/enterprise/connectors/jms/
│       ├── system/
│       │   ├── ActiveJmsResourceAdapter.java    # Main RA implementation
│       │   ├── JMSConfigListener.java           # Config change listener
│       │   ├── JmsProviderLifecycle.java        # Broker lifecycle
│       │   ├── DefaultJMSConnectionFactory.java # Default factory
│       │   ├── MdbContainerProps.java           # MDB properties
│       │   ├── JmsRaUtil.java                   # RA utilities
│       │   ├── MQAddressList.java               # MQ address parsing
│       │   ├── MQUrl.java                       # MQ URL utilities
│       │   ├── LegacyJmsRecoveryResourceHandler.java # Transaction recovery
│       │   └── JmsHostWrapper.java              # JmsHost wrapper
│       └── util/
│           └── JmsConfig.java                   # JMS configuration utilities
│
├── jms-handlers/                                 # Annotation handlers
│   └── src/main/java/com/sun/enterprise/connectors/jms/deployment/annotation/handlers/
│       ├── JMSConnectionFactoryDefinitionHandler.java
│       ├── JMSConnectionFactoryDefinitionsHandler.java
│       ├── JMSDestinationDefinitionHandler.java
│       └── JMSDestinationDefinitionsHandler.java
│
├── gf-jms-connector/                             # JMS connector runtime
│   └── src/main/java/com/sun/enterprise/connectors/jms/config/
│       ├── JmsService.java                      # JMS service configuration
│       ├── JmsHost.java                         # JMS host configuration
│       └── JmsAvailability.java                 # Cluster availability
│
├── gf-jms-injection/                             # CDI injection
│   └── src/main/java/org/glassfish/jms/injection/
│       ├── JMSCDIExtension.java                 # CDI extension
│       ├── InjectableJMSContext.java             # JMSContext wrapper
│       ├── RequestedJMSContextManager.java       # RequestScoped manager
│       ├── TransactedJMSContextManager.java      # TransactionScoped manager
│       ├── JMSContextMetadata.java               # Injection metadata
│       └── AbstractJMSContextManager.java        # Base manager
│
├── admin/                                        # CLI commands
│   └── src/main/java/org/glassfish/jms/admin/cli/
│       ├── CreateJMSResource.java               # Create JMS resources
│       ├── DeleteJMSResource.java               # Delete JMS resources
│       ├── ListJMSResources.java                # List JMS resources
│       ├── CreateJMSHost.java                   # Create JMS hosts
│       ├── DeleteJMSHost.java                   # Delete JMS hosts
│       ├── ListJMSHosts.java                    # List JMS hosts
│       ├── CreateJMSDestination.java            # Create destinations
│       ├── DeleteJMSDestination.java            # Delete destinations
│       ├── ListJMSDestinations.java             # List destinations
│       ├── FlushJMSDestination.java             # Flush destinations
│       ├── JMSPing.java                         # Ping JMS service
│       ├── ChangeMasterBrokerCommand.java       # Change master broker
│       └── ConfigureJMSCluster.java             # Configure JMS cluster
│
└── l10n/                                         # Localization
    └── src/main/languages/
        └── *.properties
```

## Admin Commands

```bash
# Configure JMS service
asadmin set servers.server.server-config.jms-service.type=EMBEDDED

# Add JMS host
asadmin create-jms-host --mqhost localhost --mqport 7676 default_JMS_host

# List JMS hosts
asadmin list-jms-hosts

# Create JMS connection factory
asadmin create-jms-resource --restype javax.jms.ConnectionFactory \
    --property imqAddressList=mq://localhost:7676 jms/MyConnectionFactory

# Create JMS destination
asadmin create-jms-resource --restype javax.jms.Queue \
    --property Name=MyPhysicalQueue jms/MyQueue

# List JMS resources
asadmin list-jms-resources

# Ping JMS connection pool
asadmin ping-connection-pool --target server jms/MyConnectionFactory

# Flush JMS connection pool
asadmin flush-connection-pool --target server jms/MyConnectionFactory

# Delete JMS resource
asadmin delete-jms-resource jms/MyQueue

# Configure JMX connector for broker management
asadmin create-jmx-connector --system jms --port 8686 jmx-connector
```

## Configuration Examples

### Embedded Broker (Default)

```xml
<jms-service type="EMBEDDED" init-timeout-in-seconds="60">
    <jms-host port="7676" admin-user-name="admin" admin-password="admin"/>
</jms-service>
```

### Local Broker

```xml
<jms-service type="LOCAL" init-timeout-in-seconds="60">
    <jms-host port="7676" admin-user-name="admin" admin-password="admin"/>
</jms-service>
```

### Remote Broker

```xml
<jms-service type="REMOTE">
    <jms-host host="broker.example.com" port="7676"/>
</jms-service>
```

### Cluster with Master Broker

```xml
<clusters>
    <cluster name="mycluster">
        <jms-service availability-enabled="true">
            <jms-host host="master.example.com" port="7676"/>
        </jms-service>
    </cluster>
</clusters>
```

## Installed Artifacts

```
$PAYARA_HOME/
├── glassfish/
│   ├── lib/
│   │   ├── install/
│   │   │   └── templates/
│   │   │       └── jms/
│   │   │           └── default-jms-host.xml
│   │   └── apps/
│   │       └── imqjmsra.rar              # OpenMQ RAR
│   └── modules/
│       ├── jms-core.jar
│       ├── jms-handlers.jar
│       ├── gf-jms-connector.jar
│       └── gf-jms-injection.jar
└── mq/                                      # OpenMQ distribution (for EMBEDDED/LOCAL)
    └── lib/
        ├── imq.jar                         # OpenMQ client
        ├── imqbroker.jar                   # OpenMQ broker
        └── ...
```

## Module Dependencies

### jms-core

| Dependency | Purpose |
|------------|---------|
| `gf-jms-connector` | JMS configuration classes |
| `internal-api` | GlassFish internal APIs |
| `glassfish-mbeanserver` | JMX management |
| `connectors-internal-api` | Connectors runtime integration |
| `connectors-inbound-runtime` | MDB activation |
| `connectors-runtime` | JCA container |
| `admin-util` | Admin utilities |
| `transaction-internal-api` | Transaction recovery |
| `gf-ejb-connector` | EJB integration |
| `kernel` | HK2 kernel |
| `jakarta.jms-api` | JMS API |

### jms-handlers

| Dependency | Purpose |
|------------|---------|
| `jakarta.jms-api` | JMS API |
| `connectors-runtime` | Resource deployment |

### gf-jms-injection

| Dependency | Purpose |
|------------|---------|
| `internal-api` | GlassFish internal APIs |
| `glassfish-api` | GlassFish public APIs |
| `weld-integration` | CDI/Weld integration (optional) |
| `connectors-internal-api` | Connectors integration |

## Related Modules

- `appserver/connectors` - Connectors runtime (JCA container)
- `appserver/ejb` - MDB container implementation
- `nucleus/admin/config-api` - Configuration infrastructure
- `appserver/weld-integration` - CDI/Weld integration
- `appserver/resources` - Generic resources framework

## Notes

- JMS Resource Adapter implements JCA 1.7 specification
- OpenMQ is the default JMS provider (replacing older GlassFish MQ)
- Lazy initialization reduces startup time when JMS is not used
- MDBs use the JMS RA for message activation via ActivationSpec
- CDI injection requires gf-jms-injection to be enabled
- Cluster support requires JMS availability and master broker configuration
- Transaction recovery is handled by LegacyJmsRecoveryResourceHandler
