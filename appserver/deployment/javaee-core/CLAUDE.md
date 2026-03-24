# CLAUDE.md - Java EE Core Deployment

This file provides guidance for working with the `javaee-core` module - Core Java EE deployment services.

## Module Overview

The javaee-core module provides the core deployment infrastructure services for Jakarta EE applications. It includes the DOL provider, base deployer class, and deployment utilities.

## Build Commands

```bash
# Build javaee-core module
mvn -DskipTests clean package -f appserver/deployment/javaee-core/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Name**: "Deployment Related JavaEE Core Classes"
- **Purpose**: Core deployment services

## Core Services

### DolProvider

Main metadata provider for applications:

```java
@Service
public class DolProvider implements ApplicationMetaDataProvider<Application>,
        ApplicationInfoProvider {

    @Inject ArchivistFactory archivistFactory;
    @Inject ApplicationFactory applicationFactory;
    @Inject DescriptorArchivist descriptorArchivist;
    @Inject ApplicationArchivist applicationArchivist;
    @Inject Domain domain;
    @Inject DasConfig dasConfig;

    // Provides Application metadata from archive
    @Override
    public MetaData getMetaData() {
        return new MetaData(false, new Class[] { Application.class }, null);
    }

    // Process DOL from archive
    private Application processDOL(DeploymentContext dc) throws IOException {
        ReadableArchive sourceArchive = dc.getSource();

        // Set parser data
        sourceArchive.setExtraData(Types.class, dc.getTransientAppMetaData(Types.class.getName(), Types.class));
        sourceArchive.setExtraData(Parser.class, dc.getTransientAppMetaData(Parser.class.getName(), Parser.class));

        // Create Application
        Application app = applicationFactory.openArchive(
            sourceArchive.getURI(),
            archivistFactory,
            sourceArchive
        );

        return app;
    }

    // Write deployment plan
    public void writeDeploymentPlan(DeploymentContext dc) throws IOException {
        // Generate deployment plan XML
    }
}
```

### JavaEEDeployer

Abstract base class for all Java EE deployers:

```java
public abstract class JavaEEDeployer<T extends Container, U extends ApplicationContainer>
        implements Deployer<T, U> {

    @Inject protected ServerEnvironment env;
    @Inject protected ApplicationRegistry appRegistry;
    @Inject protected ServiceLocator habitat;
    @Inject @Named("application_undeploy") @Optional
    protected ApplicationVisitor undeploymentVisitor;

    // Returns metadata (no metadata by default)
    public MetaData getMetaData() {
        return new MetaData(false, null, null);
    }

    // Setup classpath for module
    protected String getModuleClassPath(DeploymentContext ctx) {
        // Base module classpath
        StringBuilder classpath = new StringBuilder(
            ASClassLoaderUtil.getModuleClassPath(habitat, ctx));

        // Add module dir
        classpath.append(ctx.getSourceDir().toPath());
        classpath.append(File.pathSeparator);

        // Add stubs dir
        classpath.append(ctx.getScratchDir("ejb").toPath());
        classpath.append(File.pathSeparator);

        // Add EAR lib libraries
        Application app = ctx.getModuleMetaData(Application.class);
        if (!app.isVirtual()) {
            List<URL> earLibURLs = ASClassLoaderUtil.getAppLibDirLibrariesAsList(
                new File(ctx.getSource().getParentArchive().getURI()),
                app.getLibraryDirectory(), null);

            for (URL url : earLibURLs) {
                classpath.append(Paths.get(url.toURI()).toString());
                classpath.append(File.pathSeparator);
            }
        }

        return classpath.toString();
    }

    // Clean scratch directories
    protected void cleanScratchDirs(DeploymentContext dc) {
        // Clean generated directories
    }
}
```

### ApplicationHolder

Holds the DOL Application object:

```java
public class ApplicationHolder {
    private Application application;

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application app) {
        this.application = app;
    }
}
```

## Deployment Flow

### DolProvider Flow

```
DeploymentContext
       │
   [set archive source]
       │
┌──────▼──────────────┐
│  DolProvider        │
│                      │
│ 1. Get Types/Parser │
│ 2. Open Archive     │
│ 3. Build DOL        │
│ 4. Validate         │
└──────┬──────────────┘
       │
   Application object
       │
   [Add to context]
       │
   [Archivist saves]  │
       │
   DeploymentContext
```

### Deployer Lifecycle

``┌─────────────────────────────────────────────────────────────┐
│                      Deployer Lifecycle                       │
├─────────────────────────────────────────────────────────────┤
│  1. prepare()    - Setup, validation, classpath              │
│  2. load()       - Load metadata, create ApplicationContainer │
│  3. (running)    - Application is running                     │
│  4. unload()     - Stop, cleanup                              │
│  5. clean()      - Remove generated files                     │
└─────────────────────────────────────────────────────────────┘
```

## Admin Commands

### GetContextRootCommand

```java
@Service(name = "get-context-root")
@Scoped(PerLookup.class)
public class GetContextRootCommand implements AdminCommand {
    @Inject protected Deployment deployment;

    @Override
    public void execute(AdminCommandContext context) {
        // Get context root for deployed app
        ApplicationInfo appInfo = deployment.get(appName);
        Application app = appInfo.getMetaData(Application.class);

        // Find web module and get context root
        for (BundleDescriptor bd : app.getBundleDescriptors(WebBundleDescriptor.class)) {
            WebBundleDescriptor wbd = (WebBundleDescriptor) bd;
            context.getActionReport().setMessage(wbd.getContextRoot());
        }
    }
}
```

### ListSubComponentsCommand

```java
@Service(name = "list-sub-components")
@Scoped(PerLookup.class)
public class ListSubComponentsCommand implements AdminCommand {
    @Inject protected Deployment deployment;

    @Override
    public void execute(AdminCommandContext context) {
        // List components in application
        // - EJBs
        // - Servlets
        // - Filters
        // - Listeners
        // - etc.
    }
}
```

## Deployment Utilities

### JavaEEDeploymentUtils

Utility methods for deployment:

```java
public class JavaEEDeploymentUtils {
    // Get library directory from application.xml
    public static String getLibraryDirectory(Application app);

    // Check if virtual application (standalone module)
    public static boolean isVirtualApplication(Application app);

    // Get app name from context
    public static String getAppNameFromContext(DeploymentContext dc);
}
```

## Archive Handlers

### ArchiveFactory

Creates archive abstractions:

```java
public interface ArchiveFactory {
    // Create archive from file
    ReadableArchive createArchive(File file) throws IOException;

    // Create archive from URI
    ReadableArchive createArchive(URI uri) throws IOException;

    // Create archive from JAR
    ReadableArchive createArchive(JarFile jar) throws IOException;
}
```

## Archivist Factory

### ArchivistFactory

Creates appropriate archivist for archive type:

```java
public interface ArchivistFactory {
    // Get archivist for archive
    Archivist getArchivist(ReadableArchive archive) throws IOException;

    // Get archivist for module type
    Archivist getArchivist(String moduleType);

    // Get application archivist
    ApplicationArchivist getApplicationArchivist();
}
```

### Archivist Types

| Archivist | Handles |
|-----------|---------|
| `ApplicationArchivist` | EAR files |
| `WebArchivist` | WAR files |
| `EjbArchivist` | EJB JAR files |
| `ConnectorArchivist` | RAR files |
| `AppClientArchivist` | Application client JAR |

## Deployment Context

### ExtendedDeploymentContext

Extended deployment context with additional info:

```java
public interface ExtendedDeploymentContext extends DeploymentContext {
    // Get application metadata
    <T> T getModuleMetaData(Class<T> metadataType);

    // Add module metadata
    void addModuleMetaData(Object metadata);

    // Get transient app metadata
    <T> T getTransientAppMetaData(String key, Class<T> type);

    // Get source directory
    File getSourceDir();

    // Get scratch directory
    File getScratchDir(String subDirName);

    // Get original source
    ReadableArchive getSource();

    // Get classloader
    ClassLoader getClassLoader();

    // Get deployment tracking
    DeploymentTracing getDeploymentTracing();
}
```

## Package Structure

```
org.glassfish.javaee.core.deployment/
├── DolProvider.java                    # Metadata provider
├── JavaEEDeployer.java                 # Base deployer
├── ApplicationHolder.java              # Application holder
├── JavaEEDeploymentUtils.java          # Utilities
├── GetContextRootCommand.java          # Get context root
├── ListSubComponentsCommand.java       # List components
└── *Archive*.java                      # Archive utilities
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | Public APIs |
| `internal-api` | Internal server APIs |
| `deployment-common` | Shared deployment utilities |
| `dol` | Deployment Object Library |
| `config-api` | Configuration beans |
| `hk2-core` | Dependency injection |
| `deployment-javaee-full` | Full profile deployment |

## Integration Points

### With Deployers

```
DolProvider
       │
   [provides Application]
       │
   WebDeployer
   EjbDeployer
   ConnectorDeployer
       │
   [extends JavaEEDeployer]
       │
   Container-specific deployment
```

### With Archivists

```
DolProvider
       │
   [uses]
       │
ApplicationFactory
       │
   [uses]
       │
ArchivistFactory
       │
   [creates]
       │
WebArchivist, EjbArchivist, etc.
       │
   [reads/parses]
       │
Application (DOL)
```

## Hot Deploy Integration

```java
@Inject private HotDeployService hotDeployService;

// Check for hot deploy
ApplicationState state = hotDeployService.getApplicationState(appName);
if (state != null && state.isEnabled()) {
    // Enable hot deploy monitoring
}
```

## Related Modules

- `deployment/dol` - Deployment Object Library
- `deployment/javaee-full` - Full profile deployment
- `deployment/client` - Deployment client
- `nucleus/deployment` - Core deployment infrastructure
