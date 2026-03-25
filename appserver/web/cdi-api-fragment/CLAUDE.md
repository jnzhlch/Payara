# CLAUDE.md - CDI API Fragment

This file provides guidance for working with the `appserver/web/cdi-api-fragment` module - CDI API service provider configuration fragment.

## Module Overview

The cdi-api-fragment module provides service provider configuration for CDI (Contexts and Dependency Injection). It registers the Payara-specific CDI provider (GlassFishWeldProvider) as the CDI implementation.

**Purpose:** Register Weld as the CDI provider via service loader mechanism.

## Build Commands

```bash
# Build cdi-api-fragment module
mvn -DskipTests clean package -f appserver/web/cdi-api-fragment/pom.xml
```

## Module Structure

```
cdi-api-fragment/
└── src/main/resources/
    └── META-INF/
        └── services/
            ├── jakarta.enterprise.inject.spi.CDIProvider
            └── jakarta.enterprise.inject.build.compatible.spi.BuildServices
```

## Service Provider Configuration

### CDI Provider

```
# META-INF/services/jakarta.enterprise.inject.spi.CDIProvider
org.glassfish.weld.GlassFishWeldProvider
```

This registers GlassFish's Weld-based CDI provider as the implementation of `jakarta.enterprise.inject.spi.CDIProvider`.

### Build Services

```
# META-INF/services/jakarta.enterprise.inject.build.compatible.spi.BuildServices
org.jboss.weld.environment.deployment.build.compatible.spi.BuildServicesImpl
```

This registers Weld's build services for the CDI Build Compatible API.

## Architecture

```
Application
       │
       ├─→ CDIProvider.getCDI()
       │
       ▼
[ServiceLoader]
       │
       ├─→ Loads: org.glassfish.weld.GlassFishWeldProvider
       │
       ▼
[GlassFishWeldProvider]
       │
       ├─→ Creates CDI instance
       ├─→ Accesses BeanManager
       └─→ Initializes Weld container
       │
       ▼
[Weld CDI Container]
```

## CDI API Usage

### Programmatic CDI Access

```java
// Get CDI instance via CDIProvider
CDI<Object> cdi = CDI.current();

// Get BeanManager
BeanManager beanManager = cdi.getBeanManager();

// Look up bean programmatically
Bean<MyService> bean = (Bean<MyService>) beanManager.getBeans(
    MyService.class,
    new AnnotationLiteral<Default>() {}
).iterator().next();

CreationalContext<MyService> ctx = beanManager.createCreationalContext(bean);
MyService service = (MyService) beanManager.getReference(
    bean,
    MyService.class,
    ctx
);
```

### CDI in Java SE

```java
public class Main {
    public static void main(String[] args) {
        // Initialize CDI (via Weld provider)
        SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        try (SeContainer container = initializer.initialize()) {

            // Use CDI
            MyService service = container.select(MyService.class).get();
            service.doWork();
        }
    }
}
```

## GlassFishWeldProvider

The provider implementation is located in the weld-integration module:

```java
package org.glassfish.weld;

public class GlassFishWeldProvider implements CDIProvider {

    @Override
    public CDI<Object> getCDI() {
        // Return Weld-based CDI implementation
        return WeldCDIProvider.getCDI();
    }
}
```

## Integration Points

### Weld Integration Module

```
cdi-api-fragment (registers provider)
       │
       ▼
weld-integration (implements provider)
       │
       ├─→ GlassFishWeldProvider
       ├─→ WeldDeployer
       └─→ WeldBootstrapper
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.enterprise.cdi-api` | CDI API |
| `weld-integration` | Provider implementation |

## Notes

- **Service Provider** - Registers Weld as CDI implementation
- **ServiceLoader** - Uses Java ServiceLoader mechanism
- **CDI 4.0** - Jakarta EE 10 CDI specification
- **No Java Code** - Only service provider configuration files
