# CLAUDE.md - Nucleus Admin

This file provides guidance for working with the Nucleus Admin modules - the administration infrastructure of Payara.

## Module Overview

The admin module provides the administration framework including CLI (asadmin), REST API, configuration management, monitoring, and server lifecycle management.

## Sub-Modules

### CLI (`cli/`)
The `asadmin` command-line interface - the primary administrative tool.

**Key packages:**
- `com.sun.enterprise.admin.cli` - CLI framework and command parser
- `com.sun.enterprise.admin.cli.remote` - Remote command execution
- `org.glassfish.admin.cli` - Core admin commands
- See `cli/CLAUDE.md` for CLI command development patterns

### Config API (`config-api/`)
Configuration management and domain.xml handling.

**Key concepts:**
- **Config beans** - POJOs annotated with `@Configured` map to domain.xml
- **Domain.xml** - Central configuration file
- **ConfigListener** - Listen for configuration changes
- See `config-api/CLAUDE.md` for configuration bean patterns

### Monitor (`monitor/`)
Monitoring infrastructure (MPM - Monitoring and Performance Monitoring).

**Components:**
- Probe providers - Source of monitoring data
- MonitorableRegistry - Registry of monitorable objects
- Call flow monitoring - Request tracing
- See `monitor/CLAUDE.md` for monitoring implementation

### REST (`rest/`)
REST administration interface for web-based management.

**Components:**
- `rest-service/` - REST endpoint implementation
- `rest-client/` - REST client library
- `gf-restadmin-connector/` - Admin console connector
- See `rest/CLAUDE.md` for REST API details

### Server Management (`server-mgmt/`)
Server lifecycle, domain creation/deletion, process management.
- See `server-mgmt/CLAUDE.md` for domain and server management

### Util (`util/`)
Shared utilities for admin operations.
- Command model parsing
- Token replacement
- Name generation
- See `util/CLAUDE.md` for utility functions

## Build Commands

```bash
# Build all admin modules
mvn -DskipTests clean package -f nucleus/admin/pom.xml

# Build specific admin component
mvn -DskipTests clean package -f nucleus/admin/cli/pom.xml

# Build with tests
mvn clean package -f nucleus/admin/pom.xml
```

## Testing Admin Commands

```bash
# Start server with test domain
./asadmin start-domain

# Test your command
./asadmin your-command-name --param value

# View logs
tail -f glassfish/domains/domain1/logs/server.log
```

## Key Files

- `config-api` - Schema definitions in `src/main/resources/configschema/`
- `cli` - Command help in `.xsd` files
- `rest` - REST endpoint definitions

## Architecture Notes

1. **HK2-based** - Admin uses HK2 (not CDI) for DI
2. **Modular commands** - Each command is a separate HK2 service
3. **Remote execution** - Commands can run locally or remotely via REST
4. **Validation** - Uses `@Param` validation annotations
