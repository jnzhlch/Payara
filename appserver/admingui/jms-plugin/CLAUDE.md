# CLAUDE.md - JMS Plugin

This file provides guidance for working with the `jms-plugin` module - JMS resources console plugin.

## Module Overview

The jms-plugin provides console pages for managing JMS resources, connection factories, and message queues/topics.

## Build Commands

```bash
# Build jms-plugin
mvn -DskipTests clean package -f appserver/admingui/jms-plugin/pom.xml
```

## Key Components

### Console Provider

Located in `org.glassfish.admingui.plugin.jms`:

```java
@Service
public class PluginConsoleProvider implements ConsoleProvider {
    @Override
    public URL getConfiguration() {
        return null;
    }
}
```

### Handlers

| Handler | Purpose |
|---------|---------|
| `JmsHandlers` | JMS resource operations |
| `JMXUtil` | JMX utility functions |

## Integration Points

The jms-plugin defines integration points for:
- JMS connection factory management
- JMS destination (queue/topic) management
- JMS resource configuration
- OpenMQ integration settings

## Package Structure

```
org.glassfish.admingui.plugin.jms/
├── PluginConsoleProvider.java    # Plugin provider
├── JmsHandlers.java              # JMS handlers
└── JMXUtil.java                   # JMX utilities
```

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `common` - Shared handlers (JmsResourceHandler)
- `appserver/jms` - JMS implementation (OpenMQ)
