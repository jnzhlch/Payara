# CLAUDE.md - Core EE Security

This file provides guidance for working with the `appserver/security/core-ee` module - Java EE security core classes.

## Module Overview

The core-ee module provides the core Java EE security implementation including authentication, authorization, role mapping, and the security deployer.

**Key Components:**
- **SecurityDeployer** - Deploys security configuration
- **WebSecurityManager** - Runtime permission checking
- **LoginModules** - JAAS login modules
- **Realms** - Authentication realms

## Build Commands

```bash
# Build core-ee module
mvn -DskipTests clean package -f appserver/security/core-ee/pom.xml
```

## Module Contents

```
core-ee/src/main/java/com/sun/enterprise/security/
├── ee/
│   ├── SecurityDeployer.java
│   ├── SecuritySniffer.java
│   ├── WebSecurityManager.java
│   ├── auth/login/
│   │   ├── JDBCLoginModule.java
│   │   ├── DigestLoginModule.java
│   │   └── PamLoginModule.java
│   ├── auth/realm/
│   │   ├── JDBCRealm.java
│   │   ├── DigestRealm.java
│   │   └── PamRealm.java
│   └── authorization/
│       ├── PolicyProvider.java
│       └── EJBPolicyContextDelegate.java
└── acl/
    ├── RoleMapper.java
    ├── WebResource.java
    └── EJBResource.java
```

## Security Deployment

```
Application Deployment
       │
       ▼
[SecuritySniffer]
       │
       ├─→ Detect security annotations
       ├─→ Detect web.xml security constraints
       └─→ Detect permissions.xml
       │
       ▼
[SecurityDeployer.prepare()]
       │
       ├─→ Parse security descriptors
       ├─→ Load role mappings
       ├─→ Process permissions.xml
       └─→ Create policy context
       │
       ▼
[SecurityDeployer.load()]
       │
       ├─→ Set up WebSecurityManager
       ├─→ Configure role mapper
       └─→ Initialize authorization
```

## WebSecurityManager

```java
public class WebSecurityManager implements PolicyContext {

    private final String appId;
    private RoleMapper roleMapper;
    private PermissionCache permissionCache;

    public WebSecurityManager(String appId, RoleMapper roleMapper) {
        this.appId = appId;
        this.roleMapper = roleMapper;
        this.permissionCache = PermissionCacheFactory.createPermissionCache(appId);
    }

    public boolean checkPermission(Permission permission, Principal[] principals,
                                   String[] roleNames) {
        // Check cache first
        CachedPermission cached = permissionCache.getCachedPermission(permission);
        if (cached != null) {
            return cached.isGranted();
        }

        // Check with role mapper
        boolean granted = roleMapper.hasPermission(permission, principals, roleNames);

        // Cache result
        permissionCache.cachePermission(permission, granted);

        return granted;
    }

    public void destroy() {
        permissionCache.clear();
    }
}
```

## Role Mapping

```java
public interface RoleMapper {

    /**
     * Check if principal has role
     */
    boolean hasRole(Principal principal, String role);

    /**
     * Check if principal has permission
     */
    boolean hasPermission(Permission permission, Principal[] principals, String[] roleNames);

    /**
     * Get roles for principal
     */
    Set<String> getRoles(Principal principal);
}
```

## Login Modules

### JDBCLoginModule

```java
public class JDBCLoginModule implements LoginModule {

    private Subject subject;
    private CallbackHandler callbackHandler;
    private Map<String, ?> sharedState;
    private Map<String, ?> options;

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler,
                          Map<String, ?> sharedState, Map<String, ?> options) {
        this.subject = subject;
        this.callbackHandler = callbackHandler;
        this.sharedState = sharedState;
        this.options = options;
    }

    @Override
    public boolean login() throws LoginException {
        // Get credentials
        Callback[] callbacks = new Callback[2];
        callbacks[0] = new NameCallback("Username: ");
        callbacks[1] = new PasswordCallback("Password: ", false);

        try {
            callbackHandler.handle(callbacks);
        } catch (Exception e) {
            throw new LoginException(e.getMessage());
        }

        String username = ((NameCallback) callbacks[0]).getName();
        char[] password = ((PasswordCallback) callbacks[1]).getPassword();

        // Authenticate with JDBC realm
        String dsJndi = (String) options.get("datasourceJndi");
        String userQuery = (String) options.get("userQuery");
        String groupQuery = (String) options.get("groupQuery");

        // Query database and validate
        return authenticateJDBC(username, password, dsJndi, userQuery, groupQuery);
    }

    @Override
    public boolean commit() throws LoginException {
        // Add principal to subject
        subject.getPrincipals().add(principal);
        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        subject.getPrincipals().clear();
        return true;
    }
}
```

## Digest Authentication

```java
public class DigestLoginModule implements LoginModule {

    @Override
    public boolean login() throws LoginException {
        // Handle digest authentication
        // Extract digest parameters from request
        String username = getDigestUsername();
        String response = getDigestResponse();
        String nonce = getDigestNonce();
        String realm = getDigestRealm();
        String uri = getDigestUri();
        String qop = getDigestQop();
        String nc = getDigestNC();
        String cnonce = getDigestCnonce();

        // Calculate expected response
        String expectedResponse = calculateDigestResponse(username, realm, password,
                                                         nonce, uri, qop, nc, cnonce, method);

        if (expectedResponse.equals(response)) {
            // Authentication successful
            return true;
        }

        return false;
    }

    private String calculateDigestResponse(String username, String realm, String password,
                                           String nonce, String uri, String qop, String nc,
                                           String cnonce, String method) {
        // A1 = MD5(username:realm:password)
        String a1 = MD5(username + ":" + realm + ":" + password);

        // A2 = MD5(method:uri)
        String a2 = MD5(method + ":" + uri);

        // Response = MD5(A1:nonce:nc:cnonce:qop:A2)
        String response = MD5(a1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + a2);

        return response;
    }
}
```

## Programmatic Login

```java
public class ProgrammaticLogin {

    private String loginContextName;

    public boolean login(String username, String password) {
        try {
            LoginContext lc = new LoginContext(loginContextName,
                new PasswordCallbackHandler(username, password));
            lc.login();

            // Register with security context
            registerPrincipal(lc.getSubject());

            return true;
        } catch (LoginException e) {
            return false;
        }
    }

    public boolean logout() {
        try {
            Subject subject = getSubject();
            if (subject != null) {
                LoginContext lc = new LoginContext(loginContextName, subject);
                lc.logout();
            }
            unregisterPrincipal();
            return true;
        } catch (LoginException e) {
            return false;
        }
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.authentication-api` | Jakarta Authentication API |
| `jakarta.authorization-api` | Jakarta Authorization API |
| `jakarta.security.enterprise-api` | Jakarta EE Security API |
| `exousia` | Authorization service |
| `epicyro` | Authentication service |
| `security` | Base security classes |

## Notes

- **JAAS** - Java Authentication and Authorization Service
- **JACC** - Jakarta Authorization Contract for Containers
- **Role Mapping** - Maps principals to roles
- **Permission Cache** - Caches authorization decisions
- **Realms** - Pluggable authentication backends
