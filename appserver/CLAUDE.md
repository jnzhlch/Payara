# CLAUDE.md - Appserver

This file provides guidance for working with the Appserver module - the Jakarta EE server implementation.

## Module Overview

Appserver builds on Nucleus to provide a complete Jakarta EE (Java EE) application server including web container, EJB, JMS, JPA, web services, and admin console.

## Key Components

### Web Container (`web/`)
- **web-core** - Grizzly-based web container (Servlet, JSP, JSF)
- **weld-integration** - CDI (Weld) integration
- **gf-weld-connector** - GlassFish-Weld bridge
- **war-util** - WAR deployment utilities
- **web-ha** - High availability for web sessions

### EJB Container (`ejb/`)
- **ejb-container** - Core EJB container
- **ejb-full-container** - Full profile EJB support
- **ejb-timer-service-app** - EJB timer service
- **ejb-http-remoting** - HTTP-based EJB remoting

### Deployment (`deployment/`)
- **dol** - Deployment Object Library (application metadata)
- **javaee-core** - Core Java EE deployment
- **javaee-full** - Full profile Java EE deployment
- **client** - App client container

### Persistence (`persistence/`)
- **ejb-timer-databases** - Database-backed EJB timers
- **persistence-ejb-container** - JPA integration with EJB

### JMS (`jms/`)
- OpenMQ JMS provider integration

### Web Services (`webservices/`)
- JAX-WS (Metro) and JAX-RS (Jersey) support

### Extras (`extras/`)
- **payara-micro** - Microprofile uber-jar distribution
- **embedded** - Embedded Payara distributions
- **docker-images** - Docker image builds

### Admin Console (`admingui/`)
- **common** - Shared console components
- **core** - Core console infrastructure
- **console plugins** - Feature-specific admin UI plugins

### Payara Appserver Modules (`payara-appserver-modules/`)
- **microprofile/** - MicroProfile implementations (Config, Health, Metrics, etc.)
- **jmx-monitoring-service** - JMX-based monitoring
- **payara-rest-endpoints** - Custom REST endpoints
- **hazelcast-ejb-timer** - Hazelcast-backed EJB timers
- **hazelcast-eclipselink-coordination** - EclipseLink 2nd-level cache coordination

## Build Commands

```bash
# Build entire appserver
mvn -DskipTests clean package -f appserver/pom.xml

# Build specific component
mvn -DskipTests clean package -f appserver/<component>/pom.xml

# Build with specific profile
mvn -DskipTests clean package -f appserver/pom.xml -PFullProfile
mvn -DskipTests clean package -f appserver/pom.xml -PWebProfile
```

## Architecture Patterns

### Deployment Architecture
- **DOL (Deployment Object Library)** - Parses deployment descriptors
- **Deployers** - Handle application type-specific deployment
- **Loaders** - ClassLoaders for applications

### Integration Points
- Web container integrates CDI (Weld) via connectors
- EJB container integrates with transaction manager and security
- JPA uses EclipseLink with coordination via Hazelcast

### Feature Sets
Appserver supports multiple profiles:
- **Web Profile** - Web + limited Java EE (lighter)
- **Full Profile** - Complete Java EE (heavier)
- Controlled via `featuresets/` module

## Distributions

Appserver produces these distributions:
- `payara.zip` - Full Payara Server
- `payara-web.zip` - Web Profile only
- `payara-micro.jar` - Uber-jar microservice runtime
