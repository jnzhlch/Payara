# CLAUDE.md - AutoDeploy

This file provides guidance for working with the `deployment-autodeploy` module - automatic application deployment.

## Module Overview

The `deployment-autodeploy` module provides automatic deployment of applications placed in the autodeploy directory. This enables "drop-and-deploy" functionality for development and testing.

## Build Commands

```bash
# Build deployment-autodeploy module
mvn -DskipTests clean package -f nucleus/deployment/autodeploy/pom.xml

# Build with tests
mvn clean package -f nucleus/deployment/autodeploy/pom.xml
```

## Architecture

### AutoDeploy Flow

```
Application dropped in autodeploy/
        │
        ├─→ DirectoryScanner (monitors directory)
        │      ├─→ AutoDeployDirectoryScanner
        │      └─→ Detects new/modified/deleted files
        │
        ├─→ AutoDeployedFilesManager (tracks state)
        │
        ├─→ AutoDeployService (orchestrates deployment)
        │      ├─→ AutoDeployer (deploys files)
        │      └─→ AutodeployRetryManager (handles failures)
        │
        └─→ ApplicationLifecycle (loads applications)
```

### Key Components

| Class | Purpose |
|-------|---------|
| `AutoDeployService` | Main service, starts background scanning |
| `AutoDeployer` | Performs actual deployment operations |
| `DirectoryScanner` | Monitors autodeploy directory |
| `AutoDeployedFilesManager` | Tracks deployed files state |
| `AutodeployRetryManager` | Retries failed deployments |

## Configuration

### AutoDeploy Directory

Default location: `{domain}/autodeploy/`

```bash
# Domain structure
glassfish/
├── domains/
│   └── domain1/
│       ├── autodeploy/        # Drop applications here
│       └── applications/       # Where apps are deployed
```

### Enable/Disable AutoDeploy

```xml
<!-- In domain.xml -->
<configs>
  <config name="server-config">
    <autodeploy enabled="true">
      <jvm-option name="-Dcom.sun.enterprise.tools.verification.core>false"></jvm-option>
    </autodeploy>
  </config>
</configs>
```

Or via asadmin:

```bash
# Check autodeploy status
asadmin get "server-config.autodeploy-enabled"

# Disable autodeploy
asadmin set "server-config.autodeploy-enabled=false"

# Enable autodeploy
asadmin set "server-config.autodeploy-enabled=true"
```

## Deployment Modes

### Auto Deploy Behavior

When you drop an application in the autodeploy directory:

| Action | Behavior |
|--------|----------|
| **New file** | Application is deployed |
| **Modified file** | Application is redeployed |
| **File removed** | Application is undeployed |
| **Renamed file** | Old name undeployed, new name deployed |

### Supported Archive Types

- WAR - Web applications
- JAR - EJB JARs, RARs
- EAR - Enterprise applications
- Exploded directories

### File Naming

```
myapp.war          # Deployed with name "myapp"
myapp#2.war        # Versioned deployment (version "2")
myapp.war.deployed # Marker for deployed files
```

## AutoDeployService

### Service Lifecycle

```java
@Service
public class AutoDeployService implements PostConstruct {

    @Override
    public void postConstruct() {
        // Start background scanner
        scanner.start();
    }

    public void shutdown() {
        // Stop scanner
        scanner.stop();
    }
}
```

### Deployment Flow

```java
// AutoDeployer performs deployment
public void deploy(File file) {
    // 1. Check if already deployed
    if (filesManager.isDeployed(file)) {
        // Check if modified
        if (filesManager.isModified(file)) {
            // Redeploy
            redeploy(file);
        }
    } else {
        // New deployment
        deploy(file);
    }
}
```

## AutoDeployedFilesManager

### Tracking State

```java
// Tracks deployed files
AutoDeployedFilesManager manager = ...;

// Check if file is deployed
boolean deployed = manager.isDeployed(file);

// Get deployed application name
String appName = manager.getApplicationName(file);

// Mark as deployed
manager.fileDeployed(file, appName);

// Mark as undeployed
manager.fileUndeployed(file);

// Get all deployed files
Collection<File> files = manager.getDeployedFiles();
```

## Retry Manager

### Failed Deployment Retry

```java
// AutodeployRetryManager handles retries
AutodeployRetryManager retryManager = ...;

// Retry failed deployment
retryManager.scheduleRetry(file, exception);

// Check retry count
int retries = retryManager.getRetryCount(file);
```

## Directory Scanning

### Scan Interval

```java
// Default scan interval (milliseconds)
int interval = AutoDeployConstants.SCAN_INTERVAL;
```

### DirectoryScanner

```java
// Scan directory for changes
DirectoryScanner scanner = new DirectoryScanner(autodeployDir);

// Get new files
Collection<File> newFiles = scanner.getNewFiles();

// Get modified files
Collection<File> modifiedFiles = scanner.getModifiedFiles();

// Get deleted files
Collection<File> deletedFiles = scanner.getDeletedFiles();
```

## Deployment Properties

### AutoDeploy Properties

```java
// Properties set during autodeploy
context.getAppProps().setProperty(
    DeploymentProperties.AUTODEPLOY_DIR, autodeployDirPath
);
context.getAppProps().setProperty(
    DeploymentProperties.AUTODEPLOY, "true"
);
```

## Monitoring and Logging

### Autodeploy Events

```bash
# Check server logs for autodeploy activity
tail -f domains/domain1/logs/server.log | grep autodeploy
```

### Typical Log Messages

```
[INFO] Auto-deployment: Started scanning for changes in /path/to/autodeploy
[INFO] Auto-deployment: Deploying application myapp.war
[INFO] Application deployed successfully: myapp
[INFO] Auto-deployment: Completed scanning for changes
```

## Development Workflow

### Drop-and-Deploy Development

1. **Build application** to WAR/JAR/EAR
2. **Drop file** in `{domain}/autodeploy/`
3. **Auto-deploy** detects and deploys
4. **Test application**
5. **Rebuild** and **replace** file for updates
6. **Auto-redeploy** detects change and redeploys

### Advantages

- No manual deployment commands needed
- Fast iteration during development
- Automatic cleanup on file deletion
- Versioned deployments supported

## Related Modules

- **deployment/common** - Core deployment infrastructure
- **deployment/admin** - Manual deployment commands
- **kernel/ApplicationLifecycle** - Application lifecycle management

## Common Patterns

### 1. Custom AutoDeploy Handler

```java
@Service
public class MyAutoDeployHandler {

    @Inject
    private AutoDeployService autoDeployService;

    public void deploy(File file) {
        // Custom deployment logic
    }
}
```

### 2. Monitor AutoDeploy Events

```java
// Register for deployment events
events.register(new EventListener() {
    @Override
    public void event(Event event) {
        if (event.is(EventTypes.APPLICATION_DEPLOYED)) {
            // Check if autodeployed
            DeploymentContext ctx = ...;
            if (Boolean.parseBoolean(
                ctx.getAppProps().getProperty(DeploymentProperties.AUTODEPLOY, "false"))) {
                // Handle autodeploy event
            }
        }
    }
});
```

### 3. Programmatic AutoDeploy

```java
// Trigger autodeploy scan programmatically
AutoDeployService autoDeploy = habitat.getService(AutoDeployService.class);
autoDeploy.scan();
```

## Troubleshooting

### Files Not Deploying

```bash
# Check autodeploy is enabled
asadmin get "server-config.autodeploy-enabled"

# Check autodeploy directory permissions
ls -la domains/domain1/autodeploy/

# Check logs for errors
tail -f domains/domain1/logs/server.log | grep -i autodeploy
```

### Redeploy Not Working

```bash
# Check file timestamp
ls -l domains/domain1/autodeploy/myapp.war

# Force redeploy by touching file
touch domains/domain1/autodeploy/myapp.war
```

### Too Frequent Scans

```bash
# Adjust scan interval if needed
# (requires modifying AutoDeployConstants.SCAN_INTERVAL)
```
