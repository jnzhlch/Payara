# CLAUDE.md - WAR Utilities

This file provides guidance for working with the `appserver/web/war-util` module - WAR deployment utilities.

## Module Overview

The war-util module provides utilities for WAR (Web Application Archive) deployment, including classloading, manifest processing, and archive handling.

**Key Components:**
- **WebappLoader** - Web application classloader
- **WebArchiveHandler** - WAR archive handler
- **Manifest processing** - MANIFEST.MF parsing

## Build Commands

```bash
# Build war-util module
mvn -DskipTests clean package -f appserver/web/war-util/pom.xml
```

## Module Contents

```
war-util/
└── src/main/java/org/glassfish/web/
    ├── loader/
    │   └── WebappLoader.java
    ├── deployment/
    │   └── WebArchiveHandler.java
    └── util/
        └── ...
```

## WebappLoader

```java
public class WebappLoader extends LifecycleBase
        implements Loader, PropertyChangeListener, Runnable {

    private ClassLoader classLoader;
    private WebApplicationClassLoader webAppClassLoader;

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public void startInternal() throws LifecycleException {
        // Create classloader for web application
        webAppClassLoader = createWebappClassLoader();

        // Configure classpath
        configureClasspath(webAppClassLoader);

        classLoader = webAppClassLoader;
    }

    protected WebApplicationClassLoader createWebappClassLoader() {
        // Create classloader with delegation
        return new WebApplicationClassLoader(
            getParentClassLoader(),
            getContext()
        );
    }
}
```

## WebArchiveHandler

```java
@Service
@ArchiveHandler(WarArchiveType.ARCHIVE_TYPE)
public class WebArchiveHandler extends GenericArchiveHandler {

    @Override
    public String getArchiveType() {
        return WarArchiveType.ARCHIVE_TYPE;
    }

    @Override
    public String getVersionIdentifier(ReadableArchive archive) {
        // Get version from MANIFEST.MF or file name
        return getManifestVersion(archive);
    }

    @Override
    public boolean handles(ReadableArchive archive) {
        return isWarArchive(archive);
    }

    @Override
    public ClassLoader getClassLoader(ClassLoader parent, DeploymentContext context) {
        // Create classloader for WAR
        return createWarClassLoader(parent, context);
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `deployment-common` | Deployment utilities |
| `dol` | Deployment descriptors |

## Notes

- **WAR Classloading** - Web application classloader with delegation
- **Archive Handling** - WAR file processing
- **Manifest Processing** - MANIFEST.MF parsing
