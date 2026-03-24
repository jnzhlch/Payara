# CLAUDE.md - CORBA Plugin

This file provides guidance for working with the `corba` module - CORBA/IIOP console plugin.

## Module Overview

The corba plugin provides console pages for managing CORBA/IIOP (Common Object Request Broker Architecture/Internet Inter-ORB Protocol) settings in Payara.

## Build Commands

```bash
# Build corba plugin
mvn -DskipTests clean package -f appserver/admingui/corba/pom.xml
```

## Integration Points

The corba plugin defines integration points for:
- IIOP listener configuration
- ORB settings
- CORBA security settings
- CSIv2 SSL configuration

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `appserver/corba` - CORBA/IIOP implementation
- `common` - Shared utilities
