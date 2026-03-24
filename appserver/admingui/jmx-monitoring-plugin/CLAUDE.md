# CLAUDE.md - JMX Monitoring Plugin

This file provides guidance for working with the `jmx-monitoring-plugin` module - JMX monitoring console plugin.

## Module Overview

The jmx-monitoring-plugin provides console pages for configuring JMX-based monitoring in Payara Server.

## Build Commands

```bash
# Build jmx-monitoring plugin
mvn -DskipTests clean package -f appserver/admingui/jmx-monitoring-plugin/pom.xml
```

## Key Components

### Console Provider

Located in `fish.payara.admingui.jmxmonitoring`:

```java
@Service
public class JmxMonitoringPlugin implements ConsoleProvider {
    @Override
    public URL getConfiguration() {
        return null;
    }
}
```

## Features

The plugin provides configuration for:
- JMX monitoring service configuration
- MBean server settings
- Monitoring threshold configuration
- Notification settings

## Integration Points

The JMX monitoring plugin defines integration points for:
- Monitoring service configuration
- JMX connector settings
- MBean exposure configuration

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `nucleus/admin/monitor` - Monitoring infrastructure
- `appserver/payara-appserver-modules/jmx-monitoring-service` - JMX monitoring implementation
