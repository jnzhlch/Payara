# CLAUDE.md - Appserver Admin

This file provides guidance for working with the `appserver/admin` module - Payara's application server administration infrastructure.

## Module Overview

The appserver/admin module extends the nucleus/admin module with application server-specific administration commands, backup functionality, and CLI skinning.

## Sub-Modules

| Module | Purpose |
|--------|---------|
| `admin-core` | Application server admin core services |
| `cli` | App server CLI skinning (main entry point) |
| `cli-optional` | Optional CLI commands |
| `backup` | Domain backup and restore |
| `gf_template` | GlassFish admin templates |
| `gf_template_web` | Web admin templates |

## Build Commands

```bash
# Build all admin modules
mvn -DskipTests clean package -f appserver/admin/pom.xml

# Build specific module
mvn -DskipTests clean package -f appserver/admin/admin-core/pom.xml
mvn -DskipTests clean package -f appserver/admin/cli/pom.xml
mvn -DskipTests clean package -f appserver/admin/backup/pom.xml
```

## Architecture

### Admin Layer Architecture

```
┌────────────────────────────────────────────────────────────┐
│                   asadmin CLI                              │
│  (Main entry point: AsadminMain)                            │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│              CLI Skinning (appserver/cli)                   │
│  - Customizes nucleus CLI for app server                    │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│              App Server Admin Core                          │
│  - Extends nucleus admin with server-specific commands      │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                 Nucleus Admin                               │
│  (nucleus/admin/cli, nucleus/admin/config-api)              │
└────────────────────────────────────────────────────────────┘
```

## Key Components

### AsadminMain (CLI Entry Point)

Located in `appserver/admin/cli`:

```java
package org.glassfish.admin.cli;

public class AsadminMain {
    public static void main(String[] args) {
        // Main entry point for asadmin CLI
        // Delegates to nucleus/admin CLI with app server skinning
    }
}
```

### AdminContext

Located in `com.sun.enterprise.admin`:

Provides the administration context for application server operations.

### Backup Module

Located in `appserver/admin/backup`:

Domain backup and restore functionality:
- Backup domain configuration and applications
- Restore from backup
- Backup scheduling

## CLI Command Development

### Creating an Admin Command

Admin commands in appserver/admin extend the nucleus/admin infrastructure:

```java
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.annotations.Scoped;
import org.glassfish.hk2.api.PerLookup;

@Service
@Scoped(PerLookup.class)
public class MyAppServerCommand implements AdminCommand {

    @Override
    public void execute(AdminCommandContext context) {
        // Command implementation
    }
}
```

### Command Registration

Commands are automatically discovered via HK2 `@Service` annotation. The command name is derived from the class name (e.g., `MyAppServerCommand` → `my-app-server`).

### App Server Specific Commands

The appserver/admin module provides commands that are specific to the application server, such as:
- Application deployment commands (deploy, undeploy, list-applications)
- Resource management (create-jdbc-connection-pool, create-jms-resource)
- Domain management (backup-domain, restore-domain)
- Server instance management (start-instance, stop-instance)

## Integration with Nucleus Admin

### Dependency Hierarchy

```
appserver/admin
    ├── depends on: nucleus/admin
    ├── extends: AdminCommand interface
    ├── uses: Config API for configuration
    └── registers: HK2 services for commands
```

### Shared Infrastructure

- **Command Execution**: Uses nucleus/admin command execution framework
- **Configuration**: Uses nucleus/admin/config-api for domain.xml access
- **CLI Parser**: Uses nucleus/admin CLI parser
- **Output Formatting**: Uses nucleus/admin output formatting

## Package Structure

```
appserver/admin/
├── admin-core/
│   └── src/main/java/
│       └── com/sun/enterprise/admin/
│           └── AdminContext.java
├── cli/
│   └── src/main/java/
│       └── org/glassfish/admin/cli/
│           └── AsadminMain.java
├── cli-optional/
│   └── (optional commands)
├── backup/
│   └── (backup/restore functionality)
├── gf_template/
│   └── (GlassFish templates)
└── gf_template_web/
    └── (Web templates)
```

## Dependencies

Key dependencies:
- `nucleus/admin` - Core admin infrastructure
- `common-util` - Common utilities
- `admin-util` - Admin utilities

## Build Output

The appserver/admin module produces:
- `appserver-cli.jar` - Main CLI entry point
- Admin core classes for app server

## Running Commands

### Using asadmin

```bash
# From Payara installation
asadmin <command>

# Example commands
asadmin list-applications
asadmin deploy myapp.war
asadmin create-jdbc-connection-pool ...
```

### From Development Build

```bash
# After building
java -jar appserver/admin/cli/target/appserver-cli.jar <command>
```

## Related Modules

- `nucleus/admin` - Core admin infrastructure (CLI, config API, REST)
- `appserver/admingui` - Web-based admin console
- `appserver/deployment` - Deployment administration
