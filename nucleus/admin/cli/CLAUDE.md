# CLAUDE.md - Admin CLI

This file provides guidance for working with the Admin CLI module - the `asadmin` command-line interface.

## Module Overview

The CLI module implements the `asadmin` command-line tool, the primary administrative interface for Payara Server. It provides command parsing, local/remote execution, multimode support, and the embedded API.

## Key Components

### CLI Framework (`com.sun.enterprise.admin.cli`)

**Entry Points:**
- `AdminMain.java` - Main entry point for asadmin command
- `CLICommand.java` - Base class for all CLI commands
- `CLIContainer.java` - HK2 container initialization

**Command Support:**
- `HelpCommand` - Display command help
- `ListCommandsCommand` - List all available commands
- `MultimodeCommand` - Interactive multimode shell
- `VersionCommand` - Display version information
- `LoginCommand` - Authentication management
- `ExportCommand` - Export command output

### Remote Execution (`remote/`)

**Components:**
- `RemoteCLICommand.java` - Base class for remote commands
- `RemoteCommand.java` - Remote command execution via REST
- `DASUtils.java` - Domain Administration Server utilities
- `ClientCookieStore.java` - Session cookie management

### Parser (`Parser.java`)

Command line parser supporting:
- Options (`--option value` or `-o value`)
- Operand arguments (positional parameters)
- Quoted strings
- Escape sequences

### CLI Utilities (`CLIUtil.java`)

Common utilities for:
- String manipulation
- File operations
- Program options handling

## Build Commands

```bash
# Build CLI module
mvn -DskipTests clean package -f nucleus/admin/cli/pom.xml

# Build with tests
mvn clean package -f nucleus/admin/cli/pom.xml

# Run tests
mvn test -f nucleus/admin/cli/pom.xml
```

## Command Development Pattern

### Local Command

```java
package com.sun.enterprise.admin.cli;

import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.Param;
import org.jvnet.hk2.annotations.Service;

@Service
public class MyCommand implements AdminCommand {

    @Param(name = "name", primary = true)
    private String name;

    @Param(name = "optional", optional = true)
    private String optional;

    @Override
    public void execute(AdminCommandContext context) {
        // Command implementation
    }
}
```

### Remote Command

```java
package fish.payara.admin.cli;

import com.sun.enterprise.admin.cli.remote.RemoteCommand;

@Service
public class MyRemoteCommand extends RemoteCommand {

    @Param(name = "target", defaultValue = "server")
    private String target;

    @Override
    protected void executeRemote()
        throws CommandException, CommandValidationException {
        // Remote implementation
    }
}
```

## Command Metadata

Use `@Param` annotations for parameters:

| Attribute | Purpose |
|-----------|---------|
| `name` | Parameter name |
| `primary` | Primary operand ( positional argument) |
| `optional` | Whether parameter is optional |
| `defaultValue` | Default value |
| `shortName` | Short option name (e.g., "h" for help) |
| `acceptableValues` | Valid values (comma-separated) |
| `alias` | Parameter aliases |

## Command Locking

Control concurrent access with `@CommandLock`:

```java
import org.glassfish.api.admin.CommandLock;

@CommandLock(CommandLock.LockType.NONE)  // No locking
@CommandLock(CommandLock.LockType.SHARED)  // Read lock
@CommandLock(CommandLock.LockType.EXCLUSIVE)  // Write lock
```

## Embeddable API

The CLI provides an embeddable API for programmatic command execution:

**Components:**
- `embeddable/CommandExecutorImpl.java` - Command execution
- `embeddable/DeployerImpl.java` - Deployment operations

## Environment

`Environment.java` manages:
- AS_ADMIN_ENV environment variables
- .asadminenv configuration file
- JVM options and system properties

## Testing CLI Commands

```bash
# Build the module
mvn clean package -f nucleus/admin/cli/pom.xml

# Test command locally
./asadmin your-command --param value

# Test in multimode
./asadmin multimode
asadmin> your-command --param value
asadmin> exit
```

## Architecture Notes

1. **HK2-based** - Commands are HK2 services
2. **PerLookup scope** - Most commands use `@Scoped(PerLookup.class)`
3. **Dual execution** - Commands can run locally or remotely
4. **JLine integration** - Uses JLine for interactive terminal
5. **Plugin model** - Commands discovered via HK2 service locator

## Key Files

- `LocalStrings.properties` - Localized messages
- `CLIConstants.java` - CLI constants and defaults
- `ProgramOptions.java` - Program-wide options
- `ArgumentTokenizer.java` - Argument tokenization

## Remote Command Flow

1. `asadmin` parses command line
2. `RemoteCommand.executeRemote()` called
3. HTTP request to DAS REST endpoint
4. Response parsed and displayed
5. Exit code returned

## Multimode

Interactive shell mode:
- Commands executed one at a time
- Maintains session/cookies
- Supports tab completion
- Command history (via JLine)
