# CLAUDE.md - Admin Console (admingui)

This file provides guidance for working with the `appserver/admingui` module - Payara's web-based administration console.

## Module Overview

The Admin Console is a JSF-based web application for managing Payara Server through a browser. It uses a plugin architecture where each feature area (JDBC, JMS, EJB, etc.) provides its own console plugin.

## Sub-Modules

### Core Infrastructure
| Module | Purpose |
|--------|---------|
| `plugin-service` | Console plugin service and integration points |
| `dataprovider` | Repackaged Woodstock DataProvider |
| `common` | Shared handlers and utilities |
| `core` | Core console infrastructure |
| `faces-compat` | JSF compatibility layer |

### Feature Plugins
| Module | Purpose |
|--------|---------|
| `web` | Web container management |
| `cluster` | Clustering and instances |
| `jdbc` | JDBC resources and connection pools |
| `jms-plugin` | JMS resources |
| `ejb` / `ejb-lite` | EJB container management |
| `jca` | JCA/connector resources |
| `corba` | IIOP/ORB configuration |
| `jts` | JTS transaction service |
| `concurrent` | Concurrent utilities |

### Payara-Specific Plugins
| Module | Purpose |
|--------|---------|
| `payara-theme` | Payara branding theme |
| `payara-console-extras` | Payara-specific console features |
| `healthcheck-service-console-plugin` | Health check UI |
| `microprofile-console-plugin` | MicroProfile configuration |
| `jmx-monitoring-plugin` | JMX monitoring UI |
| `eventbus-notifier-console-plugin` | Event bus notifications |
| `cdieventbus-notifier-console-plugin` | CDI event bus notifications |
| `jms-notifier-console-plugin` | JMS notification UI |

### Localization
Each plugin has a corresponding `-l10n` module for translations.

## Build Commands

```bash
# Build entire admin console
mvn -DskipTests clean package -f appserver/admingui/pom.xml

# Build specific module
mvn -DskipTests clean package -f appserver/admingui/common/pom.xml

# Build all plugins
mvn -DskipTests clean package -f appserver/admingui/
```

## Architecture

### Console Architecture

```
┌────────────────────────────────────────────────────────────┐
│                    Web Browser                             │
│  https://localhost:4848/console                             │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                    Admingui Web App                        │
│  (JSF + Woodstock Components)                               │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   Plugin Service                           │
│  - Discovers and integrates console plugins                 │
│  - Manages integration points                               │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   Console Plugins                          │
├────────────────────────────────────────────────────────────┤
│  Web Plugin  │  JDBC Plugin  │  JMS Plugin  │  EJB Plugin   │
│  Cluster Plugin │ Health Check │ MicroProfile │ Payara       │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   REST API Layer                           │
│  (nucleus/admin/rest)                                       │
└────────────────────────────────────────────────────────────┘
```

### Plugin Integration

Plugins are discovered via HK2 and registered through `ConsoleProvider` interface:

```java
@Service
public class MyConsolePlugin implements ConsoleProvider {
    @Override
    public URL getConfiguration() {
        // Returns URL to console-config.xml
        return getClass().getResource("/console-config.xml");
    }
}
```

## Key Components

### ConsolePluginService

Located in `plugin-service`:

Manages console plugin integration points:

```java
@Service
public class ConsolePluginService {
    // Get integration points by type
    public List<IntegrationPoint> getIntegrationPoints(String type);

    // Get resources from all plugins
    public Map<String, List<URL>> getResources(String name);

    // Get help TOC
    public TOC getHelpTOC(String locale);
}
```

### Common Handlers

Located in `common/src/main/java/org/glassfish/admingui/common/handlers/`:

| Handler | Purpose |
|---------|---------|
| `ApplicationHandlers` | Application deployment/management |
| `DeploymentHandler` | Deployment operations |
| `ResourceHandlers` | Resource management (JDBC, JMS, etc.) |
| `ClusterHandler` | Cluster and instance management |
| `SecurityHandler` | Security configuration |
| `LoggingHandlers` | Log configuration |
| `MonitoringHandlers` | Monitoring setup |
| `RestUtilHandlers` | REST API utility functions |

### REST Utilities

Located in `common/src/main/java/org/glassfish/admingui/common/util/`:

- `RestUtil` - Primary REST client for admin operations
- `RestUtil2` - Updated REST client
- `RestResponse` - REST response wrapper
- `GuiUtil` - GUI-specific utilities
- `DeployUtil` - Deployment utilities

### Authentication

Located in `common/src/main/java/org/glassfish/admingui/common/security/`:

- `AdminConsoleAuthModule` - JAAS login module for console authentication

## Console Plugin Development

### Creating a Console Plugin

1. **Create console-config.xml**:

```xml
<console-config id="myPlugin">
    <integration-points>
        <!-- Define integration points -->
        <integration-point
            id="myPluginLink"
            type="org.glassfish.admingui:navNode"
            parentId="tree"
            priority="100"
            content="myPluginNav.jsf"/>
    </integration-points>
</console-config>
```

2. **Create ConsoleProvider**:

```java
package com.example.myplugin;

import org.glassfish.api.admingui.ConsoleProvider;
import org.jvnet.hk2.annotations.Service;

@Service
public class MyConsolePlugin implements ConsoleProvider {
    @Override
    public URL getConfiguration() {
        return getClass().getResource("/console-config.xml");
    }
}
```

3. **Create JSF Pages**:

```jsp
<!-- myPluginNav.jsf -->
<ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
    <sun:panelGroup id="myPluginGroup">
        <!-- Plugin content -->
    </sun:panelGroup>
</ui:composition>
```

### Integration Point Types

| Type | Purpose |
|------|---------|
| `org.glassfish.admingui:navNode` | Navigation tree nodes |
| `org.glassfish.admingui:configuration` | Configuration pages |
| `org.glassfish.admingui:helpSet` | Help documentation |
| `org.glassfish.admingui:gadget` | Dashboard gadgets |

### Handler Methods

Handlers are Java classes that provide business logic for JSF pages:

```java
package com.example.myplugin;

import org.glassfish.admingui.common.handlers.RestUtilHandlers;

@Scoped(Singleton.class)
public class MyPluginHandlers {

    public String[] getMyResources() {
        // Return list of resources via REST
        return RestUtilHandlers.getArrayFromRest(
            "/resources/myplugin");
    }

    public String createResource(HashMap<String, Object> attrs) {
        // Create resource via REST
        return RestUtilHandlers.restRequest(
            "/resources/myplugin/create", attrs);
    }
}
```

Use in JSF:
```jsp
<ui:define name="content">
    <sun:tableRowGroup id="tableRowGroup"
        sourceVar="td"
        value="#{MyPluginHandlers.myResources}"/>
</ui:define>
```

## REST API Integration

The console communicates with the server via REST API:

```java
// Get a configuration value
RestResponse response = RestUtil.get("server-config.log-service");

// Create a resource
Map<String, Object> attrs = new HashMap<>();
attrs.put("name", "myResource");
attrs.put("property", "value");
RestUtil.post("/resources/create", attrs);

// Delete a resource
RestUtil.delete("/resources/myResource");
```

## Package Structure

```
appserver/admingui/
├── plugin-service/         # Plugin infrastructure
├── dataprovider/           # Woodstock DataProvider
├── common/                 # Shared handlers/utilities
│   ├── handlers/           # JSF backing beans
│   ├── util/               # REST/GUI utilities
│   ├── security/           # Authentication
│   └── servlet/            # Custom servlets
├── core/                   # Core console pages
├── web/                    # Web container plugin
├── jdbc/                   # JDBC plugin
├── jms-plugin/             # JMS plugin
├── cluster/                # Clustering plugin
├── payara-*/               # Payara-specific plugins
└── [feature]-l10n/         # Localization modules
```

## Dependencies

Key dependencies:
- `glassfish-api` - Public APIs
- `hk2-core` - Dependency injection
- `woodstock-webui-jsf` - JSF components
- `deployment-client` - Deployment operations
- `rest-client` - REST API client
- `faces-compat` - JSF compatibility

## Console Theme

### Payara Theme

Located in `payara-theme/`:

- Custom CSS styling
- Payara branding
- Responsive layout updates

### Sun Theme

The console originally uses Woodstock Sun Theme, which Payara extends.

## Accessing the Console

### Development

```bash
# Start Payara server
asadmin start-domain

# Access console at
https://localhost:4848/console
```

### Default Login
- Username: `admin`
- Password: (empty by default, set on first access)

## Related Modules

- `nucleus/admin/rest` - REST API backend
- `nucleus/admin/config-api` - Configuration management
- `appserver/admin/cli` - CLI commands
- `appserver/deployment` - Deployment functionality
