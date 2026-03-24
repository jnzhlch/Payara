# CLAUDE.md - MicroProfile Health Check

This file provides guidance for working with the `microprofile/healthcheck` module - MicroProfile Health Check API implementation.

## Module Overview

The microprofile/healthcheck module provides an implementation of the MicroProfile Health Check API for Payara Server. It enables applications to expose health check endpoints that report service availability and readiness.

**Key Features:**
- **Health Check Endpoints** - `/health`, `/health/live`, `/health/ready`, `/health/started`
- **Liveness/Readiness/Startup** - Three types of health checks per MicroProfile 3.0+
- **CDI Integration** - Auto-registers beans annotated with @Liveness, @Readiness, @Startup
- **JSON Response** - Standard JSON health check responses
- **Security** - Optional security configuration
- **Payara Integration** - Integrates with Payara HealthCheckService

## Build Commands

```bash
# Build microprofile/healthcheck module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/healthcheck/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/healthcheck/pom.xml
```

## Architecture

### Health Check Registration Flow

```
Application Deployment
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│          HealthCDIExtension (CDI Extension)                     │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. processBean(@Observes ProcessBean)                   │ │
│  │     - Find beans with @Liveness, @Readiness, @Startup    │ │
│  │     - Store health check beans                           │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. applicationInitialized(@Observes @Initialized)       │ │
│  │     - Get HealthCheckService                            │ │
│  │     - Register each health check bean                   │ │
│  │     - Register application class loader                 │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│              HealthCheckService                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Stores health checks by type:                           │ │
│  │  - Map<String, Set<HealthCheck>> readiness               │ │
│  │  - Map<String, Set<HealthCheck>> liveness                │ │
│  │  - Map<String, Set<HealthCheck>> startup                 │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│           HealthCheckServlet (HTTP Endpoint)                   │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Handles requests to:                                    │ │
│  │  - GET /health (all checks)                              │ │
│  │  - GET /health/live (liveness only)                      │ │
│  │  - GET /health/ready (readiness only)                    │ │
│  │  - GET /health/started (startup only)                    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### Health Check Execution Flow

```
HTTP Request to /health
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│            HealthCheckServlet.processRequest()                 │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Get HealthCheckService                               │ │
│  │  2. Check if enabled                                     │ │
│  │  3. Determine HealthCheckType from path                  │ │
│  │  4. Call performHealthChecks(response, type)             │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│           HealthCheckService.performHealthChecks()              │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Get health checks by type                            │ │
│  │     getCollectiveHealthChecks(type)                      │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Execute each health check                            │ │
│  │     for each HealthCheck in healthChecks:                │ │
│  │       try {                                              │ │
│  │         responses.add(healthCheck.call())                │ │
│  │       } catch (Exception e) {                            │ │
│  │         Set HTTP 500 on error                            │ │
│  │       }                                                  │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Set HTTP status based on results                     │ │
│  │     - 200 if all checks are UP                           │ │
│  │     - 503 if any check is DOWN                          │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  4. Construct JSON response                             │ │
│  │     constructResponse(response, responses, type)         │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### Health Check Response Format

```json
{
  "status": "UP",
  "checks": [
    {
      "name": "database",
      "status": "UP",
      "data": {
        "database": "MySQL",
        "latency": "5ms"
      }
    },
    {
      "name": "disk-space",
      "status": "UP",
      "data": {
        "free": "10GB",
        "threshold": "1GB"
      }
    }
  ]
}
```

## Core Components

### HealthCheckService

```java
@Service(name = "healthcheck-service")
@RunLevel(StartupRunLevel.VAL)
public class HealthCheckService implements EventListener, ConfigListener {

    // Health check storage by type
    private final Map<String, Set<HealthCheck>> readiness = new ConcurrentHashMap<>();
    private final Map<String, Set<HealthCheck>> liveness = new ConcurrentHashMap<>();
    private final Map<String, Set<HealthCheck>> startup = new ConcurrentHashMap<>();

    // Application class loader storage
    private final Map<String, ClassLoader> applicationClassLoaders = new ConcurrentHashMap<>();

    public void registerHealthCheck(String healthCheckName,
                                   HealthCheck healthCheck,
                                   HealthCheckType type) {
        Map<String, Set<HealthCheck>> healthChecks = getHealthChecks(type);

        if (!healthChecks.containsKey(healthCheckName)) {
            synchronized (this) {
                if (!healthChecks.containsKey(healthCheckName)) {
                    healthChecks.put(healthCheckName,
                        ConcurrentHashMap.newKeySet());
                }
            }
        }
        healthChecks.get(healthCheckName).add(healthCheck);
    }

    public void performHealthChecks(HttpServletResponse response,
                                   HealthCheckType type,
                                   String enablePrettyPrint) throws IOException {
        Set<HealthCheckResponse> healthCheckResponses = new HashSet<>();

        for (Entry<String, Set<HealthCheck>> entry :
                getCollectiveHealthChecks(type).entrySet()) {
            for (HealthCheck healthCheck : entry.getValue()) {
                try {
                    healthCheckResponses.add(healthCheck.call());
                } catch (IllegalStateException ise) {
                    // Execute in application context if needed
                    healthCheckResponses.add(
                        performHealthCheckInApplicationContext(
                            entry.getKey(), healthCheck));
                }
            }
        }

        constructResponse(response, healthCheckResponses, type, enablePrettyPrint);
    }

    private Map<String, Set<HealthCheck>> getCollectiveHealthChecks(
            HealthCheckType type) {
        if (type == READINESS) {
            return readiness;
        } else if (type == LIVENESS) {
            return liveness;
        } else if (type == STARTUP) {
            return startup;
        } else {
            // Unknown type - merge all health checks
            return mergeAllHealthChecks();
        }
    }
}
```

**Purpose:** Central service for managing and executing health checks.

### HealthCDIExtension

```java
public class HealthCDIExtension implements Extension {

    private final HealthCheckService healthService;
    private final String appName;
    private final Set<Bean<?>> healthCheckBeans;

    void processBean(@Observes ProcessBean<?> event) {
        Bean<?> bean = event.getBean();
        Set<Annotation> annotations = bean.getQualifiers();

        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (Readiness.class.equals(annotationType)
                    || Liveness.class.equals(annotationType)
                    || Startup.class.equals(annotationType)) {
                this.healthCheckBeans.add(event.getBean());
            }
        }
    }

    void applicationInitialized(@Observes @Initialized(ApplicationScoped.class) Object init,
                                BeanManager beanManager) {
        for (Bean<?> bean : healthCheckBeans) {
            HealthCheck healthCheck = (HealthCheck) beanManager.getReference(
                bean, HealthCheck.class,
                beanManager.createCreationalContext(bean)
            );

            healthService.registerHealthCheck(
                appName, healthCheck,
                HealthCheckType.fromQualifiers(bean.getQualifiers())
            );

            healthService.registerClassLoader(
                appName, healthCheck.getClass().getClassLoader()
            );
        }
    }
}
```

**Purpose:** CDI extension that auto-registers health check beans.

### HealthCheckServlet

```java
public class HealthCheckServlet extends HttpServlet {

    protected void processRequest(HttpServletRequest request,
                                HttpServletResponse response)
            throws ServletException, IOException {

        HealthCheckService healthCheckService =
            Globals.getDefaultBaseServiceLocator()
                .getService(HealthCheckService.class);

        if (!healthCheckService.isEnabled()) {
            response.sendError(SC_FORBIDDEN,
                "MicroProfile Health Check Service is disabled");
            return;
        }

        healthCheckService.performHealthChecks(
            response,
            HealthCheckType.fromPath(request.getPathInfo()),
            request.getParameter("pretty")
        );
    }
}
```

**Purpose:** HTTP servlet that exposes health check endpoints.

## Usage Examples

### Readiness Health Check

```java
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import jakarta.enterprise.context.ApplicationScoped;

@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    @Inject
    private DataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(1)) {
                return HealthCheckResponse.builder()
                    .name("database")
                    .up()
                    .withData("database", "MySQL")
                    .withData("latency", "5ms")
                    .build();
            }
        } catch (SQLException e) {
            return HealthCheckResponse.builder()
                .name("database")
                .down()
                .withData("error", e.getMessage())
                .build();
        }
    }
}
```

### Liveness Health Check

```java
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import jakarta.enterprise.context.ApplicationScoped;

@Liveness
@ApplicationScoped
public class MemoryHealthCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;
        double usage = (double) usedMemory / maxMemory;

        if (usage > 0.9) {
            return HealthCheckResponse.builder()
                .name("memory")
                .down()
                .withData("usage", String.format("%.2f%%", usage * 100))
                .withData("used", String.valueOf(usedMemory))
                .withData("max", String.valueOf(maxMemory))
                .build();
        }

        return HealthCheckResponse.builder()
            .name("memory")
            .up()
            .withData("usage", String.format("%.2f%%", usage * 100))
            .build();
    }
}
```

### Startup Health Check

```java
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Startup;
import jakarta.enterprise.context.ApplicationScoped;

@Startup
@ApplicationScoped
public class CacheWarmupHealthCheck implements HealthCheck {

    private volatile boolean warmedUp = false;

    @PostConstruct
    public void init() {
        // Async cache warmup
        CompletableFuture.runAsync(() -> {
            warmupCache();
            warmedUp = true;
        });
    }

    @Override
    public HealthCheckResponse call() {
        if (warmedUp) {
            return HealthCheckResponse.builder()
                .name("cache-warmup")
                .up()
                .build();
        }

        return HealthCheckResponse.builder()
            .name("cache-warmup")
            .down()
            .withData("status", "warming up")
            .build();
    }
}
```

### Multiple Health Checks in One Class

```java
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SystemHealthChecks {

    @Liveness
    public HealthCheck checkLiveness() {
        return HealthCheckResponse.builder()
            .name("liveness")
            .up()
            .build();
    }

    @Readiness
    public HealthCheck checkReadiness() {
        return HealthCheckResponse.builder()
            .name("readiness")
            .up()
            .build();
    }

    @Startup
    public HealthCheck checkStartup() {
        return HealthCheckResponse.builder()
            .name("startup")
            .up()
            .build();
    }
}
```

## HTTP Endpoints

| Endpoint | Health Checks | Description |
|----------|---------------|-------------|
| `GET /health` | All types | Execute all health checks |
| `GET /health/live` | Liveness | Liveness checks only |
| `GET /health/ready` | Readiness | Readiness checks only |
| `GET /health/started` | Startup | Startup checks only |
| `GET /health?pretty=true` | All types | Pretty-printed JSON |

### Response Status Codes

| HTTP Status | Condition |
|-------------|-----------|
| 200 OK | All health checks are UP |
| 503 Service Unavailable | Any health check is DOWN |
| 403 Forbidden | Health check service is disabled |

## Configuration

### domain.xml Configuration

```xml
<configs>
    <config name="server-config">
        <microprofile-healthcheck-configuration
            enabled="true"
            security-enabled="false"
            endpoint="health">
        </microprofile-healthcheck-configuration>
    </config>
</configs>
```

### Admin Commands

```bash
# Enable health check
asadmin set-microprofile-healthcheck-configuration --enabled=true

# Disable health check
asadmin set-microprofile-healthcheck-configuration --enabled=false

# Enable security
asadmin set-microprofile-healthcheck-configuration --security-enabled=true

# Change endpoint
asadmin set-microprofile-healthcheck-configuration --endpoint=status

# Get configuration
asadmin get-microprofile-healthcheck-configuration
```

## Integration with Payara Health Checks

Payara Health Check Service checkers can be added to MicroProfile Health:

```bash
asadmin set-healthcheck-microprofile-health-checker-configuration
    --enabled=true
    --add-to-microprofile-health=true
```

## Package Structure

```
microprofile/healthcheck/
└── src/main/java/fish/payara/microprofile/healthcheck/
    ├── HealthCheckService.java                 # Main service
    ├── HealthCheckType.java                    # Type enum
    ├── activation/
    │   ├── HealthApplicationContainer.java     # Application lifecycle
    │   ├── HealthContainer.java                # Container
    │   ├── HealthDeployer.java                 # Deployer
    │   └── HealthSniffer.java                  # Sniffer
    ├── admin/
    │   ├── GetMPHealthCheckConfiguration.java  # Admin command
    │   └── SetMPHealthCheckConfiguration.java  # Admin command
    ├── cdi/
    │   └── extension/
    │       └── HealthCDIExtension.java         # CDI extension
    ├── checks/
    │   └── PayaraHealthCheck.java             # Payara checker wrapper
    ├── config/
    │   └── MicroprofileHealthCheckConfiguration.java  # Config bean
    ├── response/
    │   ├── HealthCheckResponseBuilderImpl.java
    │   ├── HealthCheckResponseImpl.java
    │   └── HealthCheckResponseProviderImpl.java
    └── servlet/
        ├── HealthCheckServlet.java            # HTTP endpoint
        └── HealthCheckServletContainerInitializer.java
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `microprofile-health-api` | MicroProfile Health Check API |
| `nucleus/healthcheck` | Payara HealthCheckService integration |
| `cdi-api` | CDI API |
| `jakarta.servlet-api` | Servlet API |

## Notes

- **Class Loader Context** - Health checks execute in application class loader context
- **WebComponentInvocation** - Web component invocation is set up for proper JNDI context
- **Pretty Print** - Use `?pretty=true` query parameter for readable JSON
- **Security** - When security-enabled, health check endpoints require authentication
- **Dynamic Updates** - Health checks can be dynamically registered/unregistered
- **Application Isolation** - Each application's health checks are isolated by app name
- **Empty Deployment** - Returns "No Application deployed" check when no apps are deployed
