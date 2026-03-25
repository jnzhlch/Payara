# CLAUDE.md - GlassFish Web Connector

This file provides guidance for working with the `appserver/web/gf-web-connector` module - GlassFish web container integration connector.

## Module Overview

The gf-web-connector module provides the integration layer between GlassFish/Payara and the web container. It handles web application detection, configuration, and context initialization.

**Key Components:**
- **WarDetector/WarType** - WAR application type detection
- **WebContainer** - Web container configuration
- **ContextualizerImpl** - Java EE context integration

## Build Commands

```bash
# Build gf-web-connector module
mvn -DskipTests clean package -f appserver/web/gf-web-connector/pom.xml
```

## Architecture

```
Application Deployment
       │
       ▼
[WarDetector]
       │
       ├─→ Detects WAR files
       ├─→ Sets WarType
       └─→ Triggers web container
       │
       ▼
[WebSniffer]
       │
       ├─→ Scans for web.xml
       ├─→ Detects web fragments
       └─→ Identifies web annotations
       │
       ▼
[WebContainer Configuration]
       │
       ├─→ WebModuleConfig
       ├─→ SessionConfig
       └─→ WebContainerAvailability
```

## War Detection

### WarDetector

```java
@Service
@Scoped(Singleton.class)
public class WarDetector implements Detector {

    @Override
    public boolean handles(ReadableArchive archive) {
        // Detect WAR files by presence of WEB-INF/web.xml
        // or @WebServlet annotation in classes
        return archive.exists("WEB-INF/web.xml") ||
               hasWebAnnotations(archive);
    }

    @Override
    public Detector getSniffer() {
        return new WarType();
    }
}
```

### WarType

```java
@Service
public class WarType implements Sniffer {

    @Override
    public String[] getContainers() {
        return new String[] { "org.glassfish.web.jetty.JettyContainer" };
    }

    @Override
    public boolean handles(String type) {
        return "war".equals(type);
    }
}
```

## Configuration Beans

### WebContainer

```java
@Configured
public interface WebContainer extends ConfigBeanProxy {

    /**
     * Get the session configuration for this container.
     */
    @Element
    SessionConfig getSessionConfig();

    /**
     * Get the availability (HA) configuration.
     */
    @Element
    WebContainerAvailability getAvailability();

    /**
     * Disable Jakarta EE context for JSP/JSF.
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getDisableNonFatalAlerts();

    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getEnableImplicitCdi();
}
```

### SessionConfig

```java
@Configured
public interface SessionConfig extends ConfigBeanProxy {

    /**
     * Session manager type.
     */
    @Attribute(defaultValue = "builtin")
    String getSessionManager();

    @Element
    SessionManager getSessionManagerBean();

    @Element
    ManagerProperties getManagerProperties();

    @Element
    SessionProperties getSessionProperties();

    @Element
    StoreProperties getStoreProperties();
}
```

### WebModuleConfig

```java
@Configured
public interface WebModuleConfig extends ConfigBeanProxy {

    /**
     * Directory for JSP precompilation.
     */
    @Attribute(defaultValue = "${com.sun.aas.instanceRoot}/generated/jsp")
    String getJspConfig();

    /**
     * Whether to verify JSPs while loading.
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getJspVerifyCompile();

    /**
     * Whether to keep generated JSP servlets.
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getJspKeepgenerated();

    /**
     * Whether to enable JSP tag pooling.
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getJspTagPooling();
}
```

### WebContainerAvailability

```java
@Configured
public interface WebContainerAvailability extends ConfigBeanProxy {

    /**
     * Enable web session persistence.
     */
    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getAvailabilityEnabled();

    /**
     * Persistence type (memory, file, replicated).
     */
    @Attribute(defaultValue = "memory")
    String getPersistenceType();

    /**
     * Persistence frequency (web-method, time-based).
     */
    @Attribute(defaultValue = "web-method")
    String getPersistenceFrequency();

    /**
     * Persistence scope (session, modified-session, modified-attribute).
     */
    @Attribute(defaultValue = "session")
    String getPersistenceScope();
}
```

## Context Integration

### ContextualizerImpl

```java
@Service
public class ContextualizerImpl implements Contextualizer {

    @Override
    public void contextualize(Context ctx) {
        // Set up Java EE context for web components
        ctx.setJavaEEContextUtil(new JavaEEContextUtilImpl());
    }

    @Override
    public void start(Context ctx) {
        // Initialize context
    }

    @Override
    public void stop(Context ctx) {
        // Cleanup context
    }
}
```

### ContextImpl

```java
public class ContextImpl implements Context {

    private ClassLoader classLoader;
    private JavaEEContextUtil javaEEContextUtil;

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public void setClassLoader(ClassLoader cl) {
        this.classLoader = cl;
    }

    @Override
    public JavaEEContextUtil getJavaEEContextUtil() {
        return javaEEContextUtil;
    }

    @Override
    public void setJavaEEContextUtil(JavaEEContextUtil util) {
        this.javaEEContextUtil = util;
    }
}
```

## domain.xml Configuration

```xml
<config name="server-config">
    <web-container availability-enabled="true">
        <session-config>
            <session-manager persistence-type="replicated">
                <manager-properties session-file-name="sessions"/>
                <session-properties>
                    <property name="timeoutSeconds" value="1800"/>
                </session-properties>
                <store-properties/>
            </session-manager>
        </session-config>

        <web-module-config>
            <property name="jspConfig" value="${com.sun.aas.instanceRoot}/generated/jsp"/>
            <property name="jspVerifyCompile" value="false"/>
            <property name="jspKeepgenerated" value="false"/>
            <property name="jspTagPooling" value="true"/>
        </web-module-config>

        <availability-enabled>true</availability-enabled>
    </web-container>
</config>
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | GlassFish internal APIs |
| `container-common` | Common container interfaces |
| `config-api` | Configuration API |
| `dol` | Deployment Object Library |

## Notes

- **Sniffer Pattern** - Detects WAR applications during deployment
- **Configuration** - Web container and session management configuration
- **Context Integration** - Java EE context for web components
- **Session HA** - High availability session replication support
