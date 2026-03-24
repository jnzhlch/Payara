# CLAUDE.md - JTS Plugin

This file provides guidance for working with the `jts` module - JTS transaction service console plugin.

## Module Overview

The jts plugin provides console pages for managing JTS (Java Transaction Service) configuration and transaction recovery.

## Build Commands

```bash
# Build jts plugin
mvn -DskipTests clean package -f appserver/admingui/jts/pom.xml
```

## Integration Points

The jts plugin defines integration points for:
- Transaction service configuration
- Transaction recovery settings
- JTS logging configuration

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `appserver/jts` - JTS implementation
