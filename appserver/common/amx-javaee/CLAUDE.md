# CLAUDE.md - AMX JavaEE

This file provides guidance for working with the `amx-javaee` module - JSR 77 Java EE management implementation.

## Module Overview

The amx-javaee module provides JSR 77 (J2EE Management) standard management interfaces for Java EE components, enabling monitoring and management through JMX.

## Build Commands

```bash
# Build amx-javaee
mvn -DskipTests clean package -f appserver/common/amx-javaee/pom.xml
```

## JSR 77 Architecture

### Management Hierarchy

```
J2EEDomain (e.g., "payara-domain")
    │
    ├── J2EEServer (e.g., "server")
    │   │
    │   ├── JVM
    │   │   └── Statistics (memory, threads)
    │   │
    │   ├── J2EEApplication
    │   │   ├── AppClientModule
    │   │   ├── EJBModule
    │   │   │   ├── EJB (Stateless, Stateful, Entity, MDB)
    │   │   │   └── Statistics
    │   │   ├── WebModule
    │   │   │   └── Servlet
    │   │   └── ResourceAdapterModule
    │   │       └── ResourceAdapter
    │   │
    │   └── J2EEResource
    │       ├── JDBCResource
    │       │   ├── JDBCDriver
    │       │   └── JDBCDataSource
    │       ├── JMSResource
    │       ├── JCAResource
    │       │   ├── JCAConnectionFactory
    │       │   └── JCAManagedConnectionFactory
    │       ├── JNDIResource
    │       ├── JTAResource
    │       ├── JavaMailResource
    │       ├── RMI_IIOPResource
    │       └── URLResource
```

## Core Interfaces

### J2EEDomain

Top-level management domain:

```java
J2EEDomain domain = // get domain
String getServerVersion();              // Server version
boolean isStateManageable();            // Can be started/stopped
boolean getStatisticsProvider();        // Provides statistics
```

### J2EEServer

Server instance management:

```java
J2EEServer server = // get server
JVM getJvm();                          // JVM info
Object[] getApplications();             // Deployed apps
Object[] getResources();                // Configured resources
```

### JVM

Runtime statistics:

```java
JVM jvm = // get JVM
long getHeapSize();                    // Current heap size
long getFreeMemory();                  // Free memory
int getThreadCount();                  // Active threads
```

## Application Management

### J2EEApplication

Deployed application metadata:

```java
J2EEApplication app = // get application
String getName();                       // Application name
String getDeploymentDescriptor();       // deployment.xml contents
Object[] getModules();                  // Sub-modules
```

### Module Types

| Module | Description |
|--------|-------------|
| `EJBModule` | EJB JAR deployment |
| `WebModule` | WAR deployment |
| `AppClientModule` | Application client |
| `ResourceAdapterModule` | RAR deployment |

### Component MBeans

| MBean | Description |
|-------|-------------|
| `Servlet` | Servlet instance |
| `EJB` | Enterprise bean (Stateless, Stateful, Entity, MDB) |
| `ResourceAdapter` - JCA resource adapter |

## Resource Management

### JDBC Resources

```java
JDBCResource jdbc = // get resource
String getDatasourceClass();            // DataSource class
JDBCDriver[] getDrivers();              // JDBC drivers
JDBCDataSource[] getDataSources();      // Data sources
```

### JMS Resources

```java
JMSResource jms = // get resource
// JMS connection factories, destinations
```

### JCA Resources

```java
JCAResource jca = // get resource
JCAConnectionFactory[] getConnectionFactories();
JCAManagedConnectionFactory[] getManagedConnectionFactories();
```

## Statistics (JSR 77)

### StatisticsProvider

```java
StatisticsProvider provider = // get provider
Statistic[] getStatistics();            // Available statistics
```

### Statistic Types

| Type | Description |
|------|-------------|
| `CountStatistic` - Incrementing counter |
| `RangeStatistic` - Min/max/current values |
| `TimeStatistic` - Timing data (count, min, max, total) |

### Example Statistics

```java
// Servlet statistics
TimeStatistic getServiceTime();          // Response times
CountStatistic getInvocationCount();     // Request count
RangeStatistic getActiveSessions();      // Active session range
```

## State Management

### StateManageable

```java
StateManageable manageable = // get manageable
int getState();                           // STARTING, RUNNING, STOPPING, STOPPED
void setState(int state);                 // Change state
```

| State | Description |
|-------|-------------|
| `STARTING` | Component initializing |
| `RUNNING` | Component active |
| `STOPPING` | Component shutting down |
| `STOPPED` | Component inactive |

## EventProvider

Management events:

```java
EventProvider provider = // get provider
void sendNotification(Notification notification);
```

## AMX Implementation

### Registration

```java
RegistrationSupport.register(config, mbeanServer);
// Registers all AMX MBeans for a domain
```

### Startup Service

```java
@Service
public class AMXJ2EEStartupService {
    @PostConstruct
    void start() {
        // Register JSR 77 MBeans on startup
    }
}
```

## Package Structure

```
org.glassfish.admin.amx.j2ee/
├── J2EEDomain.java
├── J2EEServer.java
├── JVM.java
├── J2EEApplication.java
├── J2EEModule.java
├── EJBModule.java
├── WebModule.java
├── AppClientModule.java
├── EJB.java
├── Servlet.java
├── J2EEResource.java
├── JDBCResource.java
├── JMSResource.java
├── StateManageable.java
├── StatisticsProvider.java
└── EventProvider.java

org.glassfish.admin.amx.impl.j2ee/
├── J2EEDomainImpl.java
├── J2EEServerImpl.java
├── JVMImpl.java
├── J2EEApplicationImpl.java
├── EJBModuleImpl.java
├── WebModuleImpl.java
├── ServletImpl.java
├── EJBImplBase.java
├── SessionBeanImplBase.java
├── EntityBeanImpl.java
├── MessageDrivenBeanImpl.java
├── RegistrationSupport.java
└── loader/
    └── AMXJ2EEStartupService.java
```

## JMX Access

### Connecting to AMX

```java
JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:8686/jmxrmi");
JMXConnector conn = JMXConnectorFactory.connect(url);
MBeanServerConnection mbsc = conn.getMBeanServerConnection();

// Access JSR 77 MBeans
ObjectName domainName = new ObjectName("j2eeType=J2EEDomain,name=payara-domain");
J2EEDomain domain = JMX.newMBeanProxy(mbsc, domainName, J2EEDomain.class);
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `amx-core` | Core AMX interfaces |
| `dol` | Deployment metadata |
| `stats77` | Statistics support |
| `glassfish-corba-omgapi` | CORBA types |

## Related Modules

- `nucleus/common/amx-core` - Core AMX infrastructure
- `appserver/common/stats77` - JSR 77 statistics
- `appserver/deployment/dol` - Deployment information
