# CLAUDE.md - EclipseLink Wrapper

This file provides guidance for working with the `persistence/eclipselink-wrapper` module - EclipseLink JPA provider wrapper.

## Module Overview

The eclipselink-wrapper module wraps the EclipseLink JPA provider for integration with Payara Server. This module contains no Java source code - it's a packaging module that bundles EclipseLink JARs.

**Purpose:** Provides EclipseLink as the default JPA provider for Payara Server.

## Build Commands

```bash
# Build eclipselink-wrapper module
mvn -DskipTests clean package -f appserver/persistence/eclipselink-wrapper/pom.xml
```

## Module Contents

This is a packaging-only module that:

1. **Bundles EclipseLink JARs** - Packages EclipseLink JPA provider
2. **No Java Sources** - Pure wrapper/configuration module
3. **Provider Registration** - Registers EclipseLink as JPA provider

## EclipseLink Integration

### Provider Registration

EclipseLink is registered as a JPA provider via:

```java
// META-INF/services/jakarta.persistence.spi.PersistenceProvider
// Points to: org.eclipse.persistence.jpa.PersistenceProvider
```

### GlassFish Integration

```
Payara JPA Container
        │
        ▼
[ProviderContainerContractInfo]
        │
        ├─→ ClassFileTransformer (weaving)
        ├─→ ClassLoader setup
        └─→ DataSource lookup
        │
        ▼
[EclipseLink JPA Provider]
        │
        ├─→ EntityManagerFactory creation
        ├─→ EntityManager lifecycle
        └─── Query execution
```

## EclipseLink-Specific Features

### Weaving Configuration

```properties
# Enable weaving (default in Payara)
eclipselink.weaving=true

# Weaving options
eclipselink.weaving.lazy=true
eclipselink.weaving.changetracking=true
eclipselink.weaving.fetchgroups=true
```

### DDL Generation

```properties
# Generate DDL scripts
eclipselink.ddl-generation=generate-tables
eclipselink.ddl-generation.output-mode=both
eclipselink.application-location=your.package
```

### Logging

```properties
# EclipseLink logging levels
eclipselink.logging.level=FINE
eclipselink.sql.level=FINE
eclipselink.session.level=FINE
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.persistence` | JPA API |
| `eclipselink` | EclipseLink JPA provider |

## Notes

- **Packaging Module** - No Java source code
- **Default Provider** - EclipseLink is Payara's default JPA provider
- **Weaving Support** - Integrated with GlassFish classloading
- **Transaction Integration** - JTA synchronization via JPA container
