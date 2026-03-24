# CLAUDE.md - Domain Template (Full Profile)

This file provides guidance for working with the `gf_template` module.

## Module Overview

The gf_template module provides the domain template configuration for creating new Payara Server domains with the full profile (complete Jakarta EE support).

## Build Commands

```bash
# Build gf_template
mvn -DskipTests clean package -f appserver/admin/gf_template/pom.xml
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
        ├── Add: appserver-specific domain.xml
        │
        └── Assemble: appserver-domain template
```

## Template Files

### Configuration Files

| File | Purpose |
|------|---------|
| `config/domain.xml` | Full profile domain configuration |
| `config/default-web.xml` | Default web.xml for web container |
| `config/glassfish-acc.xml` | Application client container configuration |
| `config/wss-server-config-1.0.xml` | WSS 1.0 configuration |
| `config/wss-server-config-2.0.xml` | WSS 2.0 configuration |

### Template Metadata

| File | Purpose |
|------|---------|
| `template-info.xml` | Template metadata (name, description, author) |
| `stringsubs.xml` | String substitution rules for domain creation |

## Template Information

```xml
<domain-template-info
    xmlns="http://xmlns.oracle.com/cie/glassfish/domain-template"
    version="4.0.0.0"
    type="Domain Template"
    name="Basic GlassFish Server Domain"
    author="Oracle Corporation"
    description="Create a basic GlassFish Server domain.">
</domain-template-info>
```

## Package Structure

```
gf_template/
├── src/main/
│   ├── assembly/
│   │   └── appserver-domain.xml    # Assembly descriptor
│   └── resources/
│       ├── config/
│       │   ├── domain.xml           # Domain configuration
│       │   ├── default-web.xml      # Web container defaults
│       │   ├── glassfish-acc.xml    # App client container
│       │   └── wss-server-config-*.xml
│       ├── stringsubs.xml           # String substitutions
│       └── template-info.xml        # Template metadata
```

## Domain Creation

When creating a domain, the template is used:

```bash
# Create domain using this template
asadmin create-domain --template appserver-domain [domain-name]

# The template provides:
# - Default configuration files
# - Server ports (HTTP: 8080, HTTPS: 8181, Admin: 4848)
# - Default JVM settings
# - Enabled services (ejb, web, jms, etc.)
```

## Assembly Descriptor

Located in `src/main/assembly/appserver-domain.xml`:

- Combines nucleus-domain template
- Adds appserver-specific configurations
- Excludes nucleus domain.xml (replaced with full profile version)

## Dependencies

- `nucleus-domain` - Base nucleus domain template

## Profile Differences

| Feature | Full Profile (gf_template) |
|---------|---------------------------|
| EJB | Full EJB container |
| JMS | OpenMQ JMS provider |
| CORBA | IIOP/ORB support |
| JCA | Connector architecture |
| JPA | EclipseLink with full features |
| JAX-WS | Metro web services |

## Related Modules

- `gf_template_web` - Web profile template (smaller footprint)
- `nucleus-domain` - Base nucleus template
