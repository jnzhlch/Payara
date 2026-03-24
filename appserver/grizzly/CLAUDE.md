# CLAUDE.md - Appserver Grizzly

This file provides guidance for working with the `appserver/grizzly` module - Grizzly adapter deployment container for Payara.

## Module Overview

The appserver/grizzly module provides a deployment container for Grizzly HttpHandler applications in Payara. It allows developers to deploy custom Grizzly adapters as standalone applications, separate from the web container.

**Note:** This is different from `nucleus/grizzly` which provides the core HTTP/NIO networking infrastructure for Payara. This module specifically enables deploying custom Grizzly `HttpHandler` implementations as applications.

## Sub-Modules

| Submodule | Purpose |
|-----------|---------|
| `grizzly-container` | Core deployment container for Grizzly adapters |
| `glassfish-grizzly-extra-all` | Aggregate bundle combining Grizzly extras |

## Build Commands

```bash
# Build entire grizzly module
mvn -DskipTests clean package -f appserver/grizzly/pom.xml

# Build grizzly-container only
mvn -DskipTests clean package -f appserver/grizzly/grizzly-container/pom.xml

# Build glassfish-grizzly-extra-all
mvn -DskipTests clean package -f appserver/grizzly/glassfish-grizzly-extra-all/pom.xml
```

## Architecture

### Deployment Flow

```
Application Archive (WAR/EJB JAR)
         │
         ├─→ Contains: META-INF/grizzly-glassfish.xml
         │
    [GrizzlyAdapterSniffer]
         │ Detects grizzly applications
         │
    [GrizzlyContainer]
         │ Container implementation
         │
    [GrizzlyDeployer]
         │ Loads and deploys adapters
         │
         ├─→ GrizzlyModuleDescriptor (parses XML)
         │
         ├─→ Instantiates HttpHandler classes
         │
         └─→ GrizzlyApp (registers endpoints)
```

## Core Components

### GrizzlyAdapterSniffer

```java
@Service(name="grizzly")
public class GrizzlyAdapterSniffer extends GenericSniffer {
    // Detects applications with META-INF/grizzly-glassfish.xml
    // Supports WAR and EJB archive types
}
```

**Descriptor Path:** `META-INF/grizzly-glassfish.xml`

**Supported Archives:**
- WAR (`ModuleType.WAR`)
- EJB JAR (`ModuleType.EJB`)

### GrizzlyContainer

```java
@Service(name="grizzly")
public class GrizzlyContainer implements Container {
    public Class<? extends Deployer> getDeployer() {
        return GrizzlyDeployer.class;
    }
}
```

The container is an HK2 service that provides the Grizzly deployer.

### GrizzlyDeployer

Loads and deploys Grizzly adapters:

```java
@Service(name="grizzly")
public class GrizzlyDeployer implements Deployer<GrizzlyContainer, GrizzlyApp> {
    public GrizzlyApp load(GrizzlyContainer container, DeploymentContext context) {
        // 1. Parse descriptor
        // 2. Load HttpHandler classes
        // 3. Set properties via introspection
        // 4. Start handlers
        // 5. Return GrizzlyApp
    }
}
```

### GrizzlyApp

Represents a deployed Grizzly application:

```java
public class GrizzlyApp implements ApplicationContainer {
    public static final class Adapter {
        final HttpHandler service;    // Grizzly HttpHandler
        final String contextRoot;      // Registration path
    }

    public boolean start(ApplicationContext startupContext) {
        // Register adapters with RequestDispatcher
    }
}
```

## Deployment Descriptor

### grizzly-glassfish.xml

Location: `META-INF/grizzly-glassfish.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE grizzly-glassfish PUBLIC
    "-//GlassFish.org//DTD Grizzly GlassFish//EN"
    "https://glassfish.org/dtds/grizzly-glassfish.dtd">

<grizzly-glassfish>
    <!-- Define HTTP handlers/adapters -->
    <adapter context-root="/mypath"
             class-name="com.example.MyHttpHandler">

        <!-- Optional properties -->
        <property name="someProperty" value="someValue"/>
        <property name="anotherProperty" value="123"/>
    </adapter>

    <!-- Alternative: http-handler element (same as adapter) -->
    <http-handler context-root="/other"
                  class-name="com.example.OtherHandler">
        <property name="enabled" value="true"/>
    </http-handler>
</grizzly-glassfish>
```

### Descriptor Elements

| Element | Attribute | Description |
|---------|-----------|-------------|
| `<adapter>` | `context-root` | URL path for the handler |
| | `class-name` | Fully qualified HttpHandler class |
| `<http-handler>` | (same as adapter) | Alternative element name |
| `<property>` | `name` | Property name (setter method) |
| | `value` | Property value (converted to type) |

## Property Configuration

### IntrospectionUtils

Properties are set via reflection using `IntrospectionUtils.setProperty()`:

**Supported Types:**
- `String`
- `int` / `Integer`
- `long` / `Long`
- `boolean` / `Boolean`
- `InetAddress`

**Conversion Logic:**
1. First tries `setProperty(String value)` method
2. Falls back to type-specific setter (`setInt`, `setBoolean`, etc.)
3. Falls back to generic `setProperty(String name, String value)`

Example:
```java
// Property: <property name="port" value="8080"/>
// Invokes: myHandler.setPort(8080)

// Property: <property name="enabled" value="true"/>
// Invokes: myHandler.setEnabled(true)
```

## Creating a Grizzly Adapter Application

### Step 1: Implement HttpHandler

```java
package com.example;

import org.glassfish.grizzly.http.HttpHandler;
import org.glassfish.grizzly.http.Request;
import org.glassfish.grizzly.http.Response;

public class MyHttpHandler extends HttpHandler {

    private int port = 8080;
    private boolean enabled = true;

    public void setPort(int port) {
        this.port = port;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void service(Request request, Response response) throws Exception {
        response.setContentType("text/plain");
        response.getWriter().write("Hello from Grizzly! Port: " + port);
    }
}
```

### Step 2: Create Descriptor

Create `META-INF/grizzly-glassfish.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<grizzly-glassfish>
    <adapter context-root="/myhandler"
             class-name="com.example.MyHttpHandler">
        <property name="port" value="9090"/>
        <property name="enabled" value="true"/>
    </adapter>
</grizzly-glassfish>
```

### Step 3: Package and Deploy

```bash
# Package as JAR or WAR
jar cvf myhandler.jar *

# Deploy to Payara
asadmin deploy myhandler.jar

# Access at: http://localhost:8080/myhandler
```

## Module Dependencies

### grizzly-container

| Dependency | Purpose |
|------------|---------|
| `hk2-core` | Dependency injection |
| `glassfish-api` | GlassFish internal APIs |
| `internal-api` | Internal server APIs |
| `jakarta.enterprise.deploy-api` | JSR-88 deployment API |

### glassfish-grizzly-extra-all

| Dependency | Purpose |
|------------|---------|
| `grizzly-comet` | Comet support |
| `grizzly-websockets` | WebSocket support |
| `grizzly-http-servlet` | Servlet integration |
| `grizzly-http-ajp` | AJP protocol support |
| `glassfish-grizzly-container` | Core container |
| `jakarta.servlet-api` | Servlet API (provided) |

## Package Structure

```
appserver/grizzly/
├── grizzly-container/                      # Core container
│   └── src/main/java/org/glassfish/extras/grizzly/
│       ├── GrizzlyAdapterSniffer.java      # Sniffer for detection
│       ├── GrizzlyContainer.java           # Container service
│       ├── GrizzlyDeployer.java            # Deployer implementation
│       ├── GrizzlyApp.java                 # Application container
│       ├── GrizzlyModuleDescriptor.java    # Descriptor parser
│       └── IntrospectionUtils.java         # Property configuration
│
└── glassfish-grizzly-extra-all/            # Aggregate bundle
    └── pom.xml                            # Combines all Grizzly extras
```

## OSGi Exports

The `glassfish-grizzly-extra-all` bundle exports:

```
org.glassfish.grizzly.comet.*
org.glassfish.grizzly.websockets.*
org.glassfish.grizzly.http.ajp.*
org.glassfish.grizzly.servlet.*
org.glassfish.grizzly.extras.*
org.glassfish.extras.grizzly.*
```

## Installed Artifacts

After building and installing:

```
$PAYARA_HOME/
└── glassfish/
    └── modules/
        └── glassfish-grizzly-extra-all.jar
```

## Integration with Nucleus Grizzly

This module builds upon `nucleus/grizzly`:

| Module | Purpose |
|--------|---------|
| `nucleus/grizzly/config` | Core HTTP/NIO networking, listener configuration |
| `appserver/grizzly` | Application deployment for custom HttpHandlers |

The `nucleus/grizzly` module provides the underlying networking infrastructure, while `appserver/grizzly` enables deploying custom Grizzly applications.

## Related Modules

- `nucleus/grizzly` - Core Grizzly networking and configuration
- `appserver/web` - Web container (Servlet/JSP)
- `appserver/deployment` - Deployment infrastructure

## Notes

- Grizzly adapters are deployed at the context-root specified in the descriptor
- Multiple adapters can be defined in a single application
- Adapters are started automatically during deployment
- Use `IntrospectionUtils.setProperty()` convention for configuration
- This module is primarily for custom Grizzly use cases, not standard web applications
