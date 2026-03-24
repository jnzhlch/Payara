# CLAUDE.md - REST Client

This file provides guidance for working with the REST Client module - programmatic client for REST administration API.

## Module Overview

The REST Client module provides a Java client library for programmatic access to the Payara REST administration API. It handles authentication, request/response marshalling, and provides a fluent API for admin operations.

## Key Components

### Client Classes

**Main client API:**
- `RestClient.java` - Main REST client
- `RestClientBase.java` - Base client implementation
- `RestLeaf.java` - Leaf resource (single entity)
- `RestLeafCollection.java` - Resource collection
- `RestResponse.java` - Response wrapper

### Utilities

**Client utilities:**
- `MarshallingUtils.java` - JSON/XML marshalling
- `RestClientLogging.java` - Request/response logging
- `Util.java` - Common utilities

## Build Commands

```bash
# Build REST client module
mvn -DskipTests clean package -f nucleus/admin/rest/rest-client/pom.xml
```

## Client Usage Pattern

### Basic Usage

```java
import org.glassfish.admin.rest.client.RestClient;

// Create client
RestClient client = RestClient.create(
    "http://localhost:4848/management",
    "admin", "");

// Execute command
Map<String, Object> result =
    (Map<String, Object>) client.executeCommand(
        "version", null);

// Get response
String exitCode = (String) result.get("exit_code");
String message = (String) result.get("message");
```

### With Authentication

```java
// With username and password
RestClient client = RestClient.create(
    "http://localhost:4848/management",
    "admin", "password");

// Or with session token
RestClient client = RestClient.create(
    "http://localhost:4848/management",
    sessionToken);
```

## Resource Access

### Domain Resource

```java
// Get domain
RestLeaf domain = client.getDomain();

// Get configuration
RestLeaf config = domain.child("configs")
    .child("server-config");

// Get config value
String adminPort = config.get("admin-port");

// Set config value
config.set("admin-port", "4848");

// Save changes
config.save();
```

### Collection Access

```java
// Get servers collection
RestLeafCollection servers =
    client.getDomain().child("servers");

// List servers
List<String> serverNames = servers.getChildrenNames();

// Get specific server
RestLeaf server = servers.child("server");

// Get server properties
Map<String, String> props = server.getAttributes();
```

## Command Execution

### Simple Command

```java
// List applications
Map<String, Object> result =
    client.executeCommand("list-applications", null);
```

### Command With Parameters

```java
// Create JDBC pool
Map<String, Object> params = new HashMap<>();
params.put("datasourceclassname",
    "org.h2.jdbcx.JdbcDataSource");
params.put("property",
    Map.of("url", "jdbc:h2:mem:test"));

Map<String, Object> result = client.executeCommand(
    "create-jdbc-connection-pool",
    "my-pool",
    params);
```

### Using ParameterMap

```java
import org.glassfish.api.admin.ParameterMap;

ParameterMap params = new ParameterMap();
params.add("datasourceclassname",
    "org.h2.jdbcx.JdbcDataSource");
params.add("property", "url=jdbc:h2:mem:test");

client.executeCommand(
    "create-jdbc-connection-pool",
    "my-pool",
    params);
```

## Response Handling

### RestResponse

```java
RestResponse response = client.get("/management/domain");

// Check status
int status = response.getStatus();

// Get body
String body = response.getBody();

// Parse as JSON
Map<String, Object> json = MarshallingUtils
    .unmarshal(body, Map.class);

// Parse as XML
MyConfig config = MarshallingUtils.unmarshalXml(
    body, MyConfig.class);
```

### Success/Failure

```java
Map<String, Object> result = client.executeCommand(...);

String exitCode = (String) result.get("exit_code");

if ("SUCCESS".equals(exitCode)) {
    // Handle success
} else {
    // Handle failure
    String message = (String) result.get("message");
}
```

## Marshalling

### JSON Marshalling

```java
import org.glassfish.admin.rest.client.utils.MarshallingUtils;

// Object to JSON
MyObject obj = new MyObject();
String json = MarshallingUtils.marshal(obj);

// JSON to object
MyObject parsed = MarshallingUtils.unmarshal(
    json, MyObject.class);
```

### XML Marshalling

```java
// Object to XML
String xml = MarshallingUtils.marshalXml(obj);

// XML to object
MyObject parsed = MarshallingUtils.unmarshalXml(
    xml, MyObject.class);
```

## Logging

### Enable Request/Response Logging

```java
import org.glassfish.admin.rest.client.utils.RestClientLogging;

// Enable logging
RestClientLogging.setEnabled(true);

// Set log level
RestClientLogging.setLogLevel(Level.FINE);

// Custom logger
RestClientLogging.setLogger(myLogger);
```

## Configuration

### Client Configuration

```java
// Create client with custom configuration
RestClient client = RestClient.create(
    "http://localhost:4848/management",
    "admin",
    "",
    RestClient.ClientConfig.builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .enableLogging(true)
        .build());
```

## Advanced Usage

### Fluent API

```java
// Fluent resource navigation
String port = client.getDomain()
    .child("configs")
    .child("server-config")
    .child("http-service")
    .child("http-listener-1")
    .get("port");
```

### Batch Operations

```java
// Get multiple properties
RestLeaf config = client.getDomain()
    .child("configs")
    .child("server-config");

Map<String, String> values = config.get(
    "admin-port",
    "instance-port",
    "jms-port");
```

## Error Handling

### Exception Types

| Exception | Cause |
|-----------|-------|
| `ClientErrorException` | 4xx errors |
| `ServerErrorException` | 5xx errors |
| `ProcessingException` | Request processing failure |

### Try-Catch Pattern

```java
try {
    Map<String, Object> result =
        client.executeCommand(...);
} catch (ClientErrorException e) {
    // Client error (4xx)
    int status = e.getResponse().getStatus();
    String message = e.getResponse().readEntity(String.class);
} catch (ServerErrorException e) {
    // Server error (5xx)
}
```

## Key Classes Reference

| Class | Purpose |
|-------|---------|
| `RestClient` | Main client entry point |
| `RestLeaf` | Single resource reference |
| `RestLeafCollection` | Resource collection |
| `RestResponse` | HTTP response wrapper |
| `MarshallingUtils` | JSON/XML conversion |
| `RestClientLogging` | Request/response logging |

## Architecture Notes

1. **Fluent API** - Chainable method calls
2. **Type-safe** - Generic types for responses
3. **Auto-auth** - Handles authentication automatically
4. **Session-aware** - Maintains session cookies
5. **Multi-format** - Supports JSON and XML

## Thread Safety

`RestClient` is thread-safe and can be reused across threads:

```java
// Create once
RestClient client = RestClient.create(...);

// Use from multiple threads
ExecutorService executor = Executors.newFixedThreadPool(10);
for (int i = 0; i < 10; i++) {
    executor.submit(() -> {
        client.executeCommand("list-applications", null);
    });
}
```

## Best Practices

1. **Reuse client** - Create once, reuse for multiple requests
2. **Handle errors** - Always check response status
3. **Use timeouts** - Set reasonable connect/read timeouts
4. **Enable logging** - For debugging, enable request logging
5. **Close resources** - Use try-with-resources when appropriate
