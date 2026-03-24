# CLAUDE.md - JMS Notifier Plugin

This file provides guidance for working with the `jms-notifier-console-plugin` module - JMS notification console plugin.

## Module Overview

The jms-notifier-console-plugin provides console pages for configuring the JMS notifier, which sends Payara notifications (health checks, monitoring, etc.) via JMS.

## Build Commands

```bash
# Build jms-notifier plugin
mvn -DskipTests clean package -f appserver/admingui/jms-notifier-console-plugin/pom.xml
```

## Features

The plugin provides configuration for:
- JMS notifier service configuration
- JMS destination (queue/topic) settings
- Message formatting options
- JMS connection factory configuration

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `eventbus-notifier-console-plugin` - Event bus notifier
- `cdieventbus-notifier-console-plugin` - CDI event bus notifier
- `appserver/jms` - JMS implementation
