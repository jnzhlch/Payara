# CLAUDE.md - Deployment Admin

This file provides guidance for working with the `deployment-admin` module - deployment admin commands.

## Module Overview

The `deployment-admin` module provides the asadmin commands for deploying, undeploying, and managing applications in Payara.

## Build Commands

```bash
# Build deployment-admin module
mvn -DskipTests clean package -f nucleus/deployment/admin/pom.xml

# Build with tests
mvn clean package -f nucleus/deployment/admin/pom.xml
```

## Core Commands

### Deploy Command

```bash
# Basic deployment
asadmin deploy myapp.war

# Deploy with context root
asadmin deploy --contextroot /myapp myapp.war

# Deploy with name
asadmin deploy --name myapp myapp.war

# Deploy to specific target
asadmin deploy --target cluster1 myapp.war

# Force redeploy
asadmin deploy --force myapp.war

# Deploy with properties
asadmin deploy --properties location=lib/myapp.jar myapp.ear

# Deploy from directory
asadmin deploy /path/to/exploded/app

# Deploy with virtual servers
asadmin deploy --virtualservers vs1,vs2 myapp.war

# Enabled/disabled deployment
asadmin deploy --enabled=false myapp.war
asadmin deploy --enabled=true myapp.war

# Keep deployment metadata
asadmin deploy --keeprepos=true myapp.war

# Preserve application settings across redeploy
asadmin deploy --preserveappscopedresources true myapp.war
```

### Undeploy Command

```bash
# Basic undeploy
asadmin undeploy myapp

# Undeploy from specific target
asadmin undeploy --target server-instance myapp

# Keep state directory
asadmin undeploy --keepstate=true myapp
```

### List Commands

```bash
# List applications
asadmin list-applications

# List applications on specific target
asadmin list-applications --target cluster1

# List application references
asadmin list-app-refs

# List libraries
asadmin list-libraries

# List application refs (short form)
asadmin list-app-refs myapp
```

### Enable/Disable Commands

```bash
# Enable application
asadmin enable myapp

# Disable application
asadmin disable myapp

# Enable on specific target
asadmin enable --target cluster1 myapp
```

### Redeploy Command

```bash
# Redeploy application
asadmin redeploy myapp

# Redeploy with new file
asadmin redeploy --name myapp newapp.war
```

### Application Reference Commands

```bash
# Create application reference
asadmin create-application-ref --target cluster1 myapp

# Delete application reference
asadmin delete-application-ref --target cluster1 myapp

# List application references
asadmin list-application-refs --target cluster1

# Update application reference
asadmin update-application-ref --target cluster1 --enabled=true myapp
```

## Advanced Commands

### Remote Deployment

```bash
# Deploy from remote directory
asadmin deploy --remote --path /remote/path myapp.war

# Validate remote deployment
asadmin validate-remote-dir-deployment --remote /remote/path
```

### Client Stubs

```bash
# Get client stubs
asadmin get-client-stubs myapp

# Specify output directory
asadmin get-client-stubs --outputdir /path/to/stubs myapp
```

### Deployment Configuration

```bash
# Get deployment configurations
asadmin get-deployment-configurations

# Get application launch URLs
asadmin get-application-launch-urls myapp

# Get host and port
asadmin get-host-and-port
```

### Multi-Tenant Commands

```bash
# Multi-tenant deploy
asadmin mt-provision --tenant mytenant myapp

# Multi-tenant undeploy
asadmin mt-unprovision --tenant mytenant myapp

# Multi-tenant deploy
asadmin mt-deploy --tenant mytenant myapp.war

# Multi-tenant undeploy
asadmin mt-undeploy --tenant mytenant myapp
```

### Library Management

```bash
# Add library
asadmin add-library mylib.jar

# Add library with description
asadmin add-library --description "My Library" mylib.jar

# Remove library
asadmin remove-library mylib

# List libraries
asadmin list-libraries
```

## Post-Processing Commands

### Post Deploy/Undeploy/Enable/Disable

```bash
# Post-deployment hook
asadmin post-deploy myapp

# Post-undeployment hook
asadmin post-undeploy myapp

# Post-enable hook
asadmin post-enable myapp

# Post-disable hook
asadmin post-disable myapp

# Post-state command
asadmin post-state --state ENABLED myapp
```

## Architecture

### Command Flow

```
asadmin deploy myapp.war
        │
        ├─→ DeployCommand (admin command)
        │      ├─→ DeploymentFacility
        │      ├─→ SnifferManager (detect app type)
        │      ├─→ Deployer (load application)
        │      └─→ ApplicationLifecycle (start)
        │
        └─→ ApplicationInfo (metadata stored in registry)
```

### Command Classes

| Command | Purpose |
|---------|---------|
| `DeployCommand` | Deploy applications |
| `UndeployCommand` | Undeploy applications |
| `RedeployCommand` | Redeploy applications |
| `EnableCommand` | Enable deployed applications |
| `DisableCommand` | Disable deployed applications |
| `ListApplicationsCommand` | List deployed applications |
| `ListAppRefsCommand` | List application references |

## Deployment Properties

### Common Properties

```java
// In deployment context
context.getAppProps().setProperty(
    DeploymentProperties.NAME, "myapp"
);
context.getAppProps().setProperty(
    DeploymentProperties.CONTEXT_ROOT, "/myapp"
);
context.getAppProps().setProperty(
    DeploymentProperties.LOCATION, "lib/myapp.jar"
);
context.getAppProps().setProperty(
    DeploymentProperties.FORCE, "true"
);
context.getAppProps().setProperty(
    DeploymentProperties.PRESERVED_APP_SCOPED_RESOURCES, "true"
);
```

## Command Implementation Patterns

### Deploy Command Structure

```java
@Service @Scoped(PerLookup.class)
public class DeployCommand implements AdminCommand {

    @Param(name = "name", optional = true)
    private String name;

    @Param(name = "contextroot", optional = true)
    private String contextRoot;

    @Param(name = "target", optional = true, multiple = true)
    private List<String> targets;

    @Param(name = "enabled", optional = true, defaultValue = "true")
    private Boolean enabled;

    @Inject
    private DeploymentFacility deploymentFacility;

    @Override
    public void execute(AdminCommandContext context) {
        // Deployment logic
    }
}
```

## Related Modules

- **deployment/common** - Core deployment infrastructure
- **deployment/autodeploy** - Automatic deployment
- **kernel** - ApplicationLifecycle service
- **admin/cli** - CLI infrastructure

## Common Patterns

### 1. Programmatic Deployment

```java
// Deploy from code
DeploymentFacility facility = habitat.getService(DeploymentFacility.class);

File archive = new File("myapp.war");
String appName = facility.deploy(archive, params);
```

### 2. Custom Deploy Command

```java
@Service @Scoped(PerLookup.class)
@ExecuteOn(RuntimeType.DAS)
public class MyDeployCommand implements AdminCommand {

    @Inject
    private DeploymentFacility facility;

    @Override
    public void execute(AdminCommandContext context) {
        // Custom deployment logic
    }
}
```

### 3. Handle Deployment Events

```java
// Register for deployment events
events.register(new EventListener() {
    @Override
    public void event(Event event) {
        if (event.is(EventTypes.APPLICATION_DEPLOYED)) {
            // Handle deployment
        }
    }
});
```
