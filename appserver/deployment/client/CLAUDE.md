# CLAUDE.md - Deployment Client

This file provides guidance for working with the `client` module - Deployment client implementation and JSR-88 support.

## Module Overview

The deployment client module provides the client-side deployment API implementation for JSR-88 (Java EE Deployment API). It enables remote deployment operations and tool integration.

## Build Commands

```bash
# Build client module
mvn -DskipTests clean package -f appserver/deployment/client/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Name**: "Deployment Client Classes and Interfaces"
- **Purpose**: JSR-88 deployment client

## Core Interfaces

### DeploymentFacility

Main deployment interface:

```java
public interface DeploymentFacility {
    String STUBS_JARFILENAME = "clientstubs.jar";

    // Connection management
    boolean connect(ServerConnectionIdentifier targetDAS);
    boolean disconnect();
    boolean isConnected();

    // Deployment operations
    ProgressObject deploy(DeploymentTarget target,
        ReadableArchive source, File deploymentPlan,
        Map<String, String> deploymentOptions);

    ProgressObject undeploy(TargetModuleID[] moduleID);

    ProgressObject start(TargetModuleID[] moduleID);
    ProgressObject stop(TargetModuleID[] moduleID);

    ProgressObject redeploy(TargetModuleID[] moduleID,
        ReadableArchive source, File deploymentPlan);

    // Query operations
    TargetModuleID[] getTargetModuleIDs();
    Target[] getTargets();

    // Client stubs
    File retrieveStubs(String appName);
}
```

### ServerConnectionIdentifier

Identifies the server to connect to:

```java
public class ServerConnectionIdentifier {
    private String hostName;
    private int hostPort;
    private boolean secure;
    private String userName;
    private String password;

    // Getters and setters
    public String getHostName();
    public int getHostPort();
    public boolean isSecure();
    public String getUserName();
    public String getPassword();
}
```

## Implementations

### RemoteDeploymentFacility

Remote deployment facility:

```java
public class RemoteDeploymentFacility extends AbstractDeploymentFacility {
    private DeploymentFacility deploymentFacility;

    @Override
    public boolean connect(ServerConnectionIdentifier targetDAS) {
        // Connect to remote DAS
        deploymentFacility = createDeploymentFacility(targetDAS);
        return true;
    }

    @Override
    public ProgressObject deploy(DeploymentTarget target,
        ReadableArchive source, File deploymentPlan,
        Map<String, String> deploymentOptions) {

        // Create deployment command
        // Send to server
        // Return progress object
    }
}
```

### DFDeploymentStatus

Deployment status implementation:

```java
public class DFDeploymentStatus {
    private String status;  // "success", "failure", "in_progress"
    private String message;
    private Throwable exception;

    public String getStatus();
    public String getMessage();
    public Throwable getException();

    public boolean isFailure();
    public boolean isSuccess();
}
```

### DFProgressObject

Progress object implementation:

```java
public class DFProgressObject implements ProgressObject {
    private DFDeploymentStatus status;
    private List<TargetModuleID> resultModules;
    private List<ProgressListener> listeners;

    @Override
    public DeploymentStatus getDeploymentStatus() {
        return status;
    }

    @Override
    public TargetModuleID[] getResultTargetModuleIDs() {
        return resultModules.toArray(new TargetModuleID[0]);
    }

    @Override
    public void addProgressListener(ProgressListener listener) {
        listeners.add(listener);
    }

    protected void notifyProgress() {
        ProgressEvent event = new ProgressEvent(status, this);
        for (ProgressListener listener : listeners) {
            listener.handleProgressEvent(event);
        }
    }
}
```

## JSR-88 Implementation

### SunDeploymentManager

Payara's JSR-88 DeploymentManager:

```java
public class SunDeploymentManager implements DeploymentManager {
    private ServerConnectionIdentifier serverConn;
    private DeploymentFacility facility;

    public SunDeploymentManager(String uri, String username, String password) {
        this.serverConn = new ServerConnectionIdentifier();
        this.serverConn.setUserName(username);
        this.serverConn.setPassword(password);
        // Parse URI for host/port
    }

    @Override
    public Target[] getTargets() {
        return new Target[] { new TargetImpl("server") };
    }

    @Override
    public ProgressObject distribute(Target[] targets, File file, File plan) {
        try {
            ReadableArchive archive = new InputJarArchive(file);
            return facility.deploy(new DeploymentTarget(targets[0].getDescription()),
                archive, plan, null);
        } catch (IOException e) {
            return createFailedProgress(e);
        }
    }

    @Override
    public ProgressObject start(TargetModuleID[] moduleIDList) {
        return facility.start(moduleIDList);
    }

    @Override
    public ProgressObject stop(TargetModuleID[] moduleIDList) {
        return facility.stop(moduleIDList);
    }

    @Override
    public ProgressObject undeploy(TargetModuleID[] moduleIDList) {
        return facility.undeploy(moduleIDList);
    }

    @Override
    public ProgressObject redeploy(TargetModuleID[] moduleIDList,
        File file, File plan) {
        try {
            ReadableArchive archive = new InputJarArchive(file);
            return facility.redeploy(moduleIDList, archive, plan);
        } catch (IOException e) {
            return createFailedProgress(e);
        }
    }

    @Override
    public void release() {
        facility.disconnect();
    }
}
```

### SunDeploymentFactory

Factory for creating DeploymentManager:

```java
public class SunDeploymentFactory implements DeploymentFactory {
    @Override
    public boolean handlesURI(String uri) {
        return uri.startsWith("deployer:payara:") ||
               uri.startsWith("deployer:Sun:") ||  // Legacy
               uri.startsWith("deployer:GlassFish:");  // Legacy
    }

    @Override
    public DeploymentManager getDeploymentManager(String uri,
        String username, String password) throws DeploymentManagerCreationException {

        try {
            return new SunDeploymentManager(uri, username, password);
        } catch (Exception e) {
            throw new DeploymentManagerCreationException(e.getMessage(), e);
        }
    }

    @Override
    public String getDisplayName() {
        return "Payara";
    }

    @Override
    public String getProductVersion() {
        return "6.x";  // Payara version
    }

    @Override
    public boolean isDFProfileSupported(Profile profile) {
        return profile == Profile.JAVAEE;  // Supports full Java EE
    }
}
```

## Deployment Configuration

### SunDeploymentConfiguration

Server-specific deployment configuration:

```java
public class SunDeploymentConfiguration implements DeploymentConfiguration {
    private ConfigBeanArchive configBeanArchive;

    public SunDeploymentConfiguration(DeploymentManager dm) {
        // Initialize configuration
    }

    @Override
    public DeploymentConfigurationDConfigBean getDConfigBean(DDBeanRoot bean) {
        // Return configuration bean for descriptor
        return new SunConfigBean(bean);
    }

    @Override
    public DDBeanRoot getDDBeanRoot() {
        return configBeanArchive.getDDBeanRoot();
    }

    @Override
    public DConfigBeanRoot getDConfigBeanRoot(DDBeanRoot bean) {
        return new SunConfigBeanRoot(bean);
    }

    @Override
    public void restore(InputStream input) throws Exception {
        // Restore saved configuration
    }

    @Override
    public void save(OutputStream output) throws Exception {
        // Save configuration
    }

    @Override
    public void removeDConfigBean(DeploymentConfigurationDConfigBean bean) {
        // Remove configuration bean
    }
}
```

## Command Execution

### AbstractDeploymentFacility

Abstract base for deployment facilities:

```java
public abstract class AbstractDeploymentFacility implements DeploymentFacility {
    // Execute deployment command
    protected DFDeploymentStatus executeDeployCommand(File file,
        Map<String, String> options) {

        // Create deployment command XML
        String command = createDeployCommand(file, options);

        // Send to server
        String response = sendCommand(command);

        // Parse response
        return parseResponse(response);
    }

    // Create deployment command
    protected String createDeployCommand(File file,
        Map<String, String> options) {

        // Generate command XML for asadmin
        StringBuilder cmd = new StringBuilder();
        cmd.append("<deploy>");
        cmd.append("<path>").append(file.getAbsolutePath()).append("</path>");
        for (Map.Entry<String, String> entry : options.entrySet()) {
            cmd.append("<option name=\"").append(entry.getKey())
               .append("\">").append(entry.getValue()).append("</option>");
        }
        cmd.append("</deploy>");
        return cmd.toString();
    }
}
```

## Deployment Properties

### DFDeploymentProperties

Standard deployment properties:

```java
public class DFDeploymentProperties {
    // Application name
    public static final String NAME = "name";

    // Context root (for web apps)
    public static final String CONTEXT_ROOT = "contextroot";

    // Deployment target
    public static final String TARGET = "target";

    // Enabled
    public static final String ENABLED = "enabled";

    // Force deployment
    public static final String FORCE = "force";

    // Keep state
    public static final String KEEP_STATE = "keepstate";

    // Verify
    public static final String VERIFY = "verify";

    // Precompile JSPs
    public static final String PRECOMPILE_JSP = "precompilejsp";

    // Availability enabled
    public static final String AVAILABILITY_ENABLED = "availabilityenabled";

    // ASYNC replication
    public static final String ASYNC_REPLICATION = "asyncreplication";

    // Keep sessions
    public static final String KEEP_SESSIONS = "keepsessions";

    // Load only (don't start)
    public static final String LOAD_ONLY = "loadonly";

    // Hot deploy
    public static final String HOT_DEPLOY = "hotdeploy";
}
```

## Progress Tracking

### ProgressObjectImpl

Progress tracking implementation:

```java
public class ProgressObjectImpl implements ProgressObject {
    private DeploymentStatusImpl status;
    private ProgressObjectSink sink;

    public ProgressObjectImpl(ProgressObjectSink sink) {
        this.sink = sink;
        this.status = new DeploymentStatusImpl(DeploymentStatus.RUNNING, "");
    }

    public void complete(TargetModuleID[] result) {
        status = new DeploymentStatusImpl(DeploymentStatus.COMPLETED, "Deployment completed");
        this.result = result;
        notifyCompletion();
    }

    public void fail(String message, Throwable exception) {
        status = new DeploymentStatusImpl(DeploymentStatus.FAILED, message);
        this.exception = exception;
        notifyCompletion();
    }

    private void notifyCompletion() {
        sink.notifyCompletion(this);
    }
}
```

## Package Structure

```
org.glassfish.deployapi/
├── SunDeploymentFactory.java           # JSR-88 factory
├── SunDeploymentManager.java            # JSR-88 manager
├── TargetImpl.java                      # Target implementation
├── TargetModuleIDImpl.java              # Module ID implementation
├── ProgressObjectImpl.java              # Progress tracking
├── DeploymentStatusImpl.java            # Status implementation
├── DeploymentStatusImplWithError.java   # Status with error
├── SimpleProgressObjectImpl.java        # Simple progress
├── ProgressObjectSink.java              # Progress sink
└── config/
    ├── SunDeploymentConfiguration.java  # Configuration
    ├── SunConfigBean.java              # Config bean
    ├── ConfigBeanArchive.java          # Config archive
    └── ConfigBeanClassLoader.java      # ClassLoader

org.glassfish.deployment.client/
├── DeploymentFacility.java              # Main interface
├── AbstractDeploymentFacility.java      # Abstract base
├── RemoteDeploymentFacility.java        # Remote implementation
├── DeploymentFacilityFactory.java       # Factory
├── ServerConnectionIdentifier.java      # Connection info
├── DFDeploymentStatus.java             # Status
├── DFProgressObject.java               # Progress
├── DFDeploymentProperties.java         # Properties
└── CommandXMLResultParser.java         # Response parser
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.enterprise.deploy-api` | JSR-88 API |
| `glassfish-api` | Public APIs |
| `internal-api` | Internal server APIs |
| `deployment-common` | Shared deployment utilities |
| `deployment-javaee-full` | Full profile support |

## Use Cases

### Programmatic Deployment

```java
// Create deployment facility
DeploymentFacility facility = DeploymentFacilityFactory.getDeploymentFacility();

// Connect to server
ServerConnectionIdentifier id = new ServerConnectionIdentifier();
id.setHostName("localhost");
id.setHostPort(4848);
id.setUserName("admin");
id.setPassword("");
facility.connect(id);

// Deploy
File archive = new File("myapp.ear");
Map<String, String> options = new HashMap<>();
options.put(DFDeploymentProperties.NAME, "myapp");
options.put(DFDeploymentProperties.ENABLED, "true");

ProgressObject po = facility.deploy(new DeploymentTarget("server"),
    new FileArchive(archive), null, options);

// Wait for completion
while (po.getDeploymentStatus().getState() == DeploymentStatus.RUNNING) {
    Thread.sleep(100);
}

// Check status
if (po.getDeploymentStatus().getState() == DeploymentStatus.COMPLETED) {
    System.out.println("Deployed successfully");
} else {
    System.out.println("Deployment failed: " +
        po.getDeploymentStatus().getMessage());
}
```

### JSR-88 Deployment

```java
// Get deployment manager
DeploymentFactory factory = new SunDeploymentFactory();
String uri = "deployer:payara:localhost:4848";
DeploymentManager dm = factory.getDeploymentManager(uri, "admin", "");

// Deploy
File archive = new File("myapp.ear");
ProgressObject po = dm.distribute(dm.getTargets(), archive, null);

// Wait and start
while (po.getDeploymentStatus().getState() == DeploymentStatus.RUNNING) {
    Thread.sleep(100);
}

dm.start(po.getResultTargetModuleIDs());
```

## Related Modules

- `deployment/javaee-full` - JSR-88 API support
- `deployment/javaee-core` - Core deployment
- `deployment/dol` - Deployment descriptors
