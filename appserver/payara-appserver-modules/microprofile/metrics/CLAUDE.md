# CLAUDE.md - MicroProfile Metrics

This file provides guidance for working with the `microprofile/metrics` module - MicroProfile Metrics API implementation.

## Module Overview

The microprofile/metrics module provides an implementation of the MicroProfile Metrics API for Payara Server. It enables applications to expose metrics in OpenMetrics format and collect runtime statistics.

**Key Features:**
- **Metric Types** - Counter, Gauge, Histogram, Timer, Concurrent Gauge
- **Metric Registries** - Base, Vendor, Application scoped registries
- **CDI Annotations** - @Counted, @Gauge, @Timed for automatic metrics
- **OpenMetrics Format** - Standard Prometheus-compatible output
- **REST Endpoint** - `/metrics` with query parameters for filtering
- **Multi-Application** - Per-application metric isolation

## Build Commands

```bash
# Build microprofile/metrics module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/metrics/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/metrics/pom.xml
```

## Architecture

### Metrics Service Structure

```
┌────────────────────────────────────────────────────────────────┐
│                     MetricsService                             │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  MetricsContext (per application)                        │ │
│  │  - String name (application name or "" for server)      │ │
│  │  - Map<String, MetricRegistry> registries                │ │
│  │  - MetricRegistry getBaseRegistry()                      │ │
│  │  - MetricRegistry getVendorRegistry()                    │ │
│  │  - MetricRegistry getApplicationRegistry()               │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Available Contexts:                                     │ │
│  │  - "" (empty) = Server context                          │ │
│  │  - "app-name" = Application context                      │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### CDI Extension Flow

```
Application Deployment
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│            MetricCDIExtension (CDI Extension)                   │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. beforeBeanDiscovery                                  │ │
│  │     - Add @Nonbinding to metric annotations              │ │
│  │     - Register interceptor classes                       │ │
│  │     - Register MetricProducer, MetricRegistryProducer    │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. validateMetrics(@Observes ProcessAnnotatedType)      │ │
│  │     - Find @Counted, @Gauge, @Timed annotations          │ │
│  │     - Validate metric names and metadata                │ │
│  │     - Pre-register @Timed metrics                       │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. validationError(@Observes AfterBeanDiscovery)        │ │
│  │     - Report any validation errors                      │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│              Metrics Interceptor Chain                         │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  @Counted → CountedInterceptor                           │ │
│  │     counter.inc()                                        │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  @Timed → TimedInterceptor                               │ │
│  │     timer.time(() -> context.proceed())                  │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  @Gauge → MetricProducer (non-invasive)                   │ │
│  │     Registered at deployment time                        │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### REST Endpoint Flow

```
GET /metrics?scope=application&name=custom.counter
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│               MetricsResource.processRequest()                 │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Check if metrics enabled                             │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Parse query parameters                               │ │
│  │     - scope (base/vendor/application)                    │ │
│  │     - name (metric name filter)                          │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Determine content type from Accept header            │ │
│  │     - text/plain (OpenMetrics format)                   │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  4. Create MetricsWriter with exporter                   │ │
│  │     OpenMetricsExporter for text/plain                   │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  5. Write metrics for all contexts                       │ │
│  │     writer.write(scope, metricName)                     │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

## Core Components

### MetricsService

```java
@Contract
public interface MetricsService {

    boolean isEnabled();

    Set<String> getContextNames();

    MetricsContext getContext(boolean createIfNotExists);

    MetricsContext getContext(String name);

    interface MetricsContext {
        String SERVER_CONTEXT_NAME = "";

        String getName();

        MetricRegistry getOrCreateRegistry(String registryName);

        default MetricRegistry getBaseRegistry() {
            return getOrCreateRegistry(MetricRegistry.BASE_SCOPE);
        }

        default MetricRegistry getVendorRegistry() {
            return getOrCreateRegistry(MetricRegistry.VENDOR_SCOPE);
        }

        default MetricRegistry getApplicationRegistry() {
            return getOrCreateRegistry(MetricRegistry.APPLICATION_SCOPE);
        }

        ConcurrentMap<String, MetricRegistry> getRegistries();
    }
}
```

**Purpose:** Central service for accessing metric registries across applications.

### CountedInterceptor

```java
@Counted
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 1)
public class CountedInterceptor extends AbstractInterceptor {

    @Override
    protected <E extends Member & AnnotatedElement> Object applyInterceptor(
            InvocationContext context, E element) throws Exception {
        return proceedCounted(context, element, bean.getBeanClass(), this::getMetric);
    }

    static <E extends Member & AnnotatedElement> Object proceedCounted(
            InvocationContext context, E element, Class<?> bean,
            ThreeFunctionResolver<MetricID, Class<Counter>, String, Counter> loader) throws Exception {
        Counter counter = apply(element, bean, AnnotationReader.COUNTED,
            Counter.class, loader);
        counter.inc();
        return context.proceed();
    }
}
```

**Purpose:** Intercepts methods annotated with @Counted to increment a counter.

### TimedInterceptor

```java
@Timed
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 1)
public class TimedInterceptor extends AbstractInterceptor {

    static <E extends Member & AnnotatedElement> Object proceedTimed(
            InvocationContext context, E element, Class<?> bean,
            ThreeFunctionResolver<MetricID, Class<Timer>, String, Timer> loader) throws Exception {
        return apply(element, bean, AnnotationReader.TIMED,
            Timer.class, loader).time(context::proceed);
    }
}
```

**Purpose:** Intercepts methods annotated with @Timed to time method execution.

### MetricsResource

```java
public class MetricsResource extends HttpServlet {

    protected void processRequest(HttpServletRequest request,
                                HttpServletResponse response)
            throws ServletException, IOException {

        MetricsService metricsService =
            Globals.getDefaultBaseServiceLocator().getService(MetricsService.class);

        if (!metricsService.isEnabled()) {
            response.sendError(SC_FORBIDDEN, "MicroProfile Metrics Service is disabled");
            return;
        }

        metricsService.refresh();

        String scopeParameter = request.getParameter("scope");
        String metricName = request.getParameter("name");
        String contentType = getContentType(request, response);

        if (contentType != null) {
            response.setContentType(contentType);
            MetricsWriter outputWriter = getOutputWriter(request, response,
                metricsService, contentType);

            if (scopeParameter != null && metricName != null) {
                outputWriter.write(scopeParameter, metricName);
            } else if (scopeParameter != null) {
                outputWriter.write(scopeParameter);
            } else {
                outputWriter.write(); // Write all scopes
            }
        }
    }
}
```

**Purpose:** HTTP servlet that serves metrics at `/metrics` endpoint.

## Usage Examples

### @Counted Annotation

```java
import org.eclipse.microprofile.metrics.annotation.Counted;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserService {

    @Counted(name = "user.creation", absolute = true)
    public void createUser(User user) {
        // Counter "user.creation" incremented on each call
        userRepository.save(user);
    }

    @Counted(name = "user.deletion", description = "Number of users deleted")
    public void deleteUser(String userId) {
        userRepository.delete(userId);
    }

    @Counted(name = "failed.logins", monotonic = true)
    public void recordFailedLogin() {
        // Counter only increases, never decreases
    }
}
```

### @Timed Annotation

```java
import org.eclipse.microprofile.metrics.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DataService {

    @Timed(name = "data.query", description = "Database query timing")
    public List<Data> queryData(String query) {
        // Timer records execution time
        return dataRepository.query(query);
    }

    @Timed(
        name = "data.process",
        unit = MetricUnits.MILLISECONDS,
        description = "Data processing duration"
    )
    public void processData(Data data) {
        // Process data
    }
}
```

### @Gauge Annotation

```java
import org.eclipse.microprofile.metrics.annotation.Gauge;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CacheMetrics {

    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

    @Gauge(name = "cache.size", unit = MetricUnits.NONE, absolute = true)
    public long getCacheSize() {
        return cache.size();
    }

    @Gauge(name = "cache.hit.ratio", unit = MetricUnits.PERCENT)
    public double getCacheHitRatio() {
        int hits = getHits();
        int total = getTotalRequests();
        return total > 0 ? (hits * 100.0 / total) : 0;
    }
}
```

### Programmatic Metrics

```java
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.MetricRegistry;
import jakarta.inject.Inject;

@ApplicationScoped
public class MetricsService {

    @Inject
    private MetricRegistry registry;

    public void recordEvent() {
        // Counter
        Counter counter = registry.counter("events.total");
        counter.inc();

        // Counter with tags
        Counter taggedCounter = registry.counter("events.by.type",
            new Tag("type", "user"), new Tag("action", "login"));
        taggedCounter.inc();
    }

    public void measureRequest() {
        // Timer
        Timer timer = registry.timer("request.duration");
        timer.time(() -> {
            // Do some work
            return processRequest();
        });
    }

    public void recordValue(long value) {
        // Histogram
        Histogram histogram = registry.histogram("request.size");
        histogram.update(value);
    }

    public void registerGauge(Object object) {
        // Gauge (procedural registration)
        registry.gauge("queue.size", object, obj -> obj.getQueueSize());
    }
}
```

### Metric Registry Scopes

```java
import org.eclipse.microprofile.metrics.MetricRegistry;
import jakarta.inject.Inject;

@ApplicationScoped
public class ScopedMetrics {

    @Inject
    private MetricRegistry applicationRegistry;

    public void createApplicationScopedMetrics() {
        // Application-scoped metrics (default)
        Counter counter = applicationRegistry.counter("app.requests");
        counter.inc();
    }

    public void createVendorScopedMetrics() {
        // Vendor-scoped metrics
        MetricRegistry vendorRegistry =
            metricRegistryRegistry.getRegistry(MetricRegistry.VENDOR_SCOPE).get();
        Counter counter = vendorRegistry.counter("vendor.custom.metric");
    }

    public void createBaseScopedMetrics() {
        // Base-scoped metrics (JVM metrics)
        MetricRegistry baseRegistry =
            metricRegistryRegistry.getRegistry(MetricRegistry.BASE_SCOPE).get();
        // JVM metrics are automatically registered here
    }
}
```

### OpenMetrics Format

```
# TYPE application:counter_user_creation_total counter
# HELP application:counter_user_creation_total Number of users created
application:counter_user_creation_total 42

# TYPE application:timer_data_query_seconds summary
# HELP application:timer_data_query_seconds Database query timing
application:timer_data_query_seconds_count 100
application:timer_data_query_seconds_sum 2.5
application:timer_data_query_seconds{quantile="0.5"} 0.023
application:timer_data_query_seconds{quantile="0.95"} 0.089
application:timer_data_query_seconds{quantile="0.99"} 0.156

# TYPE application:gauge_cache_size gauge
# HELP application:gauge_cache_size Current cache size
application:gauge_cache_size 1024

# TYPE base:classloading_loaded_classes_count gauge
base:classloading_loaded_classes_count 5234

# TYPE vendor:jvm_buffer_pool_used_bytes gauge
vendor:jvm_buffer_pool_used_bytes{pool="direct"} 1048576
```

## HTTP Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /metrics` | All metrics in all scopes |
| `GET /metrics?scope=application` | Application-scoped metrics only |
| `GET /metrics?scope=vendor` | Vendor-scoped metrics only |
| `GET /metrics?scope=base` | Base (JVM) metrics only |
| `GET /metrics?name=metric.name` | Specific metric |
| `GET /metrics?scope=application&name=metric.name` | Specific metric in scope |

### Response Format

**Accept Headers:**
- `Accept: text/plain` - OpenMetrics format (Prometheus)
- `Accept: application/json` - JSON format (legacy, may not be supported)

## Configuration

### domain.xml Configuration

```xml
<configs>
    <config name="server-config">
        <microprofile-metrics-configuration
            enabled="true"
            security-enabled="false"
            dynamic="true">
        </microprofile-metrics-configuration>
    </config>
</configs>
```

### Admin Commands

```bash
# Enable metrics
asadmin set-metrics-configuration --enabled=true

# Disable metrics
asadmin set-metrics-configuration --enabled=false

# Enable security
asadmin set-metrics-configuration --security-enabled=true

# Enable dynamic (no restart required)
asadmin set-metrics-configuration --dynamic=true

# Get configuration
asadmin get-metrics-configuration
```

## Global Tags

Global tags can be configured to be added to all metrics:

```bash
asadmin set-metrics-configuration --tags=environment:production,cluster:us-east
```

Tags format:
- Tag names must match `[a-zA-Z_][a-zA-Z0-9_]*`
- Tag values must escape `=` and `,` with backslash
- Example: `tag_name=tag\,value\=with_escapes`

## Built-in Metrics

### Base Registry (JVM Metrics)

| Metric | Type | Description |
|--------|------|-------------|
| `jvm_memory_used_bytes` | Gauge | JVM memory usage |
| `jvm_memory_max_bytes` | Gauge | JVM memory max |
| `jvm_gc_pause_seconds` | Timer | GC pause duration |
| `jvm_thread_count` | Gauge | Thread count |
| `jvm_classloading_loaded_classes_count` | Gauge | Loaded class count |
| `cpu_system_load_average` | Gauge | System load average |

### Vendor Registry (Payara Specific)

| Metric | Type | Description |
|--------|------|-------------|
| `payara.thread_pool.queue.size` | Gauge | Thread pool queue size |
| `payara.http.sessions.active` | Gauge | Active HTTP sessions |

## Package Structure

```
microprofile/metrics/
└── src/main/java/fish/payara/microprofile/metrics/
    ├── MetricsService.java                     # Service interface
    ├── activation/
    │   ├── MetricsApplicationContainer.java   # Application lifecycle
    │   ├── MetricsContainer.java              # Container
    │   ├── MetricsDeployer.java               # Deployer
    │   └── MetricsSniffer.java                # Sniffer
    ├── admin/
    │   ├── GetMetricsConfigurationCommand.java # Admin command
    │   ├── MetricsServiceConfiguration.java    # Config bean
    │   └── SetMetricsConfigurationCommand.java # Admin command
    ├── cdi/
    │   ├── extension/
    │   │   └── MetricCDIExtension.java         # CDI extension
    │   ├── interceptor/
    │   │   ├── AbstractInterceptor.java        # Base interceptor
    │   │   ├── CountedInterceptor.java         # @Counted handler
    │   │   ├── MetricsInterceptor.java         # Metrics interceptor
    │   │   └── TimedInterceptor.java           # @Timed handler
    │   ├── producer/
    │   │   ├── MetricProducer.java             # Metric producers
    │   │   └── MetricRegistryProducer.java     # Registry producer
    │   ├── AnnotationReader.java              # Annotation utilities
    │   ├── MetricUtils.java                    # Utility methods
    │   └── MetricsAnnotationBinding.java       # CDI binding
    ├── exception/
    │   ├── NoSuchMetricException.java         # Metric not found
    │   └── NoSuchRegistryException.java       # Registry not found
    ├── impl/
    │   ├── MetricsServiceImpl.java             # Service implementation
    │   ├── MetricRegistryImpl.java            # Registry impl
    │   ├── CounterImpl.java                   # Counter impl
    │   ├── TimerImpl.java                     # Timer impl
    │   ├── HistogramImpl.java                 # Histogram impl
    │   ├── GaugeImpl.java                     # Gauge impl
    │   └── Clock.java                         # Time utilities
    ├── jmx/
    │   ├── MBeanCounterImpl.java              # MBean counter
    │   ├── MBeanGuageImpl.java                # MBean gauge
    │   ├── MetricsMetadata.java               # Metadata from XML
    │   └── MetricsMetadataHelper.java         # Metadata utilities
    ├── rest/
    │   ├── MetricsResource.java               # HTTP endpoint
    │   └── MetricsServletContainerInitializer.java
    ├── writer/
    │   ├── MetricsWriter.java                  # Writer interface
    │   ├── MetricsWriterImpl.java              # Writer impl
    │   ├── MetricExporter.java                 # Exporter interface
    │   ├── FilteredMetricsExporter.java        # Filtered exporter
    │   └── OpenMetricsExporter.java            # OpenMetrics format
    └── healthcheck/
        ├── HealthCheckCounter.java            # Health check counter
        └── HealthCheckGauge.java              # Health check gauge
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `microprofile-metrics-api` | MicroProfile Metrics API |
| `cdi-api` | CDI API |
| `jakarta.servlet-api` | Servlet API |
| `nucleus/microprofile/config` | Configuration support |

## Notes

- **Multi-Application** - Each application has its own MetricsContext
- **Server Context** - Empty string `""` represents server-level metrics
- **Global Tags** - Applied to all metrics when configured
- **Dynamic Updates** - Metrics can be reconfigured dynamically
- **OpenMetrics Format** - Default format is text/plain (Prometheus compatible)
- **@Timed Pre-registration** - @Timed metrics are pre-registered during deployment for private methods
- **Application Isolation** - Metrics from different applications are isolated
- **JVM Metrics** - Base registry provides JVM metrics automatically
