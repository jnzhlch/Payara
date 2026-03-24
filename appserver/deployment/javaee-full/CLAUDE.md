# CLAUDE.md - Java EE Full Profile Deployment

This file provides guidance for working with the `javaee-full` module - Full Profile Java EE deployment.

## Module Overview

The javaee-full module provides deployment support for Java EE Full Profile features, primarily JSR-88 (Java EE Deployment API) for standardized deployment operations.

## Build Commands

```bash
# Build javaee-full module
mvn -DskipTests clean package -f appserver/deployment/javaee-full/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Name**: "Deployment Related JavaEE Full Profile Classes"
- **Purpose**: JSR-88 deployment support

## JSR-88 Overview

JSR-88 (J2EE Deployment API) provides a standard API for deploying Java EE applications across different application servers. It enables:

- **Portable deployment** - Same API works across servers
- **Remote deployment** - Deploy to remote servers
- **Tool integration** - IDE and management tool support

### JSR-88 Architecture

```
DeploymentManager
       │
   ├── getTargets()
   ├── distribute(Target, File)
   ├── start(TargetModuleID[])
   ├── stop(TargetModuleID[])
   ├── undeploy(TargetModuleID[])
   └── redeploy(TargetModuleID[], File)
```

## JSR-88 Components

### DeploymentManager

Main interface for deployment operations:

```java
public interface DeploymentManager {
    // Get available targets
    Target[] getTargets();
    TargetModuleID[] getAvailableModules(ModuleType type, Target[] targets);

    // Deployment operations
    ProgressObject distribute(Target[] targets, File file, File deploymentPlan);
    ProgressObject start(TargetModuleID[] moduleIDList);
    ProgressObject stop(TargetModuleID[] moduleIDList);
    ProgressObject undeploy(TargetModuleID[] moduleIDList);
    ProgressObject redeploy(TargetModuleID[] moduleIDList, File file, File deploymentPlan);

    // Release
    void release();

    // Status
    boolean isRunning();
    DeploymentConfiguration getConfiguration(Target target);
    Locale getDefaultLocale();
    void setLocale(Locale locale);
    Locale getCurrentLocale();

    // Notifications
    void addDeploymentConfigurationListener(DeploymentConfigurationListener dcl);
    void removeDeploymentConfigurationListener(DeploymentConfigurationListener dcl);
}
```

### Target

Represents a deployment target (server, cluster):

```java
public interface Target {
    String getDescription();
}
```

### TargetModuleID

Identifies a deployed module:

```java
public interface TargetModuleID {
    Target getTarget();
    String getModuleID();
    TargetModuleID[] getParentTargetModuleID();
    String getWebURL();
}
```

### ProgressObject

Tracks deployment progress:

```java
public interface ProgressObject {
    DeploymentStatus getDeploymentStatus();
    TargetModuleID[] getResultTargetModuleIDs();
    void addProgressListener(ProgressListener listener);
    void removeProgressListener(ProgressListener listener);
    void cancel();  // Cancel deployment
}
```

### DeploymentStatus

Status of deployment operation:

```java
public interface DeploymentStatus {
    // State constants
    int RUNNING = 0;
    int COMPLETED = 1;
    int FAILED = 2;
    int RELEASED = 3;

    int getState();
    String getMessage();
    Action[] getActions();
}
```

### ProgressListener

Receives deployment progress updates:

```java
public interface ProgressListener extends EventListener {
    void handleProgressEvent(ProgressEvent event);
}
```

## Module Type

### ModuleType

Identifies module types:

```java
public enum ModuleType {
    EAR,    // Enterprise Archive
    WAR,    // Web Archive
    EJB,    // EJB JAR
    RAR,    // Resource Adapter
    CAR,    // Application Client
    UNKNOWN
}
```

## Deployment Configuration

### DeploymentConfiguration

Server-specific configuration:

```java
public interface DeploymentConfiguration {
    // Properties
    DeploymentConfigurationDConfigBean getDConfigBean(DDBeanRoot bean);
    DDBeanRoot getDDBeanRoot();

    // DTD/Schema
    DConfigBeanRoot getDConfigBeanRoot(DDBeanRoot bean);
}
```

## Payara JSR-88 Implementation

### SunDeploymentManager

Payara's implementation of JSR-88:

```java
public class SunDeploymentManager implements DeploymentManager {
    private ServerConnectionIdentifier serverConn;
    private TargetImpl target;

    @Override
    public Target[] getTargets() {
        return new Target[] { target };
    }

    @Override
    public ProgressObject distribute(Target[] targets, File file, File deploymentPlan) {
        // Distribute archive to server
        // Return ProgressObject for tracking
    }

    @Override
    public ProgressObject start(TargetModuleID[] moduleIDList) {
        // Start deployed modules
    }
}
```

### SunDeploymentFactory

Factory for creating DeploymentManager instances:

```java
public class SunDeploymentFactory implements DeploymentFactory {
    @Override
    public boolean handlesURI(String uri) {
        return uri.startsWith("deployer:payara:");
    }

    @Override
    public DeploymentManager getDeploymentManager(String uri,
        String username, String password) throws DeploymentManagerCreationException {
        return new SunDeploymentManager(uri, username, password);
    }

    @Override
    public String getDisplayName() {
        return "Payara";
    }

    @Override
    public String getProductVersion() {
        return "6.x";  // Payara version
    }
}
```

### TargetImpl

Implementation of Target interface:

```java
public class TargetImpl implements Target {
    private String name;

    public TargetImpl(String name) {
        this.name = name;
    }

    @Override
    public String getDescription() {
        return name;
    }
}
```

### TargetModuleIDImpl

Implementation of TargetModuleID:

```java
public class TargetModuleIDImpl implements TargetModuleID {
    private Target target;
    private String moduleID;
    private TargetModuleID parent;
    private String webURL;

    @Override
    public Target getTarget() { return target; }

    @Override
    public String getModuleID() { return moduleID; }

    @Override
    public TargetModuleID[] getParentTargetModuleID() {
        return parent != null ? new TargetModuleID[] { parent } : null;
    }

    @Override
    public String getWebURL() { return webURL; }
}
```

## Progress Tracking

### ProgressObjectImpl

Implementation of ProgressObject:

```java
public class ProgressObjectImpl implements ProgressObject {
    private DeploymentStatusImpl status;
    private List<ProgressListener> listeners;
    private TargetModuleID[] result;

    @Override
    public DeploymentStatus getDeploymentStatus() {
        return status;
    }

    @Override
    public TargetModuleID[] getResultTargetModuleIDs() {
        return result;
    }

    @Override
    public void addProgressListener(ProgressListener listener) {
        listeners.add(listener);
    }

    // Fire progress event
    protected void fireProgressEvent(ProgressEvent event) {
        for (ProgressListener listener : listeners) {
            listener.handleProgressEvent(event);
        }
    }
}
```

### DeploymentStatusImpl

Implementation of DeploymentStatus:

```java
public class DeploymentStatusImpl implements DeploymentStatus {
    private int state;
    private String message;

    public DeploymentStatusImpl(int state, String message) {
        this.state = state;
        this.message = message;
    }

    @Override
    public int getState() { return state; }

    @Override
    public String getMessage() { return message; }

    @Override
    public Action[] getActions() {
        // Return available actions based on state
        return new Action[0];
    }
}
```

## JSR-88 Deployment Flow

```
Client Application
       │
   [DeploymentFactory.getDeploymentManager()]
       │
   DeploymentManager
       │
   [distribute(Target, File)]
       │
   ProgressObject
       │
   [ProgressListener receives events]
       │
   [start(TargetModuleID[])]
       │
   Application Started
```

## Registration

### META-INF/services

JSR-88 uses Java Service Loader:

```
META-INF/services/
└── javax.enterprise.deploy.spi.factories.DeploymentFactory
```

Content:
```
org.glassfish.deployapi.SunDeploymentFactory
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.enterprise.deploy-api` | JSR-88 API |
| `deployment-client` | Deployment client implementation |
| `glassfish-api` | Public APIs |
| `internal-api` | Internal server APIs |

## Use Cases

### IDE Integration

```java
// IDE uses JSR-88 to deploy
DeploymentFactory factory = getDeploymentFactory("deployer:payara:");
DeploymentManager dm = factory.getDeploymentManager(uri, user, pass);

File archive = new File("myapp.ear");
ProgressObject po = dm.distribute(dm.getTargets(), archive, null);

// Wait for completion
while (po.getDeploymentStatus().getState() == DeploymentStatus.RUNNING) {
    Thread.sleep(100);
}

// Start the app
TargetModuleID[] ids = po.getResultTargetModuleIDs();
dm.start(ids);
```

### Management Tools

```java
// Management tool
DeploymentManager dm = getDeploymentManager();

// List deployed apps
TargetModuleID[] modules = dm.getAvailableModules(ModuleType.EAR, dm.getTargets());

// Undeploy
dm.undeploy(modules);

// Redeploy
dm.redeploy(modules, newArchive, null);
```

## Package Structure

```
org.glassfish.javaee.full.deployment/
└── (minimal - mainly JSR-88 API support)

org.glassfish.deployapi/
├── SunDeploymentFactory.java           # JSR-88 factory
├── SunDeploymentManager.java            # JSR-88 manager
├── TargetImpl.java                      # Target implementation
├── TargetModuleIDImpl.java              # Module ID implementation
├── ProgressObjectImpl.java              # Progress tracking
├── DeploymentStatusImpl.java            # Status implementation
└── config/
    └── SunDeploymentConfiguration.java  # Configuration
```

## Related Modules

- `deployment/client` - JSR-88 client implementation
- `deployment/javaee-core` - Core deployment
- `deployment/dol` - Deployment descriptors
