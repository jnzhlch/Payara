# CLAUDE.md - OSGi CLI Remote

This file provides guidance for working with the `osgi-cli-remote` module - remote OSGi shell access via asadmin.

## Module Overview

The `osgi-cli-remote` module provides an asadmin command bridge to the OSGi shell, allowing remote access to OSGi framework management commands.

## Build Commands

```bash
# Build osgi-cli-remote module
mvn -DskipTests clean package -f nucleus/osgi-platforms/osgi-cli-remote/pom.xml

# Build with tests
mvn clean package -f nucleus/osgi-platforms/osgi-cli-remote/pom.xml
```

## Architecture

### Command Bridge Flow

```
asadmin osgi <command> [args]
        │
        ├─→ OSGiShellCommand (AdminCommand)
        │      ├─→ Injects BundleContext
        │      ├─→ Gets CommandProcessor from OSGi
        │      └─→ Creates RemoteCommandSession
        │
        ├─→ CommandProcessor (Felix Shell)
        │      └─→ Executes OSGi shell commands
        │
        └─→ ActionReport
               └─→ Returns output to asadmin
```

## Core Components

### OSGiShellCommand

```java
@Service(name = "osgi")
@CommandLock(CommandLock.LockType.SHARED)
@PerLookup
@TargetType({CommandTarget.CLUSTERED_INSTANCE, CommandTarget.STANDALONE_INSTANCE})
public class OSGiShellCommand implements AdminCommand {

    @Param(primary = true)
    String commandLine;

    @Inject
    private BundleContext bundleContext;

    @Override
    public void execute(AdminCommandContext context) {
        // Get CommandProcessor service
        ServiceReference ref = bundleContext.getServiceReference(
            CommandProcessor.class.getName()
        );
        CommandProcessor cp = (CommandProcessor) bundleContext.getService(ref);

        // Create session and execute
        CommandSession session = cp.createSession(...);
        Object result = session.execute(commandLine);
    }
}
```

### RemoteCommandSession

```java
class RemoteCommandSession implements CommandSession {

    private final ByteArrayOutputStream output;
    private final PrintStream printStream;

    @Override
    public Object execute(CharSequence commandline) {
        // Execute command and capture output
        return commandProcessor.execute(commandline, out, err);
    }
}
```

## Usage

### Basic Commands

```bash
# Access OSGi shell via asadmin
asadmin osgi

# Execute single command
asadmin osgi lb

# Execute command with arguments
asadmin osgi "headers 0"

# Multiple commands (interactive mode)
asadmin osgi
g! lb
g! ps
g! exit
```

### Common Commands

| Command | Description | Example |
|---------|-------------|---------|
| `lb` | List bundles | `asadmin osgi lb` |
| `headers <id>` | Show bundle headers | `asadmin osgi headers 0` |
| `start <id>` | Start bundle | `asadmin osgi start 1` |
| `stop <id>` | Stop bundle | `asadmin osgi stop 1` |
| `update <id>` | Update bundle | `asadmin osgi update 1` |
| `uninstall <id>` | Uninstall bundle | `asadmin osgi uninstall 1` |
| `ps` | List services | `asadmin osgi ps` |
| `bundles` | Detailed bundle list | `asadmin osgi bundles` |
| `help` | Show help | `asadmin osgi help` |

### Command Output

```bash
# List bundles
asadmin osgi lb
ID  State      Level  Name
[0] [Active]   [0]    System Bundle (4.3.2)
[1] [Active]   [1]    osgi-cmdline-interpreter (0.4.2)
[2] [Active]   [1]    my-bundle (1.0.0)

# Show bundle headers
asadmin osgi "headers 2"
MyBundle (1.0.0)
--------------------
Bundle-SymbolicName = com.example.mybundle
Bundle-Version = 1.0.0
Export-Package = com.example.api
Import-Package = org.osgi.framework
```

## Command Integration

### Felix Shell Service

The command integrates with Apache Felix Shell Service:

```java
// Get ShellService
ShellService shellService = (ShellService) bundleContext.getService(
    bundleContext.getServiceReference(ShellService.class.getName())
);

// Get CommandProcessor
CommandProcessor commandProcessor = (CommandProcessor) bundleContext.getService(
    bundleContext.getServiceReference(CommandProcessor.class.getName())
);
```

### Gogo Runtime

Uses Apache Felix Gogo for command processing:

```xml
<dependency>
    <groupId>org.apache.felix</groupId>
    <artifactId>org.apache.felix.gogo.runtime</artifactId>
    <scope>provided</scope>
</dependency>
```

## Target Types

```java
@TargetType({CommandTarget.CLUSTERED_INSTANCE, CommandTarget.STANDALONE_INSTANCE})
```

Command targets:
- **STANDALONE_INSTANCE** - Standalone server instances
- **CLUSTERED_INSTANCE** - Clustered server instances

## REST Endpoint

```java
@RestEndpoints({
    @RestEndpoint(configBean=Domain.class,
        opType=RestEndpoint.OpType.POST,
        path="osgi",
        description="Remote OSGi Shell Access")
})
```

Accessible via:
```
POST /management/domain/osgi
```

## Dependencies

**Required:**
- `osgi.core` - OSGi framework
- `glassfish-api` - Admin command API
- `admin-util` - Admin utilities

**Provided:**
- `org.apache.felix.shell` - Felix shell service
- `org.apache.felix.gogo.runtime` - Gogo runtime

## Package Structure

```
org.glassfish.osgi.cli.remote/
├── OSGiShellCommand.java       - Main admin command
└── RemoteCommandSession.java   - Command session wrapper
```

## Related Modules

- **osgi-container** - OSGi bundle deployment
- **felix** - Apache Felix framework
- **osgi-cli-interactive** - Interactive shell

## Troubleshooting

### Command Not Found

```bash
# Check OSGi shell service is available
asadmin osgi ps | grep ShellService

# Check bundle is active
asadmin osgi lb | grep shell
```

### No Output

```bash
# Use quotes for commands with arguments
asadmin osgi "headers 0"

# Check server logs
tail -f domains/domain1/logs/server.log
```

### Permission Denied

```bash
# Check command lock type
@CommandLock(CommandLock.LockType.SHARED)

# Multiple users can execute concurrently
```

## Notes

- Command name is "osgi" (not "felix") for OSGi compatibility
- Uses shared lock - multiple users can execute simultaneously
- Requires OSGi shell service to be available
- Output captured and returned to asadmin client
