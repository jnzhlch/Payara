# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Payara uses Maven for building. Requires JDK 21.

```bash
# Full build (all distributions)
mvn -DskipTests clean package

# Build with tests
mvn clean package

# Quick build (skips ML distributions, faster for development)
mvn -DskipTests clean package -PQuickBuild

# Build with Embedded support
mvn -DskipTests clean package -PBuildEmbedded

# Build specific module only (faster for single module changes)
mvn -DskipTests clean package -f <module-path>/pom.xml
```

**Output locations:**
- Payara Server: `appserver/distributions/payara/target/payara.zip`
- Payara Micro: `appserver/extras/payara-micro/payara-micro-distribution/target/payara-micro.jar`
- Staged server: `appserver/distributions/payara/target/stage`

## Testing

```bash
# Run quicklook tests
mvn clean test -Pall -Dglassfish.home=<path-to-payara> \
    -f appserver/tests/quicklook/pom.xml

# Run specific test class
mvn test -Dtest=<ClassName> -f <module>/pom.xml

# Run functional tests
mvn clean verify -f appserver/tests/functional/<test-suite>
```

**Test directories:**
- `appserver/tests/quicklook/` - Quick smoke tests
- `appserver/tests/functional/` - Comprehensive functional tests
- `appserver/tests/payara-samples/` - Sample application tests
- `nucleus/tests/` - Nucleus component tests

## Architecture

Payara is a Jakarta EE (Java EE) application server built on three main modules:

### Top-Level Modules
- **nucleus/** - Core kernel and infrastructure (HK2, OSGi, Grizzly, admin CLI)
  - See `nucleus/CLAUDE.md` for Nucleus-specific guidance
- **appserver/** - Full Jakarta EE server (web, ejb, jms, jpa, webservices, etc.)
  - See `appserver/CLAUDE.md` for Appserver-specific guidance
- **api/** - Public API definitions and BOM (Bill of Materials)

### Key Directories

**Nucleus (`nucleus/`):**
- `admin/` - Administration infrastructure (CLI, monitoring)
- `core/` - Kernel components (HK2 injection, OSGi platform)
- `grizzly/` - HTTP/networking layer
- `cluster/` - Clustering and distributed caching
- `payara-modules/` - Payara-specific core features (healthcheck, notification, tracing)

**Appserver (`appserver/`):**
- `web/` - Web container (Servlet, JSP, JSF, WebSocket)
- `ejb/` - EJB container
- `jms/` - JMS provider (OpenMQ)
- `persistence/` - JPA (EclipseLink)
- `webservices/` - JAX-WS / JAX-RS
- `payara-appserver-modules/` - Payara-specific application features (MicroProfile, monitoring)
- `admingui/` - Admin console (JSF web UI)
- `extras/` - Payara Micro, Embedded, Docker images
- `deployment/` - Application deployment infrastructure (DOL - Deployment Object Library)

**Key Payara Features:**
- MicroProfile support (Config, Health, Metrics, OpenAPI, etc.)
- Hazelcast-based clustering
- Health check system
- Request tracing
- Notification system (JMS, CDI events)

## Module-Specific Build

For focused development in a specific module:

```bash
# Build only Nucleus
mvn -DskipTests clean package -f nucleus/pom.xml

# Build only Appserver
mvn -DskipTests clean package -f appserver/pom.xml

# Build specific component (e.g., web container)
mvn -DskipTests clean package -f appserver/web/pom.xml
```

## Module Development Workflow

For single module development, you don't need a full rebuild:

```bash
# Build only the changed module
mvn -DskipTests clean package -f <module-path>/pom.xml

# Copy the jar to a running Payara installation
cp <module>/target/<jar-name>.jar <payara-install>/glassfish/modules/

# Restart the server to see changes
```

## Debugging

```bash
# Start Payara in debug mode (port 9009)
./asadmin start-domain --verbose --debug

# Attach debugger from IDE to localhost:9009
```

## Code Style

- Java code uses standard Java conventions
- Add `Portions Copyright [year] Payara Foundation and/or its affiliates` to modified files
- Use CDI for dependency injection (HK2 in core components)

## Maven Profiles

- `DefaultBuild` - Full build (all distributions)
- `QuickBuild` - Skip ML distributions (faster)
- `BuildEmbedded` - Include Embedded All and Web
- `BuildDockerImages` - Build Docker images
- `source` - Generate source jar
- `javadoc` - Generate Javadoc
- `gpgsign` - GPG sign artifacts
