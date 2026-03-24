# CLAUDE.md - Web Plugin

This file provides guidance for working with the `web` module - web container console plugin.

## Module Overview

The web plugin provides console pages for managing the web container, including servlet, JSP, and web application configuration.

## Build Commands

```bash
# Build web plugin
mvn -DskipTests clean package -f appserver/admingui/web/pom.xml
```

## Key Components

### Console Provider

Located in `org.glassfish.web.admingui`:

```java
@Service
public class WebConsolePlugin implements ConsoleProvider {
    @Override
    public URL getConfiguration() {
        // Uses default console-config.xml location
        return null;
    }
}
```

### Handlers

Located in `org.glassfish.web.admingui.handlers`:

- `WebHandlers` - Web container specific handlers

## Resources Structure

```
src/main/resources/
├── META-INF/
│   └── admingui/
│       └── console-config.xml    # Plugin integration points
├── apps/                         # Application pages
├── configuration/                # Configuration pages
├── grizzly/                      # Grizzly HTTP/IIOP config
├── images/                       # Plugin images
├── org/                          # JSF pages
└── serverPageNode.jsf            # Server page node
```

## Integration Points

The web plugin defines integration points for:
- Web container configuration
- HTTP listener management
- Virtual server settings
- Session configuration
- JSP settings
- Access logging

## Package Structure

```
org.glassfish.web.admingui/
├── WebConsolePlugin.java         # Plugin provider
└── handlers/
    └── WebHandlers.java          # Web container handlers
```

## Creating Similar Plugins

To create a similar console plugin:

1. **Create plugin module structure**:
   ```
   my-plugin/
   ├── src/main/java/
   │   └── org/example/myplugin/
   │       ├── MyConsolePlugin.java
   │       └── handlers/
   │           └── MyHandlers.java
   └── src/main/resources/
       └── META-INF/
           └── admingui/
               └── console-config.xml
   ```

2. **Create ConsoleProvider**:
   ```java
   @Service
   public class MyConsolePlugin implements ConsoleProvider {
   }
   ```

3. **Define integration points** in `console-config.xml`

4. **Create handlers** for JSF page backing beans

5. **Create JSF pages** for UI

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `monitoring-core` - Monitoring support
- `web-gui-plugin-common` - Web GUI common utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `common` - Shared handlers and utilities
- `plugin-service` - Plugin discovery
- `core` - Core console infrastructure
