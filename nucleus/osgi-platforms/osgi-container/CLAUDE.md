# CLAUDE.md - OSGi Container

This file provides guidance for working with the `osgi-container` module - OSGi bundle deployment container.

## Module Overview

The `osgi-container` module provides the integration between Payara's deployment system and the OSGi framework, enabling deployment of OSGi bundles as applications.

## Build Commands

```bash
# Build osgi-container module
mvn -DskipTests clean package -f nucleus/osgi-platforms/osgi-container/pom.xml

# Build with tests
mvn clean package -f nucleus/osgi-platforms/osgi-container/pom.xml
```

## Architecture

### Deployment Flow

```
asadmin deploy --type=osgi mybundle.jar
        │
        ├─→ OSGiSniffer (detects --type=osgi)
        │      └─→ Returns true for OSGi bundles
        │
        ├─→ Deployment backend
        │      └─→ Selects OSGiContainer
        │
        ├─→ OSGiDeployer
        │      ├─→ prepare() - Validate bundle
        │      ├─→ load() - Install bundle in OSGi
        │      └─→ clean() - Uninstall bundle
        │
        └─→ OSGiDeployedBundle
               └─→ Wraps installed Bundle
```

## Core Components

### OSGiContainer

```java
@Service(name = OSGiSniffer.CONTAINER_NAME)
@Singleton
public class OSGiContainer implements Container {

    public Class<? extends Deployer> getDeployer() {
        return OSGiDeployer.class;
    }

    public String getName() {
        return "osgi";
    }
}
```

### OSGiSniffer

Detects OSGi bundles for deployment:

```java
@Service(name = "osgi")
public class OSGiSniffer extends GenericSniffer {

    public static final String CONTAINER_NAME = "osgi";

    // Always returns false - requires explicit --type=osgi
    @Override
    public boolean handles(ReadableArchive location) {
        return false;
    }

    // Handles when --type=osgi is specified
    @Override
    public boolean handles(DeploymentContext context) {
        ArchiveType archiveType = ...;
        return supportsArchiveType(archiveType);
    }
}
```

### OSGiDeployer

Handles bundle lifecycle:

```java
@Service
public class OSGiDeployer implements Deployer<OSGiContainer, OSGiDeployedBundle> {

    @Override
    public OSGiDeployedBundle load(OSGiContainer container, DeploymentContext context) {
        Bundle bundle = getApplicationBundle(context, true);
        return new OSGiDeployedBundle(bundle);
    }

    @Override
    public void clean(DeploymentContext context) {
        Bundle bundle = getApplicationBundle(context);
        bundle.uninstall();
        getPA().refreshPackages(new Bundle[]{bundle});
    }

    @Override
    public boolean prepare(DeploymentContext context) {
        // Validate and prepare bundle
        File source = context.getSourceDir();
        // Install bundle in OSGi framework
    }
}
```

### OSGiDeployedBundle

Wrapper for deployed OSGi bundle:

```java
public class OSGiDeployedBundle {

    private final Bundle bundle;

    public OSGiDeployedBundle(Bundle bundle) {
        this.bundle = bundle;
    }

    public Bundle getBundle() {
        return bundle;
    }
}
```

### OSGiArchiveType

Defines OSGi archive type:

```java
@Service
public class OSGiArchiveType implements ArchiveType {

    @Override
    public String toString() {
        return "osgi";
    }
}
```

### OSGiArchiveHandler

Handles OSGi archives:

```java
@Service
public class OSGiArchiveHandler implements ArchiveHandler {

    @Override
    public String getArchiveType() {
        return "osgi";
    }

    @Override
    public boolean handles(ReadableArchive archive) {
        // Check for OSGi bundle manifest
        return archive.exists("META-INF/MANIFEST.MF");
    }
}
```

## Bundle Detection

### OSGi Manifest Headers

Bundles are identified by these headers in MANIFEST.MF:

```manifest
Bundle-SymbolicName: com.example.mybundle
Bundle-ManifestVersion: 2
```

### Archive Detection Flow

```java
// 1. User deploys with --type=osgi
asadmin deploy --type=osgi mybundle.jar

// 2. OSGiSniffer.handles(DeploymentContext) called
// 3. Checks if ArchiveType is "osgi"
// 4. If yes, OSGiContainer is selected
// 5. OSGiDeployer.prepare() installs bundle
```

## Deployment Lifecycle

### Prepare Phase

```java
@Override
public boolean prepare(DeploymentContext context) {
    File source = context.getSourceDir();

    // Get bundle context
    BundleContext bundleContext = getBundleContext();

    // Install bundle
    Bundle bundle = bundleContext.installBundle(location);

    // Store bundle reference
    context.getTransientAppMetaData().put("osgi-bundle", bundle);

    return true;
}
```

### Load Phase

```java
@Override
public OSGiDeployedBundle load(OSGiContainer container, DeploymentContext context) {
    // Get bundle from prepare phase
    Bundle bundle = getApplicationBundle(context, true);

    // Start bundle if enabled
    OpsParams params = context.getCommandParameters(OpsParams.class);
    if (params.enabled && bundle.getState() != Bundle.ACTIVE) {
        bundle.start();
    }

    return new OSGiDeployedBundle(bundle);
}
```

### Clean Phase

```java
@Override
public void clean(DeploymentContext context) {
    // Uninstall bundle
    Bundle bundle = getApplicationBundle(context);
    bundle.uninstall();

    // Refresh package admin
    PackageAdmin pa = getPA();
    pa.refreshPackages(new Bundle[]{bundle});
}
```

## OSGi Integration

### BundleContext Access

```java
// Get BundleContext from current thread
BundleContext context = getBundleContext();

// Or via BundleReference
ClassLoader cl = Thread.currentThread().getContextClassLoader();
if (cl instanceof BundleReference) {
    Bundle bundle = ((BundleReference) cl).getBundle();
    BundleContext context = bundle.getBundleContext();
}
```

### PackageAdmin

```java
// Get PackageAdmin service
PackageAdmin pa = (PackageAdmin) context.getService(
    context.getServiceReference(PackageAdmin.class.getName())
);

// Refresh packages after uninstall
pa.refreshPackages(new Bundle[]{bundle});
```

## Package Structure

```
org.glassfish.extras.osgicontainer/
├── OSGiContainer.java          - Container service
├── OSGiSniffer.java            - Bundle detection
├── OSGiDeployer.java           - Deployment lifecycle
├── OSGiDeployedBundle.java     - Deployed bundle wrapper
├── OSGiArchiveType.java        - Archive type definition
├── OSGiArchiveHandler.java     - Archive handling
└── OSGiArchiveDetector.java    - Archive detection
```

## Related Modules

- **osgi-cli-remote** - OSGi shell access
- **felix** - Apache Felix framework
- **core/bootstrap** - OSGi framework initialization

## Deployment Examples

### Deploy Bundle

```bash
# Deploy as OSGi bundle
asadmin deploy --type=osgi mybundle.jar

# Deploy with name
asadmin deploy --type=osgi --name myBundle mybundle.jar

# Deploy disabled
asadmin deploy --type=osgi --enabled=false mybundle.jar
```

### Undeploy Bundle

```bash
# Undeploy by name
asadmin undeploy osgi://myBundle

# Or by osgi:// scheme
asadmin undeploy osgi://<bundle-symbolic-name>
```

## Troubleshooting

### Bundle Installation Fails

```java
// Check bundle context is available
BundleContext ctx = getBundleContext();
if (ctx == null) {
    throw new RuntimeException("OSGi framework not available");
}
```

### Bundle Not Starting

```bash
# Check bundle state
asadmin osgi headers <bundle-id>

# Check for unresolved dependencies
asadmin osgi packages <bundle-id>
```

### Class Loading Issues

```properties
# Add boot delegation
org.osgi.framework.bootdelegation=com.sun.*,sun.*
```

## Notes

- OSGi bundles require explicit `--type=osgi` flag
- The container integrates with Payara's deployment framework
- Bundles are managed through the OSGi BundleContext
- PackageAdmin refresh is required after uninstall
