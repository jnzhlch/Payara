# CLAUDE.md - REST Service

This file provides guidance for working with the REST Service module - JAX-RS endpoints for admin operations.

## Module Overview

The REST Service module implements the JAX-RS endpoints that provide the REST administration API. It bridges admin commands to HTTP/JSON, handles authentication, and provides resources for domain management.

## Key Components

### REST Adapters

**JAX-RS to Admin Command bridge:**
- `RestAdapter.java` - Base REST adapter
- `RestManagementAdapter.java` - Management endpoints
- `RestMonitoringAdapter.java` - Monitoring endpoints
- `RestCommandAdapter.java` - Command execution

### Resource Providers

**HK2 service providers:**
- `RestManagementResourceProvider.java` - Management resources
- `RestMonitoringResourceProvider.java` - Monitoring resources
- `RestCommandResourceProvider.java` - Command resources

### Resources

**Domain and configuration resources:**
- Resources generated from config beans
- `MonitoredAttributeBagResource.java` - Monitoring attributes
- `AbstractAttributeBagResource.java` - Base resource class

### Streams

**Response streaming:**
- `JsonStreamWriter.java` - JSON streaming
- `XmlStreamWriter.java` - XML streaming
- `StreamWriter.java` - Base stream writer

## Build Commands

```bash
# Build REST service module
mvn -DskipTests clean package -f nucleus/admin/rest/rest-service/pom.xml

# Build with tests
mvn clean package -f nucleus/admin/rest/rest-service/pom.xml
```

## REST Endpoint Pattern

### Resource Class

```java
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/domain")
@Produces({"application/json", "application/xml"})
public class DomainResource {

    @GET
    public Response getDomain() {
        // Return domain configuration
        return Response.ok(domain).build();
    }

    @POST
    @Path("execute-command")
    public Response executeCommand(String command) {
        // Execute admin command
        return Response.ok(result).build();
    }
}
```

## Command Execution

### Via POST to execute-command

```bash
curl -u admin: -X POST \
  -H "Content-Type: application/json" \
  -d '{"command":"list-applications"}' \
  http://localhost:4848/management/domain/execute-command
```

### With Parameters

```bash
curl -u admin: -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "command":"create-jdbc-connection-pool",
    "id":"my-pool",
    "datasourceclassname":"org.h2.jdbcx.JdbcDataSource",
    "property":{"url":"jdbc:h2:mem:test"}
  }' \
  http://localhost:4848/management/domain/execute-command
```

## Resource Tree Structure

```
/management
├── domain                    # Domain root
│   ├── servers              # Server instances
│   │   └── server
│   ├── configs              # Configurations
│   │   └── server-config
│   ├── resources            # Resources
│   │   ├── jdbc
│   │   ├── jms
│   │   └── ...
│   ├── applications         # Deployed apps
│   └── execute-command      # Command execution
└── monitored                # Monitoring data
    ├── server
    └── applications
```

## Response Format

### Success Response

```json
{
  "exit_code": "SUCCESS",
  "message": "Command executed successfully",
  "properties": {...}
}
```

### Error Response

```json
{
  "exit_code": "FAILURE",
  "message": "Error message",
  "stack_trace": "..."
}
```

## Content Negotiation

### JSON Response

```bash
curl -H "Accept: application/json" \
  http://localhost:4848/management/domain
```

### XML Response

```bash
curl -H "Accept: application/xml" \
  http://localhost:4848/management/domain
```

## Exception Handling

### Exception Filter

`ExceptionFilter.java` converts exceptions to HTTP responses:

| Exception | HTTP Code |
|-----------|-----------|
| `CommandValidationException` | 400 Bad Request |
| `CommandException` | 500 Internal Server Error |
| `SecurityException` | 403 Forbidden |
| `Exception` | 500 Internal Server Error |

## Authentication

### Basic Auth

```bash
# With username
curl -u admin: http://localhost:4848/management/domain

# With username and password
curl -u admin:password http://localhost:4848/management/domain
```

### Session Management

Sessions maintained via:
- JSESSIONID cookie
- Server-side session tracking

## Streaming

### JSON Streaming

```java
import fish.payara.admin.rest.streams.JsonStreamWriter;

JsonStreamWriter writer = new JsonStreamWriter();
writer.writeStartObject();
writer.write("name", "value");
writer.writeEndObject();
```

### XML Streaming

```java
import fish.payara.admin.rest.streams.XmlStreamWriter;

XmlStreamWriter writer = new XmlStreamWriter();
writer.writeStartElement("root");
writer.writeAttribute("attr", "value");
writer.writeEndElement();
```

## Config Bean to Resource Mapping

Config beans automatically mapped to REST resources:

```java
@Configured
public interface MyConfig {
    @Attribute
    String getValue();
}
```

Maps to:
```
GET /management/configs/server-config/my-config
```

## Monitoring Endpoints

### Monitoring Data

```bash
# Get server monitoring data
curl http://localhost:4848/management/monitored/server

# Get application monitoring
curl http://localhost:4848/management/monitored/applications/myapp
```

## Key Packages

- `org.glassfish.admin.rest.adapter` - REST adapters
- `org.glassfish.admin.rest.resources` - REST resources
- `fish.payara.admin.rest.resources` - Payara REST resources
- `fish.payara.admin.rest.streams` - Response streaming

## Architecture Notes

1. **JAX-RS standard** - Uses standard JAX-RS (Jersey)
2. **HK2 integration** - Resources are HK2 services
3. **Config-driven** - Resources generated from config beans
4. **Command bridge** - Bridges CLI commands to REST
5. **Multi-format** - Supports JSON and XML

## Jersey Container

### JerseyContainer

`JerseyContainer.java` manages the JAX-RS application:
- HK2 service locator integration
- Resource registration
- Exception mapper registration

### Reloader

`Reloader.java` handles dynamic reloading of REST resources.

## Testing

### Test REST Endpoints

```bash
# Test connectivity
curl -u admin: http://localhost:4848/management/domain

# Test command execution
curl -u admin: -X POST \
  -H "Content-Type: application/json" \
  -d '{"command":"version"}' \
  http://localhost:4848/management/domain/execute-command

# With verbose output
curl -v -u admin: \
  http://localhost:4848/management/domain
```

## Resource Pattern

### CRUD Operations

```java
@Path("/my-resources")
public class MyResourceResource {

    @GET
    public Response list() { ... }

    @GET
    @Path("{name}")
    public Response get(@PathParam("name") String name) { ... }

    @POST
    public Response create(MyResource resource) { ... }

    @DELETE
    @Path("{name}")
    public Response delete(@PathParam("name") String name) { ... }
}
```
