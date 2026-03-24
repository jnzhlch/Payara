# CLAUDE.md - CLI Optional Commands

This file provides guidance for working with the `cli-optional` module.

## Module Overview

The cli-optional module provides optional asadmin commands that are not part of the core CLI, including backup/restore commands and database management commands.

## Build Commands

```bash
# Build cli-optional
mvn -DskipTests clean package -f appserver/admin/cli-optional/pom.xml
```

## Available Commands

### Backup and Restore Commands

| Command | Purpose |
|---------|---------|
| `backup-domain` | Create a domain backup |
| `restore-domain` | Restore a domain from backup |
| `list-backups` | List available backups |

### Database Commands

| Command | Purpose |
|---------|---------|
| `start-database` | Start the Java DB database |
| `stop-database` | Stop the Java DB database |

## Command Details

### backup-domain

```bash
asadmin backup-domain [--domaindir domaindir] [--backupdir backupdir]
    [--description description] [--force] [--recyclelimit limit]
    [domain_name]
```

**Options:**
- `--domaindir` - Directory containing domains (default: domains directory)
- `--backupdir` - Directory for backup files (default: domains/backups)
- `--description` - Backup description
- `--force` - Force backup even if domain is running
- `--recyclelimit` - Number of backups to keep (older ones deleted)

**Implementation:**
- `BackupDomainCommand` - CLI command wrapper
- Delegates to `BackupManager` from backup module

### restore-domain

```bash
asadmin restore-domain [--domaindir domaindir] [--backupdir backupdir]
    [--filename backupfile] [--force] [domain_name]
```

**Options:**
- `--domaindir` - Directory containing domains
- `--backupdir` - Directory containing backup files
- `--filename` - Specific backup file to restore
- `--force` - Force restore even if domain is running

**Implementation:**
- `RestoreDomainCommand` - CLI command wrapper
- Delegates to `RestoreManager` from backup module

### list-backups

```bash
asadmin list-backups [--domaindir domaindir] [--backupdir backupdir]
    [domain_name]
```

**Implementation:**
- `ListBackupsCommand` - Lists available backup files
- Uses `ListManager` from backup module

### Database Commands

```bash
# Start Java DB
asadmin start-database [--dbhost host] [--dbport port]

# Stop Java DB
asadmin stop-database [--dbhost host] [--dbport port]
```

**Implementation:**
- `StartDatabaseCommand` / `StopDatabaseCommand`
- Uses `DBManager` for Java DB management
- `H2Manager` for H2 database support

## Package Structure

```
com.sun.enterprise.admin.cli.optional/
├── BackupCommands.java           # Base class for backup commands
├── BackupDomainCommand.java      # backup-domain command
├── RestoreDomainCommand.java     # restore-domain command
├── ListBackupsCommand.java       # list-backups command
├── DatabaseCommand.java          # Base for database commands
├── StartDatabaseCommand.java     # start-database command
├── StopDatabaseCommand.java      # stop-database command
├── DBManager.java                # Database management (Java DB)
├── H2Manager.java                # H2 database management
├── DBControl.java                # Database control utilities
└── H2Control.java                # H2 control utilities
```

## Dependencies

- `backup` module - Backup/restore implementation
- `admin-cli` - Core CLI infrastructure

## Backup File Format

Backups are stored as ZIP files:
```
{domain_name}_backup_{timestamp}.zip
```

Example: `domain1_backup_20250324123456.zip`

## Integration Points

- `backup` module - Core backup/restore functionality
- `nucleus/admin/cli` - Command infrastructure
