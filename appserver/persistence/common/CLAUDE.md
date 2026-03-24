# CLAUDE.md - Persistence Common

This file provides guidance for working with the `persistence/common` module - Shared utilities between JPA and CMP.

## Module Overview

The common module provides shared utilities used by both JPA and CMP implementations. This avoids code duplication between the two persistence technologies.

**Key Features:**
- **Java2DB Processor** - Schema generation utilities
- **Shared Interfaces** - Common persistence interfaces
- **Connector Integration** - DataSource lookup utilities

## Build Commands

```bash
# Build common module
mvn -DskipTests clean package -f appserver/persistence/common/pom.xml

# Run tests
mvn test -f appserver/persistence/common/pom.xml
```

## Core Components

### Java2DBProcessorHelper

Handles database schema generation:

```java
public class Java2DBProcessorHelper {

    private DeploymentContext deploymentContext;

    public void init() {
        // Initialize for schema generation
    }

    public void createOrDropTablesInDB(boolean create, String vendorName) {
        // Execute DDL scripts
        // create=true: CREATE TABLE
        // create=false: DROP TABLE
    }

    public List<String> getGeneratedFiles() {
        // Return list of generated DDL files
    }

    private void executeDDLScript(String scriptFile) {
        // Execute SQL script against database
    }
}
```

### Java2DB Flow

```
PersistenceUnit/Entity
       │
       ▼
[Schema Generation Processor]
       │
       ├─→ Parse entity annotations
       ├─→ Generate DDL scripts
       │   ├─→ CREATE TABLE
       │   ├─→ CREATE INDEX
       │   └─→ ALTER TABLE
       │
       ▼
[Java2DBProcessorHelper]
       │
       ├─→ Connect to database
       ├─→ Execute DDL scripts
       └─→ Verify schema creation
```

## Package Structure

```
common/
└── src/main/java/org/glassfish/persistence/common/
    └── Java2DBProcessorHelper.java
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `dol` | Deployment descriptors |
| `connectors-internal-api` | DataSource lookup |
| `container-common` | Container utilities |

## Notes

- **Shared Code** - Used by both JPA and CMP modules
- **Java2DB** - Automatic schema generation from annotations
- **DDL Scripts** - Generated and executed during deployment
