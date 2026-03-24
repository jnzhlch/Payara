# CLAUDE.md - Core

This file provides guidance for working with the `core` module - core console infrastructure.

## Module Overview

The core module provides the core console infrastructure including theme handling, table handlers, and Woodstock component support.

## Build Commands

```bash
# Build core
mvn -DskipTests clean package -f appserver/admingui/core/pom.xml
```

## Key Components

### Handlers

Located in `org.glassfish.admingui.handlers`:

| Handler | Purpose |
|---------|---------|
| `TableHandlers` | Table component handlers |
| `ThemeHandlers` | Theme selection handlers |
| `WoodstockHandler` | Woodstock component utilities |

### Theme Context

Located in `org.glassfish.admingui.theme`:

- `AdminguiThemeContext` - Theme context management

### Utilities

Located in `org.glassfish.admingui.util`:

- `SunOptionUtil` - Option handling utilities

## Package Structure

```
org.glassfish.admingui/
├── handlers/
│   ├── TableHandlers.java       # Table component handlers
│   ├── ThemeHandlers.java       # Theme selection
│   └── WoodstockHandler.java    # Woodstock utilities
├── theme/
│   └── AdminguiThemeContext.java # Theme context
└── util/
    └── SunOptionUtil.java        # Option utilities
```

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `dataprovider` - Woodstock DataProvider
- `woodstock-webui-jsf` - JSF components
- `faces-compat` - JSF compatibility layer

## Related Modules

- `common` - Shared handlers and utilities
- `payara-theme` - Payara-specific theme
