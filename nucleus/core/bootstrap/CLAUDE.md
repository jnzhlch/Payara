# CLAUDE.md - Bootstrap

This file provides guidance for working with the `bootstrap` module - Payara server startup and initialization.

## Module Overview

The `bootstrap` module (`glassfish.jar`) is the entry point for Payara. It handles:
- Server startup (`ASMain.main()`)
- HK2 initialization
- OSGi framework startup
- Embedded GlassFish API support
- Classpath construction

**Key artifact:** `glassfish.jar` - Contains bootstrap classes and embedded API

## Build Commands

```bash
# Build bootstrap module
mvn -DskipTests clean package -f nucleus/core/bootstrap/pom.xml

# Build with tests
mvn clean package -f nucleus/core/bootstrap/pom.xml
```

## Architecture

### Entry Points

**Main entry point:**
```java
// ASMain.java - Standard server startup
com.sun.enterprise.glassfish.bootstrap.ASMain

// Embedded startup
com.sun.enterprise.glassfish.bootstrap.EmbeddedMain

// OSGi-based startup
com.sun.enterprise.glassfish.bootstrap.osgi.GlassFishMainActivator
```

### Startup Sequence

```
ASMain.main()
    │
    ├─→ Platform setup (properties, classpath)
    │
    ├─→ ClassPathBuilder - constructs classpath
    │
    ├─→ SingleHK2Factory - creates HK2 habitat
    │
    ├─→ OSGiFrameworkLauncher (if OSGi enabled)
    │      └─→ Starts Apache Felix
    │
    ├─→ AppServerStartup - kernel startup
    │      └─→ ContainerStarter - starts containers
    │
    └─→ Application deployment
```

### Key Classes

| Class | Purpose |
|-------|---------|
| `ASMain` | Main entry point for standard server |
| `EmbeddedMain` | Entry point for embedded mode |
| `MainHelper` | Shared startup utilities |
| `ClassPathBuilder` | Constructs runtime classpath |
| `SingleHK2Factory` | HK2 service registry creation |
| `GlassFishImpl` | Embedded GlassFish implementation |
| `OSGiGlassFishImpl` | OSGi-based GlassFish implementation |

## ClassPath Construction

The `ClassPathBuilder` constructs the server classpath by scanning:

```java
// Default locations
glassfish/modules/          # OSGi bundles
glassfish/lib/              # Shared libraries
glassfish/domains/domain1/  # Domain-specific
```

**Classpath order:**
1. Bootstrap classes (glassfish.jar)
2. HK2 and dependencies
3. Module system classes
4. Application libraries

## Embedded GlassFish

### Programmatic Startup

```java
// Using GlassFishRuntime
GlassFishRuntime runtime = GlassFishRuntime.bootstrap();
GlassFish glassfish = runtime.newGlassFish();
glassfish.start();

// Using builder
GlassFishProperties props = new GlassFishProperties();
props.setInstanceRoot("/path/to/glassfish");
GlassFish glassfish = runtime.newGlassFish(props);
glassfish.start();
```

### Embedded Startup Flow

```
GlassFishRuntime.bootstrap()
    │
    ├─→ StaticGlassFishRuntimeBuilder
    │      └─→ Creates StaticGlassFishRuntime
    │
    └─→ GlassFishImpl
           ├─→ start()
           ├─→ getDeployer()
           └─→ getCommandRunner()
```

## OSGi Integration

### OSGi Framework

```java
// OSGi framework launcher
OSGiFrameworkLauncher launcher = new OSGiFrameworkLauncher();
BundleContext context = launcher.start();
```

### Bundle Provisioning

```java
// Install bundles from modules directory
BundleProvisioner provisioner = new MinimalBundleProvisioner();
provisioner.provision();
```

### OSGi Services

**Bundle activator:**
```java
// GlassFishMainActivator
// Starts when OSGi framework launches
// Registers GlassFish as OSGi service
```

## HK2 Initialization

### Habitat Creation

```java
// Create HK2 service registry
ServiceLocator habitat = SingleHK2Factory.getHabitat();

// Or using HK2 API
ServiceLocator locator = ServiceLocatorFactory.getInstance().create("default");
```

### Inhabitants Parsing

```java
// Parse HK2 descriptor files
EmbeddedInhabitantsParser parser = new EmbeddedInhabitantsParser();
parser.parse(habitat);
```

## Configuration

### Startup Properties

```java
// System properties affecting startup
System.setProperty("AS_LOGFILE", "server.log");
System.setProperty("INSTANCE_ROOT", "/path/to/domain");
System.setProperty("INSTALL_ROOT", "/path/to/glassfish");
```

### Domain Configuration

```java
// DomainResolver finds domain.xml
DomainResolver resolver = new DomainResolver();
File domainXml = resolver.getDomainXml();
```

## Packaging

### glassfish.jar Structure

```
glassfish.jar
├── META-INF/
│   ├── MANIFEST.MF        # Main-Class: ASMain
│   └── services/          # ServiceLoader files
└── com/sun/enterprise/glassfish/bootstrap/
    ├── ASMain.class        # Main entry point
    ├── GlassFishImpl.class # Embedded impl
    └── ...
```

### Dependencies Included

The bootstrap module includes `simple-glassfish-api` classes for embedded scenarios:

```xml
<!-- From pom.xml - unpacks simple-glassfish-api -->
<artifactItem>
    <groupId>fish.payara.server.internal.common</groupId>
    <artifactId>simple-glassfish-api</artifactId>
</artifactItem>
```

## Development

### Running from IDE

```bash
# Set JVM properties
-DAS_LOGFILE=server.log
-DINSTANCE_ROOT=/path/to/payara/glassfish/domains/domain1
-DINSTALL_ROOT=/path/to/payara/glassfish

# Main class
com.sun.enterprise.glassfish.bootstrap.ASMain

# Arguments
[start-domain|start-instance] [domain-name]
```

### Debug Mode

```bash
# Enable debug mode
asadmin start-domain --debug
# Or with system property
-Djava.awt.headless=true
-Djavax.xml.parsers.SAXParserFactory=com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl
```

## Common Patterns

### 1. Custom Bootstrap

```java
// Extend ASMain for custom startup
public class MyBootstrap extends ASMain {
    @Override
    protected void doLaunch() {
        // Custom initialization
        super.doLaunch();
    }
}
```

### 2. Embedded with Custom Config

```java
BootstrapProperties bootstrapProps = new BootstrapProperties();
bootstrapProps.setInstallRoot("/opt/payara");
bootstrapProps.setXmlResourceName("custom-domain.xml");

GlassFishRuntime runtime = GlassFishRuntime.bootstrap(bootstrapProps);
```

### 3. Add Modules to Classpath

```java
// Extend ClassPathBuilder
ClassPathBuilder builder = new ClassPathBuilder() {
    @Override
    protected void addModules() {
        super.addModules();
        addJar(new File("my-module.jar"));
    }
};
```

## Related Modules

- **kernel** - Core kernel services started by bootstrap
- **logging** - Logging initialized during startup
- **api-exporter** - OSGi service exports
- **hk2** - HK2 injection framework

## Troubleshooting

### Startup Failures

```bash
# Check domain.xml
asadmin validate-domain

# Check ports
netstat -an | grep 4848  # Admin port
netstat -an | grep 8080  # HTTP port

# Check logs
tail -f domains/domain1/logs/server.log
```

### Classpath Issues

```bash
# List classpath
asadmin list-classpaths

# Check module status
asadmin list-modules
```

### OSGi Bundle Issues

```bash
# List bundles
asadmin list-bundles

# Check bundle state
asadmin get-bundle --bundle_id <id>
```
