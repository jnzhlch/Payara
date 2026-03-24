# CLAUDE.md - JCA Plugin

This file provides guidance for working with the `jca` module - JCA/connector console plugin.

## Module Overview

The jca plugin provides console pages for managing JCA (Java Connector Architecture) resources, connector connection pools, and resource adapters.

## Build Commands

```bash
# Build jca plugin
mvn -DskipTests clean package -f appserver/admingui/jca/pom.xml
```

## Integration Points

The jca plugin defines integration points for:
- Connector connection pool management
- Resource adapter configuration
- Work management settings
- JCA resource configuration

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `appserver/jca` - JCA/connector implementation
- `common` - Shared handlers (ResourceHandlers)
