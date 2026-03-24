# CLAUDE.md - GlassFish EE API

This file provides guidance for working with the `glassfish-ee-api` module - Java EE public APIs for GlassFish/Payara.

## Module Overview

The glassfish-ee-api module aggregates Jakarta EE public APIs and GlassFish-specific extensions used across the application server.

## Build Commands

```bash
# Build glassfish-ee-api
mvn -DskipTests clean package -f appserver/common/glassfish-ee-api/pom.xml
```

## Purpose

This module serves as:

1. **API Aggregation** - Collects Jakarta EE APIs used by appserver
2. **Public Interfaces** - Defines extension points for containers
3. **Type Safety** - Provides shared types for cross-container integration

## Key APIs

### Naming APIs

```java
org.glassfish.api.naming
├── NamingClusterInfo          # Cluster naming configuration
├── NamingObjectProxy           # Lazy JNDI object proxy
├── JNDIPropertyNames           # JNDI property constants
└── ExceptionProxyObject        # Exception handling proxy
```

### Container APIs

```java
org.glassfish.api.container
├── Container                   # Base container interface
├── RequestProcessor            # Request processing
└── Sniffer                     # Application type detection
```

### Admin/Configuration APIs

```java
org.glassfish.api.admin
├── ProcessEnvironment          # Process type (server/client)
├── ProcessType                 # SERVER, ACC, OTHER
├── ServerEnvironment           # Server context
└── CommandRunner               # Command execution
```

### Deployment APIs

```java
org.glassfish.api.deployment
├── DeploymentContext           # Deployment operation context
├── Deployer                    # Deployment handler
├── ApplicationContainer        # Deployed application container
└── MetaData                    # Deployment metadata
```

### Admin Command APIs

```java
org.glassfish.api.admin
├── AdminCommand                # Command interface
├── AdminCommandSecurity        # Authorization
├── CommandParameters           # Parameter binding
├── ParameterMap                # Command parameters
└── Progress                     # Progress tracking
```

## Naming Extensions

### NamingClusterInfo

Cluster-aware JNDI configuration:

```java
public interface NamingClusterInfo {
    String IIOP_URL_PROPERTY = "com.sun.enterprise.naming.iiop.url";

    // Set cluster endpoints for load balancing
    // Format: "corbaloc::host1:port1,host2:port2,..."
}
```

### NamingObjectProxy

Lazy JNDI lookup proxy:

```java
public interface NamingObjectProxy extends Serializable {
    Object create(SerialContext ctx) throws NamingException;
    String getJndiName();
}
```

Usage:
```java
// Defer JNDI lookup until first use
NamingObjectProxy proxy = new CommonResourceProxy("jdbc/__default");
// Bound in JNDI, lookup happens on first access
```

## Container Integration

### Container

Base interface for all containers:

```java
public interface Container {
    String getName();
    void start();
    void stop();
}
```

### ApplicationContainer

Deployed application representation:

```java
public interface ApplicationContainer {
    Object getDescriptor();
    boolean start(ApplicationContext context);
    boolean stop(ApplicationContext context);
    boolean suspend();
    boolean resume();
}
```

## Deployment Context

### DeploymentContext

Provides information during deployment:

```java
public interface DeploymentContext {
    SourceDir getSource();               // Application source
    SourceDir.getScratchDir("type");     // Scratch directory
    ClassLoader getClassLoader();        # Application class loader
    <T> T getModuleMetaData(Class<T> type);
    Properties getCommandParameters();
    ActionReport getActionReport();
}
```

## Admin Commands

### Command Parameters

```java
@ExecuteOn(RuntimeType.ALL)
@RestEndpoints({
    @RestEndpoint(configBean=Resources.class,
        opType=RestEndpoint.OpType.POST,
        path="create-jdbc-connection-pool",
        description="Create JDBC Connection Pool")
})
@Scoped(PerLookup.class)
public class CreateJdbcConnectionPool implements AdminCommand {
    @Param(name="name", primary=true)
    String name;

    @Param(name="datasourceclassname")
    String dataSourceClassName;

    @Override
    public void execute(AdminCommandContext context) {
        // Command implementation
    }
}
```

## Monitoring APIs

```java
org.glassfish.api.monitoring
├── MonitoringItem             # Monitoring data point
├── ContainerMonitoring        # Container monitoring
└── MonitoringStats            # Statistics interface
```

## Configuration Beans

```java
org.glassfish.config.support
├── TranslatedConfigView       # Configuration expansion
├── Singleton                  # Singleton pattern
└── CreationException          # Creation errors
```

### Configuration Expansion

```java
// Expand system properties in configuration
String expanded = TranslatedConfigView.expandValue("${com.sun.aas.installRoot}/lib");
```

## Security Integration

```java
org.glassfish.api.security
├── SecurityContext            # Current security context
└── Authenticator              # Authentication
```

## Package Structure

```
org.glassfish.api/
├── admin/
│   ├── AdminCommand.java
│   ├── CommandRunner.java
│   ├── ProcessEnvironment.java
│   ├── ProcessType.java
│   └── ServerEnvironment.java
├── naming/
│   ├── NamingClusterInfo.java
│   ├── NamingObjectProxy.java
│   └── JNDIPropertyNames.java
├── container/
│   ├── Container.java
│   ├── RequestProcessor.java
│   └── Sniffer.java
├── deployment/
│   ├── DeploymentContext.java
│   ├── Deployer.java
│   └── ApplicationContainer.java
├── monitoring/
│   ├── MonitoringItem.java
│   └── ContainerMonitoring.java
├── security/
│   ├── SecurityContext.java
│   └── Authenticator.java
└── config/
    └── TranslatedConfigView.java
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | Core public APIs |
| `common-util` | Shared utilities |
| `security` | Security integration |

## Related Modules

- `nucleus/common/glassfish-api` - Core public APIs
- All appserver containers - Use these APIs
- Admin commands - Use admin APIs
