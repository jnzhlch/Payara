# CLAUDE.md - OSGi Platforms

This file provides guidance for working with the `nucleus/osgi-platforms` module - OSGi framework integration.

## Module Overview

The `nucleus/osgi-platforms` module provides Apache Felix OSGi framework integration for Payara. OSGi enables:
- Dynamic module loading and unloading
- Bundle lifecycle management
- Service registry
- OSGi shell for interactive management

## Sub-Modules

- **felix** - Apache Felix OSGi framework distribution fragment
- **osgi-container** - OSGi bundle deployment container
- **osgi-cli-remote** - Remote OSGi shell access via asadmin
- **osgi-cli-interactive** - Interactive OSGi shell (gogo)
- **osgi-cli-remote-l10n** - Localization for remote CLI
- **osgi-cli-interactive-l10n** - Localization for interactive CLI

## Build Commands

```bash
# Build all osgi-platforms modules
mvn -DskipTests clean package -f nucleus/osgi-platforms/pom.xml

# Build specific module
mvn -DskipTests clean package -f nucleus/osgi-platforms/osgi-container/pom.xml
mvn -DskipTests clean package -f nucleus/osgi-platforms/osgi-cli-remote/pom.xml
```

## Architecture

### OSGi Integration Layer

```
Payara Server
        │
        ├─→ OSGi Framework (Apache Felix)
        │      ├─→ Bundle System
        │      ├─→ Service Registry
        │      └─→ OSGi Shell (gogo)
        │
        ├─→ OSGiContainer
        │      └─→ Deploys OSGi bundles via deployment backend
        │
        ├─→ OSGiSniffer
        │      └─→ Detects OSGi bundles (--type=osgi)
        │
        ├─→ OSGiDeployer
        │      └─→ Installs/starts/stops bundles
        │
        └─→ OSGiShellCommand
               └─→ asadmin osgi <command> bridge
```

### Directory Layout

```
{payara-install}/
├── osgi/
│   └── felix/
│       ├── bin/
│       │   └── felix.jar          # Felix framework
│       └── conf/
│           └── osgi.properties    # OSGi configuration
└── modules/
    ├── org.apache.felix.*.jar     # Felix bundles
    └── autostart/
        └── org.apache.felix.scr.jar  # Declarative Services
```

## OSGi Bundle Deployment

### Deploy OSGi Bundle

```bash
# Deploy as OSGi bundle (must specify --type)
asadmin deploy --type=osgi my-bundle.jar

# Deploy with context name
asadmin deploy --type=osgi --name myBundle my-bundle.jar

# Enable bundle at deployment
asadmin deploy --type=osgi --enabled=true my-bundle.jar
```

### List Bundles

```bash
# Via OSGi shell
asadmin osgi lb

# Via OSGi shell (long format)
asadmin osgi list

# Specific bundle
asadmin osgu osgi headers <bundle-id>
```

### Bundle Lifecycle

```bash
# Start bundle
asadmin osgi start <bundle-id>

# Stop bundle
asadmin osgi stop <bundle-id>

# Update bundle
asadmin osgi update <bundle-id>

# Uninstall bundle (undeploy)
asadmin undeploy osgi://<bundle-name>
```

## OSGi Shell

### Remote Shell Access

```bash
# Access OSGi shell via asadmin
asadmin osgi

# Execute single command
asadmin osgi <command> [args]

# Examples
asadmin osgi lb              # List bundles
asadmin osgi help            # Show help
asadmin osgi ps              # List services
asadmin osgi bundles         # List bundles with details
```

### Common Shell Commands

| Command | Description |
|---------|-------------|
| `lb` | List bundles |
| `start <id>` | Start bundle |
| `stop <id>` | Stop bundle |
| `update <id>` | Update bundle |
| `uninstall <id>` | Uninstall bundle |
| `headers <id>` | Show bundle headers |
| `ps` | List registered services |
| `services` | Show service details |
| `bundle <id>` | Show bundle details |

### Gogo Shell Commands

The OSGi shell uses Apache Felix Gogo:

```bash
# Access Gogo shell directly
java -jar {payara-install}/osgi/felix/bin/felix.jar

# Gogo commands
g! lb              # List bundles
g! ps              # List services
g! help            # Show help
```

## Configuration

### OSGi Properties

Located in `{domain}/osgi/felix/conf/osgi.properties`:

```properties
# OSGi framework properties
org.osgi.framework.system.packages=...
org.osgi.framework.bootdelegation=...

# Felix-specific properties
felix.auto.start.1=...
felix.log.level=4
```

### Configuring Auto-Start Bundles

```properties
# Auto-start bundles by level
felix.auto.start.1= \
    file:${com.sun.aas.installRoot}/modules/autostart/org.apache.felix.scr.jar

felix.auto.start.2= \
    file:${com.sun.aas.installRoot}/modules/org.apache.felix.configadmin.jar
```

## Bundle Development

### OSGi Bundle MANIFEST.MF

```manifest
Bundle-SymbolicName: com.example.mybundle
Bundle-Version: 1.0.0
Bundle-Activator: com.example.MyActivator
Export-Package: com.example.api
Import-Package: org.osgi.framework;version="[1.8,2)"
```

### Deploying Custom Bundles

```bash
# Deploy bundle
asadmin deploy --type=osgi target/mybundle-1.0.0.jar

# Check bundle state
asadmin osgi headers <bundle-id>

# View bundle wiring
asadmin osgi packages <bundle-id>
```

## OSGi Services

### Service Registration

```java
public class MyActivator implements BundleActivator {

    @Override
    public void start(BundleContext ctx) {
        // Register service
        MyService service = new MyServiceImpl();
        ctx.registerService(MyService.class.getName(), service, null);
    }

    @Override
    public void stop(BundleContext ctx) {
        // Cleanup
    }
}
```

### Service Consumption

```java
// Get service reference
ServiceReference ref = ctx.getServiceReference(MyService.class.getName());
MyService service = (MyService) ctx.getService(ref);

// Use service
service.doSomething();

// Unget service
ctx.ungetService(ref);
```

## HK2-OSGi Bridge

### HK2 Services in OSGi

Payara provides bridge between HK2 and OSGi:

```java
// HK2 service available in OSGi
@Service
public class MyHK2Service {
    // This service can be accessed from OSGi bundles
}
```

### OSGi Services in HK2

```java
// Inject OSGi bundle context
@Inject
private BundleContext bundleContext;

// Use OSGi services from HK2 code
```

## Sniffer and Container

### OSGiSniffer

Detects OSGi bundles for deployment:

- Always returns `false` for automatic detection
- Requires explicit `--type=osgi` flag
- Prevents accidental deployment as OSGi

### OSGiContainer

Manages OSGi bundle lifecycle:

- Integrates with deployment backend
- Handles bundle install/start/stop
- Maps to OSGi framework operations

## Related Modules

- **core/bootstrap** - OSGi framework startup
- **core/kernel** - HK2-OSGi integration
- **admin/cli** - asadmin command infrastructure

## Troubleshooting

### Bundle Not Starting

```bash
# Check bundle state
asadmin osgi headers <bundle-id>

# Check for unresolved dependencies
asadmin osgi packages <bundle-id>

# Check framework log
tail -f domains/domain1/logs/server.log | grep -i osgi
```

### Class Loading Issues

```properties
# Add boot delegation for problematic packages
org.osgi.framework.bootdelegation=sun.*,com.sun.*
```

### Bundle Installation Errors

```bash
# Enable OSGi debug
-Dorg.osgi.framework.debug=true

# Check bundle manifest
jar xf mybundle.jar META-INF/MANIFEST.MF
cat META-INF/MANIFEST.MF
```

### Shell Not Responding

```bash
# Check Felix shell service is available
asadmin osgi ps | grep ShellService

# Check remote shell is enabled
asadmin get server-config.osgi-configuration
```

## Notes

- OSGi is primarily used for internal Payara modularity
- Most application deployment uses standard Java EE, not OSGi
- OSGi bundles require explicit `--type=osgi` for deployment
- The OSGi shell provides powerful debugging capabilities
