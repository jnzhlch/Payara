# CLAUDE.md - Security Services

This file provides guidance for working with the `nucleus/security/services` module - Payara's security services API and SPI.

## Module Overview

The services module provides high-level security service APIs and SPIs for authentication, authorization, and role mapping. It serves as the abstraction layer between the core security implementation and application code.

## Build Commands

```bash
# Build the services module
mvn -DskipTests clean package -f nucleus/security/services/pom.xml

# Build with tests
mvn clean package -f nucleus/security/services/pom.xml
```

## Architecture

### Service Layer Architecture

```
┌────────────────────────────────────────────────────────────┐
│                    Application Code                         │
│  (EJBs, Servlets, Custom Security Modules)                  │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   Security Services API                     │
├────────────────────────────────────────────────────────────┤
│  AuthenticationService  │  AuthorizationService            │
│  ImpersonationService   │  RoleMappingService              │
│  SecurityContextService │                                  │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   Service Providers (SPI)                  │
├────────────────────────────────────────────────────────────┤
│  UserStoreProvider        │  AuthorizationProvider         │
│  RoleMappingProvider      │  SecurityProvider              │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                      Core Security                          │
│  (JAAS, Realms, SecurityManager)                            │
└────────────────────────────────────────────────────────────┘
```

## Key Service APIs

### AuthenticationService

Located in `org.glassfish.security.services.api.authentication`:

```java
@Contract
public interface AuthenticationService extends SecurityService {
    // Login with username/password
    Subject login(String username, char[] password, Subject subject) throws LoginException;

    // Login with CallbackHandler (supports certs, tokens)
    Subject login(CallbackHandler cbh, Subject subject) throws LoginException;

    // Impersonate a user
    Subject impersonate(String user, String[] groups, Subject subject, boolean virtual)
            throws LoginException;
}
```

### AuthorizationService

Located in `org.glassfish.security.services.api.authorization`:

```java
@Contract
public interface AuthorizationService extends SecurityService {
    AzResult authorize(AzSubject subject, AzAction action, AzResource resource,
                       AzEnvironment env) throws AuthorizationException;
}
```

Key classes:
- `AzSubject` - The subject (caller) being authorized
- `AzAction` - The action being performed
- `AzResource` - The resource being accessed
- `AzEnvironment` - Environment context
- `AzResult` - Authorization decision (ALLOW/DENY)
- `AzObligations` - Required obligations

### ImpersonationService

Located in `org.glassfish.security.services.api.authentication`:

```java
@Contract
public interface ImpersonationService extends SecurityService {
    // Impersonate a specific user
    Subject impersonate(String username, Subject subject) throws LoginException;

    // Run code as another user
    <T> T runAs(Subject subject, Callable<T> action) throws Exception;
}
```

### SecurityContextService

Located in `org.glassfish.security.services.api.context`:

```java
@Contract
public interface SecurityContextService extends SecurityService {
    // Get current security context
    SecurityScope getSecurityScope();

    // Set security context
    void setSecurityScope(SecurityScope scope);

    // Execute with security context
    <T> T runWithContext(SecurityScope scope, Callable<T> action) throws Exception;
}
```

## Service Providers (SPI)

### AuthorizationProvider

Located in `org.glassfish.security.services.spi.authorization`:

```java
public interface AuthorizationProvider extends SecurityProvider {
    String getName();
    AzResult authorize(AzSubject subject, AzAction action, AzResource resource,
                       AzEnvironment env) throws AuthorizationException;
}
```

### RoleMappingProvider

Located in `org.glassfish.security.services.spi.authorization`:

```java
public interface RoleMappingProvider extends SecurityProvider {
    String getName();
    Map<String, Set<String>> mapRoles(Subject subject, String[] logicalRoles);
}
```

### UserStoreProvider

Located in `org.glassfish.security.services.spi.authentication`:

```java
public interface UserStoreProvider extends SecurityProvider {
    User getUser(String username) throws UserStoreException;
    void validateUser(String username, char[] password) throws UserStoreException;
    void updateUser(User user) throws UserStoreException;
    void deleteUser(String username) throws UserStoreException;
}
```

## Configuration Beans

Located in `org.glassfish.security.services.config`:

| Bean | Purpose |
|------|---------|
| `SecurityConfiguration` | Root security configuration |
| `AuthenticationService` | Auth service config |
| `AuthorizationService` | Authz service config |
| `RoleMappingService` | Role mapping config |
| `LoginModuleConfig` | Login module configuration |
| `SecurityProvider` | Security provider config |

## Admin Commands

Located in `org.glassfish.security.services.commands`:

| Command | Purpose |
|---------|---------|
| `CreateSecurityService` | Create a security service |
| `CreateSecurityProvider` | Create a security provider |
| `CreateLoginModuleConfig` | Create login module config |

## Common Utilities

Located in `org.glassfish.security.services.common`:

- `Secure` - Security-related utility methods
- `SecurityScope` - Security context scope
- `SecurityScopeContext` - Security scope context holder
- `SubjectUtil` - Subject manipulation utilities

## Package Structure

```
org.glassfish.security.services/
├── api/
│   ├── SecurityService.java            # Base service interface
│   ├── authentication/
│   │   ├── AuthenticationService.java
│   │   └── ImpersonationService.java
│   ├── authorization/
│   │   ├── AuthorizationService.java
│   │   ├── RoleMappingService.java
│   │   ├── AzSubject.java
│   │   ├── AzAction.java
│   │   ├── AzResource.java
│   │   ├── AzResult.java
│   │   └── AzObligations.java
│   ├── context/
│   │   └── SecurityContextService.java
│   └── common/
│       ├── Attribute.java
│       └── Attributes.java
├── spi/
│   ├── SecurityProvider.java           # Base provider interface
│   ├── authentication/
│   │   └── UserStoreProvider.java
│   └── authorization/
│       ├── AuthorizationProvider.java
│       └── RoleMappingProvider.java
├── impl/
│   ├── AuthenticationServiceImpl.java
│   ├── AuthorizationServiceImpl.java
│   ├── ImpersonationServiceImpl.java
│   ├── SecurityContextServiceImpl.java
│   └── authorization/
│       ├── AzSubjectImpl.java
│       ├── AzActionImpl.java
│       └── AzResultImpl.java
├── provider/
│   └── authorization/
│       ├── SimpleAuthorizationProviderImpl.java
│       └── SimpleRoleMappingProviderImpl.java
├── config/
│   ├── SecurityConfiguration.java
│   ├── AuthenticationService.java
│   └── AuthorizationService.java
└── commands/
    ├── CreateSecurityService.java
    ├── CreateSecurityProvider.java
    └── CreateLoginModuleConfig.java
```

## Using the Services

### Programmatic Authentication

```java
import org.glassfish.security.services.api.authentication.AuthenticationService;

@Inject
private AuthenticationService authService;

public void authenticateUser(String username, String password) {
    try {
        Subject subject = authService.login(username, password.toCharArray(), null);
        // Use the authenticated subject
    } catch (LoginException e) {
        // Handle authentication failure
    }
}
```

### Authorization Check

```java
import org.glassfish.security.services.api.authorization.AuthorizationService;

@Inject
private AuthorizationService authService;

public boolean canAccessResource(String resource, String action) {
    AzSubject subject = new AzSubjectImpl(getCurrentSubject());
    AzAction azAction = new AzActionImpl(action);
    AzResource azResource = new AzResourceImpl(resource);
    AzEnvironment env = new AzEnvironmentImpl();

    try {
        AzResult result = authService.authorize(subject, azAction, azResource, env);
        return result.isAllowed();
    } catch (AuthorizationException e) {
        return false;
    }
}
```

### Impersonation

```java
import org.glassfish.security.services.api.authentication.ImpersonationService;

@Inject
private ImpersonationService impersonationService;

public void runAsAdmin(Runnable action) {
    try {
        Subject admin = impersonationService.impersonate("admin", null, null, false);
        impersonationService.runAs(admin, () -> {
            action.run();
            return null;
        });
    } catch (Exception e) {
        // Handle error
    }
}
```

### Security Context

```java
import org.glassfish.security.services.api.context.SecurityContextService;

@Inject
private SecurityContextService contextService;

public void executeWithContext(Runnable action) {
    SecurityScope scope = contextService.getSecurityScope();
    try {
        contextService.runWithContext(scope, () -> {
            action.run();
            return null;
        });
    } catch (Exception e) {
        // Handle error
    }
}
```

## Creating a Custom Security Provider

To create a custom authorization provider:

```java
import org.glassfish.security.services.spi.authorization.AuthorizationProvider;

@Service
@Scoped(Singleton.class)
public class MyAuthorizationProvider implements AuthorizationProvider {

    @Override
    public String getName() {
        return "MyAuthorizationProvider";
    }

    @Override
    public AzResult authorize(AzSubject subject, AzAction action,
                             AzResource resource, AzEnvironment env)
            throws AuthorizationException {
        // Your authorization logic
        boolean allowed = checkPermission(subject, action, resource);
        return new AzResultImpl(allowed, null, null);
    }

    // Implement other required methods...
}
```

## Dependencies

Key dependencies:
- `hk2` - Dependency injection
- `config-api` - Configuration management
- `security` (core module) - Core security implementation
- `internal-api` - Internal APIs
- `epicyro` - JACC provider

## Integration Points

The services module integrates with:
- **Core Security** - Uses JAAS login modules and realms
- **Admin CLI** - Commands for service configuration
- **Config API** - Service configuration beans
- **JACC** - Java Authorization Contract for Containers
