# CLAUDE.md - CDI Event Bus Notifier Plugin

This file provides guidance for working with the `cdieventbus-notifier-console-plugin` module - CDI event bus notification console plugin.

## Module Overview

The cdieventbus-notifier-console-plugin provides console pages for configuring the CDI event bus notifier, which sends Payara notifications via CDI events.

## Build Commands

```bash
# Build cdieventbus-notifier plugin
mvn -DskipTests clean package -f appserver/admingui/cdieventbus-notifier-console-plugin/pom.xml
```

## Features

The plugin provides configuration for:
- CDI event bus notifier service
- CDI event filtering
- Observer configuration

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `eventbus-notifier-console-plugin` - Standard event bus notifier
- `jms-notifier-console-plugin` - JMS notifier
