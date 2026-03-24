# CLAUDE.md - Appserver Core

This file provides guidance for working with the `appserver/core` module - Jakarta EE kernel infrastructure.

## Module Overview

The core module provides Jakarta EE server initialization infrastructure, including MEJB lazy initialization, web container startup detection, and Jakarta EE API package exports.

## Build Commands

```bash
# Build entire core module
mvn -DskipTests clean package -f appserver/core/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/core/<submodule>/pom.xml
```

## Module Structure

| Submodule | Purpose |
|-----------|---------|
| `jakartaee-kernel` | Jakarta EE kernel services |
| `api-exporter-fragment` | Jakarta EE API package imports |

## Jakarta EE Kernel Services

### MEJBService

Lazy initialization of MEJB (Management EJB):

```java
@Service
@RunLevel(InitRunLevel.VAL)
public class MEJBService implements PostConstruct {
    // Registers MEJB naming proxy on server startup
    // MEJB is deployed on first lookup (lazy initialization)
}
```

**Purpose:**
- MEJB provides programmatic management access to Jakarta EE components
- Deployed as `mejb.jar` from `lib/install/applications/`
- Lazy deployment saves startup time

**JNDI Names:**
```
ejb/mgmt/MEJB                          # Non-portable (legacy)
java:global/mejb/MEJBBean            # Portable (short)
java:global/mejb/MEJBBean!...        # Portable (long)
```

### WebContainerStarter

Detects and starts web container when needed:

```java
@Service
@RunLevel(StartupRunLevel.VAL)
public class WebContainerStarter implements ConfigListener {
    // Checks if web container features are required
    // Starts web container if HTTP listeners configured
    // Monitors domain.xml configuration changes
}
```

**Triggers Web Container Start:**
- HTTP/HTTPS listeners configured in domain.xml
- Access logging enabled
- Virtual servers configured

### Container Starter Pattern

```
Server Startup
       │
┌──────▼─────────┐
│  MEJBService   │  Register MEJB naming proxy
└──────┬─────────┘
       │
┌──────▼─────────┐
│WebContainerStarter│  Check config, start web if needed
└─────────────────┘
```

## API Exporter Fragment

### Package Imports

The `api-exporter-fragment` module statically imports Jakarta EE APIs into the Common Class Loader:

| Category | Packages |
|----------|----------|
| **Jakarta EE Core** | jakarta.annotation, jakarta.el, jakarta.inject |
| **EJB** | jakarta.ejb.* |
| **JPA** | jakarta.persistence.* |
| **JMS** | jakarta.jms.* |
| **JCA** | jakarta.resource.* |
| **JTA** | jakarta.transaction.* |
| **Servlet** | jakarta.servlet.* |
| **JAX-RS** | jakarta.ws.rs.* |
| **JAXB** | jakarta.xml.bind.* |
| **JAX-WS** | jakarta.xml.ws.* |
| **Bean Validation** | jakarta.validation.* |
| **JSON-P** | jakarta.json.* |
| **Concurrency** | jakarta.enterprise.concurrent.* |
| **Interceptors** | jakarta.interceptor.* |
| **CDI** | jakarta.enterprise.* |
| **Faces** | jakarta.faces.* |
| **Mail** | jakarta.mail.* |
| **Management** | javax.management.*, javax.jmx.* |
| **CORBA** | org.omg.CORBA.*, org.omg.CosNaming.* |
| **XML Processing** | javax.xml.*, org.w3c.dom.*, org.xml.sax.* |
| **Security** | jakarta.authentication.*, jakarta.authorization.* |

### OSGi Fragment Configuration

```
Bundle-SymbolicName: GlassFish-Application-Common-Module-Fragment
Fragment-Host: GlassFish-Application-Common-Module
Import-Package: [extensive list of Jakarta EE packages]
resolution:=optional
```

**Purpose:**
- Attaches as fragment to api-exporter bundle
- Statically imports packages for Common Class Loader
- Optional resolution prevents startup failures
- Improves performance vs. dynamic imports

## Run Levels

Services are initialized at specific run levels:

| Service | Run Level | Description |
|---------|-----------|-------------|
| `MEJBService` | `InitRunLevel.VAL` | During server initialization |
| `WebContainerStarter` | `StartupRunLevel.VAL` | During container startup |

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | Public APIs |
| `internal-api` | Internal server APIs |
| `config-api` | Configuration beans |
| `hk2-core` | Dependency injection |
| `kernel` | Nucleus kernel |

## Package Structure

### jakartaee-kernel

```
org.glassfish.kernel.jakartaee/
├── MEJBService.java                   # MEJB lazy initialization
├── MEJBNamingObjectProxy.java         # MEJB naming proxy
└── WebContainerStarter.java           # Web container startup
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Server Startup                               │
│  - Init run level services start                              │
└─────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
┌────────▼────────┐  ┌───────▼────────┐  ┌───────▼────────┐
│  MEJBService   │  │WebContainerStarter│
│                 │  │                    │
│ Register MEJB   │  │ Check domain.xml   │
│ Naming Proxy    │  │ Start web if HTTP   │
└─────────────────┘  └────────────────────┘
```

## Integration Points

### MEJB Integration

```
Application Lookup
       │
InitialContext.lookup("ejb/mgmt/MEJB")
       │
MEJBNamingObjectProxy.create()
       │
Deploy mejb.jar
       │
Return MEJB Home
```

### Web Container Detection

```
domain.xml Configuration
       │
WebContainerStarter
       │
[HTTP/HTTPS listeners?]
       │ Yes
Start Web Container
```

## Related Modules

- `nucleus/core/kernel` - Nucleus kernel (parent)
- `appserver/deployment` - MEJB deployment
- `appserver/web` - Web container
