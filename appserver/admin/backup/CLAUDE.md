# CLAUDE.md - Backup and Restore

This file provides guidance for working with the `backup` module.

## Module Overview

The backup module provides domain backup and restore functionality for Payara Server. It creates ZIP archives of domain directories and can restore domains from these backups.

## Build Commands

```bash
# Build backup module
mvn -DskipTests clean package -f appserver/admin/backup/pom.xml
```

## Architecture

### Backup Flow

```
BackupDomainCommand (CLI)
        │
        ▼
BackupRequest
        │
        ▼
BackupManager
        │
        ├─→ Validate domain directory
        ├─→ Create backup directory
        ├─→ Generate backup filename
        ├─→ Create ZIP archive (ZipStorage)
        └─→ Apply recycle limit
```

### Restore Flow

```
RestoreDomainCommand (CLI)
        │
        ▼
BackupRequest
        │
        ▼
RestoreManager
        │
        ├─→ Validate backup file
        ├─→ Extract ZIP archive
        └─→ Verify restoration
```

## Key Components

### BackupManager

Located in `com.sun.enterprise.backup`:

```java
public class BackupManager extends BackupRestoreManager {
    public BackupManager(BackupRequest req) throws BackupException;
    public String backup() throws BackupException;
}
```

**Responsibilities:**
- Validates domain directory
- Creates backup directory
- Generates backup filename
- Creates ZIP via `ZipStorage`
- Manages backup recycling (old backup cleanup)

### RestoreManager

Located in `com.sun.enterprise.backup`:

```java
public class RestoreManager extends BackupRestoreManager {
    public RestoreManager(BackupRequest req) throws BackupException;
    public String restore() throws BackupException;
}
```

**Responsibilities:**
- Validates backup file
- Extracts ZIP archive
- Restores domain directory

### BackupRequest

Configuration object for backup/restore operations:

```java
public class BackupRequest {
    File domainDir;           // Domain directory
    String domainName;         // Domain name
    File backupDir;            // Backup directory
    File backupFile;           // Backup file (restore only)
    String description;        // Backup description
    long timestamp;            // Backup timestamp
    boolean force;             // Force operation
    int recycleLimit;          // Number of backups to keep
    boolean verbose;           // Verbose output
    boolean terse;             // Terse output
}
```

### ZipStorage

Handles ZIP archive creation and extraction:

```java
public class ZipStorage {
    public ZipStorage(BackupRequest request) throws BackupException;
    public void store() throws BackupException;
    public void restore() throws BackupException;
}
```

### BackupFilenameManager

Manages backup filenames and recycling:

```java
public class BackupFilenameManager {
    public BackupFilenameManager(File backupDir, String domainName);
    public File next();                    // Generate next backup filename
    public String getCustomizedDescription();
    public List<File> getRecycleFiles(int limit);
}
```

### ListManager

Lists available backups:

```java
public class ListManager {
    public ListManager(BackupRequest request);
    public String list();  // Returns formatted list of backups
}
```

## Package Structure

```
com.sun.enterprise.backup/
├── BackupManager.java           # Main backup logic
├── RestoreManager.java          # Main restore logic
├── BackupRequest.java           # Configuration object
├── BackupException.java         # Backup exceptions
├── BackupWarningException.java  # Warning exceptions
├── ZipStorage.java              # ZIP archive handling
├── BackupFilenameManager.java   # Filename management
├── ListManager.java             # List backups
├── Status.java                  # Backup status tracking
├── Constants.java               # Constants
├── StringHelper.java            # String utilities
├── DirectoryFilter.java         # Directory filtering
├── ZipFilenameFilter.java       # ZIP file filtering
└── util/
    └── BackupUtils.java         # Backup utilities
```

## Backup File Naming

Backups are named with this pattern:
```
{domain_name}_backup_{timestamp}.zip
```

Example: `domain1_backup_20250324123456.zip`

## Backup Directory Structure

```
{domains_directory}/
└── backups/
    ├── {domain_name}_backup_*.zip
    └── {domain_name}_backup_status_*.txt
```

## Configuration

### Default Locations

| Item | Default Location |
|------|------------------|
| Domain directory | `{payara}/domains` |
| Backup directory | `{domains}/backups` |
| Recycle limit | No limit (all backups kept) |

### Environment Variables

None required. Paths are configured via command options.

## Backup Lifecycle

1. **Validation** - Check domain exists and is writable
2. **Preparation** - Create backup directory, generate filename
3. **Archive Creation** - ZIP the domain directory
4. **Recycling** - Delete old backups if recycle limit set
5. **Status** - Write status file

## Error Handling

| Exception | When Thrown |
|-----------|-------------|
| `BackupException` | Fatal backup errors |
| `BackupWarningException` | Non-fatal warnings (backup succeeded) |

## Integration Points

- `cli-optional` - CLI commands that use this module
- Backup files are standard ZIP format
- Can be manually extracted if needed

## Best Practices

1. Stop domain before backup (or use --force)
2. Set recycle limit to manage disk space
3. Store backups on separate storage for disaster recovery
4. Test restore process periodically
5. Use descriptive backup descriptions
