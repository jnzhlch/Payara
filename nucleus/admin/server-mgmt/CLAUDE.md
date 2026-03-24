# CLAUDE.md - Admin Server Management

This file provides guidance for working with the Server Management module - server lifecycle, domain management, and process control.

## Module Overview

The Server Management module handles server lifecycle operations including domain creation/deletion, server startup/shutdown, process management, and installation management.

## Key Components

### Domain Management

**Domain operations:**
- `create-domain` - Create new domain
- `delete-domain` - Delete existing domain
- `list-domains` - List all domains
- Domain templates and configuration

### Server Lifecycle

**Server operations:**
- `start-domain` - Start a domain
- `stop-domain` - Stop a domain
- `restart-domain` - Restart a domain
- Status checking

### Process Management

**Process control:**
- Process spawning
- PID file management
- Watchdog functionality
- Detached processes

## Build Commands

```bash
# Build server-mgmt module
mvn -DskipTests clean package -f nucleus/admin/server-mgmt/pom.xml
```

## Domain Management Commands

```bash
# Create a new domain
./asadmin create-domain --user admin --portbase 4800 my-domain

# Create domain with custom admin port
./asadmin create-domain --adminport 4848 my-domain

# Delete a domain
./asadmin delete-domain my-domain

# List all domains
./asadmin list-domains

# Start domain
./asadmin start-domain my-domain

# Stop domain
./asadmin stop-domain my-domain

# Restart domain
./asadmin restart-domain my-domain
```

## Domain Structure

```
glassfish/
└── domains/
    └── domain1/
        ├── config/
        │   ├── domain.xml          # Domain configuration
        │   ├── logging.properties  # Logging config
        │   └── ...
        ├── docroot/
        ├── logs/
        │   ├── server.log
        │   └── ...
        ├── applications/
        ├── generated/
        └── lib/
```

## Domain Templates

Domain creation uses templates:
- `appserver-domain.jar` - Default full profile template
- Located in `install-dir/glassfish/common/templates/gf/`

## Key Commands

### Create Domain Options

```bash
# Minimal domain
./asadmin create-domain --nopassword my-domain

# With specific ports
./asadmin create-domain \
  --adminport 4848 \
  --instanceport 8080 \
  --jmsport 7676 \
  my-domain

# With custom properties
./asadmin create-domain \
  --user admin \
  --passwordfile /path/to/password.txt \
  --property http.listeners=5 \
  my-domain
```

### Start Domain Options

```bash
# Start with verbose output
./asadmin start-domain --verbose

# Start in debug mode (port 9009)
./asadmin start-domain --debug

# Start specific domain
./asadmin start-domain my-domain
```

## Process Management

### PID Files

Domain PID files: `domains/<domain>/config/pid`

### Watchdog

The server can automatically restart if it crashes:
```bash
./asadmin create-domain --savemasterpassword=true my-domain
```

## Installation Management

```bash
# Get installation information
./asadmin version

# Show installation details
./asadmin version --verbose

# List installed components
./asadmin list-components
```

## Configuration Files

Key domain configuration files:
- `domain.xml` - Main domain configuration
- `logging.properties` - Logging configuration
- `login.conf` - JAAS login configuration
- `keyfile` - User authentication
- `server.policy` - Security policy
- `pid` - Process ID file

## Environment Variables

Domain uses these environment variables:
- `AS_ADMIN_USER` - Default admin username
- `AS_ADMIN_PASSWORDFILE` - Path to password file
- `JAVA_HOME` - Java installation directory

## Password Management

```bash
# Create domain with password file
./asadmin create-domain --passwordfile passwords.txt my-domain

# Change admin password
./asadmin change-admin-password

# Enable master password
./asadmin enable-secure-admin
```

## Domain Backup

```bash
# Backup domain configuration
./asadmin backup-domain --filename backup.zip domain1

# Restore from backup
./asadmin restore-domain --filename backup.zip domain1
```

## Architecture Notes

1. **Template-based** - Domains created from templates
2. **Process isolation** - Each domain runs in separate JVM
3. **Config driven** - All settings in domain.xml
4. **Hot deploy** - Configuration changes often don't require restart
5. **Multi-domain** - Single installation can host multiple domains

## Key Packages

- `com.sun.enterprise.admin.servermgmt` - Server management APIs
- `com.sun.enterprise.admin.servermgmt.domain` - Domain management
- `com.sun.enterprise.admin.servermgmt.ports` - Port management

## Troubleshooting

### Domain won't start
```bash
# Check logs
tail -f glassfish/domains/domain1/logs/server.log

# Check port availability
netstat -an | grep 4848

# Verify Java version
java -version
```

### Domain won't stop
```bash
# Force stop
./asadmin stop-domain --force

# Kill process manually
kill $(cat glassfish/domains/domain1/config/pid)
```
