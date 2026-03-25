# CLAUDE.md - GlassFish Weld Connector

This file provides guidance for working with the `appserver/web/gf-weld-connector` module - CDI (Weld) integration connector for GlassFish/Payara.

## Module Overview

The gf-weld-connector module provides the integration layer between GlassFish/Payara and Weld (CDI implementation). It handles CDI application detection, configuration, and service integration.

**Key Components:**
- **WeldSniffer** - CDI application type detection
- **CDIService** - CDI configuration service
- **WeldUtils** - Weld utility methods

## Build Commands

```bash
# Build gf-weld-connector module
mvn -DskipTests clean package -f appserver/web/gf-weld-connector/pom.xml
```

## Architecture

```
Application Deployment
       │
       ▼
[WeldSniffer]
       │
       ├─→ Detects beans.xml
       ├─→ Detects @Inject, @Dependent annotations
       └─→ Sets CDI type
       │
       ▼
[CDIService Configuration]
       │
       ├─→ enableImplicitCdi
       ├─→ enableConcurrentDeployment
       └─→ preLoaderThreadPoolSize
       │
       ▼
[Weld Container Initialization]
       │
       ├─→ Weld bootstrap
       ├─→ Bean discovery
       └─→ CDI context activation
```

## Weld Detection

### WeldSniffer

```java
@Service
public class WeldSniffer implements Sniffer {

    @Override
    public boolean handles(String type) {
        return "cdi".equals(type) || "weld".equals(type);
    }

    @Override
    public String[] getContainers() {
        return new String[] { "org.glassfish.weld.WeldContainer" };
    }

    @Override
    public boolean handles(ReadableArchive archive) {
        // Detect CDI applications by:
        // 1. META-INF/beans.xml
        // 2. WEB-INF/beans.xml
        // 3. CDI annotations in class files
        return archive.exists("META-INF/beans.xml") ||
               archive.exists("WEB-INF/beans.xml") ||
               WeldUtils.hasCDIAnnotatedClasses(archive);
    }
}
```

### WeldCompositeSniffer

```java
@Service
public class WeldCompositeSniffer implements Sniffer {

    @Override
    public String[] getContainers() {
        // Composite sniffer for weld-related containers
        return new String[] {
            "org.glassfish.weld.WeldContainer",
            "org.glassfish.weld.ejb.WeldEjbContainer"
        };
    }
}
```

## CDI Configuration

### CDIService

```java
@Configured
public interface CDIService extends ConfigExtension {

    /**
     * Enable implicit CDI for applications without beans.xml.
     * Default: true
     */
    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getEnableImplicitCdi();

    /**
     * Enable concurrent CDI deployment.
     * Default: false
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getEnableConcurrentDeployment();

    /**
     * Thread pool size for CDI preloader.
     * Default: 0 (auto)
     */
    @Attribute(defaultValue = "0", dataType = Integer.class)
    String getPreLoaderThreadPoolSize();
}
```

### Configuration in domain.xml

```xml
<configs>
    <config name="server-config">
        <cdi-service enable-implicit-cdi="true"
                     enable-concurrent-deployment="false"
                     pre-loader-thread-pool-size="0"/>
    </config>
</configs>
```

## Weld Utilities

### WeldUtils

```java
public class WeldUtils {

    /**
     * Check if archive has CDI annotated classes.
     */
    public static boolean hasCDIAnnotatedClasses(ReadableArchive archive) {
        // Scan for @Inject, @Dependent, @ApplicationScoped, etc.
    }

    /**
     * Get beans.xml location for archive.
     */
    public static String getBeansXmlLocation(ReadableArchive archive) {
        if (archive.exists("WEB-INF/beans.xml")) {
            return "WEB-INF/beans.xml";
        }
        if (archive.exists("META-INF/beans.xml")) {
            return "META-INF/beans.xml";
        }
        return null;
    }

    /**
     * Check if bean discovery mode is explicit.
     */
    public static boolean isExplicitBeanDiscovery(String beansXml) {
        // Parse beans.xml and check bean-discovery-mode
    }
}
```

## CDI Application Detection

### beans.xml Detection

```xml
<!-- WEB-INF/beans.xml for web applications -->
<beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                           https://jakarta.ee/xml/ns/jakartaee/beans_4_0.xsd"
       version="4.0"
       bean-discovery-mode="all">
</beans>
```

```xml
<!-- META-INF/beans.xml for JAR applications -->
<beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                           https://jakarta.ee/xml/ns/jakartaee/beans_4_0.xsd"
       version="4.0"
       bean-discovery-mode="annotated">
</beans>
```

### Bean Discovery Modes

| Mode | Description |
|------|-------------|
| `all` | All types are CDI beans |
| `annotated` | Only annotated types are beans |
| `none` | No automatic bean discovery |

## Implicit CDI

With `enable-implicit-cdi="true"`, applications without beans.xml can still use CDI:

```java
// No beans.xml required
@ApplicationScoped
public class MyService {

    @Inject
    private SomeDependency dependency;
}
```

## Concurrent Deployment

With `enable-concurrent-deployment="true"`, CDI deployment can be parallelized:

```xml
<cdi-service enable-concurrent-deployment="true"
             pre-loader-thread-pool-size="4"/>
```

This enables:
- Parallel bean class scanning
- Concurrent bean discovery
- Multi-threaded proxy generation

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | GlassFish internal APIs |
| `hk2-core` | HK2 injection framework |
| `weld` | CDI implementation |

## Integration Points

```
Application
       │
       ├─→ WeldSniffer detects CDI
       │
       ▼
WeldContainer
       │
       ├─→ WeldDeployer (from weld-integration module)
       ├─→ CDI extensions
       └─→ BeanManager
       │
       ▼
CDI Contexts
       │
       ├─→ @RequestScoped
       ├─→ @SessionScoped
       ├─→ @ApplicationScoped
       ├─→ @ConversationScoped
       └─→ @TransactionScoped (Payara extension)
```

## Notes

- **Implicit CDI** - Enabled by default in Payara
- **Concurrent Deployment** - Experimental feature for faster startup
- **Bean Discovery** - beans.xml optional with implicit CDI
- **CDI 4.0** - Jakarta EE 10 CDI specification
