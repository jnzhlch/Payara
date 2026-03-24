# CLAUDE.md - CLI (Command-Line Interface)

This file provides guidance for working with the `cli` module.

## Module Overview

The cli module provides `AsadminMain`, the main entry point for the Payara asadmin command-line interface. This is the CLI skinning layer for the application server.

## Build Commands

```bash
# Build cli module
mvn -DskipTests clean package -f appserver/admin/cli/pom.xml
```

## Architecture

### AsadminMain Entry Point

Located in `org.glassfish.admin.cli`:

```java
public class AsadminMain extends AdminMain {
    public static void main(String[] args) {
        Environment.setPrefix("AS_ADMIN_");
        Environment.setShortPrefix("AS_");
        int code = new AsadminMain().doMain(args);
        System.exit(code);
    }

    @Override
    protected String getCommandName() {
        return "asadmin";
    }
}
```

### Environment Variables

| Prefix | Purpose |
|--------|---------|
| `AS_ADMIN_` | Environment variable prefix for asadmin |
| `AS_` | Short prefix for asadmin environment variables |

### Inheritance

`AsadminMain` extends `AdminMain` from `nucleus/admin/cli`:
- Delegates to nucleus CLI infrastructure
- Provides appserver-specific environment configuration
- Sets command name to "asadmin"

## Package Structure

```
org.glassfish.admin.cli/
└── AsadminMain.java    # CLI entry point
```

## Dependencies

- `admin-cli` (nucleus) - Core CLI infrastructure

## Running asadmin

### From Development Build

```bash
java -jar appserver/admin/cli/target/appserver-cli.jar [command] [options]
```

### From Installed Payara

```bash
asadmin [command] [options]

# Examples
asadmin list-domains
asadmin start-domain domain1
asadmin deploy myapp.war
```

## Command Discovery

Commands are discovered via HK2 service injection:
- Local commands run in the same JVM
- Remote commands are sent to the running server
- Command name derived from `@Service` annotation or class name

## Integration Points

The CLI integrates with:
- `nucleus/admin/cli` - Core command infrastructure
- `nucleus/admin/config-api` - Configuration management
- `nucleus/admin/rest` - REST endpoint for remote commands
