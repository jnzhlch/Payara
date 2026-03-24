# CLAUDE.md - Full Plugin

This file provides guidance for working with the `full` module - full profile console plugin.

## Module Overview

The full plugin provides console pages and resources for the full Jakarta EE profile, combining features from multiple plugins.

## Build Commands

```bash
# Build full plugin
mvn -DskipTests clean package -f appserver/admingui/full/pom.xml
```

## Integration Points

The full plugin aggregates integration points from:
- EJB (full profile)
- CORBA/IIOP
- JCA connectors
- JTS transactions
- Other full profile features

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `ejb` - EJB container plugin
- `corba` - CORBA plugin
- `jca` - JCA plugin
- `jts` - JTS plugin
