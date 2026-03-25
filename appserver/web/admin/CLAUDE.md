# CLAUDE.md - Web Admin CLI

This file provides guidance for working with the `appserver/web/admin` module - Administration CLI commands for the web container.

## Module Overview

The admin module provides asadmin CLI commands for configuring and managing the web container, including network listeners, protocols, transports, and virtual servers. It also provides monitoring and statistics providers for web components.

**Key Components:**
- **CLI Commands** - Create/delete/list HTTP, network listeners, protocols, transports
- **Monitoring** - Statistics providers for servlets, JSP, sessions, requests
- **Probe Providers** - Flashlight probe providers for monitoring

## Build Commands

```bash
# Build admin module
mvn -DskipTests clean package -f appserver/web/admin/pom.xml
```

## CLI Commands

### Network Listener Commands

```bash
# Create network listener
asadmin create-network-listener --listenerport 8080 --listenerport 8181 \
    --protocol http-listener-1 --default-virtual-server server

# List network listeners
asadmin list-network-listeners

# Delete network listener
asadmin delete-network-listener http-listener-1
```

### Protocol Commands

```bash
# Create protocol
asadmin create-protocol --securityenabled false http-1

# List protocols
asadmin list-protocols

# Delete protocol
asadmin delete-protocol http-1
```

### HTTP Commands

```bash
# Create HTTP
asadmin create-http --protocol http-1

# Create HTTP redirect
asadmin create-http-redirect --protocol http-1 \
    --redirect-port 8443 --secure true

# Delete HTTP
asadmin delete-http --protocol http-1
```

### Transport Commands

```bash
# Create transport
asadmin create-transport --acceptorthreads 1 tcp

# List transports
asadmin list-transports

# Delete transport
asadmin delete-transport tcp
```

### Virtual Server Commands

```bash
# Create virtual server
asadmin create-virtual-server --hosts www.example.com server

# List virtual servers
asadmin list-virtual-servers

# Delete virtual server
asadmin delete-virtual-server server
```

### Protocol Filter/Finder Commands

```bash
# Create protocol filter
asadmin create-protocol-filter --protocol http-1 \
    --classname com.example.MyFilter

# Create protocol finder
asadmin create-protocol-finder --protocol http-1 \
    --classname com.example.MyFinder

# List protocol filters
asadmin list-protocol-filters

# Delete protocol filter
asadmin delete-protocol-filter --protocol http-1 \
    --classname com.example.MyFilter
```

### Payara-Specific Commands

```bash
# Restart HTTP listeners (without restarting server)
asadmin restart-http-listeners

# Set network listener configuration
asadmin set-network-listener-configuration --listenerport 8080 \
    --minthreads 5 --maxthreads 200
```

## Module Contents

### CLI Commands

```
org/glassfish/web/admin/cli/
├── CreateHttp.java
├── CreateHttpListener.java
├── CreateHttpRedirect.java
├── CreateNetworkListener.java
├── CreateProtocol.java
├── CreateProtocolFilter.java
├── CreateProtocolFinder.java
├── CreateTransport.java
├── CreateVirtualServer.java
├── DeleteHttp.java
├── DeleteHttpListener.java
├── DeleteHttpRedirect.java
├── DeleteNetworkListener.java
├── DeleteProtocol.java
├── DeleteProtocolFilter.java
├── DeleteProtocolFinder.java
├── DeleteTransport.java
├── DeleteVirtualServer.java
├── GetHttpListener.java
├── GetProtocol.java
├── ListHttpListeners.java
├── ListNetworkListeners.java
├── ListProtocolFilters.java
├── ListProtocolFinders.java
├── ListProtocols.java
├── ListTransports.java
└── ListVirtualServers.java
```

### Monitoring Providers

```
org/glassfish/web/admin/monitor/
├── HttpServiceStatsProvider.java
├── HttpServiceStatsProviderBootstrap.java
├── RequestProbeProvider.java
├── RequestStatsProvider.java
├── ServletProbeProvider.java
├── ServletStatsProvider.java
├── ServletInstanceStatsProvider.java
├── JspProbeProvider.java
├── JspStatsProvider.java
├── SessionProbeProvider.java
├── SessionStatsProvider.java
├── WebModuleProbeProvider.java
├── WebStatsProviderBootstrap.java
├── VirtualServerInfoStatsProvider.java
└── statistics/
    ├── AltServletStatsImpl.java
    ├── HTTPListenerStatsImpl.java
    ├── WebModuleVirtualServerStatsImpl.java
    └── TimeStatData.java
```

## Monitoring Statistics

### HTTP Service Statistics

```java
@Contract
@Provided(HttpServiceStatsProviderBootstrap.class)
public interface HttpServiceStatsProvider {
    int getOpenConnections();
    long getMaxOpenConnections();
    long getTotalConnections();
}
```

### Request Statistics

```java
@Contract
@Provided(RequestStatsProvider.class)
public interface RequestStatsProvider {
    long getCountRequests();
    long getCountErrors();
    long getCount1xx();
    long getCount2xx();
    long getCount3xx();
    long getCount4xx();
    long getCount5xx();
    long getMaxTimeMillis();
    long getProcessingTimeMillis();
}
```

### Servlet Statistics

```java
@Contract
@Provided(ServletStatsProvider.class)
public interface ServletStatsProvider {
    long getServletProcessingTimeMillis();
    long getErrorCount();
    long getMaxTimeMillis();
    long getProcessingTimeMillis();
    long getServiceCount();
    int getActiveServletsLoaded();
    int getActiveServletsJspCount();
    int getActiveServletsTotal();
}
```

### Session Statistics

```java
@Contract
@Provided(SessionStatsProvider.class)
public interface SessionStatsProvider {
    int getActiveSessions();
    int getActiveSessionsCurrent();
    int getActiveSessionsHighWaterMark();
    int getRejectedSessions();
    int getExpiredSessions();
    long getSessionMaxAliveTimeSeconds();
    long getSessionAverageAliveTimeSeconds();
}
```

## Configuration Architecture

```
domain.xml
    │
    ▼
<configs>
    <config name="server-config">
        │
        ├─→ <network-config>
        │     │
        │     ├─→ <protocols>
        │     │     ├─→ <protocol name="http-listener-1">
        │     │     │     ├─→ <http>
        │     │     │     ├─→ <http-redirect>
        │     │     │     ├─→ <protocol-filter>
        │     │     │     └─→ <protocol-finder>
        │     │     └─→ <protocol name="sec-admin-listener">
        │     │
        │     ├─→ <network-listeners>
        │     │     ├─→ <network-listener name="http-listener-1">
        │     │     │       protocol="http-listener-1"
        │     │     │       port="8080"
        │     │     │       transport="tcp"
        │     │     └─→ <network-listener name="admin-listener">
        │     │
        │     └─→ <transports>
        │           ├─→ <transport name="tcp">
        │           └─→ <transport name="tcp-obsolete">
        │
        └─→ <servers>
              └─→ <server name="server">
                    │
                    └─→ <application-ref> (virtual servers)
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `monitoring-core` | Monitoring framework |
| `flashlight-framework` | Flashlight probe framework |
| `grizzly-config` | Grizzly configuration |
| `config-api` | Configuration API |
| `glassfish-api` | GlassFish APIs |
| `container-common` | Common container interfaces |

## Notes

- **asadmin Commands** - CLI for web container configuration
- **Monitoring** - Statistics for servlets, JSP, sessions, requests
- **Probe Providers** - Flashlight integration for call path monitoring
- **Payara Extension** - restart-http-listeners command for dynamic restart
