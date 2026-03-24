# CLAUDE.md - AMX Core

This file provides guidance for working with the `amx-core` module - Appserver Management Extensions (AMX) Core.

## Module Overview

AMX (Appserver Management Extensions) provides JMX-based management and monitoring capabilities for Payara. AMX exposes manageable components as MBeans with typed interfaces, allowing both programmatic and tool-based management.

**Key Features:**
- JMX MBeans for all Payara configuration and runtime components
- Type-safe interfaces via AMXProxy
- Query support for finding MBeans
- Logging and monitoring MBeans
- Pathname-based navigation of the MBean hierarchy

## AMX Architecture

### AMX Core Packages

- `org.glassfish.admin.amx.base` - Core AMX interfaces (DomainRoot, RuntimeRoot, etc.)
- `org.glassfish.admin.amx.config` - Configuration MBeans
- `org.glassfish.admin.amx.core` - AMX proxy and metadata infrastructure
- `org.glassfish.admin.amx.annotation` - AMX annotations (@ManagedAttribute, @ManagedOperation)
- `org.glassfish.admin.amx.logging` - Log query and analysis
- `org.glassfish.admin.amx.monitoring` - Monitoring MBeans
- `org.glassfish.admin.amx.impl.*` - AMX implementations
- `fish.payara.admin.amx` - Payara-specific AMX extensions

### AMX Root MBeans

```java
// DomainRoot - Entry point for domain-wide management
ObjectName domainRoot = new ObjectName("amx: type=domain-root");
DomainRoot dr = AMXProxy.newProxy(mbs, domainRoot, DomainRoot.class);

// RuntimeRoot - Entry point for runtime MBeans
ObjectName runtimeRoot = new ObjectName("amx-support:type=runtime-root");
RuntimeRoot rr = AMXProxy.newProxy(mbs, runtimeRoot, RuntimeRoot.class);
```

## Enabling/Disabling AMX

AMX can be enabled or disabled via asadmin:

```bash
# Check AMX status
asadmin get "server-config.amx-enabled"

# Enable AMX (default: true)
asadmin set "server-config.amx-enabled=true"

# Disable AMX (saves resources, no JMX management MBeans)
asadmin set "server-config.amx-enabled=false"

# Set AMX boot mode
asadmin set "server-config.amx-boot-mode=lazy"  # Boot on first JMX connection
```

## Build Commands

```bash
# Build this module only
mvn -DskipTests clean package -f nucleus/common/amx-core/pom.xml

# Build with tests
mvn clean package -f nucleus/common/amx-core/pom.xml

# Run AMX tests
mvn test -f nucleus/common/amx-core/pom.xml
```

## Core AMX Concepts

### 1. AMXProxy - Type-Safe MBean Access

AMXProxy provides type-safe access to AMX MBeans:

```java
// Create proxy for any AMX MBean
ObjectName objectName = ...;
MyAMXInterface proxy = AMXProxy.newProxy(mbs, objectName, MyAMXInterface.class);

// Use like a regular Java object
String value = proxy.getName();
proxy.setName("newName");
```

### 2. Pathnames - Hierarchical Navigation

AMX MBeans are organized in a hierarchy accessible via pathnames:

```java
Pathnames paths = dr.getPathnames();

// Navigate the hierarchy
String serverPath = paths.server("server");
String applicationPath = paths.application("myapp", "server");

// Convert pathname to ObjectName
ObjectName objName = PathnameParser.parse(pathname);
```

**Pathname syntax:** `amx:domain-root[type=domain-root]/server[name=server]/application[name=myapp]`

### 3. Query - Finding MBeans

```java
Query query = dr.getQuery();

// Query for all applications
Set<ObjectName> apps = query.queryNames("amx:* type=application,*");

// Query for specific server
ObjectName server = query.queryName("amx:* type=server,name=server,*");

// Wildcard queries
Set<ObjectName> allMBeans = query.queryNames("amx:*");
```

### 4. Ext - Extension MBeans

```java
Ext ext = dr.getExt();

// Access extension MBeans
// Extensions provide additional management capabilities
```

## Configuration MBeans

AMX exposes configuration as MBeans:

```java
// Access configuration MBeans
ConfigBeanProxy config = ...;  // HK2 config bean

// Get corresponding AMX MBean
ObjectName amxMBean = ConfigBeanJMXSupportRegistry.getInstance().
    getObjectName(config);

// Access via AMXConfigProxy
AMXConfigProxy configProxy = AMXProxy.newProxy(mbs, amxMBean, AMXConfigProxy.class);
```

## Monitoring MBeans

AMX provides runtime monitoring:

```java
// RuntimeRoot provides access to runtime MBeans
RuntimeRoot rr = ...;

// Server runtime monitoring
ServerRuntime serverRuntime = rr.getServerRuntime("server");

// Application runtime monitoring
// Each deployed application has runtime MBeans
```

## Logging

AMX includes log query and analysis capabilities:

```java
// Query server logs
LogQuery logQuery = ...;
LogQueryResult result = logQuery.query("level=SEVERE");

// Analyze logs
LogAnalyzer analyzer = ...;
```

### Log MBeans

```java
// Logging MBean
ObjectName loggingMBean = new ObjectName("amx-support:type=logging");
Logging logging = AMXProxy.newProxy(mbs, loggingMBean, Logging.class);

// Query logs
LogQueryResult result = logging.queryLogs(...);
```

## AMX Annotations

For creating custom AMX MBeans:

```java
@AMXMetaInfo(description="My Custom MBean")
public interface MyCustomMBean {

    @ManagedAttribute
    String getName();

    @ManagedAttribute
    void setName(String name);

    @ManagedOperation
    void doSomething(@Param(name="param") String param);
}
```

## Payara AMX Extensions

Payara adds custom AMX MBeans:

```java
// Payara notification configuration MBeans
// fish.payara.internal.notification.admin.*

// Payara health check MBeans
// fish.payara.admin.amx.*
```

### AMXBootService

Payara's AMX boot service:

```java
@Service
public class AMXBootService {
    // Controls AMX initialization
    // Respects amx-enabled configuration
}
```

### SetAmxEnabled Command

```bash
# Enable/disable AMX via CLI
asadmin set-amx-enabled true
asadmin set-amx-enabled false
```

## Common Patterns

### 1. Navigate AMX Hierarchy

```java
// Get DomainRoot
DomainRoot dr = AMXProxy.newProxy(mbs,
    new ObjectName("amx:type=domain-root"), DomainRoot.class);

// Get servers
Set<ObjectName> servers = dr.getChildren().keySet();
for (ObjectName server : servers) {
    Server sr = AMXProxy.newProxy(mbs, server, Server.class);
    // ...
}

// Get applications
Query query = dr.getQuery();
Set<ObjectName> apps = query.queryNames("amx:* type=application,*");
```

### 2. Monitor Application State

```java
// Query application MBean
ObjectName appObj = query.queryName("amx:* type=application,name=myapp,*");
Application app = AMXProxy.newProxy(mbs, appObj, Application.class);

// Check state
String state = app.getState(); // "RUNNING", "STOPPED", etc.
```

### 3. Access Configuration

```java
// Access server config
ObjectName serverConfig = query.queryName(
    "amx:* type=server-config,name=server-config,*");

ServerConfig sc = AMXProxy.newProxy(mbs, serverConfig, ServerConfig.class);

// Read/write config values
String httpPort = sc.getHttpPort();
sc.setHttpPort("8080");
```

### 4. Custom AMX MBean

```java
// Create AMX-compliant MBean
@AMXMetaInfo(description="My Management Bean")
public class MyMBeanImpl implements MyMBean, MBeanRegistration {
    // Implementation
}

// Register with AMX metadata
MBeanInfoSupport.addAMXInfo(mBeanInfo, meta);
```

## AMX ObjectName Patterns

AMX MBeans use specific ObjectName patterns:

```java
// Configuration MBeans
amx:pp=/domain/configs/config[server-config],type=http-service
amx:pp=/domain/servers/server[server],type=server

// Runtime MBeans
amx-support:type=runtime-root
amx-support:type=server-runtime,name=server

// Monitoring MBeans
amx-support:type=monitoring-root
amx-support:category=monitoring,type=server-mon,name=server
```

## Testing

AMX tests verify:

```bash
# Run AMX tests
mvn test -f nucleus/common/amx-core/pom.xml

# Specific test
mvn test -Dtest=AMXConfigTest -f nucleus/common/amx-core/pom.xml
mvn test -Dtest=ObjectNamesTest -f nucleus/common/amx-core/pom.xml
```

## Dependencies

**Runtime:**
- `glassfish-api` - Public APIs
- `internal-api` - Internal APIs
- `mbeanserver` - MBeanServer support
- `kernel` (nucleus/core) - HK2 kernel
- `security` (nucleus/security) - Security infrastructure
- `management-api` (external) - AMX support

**Provided:**
- HK2 (optional) - For annotations

## Performance Considerations

AMX is **expensive** - it creates MBeans for all manageable components:

- **Use lazy boot mode** for production: `amx-boot-mode=lazy`
- **Disable AMX** if not using JMX management: `amx-enabled=false`
- AMX MBeans are created on first JMX connection in lazy mode

## Related Modules

- **mbeanserver** - JMX connector infrastructure
- **admin/monitor** - Monitoring infrastructure
- **admin/config-api** - Configuration management
- **internal-api** - Server runtime APIs

## Further Reading

- JMX Tutorial: https://docs.oracle.com/javase/8/docs/technotes/guides/jmx/
- Payara AMX documentation: See Payara documentation site
