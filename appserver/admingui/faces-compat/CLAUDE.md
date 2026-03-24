# CLAUDE.md - Faces Compat

This file provides guidance for working with the `faces-compat` module - JSF compatibility layer.

## Module Overview

The faces-compat module provides compatibility between different JSF versions and custom injection providers for the admin console.

## Build Commands

```bash
# Build faces-compat
mvn -DskipTests clean package -f appserver/admingui/faces-compat/pom.xml
```

## Key Components

### FacesInitializer2

Located in `com.sun.faces.config`:

Custom Faces initializer for compatibility:
- Handles JSF initialization in the admin console context
- Provides compatibility between JSF versions

### GlassFishInjectionProvider

Located in `fish.payara.server.internal.admingui`:

Custom injection provider:
- Integrates HK2 injection with JSF
- Provides dependency injection for JSF managed beans

## Package Structure

```
com.sun.faces.config/
└── FacesInitializer2.java      # JSF initializer

fish.payara.server.internal.admingui/
└── GlassFishInjectionProvider.java  # HK2 injection provider
```

## Dependencies

- `jakarta.enterprise` - CDI API
- `jakarta.security.enterprise` - Jakarta Security API

## Integration Points

Used by:
- All admin console modules that use JSF
- Provides HK2 injection integration for JSF beans

## Related Modules

- `common` - Console utilities that depend on JSF
- `core` - Core console infrastructure
