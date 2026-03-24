# CLAUDE.md - EJB Lite Plugin

This file provides guidance for working with the `ejb-lite` module - EJB Lite (web profile) console plugin.

## Module Overview

The ejb-lite plugin provides console pages for managing the EJB Lite container, which is the lightweight EJB implementation for the web profile.

## Build Commands

```bash
# Build ejb-lite plugin
mvn -DskipTests clean package -f appserver/admingui/ejb-lite/pom.xml
```

## Integration Points

The ejb-lite plugin defines integration points for:
- EJB Lite container configuration
- Lightweight EJB settings
- Web profile EJB features

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `ejb` - Full EJB plugin
- `appserver/ejb` - EJB container implementation
