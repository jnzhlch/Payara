# CLAUDE.md - Payara Console Extras

This file provides guidance for working with the `payara-console-extras` module - Payara-specific console features.

## Module Overview

The payara-console-extras module provides Payara-specific console features and utilities not found in the standard GlassFish console.

## Build Commands

```bash
# Build payara-console-extras
mvn -DskipTests clean package -f appserver/admingui/payara-console-extras/pom.xml
```

## Key Components

### Console Provider

Located in `fish.payara.admingui.extras`:

```java
@Service
public class PayaraConsolePlugin implements ConsoleProvider {
    @Override
    public URL getConfiguration() {
        return null;
    }
}
```

### Handlers

| Handler | Purpose |
|---------|---------|
| `PayaraUtilHandlers` | Payara-specific utility handlers |
| `PayaraRestApiHandlers` | REST API handlers for Payara features |

## Package Structure

```
fish.payara.admingui.extras/
├── PayaraConsolePlugin.java        # Plugin provider
├── PayaraUtilHandlers.java         # Utility handlers
└── rest/
    ├── PayaraRestApiHandlers.java  # REST handlers
    └── UnknownConfigurationException.java
```

## Features

Payara-specific console features including:
- Custom REST API endpoints
- Payara utility functions
- Additional configuration options

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `payara-theme` - Payara branding
- Payara feature plugins (healthcheck, microprofile, etc.)
