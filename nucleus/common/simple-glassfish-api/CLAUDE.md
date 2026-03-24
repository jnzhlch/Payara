# CLAUDE.md - Simple GlassFish API

This file provides guidance for working with the `simple-glassfish-api` module - the embedded GlassFish API.

## Module Overview

The `simple-glassfish-api` module provides the public API for embedding Payara/GlassFish server in Java applications. It allows programmatic startup, deployment, command execution, and lifecycle management of a Payara instance from within your own application.

**Note:** This module's contents are bundled by the `core/bootstrap` module into `glassfish.jar`.

## Key APIs

### GlassFish - Main Server Interface

The primary entry point for embedded Payara:

```java
// Bootstrap and create a GlassFish instance
GlassFish glassfish = GlassFishRuntime.bootstrap().newGlassFish();
glassfish.start();

// Get deployer and deploy applications
Deployer deployer = glassfish.getDeployer();
String appId = deployer.deploy(new File("app.war").toURI(), "--contextroot=myapp");

// Run asadmin commands programmatically
CommandRunner runner = glassfish.getCommandRunner();
CommandResult result = runner.run("create-http-listener", "--listenerport=9090", "...");

// Shutdown
glassfish.stop();
glassfish.dispose();
```

### Lifecycle States

A `GlassFish` instance progresses through these states:
- `INIT` - Newly created, not started
- `STARTING` - Startup in progress
- `STARTED` - Fully running
- `STOPPING` - Shutdown in progress
- `STOPPED` - Stopped (can be restarted)
- `DISPOSED` - Resources released, cannot be used

### GlassFishRuntime - Bootstrap Entry Point

```java
// Bootstrap with custom properties
BootstrapProperties props = new BootstrapProperties();
GlassFishRuntime runtime = GlassFishRuntime.bootstrap(props);

// Create new GlassFish instance with properties
GlassFishProperties gfProps = new GlassFishProperties();
gfProps.setInstanceRoot("path/to/glassfish");
gfProps.setConfigFileURI("path/to/domain.xml");
GlassFish glassfish = runtime.newGlassFish(gfProps);
```

### Deployer - Application Deployment

```java
Deployer deployer = glassfish.getDeployer();

// Deploy from file/URI
String appId = deployer.deploy(new File("app.war").toURI());

// Deploy with parameters
String appId = deployer.deploy(archive.toURI(), "--contextroot=myapp", "--force=true");

// Undeploy
deployer.undeploy(appId);
```

### CommandRunner - Asadmin Command Execution

```java
CommandRunner runner = glassfish.getCommandRunner();

// Run any asadmin command
CommandResult result = runner.run("list-applications");
if (result.getStatus() == CommandResult.Status.SUCCESS) {
    String output = result.getOutput();
}

// Commands with parameters
runner.run("create-jdbc-connection-pool", "--datasourceclassname=...", "myPool");
```

## Scattered Archive Integration

Works with `scattered-archive-api` to deploy exploded/unpackaged applications:

```java
ScatteredArchive archive = new ScatteredArchive("myapp", ScatteredArchive.Type.WAR);
archive.addClassPath(new File("target/classes"));
archive.addMetadata(new File("src/main/webapp/WEB-INF/web.xml"));

Deployer deployer = glassfish.getDeployer();
deployer.deploy(archive.toURI());
```

## Build Commands

```bash
# Build this module only
mvn -DskipTests clean package -f nucleus/common/simple-glassfish-api/pom.xml

# Build with tests
mvn clean package -f nucleus/common/simple-glassfish-api/pom.xml

# Install to local repository
mvn install -f nucleus/common/simple-glassfish-api/pom.xml
```

## Architecture

**Module Structure:**
- `org.glassfish.embeddable` - Core embedded API
  - `GlassFishRuntime` - Bootstrap and runtime
  - `GlassFish` - Server instance and lifecycle
  - `Deployer` - Application deployment
  - `CommandRunner` - Command execution
  - `CommandResult` - Command execution results
  - `GlassFishProperties` - Configuration properties
  - `BootstrapProperties` - Runtime bootstrap properties
- `org.glassfish.embeddable.spi` - Service provider interface (for implementors)

**Packaging:** Uses `glassfish-jar` packaging type for OSGi compatibility.

## Dependencies

- **None** - This is a minimal API module with no runtime dependencies

**Depended on by:**
- `glassfish-api` - Full public APIs
- `scattered-archive-api` - Scattered archive support
- Embedded server implementations in `nucleus/core/embedded`

## Common Use Cases

### 1. Quick Server Startup for Testing

```java
@Test
public void testWithEmbeddedServer() throws Exception {
    GlassFish glassfish = GlassFishRuntime.bootstrap().newGlassFish();
    glassfish.start();

    // Deploy test application
    Deployer deployer = glassfish.getDeployer();
    deployer.deploy(testApp.toURI());

    // Run tests...

    glassfish.dispose();
}
```

### 2. Programmatic Configuration

```java
// Create HTTP listener
CommandRunner runner = glassfish.getCommandRunner();
runner.run("create-http-listener",
    "--listenerport=9090",
    "--listeneraddress=0.0.0.0",
    "--default-virtual-server=server",
    "my-listener");

// Create thread pool
runner.run("create-threadpool",
    "--maxthreadpoolsize=200",
    "--minthreadpoolsize=10",
    "my-pool");

// Associate pool with listener
runner.run("set",
    "server.network-config.network-listeners.network-listener.my-listener.thread-pool=my-pool");
```

### 3. Custom Installation Location

```java
GlassFishProperties props = new GlassFishProperties();
props.setInstanceRoot("/opt/payara");
props.setConfigFileURI("file:///opt/payara/config/custom-domain.xml");

GlassFish glassfish = GlassFishRuntime.bootstrap().newGlassFish(props);
```

## Service Location

Services can be retrieved from a running `GlassFish` instance:

```java
// Get any HK2 service
MyService service = glassfish.getService(MyService.class);

// Get named service
MyService service = glassfish.getService(MyService.class, "myServiceName");
```

## Related Modules

- **scattered-archive-api** - For deploying unpackaged applications
- **internal-api** - Contains embedded server implementation
- **core/bootstrap** - Bundles this module into glassfish.jar
