# CLAUDE.md - Admin REST

This file provides guidance for working with the Admin REST module - REST administration interface for Payara.

## Module Overview

The REST module provides a RESTful API for server administration, enabling web-based management and remote administrative operations via HTTP/JSON.

## REST Structure

```
rest/
├── gf-restadmin-connector/   # REST connector for admin console
├── rest-client/               # REST client library
├── rest-service/              # REST endpoint implementation
├── rest-service-l10n/         # Localization
└── rest-testing/              # Testing utilities
```

## Sub-Modules

### REST Service (`rest-service/`)

**Core REST implementation:**
- JAX-RS endpoints for admin operations
- JSON request/response handling
- Authentication and authorization
- See `rest-service/CLAUDE.md` for endpoint implementation details

### REST Client (`rest-client/`)

**Client library:**
- Programmatic REST API access
- Session management
- Error handling
- See `rest-client/CLAUDE.md` for client usage patterns

### Connector (`gf-restadmin-connector/`)

**Admin console integration:**
- Bridge between console and REST
- Session management for console
- See `gf-restadmin-connector/CLAUDE.md` for console integration

## Build Commands

```bash
# Build all REST modules
mvn -DskipTests clean package -f nucleus/admin/rest/pom.xml

# Build specific REST module
mvn -DskipTests clean package -f nucleus/admin/rest/rest-service/pom.xml
```

## REST API Structure

### Base URL

```
http://localhost:4848/management
```

### Main Endpoints

| Endpoint | Description |
|----------|-------------|
| `/management/domain` | Domain configuration |
| `/management/servers` | Server instances |
| `/management/clusters` | Cluster configuration |
| `/management/applications` | Deployed applications |
| `/management/resources` | Resources (JDBC, JMS, etc.) |
| `/management/monitored` | Monitoring data |

### Example Requests

```bash
# Get domain information
curl -u admin: http://localhost:4848/management/domain

# List servers
curl -u admin: http://localhost:4848/management/domain/servers

# Get server config
curl -u admin: http://localhost:4848/management/domain/servers/server

# Execute command
curl -u admin: -X POST \
  http://localhost:4848/management/domain/execute-command \
  -d '{"command":"list-applications"}'
```

## REST Command Execution

### Via POST to execute-command

```bash
curl -u admin:admin -H "Content-Type: application/json" \
  -X POST http://localhost:4848/management/domain/execute-command \
  -d '{
    "command": "create-jdbc-connection-pool",
    "id": "my-pool",
    "datasourceclassname": "org.h2.jdbcx.JdbcDataSource",
    "property": {
      "url":"jdbc:h2:mem:test",
      "user":"sa"
    }
  }'
```

## Response Format

### Success Response

```json
{
  "exit_code": "SUCCESS",
  "message": "Command executed successfully",
  "properties": {
    "command": "create-jdbc-connection-pool",
    "pool_name": "my-pool"
  }
}
```

### Error Response

```json
{
  "exit_code": "FAILURE",
  "message": "Invalid parameter value",
  "details": "..."
}
```

## Authentication

REST API uses Basic Authentication:
- Default username: `admin`
- Default password: (empty, set password after first start)
- Session maintained via cookies

## Admin Console Integration

The REST API powers:
- Admin Console (JSF web UI)
- Remote CLI commands
- Third-party management tools

### Connector (`gf-restadmin-connector/`)

Provides bridge between admin console and REST backend.

## REST Client Usage

### Programmatic Access

```java
import org.glassfish.admin.rest.client.RestClient;

RestClient client = RestClient.create(
    "http://localhost:4848/management",
    "admin", "");

// Execute command
Map<String, Object> result = client.executeCommand(
    "list-applications", null);
```

## Key Packages

- `org.glassfish.admin.rest` - REST endpoint implementations
- `org.glassfish.admin.rest.client` - REST client library
- `org.glassfish.admin.rest.connector` - Admin console connector

## REST Command Mapping

Admin commands map to REST endpoints:
- Command name → endpoint path
- Parameters → JSON body
- Output → JSON response

## Architecture Notes

1. **JAX-RS standard** - Uses standard JAX-RS (Jersey)
2. **JSON format** - Request/response in JSON
3. **Auth required** - All endpoints require authentication
4. **Command bridge** - Bridges existing CLI commands to REST
5. **Stateless** - REST API is stateless (session via cookies)

## Testing REST API

```bash
# Test connectivity
curl -u admin: http://localhost:4848/management/domain

# Test command execution
curl -u admin: -X POST \
  http://localhost:4848/management/domain/execute-command \
  -d '{"command":"version"}'

# Test with verbose output
curl -v -u admin: \
  http://localhost:4848/management/domain
```
