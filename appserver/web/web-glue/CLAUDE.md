# CLAUDE.md - Web Glue

This file provides guidance for working with the `appserver/web/web-glue` module - Web container deployment glue code.

## Module Overview

The web-glue module provides the deployment integration between the web container (web-core) and the Payara deployment infrastructure. It handles WAR file deployment, web.xml processing, and application lifecycle management.

**Key Components:**
- **WebDeployer** - Deploys web applications
- **WebContainer** - Container for web applications
- **WebApplication** - Represents a deployed web app
- **Sniffer** - Detects web application type

## Build Commands

```bash
# Build web-glue module
mvn -DskipTests clean package -f appserver/web/web-glue/pom.xml
```

## Module Contents

```
web-glue/
└── src/main/java/org/glassfish/web/
    ├── deployment/
    │   ├── WebDeployer.java
    │   ├── WebContainer.java
    │   ├── WebApplication.java
    │   └── WebModuleConfiguration.java
    ├── admin/
    │   └── ...
    └── sniffer/
        └── WebSniffer.java
```

## WebDeployer

### Deployment Process

```java
@Service
public class WebDeployer extends AbstractDeployer<WebContainer, WebApplication> {

    @Override
    public boolean prepare(DeploymentContext context) {
        // Check if this is a web application
        if (!isWebArchive(context.getSource())) {
            return false;
        }

        // Parse deployment descriptors
        parseWebXml(context);
        parseWebFragments(context);
        processAnnotations(context);

        // Configure the application
        configureWebApp(context);

        return true;
    }

    @Override
    public WebApplication load(WebContainer container, DeploymentContext context) {
        // Create the web application
        WebApplication app = new WebApplication(context);

        // Configure servlets, filters, listeners
        configureComponents(app, context);

        // Configure JNDI resources
        configureResources(app, context);

        // Start the application
        app.start();

        return app;
    }

    @Override
    public void unload(WebApplication app, DeploymentContext context) {
        // Stop the web application
        app.stop();
    }

    @Override
    public void clean(DeploymentContext context) {
        // Clean up resources
    }
}
```

### Web XML Parsing

```java
private void parseWebXml(DeploymentContext context) {
    StandardRoot root = new StandardRoot();
    context.getScratchDir("ejb", root.getScratchDir());

    // Parse web.xml
    WebXmlParser parser = new WebXmlParser(context);
    WebXml webXml = parser.parseWebXml();

    // Parse web-fragment.xml from JARs
    List<WebFragmentDescriptor> fragments = parser.parseWebFragments();

    // Merge web.xml with web-fragment.xml files
    WebXml merged = mergeWebXml(webXml, fragments);

    // Store in deployment context
    context.addTransientAppMetaData(WebXml.class.getName(), merged);
}
```

### Annotation Processing

```java
private void processAnnotations(DeploymentContext context) {
    // Scan for annotations
    AnnotationScanner scanner = new AnnotationScanner(context);

    // @WebServlet
    List<ServletDescriptor> servlets = scanner.scanServletAnnotations();

    // @WebFilter
    List<FilterDescriptor> filters = scanner.scanFilterAnnotations();

    // @WebListener
    List<ListenerDescriptor> listeners = scanner.scanListenerAnnotations();

    // Merge with XML descriptors
    mergeDescriptors(context, servlets, filters, listeners);
}
```

## WebContainer

```java
@Service
public class WebContainer implements Container {

    private Engine engine;

    @Override
    public Class<? extends Deployer> getDeployer() {
        return WebDeployer.class;
    }

    public Engine getEngine() {
        return engine;
    }

    public Host getDefaultHost() {
        return engine.findChild(engine.getDefaultHost());
    }
}
```

## WebApplication

```java
public class WebApplication {

    private final StandardContext context;
    private final DeploymentContext deploymentContext;

    public WebApplication(DeploymentContext dc) {
        this.deploymentContext = dc;

        // Create the context
        StandardContext context = new StandardContext();
        context.setName(getContextName(dc));
        context.setPath(getContextPath(dc));
        context.setDocBase(getDocBase(dc));
        context.setRealm(new RealmAdapter(dc));

        this.context = context;
    }

    public void start() throws LifecycleException {
        // Configure classloader
        configureClassLoader();

        // Configure JNDI
        configureJNDI();

        // Configure session manager
        configureSessionManager();

        // Start the context
        context.start();
    }

    public void stop() throws LifecycleException {
        context.stop();
    }

    public StandardContext getContext() {
        return context;
    }
}
```

## WebModuleConfiguration

```java
public class WebModuleConfiguration {

    private String contextPath;
    private String location;
    private Map<String, String> servletMappings;
    private Map<String, String> filterMappings;
    private List<String> listeners;

    // Configure from web.xml or annotations
    public void configure(WebXml webXml) {
        this.contextPath = webXml.getContextPath();
        this.location = webXml.getLocation();

        // Servlet mappings
        for (ServletMapping mapping : webXml.getServletMappings()) {
            servletMappings.put(mapping.getServletName(), mapping.getUrlPattern());
        }

        // Filter mappings
        for (FilterMapping mapping : webXml.getFilterMappings()) {
            filterMappings.put(mapping.getFilterName(), mapping.getUrlPattern());
        }

        // Listeners
        for (Listener listener : webXml.getListeners()) {
            listeners.add(listener.getClassName());
        }
    }
}
```

## WebSniffer

```java
@Service
@Sorted
public class WebSniffer implements Sniffer {

    @Override
    public String[] getContainersNames() {
        return new String[] { WebContainer.class.getName() };
    }

    @Override
    public boolean handles(DeploymentContext context) {
        // Check if this is a WAR file
        ArchiveType type = context.getArchiveHandler().getArchiveType();
        return DOLUtils.warType().equals(type);
    }

    @Override
    public String[] getAnnotationNames() {
        return new String[] {
            "jakarta.servlet.annotation.WebServlet",
            "jakarta.servlet.annotation.WebFilter",
            "jakarta.servlet.annotation.WebListener",
            "jakarta.servlet.annotation.MultipartConfig",
            "jakarta.websocket.server.ServerEndpoint"
        };
    }
}
```

## Security Integration

### RealmAdapter

```java
public class RealmAdapter extends RealmBase {

    private final SecurityContext securityContext;

    @Override
    protected Principal getPrincipal(String username) {
        // Get principal from security context
        return securityContext.getPrincipal(username);
    }

    @Override
    protected String getPassword(String username) {
        // Get password from security context
        return securityContext.getPassword(username);
    }

    @Override
    public Principal authenticate(String username, String password) {
        // Authenticate using Payara security
        return securityContext.login(username, password);
    }

    @Override
    public boolean hasRole(Principal principal, String role) {
        // Check role using Payara security
        return securityContext.isCallerInRole(principal, role);
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.servlet-api` | Servlet API |
| `web-core` | Catalina web container |
| `war-util` | WAR utilities |
| `web-naming` | JNDI for web |
| `deployment-javaee-core` | Java EE deployment |
| `dol` | Deployment descriptors |
| `security` | Security integration |
| `gf-web-connector` | Web connector |
| `wasp` | JSP implementation |
| `expressly` | EL implementation |

## Notes

- **Deployment Integration** - Bridges web container and Payara deployment
- **Web XML Processing** - Parses web.xml and web-fragment.xml
- **Annotation Scanning** - Processes @WebServlet, @WebFilter, @WebListener
- **JNDI Integration** - JNDI resources for web applications
- **Security Integration** - Integrates with Payara security
- **Session Management** - Configures session managers (including HA)
