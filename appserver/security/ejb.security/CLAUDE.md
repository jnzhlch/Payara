# CLAUDE.md - EJB Security

This file provides guidance for working with the `appserver/security/ejb.security` module - EJB and IIOP security (CSIv2).

## Module Overview

The ejb.security module provides security for EJB IIOP remoting using CSIv2 (Common Secure Inter-ORB Protocol), including GSSUP authentication and security context propagation.

**Key Components:**
- **CSIv2 Interceptors** - Client and server interceptors
- **GSSUtils** - GSSUP token handling
- **SecurityMechanismSelector** - Security mechanism negotiation

## Build Commands

```bash
# Build ejb.security module
mvn -DskipTests clean package -f appserver/security/ejb.security/pom.xml
```

## Module Contents

```
ejb.security/src/main/java/com/sun/enterprise/iiop/security/
├── SecClientRequestInterceptor.java
├── SecServerRequestInterceptor.java
├── SecIORInterceptor.java
├── GSSUtils.java
├── GSSUPToken.java
├── SecurityMechanismSelector.java
└── IIOPSSLUtilImpl.java
```

## CSIv2 Architecture

```
EJB Client
       │
       ▼
[SecClientRequestInterceptor]
       │
       ├─→ Create GSSUP token
       ├─→ Add to service context
       └─→ Forward to server
       │
       ▼
[IIOP Network]
       │
       ▼
[SecServerRequestInterceptor]
       │
       ├─→ Extract GSSUP token
       ├─→ Validate credentials
       ├─→ Establish security context
       └─→ Set principal
       │
       ▼
[EJB Container
```

## GSSUP Authentication

```java
public class GSSUtils {

    public GSSUPToken createGSSUPToken(String username, String password,
                                         String targetName) {
        try {
            GSSManager manager = GSSManager.getInstance();

            // Create server name
            GSSName serverName = manager.createName(targetName, null);

            // Create credential
            GSSCredential creds = manager.createCredential(
                username,
                GSSCredential.INDEFINITE_LIFETIME,
                serverName,
                GSSCredential.INITIATE_AND_ACCEPT
            );

            return new GSSUPToken(creds);

        } catch (GSSException e) {
            throw new SecurityException("Failed to create GSSUP token", e);
        }
    }

    public boolean validateGSSUPToken(GSSUPToken token, String password) {
        try {
            // Validate token credentials
            GSSCredential creds = token.getCredentials();

            // Verify password
            return verifyPassword(creds.getName(), password);

        } catch (GSSException e) {
            return false;
        }
    }
}
```

## Server Request Interceptor

```java
public class SecServerRequestInterceptor implements ServerRequestInterceptor {

    @Override
    public void receive_request(ServerRequestInfo ri) {
        try {
            // Extract security context from IOR
            CompoundSecurityContext secCtx = extractSecurityContext(ri);

            // Get client authentication token
            GSSUPToken token = secCtx.getGSSUPToken();

            if (token != null) {
                // Validate credentials
                String username = token.getUsername();
                String password = token.getPassword();

                // Authenticate
                Realm realm = Realm.getInstance();
                Principal principal = realm.authenticate(username, password);

                if (principal != null) {
                    // Establish security context
                    SecurityContext.establishSecurityContext(principal);
                }
            }

        } catch (Exception e) {
            throw new SecurityException("Authentication failed", e);
        }
    }

    @Override
    public void send_exception(ServerRequestInfo ri) {
        // Handle exception
    }

    @Override
    public void send_reply(ServerRequestInfo ri) {
        // Add security context to reply
    }
}
```

## Client Request Interceptor

```java
public class SecClientRequestInterceptor extends ClientRequestInterceptor {

    @Override
    public void send_request(ClientRequestInfo ri) {
        try {
            // Get current security context
            SecurityContext secCtx = SecurityContext.getCurrent();
            Principal principal = secCtx.getCallerPrincipal();

            if (principal != null) {
                // Create GSSUP token
                GSSUPToken token = GSSUtils.createGSSUPToken(
                    principal.getName(),
                    secCtx.getPassword(),
                    ri.effective_target()
                );

                // Add to service context
                ri.add_service_context(createSecurityContext(token));
            }

        } catch (Exception e) {
            throw new SecurityException("Failed to add security context", e);
        }
    }

    @Override
    public void receive_exception(ClientRequestInfo ri) {
        // Handle exception
    }

    @Override
    public void receive_reply(ClientRequestInfo ri) {
        // Process reply
    }
}
```

## Security Mechanism Selector

```java
public class SecurityMechanismSelector {

    public static final String GSSUP = "GSSUP";
    public static final String SSL = "SSL";
    public static final String USERNAME_PASSWORD = "USERNAME_PASSWORD";

    public String selectMechanism(IOR ior) {
        // Extract CSIv2 components from IOR
        TaggedComponent[] components = extractTaggedComponents(ior);

        for (TaggedComponent comp : components) {
            if (comp.tag == CSI_SEC_MECH_LIST.value) {
                // Get supported mechanisms
                return selectFromList(comp.components);
            }
        }

        // Default to GSSUP
        return GSSUP;
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `orb` | IIOP ORB |
| `ejb-internal-api` | EJB internal API |
| `security-ee` | Core security |

## Notes

- **CSIv2** - Common Secure Inter-ORB Protocol
- **GSSUP** - GSS Username Password (IIOP authentication)
- **IIOP** - Internet Inter-ORB Protocol
- **EJB Remoting** - Remote EJB calls over IIOP
- **Security Context** - Propagated across IIOP calls
