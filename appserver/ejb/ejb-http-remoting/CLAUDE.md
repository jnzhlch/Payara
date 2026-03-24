# CLAUDE.md - EJB HTTP Remoting

This file provides guidance for working with the `ejb-http-remoting` module - HTTP-based EJB remoting implementation.

## Module Overview

The ejb-http-remoting module provides a lightweight HTTP-based remoting protocol for EJBs, allowing remote EJB invocation without IIOP/CORBA. It's Payara-specific and enables EJB calls over HTTP/HTTPS.

## Build Commands

```bash
# Build entire ejb-http-remoting module
mvn -DskipTests clean package -f appserver/ejb/ejb-http-remoting/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/ejb/ejb-http-remoting/<submodule>/pom.xml
```

## Module Structure

| Submodule | Purpose |
|-----------|---------|
| `admin` | Admin commands for HTTP remoting |
| `endpoint` | HTTP endpoint (WAR) |
| `client` | Client-side proxy factory |

## HTTP Remoting Architecture

```
Client Application
       │
   [EJB Lookup]
       │
   [Create HTTP Proxy]
       │
   HTTP POST /ejb-invoker
       │
┌──────▼───────────────────────────────┐
│      HTTP Endpoint (WAR)              │
│                                      │
│  1. Receive HTTP Request             │
│  2. Deserialize Request              │
│  3. Lookup EJB Container             │
│  4. Invoke EJB Method                │
│  5. Serialize Response               │
│  6. Return HTTP Response             │
└──────────────────────────────────────┘
```

## HTTP Endpoint

### Endpoint Location

The endpoint WAR is deployed at:
```
$PAYARA_HOME/glassfish/domains/domain1/endpoints/__ejb-invoker
```

### Request Format

```json
POST /ejb-invoker/ejb-invoker
Content-Type: application/json

{
  "appName": "myapp",
  "moduleName": "myejb",
  "beanName": "MyService",
  "interfaceView": "com.example.MyService",
  "methodName": "doWork",
  "parameters": ["param1", 123],
  "parameterTypes": ["java.lang.String", "int"]
}
```

### Response Format

```json
{
  "status": "SUCCESS",
  "result": "work completed",
  "exception": null
}
```

## Client-Side Proxy

### Proxy Creation

```java
// Create proxy using HTTP remoting
String url = "http://localhost:8080/ejb-invoker";

Properties props = new Properties();
props.put("ejb-url", url);
props.put("ejb.username", "admin");
props.put("ejb.password", "");

MyService service = (MyService) new InitialContext(props).lookup(
    "ejb:/myapp/myejb/com.example.MyService"
);

// Invoke method
String result = service.doWork("param1", 123);
```

### Client Configuration

```xml
<payara-ejb-http-remoting>
    <enabled>true</enabled>
    <client-mapping>
        <context-root>/ejb-invoker</context-root>
    </client-mapping>
</payara-ejb-http-remoting>
```

## Admin Commands

```bash
# Enable HTTP remoting
asadmin set ejb-http-remoting-config.enabled=true

# Set context root
asadmin set ejb-http-remoting-config.client-mapping.context-root=/ejb-invoker

# List remoting config
asadmin get ejb-http-remoting-config.*
```

## Benefits

| Feature | HTTP Remoting | IIOP |
|---------|---------------|------|
| **Firewall friendly** | Yes (port 80/443) | No |
| **Load balancer** | Easy | Complex |
| **Client** | Simple HTTP | ORB required |
| **Standard** | Payara-specific | CORBA standard |

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.servlet-api` | Servlet API |
| `jakarta.ws.rs-api` | JAX-RS |
| `jakarta.json-api` | JSON-P |
| `security-ee` | Authentication |
| `ejb-http-admin` | Admin config |

## Related Modules

- `ejb-container` - Core EJB container
- `web` - HTTP endpoint deployment

## Protocol Details

### Wire Protocol

1. **Serialization** - JSON-B for Java objects
2. **Authentication** - Basic Auth + Session tokens
3. **Compression** - GZIP support
4. **Async** - Future/AsyncResult support

### Supported Types

- Primitive types (int, long, etc.)
- String
- Date
- Collections (List, Set, Map)
- Custom types (Serializable)
- Enums
- Other EJB interfaces
