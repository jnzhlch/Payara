# CLAUDE.md - Domain Template (Web Profile)

This file provides guidance for working with the `gf_template_web` module.

## Module Overview

The gf_template_web module provides the domain template configuration for creating new Payara Server domains with the web profile (lighter weight, web-focused Jakarta EE support).

## Build Commands

```bash
# Build gf_template_web
mvn -DskipTests clean package -f appserver/admin/gf_template_web/pom.xml
```

## Architecture

### Template Assembly Process

```
nucleus-domain (template)
        │
        ├── Unpack: nucleus template files
        │
        ├── Merge: logging.properties
        │
        ├── Add: appserver web-specific domain.xml
        │
        └── Assemble: appserver-domain-web template
```

## Template Files

### Configuration Files

| File | Purpose |
|------|---------|
| `config/domain.xml` | Web profile domain configuration |
| `config/default-web.xml` | Default web.xml for web container |
| `config/glassfish-acc.xml` | Application client container configuration |
| `config/wss-server-config-1.0.xml` | WSS 1.0 configuration |
| `config/wss-server-config-2.0.xml` | WSS 2.0 configuration |

### Template Metadata

| File | Purpose |
|------|---------|
| `template-info.xml` | Template metadata |
| `stringsubs.xml` | String substitution rules |

## Package Structure

```
gf_template_web/
├── src/main/
│   ├── assembly/
│   │   └── appserver-domain-web.xml  # Assembly descriptor
│   └── resources/
│       ├── config/
│       │   ├── domain.xml             # Web profile domain config
│       │   ├── default-web.xml        # Web container defaults
│       │   ├── glassfish-acc.xml      # App client container
│       │   └── wss-server-config-*.xml
│       ├── stringsubs.xml             # String substitutions
│       └── template-info.xml          # Template metadata
```

## Domain Creation

When creating a web profile domain:

```bash
# Create web profile domain
asadmin create-domain --template appserver-domain-web [domain-name]

# The template provides:
# - Web-focused configuration
# - Smaller memory footprint
# - Faster startup time
# - Essential web services only
```

## Assembly Descriptor

Located in `src/main/assembly/appserver-domain-web.xml`:

- Combines nucleus-domain template
- Adds web profile specific configurations
- Excludes nucleus domain.xml (replaced with web profile version)

## Dependencies

- `nucleus-domain` - Base nucleus domain template

## Profile Differences

| Feature | Full Profile (gf_template) | Web Profile (gf_template_web) |
|---------|---------------------------|-------------------------------|
| EJB | Full EJB container | EJB Lite only |
| JMS | OpenMQ JMS provider | Optional/embedded |
| CORBA | IIOP/ORB support | Not included |
| JCA | Connector architecture | Limited |
| JPA | EclipseLink full | EclipseLink basic |
| JAX-WS | Metro full | Metro basic |
| Memory footprint | Larger | Smaller |
| Startup time | Slower | Faster |

## When to Use Web Profile

Use the web profile template when:
- Building web applications (Servlet, JSP, JSF)
- Using RESTful services (JAX-RS)
- Need lightweight deployment
- Don't require full Jakarta EE features (CORBA, full EJB, etc.)

## Related Modules

- `gf_template` - Full profile template (complete Jakarta EE)
- `nucleus-domain` - Base nucleus template
