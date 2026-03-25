# CLAUDE.md - Weld Integration Fragment

This file provides guidance for working with the `appserver/web/weld-integration-fragment` module - OSGi fragment bundle for Weld integration.

## Module Overview

The weld-integration-fragment module is an OSGi fragment bundle that extends the Weld OSGi bundle to export additional packages required by Weld-generated proxies and Payara's integration.

**Purpose:** Export Weld internal packages for proxy generation and CDI integration.

## Build Commands

```bash
# Build weld-integration-fragment module
mvn -DskipTests clean package -f appserver/web/weld-integration-fragment/pom.xml
```

## OSGi Fragment Bundle

### Fragment Host

```xml
<Fragment-Host>org.jboss.weld.osgi-bundle;bundle-version=${weld.version}</Fragment-Host>
```

This fragment extends the Weld OSGi bundle, allowing it to export additional packages without modifying the original Weld bundle.

### Exported Packages

```xml
<Export-Package>
    <!-- Proxy utilities -->
    org.jboss.weld.proxy.util,
    org.jboss.weld.bean.proxy.util,

    <!-- Context implementations -->
    org.jboss.weld.context.http,
    org.jboss.weld.contexts.bound,
    org.jboss.weld.contexts.cache,

    <!-- EE integration -->
    org.jboss.weld.bean.builtin.ee,

    <!-- Interceptor proxies -->
    org.jboss.weld.interceptor.proxy,
    org.jboss.weld.interceptor.util.proxy,

    <!-- Utilities -->
    org.jboss.weld.util.bean,
    org.jboss.weld.serialization,
    org.jboss.weld.injection.attributes,
    org.jboss.weld.util.collections,
    org.jboss.weld.annotated.slim,
    org.jboss.weld.annotated.slim.backed,
    org.jboss.weld.security,
    org.jboss.weld.annotated.enhanced,
    org.jboss.weld.resources,
    org.jboss.weld.bean.attributes,
    org.jboss.weld.contexts,
    org.jboss.weld.util.reflection,
    org.jboss.weld.executor,
    org.jboss.weld.exceptions
</Export-Package>
```

### Import Packages

```xml
<Import-Package>
    org.glassfish.weld,
    jakarta.ejb,
    org.glassfish.weld.services
</Import-Package>
```

## Architecture

```
Weld OSGi Bundle (org.jboss.weld.osgi-bundle)
       │
       ▼
[Weld Integration Fragment]
       │
       ├─→ Exports additional Weld internal packages
       ├─→ Enables proxy class generation
       ├─→ Provides context implementation access
       └─→ Integrates with GlassFish services
```

## Purpose of Exported Packages

### Proxy Generation

Weld generates proxy classes for:
- Normal scoped beans (@RequestScoped, @SessionScoped, etc.)
- Interceptors
- Decorators
- Client proxies

These proxies require access to internal Weld packages.

### Context Integration

Payara's CDI integration needs access to:
- `org.jboss.weld.context.http` - HTTP context implementation
- `org.jboss.weld.contexts.bound` - Bound context support
- `org.jboss.weld.contexts.cache` - Context caching

### EE Integration

- `org.jboss.weld.bean.builtin.ee` - EE built-in beans

### Interceptor Support

- `org.jboss.weld.interceptor.proxy` - Interceptor proxy generation
- `org.jboss.weld.interceptor.util.proxy` - Proxy utilities

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `weld-osgi-bundle` | Fragment host (Weld OSGi bundle) |
| `glassfish-weld` | Payara Weld integration |

## Notes

- **Fragment Bundle** - Extends Weld OSGi bundle
- **Proxy Support** - Enables Weld proxy generation
- **Internal Access** - Exports Weld internal packages
- **OSGi Only** - Used in OSGi/GlassFish module system
