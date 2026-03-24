# CLAUDE.md - Health Check Plugin

This file provides guidance for working with the `healthcheck-service-console-plugin` module - health check console plugin.

## Module Overview

The healthcheck-service-console-plugin provides console pages for configuring Payara's health check service, which monitors various aspects of the server (CPU, memory, connections, etc.).

## Build Commands

```bash
# Build healthcheck plugin
mvn -DskipTests clean package -f appserver/admingui/healthcheck-service-console-plugin/pom.xml
```

## Key Components

### Console Provider

Located in `fish.payara.admingui.healthcheck`:

```java
@Service
public class HealthcheckServicePlugin implements ConsoleProvider {
    @Override
    public URL getConfiguration() {
        return null;
    }
}
```

## Health Check Services

The plugin provides configuration for these health check services:

| Service | Purpose |
|---------|---------|
| `CPU` | CPU usage monitoring |
| `HC` | Heap memory usage |
| `MEM` | System memory usage |
| `SWAP` | Swap space usage |
| `THREAD` | Thread count monitoring |
| `CONNECTION_POOL` | Database connection pool health |
| `GC` | Garbage collection monitoring |

## Integration Points

The health check plugin defines integration points for:
- Health check service configuration
- Individual service configuration
- Health check threshold settings
- Notification configuration

## Resources

```
src/main/resources/
├── META-INF/admingui/
│   └── console-config.xml
├── fish/payara/admingui/healthcheck/  # Health check pages
│   ├── config/                         # Configuration pages
│   └── services/                        # Service-specific pages
└── org/                                 # JSF pages
```

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `nucleus/payara-modules/healthcheck-core` - Health check implementation
- `payara-console-extras` - Payara console extras
