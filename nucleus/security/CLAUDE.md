# CLAUDE.md - Security

This file provides guidance for working with the `nucleus/security` module - Payara's security infrastructure.

## Module Overview

The `nucleus/security` module provides core security infrastructure for Payara, including authentication (JAAS login modules), authorization, realms, SSL/TLS, and admin security.

## Sub-Modules

- **core** - Core security infrastructure (JAAS, realms, login modules, security manager)
- **services** - Security services API (authentication, authorization, impersonation)
- **ssl-impl** - SSL/TLS implementation
- **core-l10n** - Localization for core
- **services-l10n** - Localization for services

## Build Commands

```bash
# Build all security modules
mvn -DskipTests clean package -f nucleus/security/pom.xml

# Build specific module
mvn -DskipTests clean package -f nucleus/security/core/pom.xml
mvn -DskipTests clean package -f nucleus/security/services/pom.xml
mvn -DskipTests clean package -f nucleus/security/ssl-impl/pom.xml
```

## Architecture

### Security Layer Architecture

```
┌────────────────────────────────────────────────────────────┐
│                      Application Layer                      │
│  (EJBs, Servlets, Admin Commands)                            │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                      Security Services                      │
├────────────────────────────────────────────────────────────┤
│  AuthenticationService  │  AuthorizationService  │            │
│  └─→ Login modules         │  └─→ Policy enforcement    │
│  └─→ Realms               │  └─→ Role mapping          │
│                                                               │
│  ImpersonationService  │  SecurityContextService        │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                      Security Core                          │
├────────────────────────────────────────────────────────────┤
│  SecurityManager         │  JAAS Bridge                 │
│  SecurityLifecycle       │  SSL/TLS                     │
│  Admin Commands          │  Audit                       │
└────────────────────────────────────────────────────────────┘
```

### Authentication Flow

```
HTTP/EJB Request
        │
        ├─→ SecurityManager.preInvoke()
        │      └─→ Establishes security context
        │
        ├─→ LoginModule (JAAS)
        │      ├─→ ClientCertificateLoginModule
        │      ├─→ FileLoginModule
        │      ├─→ LDAPLoginModule
        │      └─→ DigestLoginModule
        │
        ├─→ Realm
        │      ├─→ authenticate(user, password)
        │      ├─→ getGroups(user)
        │      └─→ FileRealm, LDAPRealm, CertificateRealm
        │
        └─→ Subject created
               ├─→ Principal (caller identity)
               └─→ Credentials (roles, groups)
```

### Authorization Flow

```
EJB Method Invocation
        │
        ├─→ SecurityManager.authorize()
        │      └─→ Check if caller has required role
        │
        ├─→ AuthorizationService
        │      ├─→ AzAction (method being called)
        │      ├─→ AzSubject (caller)
        │      ├─ AzResource (EJB being accessed)
        │      └─→ AzObligations (required roles/permissions)
        │
        └─→ AzResult (ALLOW/DENY)
```

## Core Security Components

### SecurityManager

```java
public interface SecurityManager {
    // Authorize EJB method invocation
    boolean authorize(ComponentInvocation inv);

    // Get caller principal
    Principal getCallerPrincipal();

    // Check if caller has role
    boolean isCallerInRole(String role);

    // Setup security context before invocation
    void preInvoke(ComponentInvocation inv);

    // Execute method with security context
    Object invoke(Object bean, Method method, Object[] params);

    // Cleanup after invocation
    void postInvoke(ComponentInvocation inv);
}
```

### JAAS Login Modules

#### FileLoginModule

```java
public class FileLoginModule extends BasePasswordLoginModule {

    @Override
    protected boolean authenticate(String username, char[] password) {
        // Authenticate against file realm (keyfile)
        FileRealm realm = getRealm();
        return realm.authenticate(username, password);
    }

    @Override
    protected Enumeration<String> getGroups(String username) {
        // Get groups from file realm
        FileRealm realm = getRealm();
        return realm.getGroupNames(username);
    }
}
```

#### LDAPLoginModule

```java
public class LDAPLoginModule extends BasePasswordLoginModule {

    @Override
    protected boolean authenticate(String username, char[] password) {
        // Authenticate against LDAP server
        LDAPRealm realm = (LDAPRealm) getRealm();
        return realm.authenticate(username, password);
    }

    @Override
    protected Enumeration<String> getGroups(String username) {
        // Get groups from LDAP
        return realm.getGroupNames(username);
    }
}
```

#### ClientCertificateLoginModule

```java
public class ClientCertificateLoginModule extends BaseCertificateLoginModule {

    @Override
    protected boolean authenticate(X509Certificate[] certificates) {
        // Validate client certificates
        CertificateRealm realm = (CertificateRealm) getRealm();
        return realm.authenticate(certificates);
    }

    @Override
    protected Enumeration<String> getGroups(X509Certificate[] certificates) {
        // Get groups based on certificate
        // May use certificate OU fields or mapping
        return realm.getGroupNames(certificates);
    }
}
```

## Realms

### FileRealm

File-based user authentication:

```java
@Service
public class FileRealm extends AbstractStatefulRealm {

    private String keyfilePath;  // Usually domains/domain1/config/keyfile

    @Override
    public boolean authenticate(String user, char[] password) {
        // Read from keyfile
        // Format: username; groups;{encrypted password}
    }

    @Override
    public Enumeration<String> getGroupNames(String username) {
        // Return user groups
    }

    @Override
    public void updateUser(String user, char[] password, String[] groups) {
        // Update user in keyfile
    }

    @Override
    public void addUser(String user, char[] password, String[] groups) {
        // Add user to keyfile
    }

    @Override
    public void removeUser(String user) {
        // Remove user from keyfile
    }
}
```

### LDAPRealm

LDAP-based authentication:

```java
@Service
public class LDAPRealm extends AbstractStatefulRealm {

    private String url;
    private String baseDn;
    private String userSearchFilter;
    private String groupSearchFilter;

    @Override
    public boolean authenticate(String user, char[] password) {
        // Bind to LDAP server
        DirContext ctx = new InitialDirContext(getLdapEnv(user, password));
        // Validate connection
        return ctx != null;
    }

    @Override
    public Enumeration<String> getGroupNames(String user) {
        // Search LDAP for user groups
        // Use groupSearchFilter to find group memberships
    }
}
```

### CertificateRealm

Certificate-based authentication:

```java
@Service
public class CertificateRealm extends AbstractRealm {

    @Override
    public boolean authenticate(X509Certificate[] certificates) {
        // Validate certificate chain
        // Check expiration
        // Check revocation
        // Verify trust
    }

    @Override
    public Enumeration<String> getGroupNames(X509Certificate[] certificates) {
        // Extract groups from certificate OU fields
        // Or map certificates to groups via configuration
    }
}
```

## Admin Commands

### Realm Management

```bash
# Create file realm
asadmin create-auth-realm \
    --classname com.sun.enterprise.security.auth.realm.file.FileRealm \
    --property file=${com.sun.aas.instanceRoot}/config/keyfile

# Create LDAP realm
asadmin create-auth-realm \
    --classname com.sun.enterprise.security.auth.realm.ldap.LDAPRealm \
    --property url=ldap://localhost:389 \
    --property base-dn=dc=example,dc=com

# List realms
asadmin list-auth-realms

# Delete realm
asadmin delete-auth-realm fileRealm
```

### User Management

```bash
# Create file user
asadmin create-file-user \
    --usergroups admin,guest \
    --authrealmname fileRealm \
    username

# List file users
asadmin list-file-users --authrealmname fileRealm

# Update file user
asadmin update-file-user \
    --groups admin,deployer \
    --authrealmname fileRealm \
    username

# Delete file user
asadmin delete-file-user --authrealmname fileRealm username
```

### Password Management

```bash
# Change admin password
asadmin change-admin-password

# Create password alias
asadmin create-password-alias jms-password

# List password aliases
asadmin list-password-aliases
```

### JAAS Configuration

```bash
# Create login module config
asadmin create-login-module-config \
    --loginmodulename fileRealm \
    --classname com.sun.enterprise.security.auth.login.FileLoginModule

# Delete login module config
asadmin delete-login-module-config fileRealm
```

### Secure Admin

```bash
# Enable secure admin
asadmin enable-secure-admin

# Disable secure admin
asadmin disable-secure-admin

# Set secure admin configuration
asadmin set-secure-admin-configuration \
    --jmxConnectorEnabled=true \
    --sslEnabled=true
```

## SSL/TLS

### SSL Configuration

```bash
# Enable SSL for HTTP listener
asadmin create-network-listener \
    --listenerport 8181 \
    --protocol sec-admin-listener \
    --securityenabled=true

# Configure SSL
asadmin set server-config.network-config.protocols.protocol.sec-admin-listener.ssl \
    --cert-nickname=s1as \
    --ssl2-enabled=false \
    --ssl3-enabled=false \
    --tls-enabled=true
```

### Certificate Management

```bash
# List certificates in keystore
asadmin list-jvm-options -Djavax.net.ssl.keyStore

# Generate CSR
keytool -certreq -keyalg RSA -alias s1as -file s1as.csr

# Import certificate
keytool -import -alias s1as -file s1as.crt \
    -keystore ${com.sun.aas.instanceRoot}/config/keystore.jks
```

## JACC (Java Authorization Contract for Containers)

### JACC Provider

```bash
# Create JACC provider
asadmin create-jacc-provider \
    --policyprovider com.sun.enterprise.security.jacc.provider.PolicyConfigurationFactoryImpl

# Set default JACC provider
asadmin set-jacc-provider --providername JACC
```

## Security Services API

### Using AuthenticationService

```java
@Inject
private AuthenticationService authService;

public void authenticateUser(String username, String password) {
    // Authenticate user
    Subject subject = authService.login(username, password);

    // Check authentication
    if (subject == null) {
        throw new AuthenticationException("Authentication failed");
    }
}
```

### Using AuthorizationService

```java
@Inject
private AuthorizationService authorizationService;

public void checkPermission(String resource, String action) {
    AzAction azAction = new AzActionImpl(action);
    AzResource azResource = new AzResourceImpl(resource);
    AzSubject azSubject = new AzSubjectImpl(getCallerPrincipal());

    AzResult result = authorizationService.authorize(
        azSubject,
        azAction,
        azResource,
        Collections.emptyMap()
    );

    if (!result.isAllowed()) {
        throw new AccessDeniedException("Access denied");
    }
}
```

### Using ImpersonationService

```java
@Inject
private ImpersonationService impersonationService;

public void runAs(String username, Runnable action) {
    // Impersonate another user
    impersonationService.impersonate(username, () -> {
        // Run as specified user
        action.run();
    });
}
```

## Security Context

### Programmatic Login

```java
import com.sun.enterprise.security.WebAndEjbToJaasBridge;

// Programmatic login
WebAndEjbToJaasBridge.login(username, password.toCharArray());

// Get current subject
Subject subject = Subject.getSubject(AccessController.getContext());

// Check if caller in role
boolean isAdmin = WebAndEjbToJaasBridge.isCallerInRole("admin");
```

### Run-As Impersonation

```java
import jakarta.annotation.security.RunAs;

@RunAs("admin")
public class AdminService {

    public void adminOperation() {
        // This method runs as admin user
    }
}
```

## Configuration Files

### server.policy

Java security policy file location:
```
{domain}/config/server.policy
```

### login.conf

JAAS login configuration:
```
{domain}/config/login.conf
```

### keyfile

File realm user database:
```
{domain}/config/keyfile
```

Format:
```
username;group1,group2,...;{encryptedPassword}
```

## Package Structure

### Core Module

```
com.sun.enterprise.security/
├── SecurityManager.java              # Main security manager
├── SecurityLifecycle.java           # Security lifecycle
├── auth/
│   ├── login/                       # Login modules
│   │   ├── FileLoginModule.java
│   │   ├── LDAPLoginModule.java
│   │   └── ClientCertificateLoginModule.java
│   └── realm/                       # Realms
│       ├── FileRealm.java
│       ├── LDAPRealm.java
│       └── certificate/CertificateRealm.java
├── cli/                            # Admin commands
│   ├── CreateAuthRealm.java
│   ├── CreateFileUser.java
│   └── ChangeAdminPassword.java
└── audit/                          # Audit logging
```

### Services Module

```
org.glassfish.security.services.api/
├── SecurityService.java             # Base service interface
├── authentication/
│   └── AuthenticationService.java
├── authorization/
│   └── AuthorizationService.java
└── common/
    ├── Secure.java                  # Security utilities
    └── SecurityScope.java           # Security context
```

## Best Practices

1. **Use proper realms** - LDAP for enterprise, File for simple setups
2. **Protect admin access** - Enable secure admin and use SSL
3. **Use password aliases** - Never hardcode passwords
4. **Configure SSL properly** - Disable old protocols (SSLv2, SSLv3)
5. **Use certificates** - Client certificates for enhanced security
6. **Audit security events** - Enable audit logging for compliance

## Related Modules

- **admin/rest** - REST API security
- **common/internal-api** - Security-related APIs
- **appserver/security** - Application-level security

## Troubleshooting

### Authentication Failures

```bash
# Enable debug for JAAS
asadmin create-jvm-options \
    "-Djava.security.debug=access,failure"

# Check realm configuration
asadmin get-auth-realm
```

### SSL Issues

```bash
# Check SSL configuration
asadmin get-server-config.network-config.protocols.protocol.*.ssl.*

# Enable SSL debug
asadmin create-jvm-options \
    "-Djavax.net.debug=ssl,handshake"

# Verify certificate
keytool -list -v -keystore keystore.jks
```

### Login Module Issues

```bash
# List login modules
asadmin list-login-module-configs

# Check login module config
asadmin get "server-config.security-service.login-module-config.*"
```

## Notes

- JAAS is the foundation for authentication
- Realms provide pluggable user stores
- Authorization uses JACC (Java Authorization Contract for Containers)
- Security context is maintained for the request scope
- SSL/TLS configuration is per-network listener
