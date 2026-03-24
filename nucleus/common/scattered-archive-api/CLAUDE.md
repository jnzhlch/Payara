# CLAUDE.md - Scattered Archive API

This file provides guidance for working with the `scattered-archive-api` module - scattered/deployed archive support.

## Module Overview

The `scattered-archive-api` module provides the ability to deploy "scattered" applications - applications whose files are distributed across multiple directories rather than packaged as a single archive (WAR, JAR, RAR). This is particularly useful for:

- Development workflows with exploded directories
- IDE integration for rapid application deployment
- Testing scenarios without packaging artifacts

## Core Concept: ScatteredArchive

A `ScatteredArchive` represents a Java EE application whose contents are spread across multiple locations:

```java
// Create a scattered web application
ScatteredArchive archive = new ScatteredArchive("myapp", ScatteredArchive.Type.WAR);

// Add compiled classes
archive.addClassPath(new File("target/classes"));

// Add metadata files
archive.addMetadata(new File("src/main/webapp/WEB-INF/web.xml"));

// Get deployable URI
Deployer deployer = glassfish.getDeployer();
deployer.deploy(archive.toURI());
```

## Archive Types

Supported archive types via `ScatteredArchive.Type`:

- **JAR** - EJB JAR or Application Client
- **WAR** - Web Application
- **RAR** - Resource Adapter (Connector)

## Working with Classpaths

### Adding Classpath Entries

The `addClassPath()` method adds directories or JARs to the archive:

```java
// Add a directory (all classes and resources included)
archive.addClassPath(new File("target/classes"));

// Add a JAR file
archive.addClassPath(new File("lib/my-dependency.jar"));

// Multiple classpaths
archive.addClassPath(new File("target/classes"));
archive.addClassPath(new File("target/generated-sources"));
```

**What gets included from directories:**
- All `.class` files
- All resource files (properties, images, etc.)
- Everything is available via classloader

### Example: Exploded WAR Structure

```java
ScatteredArchive archive = new ScatteredArchive("myapp", ScatteredArchive.Type.WAR);

// Compiled servlets and classes
archive.addClassPath(new File("target/classes"));

// Dependencies in WEB-INF/lib
archive.addClassPath(new File("lib/dependency1.jar"));
archive.addClassPath(new File("lib/dependency2.jar"));

// Metadata goes to WEB-INF/
archive.addMetadata(new File("src/main/webapp/WEB-INF/web.xml"));
archive.addMetadata(new File("src/main/webapp/WEB-INF/sun-web.xml"));

// Static resources via root directory
ScatteredArchive withRoot = new ScatteredArchive("myapp", Type.WAR, new File("src/main/webapp"));
```

## Working with Metadata

Metadata files are deployment descriptors that go into specific locations:

### Default Metadata Location

```java
// For WAR - goes to WEB-INF/<filename>
archive.addMetadata(new File("resources/web.xml"));  // WEB-INF/web.xml

// For JAR/RAR - goes to META-INF/<filename>
archive.addMetadata(new File("resources/ejb-jar.xml"));  // META-INF/ejb-jar.xml
```

### Explicit Metadata Location

```java
// Specify exact target location
archive.addMetadata(
    new File("resources/my-log-factory"),
    "META-INF/services/org.apache.commons.logging.LogFactory"
);

// Add with explicit WEB-INF location
archive.addMetadata(
    new File("config/sun-web.xml"),
    "WEB-INF/sun-web.xml"
);
```

### Common Metadata Files

**Web Application (WAR):**
- `WEB-INF/web.xml` - Standard deployment descriptor
- `WEB-INF/sun-web.xml` - GlassFish-specific configuration
- `WEB-INF/glassfish-web.xml` - Payara-specific configuration

**EJB/Client (JAR):**
- `META-INF/ejb-jar.xml` - EJB deployment descriptor
- `META-INF/persistence.xml` - JPA configuration
- `META-INF/beans.xml` - CDI configuration

## Working with Root Directory

Using a root directory establishes a base structure:

```java
// Root directory for WAR
// rootDirectory/
//   WEB-INF/classes/org/myorg/FooServlet.class
//   WEB-INF/classes/org/myorg/Bar.class
//   WEB-INF/web.xml
//   WEB-INF/lib/myjar.jar
//   index.jsp
//   theme.css

ScatteredArchive archive = new ScatteredArchive("myapp", Type.WAR, new File("build/war"));

// Can still add scattered elements
archive.addClassPath(new File("target/extra-classes"));
archive.addMetadata(new File("config/override-web.xml"), "WEB-INF/web.xml");
```

## Complete Example: Maven Project

```java
public void deployMavenProject() throws Exception {
    // Create scattered WAR from Maven project structure
    ScatteredArchive archive = new ScatteredArchive("mywebapp", ScatteredArchive.Type.WAR);

    // Add compiled classes
    archive.addClassPath(new File("target/classes"));

    // Add dependencies from WEB-INF/lib simulation
    File webInfLib = new File("target/dependency");
    for (File jar : webInfLib.listFiles()) {
        if (jar.getName().endsWith(".jar")) {
            archive.addClassPath(jar);
        }
    }

    // Add deployment descriptors
    archive.addMetadata(new File("src/main/webapp/WEB-INF/web.xml"));
    archive.addMetadata(new File("src/main/webapp/WEB-INF/glassfish-web.xml"));

    // Add service provider configurations
    archive.addMetadata(
        new File("src/main/resources/META-INF/services/my.spi"),
        "META-INF/services/my.spi"
    );

    // Deploy via embedded GlassFish
    GlassFish glassfish = GlassFishRuntime.bootstrap().newGlassFish();
    glassfish.start();
    glassfish.getDeployer().deploy(archive.toURI());
}
```

## Build Commands

```bash
# Build this module only
mvn -DskipTests clean package -f nucleus/common/scattered-archive-api/pom.xml

# Build with tests
mvn clean package -f nucleus/common/scattered-archive-api/pom.xml
```

## Architecture

**Module Structure:**
- `org.glassfish.embeddable.archive` - Archive abstractions
  - `ScatteredArchive` - Main scattered archive class
  - `ScatteredEnterpriseArchive` - EAR support
  - `Assembler` - Builds deployable URIs from scattered archives

**Dependencies:**
- None (standalone API)

**Depended on by:**
- `internal-api` - Embedded server implementation
- `glassfish-api` - Public deployment APIs

## URI Creation

The `toURI()` method creates a deployable URI:

```java
ScatteredArchive archive = new ScatteredArchive("myapp", Type.WAR);
archive.addClassPath(new File("target/classes"));

// Creates a temporary archive and returns its URI
URI deployUri = archive.toURI();

// Use with any deployer
Deployer deployer = glassfish.getDeployer();
String appId = deployer.deploy(deployUri);
```

**Note:** The URI points to a temporary location in `java.io.tmpdir`.

## Common Patterns

### 1. Development Hot Reload

```java
// Setup once
ScatteredArchive archive = new ScatteredArchive("devapp", Type.WAR);
archive.addClassPath(new File("target/classes"));
archive.addClassPath(new File("src/main/resources"));
archive.addMetadata(new File("src/main/webapp/WEB-INF/web.xml"));

Deployer deployer = glassfish.getDeployer();

// Redeploy after code changes
deployer.undeploy("devapp");
deployer.deploy(archive.toURI(), "--force=true");
```

### 2. Multi-Module Maven Project

```java
// Main web module
ScatteredArchive webArchive = new ScatteredArchive("myapp", Type.WAR);
webArchive.addClassPath(new File("web/target/classes"));
webArchive.addClassPath(new File("service/target/classes"));  // Add service module
webArchive.addClassPath(new File("api/target/classes"));      // Add API module

// Add all dependency JARs
for (File jar : new File("web/target/dependency").listFiles()) {
    webArchive.addClassPath(jar);
}
```

### 3. Testing with Embedded Resources

```java
@Test
public void testScatteredDeployment() throws Exception {
    ScatteredArchive archive = new ScatteredArchive("testapp", Type.WAR);

    // Add test classes
    archive.addClassPath(new File("target/test-classes"));

    // Add test resources
    archive.addMetadata(new File("src/test/resources/test-web.xml"));

    // Deploy and test
    String appId = deployer.deploy(archive.toURI());
    // ... run tests ...
    deployer.undeploy(appId);
}
```

## Related Modules

- **simple-glassfish-api** - Embedded server API (GlassFish, Deployer)
- **internal-api** - Contains ScatteredArchive implementation
- **glassfish-api** - Deployment infrastructure
