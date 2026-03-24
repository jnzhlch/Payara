# CLAUDE.md - Appserver ORB

This file provides guidance for working with the `appserver/orb` module - CORBA/IIOP support for Payara Server.

## Module Overview

The appserver/orb module provides CORBA (Common Object Request Broker Architecture) support through IIOP (Internet Inter-ORB Protocol). It enables EJB remote invocations via CORBA, integrates the GlassFish CORBA ORB, and provides failover/load balancing for clustered environments.

**Key Features:**
- **RMI-IIOP Support** - Remote EJB method invocations using CORBA/IIOP protocol
- **POA (Portable Object Adapter)** - Object adapter for managing CORBA object references
- **CSIv2 Security** - CORBA Security Interoperability (SSL/TLS support)
- **Failover and Load Balancing** - Cluster-aware IIOP endpoint management
- **Lazy Initialization** - Optional delayed ORB listener startup
- **OTS Integration** - Object Transaction Service for distributed transactions

## 子目录文档索引 (Subdirectory Documentation)

| 子目录 | 文档位置 | 说明 |
|--------|----------|------|
| **orb-enabler** | [orb-enabler/CLAUDE.md](orb-enabler/CLAUDE.md) | 配置基础设施和启动服务 |
| **orb-connector** | [orb-connector/CLAUDE.md](orb-connector/CLAUDE.md) | 核心 ORB 连接器 API |
| **orb-iiop** | [orb-iiop/CLAUDE.md](orb-iiop/CLAUDE.md) | IIOP 协议实现 |

## Sub-Modules

| Submodule | Purpose | Packaging |
|-----------|---------|------------|
| `orb-enabler` | Configuration beans and ORB startup | glassfish-jar |
| `orb-connector` | Core ORB connector APIs and utilities | glassfish-jar |
| `orb-iiop` | IIOP protocol implementation | glassfish-jar |
| `orb-connector-l10n` | Localized messages | jar |

## Build Commands

```bash
# Build entire orb module
mvn -DskipTests clean package -f appserver/orb/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/orb/orb-enabler/pom.xml
mvn -DskipTests clean package -f appserver/orb/orb-connector/pom.xml
mvn -DskipTests clean package -f appserver/orb/orb-iiop/pom.xml

# Run tests
mvn test -f appserver/orb/orb-connector/pom.xml
```

## Architecture

### ORB Initialization Flow

```
Server Startup
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│              ORBConnectorStartup (RunLevel.VAL)                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. setORBSystemProperties()                            │ │
│  │     - Set ORB class properties                           │ │
│  │     - Set RMI-IIOP delegate classes                     │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. initializeLazyListener()                             │ │
│  │     - Create Grizzly proxy for lazy-init listeners       │ │
│  │     - Defer actual ORB startup until first request       │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│                  GlassFishORBManager                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. initProperties()                                    │ │
│  │     - Read IiopService configuration                     │ │
│  │     - Read IiopListener beans                           │ │
│  │     - Initialize CSIv2 properties                        │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. initORB(Properties)                                 │ │
│  │     - Set FOLB (failover/load balancing) properties     │ │
│  │     - Initialize ORB with GlassFish ORBInitializer      │ │
│  │     - Start glassfish-corba-orb bundle (OSGi)           │ │
│  │     - Create ReferenceFactoryManager                    │ │
│  │     - Register GroupInfoService for clustering          │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│                    GlassFish CORBA ORB                         │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  POAProtocolMgr                                         │ │
│  │  - Initializes RootPOA and EJB-specific POAs            │ │
│  │  - Registers naming service                            │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  POARemoteReferenceFactory                              │ │
│  │  - Creates EJB remote references                        │ │
│  │  - Implements ServantLocator (preinvoke/postinvoke)     │ │
│  │  - Manages ReferenceFactory for EJBObject/EJBHome       │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### EJB Remote Invocation Flow

```
Client Application
       │
       ├─→ JNDI Lookup (COS Naming)
       │   or @EJB injection
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│                     EJB Stub (RMI-IIOP)                        │
│                   (Generated by PresentationManager)           │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│                    IIOP Request                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  PortableInterceptor(s)                                  │ │
│  │  - IORAddrAnyInterceptor                                 │ │
│  │  - CSIv2 Interceptors (security)                         │ │
│  │  - OTS Interceptor (transactions)                        │ │
│  │  - FOLB Interceptors (failover/load balancing)          │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│                  POA ServantLocator                            │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  preinvoke(byte[] ejbKey, ...)                           │ │
│  │  1. Extract instanceKey from ejbKey                     │ │
│  │  2. Call container.getTargetObject(instanceKey)         │ │
│  │  3. Get Tie from PresentationManager                     │ │
│  │  4. Tie.setTarget(targetObj)                             │ │
│  │  5. Return Tie (Servant)                                 │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│                  EJB Container                                 │
│  - Invoke method on target EJB                                │
│  - Return result through ORB                                 │
└────────────────────────────────────────────────────────────────┘
```

## Configuration

### IiopService

```java
@Configured
public interface IiopService extends ConfigBeanProxy, ConfigExtension {
    // Client authentication
    String getClientAuthenticationRequired();  // Default: false

    // ORB configuration
    Orb getOrb();

    // SSL client configuration
    SslClientConfig getSslClientConfig();

    // IIOP listeners
    List<IiopListener> getIiopListener();
}
```

### IiopListener

```java
@Configured
public interface IiopListener {
    String getId();                          // Unique listener ID
    String getAddress();                     // Host/IP address (default: from host)
    String getPort();                        // Port number (default: 1072)
    String getSecurityEnabled();             // Enable SSL (default: false)
    String getEnabled();                     // Enable listener (default: true)
    String getLazyInit();                    // Lazy initialization (default: false)
    Ssl getSsl();                           // SSL configuration

    // Custom properties
    List<Property> getProperty();
}
```

### Orb

```java
@Configured
public interface Orb {
    String getMaxConnections();              // Max connections (default: 1024)
    String getMessageFragmentSize();         // GIOP fragment size in bytes

    // Custom properties passed to ORB
    List<Property> getProperty();
}
```

## ORB Constants

### ORB Properties

```java
// OMG Standard Properties
public static final String OMG_ORB_CLASS_PROPERTY =
    "org.omg.CORBA.ORBClass";
public static final String OMG_ORB_SINGLETON_CLASS_PROPERTY =
    "org.omg.CORBA.ORBSingletonClass";

// Sun/GlassFish Specific Properties
public static final String SUN_ORB_SOCKET_FACTORY_CLASS_PROPERTY =
    "com.sun.CORBA.ORBSocketFactoryClass";

// ORB Server ID
public static final String DEFAULT_SERVER_ID = "100";
public static final String ACC_DEFAULT_SERVER_ID = "101";

// Default Ports
public static final String DEFAULT_ORB_INIT_PORT = "3700";
public static final String DEFAULT_ORB_INIT_HOST = "localhost";
```

### CSIv2 Properties

```java
// CSIv2 (Common Secure Inter-ORB Protocol)
public static final String ORB_SSL_SERVER_REQUIRED =
    "com.sun.CSIV2.ssl.server.required";
public static final String ORB_SSL_CLIENT_REQUIRED =
    "com.sun.CSIV2.ssl.client.required";
public static final String ORB_CLIENT_AUTH_REQUIRED =
    "com.sun.CSIV2.client.authentication.required";
```

## Policy Types

The ORB module defines several custom CORBA policies:

```java
// Policy type values
public static final int OTS_POLICY_TYPE = SUNVMCID.value + 123;
public static final int CSIv2_POLICY_TYPE = SUNVMCID.value + 124;
public static final int REQUEST_DISPATCH_POLICY_TYPE = SUNVMCID.value + 125;
public static final int SFSB_VERSION_POLICY_TYPE = SUNVMCID.value + 126;
```

### Policy Implementations

| Policy | Purpose |
|--------|---------|
| `OTSPolicyImpl` | Object Transaction Service integration |
| `CSIv2Policy` | CORBA security (SSL/TLS, client authentication) |
| `RequestPartitioningPolicy` | Thread pool partitioning for EJBs |
| `SFSBVersionPolicy` | Stateful session bean versioning |
| `ZeroPortPolicy` | Disable clear-text IIOP (SSL-only) |
| `CopyObjectPolicy` | Pass-by-reference vs pass-by-value |
| `ServantCachingPolicy` | Local invocation performance optimization |

## Lazy Initialization

The ORB module supports lazy initialization for faster server startup:

```xml
<iiop-service>
    <iiop-listener id="orb-lazy" port="3700"
                   lazy-init="true"
                   enabled="true" />
</iiop-service>
```

**How it works:**
1. `ORBConnectorStartup` creates a Grizzly network proxy
2. First IIOP request triggers actual ORB initialization
3. Subsequent requests use the initialized ORB

**Constraints:**
- Only one listener can have `lazy-init="true"`
- Lazy init not supported for SSL listeners
- Clear-text listener required for lazy init

## Failover and Load Balancing

### IiopFolbGmsClient

```java
@Service
public class IiopFolbGmsClient implements ClusterListener {
    // Listens to Hazelcast cluster events
    // Updates GroupInfoService with cluster membership

    public void memberAdded(MemberEvent event);
    public void memberRemoved(MemberEvent event);
    public String getIIOPEndpoints();  // Returns "host:port,host:port,..."
}
```

### FOLB Properties

```java
// Set during ORB initialization
orbInitProperties.put(ORBConstants.RFM_PROPERTY, "dummy");
orbInitProperties.setProperty(
    ORBConstants.USER_CONFIGURATOR_PREFIX + "com.sun.corba.ee.impl.folb.ClientGroupManager",
    "dummy");
orbInitProperties.setProperty(
    ORBConstants.USER_CONFIGURATOR_PREFIX + "com.sun.corba.ee.impl.folb.ServerGroupManager",
    "dummy");
```

**Benefits:**
- Automatic failover on instance failure
- Load balancing across cluster instances
- Dynamic endpoint updates via Hazelcast

## POA Remote Reference Factory

### EJB Key Format

```
┌────────────────────────────────────────────────────────────┐
│  EJB Key Format                                            │
│  ┌──────────┬──────────────────┬────────────────────────┐ │
│  │ EJB ID   │ InstanceKey Len  │ InstanceKey             │ │
│  │ (8 bytes)│ (4 bytes)        │ (variable)              │ │
│  └──────────┴──────────────────┴────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
```

### ReferenceFactory Lifecycle

```java
// One POARemoteReferenceFactory per EJB type
POARemoteReferenceFactory factory = protocolMgr.getRemoteReferenceFactory(
    container, remoteHomeView, ejbId);

// Set repository IDs (generated dynamically)
factory.setRepositoryIds(homeIntf, remoteIntf);

// Create remote references
Remote ejbObject = factory.createRemoteReference(instanceKey);
Remote ejbHome = factory.createHomeReference(homeKey);
```

## Admin Commands

```bash
# Create IIOP listener
asadmin create-iiop-listener --listeneraddress 0.0.0.0 \
    --iiopport 3700 --enabled true --securityenabled false \
    --lazyInit false orb-listener

# List IIOP listeners
asadmin list-iiop-listeners

# Delete IIOP listener
asadmin delete-iiop-listener orb-listener

# Configure ORB properties
asadmin set configs.config.server-config.iiop-service.orb.max-connections=2048
asadmin set configs.config.server-config.iiop-service.orb.message-fragment-size=2048
```

## RMI-IIOP Delegates

The ORB sets system properties for RMI-IIOP delegates:

```java
System.setProperty("javax.rmi.CORBA.UtilClass",
    "com.sun.corba.ee.impl.javax.rmi.CORBA.Util");
System.setProperty("javax.rmi.CORBA.StubClass",
    "com.sun.corba.ee.impl.javax.rmi.CORBA.StubDelegateImpl");
System.setProperty("javax.rmi.CORBA.PortableRemoteObjectClass",
    "com.sun.corba.ee.impl.javax.rmi.PortableRemoteObject");
```

## Package Structure

```
appserver/orb/
├── orb-enabler/                                 # Configuration and startup
│   └── src/main/java/
│       ├── org/glassfish/orb/admin/config/
│       │   ├── IiopService.java               # IIOP service config
│       │   ├── IiopListener.java              # IIOP listener config
│       │   └── Orb.java                       # ORB config
│       └── org/glassfish/enterprise/api/enabler/
│           └── ORBConnectorStartup.java       # ORB startup service
│
├── orb-connector/                               # Core ORB connector APIs
│   └── src/main/java/
│       ├── org/glassfish/enterprise/iiop/api/
│       │   ├── GlassFishORBFactory.java       # ORB factory contract
│       │   ├── GlassFishORBHelper.java        # ORB utilities
│       │   ├── GlassFishORBLifeCycleListener.java
│       │   ├── HandleDelegateFacade.java      # RMI-IIOP HandleDelegate
│       │   ├── IIOPConstants.java             # Constants
│       │   ├── IIOPInterceptorFactory.java    # Interceptor factory
│       │   ├── IIOPSSLUtil.java               # SSL utilities
│       │   ├── ORBNamingProxy.java            # Naming proxy
│       │   ├── ProtocolManager.java           # Protocol manager contract
│       │   └── RemoteReferenceFactory.java    # Remote reference factory
│       ├── org/glassfish/enterprise/iiop/spi/
│       │   ├── EjbContainerFacade.java        # EJB container SPI
│       │   └── EjbService.java                # EJB service SPI
│       ├── org/glassfish/enterprise/iiop/util/
│       │   ├── IIOPUtils.java                 # Utilities
│       │   ├── MonitoringConstants.java       # Monitoring constants
│       │   ├── NotServerException.java        # Exception
│       │   ├── ORBCommonStatsImpl.java        # Stats
│       │   └── S1ASThreadPoolManager.java     # Thread pool manager
│       └── org/glassfish/orb/admin/cli/
│           ├── CreateIiopListener.java        # CLI command
│           ├── DeleteIiopListener.java        # CLI command
│           └── ListIiopListeners.java         # CLI command
│
└── orb-iiop/                                    # IIOP implementation
    └── src/main/java/org/glassfish/enterprise/iiop/impl/
        ├── GlassFishORBManager.java           # ORB manager (main)
        ├── GlassFishORBFactoryImpl.java       # ORB factory impl
        ├── GlassFishORBInitializer.java       # ORB initializer
        ├── POAProtocolMgr.java                # POA protocol manager
        ├── POARemoteReferenceFactory.java     # POA remote ref factory
        ├── CSIv2Policy.java                   # CSIv2 policy
        ├── CSIv2SSLTaggedComponentHandlerImpl.java
        ├── OTSPolicyImpl.java                 # OTS policy
        ├── IiopFolbGmsClient.java             # FOLB/GMS client
        ├── IIOPEndpointsInfo.java             # Endpoint info
        ├── IIOPSSLSocketFactory.java          # SSL socket factory
        ├── IIOPHandleDelegate.java            # Handle delegate
        ├── IORAddrAnyInterceptor.java         # IOR interceptor
        └── NamingClusterInfoImpl.java         # Naming cluster info
```

## Module Dependencies

### orb-enabler

| Dependency | Purpose |
|------------|---------|
| `hk2-core` | Dependency injection |
| `glassfish-api` | GlassFish APIs |
| `kernel` | Kernel services |

### orb-connector

| Dependency | Purpose |
|------------|---------|
| `orb-enabler` | Configuration beans |
| `admin-util` | Admin utilities |
| `glassfish-corba-omgapi` | CORBA OMG API |
| `glassfish-corba-internal-api` | CORBA internal API |
| `dol` | Deployment descriptor |
| `jakarta.ejb-api` | EJB API |
| `config-api` | Configuration API |
| `monitoring-core` | Monitoring |

### orb-iiop

| Dependency | Purpose |
|------------|---------|
| `orb-connector` | ORB connector APIs |
| `orb-enabler` | Configuration beans |
| `glassfish-corba-orb` | GlassFish CORBA ORB |
| `pfl-basic` | PFL basic utilities |
| `pfl-dynamic` | PFL dynamic codegen |
| `common-util` | Common utilities |
| `dol` | Deployment descriptor |

## CSIv2 Security

### SSL Configuration

```xml
<iiop-service client-authentication-required="false">
    <ssl-client-config>
        <ssl cert-nickname="s1as" ssl2-enabled="false"
             ssl3-enabled="true" tls-enabled="true"
             client-auth-enabled="false" />
    </ssl-client-config>
    <iiop-listener id="orb-ssl" port="3820" security-enabled="true">
        <ssl cert-nickname="s1as" ssl2-enabled="false"
             ssl3-enabled="true" tls-enabled="true" />
    </iiop-listener>
</iiop-service>
```

### SSL Only Mode

```java
// If all mechanisms require SSL, ZeroPortPolicy is added
if (ejbDescriptor.allMechanismsRequireSSL()) {
    if (!GlassFishORBManager.disableSSLCheck()) {
        policies.add(ZeroPortPolicy.getPolicy());
    }
}
```

## Monitoring

### ORB Statistics

```java
@Singleton
@Contract
public interface ORBCommonStatsProvider {
    // Common ORB statistics
    int getNumRequests();
    int getNumErrors();
    long getTotalResponseTime();
}
```

### ThreadPool Statistics

```java
@Service
public class S1ASThreadPoolManager {
    // ThreadPool management for ORB
    ThreadPoolManager getThreadPoolManager();
    int getThreadPoolNumericId(String threadPoolName);
}
```

## Related Modules

- `appserver/ejb` - EJB container (uses ORB for remote invocations)
- `nucleus/cluster` - Clustering infrastructure (Hazelcast integration)
- `nucleus/admin/config-api` - Configuration infrastructure
- `glassfish-corba` - GlassFish CORBA ORB implementation (external)

## Notes

- ORB initialization happens lazily when `lazy-init="true"` is configured
- Only one IIOP listener can have lazy initialization enabled
- Lazy initialization is not supported for SSL listeners
- CSIv2 security requires proper SSL certificate configuration
- FOLB (failover/load balancing) requires Hazelcast clustering to be enabled
- The ORB uses GlassFish CORBA (formerly Sun CORBA) implementation
- EJB keys contain both EJB ID and instance key for routing
- Thread pool partitioning allows per-EJB thread pool assignment
- Pass-by-reference optimization only applies to co-located callers
