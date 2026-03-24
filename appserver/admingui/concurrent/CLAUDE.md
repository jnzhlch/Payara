# CLAUDE.md - Concurrent Plugin

This file provides guidance for working with the `concurrent` module - concurrent utilities console plugin.

## Module Overview

The concurrent plugin provides console pages for managing concurrent utilities, including the ManagedExecutorService and other JSR-236 concurrency features.

## Build Commands

```bash
# Build concurrent plugin
mvn -DskipTests clean package -f appserver/admingui/concurrent/pom.xml
```

## Integration Points

The concurrent plugin defines integration points for:
- ManagedExecutorService configuration
- ManagedScheduledExecutorService configuration
- ContextService configuration
- Concurrency resource settings

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `common` - Shared handlers (ResourceHandlers)
