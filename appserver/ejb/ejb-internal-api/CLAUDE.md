# CLAUDE.md - EJB Internal API

This file provides guidance for working with the `ejb-internal-api` module - Internal EJB container APIs.

## Module Overview

The ejb-internal-api module defines internal interfaces and SPIs used by EJB container implementations and other Payara modules that integrate with EJB.

## Build Commands

```bash
# Build ejb-internal-api module
mvn -DskipTests clean package -f appserver/ejb/ejb-internal-api/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Name**: "EJB container Internal API"

## Key Interfaces

### EjbContainerServices

```java
public interface EjbContainerServices {
    // EJB invocation
    <T> T getEjb(Class<T> ejbClass, Class<?>[] intfClasses);

    // Timer service
    TimerService getTimerService();

    // Naming
    Object lookup(String name);
}
```

### Container Interceptor SPI

```java
public interface EjbContainerInterceptor {
    // Interceptor points
    void aroundConstruct(ConstructorInvocationContext ctx);
    Object aroundInvoke(InvocationContext ctx);
    void preDestroy(InvocationContext ctx);

    // Priority ordering
    int getPriority();
}
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | Public APIs |
| `deployment-common` | Deployment integration |
| `connectors-internal-api` | JCA integration |
| `config-api` | Configuration |
| `jakarta.interceptor-api` | Interceptor API |

## Related Modules

- `ejb-container` - Main consumer
- `ejb-opentracing` | OpenTracing interceptor
