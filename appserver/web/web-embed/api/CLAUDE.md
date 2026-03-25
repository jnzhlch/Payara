# CLAUDE.md - Web Embed

This file provides guidance for working with the `appserver/web/web-embed` module - Web container embedded API modules.

## Module Overview

The web-embed module is a parent POM for embedded web container functionality. It contains the `api` submodule that provides APIs for embedding the Payara web container in other applications.

**Purpose:** Enable embedded web container usage.

## Build Commands

```bash
# Build web-embed module (includes api submodule)
mvn -DskipTests clean package -f appserver/web/web-embed/pom.xml
```

## Module Structure

```
web-embed/
├── pom.xml (parent)
└── api/
    ├── src/main/java/
    │   └── org/glassfish/web/embed/
    │       └── impl/
    │           ├── GlassFishProperties.java
    │           ├── WebContainer.java
    │           └── WebEmbeddedException.java
    └── pom.xml
```

## Embedded Web Container Usage

### Basic Embedded Setup

```java
import org.glassfish.web.embed.impl.WebContainer;
import org.glassfish.web.embed.impl.GlassFishProperties;

public class EmbeddedServer {

    public static void main(String[] args) throws Exception {
        // Configure embedded server
        GlassFishProperties props = new GlassFishProperties();

        // Set server properties
        props.setPort(8080);
        props.setInstanceRoot("/path/to/glassfish");

        // Create web container
        WebContainer container = WebContainer.newInstance(props);

        // Deploy application
        container.deploy("/path/to/app.war", "/app");

        // Start container
        container.start();

        System.out.println("Server running on http://localhost:8080/app");

        // Keep running
        Thread.sleep(Long.MAX_VALUE);
    }
}
```

### Configuration Properties

```java
GlassFishProperties props = new GlassFishProperties();

// Basic configuration
props.setPort(8080);                           // HTTP port
props.setInstanceRoot("/path/to/instance");    // Instance directory
props.setConfigFileURI("file:///path/to/domain.xml");

// JVM configuration
props.setJvmArgs("-Xmx512m -XX:MaxPermSize=256m");

// Multiple listeners
props.setConfigFileURI("file:///config/domain.xml");
```

### WebContainer API

```java
public interface WebContainer {

    /**
     * Create new embedded web container.
     */
    static WebContainer newInstance(GlassFishProperties properties) {
        // Implementation
    }

    /**
     * Start the web container.
     */
    void start() throws WebEmbeddedException;

    /**
     * Stop the web container.
     */
    void stop() throws WebEmbeddedException;

    /**
     * Deploy a web application.
     *
     * @param archivePath Path to WAR file or exploded directory
     * @param contextRoot Context root for the application
     */
    void deploy(String archivePath, String contextRoot)
            throws WebEmbeddedException;

    /**
     * Undeploy a web application.
     */
    void undeploy(String contextRoot)
            throws WebEmbeddedException;

    /**
     * Get container status.
     */
    Status getStatus();
}
```

## Deployment Scenarios

### Deploy WAR File

```java
WebContainer container = WebContainer.newInstance(props);
container.start();

// Deploy from WAR file
container.deploy("/path/to/myapp.war", "/myapp");

// Access at http://localhost:8080/myapp
```

### Deploy Exploded Directory

```java
// Deploy from exploded directory
container.deploy("/path/to/exploded/app", "/app");
```

### Deploy Multiple Applications

```java
WebContainer container = WebContainer.newInstance(props);
container.start();

container.deploy("/apps/app1.war", "/app1");
container.deploy("/apps/app2.war", "/app2");
container.deploy("/apps/api.war", "/api");
```

## Programmatic Deployment

```java
// Create custom deployment
WebContainer container = WebContainer.newInstance(props);
container.start();

// Deploy with custom configuration
Deployment deployment = container.deploy(
    new Deployment()
        .archive(new File("myapp.war"))
        .contextRoot("/myapp")
        .name("myapp")
        .classLoader(myClassLoader)
);
```

## Use Cases

### Testing

```java
public class MyServletTest {

    private static WebContainer container;

    @BeforeAll
    static void startServer() throws Exception {
        GlassFishProperties props = new GlassFishProperties();
        props.setPort(8080);
        container = WebContainer.newInstance(props);
        container.start();
        container.deploy("target/myapp.war", "/test");
    }

    @AfterAll
    static void stopServer() throws Exception {
        container.stop();
    }

    @Test
    void testServlet() {
        // Test servlet at http://localhost:8080/test
    }
}
```

### Microservices

```java
public class MicroserviceMain {

    public static void main(String[] args) throws Exception {
        GlassFishProperties props = new GlassFishProperties();
        props.setPort(System.getenv().getOrDefault("PORT", "8080"));

        WebContainer container = WebContainer.newInstance(props);
        container.start();
        container.deploy("microservice.war", "/");

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                container.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }
}
```

### Desktop Application

```java
public class DesktopAppWithEmbeddedServer {

    public static void main(String[] args) throws Exception {
        // Start embedded server for REST API
        GlassFishProperties props = new GlassFishProperties();
        props.setPort("9515");
        WebContainer container = WebContainer.newInstance(props);
        container.start();
        container.deploy("api.war", "/api");

        // Launch desktop UI
        launchDesktopUI();
    }
}
```

## Module Contents

### API Module

- **GlassFishProperties** - Configuration properties for embedded container
- **WebContainer** - Main embedded web container interface
- **WebEmbeddedException** - Exception for embedded container errors

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `web-core` | Web container implementation |
| `weld-integration` | CDI integration |
| `grizzly` | HTTP connector |

## Notes

- **Parent POM** - Contains api submodule
- **Embedded Usage** - For programmatic web container embedding
- **Testing** - Useful for integration tests
- **Microservices** - Enables standalone WAR deployment
