# CLAUDE.md - Appserver Deployment

This file provides guidance for working with the `appserver/deployment` module - Jakarta EE application deployment infrastructure.

## Module Overview

The deployment module provides the core deployment infrastructure for Jakarta EE applications, including deployment descriptor parsing (DOL), deployers, and deployment client APIs. It handles EAR, WAR, EJB JAR, RAR, and application client deployment.

## Build Commands

```bash
# Build entire deployment module
mvn -DskipTests clean package -f appserver/deployment/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/deployment/<submodule>/pom.xml
```

## Module Structure

| Submodule | Purpose |
|-----------|---------|
| `dol` | Deployment Object Library (application metadata) |
| `javaee-core` | Core Java EE deployment services |
| `javaee-full` | Full profile Java EE deployment |
| `client` | JSR-88 deployment client API |
| `dtds` | Legacy DTD deployment descriptors |
| `schemas` | XML schema deployment descriptors |
| `jsr88-jar` | JSR-88 one-JAR deployment |
| `*-l10n` | Localization resources |

## Deployment Architecture Overview

```
Application Archive (EAR/WAR/JAR/RAR)
              │
    ┌─────────▼──────────┐
    │   Archive Handler  │  Detects archive type
    └─────────┬──────────┘
              │
    ┌─────────▼──────────┐
    │   DolProvider      │  Parses descriptors, builds DOL
    │   - Archivist       │
    │   - ApplicationFactory │
    └─────────┬──────────┘
              │
    ┌─────────▼──────────┐
    │   Application      │  Root descriptor object
    │   (DOL)            │  - Modules
    │   - BundleDescriptor│  - References
    │   - EjbDescriptor   │  - Security
    │   - WebBundleDescriptor│
    └─────────┬──────────┘
              │
    ┌─────────▼──────────┐
    │   Deployer         │  Container-specific deployment
    │   - WebDeployer    │
    │   - EjbDeployer    │
    │   - ConnectorDeployer │
    └─────────┬──────────┘
              │
    ┌─────────▼──────────┐
    │   Container        │  Runtime container
    │   - WebContainer   │
    │   - EjbContainer   │
    │   - ResourceAdapter │
    └────────────────────┘
```

## Deployment Object Library (DOL)

### Core Descriptors

| Descriptor | Purpose |
|------------|---------|
| `Application` | Root application descriptor (EAR) |
| `BundleDescriptor` | Abstract base for all modules |
| `WebBundleDescriptor` | Web application metadata |
| `EjbBundleDescriptor` | EJB module metadata |
| `EjbDescriptor` | Individual EJB metadata |
| `ConnectorDescriptor` | Resource adapter metadata |
| `ApplicationClientDescriptor` | App client metadata |

### Application Descriptor Structure

```java
Application
├── Modules (Set<ModuleDescriptor<BundleDescriptor>>)
│   ├── WebBundleDescriptor
│   ├── EjbBundleDescriptor
│   ├── ConnectorDescriptor
│   └── ApplicationClientDescriptor
├── Roles (Set<Role>)
├── SecurityRoleMapper
├── EntityManagerFactories (Map<String, EntityManagerFactory>)
├── ResourceReferences
├── EjbReferences
├── ServiceReferences
├── MessageDestinations
└── WebServicesDescriptor
```

### Descriptor Hierarchy

```
RootDeploymentDescriptor
        │
        ├─ Application (EAR root)
        │
        └─ BundleDescriptor (Module base)
                │
                ├─ WebBundleDescriptor
                ├─ EjbBundleDescriptor
                ├─ ApplicationClientDescriptor
                └─ ConnectorDescriptor
```

## Deployment Lifecycle

### Deployment Phases

| Phase | Description |
|-------|-------------|
| **Detect** | Sniffer identifies archive type |
| **Load** | Archive opened, descriptors parsed |
| **Validate** | Descriptor validation |
| **Prepare** | ClassLoader setup, scratch dirs |
| **Deploy** | Container-specific deployment |
| **Start** | Application started |

### DolProvider Workflow

```java
@Service
public class DolProvider implements ApplicationMetaDataProvider<Application> {
    @Inject ArchivistFactory archivistFactory;
    @Inject ApplicationFactory applicationFactory;
    @Inject DescriptorArchivist descriptorArchivist;
    @Inject ApplicationArchivist applicationArchivist;

    // 1. Parse deployment descriptors
    public Application getMetaData(DeploymentContext dc) {
        ReadableArchive source = dc.getSource();

        // 2. Create Application from archive
        Application app = applicationFactory.openArchive(
            source.getURI(),
            archivistFactory,
            source
        );

        // 3. Store in deployment context
        dc.addModuleMetaData(app);

        return app;
    }
}
```

### Deployment Flow

```
deploy command
       │
   [Sniffers detect type]
       │
   [DolProvider builds DOL]
       │
   [Validate descriptors]
       │
   [ClassLoader setup]
       │
   [Deployer.prepare()]
       │
   [Deployer.load()]
       │
   [Deployer.deploy()]
       │
   Application Started
```

## Java EE Core Deployment

### DolProvider Service

Main metadata provider for applications:

```java
@Service
public class DolProvider {
    // Provides Application metadata from archive
    public Application getMetaData(DeploymentContext dc);

    // Writes generated descriptors
    public void writeDeploymentPlan(DeploymentContext dc);
}
```

### JavaEEDeployer

Base class for all Java EE deployers:

```java
public abstract class JavaEEDeployer<T extends Container, U extends ApplicationContainer>
        implements Deployer<T, U> {

    // Deployment lifecycle
    public void prepare(DeploymentContext dc);
    public U load(T container, DeploymentContext dc);
    public void unload(U appContainer);
    public void clean(DeploymentContext dc);

    // Classpath construction
    protected String getModuleClassPath(DeploymentContext dc);
}
```

### ApplicationHolder

Holds the DOL Application object:

```java
public class ApplicationHolder {
    private Application application;

    public Application getApplication();
    public void setApplication(Application app);
}
```

## Descriptor Sources

### Descriptor Locations

| Descriptor | Standard Location |
|------------|-------------------|
| `application.xml` | `META-INF/application.xml` |
| `web.xml` | `WEB-INF/web.xml` |
| `ejb-jar.xml` | `META-INF/ejb-jar.xml` |
| `ra.xml` | `META-INF/ra.xml` |
| `persistence.xml` | `META-INF/persistence.xml` |
| `faces-config.xml` | `WEB-INF/faces-config.xml` |
| `beans.xml` | `META-INF/beans.xml` or `WEB-INF/beans.xml` |
| `payara-web.xml` | `WEB-INF/payara-web.xml` |

### GlassFish/Payara Descriptors

| Descriptor | Purpose |
|------------|---------|
| `glassfish-application.xml` | Application config |
| `glassfish-web.xml` | Web container config |
| `glassfish-ejb-jar.xml` | EJB container config |
| `payara-web.xml` | Payara-specific web config |
| `payara-domain.xml` | Payara domain config |

## Annotation Processing

### Annotation Scanning

DOL processes Jakarta EE annotations:

| Annotation | Purpose |
|------------|---------|
| `@EJB`, `@EJBs` | EJB references |
| `@Resource`, `@Resources` | Resource references |
| `@WebService`, `@WebServiceProvider` | Web services |
| `@PersistenceContext`, `@PersistenceUnit` | JPA |
| `@DeclaredRoles`, `@RunAs` | Security |
| `@DataSourceDefinition` | Data sources |
| `@ConnectionFactoryDefinition` | JCA resources |
| `@ManagedBean` | Managed beans |
| `@Singleton`, `@Stateless`, `@Stateful` | EJB types |

### Annotation Processing Flow

```
ClassLoader scans classes
       │
   [Find annotations]
       │
   [Build descriptors]
       │
   [Merge with XML descriptors]
       │
   [Validate complete descriptor]
       │
   Final DOL
```

## Application Types

### EAR (Enterprise Archive)

```
myapp.ear
├── META-INF/
│   ├── application.xml          # Module declaration
│   ├── glassfish-application.xml
│   └── MANIFEST.MF
├── lib/                          # Shared libraries
│   └── shared.jar
├── web-module.war
├── ejb-module.jar
└── rar-module.rar
```

### WAR (Web Archive)

```
myapp.war
├── WEB-INF/
│   ├── web.xml                   # Web descriptor
│   ├── glassfish-web.xml
│   ├── payara-web.xml
│   ├── classes/                  # Servlets, etc.
│   ├── lib/                      # WEB-INF libraries
│   └── faces-config.xml
├── META-INF/
│   └── persistence.xml
└── index.jsp
```

### EJB JAR

```
myapp-ejb.jar
├── META-INF/
│   ├── ejb-jar.xml
│   ├── glassfish-ejb-jar.xml
│   ├── persistence.xml
│   └── MANIFEST.MF
└── com/example/ejb/
    └── MyEJB.class
```

## Deployment Client (JSR-88)

### DeploymentFacility Interface

```java
public interface DeploymentFacility {
    // Connection management
    boolean connect(ServerConnectionIdentifier targetDAS);
    boolean disconnect();
    boolean isConnected();

    // Deployment operations
    ProgressObject deploy(DeploymentTarget target,
        ReadableArchive source, File deploymentPlan,
        Map<String, String> deploymentOptions);

    ProgressObject undeploy(TargetModuleID[] moduleID);

    ProgressObject start(TargetModuleID[] moduleID);
    ProgressObject stop(TargetModuleID[] moduleID);

    // Query operations
    TargetModuleID[] getTargetModuleIDs();
    Target[] getTargets();
}
```

### Remote Deployment

```
DeploymentFacility
       │
   [connect to DAS]
       │
   [deploy archive]
       │
   ProgressObject
       │
   DeploymentStatus
       │
   TargetModuleID
```

## Descriptor Validation

### Validation Levels

| Level | Description |
|-------|-------------|
| **Syntax** | XML/annotation syntax validation |
| **Semantic** | Reference validity, naming conflicts |
| **Integration** | Cross-module reference validation |

### Validator Chain

```
ApplicationDescriptor
       │
   [CommonResourceValidator]
       │
   [ApplicationValidator]
       │
   [WebBundleValidator]
       │
   [EjbBundleValidator]
       │
   Validated DOL
```

## Class Loading

### ClassLoader Hierarchy

```
Bootstrap ClassLoader
       │
Extension ClassLoader
       │
Common ClassLoader
       │
   ┌───┴────────────────────┐
   │                        │
App ClassLoader        Module ClassLoaders
(EAR level)            (Per module)
   │                        │
   └────────────────────────┘
```

### ClassLoader Setup

```java
// EarLibClassLoader - EAR lib directory
// WebappClassLoader - Web module
// EjbClassLoader - EJB module
// ConnectorClassLoader - RAR module
```

## Deployment Properties

| Property | Description |
|----------|-------------|
| `deployment.plan` | External deployment plan |
| `javaee.deploy.backupOnUpgrade` | Backup before upgrade |
| `compatibility` | Compatibility settings |
| `keepstate` | Preserve session state |
| `contextroot` | Web context root |
| `name` | Application name |
| `enabled` | Auto-start flag |

## Archive Handling

### ReadableArchive

```java
public interface ReadableArchive {
    URI getURI();
    InputStream getEntry(String name);
    boolean exists(String name);
    Collection<String> entries();
    ReadableArchive getSubArchive(String name);
    ReadableArchive getParentArchive();
    void close();
}
```

### Archive Types

| Type | Handler | Extension |
|------|---------|-----------|
| EAR | `EarHandler` | `.ear` |
| WAR | `WarHandler` | `.war` |
| EJB JAR | `EjbJarHandler` | `.jar` |
| RAR | `RarHandler` | `.rar` |
| App Client | `AppClientHandler` | `.jar` |

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | Public deployment APIs |
| `internal-api` | Internal server APIs |
| `config-api` | Configuration beans |
| `hk2-core` | Dependency injection |
| `deployment-common` | Shared deployment utilities |
| `schema2beans` | XML to Java generation |

## Related Modules

- `nucleus/deployment` - Core deployment infrastructure
- `appserver/web` - Web container deployment
- `appserver/ejb` - EJB deployment
- `appserver/connectors` - RAR deployment
- `appserver/persistence` - JPA deployment
