# CLAUDE.md - Security

This file provides guidance for working with the `appserver/security` module - Payara Server security infrastructure.

## Module Overview

The security module provides Payara Server's security infrastructure including authentication, authorization, realms, and integration with Jakarta EE security APIs.

**Key Components:**
- **core-ee** - Java EE security core classes and deployer
- **webintegration** - Web container security integration
- **realm-stores** - Identity stores for authentication (File, Certificate, PAM, Solaris)
- **ejb.security** - EJB/IIOP security (CSIv2)
- **jacc.provider.inmemory** - JACC policy provider
- **webservices.security** - Web services security

## Build Commands

```bash
# Build entire security module
mvn -DskipTests clean package -f appserver/security/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/security/core-ee/pom.xml
mvn -DskipTests clean package -f appserver/security/webintegration/pom.xml
mvn -DskipTests clean package -f appserver/security/realm-stores/pom.xml
```

## Architecture

### Module Structure

```
security/
├── core-ee/                    # Java EE security core
├── webintegration/             # Web container security
├── realm-stores/               # Identity stores
├── ejb.security/               # EJB/IIOP security
├── jacc.provider.inmemory/     # JACC provider
├── webservices.security/        # Web services security
├── appclient.security/         # App client security
├── security-all/               # Security aggregator
└── Localization modules
```

### Security Layer Architecture

```
Application (@RolesAllowed, @DenyAll, @PermitAll)
       │
       ▼
[Jakarta EE Security] (jakarta.security.enterprise)
       │
       ├─→ SecurityContext
       ├─→ IdentityStore
       └─→ AuthenticationMechanism
       │
       ▼
[Payara Security Core]
       │
       ├─→ SecurityDeployer - Deployment
       ├─→ WebSecurityManager - Runtime
       ├─→ RoleMapper - Role mapping
       └─→ PermissionCache - Authorization cache
       │
       ▼
[Realms] (Identity Stores)
       │
       ├─→ FileRealm - keyfile
       ├─→ CertificateRealm - Certificates
       ├─→ JDBCRealm - Database
       ├─→ PamRealm - PAM
       └─→ LDAPRealm - LDAP
```

## Core EE Security

### SecurityDeployer

```java
@Service
public class SecurityDeployer extends AbstractDeployer<SecurityContainer, SecurityApplicationContainer> {

    @Override
    public boolean prepare(DeploymentContext context) {
        // Parse security annotations
        // Parse web.xml security constraints
        // Process permissions.xml if present

        // Load role mappings
        loadRoleMappings(context);

        return true;
    }

    @Override
    public SecurityApplicationContainer load(SecurityContainer container, DeploymentContext context) {
        // Set up policy context
        // Configure permissions
        // Initialize security manager
    }
}
```

### WebSecurityManager

```java
public class WebSecurityManager implements PolicyContext {

    private PermissionCache permissionCache;

    public boolean checkPermission(Permission permission, Principal[] principals,
                                   String[] roleNames) {
        // Check cached permission first
        CachedPermission cached = permissionCache.getCachedPermission(permission);
        if (cached != null) {
            return cached.isGranted();
        }

        // Check policy
        boolean granted = checkPolicy(permission, principals, roleNames);

        // Cache result
        permissionCache.cachePermission(permission, granted);

        return granted;
    }
}
```

### Permissions XML

Payara supports `permissions.xml` for declaring permissions:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<permissions xmlns="https://jakarta.ee/xml/ns/jakartaee"
             version="3.0">
    <permission>
        <class-name>java.lang.RuntimePermission</class-name>
        <name>createClassLoader</name>
        <actions></actions>
    </permission>
    <permission>
        <class-name>java.net.SocketPermission</class-name>
        <name>example.com:80</name>
        <action>connect</action>
    </permission>
</permissions>
```

## Web Integration

### RealmAdapter

```java
public class RealmAdapter extends RealmBase {

    private Realm loginRealm;

    @Override
    public Principal authenticate(String username, String password) {
        // Delegate to login realm
        return loginRealm.authenticate(username, password);
    }

    @Override
    public boolean hasRole(Principal principal, String role) {
        // Check if principal has role
        return roleMapper.hasRole(principal, role);
    }
}
```

### Programmatic Login

```java
public class WebProgrammaticLogin implements ProgrammaticLogin {

    public boolean login(String username, String password) {
        try {
            // Authenticate with JAAS
            LoginContext lc = new LoginContext(loginContextName,
                new WebPasswordCallbackHandler(username, password));
            lc.login();

            // Register principal
            registerPrincipal(lc.getSubject());

            return true;
        } catch (LoginException e) {
            return false;
        }
    }

    public boolean logout() {
        // Unregister principal
        unregisterPrincipal();
        return true;
    }
}
```

## Realms and Identity Stores

### Realm Types

| Realm | Description |
|-------|-------------|
| **FileRealm** | File-based authentication (keyfile) |
| **CertificateRealm** | X.509 certificate authentication |
| **JDBCRealm** | Database-backed authentication |
| **PamRealm** | Pluggable Authentication Modules (PAM) |
| **LDAPRealm** | LDAP directory authentication |

### FileRealm

```java
public class FileRealm extends RealmBase {

    private String keyFile;

    @Override
    protected Principal getPrincipal(String username) {
        // Read from keyfile
        Properties users = loadKeyFile(keyFile);
        String password = users.getProperty(username + ".password");
        String groups = users.getProperty(username + ".groups");

        return new FilePrincipal(username, password, groups);
    }
}
```

### JDBCRealm

```java
public class JDBCRealm extends RealmBase {

    private String dataSourceJNDI;
    private String userQuery;
    private String groupQuery;

    @Override
    protected Principal getPrincipal(String username) {
        // Query database
        DataSource ds = (DataSource) new InitialContext().lookup(dataSourceJNDI);

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(userQuery)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String password = rs.getString("password");
                List<String> groups = loadGroups(username, conn);
                return new JDBCPrincipal(username, password, groups);
            }
        }

        return null;
    }
}
```

### Identity Store (Jakarta EE Security)

```java
@ApplicationScoped
public class FileRealmIdentityStore implements IdentityStore {

    @Inject
    private FileRealmIdentityStoreConfiguration config;

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (credential instanceof UsernamePasswordCredential) {
            UsernamePasswordCredential upc = (UsernamePasswordCredential) credential;

            // Authenticate with file realm
            Realm realm = Realm.getInstance(config.getRealmName());
            Principal principal = realm.authenticate(upc.getUsername(), upc.getPasswordAsString());

            if (principal != null) {
                Set<String> groups = getGroups(principal);
                return CredentialValidationResult.success(principal.getName(), groups);
            }
        }

        return CredentialValidationResult.INVALID_RESULT;
    }
}
```

## Authentication Mechanisms

### Basic Authentication

```java
@ApplicationScoped
@CustomAuthenticationMechanism(
    realmName = "${realmName}"
)
public class BasicAuthenticationMechanism implements HttpAuthenticationMechanism {

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request,
                                               HttpServletResponse response) {
        // Extract Authorization header
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Basic ")) {
            // Decode credentials
            String credentials = new String(Base64.getDecoder()
                .decode(authHeader.substring(6)));
            String[] parts = credentials.split(":", 2);

            // Validate
            CredentialValidationResult result = identityStore.validate(
                new UsernamePasswordCredential(parts[0], parts[1])
            );

            if (result.getStatus() == VALID) {
                // Set security context
                setSecurityContext(result.getCallerPrincipal(), result.getCallerGroups());
                return SUCCESS;
            }
        }

        // Request credentials
        response.setHeader("WWW-Authenticate", "Basic realm=\"" + realmName + "\"");
        return SEND_FAILURE;
    }
}
```

### Form Authentication

```java
public class FormAuthenticator extends AuthenticatorBase {

    @Override
    protected boolean authenticate(Request request, Response response) {
        // Check for j_security_check
        if (request.getRequestURI().endsWith("/j_security_check")) {
            String username = request.getParameter("j_username");
            String password = request.getParameter("j_password");

            Principal principal = context.getRealm().authenticate(username, password);
            if (principal != null) {
                request.setUserPrincipal(principal);
                request.setAuthType("FORM");
                return true;
            }
        }
        return false;
    }
}
```

## JACC (Jakarta Authorization)

### JACC Policy Provider

```java
public class PolicyProvider extends Policy {

    @Override
    public PermissionCollection getPermissions(CodeSource codesource) {
        // Get permissions for codesource
        Permissions permissions = new Permissions();

        // Add declared permissions from permissions.xml
        for (DeclaredPermission dp : declaredPermissions) {
            permissions.add(dp.getPermission());
        }

        return permissions;
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
        // Check if permission is granted
        PermissionCollection pc = getPermissions(domain.getCodeSource());
        return pc.implies(permission);
    }
}
```

## EJB/IIOP Security

### CSIv2 Interceptors

```java
public class SecServerRequestInterceptor implements ServerRequestInterceptor {

    @Override
    void receive_request(ServerRequestInfo ri) {
        // Extract security context from IOR
        // Authenticate client
        // Establish security context
    }

    @Override
    void send_exception(ServerRequestInfo ri) {
        // Handle exception
    }

    @Override
    void send_reply(ServerRequestInfo ri) {
        // Add security context to reply
    }
}
```

### GSSUP (GSS Username Password)

```java
public class GSSUtils {

    public GSSUPToken createGSSUPToken(String username, String password,
                                         String targetName) {
        // Create GSSUP token for IIOP authentication
        GSSManager manager = GSSManager.getInstance();
        GSSName serverName = manager.createName(targetName, null);

        // Create credential
        GSSCredential creds = manager.createCredential(
            username, password, serverName
        );

        return new GSSUPToken(creds);
    }
}
```

## Security Configuration

### domain.xml

```xml
<security-config>
    <default-realm>file</default-realm>
    <default-principal>PasswordRealm</default-principal>
    <jacc>org.glassfish.exousia.AuthorizationServiceImpl</jacc>
</security-config>

<auth-realm name="file" classname="com.sun.enterprise.security.auth.realm.file.FileRealm">
    <property name="file" value="${com.sun.aas.instanceRoot}/config/keyfile"/>
    <property name="jaas-context" value="fileRealm"/>
</auth-realm>

<auth-realm name="certificate" classname="com.sun.enterprise.security.auth.realm.certificate.CertificateRealm">
    <property name="jaas-context" value="certificateRealm"/>
</auth-realm>

<auth-realm name="jdbc" classname="com.sun.enterprise.security.auth.realm.jdbc.JDBCRealm">
    <property name="datasource-jndi" value="jdbc/__default"/>
    <property name="user-table" value="users"/>
    <property name="user-name-column" value="username"/>
    <property name="password-column" value="password"/>
    <property name="group-table" value="groups"/>
    <property name="group-name-column" value="groupname"/>
    <property name="jaas-context" value="jdbcRealm"/>
</auth-realm>
```

### Login Configuration

```
fileRealm {
    com.sun.enterprise.security.auth.login.FileLoginModule required;
};

certificateRealm {
    com.sun.enterprise.security.auth.login.CertificateLoginModule required;
};

jdbcRealm {
    com.sun.enterprise.security.ee.auth.login.JDBCLoginModule required;
};
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.authentication-api` | Jakarta Authentication API |
| `jakarta.authorization-api` | Jakarta Authorization API |
| `jakarta.security.enterprise-api` | Jakarta EE Security API |
| `exousia` | Authorization service |
| `epicyro` | Authentication service |
| `web-core` | Web container integration |
| `ejb` | EJB security |
| `orb` | IIOP security |

## Notes

- **Jakarta EE Security** - Jakarta EE 10 security APIs
- **Exousia** - Authorization service implementation
- **Epicyro** - Authentication service implementation
- **File Realm** - Default file-based authentication
- **Certificate Realm** - X.509 certificate authentication
- **JDBCRealm** - Database-backed authentication
- **PAMRealm** - Pluggable Authentication Modules
- **JACC** - Jakarta Authorization Contract for Containers
- **CSIv2** - Common Secure Inter-ORB Protocol (IIOP security)
