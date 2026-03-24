# CLAUDE.md - JPA Container

This file provides guidance for working with the `persistence/jpa-container` module - Jakarta Persistence API (JPA) 3.1 container implementation.

## Module Overview

The jpa-container module implements the JPA 3.1 container for Payara Server, providing integration between Jakarta Persistence and the GlassFish kernel. It handles persistence unit deployment, EntityManagerFactory creation, and schema generation.

**Key Features:**
- **JPA 3.1 Support** - Full Jakarta Persistence API 3.1 implementation
- **EclipseLink Integration** - Default provider with custom classloader weaving
- **Java2DB** - Automatic schema generation from entity annotations
- **Scoped Resources** - Supports application-scoped DataSources

## Build Commands

```bash
# Build jpa-container module
mvn -DskipTests clean package -f appserver/persistence/jpa-container/pom.xml

# Run tests
mvn test -f appserver/persistence/jpa-container/pom.xml
```

## Core Components

### JPAContainer

```java
@Service(name = "org.glassfish.persistence.jpa.JPAContainer")
public class JPAContainer implements Container {

    @Override
    public Class<? extends Deployer> getDeployer() {
        return JPADeployer.class;
    }

    @Override
    public String getName() {
        return "JPA";
    }
}
```

### JPADeployer

Handles JPA deployment lifecycle:

```java
@Service
public class JPADeployer extends SimpleDeployer<JPAContainer, JPApplicationContainer>
        implements EventListener {

    @Inject
    private ConnectorRuntime connectorRuntime;

    @Override
    public boolean prepare(DeploymentContext context) {
        if (isEMFCreationRequired(context)) {
            createEMFs(context);
        }
        return true;
    }

    private void loadPersistenceUnit(PersistenceUnitDescriptor pud, DeploymentContext dc) {
        // Determine provider contract info (weaving enabled/disabled)
        ProviderContainerContractInfo providerInfo = weavingEnabled
            ? new ServerProviderContainerContractInfo(dc, connectorRuntime, isDas)
            : new EmbeddedProviderContainerContractInfo(dc, connectorRuntime, isDas);

        // Create PersistenceUnitLoader which loads the PU
        PersistenceUnitLoader loader = new PersistenceUnitLoader(pud, providerInfo);

        // Store for later use (java2db, cleanup)
        dc.addTransientAppMetaData(getUniquePuIdentifier(pud), loader);
    }

    @Override
    public void event(Event event) {
        if (event.is(Deployment.APPLICATION_PREPARED)) {
            // Perform Java2DB schema generation
            // Validate PUs by creating EntityManager
            // Save EMFs for runtime use
        } else if (event.is(Deployment.APPLICATION_DISABLED)) {
            // Close all EMFs for cleanup
        }
    }
}
```

### PersistenceUnitLoader

Loads a persistence unit and creates EntityManagerFactory:

```java
public class PersistenceUnitLoader {

    private final PersistenceUnitDescriptor pud;
    private final ProviderContainerContractInfo providerContractInfo;

    public EntityManagerFactory getEMF() {
        // Create EMF using provider contract info
        // Handles weaving, classloading, datasource lookup
    }

    public void doJava2DB() {
        // Execute Java2DB schema generation
        // Uses provider's DDL generation
    }
}
```

### PersistenceUnitInfoImpl

JPA PersistenceUnitInfo implementation:

```java
public class PersistenceUnitInfoImpl implements PersistenceUnitInfo {

    @Override
    public String getPersistenceUnitName() {
        return pud.getName();
    }

    @Override
    public PersistenceUnitTransactionType getTransactionType() {
        return pud.getTransactionType();
    }

    @Override
    public List<String> getManagedClassNames() {
        return pud.getClassNames();
    }

    @Override
    public DataSource getJtaDataSource() {
        // Lookup JTA DataSource from JNDI
        return lookupDataSource(pud.getJtaDataSource());
    }

    @Override
    public Properties getProperties() {
        return pud.getProperties();
    }
}
```

## Schema Generation

### SchemaGenerationProcessor

```java
public interface SchemaGenerationProcessor {

    void generateSchema(PersistenceUnitDescriptor pud) throws IOException;
}

// Implementations:
// - JPAStandardSchemaGenerationProcessor - Standard JPA 2.1+
// - EclipseLinkSchemaGenerationProcessor - EclipseLink-specific
```

### Schema Generation Flow

```
PersistenceUnit with schema-generation properties
       │
       ▼
[SchemaGenerationProcessorFactory]
       │
       ├─→ Detect provider (EclipseLink vs other)
       │
       ├─→ Select processor
       │   │
       │   └─→ [JPAStandardSchemaGenerationProcessor]
       │   │   ├─→ Use javax.persistence.schema-generation.* properties
       │   │   └─→ Call provider.generateSchema()
       │   │
       │   └─→ [EclipseLinkSchemaGenerationProcessor]
       │       ├─→ Use eclipselink.ddl-generation properties
       │       ├─→ Parse eclipselink sessions.xml
       │       └─→ Call EclipseLink DDL generator
       │
       ▼
DDL Scripts executed
       ├─→ CREATE TABLE statements
       ├─→ CREATE INDEX statements
       └─→ ALTER TABLE statements
```

## Provider Integration

### ProviderContainerContractInfo

Integrates JPA provider with GlassFish container:

```java
public class ServerProviderContainerContractInfo extends ProviderContainerContractInfoBase {

    private final DeploymentContext deploymentContext;
    private final ConnectorRuntime connectorRuntime;

    @Override
    public InputStream getInputStreamForClass(String className) {
        // Get class bytes for weaving
        return deploymentContext.getOriginalClassLoader()
            .getResourceAsStream(className.replace('.', '/') + ".class");
    }

    @Override
    public ClassLoader getNewTempClassLoader() {
        // Create new classloader with weaving transformers
        return deploymentContext.getClassLoader();
    }

    @Override
    public ContainerProps getContainerProperties() {
        ContainerProps props = new ContainerProps();
        props.setProperty(ServerProviderContainerContractInfo.DELEGATE_CLASSLOADING, "true");
        props.setProperty(ServerProviderContainerContractInfo.WEAVING_CHANGETRACKING, "true");
        props.setProperty(ServerProviderContainerContractInfo.WEAVING_LAZY, "true");
        return props;
    }

    @Override
    public DataSource getDataSource(String jndiName) {
        // Lookup DataSource via ConnectorRuntime
        try {
            return connectorRuntime.lookupDataSource(jndiName);
        } catch (Exception e) {
            return null;
        }
    }
}
```

## EMF Creation Logic

### Decision Matrix

| Scenario | DAS | Instance | Result |
|----------|-----|----------|--------|
| Deploy --target=server --enabled=true | ✓ | - | EMF created, Java2DB, EMF remains open |
| Deploy --target=server --enabled=false | ✓ | - | EMF created, Java2DB, EMF closed |
| Deploy --target=instance --enabled=true | - | ✓ | EMF created |
| Deploy --target=instance --enabled=false | - | - | No EMF created |

### Key Methods

```java
private boolean isEMFCreationRequired(DeploymentContext context) {
    boolean isDas = serverEnvironment.isDas();
    DeployCommandParameters params = context.getCommandParameters(DeployCommandParameters.class);

    if (isDas) {
        if (params.origin.isDeploy()) {
            return true; // Always create on deploy (for Java2DB)
        } else {
            // Restart or enable
            return params.enabled && isTargetDas(params);
        }
    } else {
        // Instance
        return params.enabled;
    }
}
```

## Package Structure

```
jpa-container/
└── src/main/java/org/glassfish/persistence/jpa/
    ├── JPAContainer.java                      # Container definition
    ├── JPADeployer.java                       # Deployer implementation
    ├── JPApplicationContainer.java             # Application container
    ├── PersistenceUnitLoader.java             # PU loading logic
    ├── PersistenceUnitInfoImpl.java           # PU info implementation
    ├── ProviderContainerContractInfo.java     # Provider integration
    ├── ServerProviderContainerContractInfo.java
    ├── EmbeddedProviderContainerContractInfo.java
    ├── ProviderContainerContractInfoBase.java
    └── schemageneration/
        ├── SchemaGenerationProcessor.java      # Processor interface
        ├── JPAStandardSchemaGenerationProcessor.java
        ├── EclipseLinkSchemaGenerationProcessor.java
        └── SchemaGenerationProcessorFactory.java
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `dol` | PersistenceUnitDescriptor parsing |
| `deployment-common` | DeploymentContext, DeployCommandParameters |
| `container-common` | Container integration |
| `connectors-internal-api` | DataSource lookup |
| `glassfish-api` | Container, Deployer APIs |
| `internal-api` | ServerEnvironment |

## Configuration

### persistence.xml Properties

```xml
<persistence-unit name="myPU" transaction-type="JTA">
    <jta-data-source>jdbc/__default</jta-data-source>

    <properties>
        <!-- Standard JPA properties -->
        <property name="javax.persistence.schema-generation.database.action" value="create"/>
        <property name="eclipselink.ddl-generation" value="create-tables"/>
        <property name="eclipselink.weaving" value="true"/>
        <property name="eclipselink.logging.level" value="FINE"/>
    </properties>
</persistence-unit>
```

### Weaving Configuration

```bash
# Enable/disable weaving for embedded mode
asadmin create-jvm-options --target server \
    "-Dorg.glassfish.persistence.embedded.weaving.enabled=true"
```

## Notes

- **JPA 3.1** - Full support for Jakarta Persistence 3.1
- **EclipseLink** - Default provider with GlassFish-specific integration
- **Weaving** - Automatic bytecode enhancement for lazy loading
- **Java2DB** - Schema generation on deploy
- **Scoped Resources** - Application-specific DataSource support
- **Transaction Integration** - JTA synchronization via ConnectorRuntime
