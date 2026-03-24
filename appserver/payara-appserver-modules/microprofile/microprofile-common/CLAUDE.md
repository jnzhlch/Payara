# CLAUDE.md - MicroProfile Common

This file provides guidance for working with the `microprofile/microprofile-common` module - Shared utilities and constants for MicroProfile implementations.

## Module Overview

The microprofile/microprofile-common module provides shared utilities and constants used across Payara's MicroProfile implementations. It primarily handles default user creation for secure MicroProfile endpoints and provides common constants.

**Key Features:**
- **Default User Creation** - Auto-creates "mp" user for secure MicroProfile endpoints
- **Role-Based Access Control** - Configures roles for MicroProfile security
- **Common Constants** - Shared constants across MicroProfile modules

## Build Commands

```bash
# Build microprofile/microprofile-common module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/microprofile-common/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/microprofile-common/pom.xml
```

## Core Components

### Constants

```java
public interface Constants {

    String DEFAULT_GROUP_NAME = "microprofile";
    String DEFAULT_USER_NAME = "mp";

    String EMPTY_STRING = "";
    String CREATE_INSECURE_ENDPOINT_TEST = "fish.payara.test.create-insecure-endpoint";
}
```

**Purpose:** Defines default values for MicroProfile configuration:
- **DEFAULT_GROUP_NAME** - Default user group name
- **DEFAULT_USER_NAME** - Default username for MicroProfile access
- **EMPTY_STRING** - Empty string constant
- **CREATE_INSECURE_ENDPOINT_TEST** - Test property for creating insecure endpoints

### SetSecureMicroprofileConfigurationCommand

```java
public abstract class SetSecureMicroprofileConfigurationCommand implements AdminCommand {

    @Param(optional = true, defaultValue = "server-config")
    protected String target;

    @Inject
    protected CommandRunner commandRunner;

    @Inject
    private SecurityService securityService;

    @Param(optional = true, defaultValue = "microprofile")
    protected String roles;

    @Param(optional = true, alias = "securityenabled")
    protected Boolean securityEnabled;

    protected boolean defaultMicroprofileUserExists(ActionReport subActionReport, Subject subject) {
        // Checks if "mp" user exists in default realm
        CommandRunner.CommandInvocation invocation = commandRunner
                .getCommandInvocation("list-file-users", subActionReport, subject, false);
        // ... checks for DEFAULT_USER_NAME
    }

    protected void createDefaultMicroprofileUser(ActionReport subActionReport, Subject subject) {
        // Creates "mp" user with specified roles
        CommandRunner.CommandInvocation invocation = commandRunner
                .getCommandInvocation("create-file-user", subActionReport, subject, false);

        ParameterMap parameters = new ParameterMap();
        parameters.add("groups", roles.replace(',', ':'));
        parameters.add("userpassword", "mp");
        parameters.add("target", target);
        parameters.add("authrealmname", securityService.getDefaultRealm());
        parameters.add("DEFAULT", DEFAULT_USER_NAME);

        invocation.parameters(parameters).execute();
    }
}
```

**Purpose:** Abstract admin command that subclasses use to create and configure the default MicroProfile user.

## Package Structure

```
microprofile/microprofile-common/
└── src/main/java/fish/payara/microprofile/
    ├── Constants.java                                  # Shared constants
    └── SetSecureMicroprofileConfigurationCommand.java  # Base class for secure config
```

## Usage in Other Modules

This module is used by other MicroProfile modules that need secure endpoint configuration:

```java
// Used by:
// - microprofile/healthcheck - Secure health check endpoints
// - microprofile/metrics - Secure metrics endpoints
// - microprofile/config - Secure config endpoints
// etc.
```

When a MicroProfile feature has security enabled and requires authentication, it typically uses this module to:
1. Check if the default "mp" user exists
2. Create the user if it doesn't exist
3. Assign the user to the "microprofile" group
4. Configure roles for accessing secured endpoints

## Default Credentials

When secure endpoints are enabled, the default credentials are:

| Property | Value |
|----------|-------|
| **Username** | `mp` |
| **Password** | `mp` |
| **Group** | `microprofile` |

## Admin Commands

The `SetSecureMicroprofileConfigurationCommand` is extended by specific MicroProfile modules:

```bash
# Health Check secure configuration
asadmin set-microprofile-health-check-configuration --securityenabled=true

# Metrics secure configuration
asadmin set-metrics-configuration --security-enabled=true

# Config secure configuration
asadmin set-config-configuration --security-enabled=true
```

When `securityenabled=true`, the command will:
1. Check if the "mp" user exists
2. Create it if not present
3. Assign to configured roles

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | GlassFish public APIs (AdminCommand, CommandRunner) |
| `security` | Security services for user management |

## Notes

- **Minimal Module** - This is a small utility module with few classes
- **Shared Infrastructure** - Provides common functionality for MicroProfile security
- **User Management** - Delegates to GlassFish file user realm
- **Default Password** - The default "mp" password should be changed in production
