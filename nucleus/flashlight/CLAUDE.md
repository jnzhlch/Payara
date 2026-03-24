# CLAUDE.md - Flashlight

This file provides guidance for working with the `nucleus/flashlight` module - monitoring and probe infrastructure.

## Module Overview

Flashlight is Payara's monitoring framework that provides:
- **Probe infrastructure** - For instrumenting code for monitoring
- **DTrace integration** - Solaris/DTrace dynamic tracing support
- **BTrace integration** - Bytecode instrumentation for tracing
- **Probe providers** - Annotations for defining monitoring points

**Note:** Flashlight is an older monitoring framework. Newer Payara features use MicroProfile APIs instead.

## Sub-Modules

- **framework** - Core Flashlight framework and probe infrastructure
- **agent** - Java agent for bytecode instrumentation
- **flashlight-extra-jdk-packages** - OSGi fragment exporting JDK packages

## Build Commands

```bash
# Build all flashlight modules
mvn -DskipTests clean package -f nucleus/flashlight/pom.xml

# Build specific module
mvn -DskipTests clean package -f nucleus/flashlight/framework/pom.xml
mvn -DskipTests clean package -f nucleus/flashlight/agent/pom.xml
```

## Architecture

### Probe Flow

```
Application Code (with @Probe annotations)
        │
        ├─→ ProbeProvider (defines monitoring points)
        │      └─→ @Probe methods emit monitoring data
        │
        ├─→ ProbeProviderRegistry (registered providers)
        │
        ├─→ ProbeClientMediator (connects providers to listeners)
        │      ├─→ ProbeClientInvoker (calls listener methods)
        │      └─→ ProbeClientMethodHandle (listener method handle)
        │
        └─→ ProbeListener (receives monitoring data)
```

## Probe Annotations

### @ProbeProvider

```java
@ProbeProvider(moduleName = "myapp",
               providerName = "myprovider",
               eventType = "myevent")
public interface MyProbeProvider {

    @Probe(name = "methodEntry")
    void methodEntryEvent(
        @ProbeParam("methodName") String methodName,
        @ProbeParam("duration") long duration
    );
}
```

### Probe Parameters

| Element | Description |
|---------|-------------|
| `moduleName` | Logical module name |
| `providerName` | Provider name (unique within module) |
| `eventType` | Event type name |
| `@Probe` | Marks method as a probe |

## Client API

### Probe Listener

```java
// Implement probe listener
public class MyListener implements ProbeListener {

    @Override
    public void probeCallback(Probe probe, Object[] args) {
        // Handle probe event
        String methodName = (String) args[0];
        long duration = (Long) args[1];
        System.out.println("Method: " + methodName + ", Duration: " + duration);
    }
}
```

### Registering Listeners

```java
// Register listener programmatically
ProbeRegistry registry = ...;
registry.registerListener(MyListener.class);
```

## DTrace Integration

### DTrace Probe Methods

Flashlight can generate DTrace probe methods:

```java
@ProbeProvider(moduleName = "myapp",
               providerName = "dtrace-provider",
               eventType = "dtrace-event")
public interface DTraceProbeProvider {

    @Probe(name = "dtrace-method")
    void dtraceMethod(
        @ProbeParam("arg0") String arg0
    );
}
```

### DTrace Method Finder

```java
// Find DTrace methods
DTraceMethodFinder finder = new DTraceMethodFinder();
List<Method> methods = finder.findDTraceMethods(providerClass);
```

## BTrace Integration

### BTrace Client Generator

```java
// Generate BTrace client code
BtraceClientGenerator generator = new BtraceClientGenerator();
String clientCode = generator.generate(probeProviderClass);
```

### Reflective Client Invoker

```java
// Invoke probe methods via reflection
ReflectiveClientInvoker invoker = new ReflectiveClientInvoker();
invoker.invoke(probeMethod, args);
```

## Admin Commands

```bash
# Enable monitoring
asadmin enable-monitoring --level LOW

# Disable monitoring
asadmin disable-monitoring

# Monitoring configuration
asadmin monitoring-config
```

## XML Configuration

### Probe Provider XML

```xml
<probe-providers>
  <provider name="myprovider" module="mymodule">
    <probe name="myprobe" class="com.example.MyProbeProvider">
      <params>
        <param name="methodName" type="String"/>
        <param name="duration" type="long"/>
      </params>
    </probe>
  </provider>
</probe-providers>
```

## Key Components

### Framework Package Structure

| Package | Purpose |
|---------|---------|
| `org.glassfish.flashlight.provider` | Probe provider infrastructure |
| `org.glassfish.flashlight.client` | Client-side probe invocation |
| `org.glassfish.flashlight.impl.core` | Core implementation |
| `org.glassfish.flashlight.impl.client` | Client implementations |
| `org.glassfish.flashlight.xml` | XML configuration parsing |

## Related Modules

- **admin/monitor** - Monitoring infrastructure
- **payara-modules/monitoring** - Payara-specific monitoring
- **appserver/monitoring** - Application server monitoring

## Notes

- Flashlight is primarily used for DTrace on Solaris
- On other platforms, BTrace or reflective invocation is used
- For new monitoring code, prefer MicroProfile APIs

## Common Patterns

### 1. Create Probe Provider

```java
@ProbeProvider(moduleName = "myapp",
               providerName = "operations",
               eventType = "operation")
public interface OperationProbeProvider {

    @Probe(name = "operationStart")
    void operationStart(
        @ProbeParam("operationName") String name
    );

    @Probe(name = "operationEnd")
    void operationEnd(
        @ProbeParam("operationName") String name,
        @ProbeParam("duration") long duration
    );
}
```

### 2. Emit Probe Data

```java
// In application code
OperationProbeProvider probe = ...;
probe.operationStart("saveData");

// ... do work ...

probe.operationEnd("saveData", duration);
```

### 3. Register Probe Provider

```java
// Probe provider is auto-registered via HK2
@Service
public class MyProbeProviderImpl implements OperationProbeProvider {
    // Implementation
}
```
