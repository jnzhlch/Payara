# CLAUDE.md - Event Bus Notifier Plugin

This file provides guidance for working with the `eventbus-notifier-console-plugin` module - event bus notification console plugin.

## Module Overview

The eventbus-notifier-console-plugin provides console pages for configuring the Payara event bus notification system, which sends notifications (for health checks, monitoring, etc.) via the event bus.

## Build Commands

```bash
# Build eventbus-notifier plugin
mvn -DskipTests clean package -f appserver/admingui/eventbus-notifier-console-plugin/pom.xml
```

## Key Components

### Console Provider

Located in `fish.payara.admingui.notifier.eventbus`:

```java
@Service
public class EventBusNotifierPlugin implements ConsoleProvider {
    @Override
    public URL getConfiguration() {
        return null;
    }
}
```

## Features

The plugin provides configuration for:
- Event bus notifier service configuration
- Notification event filtering
- Event bus integration settings

## Integration Points

The eventbus-notifier plugin defines integration points for:
- Notifier service configuration
- Event bus connection settings
- Notification rules configuration

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `cdieventbus-notifier-console-plugin` - CDI event bus notifier
- `jms-notifier-console-plugin` - JMS notifier
- `nucleus/payara-modules/notification-core` - Notification implementation
