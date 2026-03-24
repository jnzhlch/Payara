# CLAUDE.md - Admin GUI Connector

This file provides guidance for working with the `gf-admingui-connector` module - console plugin connector models.

## Module Overview

The gf-admingui-connector module provides the data models and interfaces for console plugin integration, including integration points, help TOC, and gadget configuration.

## Build Commands

```bash
# Build gf-admingui-connector
mvn -DskipTests clean package -f appserver/admingui/gf-admingui-connector/pom.xml
```

## Key Models

### ConsoleConfig

Located in `org.glassfish.admingui.connector`:

```java
public class ConsoleConfig {
    private String id;                      // Plugin ID
    private List<IntegrationPoint> integrationPoints;
    private String description;             // Plugin description
}
```

### IntegrationPoint

```java
public class IntegrationPoint {
    private String id;              // Unique ID
    private String type;            // Integration point type
    private int priority;           // Order priority
    private String parentId;        // Parent ID
    private String content;         // Content file path
    private String consoleConfigId; // Module providing this IP
}
```

### Help TOC Models

```java
// TOC (Table of Contents)
public class TOC {
    private String version;
    private List<TOCItem> TOCItems;
}

// TOC Item
public class TOCItem {
    private String id;
    private String target;          // HTML target
    private String text;            // Display text
    private List<TOCItem> TOCItems; // Children
}

// Index (for search)
public class Index {
    private String version;
    private List<IndexItem> indexItems;
}

// Index Item
public class IndexItem {
    private String keyword;
    private String target;          // HTML file
    private String text;            // Description
    private List<IndexItem> indexItems;
}
```

### Gadget Models

```java
// Gadget module (dashboard gadget)
public class GadgetModule {
    private String id;
    private String name;
    private String context;         // Gadget context
    private int refreshInterval;
    private List<GadgetContent> contents;
}

// Gadget content
public class GadgetContent {
    private String type;            // Content type
    private String url;             // Content URL
    private String text;            // Text content
}

// Gadget user preferences
public class GadgetUserPref {
    private String name;
    private String value;
}

// Gadget module preferences
public class GadgetModulePrefs {
    private List<GadgetUserPref> userPrefs;
}
```

## Package Structure

```
org.glassfish.admingui.connector/
├── ConsoleConfig.java           # Console config model
├── IntegrationPoint.java        # Integration point model
├── TOC.java                     # Help TOC model
├── TOCItem.java                 # TOC item model
├── Index.java                   # Help index model
├── IndexItem.java               # Index item model
├── GadgetModule.java            # Gadget module model
├── GadgetContent.java           # Gadget content model
├── GadgetUserPref.java          # Gadget user prefs
└── GadgetModulePrefs.java       # Gadget module prefs
```

## Integration Point Types Reference

| Type Constant | Purpose |
|--------------|---------|
| `org.glassfish.admingui:navNode` | Navigation tree node |
| `org.glassfish.admingui:configuration` | Configuration page |
| `org.glassfish.admingui:serverInstTab` | Server instance tab |
| `org.glassfish.admingui:clusterInstTab` | Cluster instance tab |
| `org.glassfish.admingui:standaloneInstTab` | Standalone instance tab |
| `org.glassfish.admingui:uploadPropertySheet` | Upload property sheet |
| `org.glassfish.admingui:editAppPage` | Edit application page |
| `org.glassfish.admingui:helpSet` | Help documentation |
| `org.glassfish.admingui:gadget` | Dashboard gadget |
| `org.glassfish.admingui:appTypeDropdown` | Application type dropdown |

## Dependencies

- `hk2-core` - Dependency injection
- `glassfish-api` - Public APIs

## Integration Points

Used by:
- `plugin-service` - Integration point discovery
- All console plugins - Define their integration points
