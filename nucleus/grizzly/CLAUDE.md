# CLAUDE.md - Grizzly

This file provides guidance for working with the `nucleus/grizzly` module - HTTP/NIO networking layer.

## Module Overview

The `nucleus/grizzly` module integrates the Grizzly NIO framework into Payara, providing:
- HTTP/HTTPS network listeners
- Non-blocking I/O (NIO) based connection handling
- Thread pool management for network operations
- SSL/TLS configuration
- Port unification (multiple protocols on single port)

## Sub-Modules

- **config** - Grizzly network configuration and listener management
- **nucleus-grizzly-all** - Combined Grizzly JAR bundle (all Grizzly dependencies)

## Build Commands

```bash
# Build all grizzly modules
mvn -DskipTests clean package -f nucleus/grizzly/pom.xml

# Build specific module
mvn -DskipTests clean package -f nucleus/grizzly/config/pom.xml
mvn -DskipTests clean package -f nucleus/grizzly/nucleus-grizzly-all/pom.xml
```

## Architecture

### Network Configuration Flow

```
domain.xml network-config
        │
        ├─→ NetworkConfig (configuration root)
        │      ├─→ Transports (TCP, SSL transports)
        │      ├─→ Protocols (HTTP, HTTPS, port unification)
        │      └─→ NetworkListeners (listener endpoints)
        │
        ├─→ GrizzlyConfig (reads config, creates listeners)
        │      └─→ setupNetwork() / shutdownNetwork()
        │
        ├─→ GrizzlyListener (listener interface)
        │      └─→ GenericGrizzlyListener (implementation)
        │            ├─→ configure()
        │            ├─→ start() / stop()
        │            └─→ processDynamicConfigurationChange()
        │
        └─→ Grizzly Network Stack
              ├─→ TCPNIOTransport
              ├─→ FilterChain (protocol filters)
              └─→ ThreadPool
```

### Configuration Hierarchy

```
NetworkConfig
├── Transports
│   └── Transport (name, acceptor-threads)
├── Protocols
│   └── Protocol
│       ├── Http (file-cache, max-connections)
│       ├── Ssl (cert-nickname, ssl2-enabled, etc.)
│       └── PortUnification (protocol-finders)
└── NetworkListeners
    └── NetworkListener (name, address, port, protocol-ref, transport-ref)
```

## Network Configuration

### domain.xml Structure

```xml
<network-config>
  <transports>
    <transport name="tcp" acceptor-threads="-1"/>
  </transports>
  <protocols>
    <protocol name="http-listener">
      <http default-virtual-server="server" max-connections="250">
        <file-cache enabled="false"/>
      </http>
    </protocol>
    <protocol name="sec-admin-listener">
      <ssl ssl3-enabled="false" cert-nickname="s1as"/>
      <http/>
    </protocol>
  </protocols>
  <network-listeners>
    <network-listener name="http-listener-1"
                      protocol="http-listener"
                      transport="tcp"
                      port="8080"/>
    <network-listener name="admin-listener"
                      protocol="sec-admin-listener"
                      transport="tcp"
                      port="4848"/>
  </network-listeners>
</network-config>
```

## Key Components

### GrizzlyConfig

Main entry point for Grizzly network configuration:

```java
// Initialize from config file
GrizzlyConfig config = new GrizzlyConfig("domain.xml");

// Setup all network listeners
config.setupNetwork();

// Shutdown all network listeners
config.shutdownNetwork();
```

### GrizzlyListener

Interface for network listener lifecycle:

```java
public interface GrizzlyListener {
    void start() throws IOException;
    void stop() throws IOException;
    void destroy();
    String getName();
    InetAddress getAddress();
    int getPort();
    void configure(ServiceLocator habitat, NetworkListener networkListener);
    void processDynamicConfigurationChange(ServiceLocator habitat, PropertyChangeEvent[] events);
    <T> T getAdapter(Class<T> adapterClass);
}
```

### Network Listeners

| Listener | Default Port | Purpose |
|----------|--------------|---------|
| `http-listener-1` | 8080 | HTTP applications |
| `http-listener-2` | 8181 | Alternate HTTP |
| `admin-listener` | 4848 | Admin console (HTTP) |
| `sec-admin-listener` | 4849 | Admin console (HTTPS) |

## Admin Commands

```bash
# List network listeners
asadmin list-network-listeners

# Create network listener
asadmin create-network-listener --listenerport 9090 --listenername my-listener

# Configure network listener
asadmin set server-config.network-config.network-listeners.network-listener.my-listener.enabled=true

# Delete network listener
asadmin delete-network-listener my-listener

# Create protocol
asadmin create-protocol --securityenabled=false my-protocol

# Configure HTTP settings
asadmin set server-config.network-config.protocols.protocol.my-protocol.http.max-connections=500
```

## SSL Configuration

### SSL Settings in domain.xml

```xml
<protocol name="https-listener">
  <ssl cert-nickname="s1as"
       ssl2-enabled="false"
       ssl3-enabled="false"
       tls-enabled="true"
       tls1.1-enabled="true"
       tls1.2-enabled="true"
       tls1.3-enabled="true"
       client-auth="want"/>
  <http/>
</protocol>
```

### SSL Configuration via CLI

```bash
# Enable SSL on a listener
asadmin create-protocol --securityenabled=true https-protocol

# Set certificate nickname
asadmin set server-config.network-config.protocols.protocol.https-protocol.ssl.cert-nickname="my-cert"

# Configure TLS versions
asadmin set server-config.network-config.protocols.protocol.https-protocol.ssl.tls1.3-enabled="true"

# Set client authentication
asadmin set server-config.network-config.protocols.protocol.https-protocol.ssl.client-auth="need"
```

## Thread Pool Configuration

### Thread Pool Settings

```xml
<thread-pools>
  <thread-pool name="http-thread-pool"
               max-thread-pool-size="200"
               max-queue-size="4096"
               idle-thread-timeout-seconds="120"/>
</thread-pools>
```

### Via CLI

```bash
# Configure thread pool
asadmin set server-config.thread-pools.thread-pool.http-thread-pool.max-thread-pool-size=400

# Set queue size
asadmin set server-config.network-config.protocols.protocol.http-listener.thread-pool.max-queue-size=8192
```

## File Cache Configuration

### HTTP File Cache

```xml
<http max-connections="250">
  <file-cache enabled="true"
              max-age-seconds="60"
              max-files="1024"
              cache-size="1024"/>
</http>
```

### Via CLI

```bash
# Enable file cache
asadmin set server-config.network-config.protocols.protocol.http-listener.http.file-cache.enabled=true

# Set cache size
asadmin set server-config.network-config.protocols.protocol.http-listener.http.file-cache.cache-size=2048
```

## Port Unification

### Protocol Finder Configuration

Port unification allows multiple protocols (HTTP, HTTPS) on a single port:

```xml
<protocol name="pu-protocol">
  <port-unification>
    <protocol-finder name="http-finder"
                     protocol="http-listener"
                     classname="org.glassfish.grizzly.config.portunif.HttpProtocolFinder"/>
  </port-unification>
</protocol>
```

## HSTS Configuration

### HTTP Strict Transport Security

```java
// HSTSFilter enforces HSTS for HTTPS connections
asadmin set server-config.network-config.protocols.protocol.https-listener.http.hsts-enabled=true
asadmin set server-config.network-config.protocols.protocol.https-listener.http.max-age-seconds=31536000
```

## Dynamic Configuration

### Runtime Configuration Changes

```java
// Dynamic reconfiguration without restart
GrizzlyListener listener = ...;
PropertyChangeEvent[] events = ...;
listener.processDynamicConfigurationChange(habitat, events);
```

### Common Runtime Changes

```bash
# Change listener port (requires restart)
asadmin set server-config.network-config.network-listeners.network-listener.http-listener-1.port=9090

# Enable/disable listener (requires restart)
asadmin set server-config.network-config.network-listeners.network-listener.http-listener-1.enabled=false
```

## Connection Settings

### Connection Limits

```xml
<http max-connections="250"
      request-timeout-seconds="30"
      timeout-seconds="30"/>
```

### Keep-Alive Settings

```xml
<http keep-alive-timeout-seconds="30"
      keep-alive-max-connections="250"
      timeout-seconds="30"/>
```

## Virtual Servers

### Virtual Server Mapping

Network listeners map to virtual servers for request routing:

```xml
<http default-virtual-server="server"/>
```

```bash
# Create virtual server
asadmin create-virtual-server --hosts myhost.com my-virtual-server

# Map listener to virtual server
asadmin set server-config.network-config.protocols.protocol.my-protocol.http.default-virtual-server=my-virtual-server
```

## Troubleshooting

### Port Already in Use

```bash
# Check what's using the port
netstat -an | grep 8080
lsof -i :8080

# Change listener port
asadmin set server-config.network-config.network-listeners.network-listener.http-listener-1.port=8081
```

### Connection Refused

```bash
# Check listener is enabled
asadmin get server-config.network-config.network-listeners.network-listener.http-listener-1.enabled

# Check listener status
asadmin list-network-listeners
```

### SSL Issues

```bash
# Check SSL configuration
asadmin get server-config.network-config.protocols.protocol.sec-admin-listener.ssl.*

# Verify certificate exists
keytool -list -keystore ${com.sun.aas.instanceRoot}/config/keystore.jks

# Enable SSL debug
asadmin create-jvm-options "-Djavax.net.debug=ssl"
```

### Performance Issues

```bash
# Check thread pool settings
asadmin get server-config.thread-pools.thread-pool.*

# Check connection limits
asadmin get server-config.network-config.protocols.protocol.*.http.max-connections

# Enable file cache
asadmin set server-config.network-config.protocols.protocol.*.http.file-cache.enabled=true
```

## Related Modules

- **appserver/web** - Web container (Servlet, JSP, JSF)
- **admin/rest** - REST admin interface
- **admin/config-api** - Configuration management

## Monitoring

```bash
# Enable connection monitoring
asadmin set server-config.monitoring-service.module-monitoring-levels.module-connection-queue=HIGH

# View connection queue stats
asadmin get --monitor server.server-instance.connection-queue.*
```
