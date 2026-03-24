# CLAUDE.md - API Exporter

This file provides guidance for working with the `api-exporter` module - OSGi API export infrastructure.

## Module Overview

The `api-exporter` module handles exporting internal Payara APIs as OSGi services. This enables:

- Dynamic service discovery across OSGi bundles
- Decoupling of modules via OSGi service registry
- Runtime binding of implementations to interfaces

## Build Commands

```bash
# Build api-exporter module
mvn -DskipTests clean package -f nucleus/core/api-exporter/pom.xml

# Build with tests
mvn clean package -f nucleus/core/api-exporter/pom.xml
```

## Architecture

### Service Export

```
Internal API
        │
        ├─→ APIExporter (HK2 service)
        │      └─→ Registers as OSGi service
        │
        └─→ OSGi Service Registry
               └─→ Available to all bundles
```

### Key Classes

| Class | Purpose |
|-------|---------|
| `APIExporter` | Interface for API export service |
| `APIExporterImpl` | Implementation of API exporter |

## Usage

### API Exporter Service

```java
// Get APIExporter service
APIExporter exporter = habitat.getService(APIExporter.class);

// Export an API as OSGi service
exporter.export(myApiInterface, myImplementation);
```

### OSGi Service Registration

```java
// Bundle context
BundleContext context = ...;

// Register service
ServiceRegistration registration = context.registerService(
    MyApi.class.getName(),
    new MyApiImpl(),
    null
);
```

## Integration

### With HK2

```java
// HK2 service exported to OSGi
@Service
public class MyServiceImpl implements MyService {

    // Automatically exported via APIExporter
}
```

### With OSGi

```java
// Consume OSGi service
BundleContext context = ...;
ServiceReference<MyService> ref = context.getServiceReference(MyService.class);
MyService service = context.getService(ref);
```

## Dependencies

**Runtime:**
- HK2 core
- OSGi core

**Depended on by:**
- kernel - Uses API exporter for service registration

## Related Modules

- **bootstrap** - OSGi framework startup
- **kernel** - Core kernel services
- **common/internal-api** - APIs being exported

## Notes

This is a small infrastructure module. Most interaction is automatic - HK2 services are automatically exported to OSGi service registry.
