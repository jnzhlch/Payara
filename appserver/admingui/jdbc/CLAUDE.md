# CLAUDE.md - JDBC Plugin

This file provides guidance for working with the `jdbc` module - JDBC resources console plugin.

## Module Overview

The jdbc plugin provides console pages for managing JDBC connection pools, JDBC resources, and database connectivity.

## Build Commands

```bash
# Build jdbc plugin
mvn -DskipTests clean package -f appserver/admingui/jdbc/pom.xml
```

## Key Components

### Console Provider

Located in `org.glassfish.jdbc.admingui`:

```java
@Service
public class JdbcConsolePlugin implements ConsoleProvider {
    @Override
    public URL getConfiguration() {
        // Uses default console-config.xml location
        return null;
    }
}
```

## Integration Points

The jdbc plugin defines integration points for:
- JDBC connection pool management
- JDBC resource configuration
- Pool settings (min/max pool size, validation, etc.)
- Connection properties
- DataSource configuration

## Package Structure

```
org.glassfish.jdbc.admingui/
└── JdbcConsolePlugin.java         # Plugin provider
```

## Resources

```
src/main/resources/
├── META-INF/admingui/
│   └── console-config.xml
├── jdbc/                          # JDBC pages
│   ├── connectionPool/
│   ├── dataSource/
│   └── resources/
└── org/                           # JSF pages
```

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `common` - Shared handlers (ResourceHandlers for JDBC)
- `jca` - JCA/connector plugin (related resources)
