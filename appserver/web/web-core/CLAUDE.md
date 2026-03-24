# CLAUDE.md - Web Core

This file provides guidance for working with the `appserver/web/web-core` module - Apache Catalina-based web container implementation.

## Module Overview

The web-core module provides Payara Server's web container implementation based on Apache Catalina (from Apache Tomcat) with Payara-specific enhancements and Jakarta EE integration.

**Key Components:**
- **Apache Catalina** - Core servlet container
- **Connector Integration** - Grizzly HTTP connector integration
- **Security Integration** - Payara security integration
- **Session Management** - Standard and distributed sessions

## Build Commands

```bash
# Build web-core module
mvn -DskipTests clean package -f appserver/web/web-core/pom.xml
```

## Module Contents

```
web-core/src/main/java/org/apache/catalina/
├── connector/      # Request/Response handling
│   ├── Connector.java
│   ├── CoyoteAdapter.java
│   ├── Request.java
│   ├── Response.java
│   ├── AsyncContextImpl.java
│   └── ...
├── core/           # Core container implementation
│   ├── StandardContext.java
│   ├── StandardWrapper.java
│   ├── ApplicationFilterChain.java
│   ├── ApplicationContext.java
│   └── ...
├── authenticator/  # Authentication mechanisms
│   ├── BasicAuthenticator.java
│   ├── DigestAuthenticator.java
│   ├── FormAuthenticator.java
│   ├── SSLAuthenticator.java
│   └── SingleSignOn.java
├── loader/         # Classloader for web applications
├── manager/        # Session manager
├── session/        # Session implementation
└── valves/         # Valves (interceptor pattern)
```

## Catalina Architecture

### Container Hierarchy

```
Server (Catalina)
    │
    ▼
Service (StandardService)
    │
    ├─→ Connector (HTTP, AJP)
    │
    ▼
Engine (StandardEngine)
    │
    ▼
Host (StandardHost - virtual host)
    │
    ▼
Context (StandardContext - web application)
    │
    ▼
Wrapper (StandardWrapper - servlet wrapper)
```

### Lifecycle Management

```java
// Catalina lifecycle interface
public interface Lifecycle {

    String INIT = "init";
    String START = "start";
    String STOP = "stop";
    String DESTROY = "destroy";

    void addLifecycleListener(LifecycleListener listener);
    void removeLifecycleListener(LifecycleListener listener);
    void init() throws LifecycleException;
    void start() throws LifecycleException;
    void stop() throws LifecycleException;
    void destroy() throws LifecycleException;
    LifecycleState getState();
}
```

## Request Processing

### CoyoteAdapter

Adapts Coyote (Grizzly) requests to Catalina:

```java
public class CoyoteAdapter implements Adapter {

    @Override
    public void service(org.glassfish.grizzly.http.server.Request req,
                       org.glassfish.grizzly.http.server.Response res) {
        // Create Catalina request/response
        Request request = createRequest();
        Response response = createResponse();

        // Set request/response
        request.setCoyoteRequest(req);
        response.setCoyoteResponse(res);

        // Process through connector
        connector.getService().getContainer().getPipeline()
            .getFirst().invoke(request, response);
    }
}
```

### ApplicationFilterChain

Processes servlet filters:

```java
public class ApplicationFilterChain implements FilterChain {

    private ArrayList<FilterConfig> filters = new ArrayList<>();
    private Servlet servlet;
    private int pos = 0;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response) {
        if (pos < filters.size()) {
            ApplicationFilterConfig filterConfig = filters.get(pos++);
            Filter filter = filterConfig.getFilter();
            filter.doFilter(request, response, this);
        } else {
            // All filters done, invoke servlet
            servlet.service(request, response);
        }
    }
}
```

### Valve Pipeline

Intercepts requests (interceptor pattern):

```
Request
    │
    ▼
[StandardHostValve] - Always first
    │
    ▼
[Access Log Valve] - Optional
    │
    ▼
[SSO Valve] - Optional
    │
    ▼
[Custom Valves] - Optional
    │
    ▼
[StandardContextValve] - Context-specific
    │
    ▼
[StandardWrapperValve] - Servlet invocation
```

## StandardContext

Represents a web application context:

```java
public class StandardContext extends ContainerBase implements Context {

    // Servlet configuration
    public Wrapper createWrapper() {
        StandardWrapper wrapper = new StandardWrapper();
        wrapper.setServlet(this);
        return wrapper;
    }

    // JNDI resources
    public Object lookup(String name) {
        return getNamingContext().lookup(name);
    }

    // Session management
    public Manager getManager() {
        if (manager == null) {
            manager = new StandardManager();
        }
        return manager;
    }

    // Classloader
    public ClassLoader getLoader() {
        return loader.getClassLoader();
    }

    // Lifecycle
    @Override
    protected void startInternal() throws LifecycleException {
        // Set up resources
        // Set up JNDI
        // Set up session manager
        // Process servlets, filters, listeners
        // Start application
    }
}
```

## StandardWrapper

Wraps a servlet:

```java
public class StandardWrapper extends ContainerBase implements Wrapper {

    private Servlet instance;
    private String servletClass;

    @Override
    public void load() throws ServletException {
        // Create servlet instance
        instance = (Servlet) getInstanceManager().newInstance(servletClass);

        // Initialize servlet
        instance.init(new StandardWrapperFacade(this));
    }

    @Override
    public void allocate() throws ServletException {
        // Get or create servlet instance
        if (instance == null) {
            load();
        }
        return instance;
    }

    public Servlet service(Request request, Response response) {
        Servlet servlet = allocate();
        servlet.service(request, response);
    }
}
```

## Authentication

### Authenticator Base

```java
public abstract class AuthenticatorBase extends Authenticator {

    @Override
    public boolean authenticate(Request request, Response response) {
        // Check for authenticated principal
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            return true;
        }

        // Perform authentication
        return authenticateInternal(request, response);
    }

    protected abstract boolean authenticateInternal(Request request, Response response);

    @Override
    public void login(String username, String password, Request request) {
        // Authenticate and register principal
        Principal principal = context.getRealm().authenticate(username, password);
        if (principal != null) {
            request.setUserPrincipal(principal);
            request.setAuthType(authMethod);
        }
    }
}
```

### Form Authentication

```java
public class FormAuthenticator extends AuthenticatorBase {

    @Override
    protected boolean authenticateInternal(Request request, Response response) {
        // Check for j_security_check
        if (request.getRequestURI().endsWith("/j_security_check")) {
            String username = request.getParameter("j_username");
            String password = request.getParameter("j_password");

            Principal principal = context.getRealm().authenticate(username, password);
            if (principal != null) {
                request.setUserPrincipal(principal);
                request.setAuthType("FORM");

                // Save original request
                Session session = request.getSession(true);
                session.setNote("FORM_ORIGINAL_REQUEST", savedRequest);

                return true;
            }
        }
        return false;
    }
}
```

### Single Sign-On (SSO)

```java
public class SingleSignOn extends ValveBase {

    private Map<String, SingleSignOnEntry> cache = new ConcurrentHashMap<>();

    @Override
    public void invoke(Request request, Response response) {
        // Check for SSO cookie
        Cookie ssoCookie = findSSOCookie(request);
        if (ssoCookie != null) {
            String ssoId = ssoCookie.getValue();
            SingleSignOnEntry entry = cache.get(ssoId);
            if (entry != null) {
                // Reuse authentication
                request.setUserPrincipal(entry.getPrincipal());
                request.setAuthType(entry.getAuthType());
                request.setNote("SSO_ENTRY", entry);
            }
        }

        // Continue pipeline
        getNext().invoke(request, response);
    }

    public void register(String ssoId, Principal principal, String authType) {
        SingleSignOnEntry entry = new SingleSignOnEntry(principal, authType);
        cache.put(ssoId, entry);
    }
}
```

## Async Servlet Support

```java
public class AsyncContextImpl implements AsyncContext {

    private final Request request;
    private final Response response;
    private volatile boolean completed = false;

    @Override
    public void complete() {
        if (!completed) {
            completed = true;
            // Finish response
            response.finishResponse();
        }
    }

    @Override
    public void start(Runnable run) {
        // Execute in separate thread
        ExecutorService executor = context.getExecutor();
        executor.execute(() -> {
            try {
                run.run();
            } catch (Throwable t) {
                onThrowable(t);
            }
        });
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.servlet-api` | Servlet API |
| `grizzly` | HTTP connector |
| `glassfish-api` | GlassFish APIs |
| `internal-api` | Internal APIs |
| `transaction-internal-api` | Transaction integration |

## Notes

- **Apache Catalina** - Based on Tomcat's servlet container
- **Servlet 6.0** - Jakarta EE 10 servlet spec
- **Valve Pattern** - Interceptor-like request processing
- **Authentication** - Basic, Digest, Form, SSL
- **Single Sign-On** - SSO across web applications
- **Async Servlet** - Non-blocking I/O support
