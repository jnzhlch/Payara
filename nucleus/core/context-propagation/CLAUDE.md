# CLAUDE.md - Context Propagation

This file provides guidance for working with the `context-propagation` module - thread context propagation for async operations.

## Module Overview

The `context-propagation` module enables thread-local context to be propagated across asynchronous operations, thread pools, and remote calls. This is critical for:

- Distributed tracing
- Security context propagation
- Transaction context
- Application-specific context data

## Build Commands

```bash
# Build context-propagation module
mvn -DskipTests clean package -f nucleus/core/context-propagation/pom.xml

# Build with tests
mvn clean package -f nucleus/core/context-propagation/pom.xml
```

## Architecture

### Core Concepts

```
ThreadLocal Context
        │
        ├─→ ContextMap (key-value store)
        │      ├─→ View (read-only snapshot)
        │      └─→ Location (context source)
        │
        ├─→ WireAdapter (serialization)
        │      ├─→ DefaultWireAdapter (GlassFish)
        │      └─→ WLSWireAdapter (WebLogic compatible)
        │
        └─→ ContextMapPropagator
               ├─→ Capture context
               └─→ Restore context
```

## Context Management

### Creating Context

```java
// Get or create ContextMap
ContextMap context = ContextBootstrap.getContext();

// Store values
context.put("key1", "value1");
context.put("key2", 12345);

// Get values
String value1 = context.get("key1");
Integer value2 = context.get("key2");
```

### Context View

```java
// Create read-only snapshot
View view = context.createView();

// View is immutable
String value = view.get("key");

// Check if contains key
if (view.containsKey("key")) {
    // Process
}

// Check propagation mode
PropagationMode mode = view.getPropagationMode("key");
```

### Propagation Modes

```java
public enum PropagationMode {
    THREADLOCAL,    // Stay in same thread only
    SERIALIZE,      // Serialize and propagate
    DEFAULT         // Use default behavior
}

// Set propagation mode for key
context.setPropagationMode("myKey", PropagationMode.SERIALIZE);
```

## Bootstrap

### ContextBootstrap

```java
// Initialize context propagation
ContextBootstrap bootstrap = ContextBootstrap.getInstance();

// Access context map
ContextMap context = bootstrap.getContext();

// Set default propagation mode
bootstrap.setDefaultPropagationMode(PropagationMode.SERIALIZE);
```

### Context Lifecycle

```java
// Lifecycle methods
ContextLifecycle lifecycle = ...;

// Capture current context
context = lifecycle.captureContext();

// Restore context in new thread
lifecycle.restoreContext(context);

// Clear context
lifecycle.clearContext();
```

## Wire Adapters

### Serialization

```java
// Get wire adapter
WireAdapter adapter = Catalog.getDefaultWireAdapter();

// Serialize context
byte[] data = adapter.writeToWire(context);

// Deserialize context
ContextMap restored = adapter.readFromWire(data);
```

### Custom Wire Adapter

```java
// Extend for custom serialization
public class MyWireAdapter extends AbstractWireAdapter {

    @Override
    public byte[] writeToWire(ContextMap context) throws IOException {
        // Custom serialization
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(context.getEntries());
            return bos.toByteArray();
        }
    }

    @Override
    public ContextMap readFromWire(byte[] data) throws IOException {
        // Custom deserialization
    }
}
```

## Thread Context Propagation

### Propagating to New Thread

```java
// Capture context
ContextMap capturedContext = ContextBootstrap.getContext();

// In new thread
Runnable task = () -> {
    // Restore context
    ContextLifecycle lifecycle = ContextBootstrap.getLifecycle();
    lifecycle.restoreContext(capturedContext);

    // Work with context
    ContextMap context = ContextBootstrap.getContext();
    String value = context.get("key");
};

// Execute with context
new Thread(task).start();
```

### Executor Integration

```java
// Wrap executor with context propagation
ExecutorService executor = Executors.newFixedThreadPool(10);

// Wrap task to propagate context
Runnable wrappedTask = ContextPropagator.wrap(task);

// Execute
executor.execute(wrappedTask);
```

## Context SPI

### ContextMapPropagator

```java
// Service provider interface
ContextMapPropagator propagator = ...;

// Capture context
ContextMap capture() {
    return ContextBootstrap.getContext();
}

// Restore context
void restore(ContextMap context) {
    ContextLifecycle lifecycle = ContextBootstrap.getLifecycle();
    lifecycle.restoreContext(context);
}
```

## Security

### Access Control

```java
// InsufficientCredentialException
// Thrown when context access is denied

// Check access permissions
try {
    context.put("secureKey", value);
} catch (InsufficientCredentialException e) {
    // Handle access denied
}
```

### View Permissions

```java
// Create view with specific permissions
ContextAccessController controller = ...;
View view = controller.createView(context, permissions);
```

## Locations

### Context Source

```java
// Location identifies context source
Location location = Location.getLocation("my-module");

// Set context for location
ContextMap context = ContextBootstrap.getContext(location);

// Propagate to specific location
context.setLocation(Location.getLocation("target-module"));
```

## Best Practices

### 1. Always Propagate Context

```java
// Good - capture and restore
ContextMap captured = ContextBootstrap.getContext();
runInNewThread(() -> {
    ContextLifecycle lifecycle = ContextBootstrap.getLifecycle();
    lifecycle.restoreContext(captured);
    // Work...
});
```

### 2. Use Appropriate Propagation Modes

```java
// Thread-local only (no serialization)
context.setPropagationMode("localData", PropagationMode.THREADLOCAL);

// Serializable data
context.setPropagationMode("userData", PropagationMode.SERIALIZE);
```

### 3. Clean Up Context

```java
try {
    // Work with context
} finally {
    // Clear sensitive data
    context.remove("sensitiveKey");
    // Or clear entire context
    ContextLifecycle lifecycle = ContextBootstrap.getLifecycle();
    lifecycle.clearContext();
}
```

## Related Modules

- **kernel** - Core services using context propagation
- **payara-modules/requesttracing-core** - Request tracing using context propagation
- **grizzly** - HTTP layer integration

## Common Patterns

### 1. Async Context Propagation

```java
// Capture context before async operation
ContextMap context = ContextBootstrap.getContext();

CompletableFuture.supplyAsync(() -> {
    // Restore context in async thread
    ContextBootstrap.getLifecycle().restoreContext(context);
    return doWork();
});
```

### 2. Remote Call Propagation

```java
// Serialize context
WireAdapter adapter = Catalog.getDefaultWireAdapter();
byte[] contextData = adapter.writeToWire(context);

// Send with remote call
remoteService.call(data, contextData);

// Receiver deserializes
ContextMap receivedContext = adapter.readFromWire(contextData);
ContextBootstrap.getLifecycle().restoreContext(receivedContext);
```

### 3. Request Scope Context

```java
// Store request-specific data
ContextMap context = ContextBootstrap.getContext();
context.put("requestId", UUID.randomUUID().toString());
context.put("userId", user.getId());

// Access later in call chain
String requestId = ContextBootstrap.getContext().get("requestId");
```

### 4. Transaction Context

```java
// Propagate transaction context
context.put("transactionId", tx.getId());
context.setPropagationMode("transactionId", PropagationMode.SERIALIZE);
```

## Troubleshooting

### Context Not Propagating

```java
// Ensure context is captured before thread creation
ContextMap captured = ContextBootstrap.getContext();  // Capture here
// Not inside the new thread
```

### Serialization Errors

```java
// Ensure objects are Serializable
public class MyContextData implements Serializable {
    private static final long serialVersionUID = 1L;
    // ...
}

// Or use THREADLOCAL mode
context.setPropagationMode("nonSerializable", PropagationMode.THREADLOCAL);
```

### Memory Leaks

```java
// Always clean up context
try {
    // Work with context
} finally {
    context.clear();
    // Or remove specific keys
    context.remove("largeObject");
}
```
