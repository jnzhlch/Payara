# CLAUDE.md - MBeanServer

This file provides guidance for working with the `mbeanserver` module - JMX MBeanServer infrastructure.

## Module Overview

The `mbeanserver` module provides JMX (Java Management Extensions) infrastructure for Payara. It handles:

- MBeanServer creation and initialization
- JMX connector servers (RMI, JMXMP)
- SSL/TLS configuration for secure JMX
- AMX (Appserver Management Extensions) bootstrapping
- Authorization and security for JMX access

## Key Components

### JMXStartupService - Main JMX Service

The `JMXStartupService` is a HK2 service that starts JMX connectors during server startup:

```java
@Service
@RunLevel(mode = RunLevel.RUNLEVEL_MODE_NON_VALIDATING, value = PostStartupRunLevel.VAL)
public final class JMXStartupService implements PostConstruct {
    // Creates BootAMX MBean
    // Starts configured JMX connectors
    // Registers shutdown listeners
}
```

**Lifecycle:**
- Starts at `PostStartupRunLevel` (after basic server startup)
- Reads JMX connector configuration from domain.xml
- Creates connector servers in background thread
- Shuts down on `PREPARE_SHUTDOWN` event

### JMX Connector Protocols

Supports two JMX connector protocols:

#### 1. RMI_JRMP (Default)

```xml
<jmx-connector name="system" port="8686" address="0.0.0.0"
    protocol="rmi_jrmp" security-enabled="false" />
```

- Standard RMI-based JMX connector
- Widely supported by JMX clients (JConsole, VisualVM)
- Uses JRMP (Java Remote Method Protocol)

#### 2. JMXMP

```xml
<jmx-connector name="jmxmp" port="8687" address="0.0.0.0"
    protocol="jmxmp" security-enabled="true" />
```

- Custom JMX Message Protocol
- More firewall-friendly
- Requires additional JARs on client

### MBeanServerFactory

Creates the platform MBeanServer:

```java
// Get or create MBeanServer
MBeanServer mbs = MBeanServerFactory.createMBeanServer("Payara");

// Or use platform MBeanServer
MBeanServer platformMBS = ManagementFactory.getPlatformMBeanServer();
```

## Configuration

### Domain.xml Configuration

JMX connectors are configured in `domain.xml` under `<admin-service>`:

```xml
<admin-service ...>
    <jmx-connector name="system"
        port="8686"
        address="0.0.0.0"
        protocol="rmi_jrmp"
        security-enabled="false"
        auth-realm-name="admin-realm"
        enabled="true">

        <ssl client-auth="want" ssl3-enabled="false"
            certificate-nickname="s1as"
            ssl2-enabled="false"
            tls-enabled="true"
            tls-client-auth="want"/>
    </jmx-connector>
</admin-service>
```

### SSL Configuration

For secure JMX connections:

```xml
<ssl ssl3-enabled="false"
    tls-enabled="true"
    certificate-nickname="s1as"
    client-auth="want"
    cert-nickname="glassfish-instance" />
```

**SSL parameters:**
- `security-enabled="true"` - Enable SSL/TLS
- `certificate-nickname` - Alias in keystore
- `client-auth` - Client authentication (want/required)

## Build Commands

```bash
# Build this module only
mvn -DskipTests clean package -f nucleus/common/mbeanserver/pom.xml

# Build with tests
mvn clean package -f nucleus/common/mbeanserver/pom.xml
```

## Architecture

**Module Structure:**
```
org.glassfish.admin.mbeanserver/
├── JMXStartupService           - Main service, creates connectors
├── MBeanServerFactory          - MBeanServer creation
├── BootAMX                     - AMX bootstrapping MBean
├── BootAMXListener             - Boots AMX on first connection
├── RMIConnectorStarter         - RMI connector implementation
├── JMXMPConnectorStarter       - JMXMP connector implementation
├── ConnectorStarter            - Abstract connector starter
├── AdminAuthorizedMBeanServer  - Authorization wrapper
└── ssl/                        - SSL configuration and socket factories
    ├── SSLParams               - SSL parameter holder
    ├── SSLClientConfigurator   - Client SSL setup
    ├── SecureRMIClientSocketFactory
    └── SecureRMIServerSocketFactory
```

## AMX Integration

AMX (Appserver Management Extensions) provides JMX MBeans for managing Payara:

```java
// BootAMX is registered as an MBean
ObjectName bootAMXName = new ObjectName("amx-support:type=boot-amx");
BootAMXMBean bootAMX = JMX.newMBeanProxy(mbs, bootAMXName, BootAMXMBean.class);

// Boot AMX on first JMX connection
bootAMX.boot();

// AMX MBeans are registered under amx: domain
Set<ObjectName> amxMBeans = mbs.queryNames(new ObjectName("amx:*"), null);
```

**AMX Boot-on-Demand:**
- AMX MBeans are heavy, so boot on first JMX connection
- `BootAMXListener` triggers boot when client connects
- Can be disabled via configuration

## Security and Authorization

### AdminAuthorizedMBeanServer

Wraps MBeanServer with authorization checks:

```java
// Create authorized wrapper
MBeanServer authorizedMBS = AdminAuthorizedMBeanServer.newInstance(
    platformMBS,
    serverEnv.isInstance()
);

// Checks permissions before MBean operations
// Respects @AccessRequired annotations
```

### Authentication

JMX connectors use GlassFish security realms:

```xml
<jmx-connector auth-realm-name="admin-realm" ... />
```

- Default: `admin-realm` (file-based)
- Can use custom realms (LDAP, JDBC, etc.)
- Credentials checked on JMX connection

## Programmatic Access

### Getting JMX Service URL

```java
// After JMXStartupService starts
JMXServiceURL[] urls = JMXStartupService.getJMXServiceURLs(mbs);

// Connect from client
JMXConnector connector = JMXConnectorFactory.connect(urls[0], env);
MBeanServerConnection mbsc = connector.getMBeanServerConnection();
```

### Waiting for JMX Startup

```java
// Blocks until JMX connectors are ready
jmxStartupService.waitUntilJMXConnectorStarted();
```

## Connector Status

JMX connectors have two states:

```java
public enum JMXConnectorStatus {
    STOPPED,  // Not started or stopped
    STARTED   // Running and accepting connections
}
```

## Common Patterns

### 1. Remote JMX Connection (Without Security)

```bash
# Start Payara with JMX enabled
asadmin start-domain

# Connect with JConsole
jconsole localhost:8686

# Or specify service URL
jconsole service:jmx:rmi:///jndi/rmi://localhost:8686/jmxrmi
```

### 2. Secure JMX Connection

```bash
# Enable SSL in domain.xml
asadmin set "server-config.admin-service.jmx-connector.system.security-enabled=true"

# Connect with SSL
jconsole -J-Djavax.net.ssl.trustStore=$GF_HOME/domains/domain1/config/cacerts.jks \
        service:jmx:rmi:///jndi/rmi://localhost:8686/jmxrmi
```

### 3. Programmatic JMX Client

```java
// Connect to remote Payara
Map<String, Object> env = new HashMap<>();
String[] credentials = {"admin", "password"};
env.put(JMXConnector.CREDENTIALS, credentials);

JMXServiceURL url = new JMXServiceURL(
    "service:jmx:rmi:///jndi/rmi://localhost:8686/jmxrmi"
);

JMXConnector connector = JMXConnectorFactory.connect(url, env);
MBeanServerConnection conn = connector.getMBeanServerConnection();

// Use MBeans
MemoryMXBean memory = ManagementFactory.newPlatformMXBeanProxy(
    conn,
    ManagementFactory.MEMORY_MXBEAN_NAME,
    MemoryMXBean.class
);

connector.close();
```

### 4. Custom MBean Registration

```java
// Register custom MBean
MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
ObjectName name = new ObjectName("com.example:type=MyMBean");
mbs.registerMBean(new MyMBean(), name);

// Access via JMX client
```

## Dependencies

**Runtime:**
- `glassfish-api` - Public APIs
- `common-util` - Utilities
- `internal-api` - Internal APIs
- HK2 `hk2-core` - Dependency injection
- `management-api` (GlassFish) - AMX support
- `jmxremote_optional-repackaged` - JMX remote

**Provided:**
- OSGi core/enterprise (for bundle activator)

## Troubleshooting

### JMX Connection Refused

```bash
# Check if JMX connector is enabled
asadmin get "server-config.admin-service.jmx-connector.system.enabled"

# Check if port is in use
netstat -an | grep 8686
```

### SSL Issues

```bash
# Verify certificate nickname
asadmin get "server-config.admin-service.jmx-connector.system.ssl.certificate-nickname"

# Check SSL debug output
jconsole -J-Djavax.net.debug=ssl ...
```

### AMX Not Available

```bash
# Check if AMX is enabled
asadmin get "server-config.amx.enabled"

# Enable AMX
asadmin set "server-config.amx.enabled=true"
```

## Related Modules

- **amx-core** - AMX MBean implementations
- **internal-api** - Server environment and configuration
- **admin/config-api** - Domain configuration
- **admin/monitor** - Monitoring infrastructure
