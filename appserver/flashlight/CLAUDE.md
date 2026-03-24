# CLAUDE.md - Appserver Flashlight

This file provides guidance for working with the `appserver/flashlight` module - Flashlight monitoring framework integration for application server modules.

## Module Overview

The flashlight module integrates the Flashlight monitoring framework into Payara's application server layer. It bundles BTrace (bytecode instrumentation) and provides client-side probe infrastructure for monitoring EJB and other application server components.

## Build Commands

```bash
# Build entire flashlight module
mvn -DskipTests clean package -f appserver/flashlight/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/flashlight/<submodule>/pom.xml
```

## Module Structure

| Submodule | Purpose |
|-----------|---------|
| `btrace` | BTrace JAR distribution fragment |
| `client` | Flashlight client for appserver |

## Flashlight Integration

### BTrace Distribution Fragment

The `btrace` submodule packages BTrace JARs as a distribution fragment:

```xml
<packaging>distribution-fragment</packaging>
```

**Installed Location:**
```
$PAYARA_HOME/glassfish/lib/monitor/
├── btrace-agent.jar
└── btrace-boot.jar
```

### BTrace Components

| JAR | Purpose |
|-----|---------|
| `btrace-agent.jar` | Java agent for bytecode instrumentation |
| `btrace-boot.jar` | Boot classpath instrumentation |

## Flashlight Client

### Probe Client Usage

```java
package org.glassfish.flashlight.client;

import org.glassfish.external.probe.provider.annotations.ProbeListener;
import org.glassfish.external.probe.provider.annotations.ProbeParam;

public class ProbeClient {

    @ProbeListener("glassfish:ejb:ejb-container:onDeploy")
    public void onEjbDeploy(@ProbeParam("appName") String app,
                           @ProbeParam("beanName") String bean) {
        System.out.println("EJB deployed: " + app + "/" + bean);
    }

    @ProbeListener("glassfish:ejb:ejb-container:onCreate")
    public void onEjbCreate(@ProbeParam("appName") String app,
                           @ProbeParam("beanName") String bean) {
        System.out.println("EJB created: " + app + "/" + bean);
    }

    @ProbeListener("glassfish:ejb:ejb-container:onShutdown")
    public void onEjbShutdown(@ProbeParam("appName") String app,
                              @ProbeParam("beanName") String bean) {
        System.out.println("EJB shutdown: " + app + "/" + bean);
    }

    @ProbeListener("glassfish:ejb:ejb-container:onUndeploy")
    public void onEjbUndeploy(@ProbeParam("appName") String app,
                             @ProbeParam("beanName") String bean) {
        System.out.println("EJB undeployed: " + app + "/" + bean);
    }
}
```

## Probe Provider Pattern

### Probe Points

```
Application Server Event
       │
   [Component Action]
       │
┌──────▼──────────────┐
│  ProbeProvider      │  - Defines monitoring points
│  ┌────────────────┐  │
│  │ @Probe methods │──┼──► Emit monitoring data
│  └────────────────┘  │
└──────────────────────┘
       │
   [ProbeClientMediator]
       │
┌──────▼──────────────┐
│  ProbeListener      │  - Receives probe callbacks
│                     │  - Processes monitoring data
└──────────────────────┘
```

### Provider Naming Convention

```
<module>:<submodule>:<component>:<event>

Examples:
glassfish:ejb:ejb-container:onDeploy
glassfish:web:servlet-container:onRequest
glassfish:web:servlet-container:onResponse
glassfish:jdbc:connection-pool:onAcquire
```

## EJB Monitoring Points

| Probe Point | Description | Parameters |
|------------|-------------|------------|
| `onDeploy` | EJB deployed | appName, beanName |
| `onCreate` | EJB instance created | appName, beanName |
| `onShutdown` | EJB shutdown | appName, beanName |
| `onUndeploy` | EJB undeployed | appName, beanName |
| `onInvocationStart` | Method invocation starts | appName, beanName, method |
| `onInvocationEnd` | Method invocation ends | appName, beanName, method, duration |

## Module Dependencies

### Client Dependencies

| Dependency | Purpose |
|------------|---------|
| `flashlight-framework` | Core Flashlight framework |
| `internal-api` | Internal server APIs |
| `hk2-core` | Dependency injection |

### BTrace Dependencies

| Dependency | Purpose |
|------------|---------|
| `btrace-agent` | BTrace Java agent |
| `btrace-boot` | BTrace boot instrumentation |

## Monitoring Configuration

### Enable Monitoring

```bash
# Enable monitoring
asadmin set monitoring-enabled=true

# Set monitoring level
asadmin set monitoring-level=LOW|MEDIUM|HIGH

# Configure monitoring
asadmin configure-monitoring --targets ejb,web,jdbc
```

### Monitoring Levels

| Level | Overhead | Data Collected |
|-------|----------|-----------------|
| **OFF** | None | No monitoring |
| **LOW** | ~1-2% | Basic probe points |
| **MEDIUM** | ~3-5% | Additional probe points |
| **HIGH** | ~10-15% | All probe points with detailed info |

## Package Structure

```
appserver/flashlight/
├── btrace/                           # BTrace distribution
│   └── pom.xml                      # Distribution fragment config
└── client/                          # Flashlight client
    └── src/main/java/
        └── org/glassfish/flashlight/client/
            └── ProbeClient.java     # Example probe client
```

## Installed Artifacts

After building and installing:

```
$PAYARA_HOME/
├── glassfish/
│   ├── lib/
│   │   └── monitor/
│   │       ├── btrace-agent.jar
│   │       └── btrace-boot.jar
│   └── modules/
│       └── flashlight-client.jar
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `flashlight-framework` | Core Flashlight framework (from nucleus/flashlight) |
| `internal-api` | Internal server APIs |
| `hk2-core` | HK2 dependency injection |

## Related Modules

- `nucleus/flashlight` - Core Flashlight framework
- `admin/monitor` | Monitoring admin commands
- `payara-modules/monitoring` | Payara-specific monitoring

## Usage Example

### Creating Custom Probe Listener

```java
package com.example.monitoring;

import org.glassfish.external.probe.provider.annotations.ProbeListener;
import org.glassfish.external.probe.provider.annotations.ProbeParam;

@Service
public class MyProbeListener {

    @ProbeListener("glassfish:ejb:ejb-container:onInvocationEnd")
    public void logMethodEnd(@ProbeParam("appName") String app,
                             @ProbeParam("beanName") String bean,
                             @ProbeParam("method") String method,
                             @ProbeParam("duration") long duration) {
        System.out.printf("Method %s.%s.%s took %dms%n",
            app, bean, method, duration);
    }

    @ProbeListener("glassfish:ejb:ejb-container:onDeploy")
    public void logDeploy(@ProbeParam("appName") String app,
                        @ProbeParam("beanName") String bean) {
        // Log deployment event
        logger.info("EJB deployed: " + app + "/" + bean);
    }
}
```

## Probe Development

### 1. Define Probe Provider Interface

```java
@ProbeProvider(moduleName = "myapp",
               providerName = "mymonitor",
               eventType = "custom-event")
public interface CustomProbeProvider {

    @Probe(name = "customAction")
    void onCustomAction(
        @ProbeParam("component") String component,
        @ProbeParam("action") String action
    );
}
```

### 2. Implement Probe Listener

```java
public class CustomProbeListener {

    @ProbeListener("myapp:mymonitor:custom-event")
    public void handleCustomAction(
        @ProbeParam("component") String component,
        @ProbeParam("action") String action
    ) {
        // Handle probe event
    }
}
```

### 3. Register Provider and Listener

```java
// Register via HK2
@Service
public class CustomProbeProviderImpl implements CustomProbeProvider {
    // Emit probe events
}
```

## BTrace Usage

### Dynamic Instrumentation

```bash
# Attach BTrace agent to running server
java -jar btrace-agent.jar <pid> <script.btrace>

# Example BTrace script
import org.glassfish.flashlight.provider.*;

@ProbeProviderClassName("glassfish:ejb:ejb-container")
public class EjbTracer {
    @Probe("onDeploy")
    public static void onDeploy(
        @ProbeParameter("appName") String app,
        @ProbeParameter("beanName") String bean
    ) {
        println("Deployed: " + app + "/" + bean);
    }
}
```

## Notes

- Flashlight is primarily used for internal Payara monitoring
- For application-level monitoring, consider MicroProfile Metrics API
- BTrace adds overhead when attached - use in development/debugging only
- Probe listeners should be lightweight to avoid impacting performance
