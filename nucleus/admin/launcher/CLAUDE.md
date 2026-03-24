# CLAUDE.md - Admin Launcher

This file provides guidance for working with the Admin Launcher module - server startup and JVM management.

## Module Overview

The Launcher module is responsible for starting Payara Server instances. It handles JVM configuration, classpath setup, process spawning, and provides both embedded and standalone launch capabilities.

## Key Components

### Launchers

**Main launcher classes:**
- `GFLauncher.java` - Base launcher class
- `GFInstanceLauncher.java` - Launch standalone server instance
- `GFDomainLauncher.java` - Launch domain (DAS)
- `GFEmbeddedLauncher.java` - Launch embedded server

### Launcher Factory

**Launcher creation:**
- `GFLauncherFactory.java` - Factory for creating launchers
- `GFLauncherInfo.java` - Launcher configuration info

### Configuration

**JVM and server configuration:**
- `JavaConfig.java` - Java/JVM configuration
- `JvmOptions.java` - JVM options management
- `ArgumentManager.java` - Command-line argument processing

### Native Integration

**Platform-specific code:**
- `GFLauncherNativeHelper.java` - Native process helpers

## Build Commands

```bash
# Build launcher module
mvn -DskipTests clean package -f nucleus/admin/launcher/pom.xml

# Build with tests
mvn clean package -f nucleus/admin/launcher/pom.xml
```

## Launcher Usage Pattern

### Starting a Domain

```java
import com.sun.enterprise.admin.launcher.GFLauncher;
import com.sun.enterprise.admin.launcher.GFLauncherFactory;
import com.sun.enterprise.admin.launcher.GFLauncherInfo;

// Create launcher info
GFLauncherInfo info = new GFLauncherInfo();
info.setDomainRootPath("/path/to/domain");
info.setDomainName("domain1");
info.setInstanceName("server");

// Create launcher
GFLauncher launcher = GFLauncherFactory.getInstance(info);

// Launch
launcher.launch();
```

### Starting in Debug Mode

```java
GFLauncherInfo info = new GFLauncherInfo();
info.setDebug(true);
info.setDebugPort(9009);

GFLauncher launcher = GFLauncherFactory.getInstance(info);
launcher.launch();
```

## JVM Options

### Setting JVM Options

```java
JvmOptions jvmOptions = new JvmOptions();

// Add option
jvmOptions.addJvmOption("-Xmx512m");

// Add system property
jvmOptions.addJvmOption("-Dmy.property=value");

// Add agent
jvmOptions.addJvmOption("-javaagent:path/to/agent.jar");

// Apply to launcher
launcher.setJvmOptions(jvmOptions);
```

### JVM Option Sources

Options are read from:
1. Command-line arguments
2. `domain.xml` (`<java-config>` element)
3. Environment variables
4. Launcher defaults

## JavaConfig

### Java Configuration

```java
JavaConfig javaConfig = new JavaConfig();

// Set Java home
javaConfig.setJavaHome("/path/to/jdk");

// Set Java version
javaConfig.setJavaVersion("21");

// Detect from environment
javaConfig.detectJavaHome();
```

## Process Management

### Process Control

```java
// Launch server
launcher.launch();

// Check if running
boolean running = launcher.isRunning();

// Get PID
int pid = launcher.getPid();

// Stop server
launcher.stop();
```

### Respawning

The launcher can respawn the server if it crashes:

```java
RespawnInfo respawnInfo = new RespawnInfo();
respawnInfo.setEnabled(true);
respawnInfo.setMaxRetries(3);

launcher.setRespawnInfo(respawnInfo);
```

## Classpath Management

### Classpath Construction

The launcher builds classpath from:
1. `glassfish/modules/` - OSGi bundles
2. `glassfish/lib/` - Shared libraries
3. `glassfish/domain/lib/` - Domain-specific libraries

## ArgumentManager

### Argument Processing

```java
ArgumentManager argManager = new ArgumentManager();

// Parse arguments
String[] args = {"--debug", "--port", "8080"};
argManager.parseArguments(args);

// Get values
boolean debug = argManager.hasOption("debug");
String port = argManager.getOptionValue("port");
```

## Payara JVM Options

### Default Payara Options

`PayaraDefaultJvmOptions.java` provides Payara-specific defaults:
- G1GC configuration
- JDK-specific options
- Payara branding

## Main Entry Point

### GFLauncherMain

```java
public class GFLauncherMain {
    public static void main(String[] args) {
        GFLauncher launcher = GFLauncherFactory
            .getInstance(args);
        launcher.launch();
    }
}
```

## Environment Variables

| Variable | Purpose |
|----------|---------|
| `JAVA_HOME` | Java installation directory |
| `AS_JAVA` | Alternative to JAVA_HOME |
| `AS_ADMIN_USER` | Default admin username |

## Launcher Constants

`GFLauncherConstants.java` defines:
- Default ports (4848, 8080, 8181, 7676)
- JVM option prefixes
- System property names

## Logging

### Launcher Logging

```java
GFLauncherLogger logger = new GFLauncherLogger();

// Log message
logger.info("Server starting on port {0}", port);
logger.warning("Configuration issue detected");
logger.severe("Failed to start server");
```

## Architecture Notes

1. **Multi-mode** - Supports embedded, standalone, domain modes
2. **Platform-aware** - Handles Windows, Linux, macOS differences
3. **Configuration-driven** - Reads from domain.xml
4. **Process isolation** - Each instance in separate JVM
5. **Watchdog capable** - Can respawn crashed servers

## Key Packages

- `com.sun.enterprise.admin.launcher` - Core launcher classes
- `fish.payara.admin.launcher` - Payara-specific launcher extensions

## Troubleshooting

### Server Won't Start

```bash
# Check Java version
java -version

# Check ports are available
netstat -an | grep 4848

# Enable verbose launcher output
./asadmin start-domain --verbose

# Check launcher logs
tail -f glassfish/domains/domain1/logs/server.log
```

### JVM Issues

```bash
# View JVM options
./asadmin get "server.java-config.*"

# Add JVM option
./asadmin create-jvm-options "-Dmy.property=value"

# Debug mode
./asadmin start-domain --debug --trace=true
```

## Debug Port

Default debug port is `9009`:

```bash
# Start in debug mode
./asadmin start-domain --debug

# Attach debugger to localhost:9009
```

## Native Helper

`GFLauncherNativeHelper` provides:
- Process ID retrieval
- Native signal handling
- Platform-specific utilities
