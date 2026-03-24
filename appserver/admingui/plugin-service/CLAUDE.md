# CLAUDE.md - Plugin Service

This file provides guidance for working with the `plugin-service` module - the console plugin infrastructure.

## Module Overview

The plugin-service module provides the core infrastructure for discovering and integrating console plugins. It manages integration points, help TOCs, and resource loading across all console plugins.

## Build Commands

```bash
# Build plugin-service
mvn -DskipTests clean package -f appserver/admingui/plugin-service/pom.xml
```

## Architecture

### Plugin Discovery and Integration

```
ConsoleProvider (interface)
        │
        ├─→ Console Plugin (@Service)
        │       └─→ console-config.xml
        │
        ▼
ConsolePluginService
        │
        ├─→ Parse console-config.xml
        ├─→ Extract IntegrationPoints
        ├─→ Build Help TOC
        └─→ Manage ClassLoaders
```

## Key Components

### ConsolePluginService

Located in `org.glassfish.admingui.plugin`:

```java
@Service
public class ConsolePluginService {
    // Get integration points by type
    public List<IntegrationPoint> getIntegrationPoints(String type);

    // Get resources from all plugins
    public Map<String, List<URL>> getResources(String name);

    // Get help TOC
    public TOC getHelpTOC(String locale);

    // Get help index
    public Index getHelpIndex(String locale);

    // Get module ClassLoader
    public ClassLoader getModuleClassLoader(String moduleName);
}
```

### IntegrationPoint

Located in `org.glassfish.admingui.connector`:

```java
public class IntegrationPoint {
    private String id;              // Unique ID
    private String type;            // Integration point type
    private int priority;           // Order priority
    private String parentId;        // Parent integration point
    private String content;         // Content (JSF page, etc.)
    private String consoleConfigId; // Module ID
}
```

## Integration Point Types

| Type | Purpose |
|------|---------|
| `org.glassfish.admingui:navNode` | Navigation tree nodes |
| `org.glassfish.admingui:configuration` | Configuration pages |
| `org.glassfish.admingui:serverInstTab` | Server instance tabs |
| `org.glassfish.admingui:clusterInstTab` | Cluster instance tabs |
| `org.glassfish.admingui:uploadPropertySheet` | Deployment property sheets |
| `org.glassfish.admingui:helpSet` | Help documentation |
| `org.glassfish.admingui:gadget` | Dashboard gadgets |

## Console Plugin Registration

Plugins register via `ConsoleProvider`:

```java
@Service
public class MyConsolePlugin implements ConsoleProvider {
    @Override
    public URL getConfiguration() {
        // Return null to use default console-config.xml location
        // Or return specific config file
        return getClass().getResource("/META-INF/admingui/console-config.xml");
    }
}
```

Default config file location: `META-INF/admingui/console-config.xml`

## Console Config Format

```xml
<console-config id="pluginId">
    <integration-point
        id="uniqueId"
        type="org.glassfish.admingui:navNode"
        priority="100"
        parentId="tree"
        content="myPage.jsf"/>
</console-config>
```

## Help System

### Help TOC Structure

The help system combines TOC files from all plugins:

```
{locale}/help/toc.xml
```

TOC merging:
1. Each plugin provides `toc.xml`
2. ConsolePluginService merges all TOCs
3. Target paths prefixed with module ID

### Help Index

Similar to TOC, the help index combines `index.xml` from all plugins.

## Resource Loading

Resources are loaded from plugin ClassLoaders:

```java
// Get resource from all plugins
Map<String, List<URL>> urls = pluginService.getResources("path/to/resource");

// Returns map of module ID to list of URLs
```

## Package Structure

```
org.glassfish.admingui.plugin/
├── ConsolePluginService.java     # Main plugin service
└── IntegrationPointComparator.java # For sorting integration points

org.glassfish.admingui.connector/
├── IntegrationPoint.java         # Integration point model
├── ConsoleConfig.java            # Console config model
├── TOC.java                      # Help TOC model
├── TOCItem.java                  # TOC item model
├── Index.java                    # Help index model
├── IndexItem.java                # Index item model
└── Gadget*.java                  # Gadget-related classes
```

## Dependencies

- `hk2-core` - Dependency injection
- `glassfish-api` - Public APIs
- `gf-admingui-connector` - Connector models

## Integration Points

The plugin-service integrates with:
- All console plugins (via HK2 `ConsoleProvider`)
- Help system (TOC and index merging)
- REST API (via handlers in plugins)

## Priority and Ordering

Integration points are ordered by `priority`:
- Lower priority = appears first
- Higher priority = appears later
- Used for navigation tree ordering, tab ordering, etc.
