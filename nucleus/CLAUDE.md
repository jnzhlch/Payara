# CLAUDE.md - Nucleus

This file provides guidance for working with the Nucleus module - the core kernel and infrastructure of Payara.

## Module Overview

Nucleus is the foundation of Payara Server. It provides the kernel, injection framework, OSGi platform, admin CLI, monitoring, and core services that all other modules depend on.

## Key Components

### Core Infrastructure (`core/`)
- **bootstrap** - Server startup and initialization
- **kernel** - HK2 injection kernel, service registry
- **logging** - Logging infrastructure
- **context-propagation** - Thread context propagation for async operations
- See `core/CLAUDE.md` for detailed kernel and bootstrap guidance

### Administration (`admin/`)
- **cli** - `asadmin` command-line interface
- **config-api** - Configuration management and domain.xml handling
- **monitor** - Monitoring infrastructure (MPM, JMX)
- **rest** - REST administration interface
- **launcher** - Server launcher and process management
- See `admin/CLAUDE.md` for CLI command development and admin patterns

### Networking (`grizzly/`)
- HTTP/NIO framework based on Grizzly
- Connection handling and thread pools

### Clustering (`cluster/`)
- Hazelcast-based distributed caching
- Group management and discovery

### Payara Core Modules (`payara-modules/`)
- **healthcheck-core** - Health check framework
- **notification-core** - Notification system base
- **requesttracing-core** - Request tracing for performance analysis
- **hazelcast-bootstrap** - Hazelcast initialization
- **asadmin-recorder** - Records asadmin commands for replay
- **asadmin-audit** - Audit logging for admin operations
- See `payara-modules/CLAUDE.md` for Payara-specific features

## Build Commands

```bash
# Build entire nucleus
mvn -DskipTests clean package -f nucleus/pom.xml

# Build specific nucleus component
mvn -DskipTests clean package -f nucleus/<component>/pom.xml

# Build with tests
mvn clean package -f nucleus/pom.xml
```

### Sub-Module Builds

```bash
# Build admin modules only
mvn -DskipTests clean package -f nucleus/admin/pom.xml

# Build core modules only
mvn -DskipTests clean package -f nucleus/core/pom.xml

# Build packager (assembly)
mvn -DskipTests clean package -f nucleus/packager/pom.xml

# Build Payara modules only
mvn -DskipTests clean package -f nucleus/payara-modules/pom.xml
```

## Architecture Patterns

### HK2 Service Injection
Nucleus uses HK2 (not CDI) for dependency injection:
- Services are annotated with `@Service`
- Injection points use `@Inject`
- Use `@Configured` for configuration beans

### CLI Commands
Admin commands in `nucleus/admin/cli/`:
- Extend `AdminCommand` interface
- Use `@Service` and `@Scoped(PerLookup.class)`
- Execute via `asadmin <command-name>`

### Configuration
- Domain XML schema defined in `config-api`
- Config beans map to domain.xml elements
- Use `@Configured` annotation for POJO-based config

## Distribution

Nucleus produces `payara-minimal` distribution - the smallest runtime without Java EE specifications.

**Packager structure** (`packager/`):
- Assembly of modules into packages
- Distribution creation (zip files)
- See `packager/CLAUDE.md` for packaging details

## Module Structure

```
nucleus/
├── admin/           - Administration (CLI, config, REST, monitoring)
├── core/            - Kernel (HK2, bootstrap, logging)
├── cluster/         - Hazelcast clustering
├── common/          - Shared utilities
├── deployment/      - Deployment infrastructure
├── diagnostics/     - Diagnostics tools
├── distributions/   - Minimal distribution output
├── grizzly/         - HTTP/NIO layer
├── hk2/             - HK2 injection framework
├── osgi-platforms/  - OSGi framework (Felix)
├── packager/        - Package assembly
├── payara-modules/  - Payara-specific features
├── resources/       - Resources and templates
├── security/        - Security infrastructure
└── tests/           - Test suites
```
