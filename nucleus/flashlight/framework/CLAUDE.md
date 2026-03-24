# CLAUDE.md - Flashlight Framework

This file provides guidance for working with the `flashlight-framework` module - core Flashlight monitoring infrastructure.

## Module Overview

The `flashlight-framework` module provides the core infrastructure for Flashlight monitoring, including:

- Probe provider registry and management
- Probe client invocation (DTrace, BTrace, reflective)
- Annotation-based probe definition
- XML configuration parsing
- Listener registration and mediation

## Build Commands

```bash
# Build flashlight-framework module
mvn -DskipTests clean package -f nucleus/flashlight/framework/pom.xml

# Build with tests
mvn clean package -f nucleus/flashlight/framework/pom.xml
```

## Architecture

### Core Components

```
ProbeProvider (annotations)
        │
        ├─→ ProbeClientAnnotationHandler
        │      └─→ Processes @ProbeProvider annotations
        │
        ├─→ ProbeProviderRegistry
        │      └─→ Registers probe providers
        │
        ├─→ ProbeClientMediator
        │      ├─→ ProbeClientInvokerFactory
        │      ├─→ DTraceClientInvoker
        │      ├─→ ReflectiveClientInvoker
        │      └─→ ProbeClientMethodHandle
        │
        └─→ ProbeListener
              └─→ Receives probe events
```

## Probe Provider Definition

### Using Annotations

```java
@ProbeProvider(moduleName = "mymodule",
               providerName = "myprovider",
               eventType = "myevent")
public interface MyProbeProvider {

    @Probe(name = "methodCall")
    void methodCall(
        @ProbeParam("className") String className,
        @ProbeParam("methodName") String methodName
    );
}
```

### Using XML Configuration

```xml
<probe-providers>
  <provider name="myprovider" module="mymodule">
    <probe name="methodCall">
      <class>com.example.MyProvider</class>
      <method>methodCall</method>
      <params>
        <param name="className" type="String"/>
        <param name="methodName" type="String"/>
      </params>
    </probe>
  </provider>
</probe-providers>
```

## Probe Client Invokers

### DTrace Client Invoker

```java
// For DTrace-based monitoring (Solaris)
DTraceClientInvoker invoker = new DTraceClientInvoker();
invoker.invoke(probeMethod, args);
```

### Reflective Client Invoker

```java
// For reflective method invocation
ReflectiveClientInvoker invoker = new ReflectiveClientInvoker();
invoker.invoke(probeMethod, args);
```

### Stateful Probe Client Invoker

```java
// For maintaining state across invocations
StatefulProbeClientInvoker invoker = new StatefulProbeClientInvoker();
invoker.invoke(probeMethod, args);
```

## Probe Client Mediator

### Mediating Probes to Listeners

```java
// ProbeClientMediator connects probes to listeners
ProbeClientMediator mediator = new FlashlightProbeClientMediator();

// Register listener
mediator.addListener(myListener);

// Register provider
mediator.registerProvider(probeProviderClass);
```

## Probe Registry

### ProbeProviderRegistry

```java
// Register probe provider
ProbeProviderRegistry registry = habitat.getService(ProbeProviderRegistry.class);
registry.registerProvider(probeProviderClass);

// Get registered providers
Collection<Class<?>> providers = registry.getProbeProviders();
```

## Provider Generation

### Dynamic Provider Generation

```java
// Generate provider subclass at runtime
ProviderSubClassImplGenerator generator = new ProviderSubClassImplGenerator();
Class<?> generatedClass = generator.generate(providerClass);
```

### Provider Implementation Generator

```java
// Generate provider implementation
ProviderImplGenerator generator = new ProviderImplGenerator();
String implCode = generator.generate(providerClass);
```

## Annotation Processing

### ProbeClientAnnotationHandler

```java
// Process @Probe annotations
ProbeClientAnnotationHandler handler = new ProbeClientAnnotationHandler();

// Handle probe provider class
handler.process(probeProviderClass);
```

### Annotation Handler Manager

```java
// Manage annotation handlers
ProbeClientAnnotationHandlerManager manager = ...;

// Register handler
manager.registerHandler(handler);
```

## XML Parsing

### ProbeProviderStaxParser

```java
// Parse probe provider XML
ProbeProviderStaxParser parser = new ProbeProviderStaxParser();

// Parse XML file
List<Provider> providers = parser.parse(xmlFile);
```

### XML Structure

```xml
<probe-providers>
  <provider name="myprovider" module="mymodule" class="com.example.MyProvider">
    <probes>
      <probe name="myprobe" method="myMethod">
        <params>
          <param name="param1" type="String"/>
          <param name="param2" type="int"/>
        </params>
      </probe>
    </probes>
  </provider>
</probe-providers>
```

## Computed Parameters

### ComputedParamHandler

```java
// Handle computed probe parameters
ComputedParamHandler handler = new ComputedParamHandler();

// Compute parameter value
Object value = handler.computeValue(paramName, context);
```

### ComputedParamsHandlerManager

```java
// Manage computed parameter handlers
ComputedParamsHandlerManager manager = ...;

// Register handler
manager.registerHandler(paramName, handler);
```

## Agent Attachment

### Agent Attacher

```java
// Attach Flashlight agent programmatically
AgentAttacher attacher = new AgentAttacher();
attacher.attach(pid);
```

## Admin Commands

```bash
# Enable monitoring
asadmin enable-monitoring --level LOW|MEDIUM|HIGH

# Disable monitoring
asadmin disable-monitoring

# View monitoring configuration
asadmin monitoring-config
```

## CLI Commands

| Command | Purpose |
|---------|---------|
| `EnableMonitoring` | Enable Flashlight monitoring |
| `DisableMonitoring` | Disable Flashlight monitoring |
| `MonitoringConfig` | Display monitoring configuration |

## Package Structure

| Package | Purpose |
|---------|---------|
| `org.glassfish.flashlight.provider` | Probe provider interfaces |
| `org.glassfish.flashlight.xml` | XML configuration |
| `org.glassfish.flashlight.client` | Client interfaces |
| `org.glassfish.flashlight.impl.core` | Core implementation |
| `org.glassfish.flashlight.impl.client` | Client implementations |
| `org.glassfish.flashlight.cli` | Admin commands |

## Dependencies

**Runtime:**
- `internal-api` - Internal APIs
- HK2 core
- OSGi core (provided)

## Related Modules

- **flashlight/agent** - Java agent for instrumentation
- **admin/monitor** - Monitoring infrastructure

## Common Patterns

### 1. Define Probe Provider

```java
@ProbeProvider(moduleName = "myapp",
               providerName = "operations",
               eventType = "operation")
public interface OperationProbeProvider {

    @Probe(name = "operationStart")
    void operationStart(@ProbeParam("name") String name);

    @Probe(name = "operationEnd")
    void operationEnd(
        @ProbeParam("name") String name,
        @ProbeParam("duration") long duration
    );
}
```

### 2. Implement and Register

```java
@Service
public class OperationProbeProviderImpl implements OperationProbeProvider {

    @Override
    public void operationStart(String name) {
        // Emit probe event
    }

    @Override
    public void operationEnd(String name, long duration) {
        // Emit probe event
    }
}
```

### 3. Create Probe Listener

```java
public class MyProbeListener implements ProbeListener {

    @Override
    public void probeCallback(Probe probe, Object[] args) {
        String name = (String) args[0];
        System.out.println("Operation: " + name);
    }
}

// Register listener
ProbeRegistry registry = habitat.getService(ProbeRegistry.class);
registry.registerListener(MyProbeListener.class);
```

### 4. Use Probe Registry

```java
// Get probe registry
ProbeRegistry registry = habitat.getService(ProbeRegistry.class);

// Register listener
registry.registerListener(MyListener.class, myProviderClass);

// Get probe handles
Collection<ProbeHandle> handles = registry.getProbeHandles(myProviderClass);
```

## Monitoring Levels

| Level | Description |
|-------|-------------|
| LOW | Minimal overhead |
| MEDIUM | Moderate monitoring |
| HIGH | Detailed monitoring (higher overhead) |

## Best Practices

1. **Use specific module names** - Avoid naming conflicts
2. **Keep probe methods lightweight** - They're called frequently
3. **Use appropriate data types** - Avoid expensive serialization
4. **Test with monitoring disabled** - Ensure no monitoring dependency
