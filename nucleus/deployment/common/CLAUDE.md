# CLAUDE.md - Deployment Common

This file provides guidance for working with the `deployment-common` module - core deployment infrastructure.

## Module Overview

The `deployment-common` module provides the foundational infrastructure for application deployment in Payara. It handles:

- Archive handling (JAR, WAR, exploded directories)
- Deployment context and lifecycle
- Application descriptors and metadata
- Annotation scanning
- Versioning support
- Deployment utilities

## Build Commands

```bash
# Build deployment-common module
mvn -DskipTests clean package -f nucleus/deployment/common/pom.xml

# Build with tests
mvn clean package -f nucleus/deployment/common/pom.xml
```

## Architecture

### Deployment Flow

```
Application Archive
        │
        ├─→ ArchiveHandler (detects type)
        │      └─→ ReadableArchive abstraction
        │
        ├─→ DeploymentContext (deployment state)
        │      ├─→ DeploymentProperties
        │      ├─→ Source (archive)
        │      └─→ Command parameters
        │
        ├─→ Sniffer (application type detection)
        │
        ├─→ Deployer (loading/starting applications)
        │
        └─→ ApplicationInfo (deployment metadata)
```

### Core Packages

| Package | Purpose |
|---------|---------|
| `org.glassfish.deployment.common` | Core deployment interfaces and utilities |
| `com.sun.enterprise.deploy.shared` | Archive handling (FileArchive, JarArchive, etc.) |
| `org.glassfish.deployment.versioning` | Application versioning |
| `fish.payara.deployment.util` | Payara deployment utilities |

## Archive Handling

### ReadableArchive

```java
// Archive abstraction
ReadableArchive archive = new FileArchive(new File("myapp.war"));

// List entries
Collection<String> entries = archive.entries();

// Get entry contents
InputStream is = archive.getEntry("META-INF/MANIFEST.MF");

// Close archive
archive.close();
```

### Archive Types

| Archive Type | Description |
|--------------|-------------|
| `FileArchive` | File-system based archive |
| `JarArchive` | JAR file archive |
| `MemoryMappedArchive` | Memory-mapped file access |
| `MultiReadableArchive` | Composite of multiple archives |
| `OutputJarArchive` | Writable JAR archive |

### ArchiveFactory

```java
// Create archive from file
ReadableArchive archive = ArchiveFactory.createArchive(new File("app.war"));

// Create from directory
ReadableArchive archive = ArchiveFactory.createArchive(new File("/path/to/exploded"));

// Open existing archive
ReadableArchive archive = ArchiveFactory.openArchive(new File("app.war"));
```

## Deployment Context

### DeploymentContextImpl

```java
// Create deployment context
ReadableArchive source = new FileArchive(new File("app.war"));
String appName = "myapp";
DeploymentContext context = new DeploymentContextImpl(source, appName);

// Set properties
context.getAppProps().setProperty("contextRoot", "/myapp");
context.setCommandParameters(params);

// Get logger
Logger logger = context.getLogger();

// Add class loaders
context.addClassLoader(myClassLoader);
```

### DeploymentProperties

```java
// Standard deployment properties
context.getAppProps().setProperty(DeploymentProperties.APP_NAME, "myapp");
context.getAppProps().setProperty(DeploymentProperties.CONTEXT_ROOT, "/myapp");
context.getAppProps().setProperty(DeploymentProperties.FORCE, "true");
context.getAppProps().setProperty(DeploymentProperties.KEEP_METADATA_DIRECTORY, "true");
```

## Application Metadata

### ApplicationConfigInfo

```java
// Get application configuration info
ApplicationConfigInfo configInfo = ...;

// Get descriptor
RootDeploymentDescriptor descriptor = configInfo.getDescriptor();
```

### ModuleDescriptor

```java
// Module information (e.g., EJB JAR, WAR)
ModuleDescriptor module = ...;

String moduleName = module.getModuleName();
String moduleType = module.getModuleType();
```

### Descriptor

```java
// Base descriptor class
Descriptor descriptor = ...;

// Get metadata
String displayName = descriptor.getDisplayName();
String description = descriptor.getDescription();
```

## Annotation Scanning

### AnnotationScanner

```java
// Scan for annotations
AnnotationScanner scanner = new AnnotationScanner();

// Scan archive
Set<Class<?>> annotatedClasses = scanner.scan(archive);

// Or scan with specific annotation
Set<Class<?>> classes = scanner.scan(archive, MyAnnotation.class);
```

### GenericAnnotationDetector

```java
// Detect specific annotations
GenericAnnotationDetector detector = new GenericAnnotationDetector();
boolean hasAnnotation = detector.hasAnnotation(archive, "javax.ejb.Stateless");
```

## Versioning

### VersioningService

```java
// Application versioning
VersioningService versioning = habitat.getService(VersioningService.class);

// Check if version is enabled
boolean enabled = versioning.isEnabled();

// Get version expression
String version = versioning.getVersionExpression("myapp");
```

### Versioning Patterns

```
1.0.0          # Exact version
[1.0.0,2.0.0)  # Range: 1.0.0 <= x < 2.0.0
[1.0.0,]       # Range: >= 1.0.0
[1.0.0,1.1.0]  # Range: 1.0.0 <= x <= 1.1.0
1.0.0-SNAPSHOT # Pre-release
```

## Client Artifacts

### ClientJarWriter

```java
// Generate client JAR
ClientJarWriter writer = new ClientJarWriter();

// Write client JAR
File clientJar = writer.writeClientJar(appInfo, outputDir);
```

### ClientArtifactsManager

```java
// Manage client artifacts
ClientArtifactsManager manager = ...;

// Get client stubs
File[] clientStubs = manager.getClientArtifacts(appName);
```

## Deployment Utilities

### DeploymentUtils

```java
// Check archive type
boolean isWar = DeploymentUtils.isWar(archive);
boolean isJar = DeploymentUtils.isJar(archive);
boolean isRar = DeploymentUtils.isRar(archive);

// Get application type
String appType = DeploymentUtils.getApplicationType(archive);
```

### JavaArchiveUtils

```java
// Payara-specific utilities
String className = JavaArchiveUtils.getMainClassName(archive);
String version = JavaArchiveUtils.getImplementationVersion(archive);
```

### URIUtils

```java
// URI handling for deployment
URI archiveUri = URIUtils.getJarURIFromFile(file);
String schemeSpecificPart = URIUtils.getSchemeSpecificPart(uri);
```

## Monitoring

### DeploymentLifecycleProbeProvider

```java
// Monitoring probes
@ProbeProvider(moduleName = "deployment",
               providerName = "deployment-lifecycle",
               eventType = "deployment-event")
public interface DeploymentLifecycleProbeProvider {

    @Probe(name = "deploymentStarted")
    void deploymentStartedEvent(
        @ProbeParam("appName") String appName
    );

    @Probe(name = "deploymentCompleted")
    void deploymentCompletedEvent(
        @ProbeParam("appName") String appName,
        @ProbeParam("duration") long duration
    );
}
```

## Related Modules

- **deployment/admin** - Deployment admin commands
- **deployment/autodeploy** - Automatic deployment
- **kernel** - ApplicationLifecycle service
- **admin/config-api** - Domain configuration

## Common Patterns

### 1. Create Custom Archive Handler

```java
public class MyArchiveHandler extends AbstractArchiveHandler {

    @Override
    public String getArchiveType() {
        return "mytype";
    }

    @Override
    public boolean handles(ReadableArchive archive) {
        return archive.exists("META-INF/my-config.xml");
    }
}
```

### 2. Process Deployment Context

```java
public void deploy(ReadableArchive source) throws Exception {
    DeploymentContext context = new DeploymentContextImpl(source, "myapp");

    // Pre-deploy
    prepare(context);

    // Load
    load(context);

    // Start
    start(context);
}
```

### 3. Handle Annotations

```java
AnnotationScanner scanner = new AnnotationScanner();
scanner.process(archive, new AnnotationScanner.Processor() {
    @Override
    public void process(Class<?> clazz) {
        // Process annotated class
    }
});
```

### 4. Version Management

```java
// Set version at deployment
context.getAppProps().setProperty(
    DeploymentProperties.VERSION,
    "1.0.0"
);

// Or use versioning service
VersioningService vs = habitat.getService(VersioningService.class);
context.getAppProps().setProperty(
    DeploymentProperties.VERSIONING,
    vs.getVersionExpression("myapp")
);
```
