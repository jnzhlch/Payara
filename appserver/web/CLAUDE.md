# CLAUDE.md - Web Container

This file provides guidance for working with the `appserver/web` module - Payara's web container implementation.

## Module Overview

The web module provides Payara Server's web container based on Apache Catalina (Tomcat) with Jakarta EE integration. It handles Servlet, JSP, JSF, WebSocket, and CDI for web applications.

**Key Components:**
- **web-core** - Apache Catalina-based web container
- **web-glue** - Web container deployment and integration
- **weld-integration** - CDI (Weld) integration
- **war-util** - WAR deployment utilities
- **web-naming** - JNDI naming for web components
- **web-ha** - High availability for web sessions

## Build Commands

```bash
# Build entire web module
mvn -DskipTests clean package -f appserver/web/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/web/web-core/pom.xml
mvn -DskipTests clean package -f appserver/web/web-glue/pom.xml
mvn -DskipTests clean package -f appserver/web/weld-integration/pom.xml
```

## Architecture

### Module Structure

```
web/
├── web-core/              # Apache Catalina web container
├── web-glue/              # Deployment glue code
├── weld-integration/      # CDI/Weld integration
├── war-util/              # WAR utilities
├── web-naming/            # JNDI for web
├── web-ha/                # High availability
├── web-sse/               # Server-Sent Events
├── gf-web-connector/      # GlassFish web connector
├── gf-weld-connector/     # GlassFish Weld connector
├── jsf-connector/         # JSF (Mojarra) connector
├── jstl-connector/        # JSTL connector
├── jersey-mvc-connector/  # Jersey MVC connector
├── jspcaching-connector/  # JSP caching connector
└── admin/                 # Admin CLI commands
```

## Web Container (web-core)

### Apache Catalina Integration

web-core is based on Apache Catalina (Tomcat's servlet container) with GlassFish/Payara enhancements:

```
Apache Catalina + Payara Enhancements
       │
       ├─→ Servlet 6.0 support
       ├─→ Jakarta EE integration
       ├─→ JNDI integration
       ├─→ Security integration
       └─→ Session management (including HA)
```

### Key Components

```java
// Standard Catalina interfaces
org.apache.catalina.Container      // Container interface
org.apache.catalina.Context        // Web application context
org.apache.catalina.Wrapper        // Servlet wrapper
org.apache.catalina.Valve          // Valve (interceptor-like) pattern
org.apache.catalina.Lifecycle      // Lifecycle management
org.apache.catalina.connector.*    // Request/Response handling
```

### Request Processing

```
HTTP Request
       │
       ▼
[Connector] (Grizzly)
       │
       ▼
[CoyoteAdapter]
       │
       ▼
[Engine] → [Host] → [Context]
       │
       ▼
[Pipeline] + [Valves]
       │
       ├─→ Access Log Valve
       ├─→ SSO Valve
       ├─→ Security Valve
       └─→ Custom Valves
       │
       ▼
[Filter Chain]
       │
       ▼
[Servlet]
```

## Web Glue (web-glue)

### WebDeployer

Deploys web applications (WAR files):

```java
@Service
public class WebDeployer extends AbstractDeployer<WebContainer, WebApplication> {

    @Override
    public boolean prepare(DeploymentContext context) {
        // Parse web.xml, web-fragment.xml
        // Process annotations (@WebServlet, @WebFilter, etc.)
        // Create deployment descriptors
    }

    @Override
    public WebApplication load(WebContainer container, DeploymentContext context) {
        // Create Context
        // Configure servlets, filters, listeners
        // Start the web application
    }
}
```

### WebApplication

Represents a deployed web application:

```java
public class WebApplication {

    private WebModuleConfig config;
    private StandardContext context;
    private DeploymentContext deploymentContext;

    // Web application lifecycle
    public void start() throws Exception {
        context.start();
    }

    public void stop() throws Exception {
        context.stop();
    }
}
```

## Weld Integration (weld-integration)

### WeldDeployer

Deploys CDI (Weld) for web applications:

```java
@Service
public class WeldDeployer extends AbstractDeployer<WeldContainer, WeldApplicationContainer> {

    @Override
    public boolean prepare(DeploymentContext context) {
        // Scan for beans.xml
        // Create BeanDeploymentArchive
        // Register CDI extensions
    }

    @Override
    public WeldApplicationContainer load(WeldContainer container, DeploymentContext context) {
        // Initialize Weld
        // Start CDI container
        // Register BeanManager
    }
}
```

### CDI Extensions

```java
// HK2 Integration Extension
@Extension
public class HK2IntegrationExtension implements Extension {

    public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        // Register HK2 integration
    }

    public void processAnnotatedType(@Observes ProcessAnnotatedType<?> event) {
        // Integrate HK2 services with CDI
    }
}

// Transactional Extension
@Extension
public class TransactionalExtension implements Extension {

    public void processAnnotatedType(@Observes ProcessAnnotatedType<?> event) {
        // Process @Transactional annotation
    }
}

// TransactionScoped Extension
@Extension
public class TransactionScopedContextExtension implements Extension {

    public void afterBeanDiscovery(@Observes AfterBeanDiscovery event) {
        // Register transaction-scoped context
    }
}
```

### TransactionScoped CDI

```java
// Transaction-scoped bean
@TransactionScoped
public class TransactionalCache {

    private Map<String, Object> cache = new HashMap<>();

    // Cache lives only for the duration of a transaction
}

// Usage
@Inject
private TransactionalCache cache;
```

### @Transactional Interceptors

```java
@Transactional(Transactional.TxType.REQUIRED)
public void requiredMethod() {
    // Runs in existing transaction or starts new one
}

@Transactional(Transactional.TxType.REQUIRES_NEW)
public void requiresNewMethod() {
    // Always starts a new transaction
}

@Transactional(Transactional.TxType.MANDATORY)
public void mandatoryMethod() {
    // Must be called within existing transaction
}

@Transactional(Transactional.TxType.NOT_SUPPORTED)
public void notSupportedMethod() {
    // Runs without transaction
}
```

## Web HA (web-ha)

### Distributed Session Management

Web HA provides session replication across cluster:

```java
// Replicated session
public class ReplicatedSession extends StandardSession {

    @Override
    public void setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        // Trigger replication
        replicate();
    }

    private void replicate() {
        // Replicate to other cluster members
        DistributedCache.cache.put(sessionId, this);
    }
}
```

### Hazelcast Session Replication

```
Request → Instance A
       │
       ├─→ Create/Update Session
       │
       ▼
[Hazelcast Cache]
       │
       ├─→ Replicate to Instance B
       ├─→ Replicate to Instance C
       └─→ Replicate to Instance D
```

## Servlet Configuration

### web.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                             https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">

    <servlet>
        <servlet-name>MyServlet</servlet-name>
        <servlet-class>com.example.MyServlet</servlet-class>
        <load-on-startup>1</load-on-startup>
    </servlet>

    <servlet-mapping>
        <servlet-name>MyServlet</servlet-name>
        <url-pattern>/api/*</url-pattern>
    </servlet-mapping>

    <filter>
        <filter-name>CORSFilter</filter-name>
        <filter-class>com.example.CORSFilter</filter-class>
    </filter>

    <filter-mapping>
        <filter-name>CORSFilter</filter-name>
        <url-pattern>/*</url-pattern>
    </filter-mapping>

    <listener>
        <listener-class>com.example.MyServletContextListener</listener-class>
    </listener>

    <session-config>
        <session-timeout>30</session-timeout>
        <cookie-config>
            <http-only>true</http-only>
            <secure>true</secure>
            <same-site>None</same-site>
        </cookie-config>
    </session-config>
</web-app>
```

### Programmatic Servlet Registration

```java
@WebServlet("/api/hello")
public class HelloServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("text/plain");
        resp.getWriter().write("Hello, World!");
    }
}

// WebListener
@WebListener
public class MyAppInitializer implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Register servlets programmatically
        ServletRegistration.Dynamic servlet =
            sce.getServletContext().addServlet("dynamicServlet", DynamicServlet.class);
        servlet.addMapping("/dynamic/*");

        // Register filters
        FilterRegistration.Dynamic filter =
            sce.getServletContext().addFilter("dynamicFilter", DynamicFilter.class);
        filter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
    }
}
```

## Async Servlet Support

```java
@WebServlet(urlPatterns = "/async", asyncSupported = true)
public class AsyncServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        AsyncContext asyncContext = req.startAsync();

        // Execute in separate thread
        ExecutorService executor = Executors.newCachedThreadPool();
        executor.submit(() -> {
            try {
                // Long-running operation
                Thread.sleep(5000);

                // Write response
                HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
                response.setContentType("text/plain");
                response.getWriter().write("Async response");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                asyncContext.complete();
            }
        });
    }
}
```

## WebSocket Support

```java
@ServerEndpoint("/ws/chat")
public class ChatEndpoint {

    private static Set<Session> sessions = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
    }

    @OnMessage
    public void onMessage(String message, Session sender) {
        // Broadcast to all sessions
        sessions.forEach(session -> {
            if (session.isOpen()) {
                session.getAsyncRemote().sendText(message);
            }
        });
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
    }

    @OnError
    public void onError(Throwable error) {
        error.printStackTrace();
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.servlet-api` | Servlet 6.0 API |
| `wasp` | JSP implementation |
| `expressly` | EL implementation |
| `weld` | CDI implementation |
| `mojarra` | JSF implementation |
| `grizzly` | HTTP connector |
| `hazelcast` | Distributed caching (web-ha) |
| `dol` | Deployment descriptors |
| `deployment-javaee-core` | Java EE deployment |

## Notes

- **Servlet 6.0** - Jakarta EE 10 servlet specification
- **Apache Catalina** - Based on Tomcat's servlet container
- **CDI Integration** - Full Weld (CDI) integration
- **Session Replication** - Hazelcast-based distributed sessions
- **Async Servlet** - Non-blocking request processing
- **WebSocket** - JSR 356 WebSocket support
- **JSP** - Jakarta Server Pages support
- **JSF** - Jakarta Faces support via Mojarra
