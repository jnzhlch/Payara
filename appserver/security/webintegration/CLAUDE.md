# CLAUDE.md - Web Integration

This file provides guidance for working with the `appserver/security/webintegration` module - Web container security integration.

## Module Overview

The webintegration module provides the integration between the security core and the web container (Apache Catalina), handling web authentication and authorization.

**Key Components:**
- **RealmAdapter** - Adapts security realms to Catalina Realm interface
- **AuthenticatorProxy** - Proxies Catalina authenticators
- **WebProgrammaticLoginImpl** - Programmatic login for web
- **SSLSocketFactory** - SSL socket factory for HTTPS

## Build Commands

```bash
# Build webintegration module
mvn -DskipTests clean package -f appserver/security/webintegration/pom.xml
```

## Module Contents

```
webintegration/src/main/java/com/sun/web/security/
├── RealmAdapter.java
├── AuthenticatorProxy.java
├── WebProgrammaticLoginImpl.java
├── SSLSocketFactory.java
├── HttpRequestWrapper.java
├── HttpResponseWrapper.java
└── LoginProbeProvider.java
```

## RealmAdapter

```java
public class RealmAdapter extends RealmBase {

    private com.sun.enterprise.security.auth.realm.Realm loginRealm;

    public RealmAdapter(com.sun.enterprise.security.auth.realm.Realm realm) {
        this.loginRealm = realm;
    }

    @Override
    public Principal authenticate(String username, String password) {
        return loginRealm.authenticate(username, password);
    }

    @Override
    public Principal authenticate(String username, String digest, String nonce,
                                String nc, String cnonce, String qop, String realm) {
        return loginRealm.authenticate(username, digest, nonce, nc, cnonce, qop, realm);
    }

    @Override
    public boolean hasRole(Principal principal, String role) {
        // Check if principal has role
        return loginRealm.hasRole(principal, role);
    }

    @Override
    public void backgroundProcess() {
        // Periodic tasks
    }
}
```

## Authenticator Proxy

```java
public class AuthenticatorProxy extends AuthenticatorBase {

    private Authenticator authenticator;

    @Override
    public boolean authenticate(Request request, Response response) {
        // Check if already authenticated
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            return true;
        }

        // Delegate to wrapped authenticator
        return authenticator.authenticate(request, response);
    }

    @Override
    protected String getAuthMethod() {
        return authenticator.getAuthMethod();
    }
}
```

## Web Programmatic Login

```java
public class WebProgrammaticLoginImpl implements ProgrammaticLogin {

    @Override
    public boolean login(String username, String password) {
        HttpRequestWrapper request = getCurrentRequest();

        // Authenticate with realm
        Realm realm = getRealm();
        Principal principal = realm.authenticate(username, password);

        if (principal != null) {
            // Set principal in request
            request.setUserPrincipal(principal);
            request.setAuthType("BASIC");

            // Register in session
            request.getSession(true).setAttribute("j_authenticated", principal);

            return true;
        }

        return false;
    }

    @Override
    public boolean logout() {
        HttpRequestWrapper request = getCurrentRequest();

        // Clear principal
        request.setUserPrincipal(null);
        request.setAuthType(null);

        // Clear from session
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute("j_authenticated");
        }

        return true;
    }
}
```

## SSL Socket Factory

```java
public class SSLSocketFactory extends SSLSocketFactory {

    private SSLContext sslContext;

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return sslContext.getSocketFactory().createSocket(host, port);
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose)
            throws IOException {
        return sslContext.getSocketFactory().createSocket(s, host, port, autoClose);
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.servlet-api` | Servlet API |
| `security-ee` | Core security classes |
| `web-core` | Catalina Realm interface |
| `war-util` | WAR utilities |

## Notes

- **Catalina Integration** - Adapts security to Apache Catalina
- **Realm Adapter** - Bridges Payara realms and Catalina realms
- **Programmatic Login** - Login/logout for web apps
- **SSL** - HTTPS support for secure connections
