# CLAUDE.md - WAR Plugin

This file provides guidance for working with the `war` module - WAR deployment console plugin.

## Module Overview

The war plugin provides console pages and resources specific to WAR (Web Application Archive) deployment and management.

## Build Commands

```bash
# Build war plugin
mvn -DskipTests clean package -f appserver/admingui/war/pom.xml
```

## Integration Points

The war plugin defines integration points for:
- WAR-specific deployment settings
- Web application configuration
- Context root configuration
- Class loader settings for WARs

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `web` - Web container plugin
- `common` - Shared handlers (ApplicationHandlers, DeploymentHandler)
