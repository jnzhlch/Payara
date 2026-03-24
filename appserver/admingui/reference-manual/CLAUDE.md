# CLAUDE.md - Reference Manual Plugin

This file provides guidance for working with the `reference-manual` module - documentation and help system.

## Module Overview

The reference-manual plugin provides integrated documentation and help content for the admin console.

## Build Commands

```bash
# Build reference-manual plugin
mvn -DskipTests clean package -f appserver/admingui/reference-manual/pom.xml
```

## Features

- Integrated help system
- Context-sensitive documentation
- Product documentation links
- Reference material for console features

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities

## Related Modules

- `common` - Help handlers (HelpHandlers, HelpTreeAdaptor)
- `plugin-service` - Help TOC/Index management
