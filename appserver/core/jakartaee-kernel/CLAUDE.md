# CLAUDE.md - Jakarta EE Kernel

This file provides guidance for working with the `jakartaee-kernel` module - Jakarta EE server initialization services.

## Module Overview

The jakartaee-kernel module provides core initialization services for Jakarta EE server startup, including MEJB lazy initialization and web container startup detection.

## Build Commands

```bash
# Build jakartaee-kernel
mvn -DskipTests clean package -f appserver/core/jakartaee-kernel/pom.xml
```

## Core Services

### MEJBService

Lazy initialization of Management EJB:

```java
@Service
@RunLevel(InitRunLevel.VAL)
public class MEJBService implements PostConstruct {
    @Inject Globals globals;
    @Inject ServiceLocator habitat;

    public void postConstruct() {
        // Register MEJB naming proxies
        // MEJB deployed on first lookup (lazy)
    }
}
```

**Purpose:**
- MEJB provides programmatic management of Jakarta EE components
- Delayed deployment reduces server startup time
- MEJB archive located at `lib/install/applications/mejb.jar`

**JNDI Names:**

| Name | Type | Description |
|------|------|-------------|
| `ejb/mgmt/MEJB` | Non-portable | Legacy GlassFish name |
| `java:global/mejb/MEJBBean` | Portable | Jakarta EE global (short) |
| `java:global/mejb/MEJBBean!org.glassfish.admin.mejb.MEJBHome` | Portable | Jakarta EE global (long) |

### MEJBNamingObjectProxy

Handles lazy MEJB deployment:

```java
public class MEJBNamingObjectProxy implements NamingObjectProxy {
    public Object create(Context ic) throws NamingException {
        // 1. Unpublish temporary JNDI names
        unpublishJndiNames();

        // 2. Deploy MEJB application
        deployMEJB();

        // 3. Return MEJB Home
        return ic.lookup("ejb/mgmt/MEJB");
    }
}
```

**Deployment Flow:**
1. Application performs JNDI lookup for MEJB
2. NamingObjectProxy intercepts the lookup
3. MEJB is deployed from `mejb.jar`
4. Actual MEJB Home is returned
5. Subsequent lookups return deployed instance

### WebContainerStarter

Conditional web container startup:

```java
@Service
@RunLevel(StartupRunLevel.VAL)
public class WebContainerStarter implements ConfigListener {
    @Inject private ModulesRegistry modulesRegistry;

    // Checks config and starts web if needed
    public void postConstruct();

    // Listens for config changes
    public UnprocessedChangeEvents changed(EventTypes... types);
}
```

**Startup Triggers:**
- HTTP/HTTPS listeners defined
- Access logging enabled
- Virtual server with web components
- Configuration changes in domain.xml

**Config Listener Pattern:**
```
domain.xml Change
       │
ConfigListener.changed()
       │
[Web-related change?]
       │ Yes
[Web container started?]
       │ No
    Start Web Container
```

## Container Starter Framework

Uses `ContainerStarter` infrastructure from nucleus:

```java
ContainerStarter starter = new ContainerStarter(
    modulesRegistry, "web", "WebContainer");
starter.startContainer();
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | Public APIs |
| `internal-api` | Internal server APIs |
| `config-api` | Configuration beans |
| `hk2-core` | Dependency injection |
| `kernel` | Nucleus kernel container starter |

## Package Structure

```
org.glassfish.kernel.jakartaee/
├── MEJBService.java                   # MEJB initialization service
├── MEJBNamingObjectProxy.java         # Lazy MEJB deployment proxy
└── WebContainerStarter.java           # Web container startup
```

## Related Modules

- `nucleus/core/kernel` - ContainerStarter framework
- `appserver/deployment` - Deployment infrastructure
- `appserver/web` - Web container
- `appserver/ejb/ejb-container` - MEJB app
