# CLAUDE.md - EJB Plugin

This file provides guidance for working with the `ejb` module - EJB container console plugin.

## Module Overview

The ejb plugin provides console pages for managing the EJB container, including EJB timers, pools, and configuration.

## Build Commands

```bash
# Build ejb plugin
mvn -DskipTests clean package -f appserver/admingui/ejb/pom.xml
```

## Integration Points

The ejb plugin defines integration points for:
- EJB container configuration
- EJB timer service settings
- EJB pool configuration
- EJB monitoring settings

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `appserver/ejb` - EJB container implementation
- `ejb-lite` - EJB Lite (web profile) plugin
