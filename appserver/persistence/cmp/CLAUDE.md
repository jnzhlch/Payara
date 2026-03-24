# CLAUDE.md - CMP (Container Managed Persistence)

This file provides guidance for working with the `persistence/cmp` module - EJB 2.x Container Managed Persistence support.

## Module Overview

The cmp module provides EJB 2.x CMP (Container Managed Persistence) support for Payara Server. This is a legacy technology for entity bean persistence before JPA.

**Key Features:**
- **Bytecode Enhancement** - Runtime bytecode enhancer for entity classes
- **EJB Mapping** - EJB-to-relational mapping configuration
- **SQL Store Support** - SQL-based persistence backend
- **Model Classes** - CMP metadata model

## Build Commands

```bash
# Build cmp module
mvn -DskipTests clean package -f appserver/persistence/cmp/pom.xml

# Run tests
mvn test -f appserver/persistence/cmp/pom.xml

# Run bytecode enhancer
java -jar cmp-enhancer.jar <entity-classes>
```

## Submodules

```
cmp/
├── cmp-all/               # Aggregator module
├── cmp-scripts/           # Deployment scripts
├── ejb-mapping/           # EJB mapping to database
├── enhancer/              # Bytecode enhancer
├── generator-database/    # Database schema generator
├── internal-api/          # Internal SPI
├── model/                 # CMP model classes
├── support-ejb/           # EJB support classes
├── support-sqlstore/      # SQL store backend
└── utility/               # Utility classes
```

## ByteCode Enhancer

### Enhancement Process

```
Entity Class (.class)
        │
        ▼
[ClassFile Reader]
        │
        ├─→ Parse constant pool
        ├─→ Parse methods
        └─→ Parse fields
        │
        ▼
[ClassAction/MethodAction]
        │
        ├─→ Add persistence tracking
        ├─→ Add dirty checking
        ├─→ Add relationship management
        └─→ Add callback methods
        │
        ▼
Enhanced Entity Class
```

### Main Entry Point

```java
package com.sun.jdo.api.persistence.enhancer;

public class Main {
    public static void main(String[] args) throws Exception {
        String[] classNames = args; // Entity class names
        ByteCodeEnhancer enhancer = new ByteCodeEnhancer();

        for (String className : classNames) {
            ClassFile classFile = loadClass(className);
            enhancer.enhance(classFile);
        }
    }
}
```

### Enhancement Features

| Feature | Description |
|---------|-------------|
| **Dirty Tracking** | Track modified fields for ejbStore |
| **Relationship Management** | Handle CMR (Container Managed Relationships) |
| **Callback Injection** | Add ejbLoad, ejbStore, ejbActivate, ejbPassivate |
| **Field Accessors** | Generate getter/setter methods |

## EJB Mapping

### SunCmpMapping Configuration

```java
public class SunCmpMapping {
    private List<EntityMapping> entityMappings;
    private String version;        // Mapping version
}

public class EntityMapping {
    private String tableName;       // Database table
    private String className;      // Entity class
    private List<CmpFieldMapping> cmpFields;
    private List<CmrFieldMapping> cmrFields;
    private List<SecondaryTable> secondaryTables;
}

public class CmpFieldMapping {
    private String fieldName;      // Java field name
    private String columnName;     // Database column
    private boolean primaryKey;   // Is primary key
}
```

### Mapping File Format

```xml
<!-- sun-cmp-mappings.xml -->
<mappings>
    <entity-mapping>
        <entity>
            <ejb-name>Customer</ejb-name>
            <table-name>CUSTOMER_TABLE</table-name>
            <cmp-field-mapping>
                <field-name>id</field-name>
                <column-name>CUST_ID</column-name>
            </cmp-field-mapping>
        </entity>
    </entity-mapping>
</mappings>
```

## CMP Model Classes

### JDO Metadata

```java
// JDOMetaData - Persistence metadata
public class JDOMetaData {
    String getPersistenceCapableClass();
    String[] getPersistenceCapableFields();
    String[] getPersistenceAwareFields();
}

// JDOMetaDataModelImpl - Implementation
public class JDOMetaDataModelImpl extends JDOMetaData {
    // Parses JDO metadata for entity classes
}
```

### Fetch Groups

```java
public class FetchedWith {
    private String name;          // Fetch group name
    private List<String> fields;  // Fields to fetch

    // Define which related entities to fetch
}
```

## SQL Store Support

### Support Classes

```
support-sqlstore/
└── src/main/java/com/sun/jdo/spi/persistence/support/sqlstore/
    ├── SQLStoreManager.java       # Manages SQL store backend
    ├── SQLStateManager.java      # State management
    └── SQLStorePersistence.java   # Persistence integration
```

### SQL Generation

```java
// Automatic SQL generation from mappings
public class SQLGenerator {

    public String generateSelect(EntityMapping mapping) {
        // Generate SELECT statement from mapping
    }

    public String generateInsert(EntityMapping mapping) {
        // Generate INSERT statement
    }

    public String generateUpdate(EntityMapping mapping) {
        // Generate UPDATE statement
    }

    public String generateDelete(EntityMapping mapping) {
        // Generate DELETE statement
    }
}
```

## Package Structure

```
cmp/
├── enhancer/src/main/java/com/sun/jdo/api/persistence/enhancer/
│   ├── ByteCodeEnhancer.java
│   ├── Main.java
│   ├── PersistenceLauncher.java
│   ├── classfile/
│   │   ├── ClassFile.java
│   │   ├── ConstantPool.java
│   │   ├── MethodAction.java
│   │   └── ClassMember.java
│   ├── impl/
│   │   ├── ClassControl.java
│   │   ├── MethodBuilder.java
│   │   └── Environment.java
│   └── meta/
│       └── JDOMetaData.java
│
├── ejb-mapping/src/main/java/com/sun/jdo/api/persistence/mapping/ejb/
│   ├── MappingFile.java
│   ├── MappingGenerator.java
│   ├── ConversionHelper.java
│   └── beans/
│       ├── SunCmpMapping.java
│       ├── EntityMapping.java
│       └── CmpFieldMapping.java
│
├── model/src/main/java/com/sun/jdo/spi/persistence/support/
│   └── sqlstore/
│       ├── SQLStoreManager.java
│       └── SQLStateManager.java
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `deployment-common` | Deployment integration |
| `dol` | EJB descriptor parsing |
| `jdbc-runtime` | Database connectivity |

## Enhancement Flow

```
1. Deploy EJB JAR with entity beans
                │
                ▼
2. Parse ejb-jar.xml for CMP descriptors
                │
                ▼
3. Generate mappings (if not provided)
                │
                ▼
4. Run bytecode enhancer
                │
                ├─→ Load .class files
                ├─→ Add persistence tracking code
                ├─→ Add relationship management
                └─→ Write enhanced classes
                │
                ▼
5. Deploy enhanced entity beans
```

## Notes

- **EJB 2.x CMP** - Legacy technology (use JPA for new code)
- **Bytecode Enhancement** - Required for container-managed persistence
- **Runtime Enhancement** - Classes enhanced during deployment
- **Mapping Generation** - Automatic from EJB descriptors or manual
- **SQL Store** - Database persistence backend
