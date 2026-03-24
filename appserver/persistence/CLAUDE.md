# CLAUDE.md - Persistence

This file provides guidance for working with the `appserver/persistence` module - Persistence support including JPA, CMP, and Entity Beans.

## Module Overview

The persistence module provides comprehensive persistence support for Payara Server, including Jakarta Persistence API (JPA) integration, EJB 2.x Entity Beans, and Container Managed Persistence (CMP).

**Key Areas:**
- **JPA Container** - Jakarta Persistence API (JPA) 3.1 integration with EclipseLink
- **Entity Bean Container** - EJB 2.x Entity Bean support
- **CMP** - Container Managed Persistence for EJB 2.x
- **EclipseLink Wrapper** - EclipseLink JPA provider integration
- **Common** - Shared utilities for JPA and CMP

## Build Commands

```bash
# Build entire persistence module
mvn -DskipTests clean package -f appserver/persistence/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/persistence/jpa-container/pom.xml
mvn -DskipTests clean package -f appserver/persistence/entitybean-container/pom.xml
mvn -DskipTests clean package -f appserver/persistence/cmp/pom.xml

# Run tests
mvn test -f appserver/persistence/jpa-container/pom.xml
```

## Architecture

### Module Structure

```
persistence/
├── common/                    # Shared utilities for JPA and CMP
├── jpa-container/             # JPA 3.1 container implementation
├── eclipselink-wrapper/       # EclipseLink provider wrapper
├── gf-jpa-connector/          # JPA runtime connector
├── entitybean-container/      # EJB 2.x Entity Bean container
├── cmp/                        # EJB 2.x CMP support
│   ├── enhancer/              # Bytecode enhancer for CMP
│   ├── ejb-mapping/           # EJB mapping for CMP
│   ├── model/                 # CMP model classes
│   └── support-*/             # CMP support classes
├── jpa-container-l10n/         # Localization
├── cmp-l10n/                   # CMP localization
└── oracle-jdbc-driver-packages/# Oracle JDBC drivers
```

## JPA Container

### JPAContainer

```java
@Service(name = "org.glassfish.persistence.jpa.JPAContainer")
public class JPAContainer implements Container {
    public Class<? extends Deployer> getDeployer() {
        return JPADeployer.class;
    }
}
```

### JPADeployer

Handles JPA application deployment:

```java
@Service
public class JPADeployer extends SimpleDeployer<JPAContainer, JPApplicationContainer> {

    @Inject
    private ConnectorRuntime connectorRuntime;

    @Override
    public boolean prepare(DeploymentContext context) {
        // Create EntityManagerFactories for persistence units
        if (isEMFCreationRequired(context)) {
            createEMFs(context);
        }
    }

    private void loadPersistenceUnit(PersistenceUnitDescriptor pud, DeploymentContext dc) {
        // Load persistence unit with ProviderContainerContractInfo
        ProviderContainerContractInfo providerInfo = new ServerProviderContainerContractInfo(
            deploymentContext, connectorRuntime, isDas);

        PersistenceUnitLoader loader = new PersistenceUnitLoader(pud, providerInfo);
    }
}
```

### Persistence Unit Loading

```
Application Deployment
       │
       ▼
[JPADeployer.prepare()]
       │
       ├─→ Register scoped resources (java:comp/env DataSource)
       │
       ├─→ Iterate PersistenceUnitDescriptors
       │   │
       │   └─→ [PersistenceUnitLoader]
       │       ├─→ Create EntityManagerFactory
       │       ├─→ Load entity classes
       │       ├─→ Apply weaving (bytecode enhancement)
       │       └─→ Register transformers
       │
       ▼
[APPLICATION_PREPARED]
       │
       ├─→ Java2DB (schema generation)
       ├─→ Validation (create EntityManager)
       └─→ Store EMF for cleanup
```

### Schema Generation

Java2DB generates database schemas from JPA annotations:

```properties
# Standard JPA properties
javax.persistence.schema-generation.database.action=create
javax.persistence.schema-generation.create-source-source=metadata
javax.persistence.schema-generation.create-script-source=metadata

# EclipseLink extensions
eclipselink.ddl-generation=generate-tables
eclipselink.applocation-packages=your.package
```

### ProviderContainerContractInfo

Integrates JPA provider with GlassFish:

```java
public class ServerProviderContainerContractInfo extends ProviderContainerContractInfoBase {

    @Override
    public InputStream getInputStreamForClass(String className) {
        // Load class bytes for weaving
    }

    @Override
    public ClassLoader getNewTempClassLoader() {
        // Create temp classloader for weaving
    }

    @Override
    public ContainerProps getContainerProperties() {
        // JPA container integration properties
    }
}
```

## Entity Bean Container

### EntityContainer

EJB 2.x Entity Bean container implementation:

```java
public class EntityContainer extends BaseContainer {

    // Entity bean lifecycle management
    @Override
    protected void instantiateEJB() throws Exception {
        // Create entity bean instance
    }

    @Override
    protected void initializeEJB() throws Exception {
        // Initialize entity bean state
    }

    // Passivation/Activation for clustering
    protected void passivateEJB(EntityContextImpl ctx) {
        // Serialize bean state
    }

    protected EntityContextImpl activateEJB(Object primaryKey) {
        // Restore bean from passivated state
    }
}
```

### ReadOnlyBeanContainer

Read-only entity bean support:

```java
public class ReadOnlyBeanContainer extends EntityContainer {

    @Override
    protected boolean isReadOnlyBean() {
        return true;
    }

    // Refresh beans when notified
    @Override
    public void refreshAllReadOnlyBeans() {
        // Refresh all read-only beans
        notifyReadOnlyBeanNotifiers();
    }
}
```

### EJBObjectCache

Entity bean caching:

```java
public interface EJBObjectCache extends Cache {
    void add(Object primaryKey, EJBLocalRemoteObject ejbObject);
    EJBLocalRemoteObject remove(Object primaryKey);
    EJBLocalRemoteObject get(Object primaryKey);
}

// Implementations:
// - FIFOEJBObjectCache - First-in-first-out cache
// - UnboundedEJBObjectCache - No size limit
```

## CMP (Container Managed Persistence)

### CMP Module Structure

```
cmp/
├── enhancer/         # Bytecode enhancer for entity classes
├── ejb-mapping/      # EJB-to-relational mapping
├── model/            # CMP metadata model
├── internal-api/     # Internal SPI
├── support-ejb/      # EJB support classes
├── support-sqlstore/ # SQL store support
└── utility/          # Utility classes
```

### ByteCodeEnhancer

Enhances entity classes at bytecode level:

```java
public class ByteCodeEnhancer {

    public static void main(String[] args) throws Exception {
        // Enhance classes for CMP
        // Usage: java com.sun.jdo.api.persistence.enhancer.Main <classes>
    }

    public void enhance(ClassFile classFile) {
        // Add persistence tracking code
        // Add relationship management
        // Add dirty checking
    }
}
```

### Mapping Classes

```java
// SunCmpMapping - Root mapping element
public class SunCmpMapping {
    private List<EntityMapping> entityMappings;
    private List<CmrFieldMapping> cmrFieldMappings;
}

// EntityMapping - Entity to table mapping
public class EntityMapping {
    private String tableName;
    private List<CmpFieldMapping> cmpFields;
    private List<SecondaryTable> secondaryTables;
}
```

## EclipseLink Integration

### EclipseLink Wrapper

Wraps EclipseLink JPA provider:

```
gf-jpa-connector/
└── (no Java sources - configuration only)
```

The connector integrates EclipseLink with GlassFish through:
- `ProviderContainerContractInfo` - Container integration
- weaving/class transformation - Bytecode enhancement
- DataSource lookup - JTA DataSource integration
- Transaction synchronization - JTA integration

## Package Structure

```
appserver/persistence/
├── jpa-container/
│   └── src/main/java/org/glassfish/persistence/jpa/
│       ├── JPAContainer.java
│       ├── JPADeployer.java
│       ├── JPApplicationContainer.java
│       ├── PersistenceUnitLoader.java
│       ├── PersistenceUnitInfoImpl.java
│       ├── ProviderContainerContractInfo.java
│       ├── ServerProviderContainerContractInfo.java
│       └── schemageneration/
│           ├── SchemaGenerationProcessor.java
│           ├── JPAStandardSchemaGenerationProcessor.java
│           └── EclipseLinkSchemaGenerationProcessor.java
│
├── entitybean-container/
│   └── src/main/java/org/glassfish/persistence/ejb/entitybean/container/
│       ├── EntityContainer.java
│       ├── EntityContextImpl.java
│       ├── EntityContainerFactory.java
│       ├── ReadOnlyBeanContainer.java
│       ├── ReadOnlyEJBHomeImpl.java
│       ├── cache/
│       │   ├── EJBObjectCache.java
│       │   ├── FIFOEJBObjectCache.java
│       │   └── UnboundedEJBObjectCache.java
│       └── distributed/
│           ├── DistributedEJBService.java
│           ├── DistributedReadOnlyBeanService.java
│           └── ReadOnlyBeanRefreshEventHandler.java
│
├── cmp/
│   ├── enhancer/src/main/java/com/sun/jdo/api/persistence/enhancer/
│   ├── ejb-mapping/src/main/java/com/sun/jdo/api/persistence/mapping/ejb/
│   └── model/src/main/java/com/sun/jdo/spi/persistence/support/
│
├── common/
│   └── src/main/java/org/glassfish/persistence/common/
│       └── Java2DBProcessorHelper.java
│
└── eclipselink-wrapper/
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `dol` | Deployment Object Library (persistence.xml parsing) |
| `deployment-common` | Deployment infrastructure |
| `container-common` | Container utilities |
| `connectors-internal-api` | DataSource/JTA integration |
| `glassfish-api` | GlassFish public APIs |

## Notes

- **JPA 3.1** - Supports Jakarta Persistence API 3.1
- **EclipseLink** - Default JPA provider (wrapped for integration)
- **Weaving** - Bytecode enhancement for lazy loading and change tracking
- **Java2DB** - Schema generation from JPA annotations
- **Scoped Resources** - Supports `java:comp/env` DataSource references
- **Cluster Support** - Entity beans support passivation/activation
- **EJB 2.x** - Legacy Entity Bean and CMP support (deprecated)
