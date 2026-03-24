# CLAUDE.md - Common

This file provides guidance for working with the `common` module - shared console utilities and handlers.

## Module Overview

The common module provides shared handlers, utilities, and components used across all console plugins. This includes REST utilities, deployment handlers, resource management, and security.

## Build Commands

```bash
# Build common
mvn -DskipTests clean package -f appserver/admingui/common/pom.xml
```

## Key Components

### REST Utilities

Located in `org.glassfish.admingui.common.util`:

| Class | Purpose |
|-------|---------|
| `RestUtil` | Primary REST client for admin operations |
| `RestUtil2` | Updated REST client |
| `RestResponse` | REST response wrapper |
| `GuiUtil` | GUI-specific utilities |
| `DeployUtil` | Deployment utilities |
| `TargetUtil` | Target (server/cluster) utilities |

### Handlers

Located in `org.glassfish.admingui.common.handlers`:

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
| `PreferencesHandler` | User preferences |
| `PluginHandlers` | Plugin management |

### Navigation

Located in `org.glassfish.admingui.common.factories`:

- `NavigationNodeFactory` - Navigation tree node factory
- `NavNodeContainerFactory` - Navigation container factory

### Security

Located in `org.glassfish.admingui.common.security`:

- `AdminConsoleAuthModule` - JAAS login module for console authentication

### Servlets

Located in `org.glassfish.admingui.common.servlet`:

| Servlet | Purpose |
|---------|---------|
| `DownloadServlet` | File downloads |
| `LogFilesContentSource` | Log file access |
| `LogViewerContentSource` | Log viewing |
| `ClientStubsContentSource` | Client stub downloads |

## REST Usage Examples

### Getting Configuration

```java
// Get a configuration value
RestResponse response = RestUtil.get("server-config.log-service");
String result = response.getResponse();
```

### Creating Resources

```java
// Create a resource via REST
Map<String, Object> attrs = new HashMap<>();
attrs.put("name", "myResource");
attrs.put("property", "value");
RestUtil.post("/resources/create", attrs);
```

### Deleting Resources

```java
// Delete a resource
RestUtil.delete("/resources/myResource");
```

### Querying Lists

```java
// Get list of resources
String[] resources = RestUtil.getArrayFromRest("/resources/list");
```

## Common Patterns

### Handler Methods

Handler methods are used from JSF pages:

```java
@Scoped(Singleton.class)
public class MyHandlers {
    public String[] getMyResources() {
        return RestUtil.getArrayFromRest("/resources/myplugin");
    }

    public String createResource(HashMap<String, Object> attrs) {
        return RestUtilHandlers.restRequest(
            "/resources/myplugin/create", attrs);
    }
}
```

### JSF Integration

In JSF pages:
```jsp
<ui:define name="content">
    <sun:tableRowGroup id="tableRowGroup"
        sourceVar="td"
        value="#{MyHandlers.myResources}"/>
</ui:define>
```

## Integration Points

The common module defines many integration points:

### Navigation Nodes
- `common_domain` - Domain configuration
- `common_server` - Server settings
- `common_apps` - Applications
- `common_resources` - Resources
- `common_config` - Configurations
- `common_jvm` - JVM settings
- `common_logger` - Logging
- `common_security` - Security

### Configuration Pages
- `common_jvmConfigurationLink` - JVM configuration link
- `common_loggerConfigurationLink` - Logger configuration link
- `common_securityLink` - Security configuration link
- `sysPropsConfigurationLink` - System properties link

### Deployment
- `common_deploy` - Deployment property sheet
- `common_deploy_type` - Application type dropdown
- `common_edit_app` - Edit application page

## Package Structure

```
org.glassfish.admingui.common/
├── handlers/                   # JSF backing beans
│   ├── ApplicationHandlers.java
│   ├── DeploymentHandler.java
│   ├── ResourceHandlers.java
│   ├── ClusterHandler.java
│   ├── SecurityHandler.java
│   ├── LoggingHandlers.java
│   ├── MonitoringHandlers.java
│   └── RestUtilHandlers.java
├── util/                       # Utilities
│   ├── RestUtil.java
│   ├── RestUtil2.java
│   ├── RestResponse.java
│   ├── GuiUtil.java
│   ├── DeployUtil.java
│   └── TargetUtil.java
├── factories/                  # Navigation factories
│   ├── NavigationNodeFactory.java
│   └── NavNodeContainerFactory.java
├── security/                   # Authentication
│   └── AdminConsoleAuthModule.java
├── servlet/                    # Custom servlets
│   ├── DownloadServlet.java
│   └── LogViewerContentSource.java
└── help/                       # Help system
    ├── HelpHandlers.java
    └── HelpTreeAdaptor.java
```

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `deployment-client` - Deployment operations
- `jersey-client` - REST client
- `rest-client` - REST API client
- `security` - Security services
- `faces-compat` - JSF compatibility

## Related Modules

- `console-core` - Core console infrastructure
- `plugin-service` - Plugin discovery and integration
- Feature plugins (jdbc, jms-plugin, etc.) - Use common utilities
