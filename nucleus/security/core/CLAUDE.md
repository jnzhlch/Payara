# CLAUDE.md - Security Core

This file provides guidance for working with the `nucleus/security/core` module - Payara's core security infrastructure.

## Module Overview

The core module provides the foundational security infrastructure for Payara, including:
- JAAS (Java Authentication and Authorization Service) login modules
- Security realms (File, LDAP, Certificate, Solaris)
- SecurityManager for EJB/web authorization
- Admin CLI commands for security management
- Audit framework
- SSL integration

## Build Commands

```bash
# Build the core module
mvn -DskipTests clean package -f nucleus/security/core/pom.xml

# Build with tests
mvn clean package -f nucleus/security/core/pom.xml
```

## Architecture

### Security Manager Interface

The `SecurityManager` interface is the entry point for container security:

```java
public interface SecurityManager {
    boolean authorize(ComponentInvocation inv);
    Principal getCallerPrincipal();
    boolean isCallerInRole(String role);
    void preInvoke(ComponentInvocation inv);
    Object invoke(Object bean, Method method, Object[] params) throws Throwable;
    void postInvoke(ComponentInvocation inv);
    Subject getCurrentSubject();
    void resetPolicyContext();
}
```

### JAAS Login Module Hierarchy

```
BaseLoginModule
    │
    ├── BasePasswordLoginModule
    │       ├── FileLoginModule
    │       ├── LDAPLoginModule
    │       └── SolarisLoginModule
    │
    └── BaseCertificateLoginModule
            └── ClientCertificateLoginModule
```

### Realm Hierarchy

```
Realm (interface)
    │
    └── AbstractRealm
            │
            ├── AbstractStatefulRealm
            │       ├── FileRealm
            │       ├── LDAPRealm
            │       └── SolarisRealm
            │
            └── CertificateRealm
```

## Key Components

### Login Modules

Located in `com.sun.enterprise.security.auth.login`:

| Class | Purpose |
|-------|---------|
| `FileLoginModule` | Authenticates against file-based keyfile |
| `LDAPLoginModule` | Authenticates against LDAP directory |
| `ClientCertificateLoginModule` | Authenticates using X.509 client certificates |
| `SolarisLoginModule` | Authenticates against Solaris users |
| `LoginContextDriver` | Manages JAAS login context |

### Realms

Located in `com.sun.enterprise.security.auth.realm`:

| Class | Purpose |
|-------|---------|
| `FileRealm` | File-based user store (keyfile format) |
| `LDAPRealm` | LDAP directory user store |
| `CertificateRealm` | X.509 certificate-based authentication |
| `SolarisRealm` | Solaris OS user authentication |
| `RealmsManager` | Manages realm lifecycle and configuration |

### Admin Commands

Located in `com.sun.enterprise.security.cli`:

| Command | Purpose |
|---------|---------|
| `CreateAuthRealm` | Create a new authentication realm |
| `DeleteAuthRealm` | Delete an authentication realm |
| `CreateFileUser` | Create user in file realm |
| `DeleteFileUser` | Delete user from file realm |
| `UpdateFileUser` | Update user in file realm |
| `ListFileUsers` | List users in file realm |
| `ChangeAdminPassword` | Change admin password |
| `CreatePasswordAlias` | Create password alias |
| `ListPasswordAliases` | List password aliases |
| `DeletePasswordAlias` | Delete password alias |
| `CreateAuditModule` | Create audit module |
| `DeleteAuditModule` | Delete audit module |

### Audit Framework

Located in `com.sun.enterprise.security.audit`:

- `AuditManager` - Manages audit modules
- `BaseAuditManager` - Base implementation for audit modules

### Secure Admin

Located in `com.sun.enterprise.security.admin.cli`:

- `EnableSecureAdminCommand` - Enable secure administration
- `DisableSecureAdminCommand` - Disable secure administration
- `SecureAdminConfigMonitor` - Monitors secure admin configuration changes

## Configuration Files

### Bundled Resources (in src/main/resources)

| File | Purpose |
|------|---------|
| `keyfile` | Default file realm user database |
| `admin-keyfile` | Admin user database |
| `server.policy` | Java security policy |
| `login.conf` | JAAS login configuration |
| `secure.seed` | Secure random seed |

### Keyfile Format

```
username;group1,group2,...;{encryptedPassword}
```

Example:
```
admin;admin;{SSHA256}hashedPassword...
guest;guest-users;{SSHA256}hashedPassword...
```

## Package Structure

```
com.sun.enterprise.security/
├── SecurityManager.java              # Main security manager interface
├── SecurityLifecycle.java            # Security lifecycle management
├── SecurityContext.java              # Security context holder
├── SecurityConfigListener.java       # Config change listener
├── auth/
│   ├── login/                        # Login modules
│   │   ├── BasePasswordLoginModule.java
│   │   ├── BaseCertificateLoginModule.java
│   │   ├── FileLoginModule.java
│   │   ├── LDAPLoginModule.java
│   │   └── LoginContextDriver.java
│   └── realm/                        # Realms
│       ├── Realm.java                # Realm interface
│       ├── AbstractRealm.java
│       ├── AbstractStatefulRealm.java
│       ├── RealmsManager.java
│       ├── file/FileRealm.java
│       ├── ldap/LDAPRealm.java
│       ├── certificate/CertificateRealm.java
│       └── solaris/SolarisRealm.java
├── cli/                              # Admin CLI commands
├── admin/cli/                        # Secure admin commands
├── audit/                            # Audit framework
└── common/iiop/security/             # IIOP security
```

## Adding a New Login Module

To create a custom login module:

1. Extend `BasePasswordLoginModule` or `BaseCertificateLoginModule`
2. Implement `authenticate()` method
3. Implement `getGroups()` method
4. Register in `login.conf`

```java
public class MyLoginModule extends BasePasswordLoginModule {
    @Override
    protected boolean authenticate(String username, char[] password) {
        // Your authentication logic
        return true;
    }

    @Override
    protected Enumeration<String> getGroups(String username) {
        // Return groups for user
        return new Vector<>(Arrays.asList("group1", "group2")).elements();
    }
}
```

## Adding a New Realm

To create a custom realm:

1. Extend `AbstractRealm` or `AbstractStatefulRealm`
2. Implement required methods
3. Register via `@Service` annotation
4. Add CLI command support

```java
@Service
public class MyRealm extends AbstractRealm {
    @Override
    public String getAuthType() {
        return "MyRealm";
    }

    @Override
    public Enumeration<String> getGroupNames(String username) throws InvalidOperationException, NoSuchUserException {
        // Return groups
    }

    // Implement other required methods...
}
```

## Dependencies

Key dependencies:
- `hk2` - Dependency injection
- `config-api` - Configuration management
- `ssl-impl` - SSL/TLS support
- `deployment-common` - Deployment integration
- `internal-api` - Internal APIs
- `epicyro` - JACC provider

## Related Modules

- `nucleus/security/services` - Security services API
- `nucleus/security/ssl-impl` - SSL implementation
- `appserver/security` - Application-level security
