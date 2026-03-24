# CLAUDE.md - MicroProfile Plugin

This file provides guidance for working with the `microprofile-console-plugin` module - MicroProfile configuration console plugin.

## Module Overview

The microprofile-console-plugin provides console pages for configuring Payara's MicroProfile implementations, including Config, Health, Metrics, OpenAPI, Fault Tolerance, and JWT Auth.

## Build Commands

```bash
# Build microprofile plugin
mvn -DskipTests clean package -f appserver/admingui/microprofile-console-plugin/pom.xml
```

## Key Components

### Console Provider

Located in `fish.payara.admingui.microprofile`:

```java
@Service
public class MicroProfilePlugin implements ConsoleProvider {
    @Override
    public URL getConfiguration() {
        return null;
    }
}
```

## MicroProfile Features

The plugin provides configuration for:

| Feature | Description |
|---------|-------------|
| **Config** | MicroProfile Config - externalized configuration |
| **Health** | MicroProfile Health - health check endpoints |
| **Metrics** | MicroProfile Metrics - metrics endpoints |
| **OpenAPI** | MicroProfile OpenAPI - OpenAPI documentation |
| **Fault Tolerance** | MicroProfile Fault Tolerance - retry, timeout, circuit breaker |
| **JWT Auth** | MicroProfile JWT Authentication - JWT token validation |

## Integration Points

The microprofile plugin defines integration points for:
- MicroProfile service configuration
- Individual feature settings
- Endpoint configuration
- Notifier configuration

## Resources

```
src/main/resources/
├── META-INF/admingui/
│   └── console-config.xml
├── fish/payara/admingui/microprofile/  # MicroProfile pages
│   ├── config/                          # Config pages
│   ├── health/                          # Health pages
│   ├── metrics/                         # Metrics pages
│   ├── openapi/                         # OpenAPI pages
│   └── fault-tolerance/                 # Fault tolerance pages
└── org/                                 # JSF pages
```

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `appserver/payara-appserver-modules/microprofile/` - MicroProfile implementations
- `payara-console-extras` - Payara console extras
