# CLAUDE.md - GF REST Admin Connector

This file provides guidance for working with the GF REST Admin Connector module - bridge between admin console and REST backend.

## Module Overview

The GF REST Admin Connector (GlassFish REST Admin Connector) provides the integration layer between the JSF-based admin console and the REST administration backend. It handles authentication, session management, and console-specific REST extensions.

## Key Components

### Connector

**Admin console integration:**
- Bridge between console UI and REST API
- Session management for console
- Console-specific authentication handling

### REST Integration

**Console to REST bridge:**
- Wraps REST client for console use
- Converts console actions to REST calls
- Handles console-specific response formats

## Build Commands

```bash
# Build connector module
mvn -DskipTests clean package -f nucleus/admin/rest/gf-restadmin-connector/pom.xml
```

## Architecture

```
┌─────────────────┐
│  Admin Console  │ (JSF Web UI)
│   (admingui)    │
└────────┬────────┘
         │
         │ HTTP/JSON
         │
┌────────▼────────┐
│   REST Admin    │
│    Connector    │
└────────┬────────┘
         │
         │ HTTP/JSON
         │
┌────────▼────────┐
│  REST Service   │ (JAX-RS Endpoints)
│   (rest-service)│
└─────────────────┘
```

## Console Integration

### Authentication Flow

1. User enters credentials in console login page
2. Console uses connector to authenticate via REST
3. Session token stored in console session
4. Subsequent requests use stored token

### Request Flow

1. Console action triggered by user
2. Console JavaScript makes AJAX request
3. Connector forwards to REST endpoint
4. Response converted for console display
5. Console UI updated

## Console-Specific Features

### Extended Responses

Connector may add console-specific data:
- UI hints
- Display formatting
- Navigation info
- Validation messages

### Batch Operations

Console can batch multiple REST calls:
- Reduces round trips
- Improves performance
- Maintains transaction semantics

## Key Packages

- `org.glassfish.admingui.connector` - Console connector classes
- Integration packages for REST client

## Console REST Usage

### From Console JavaScript

```javascript
// Console uses connector internally
$.ajax({
    url: '/management/domain/servers/server',
    type: 'GET',
    dataType: 'json',
    success: function(data) {
        // Update UI with data
    }
});
```

### From Console Backing Beans

```java
// Console backing bean uses connector
@ManagedBean
public class ServerBean {
    @Inject
    private RestClient client;

    public String getServerName() {
        // Use connector to get data
        RestLeaf server = client.getDomain()
            .child("servers")
            .child("server");
        return server.get("name");
    }
}
```

## Session Management

### Console Session

Console maintains session with REST backend:
- JSESSIONID cookie shared between console and REST
- Session timeout handling
- Auto-reauthentication on expiry

### Session Tokens

```java
// Get session token from request
String sessionToken = getSessionToken();

// Use token in connector
RestClient client = RestClient.create(
    "http://localhost:4848/management",
    sessionToken);
```

## Error Handling

### Console Error Display

Connector converts REST errors to console UI:
- Error messages displayed to user
- Validation errors shown inline
- Server errors shown as notifications

## Console-Specific Endpoints

Connector may provide console-specific extensions:
- UI metadata endpoints
- Navigation structure
- Help system integration

## Authentication

### Console Login

```java
// Console login through connector
public boolean login(String username, String password) {
    RestClient client = RestClient.create(
        REST_URL, username, password);

    // Test authentication
    try {
        client.getDomain();
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

## Key Files

| File | Purpose |
|------|---------|
| Connector classes | Console-REST bridge |
| Response wrappers | Console-specific formatting |
| Session management | Authentication state |

## Architecture Notes

1. **Transparent bridge** - Console doesn't directly call REST
2. **Session aware** - Manages console session state
3. **Error mapping** - Maps REST errors to console UI
4. **Performance** - Optimizes for console use cases

## Development

### Testing Console Integration

```bash
# Start server
./asadmin start-domain

# Access console at
http://localhost:4848/

# Use browser dev tools to see REST calls
# Network tab shows connector → REST communication
```

### Console Deployment

Console deployed as:
- Web application in domain
- Uses connector for all admin operations
- Connector packaged with console

## Related Modules

- `admingui` - Admin console (JSF UI)
- `rest-service` - REST endpoints
- `rest-client` - REST client library
