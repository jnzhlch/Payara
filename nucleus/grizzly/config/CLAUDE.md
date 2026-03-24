# CLAUDE.md - Grizzly Config

This file provides guidance for working with the `grizzly-config` module - Grizzly network configuration and listener management.

## Module Overview

The `grizzly-config` module provides the integration layer between Payara's configuration system and the Grizzly NIO framework. It handles:
- Network configuration from domain.xml
- Network listener lifecycle (start/stop)
- SSL/TLS configuration
- HTTP protocol configuration
- Port unification (multiple protocols on single port)
- Dynamic configuration changes

## Build Commands

```bash
# Build grizzly-config module
mvn -DskipTests clean package -f nucleus/grizzly/config/pom.xml

# Build with tests
mvn clean package -f nucleus/grizzly/config/pom.xml
```

## Architecture

### Configuration Flow

```
domain.xml
        │
        ├─→ NetworkConfig (@Configured)
        │      ├─→ Transports
        │      ├─→ Protocols
        │      └─→ NetworkListeners
        │
        ├─→ GrizzlyConfig
        │      ├─→ setupNetwork() - start all listeners
        │      └─→ shutdownNetwork() - stop all listeners
        │
        ├─→ GenericGrizzlyListener
        │      ├─→ configure() - setup from NetworkListener config
        │      ├─→ start() / stop() - lifecycle
        │      └─→ processDynamicConfigurationChange() - runtime updates
        │
        └─→ Grizzly NIO Stack
              ├─→ TCPNIOTransport
              ├─→ FilterChain
              └─→ ThreadPool
```

### Package Structure

| Package | Purpose |
|---------|---------|
| `org.glassfish.grizzly.config` | Main config classes |
| `org.glassfish.grizzly.config.dom` | Config domain objects (@Configured) |
| `org.glassfish.grizzly.config.ssl` | SSL/TLS implementation |
| `org.glassfish.grizzly.config.portunif` | Port unification support |

## Core Classes

### GrizzlyConfig

Main entry point for network setup:

```java
// Create from domain.xml
GrizzlyConfig config = new GrizzlyConfig("path/to/domain.xml");

// Setup all network listeners
config.setupNetwork();

// Shutdown all network listeners
config.shutdownNetwork();

// Get configuration root
NetworkConfig networkConfig = config.getConfig();

// Get active listeners
List<GrizzlyListener> listeners = config.getListeners();
```

### GenericGrizzlyListener

Implementation of GrizzlyListener that handles actual network operations:

```java
GenericGrizzlyListener listener = new GenericGrizzlyListener();

// Configure from domain.xml NetworkListener
listener.configure(serviceLocator, networkListener);

// Start listening
listener.start();

// Get adapter for container integration
HttpServer adapter = listener.getAdapter(HttpServer.class);

// Stop listening
listener.stop();

// Dynamic reconfiguration
listener.processDynamicConfigurationChange(serviceLocator, events);
```

### GrizzlyListener Interface

```java
public interface GrizzlyListener {
    void start() throws IOException;
    void stop() throws IOException;
    void destroy();
    String getName();
    InetAddress getAddress();
    int getPort();
    PortRange getPortRange();
    void configure(ServiceLocator habitat, NetworkListener networkListener);
    void processDynamicConfigurationChange(ServiceLocator habitat, PropertyChangeEvent[] events);
    <T> T getAdapter(Class<T> adapterClass);
}
```

## Configuration Domain Objects

### NetworkConfig

Root configuration object for network settings:

```java
@Configured
public interface NetworkConfig extends ConfigBeanProxy, PropertyBag {
    Transports getTransports();
    Protocols getProtocols();
    NetworkListeners getNetworkListeners();

    @DuckTyped
    NetworkListener getNetworkListener(String name);

    @DuckTyped
    Transport getTransport(String name);

    @DuckTyped
    Protocol getProtocol(String name);
}
```

### NetworkListener

Represents a single network listener endpoint:

```java
@Configured
public interface NetworkListener extends ConfigBeanProxy, PropertyBag {
    String getAddress();        // IP to bind to (default: 0.0.0.0)
    int getPort();              // Port number
    String getProtocol();       // Reference to Protocol
    String getTransport();      // Reference to Transport
    boolean isEnabled();        // Enabled flag
    String getName();           // Listener name

    // JK (mod_jk) support
    boolean isJkEnabled();
    String getJkConfigurationFile();
}
```

### Protocol

Defines protocol configuration (HTTP, SSL, port unification):

```java
@Configured
public interface Protocol extends ConfigBeanProxy {
    String getName();
    Http getHttp();
    Ssl getSsl();
    PortUnification getPortUnification();
    ThreadPool getThreadPool();
    String getSecurityEnabled();
}
```

### Http

HTTP-specific settings:

```java
@Configured
public interface Http extends ConfigBeanProxy {
    FileCache getFileCache();
    String getDefaultVirtualServer();
    int getMaxConnections();
    int getRequestTimeoutSeconds();
    int getTimeoutSeconds();
    int getKeepAliveTimeoutSeconds();
    boolean isHstsEnabled();
    int getMaxAgeSeconds();
}
```

### Ssl

SSL/TLS configuration:

```java
@Configured
public interface Ssl extends ConfigBeanProxy {
    String getCertNickname();           // Certificate alias
    boolean isSsl2Enabled();
    boolean isSsl3Enabled();
    boolean isTlsEnabled();
    boolean isTls11Enabled();
    boolean isTls12Enabled();
    boolean isTls13Enabled();
    String getClientAuth();             // "none", "want", "need"
    String getKeyStore();
    String getTrustStore();
    String getCrlPath();
}
```

### Transport

Transport-level configuration:

```java
@Configured
public interface Transport extends ConfigBeanProxy {
    String getName();
    int getAcceptorThreads();           // -1 for CPU count
    int getSelectorPollTimeoutMillis();
    int getMaxConnections();            // -1 for unlimited
    int getReadBufferSize();
    int getWriteBufferSize();
    String getSelectionKeyHandler();
}
```

### ThreadPool

Thread pool configuration:

```java
@Configured
public interface ThreadPool extends ConfigBeanProxy {
    String getName();
    int getMaxThreadPoolSize();         // Maximum threads
    int getMinThreadPoolSize();         // Minimum threads
    int getMaxQueueSize();              // Task queue size
    int getIdleThreadTimeoutSeconds();
    String getThreadPriority();
}
```

## SSL Configuration

### SSLConfigurator

Configures SSL from Ssl config object:

```java
Ssl sslConfig = protocol.getSsl();
SSLConfigurator configurator = new SSLConfigurator(sslConfig, serviceLocator);

// Create SSL context
SSLContext sslContext = configurator.createSSLContext();

// Configure SSLEngine
SSLEngine engine = configurator.configureSSLEngine(sslContext);
```

### SSL Implementation Classes

| Class | Purpose |
|-------|---------|
| `JSSEImplementation` | JSSE-based SSL implementation |
| `JSSESupport` | JSSE support utilities |
| `JSSEKeyManager` | Custom X509KeyManager |
| `JSSESocketFactory` | Custom socket factory |
| `ServerSocketFactory` | Server socket factory |

### SSL Password Provider

```java
// SecurePasswordProvider for reading passwords
SecurePasswordProvider provider = new SecurePasswordProvider();
char[] password = provider.getPassword("ssl");

// From alias in domain.xml
String password = provider.getPasswordForAlias("s1as");
```

## Port Unification

### PortUnification

Allows multiple protocols on single port:

```java
@Configured
public interface PortUnification extends ConfigBeanProxy {
    List<ProtocolFinder> getProtocolFinders();
}
```

### ProtocolFinder

Detects protocol from incoming connection:

```java
@Configured
public interface ProtocolFinder extends ConfigBeanProxy {
    String getName();
    String getProtocol();           // Target protocol
    String getClassname();          // Finder implementation class
}
```

### HttpProtocolFinder

```java
// Detects HTTP traffic by inspecting first bytes
public class HttpProtocolFinder implements ProtocolFinder {
    @Override
    public ProtocolFinderResult find(final Context context) {
        // Check for HTTP request bytes
        if (isHttpRequest(context)) {
            return ProtocolFinderResult.FOUND;
        }
        return ProtocolFinderResult.NOT_FOUND;
    }
}
```

### HttpRedirectFilter

```java
// Redirects HTTP to HTTPS
public class HttpRedirectFilter extends BaseFilter {
    @Override
    public Action handleEvent(FilterChainContext ctx) {
        // Send redirect response
        return new Action(true, RedirectFilter.redirectResponse(redirectPort));
    }
}
```

## HTTP File Cache

### FileCache Configuration

```java
@Configured
public interface FileCache extends ConfigBeanProxy {
    boolean isEnabled();
    int getMaxAgeSeconds();            // Cache expiration
    int getMaxFiles();                 // Max cached files
    int getCacheSize();                // Cache size in KB
}
```

### File Cache Behavior

- Caches static file content (HTML, CSS, JS, images)
- Reduces disk I/O for frequently accessed files
- Configurable max age, size, file count

## HSTS (HTTP Strict Transport Security)

### HSTSFilter

Enforces HSTS for HTTPS connections:

```java
public class HSTSFilter extends BaseFilter {
    private boolean enabled;
    private int maxAge;

    @Override
    public Action handleRead(FilterChainContext ctx) {
        // Add Strict-Transport-Security header
        addHstsHeader(response);
        return context.getInvokeAction();
    }
}
```

### Configuration

```xml
<http hsts-enabled="true" max-age-seconds="31536000"/>
```

## Dynamic Reconfiguration

### Process Dynamic Configuration Changes

```java
// Listen for config changes
PropertyChangeEvent[] events = ...;

// Update listener without restart
listener.processDynamicConfigurationChange(serviceLocator, events);

// Example events:
// - Port change (requires restart)
// - Protocol change (requires restart)
// - Thread pool change (may update dynamically)
```

### Config Change Handling

```java
public void processDynamicConfigurationChange(ServiceLocator habitat,
                                             PropertyChangeEvent[] events) {
    for (PropertyChangeEvent event : events) {
        String propName = event.getPropertyName();
        Object newValue = event.getNewValue();

        if ("enabled".equals(propName)) {
            // Handle enable/disable
        } else if ("port".equals(propName)) {
            // Handle port change
        }
    }
}
```

## Admin Integration

### Container Registration

```java
// Containers register with GrizzlyListener
public void registerContainer(String name, Container container) {
    HttpServer adapter = listener.getAdapter(HttpServer.class);
    adapter.addContainer(container, name);
}

// Add context root
adapter.createServerContext(contextRootInfo, httpHandler);
```

### ContextRootInfo

```java
// Maps context root to container
public class ContextRootInfo {
    private String contextRoot;
    private Class<? extends Container> containerClass;

    // Used for routing requests to proper container
}
```

## Utilities

### Utils Class

```java
// Service locator utilities
ServiceLocator habitat = Utils.getServiceLocation();

// Get service
NetworkConfig config = habitat.getService(NetworkConfig.class);

// Parse address string
InetAddress address = Utils.getAddress("0.0.0.0");

// Resolve system properties
String value = Utils.resolveSystemProperty("${com.sun.aas.instanceRoot}");
```

## Related Modules

- **admin/config-api** - Configuration framework (@Configured)
- **core/kernel** - ApplicationLifecycle integration
- **appserver/web** - Web container integration

## Common Patterns

### 1. Custom Network Listener

```java
// Create custom GrizzlyListener
@Service
public class MyGrizzlyListener implements GrizzlyListener {

    @Override
    public void configure(ServiceLocator habitat, NetworkListener networkListener) {
        // Custom configuration logic
        Transport transport = habitat.getService(Transport.class,
                                               networkListener.getTransport());

        // Setup Grizzly transport
        TCPNIOTransport tcpTransport = createTransport(transport);
    }

    @Override
    public void start() throws IOException {
        tcpTransport.bind(address, port);
        tcpTransport.start();
    }
}
```

### 2. Custom Protocol Finder

```java
// Implement protocol detection
public class MyProtocolFinder implements ProtocolFinder {

    @Override
    public ProtocolFinderResult find(Context context) {
        Connection connection = context.getConnection();

        // Read first bytes
        byte[] data = new byte[4];
        context.read(buffer);

        // Detect protocol
        if (isMyProtocol(data)) {
            return ProtocolFinderResult.FOUND;
        }
        return ProtocolFinderResult.NOT_FOUND;
    }
}
```

### 3. Dynamic Configuration Listener

```java
// Listen for config changes
events.register(new EventListener() {
    @Override
    public void event(Event event) {
        if (event.is(EventTypes.SERVER_SHUTDOWN)) {
            // Shutdown listeners
            grizzlyConfig.shutdownNetwork();
        }
    }
});
```

## Troubleshooting

### Listener Not Starting

```java
// Check configuration is valid
validateConfig(networkConfig);

// Check port not in use
if (isPortInUse(port)) {
    LOGGER.log(Level.WARNING, "Port {0} already in use", port);
}

// Check transport configured
Transport transport = networkConfig.getTransport(listener.getTransport());
if (transport == null) {
    throw new IllegalStateException("Transport not found");
}
```

### SSL Configuration Issues

```java
// Check certificate exists
KeyStore ks = KeyStore.getInstance("JKS");
ks.load(new FileInputStream(keyStoreFile), password);

// Check cert alias
if (!ks.containsAlias(certNickname)) {
    LOGGER.log(Level.SEVERE, "Certificate {0} not found", certNickname);
}
```

### Thread Pool Exhaustion

```java
// Check thread pool config
ThreadPool pool = protocol.getThreadPool();

// Increase max threads
if (pool.getMaxThreadPoolSize() < 200) {
    LOGGER.log(Level.WARNING, "Thread pool too small");
}

// Check queue size
if (pool.getMaxQueueSize() < 4096) {
    LOGGER.log(Level.WARNING, "Queue size too small");
}
```
