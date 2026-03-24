# CLAUDE.md - MicroProfile Telemetry

This file provides guidance for working with the `microprofile/telemetry` module - MicroProfile Telemetry (OpenTelemetry) implementation.

## Module Overview

The microprofile/telemetry module provides an implementation of the MicroProfile Telemetry API for Payara Server. It enables distributed tracing for JAX-RS applications using OpenTelemetry, with automatic span creation and context propagation.

**Key Features:**
- **@WithSpan Annotation** - Automatic span creation for CDI methods
- **JAX-RS Tracing** - Automatic tracing for JAX-RS client/server calls
- **Context Propagation** - W3C Trace Context propagation across services
- **Span Injection** - Inject current Span/Tracer/OpenTelemetry
- **Async Support** - CompletionStage and asynchronous JAX-RS support
- **Application-Scoped Tracer** - Per-application OpenTelemetry configuration
- **OpenTracing Bridge** - Backward compatibility with OpenTracing API

## Build Commands

```bash
# Build microprofile/telemetry module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/telemetry/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/telemetry/pom.xml
```

## Architecture

### CDI Extension Flow

```
Application Deployment
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│          TelemetryCdiExtension (CDI Extension)                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. beforeBeanDiscovery(@Observes)                       │ │
│  │     - Register OpenTracingTracerProducer                │ │
│  │     - Register OpenTelemetryTracerProducer              │ │
│  │     - Add @WithSpan as interceptor binding               │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. afterBeanDiscovery(@Observes)                        │ │
│  │     - Add WithSpanMethodInterceptorBean                 │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. initializeOpenTelemetry(@Observes AfterDeploymentValidation)│ │
│  │     - Check if otel.sdk.disabled=false                  │ │
│  │     - Collect all otel.* config properties              │ │
│  │     - Initialize application-scoped OpenTelemetry       │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  4. shutdownAppScopedTelemetry(@Observes BeforeShutdown)│ │
│  │     - Shutdown application OpenTelemetry if app-managed │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│          WithSpanMethodInterceptor                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  @AroundInvoke on methods with @WithSpan                │ │
│  │  - Check if not JAX-RS or JAX-WS method                 │ │
│  │  - Extract @SpanAttribute annotations                   │ │
│  │  - Start span with OpenTelemetry Tracer                │ │
│  │  - Handle sync/async invocations                       │ │
│  │  - Record exceptions and mark span status               │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### JAX-RS Client Tracing Flow

```
JAX-RS Client Request
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│      JaxrsClientRequestTelemetryFilter                         │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  filter(ClientRequestContext)                            │ │
│  │  1. Add Payara Request Tracing headers                   │ │
│  │  2. Create CLIENT span                                  │ │
│  │  3. Set HTTP method, URL, peer attributes              │ │
│  │  4. Inject W3C trace context headers                     │ │
│  │  5. Store span in request context property              │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  filter(ClientRequestContext, ClientResponseContext)    │ │
│  │  1. Retrieve span from request context                  │ │
│  │  2. Set HTTP status code attribute                      │ │
│  │  3. Mark error if 4xx/5xx status                         │ │
│  │  4. End span                                            │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### JAX-RS Server Tracing Flow

```
JAX-RS Server Request
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│      OpenTelemetryRequestEventListener                         │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  onEvent(HttpRequest event)                              │ │
│  │  1. Extract W3C trace context from headers               │ │
│  │  2. Create SERVER span                                  │ │
│  │  3. Set HTTP method, URL, host attributes               │ │
│  │  4. Set span as current                                 │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  onEvent(HttpResponse event)                            │ │
│  │  1. Set HTTP status code attribute                      │ │
│  │  2. Mark error if 4xx/5xx status                         │ │
│  │  3. End span                                            │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

## Core Components

### TelemetryCdiExtension

```java
public class TelemetryCdiExtension implements Extension {

    private final OpenTelemetryService openTelemetryService;
    private boolean appManagedOtel;

    void beforeBeanDiscovery(@Observes BeforeBeanDiscovery bbd, BeanManager bm) {
        addAnnotatedType(bbd, bm, OpenTracingTracerProducer.class);
        addAnnotatedType(bbd, bm, OpenTelemetryTracerProducer.class);
        bbd.addInterceptorBinding(new WithSpanAnnotatedType(bm.createAnnotatedType(WithSpan.class)));
    }

    void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager bm) {
        abd.addBean(new WithSpanMethodInterceptorBean(bm));
    }

    void initializeOpenTelemetry(@Observes AfterDeploymentValidation adv) {
        var config = ConfigProvider.getConfig();
        if (config.getOptionalValue("otel.sdk.disabled", Boolean.class).orElse(true)) {
            return; // app-specific OpenTelemetry is not configured
        }
        // Collect all otel.* properties and initialize
        var otelProps = StreamSupport.stream(config.getPropertyNames().spliterator(), false)
                .filter(key -> key.startsWith("otel."))
                .collect(Collectors.toMap(k -> k, k -> config.getValue(k, String.class)));
        appManagedOtel = true;
        openTelemetryService.initializeCurrentApplication(otelProps);
    }

    void shutdownAppScopedTelemetry(@Observes BeforeShutdown beforeShutdown) {
        if (appManagedOtel) {
            openTelemetryService.shutdownCurrentApplication();
        }
    }
}
```

**Purpose:** CDI extension that sets up OpenTelemetry producers and @WithSpan interception.

### WithSpanMethodInterceptor

```java
public class WithSpanMethodInterceptor {

    @AroundInvoke
    public Object withSpanCdiCall(final InvocationContext invocationContext) throws Exception {
        final PayaraTracingServices payaraTracingServices = new PayaraTracingServices();
        final OpenTelemetryService openTelemetryService = payaraTracingServices.getOpenTelemetryService();

        // Skip if not enabled or if JAX-RS/JAX-WS method (handled separately)
        if (openTelemetryService == null || !openTelemetryService.isEnabled()
                || isJaxRsMethod(invocationContext) || isWebServiceMethod(invocationContext)) {
            return invocationContext.proceed();
        }

        // Get @WithSpan annotation
        final WithSpan withSpan = getAnnotation(bm, WithSpan.class, invocationContext);

        // Extract @SpanAttribute values
        var builder = Attributes.builder();
        extractSpanAttributes(invocationContext, builder);
        var attributes = builder.build();

        // Build and start span
        SpanBuilder spanBuilder = openTelemetryService.getCurrentTracer()
                .spanBuilder(getWithSpanValue(invocationContext, withSpan))
                .setSpanKind(withSpan.kind())
                .setAllAttributes(attributes)
                .setParent(Context.current());

        final var span = spanBuilder.startSpan();

        // Handle sync or async
        if (invocationContext.getMethod().getReturnType().equals(CompletionStage.class)) {
            return handleAsyncInvocation(invocationContext, span);
        }
        return handleSyncInvocation(invocationContext, span);
    }

    private Object handleSyncInvocation(InvocationContext invocationContext, Span span) throws Exception {
        try (var ignore = PropagationHelper.start(span, Context.current())) {
            try {
                return invocationContext.proceed();
            } catch (final Exception ex) {
                markSpanAsFailed(span, ex);
                throw ex;
            }
        }
    }

    private Object handleAsyncInvocation(InvocationContext invocationContext, Span span) throws Exception {
        var helper = PropagationHelper.startMultiThreaded(span, Context.current());
        CompletionStage<?> future = (CompletionStage<?>) invocationContext.proceed();
        return future.whenComplete((value, ex) -> {
            if (ex != null) {
                markSpanAsFailed(helper.span(), ex);
            }
            helper.end();
            helper.close();
        });
    }

    private void markSpanAsFailed(Span span, Throwable ex) {
        span.setAttribute("error", true);
        span.setAttribute(SemanticAttributes.EXCEPTION_TYPE, Throwable.class.getName());
        span.addEvent(SemanticAttributes.EXCEPTION_EVENT_NAME,
                Attributes.of(SemanticAttributes.EXCEPTION_MESSAGE, ex.getMessage()));
        span.recordException(ex);
    }
}
```

**Purpose:** Intercepts methods annotated with @WithSpan to create telemetry spans.

### JaxrsClientRequestTelemetryFilter

```java
public class JaxrsClientRequestTelemetryFilter implements ClientRequestFilter, ClientResponseFilter {

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        final Tracer tracer = payaraTracingServices.getActiveTracer();

        // Build a CLIENT span
        SpanBuilder spanBuilder = tracer.spanBuilder(requestContext.getMethod())
                .setAttribute(SemanticAttributes.HTTP_URL, requestContext.getUri().toString())
                .setAttribute(SemanticAttributes.HTTP_METHOD, requestContext.getMethod())
                .setAttribute(SemanticAttributes.NET_PEER_NAME, requestContext.getUri().getHost())
                .setAttribute("component", "jaxrs")
                .setAttribute("span.kind", "client")
                .setSpanKind(SpanKind.CLIENT);

        // Get parent context from async request if present
        var parentSpanContext = (Context) requestContext.getProperty(PropagationHeaders.TELEMETRY_PROPAGATED_SPANCONTEXT);
        if (parentSpanContext != null) {
            spanBuilder.setParent(parentSpanContext);
        } else {
            spanBuilder.setParent(Context.current());
        }

        // Start span and inject context into headers
        Span span = spanBuilder.startSpan();
        requestContext.setProperty(PropagationHelper.class.getName(),
            PropagationHelper.start(span, parentSpanContext));

        openTelemetryService.getCurrentSdk().getPropagators().getTextMapPropagator().inject(
                Context.current(), requestContext,
                (clientRequestContext, s, s1) -> clientRequestContext.getHeaders().put(s, Collections.singletonList(s1))
        );
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        var helper = (PropagationHelper) requestContext.getProperty(PropagationHelper.class.getName());
        Span activeSpan = helper.span();

        // Set HTTP status
        Response.StatusType statusInfo = responseContext.getStatusInfo();
        activeSpan.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, statusInfo.getStatusCode());

        // Mark error for 4xx/5xx
        if (statusInfo.getFamily() == Response.Status.Family.CLIENT_ERROR
                || statusInfo.getFamily() == Response.Status.Family.SERVER_ERROR) {
            activeSpan.setAttribute("error", true);
            activeSpan.setStatus(StatusCode.ERROR);
        }

        helper.end();
        helper.close();
    }
}
```

**Purpose:** JAX-RS client filter that creates spans for outbound HTTP calls.

### OpenTelemetryTracerProducer

```java
@ApplicationScoped
public class OpenTelemetryTracerProducer {

    @Produces
    @ApplicationScoped
    Tracer createTracer() {
        return openTelemetry.getCurrentTracer();
    }

    @Produces
    Span currentSpan() {
        return FORWARDED_SPAN; // Delegates to Span.current()
    }

    @Produces
    @ApplicationScoped
    OpenTelemetry currentTelemetry() {
        return openTelemetry.getCurrentSdk();
    }

    @Produces
    Baggage currentBaggage() {
        return FORWARDED_BAGGAGE; // Delegates to Baggage.current()
    }
}
```

**Purpose:** CDI producers for OpenTelemetry objects (Tracer, Span, OpenTelemetry, Baggage).

## Usage Examples

### Basic @WithSpan Annotation

```java
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserService {

    @WithSpan
    public User getUser(Long id) {
        // Creates a span named "getUser"
        return userRepository.findById(id);
    }

    @WithSpan("CustomOperationName")
    public void customOperation() {
        // Creates a span named "CustomOperationName"
    }
}
```

### @WithSpan with Parameters

```java
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;

@ApplicationScoped
public class DataService {

    @WithSpan
    public processData(
            @SpanAttribute("data.id") Long id,
            @SpanAttribute("data.type") String type) {
        // Span attributes: data.id=123, data.type="example"
    }
}
```

### @WithSpan with Span Kind

```java
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.api.trace.SpanKind;

@ApplicationScoped
public class ExternalServiceClient {

    @WithSpan(kind = SpanKind.CLIENT)
    public String callExternalService(String url) {
        // Creates a CLIENT span for external calls
        return restTemplate.getForObject(url, String.class);
    }

    @WithSpan(kind = SpanKind.SERVER)
    public void handleRequest(String data) {
        // Creates a SERVER span
    }
}
```

### Injecting Tracer

```java
import io.opentelemetry.api.trace.Tracer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TracingService {

    @Inject
    private Tracer tracer;

    public void manualTracing() {
        Span span = tracer.spanBuilder("manualOperation")
                .setAttribute("operation", "custom")
                .startSpan();

        try {
            // Do work
            doSomething();
        } catch (Exception e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### Injecting Current Span

```java
import io.opentelemetry.api.trace.Span;
import jakarta.inject.Inject;

@ApplicationScoped
public class SpanService {

    @Inject
    private Span span;

    public void addCustomAttribute(String key, String value) {
        // Adds attribute to current span
        span.setAttribute(key, value);
    }

    public void addEvent(String eventName) {
        // Adds event to current span
        span.addEvent(eventName);
    }
}
```

### Injecting OpenTelemetry

```java
import io.opentelemetry.api.OpenTelemetry;
import jakarta.inject.Inject;

@ApplicationScoped
public class TelemetryService {

    @Inject
    private OpenTelemetry openTelemetry;

    public void getMetrics() {
        // Access propagators, context, etc.
        var propagators = openTelemetry.getPropagators();
        var context = Context.current();
    }
}
```

### JAX-RS Client Tracing

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class ApiClient {

    private final Client client;

    public ApiClient() {
        this.client = ClientBuilder.newClient();
    }

    public String getData(String url) {
        // Automatic tracing - creates CLIENT span
        try (Response response = client.target(url)
                .request()
                .get()) {
            return response.readEntity(String.class);
        }
    }
}
```

### JAX-RS Server Tracing

```java
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/api")
public class MyResource {

    @GET
    @Path("/data")
    @Produces("application/json")
    public String getData() {
        // Automatic SERVER span created for this endpoint
        return "data";
    }
}
```

### Async CompletionStage Support

```java
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class AsyncService {

    @WithSpan
    public CompletionStage<String> asyncOperation() {
        // Span is kept open until CompletionStage completes
        return CompletableFuture.supplyAsync(() -> {
            doWork();
            return "result";
        });
    }
}
```

### Manual Span Context Propagation

```java
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import jakarta.inject.Inject;

@ApplicationScoped
public class PropagationService {

    @Inject
    private Tracer tracer;

    public void propagateContext() {
        Span span = tracer.spanBuilder("operation")
                .startSpan();

        Context context = Context.current().with(span);

        try (var scope = context.makeCurrent()) {
            // All code in this block uses the same span context
            // Even across threads
            CompletableFuture.runAsync(() -> {
                // This async task will have the same span context
                doAsyncWork();
            });
        } finally {
            span.end();
        }
    }
}
```

## Configuration

### MicroProfile Config Properties

```properties
# Enable application-managed OpenTelemetry (disables server-level config)
otel.sdk.disabled=false

# Exporter configuration
otel.traces.exporter=otlp
otel.metrics.exporter=none
otel.logs.exporter=none

# OTLP endpoint
otel.exporter.otlp.endpoint=http://jaeger:4317
otel.exporter.otlp.protocol=grpc

# Service configuration
otel.service.name=my-service
otel.service.version=1.0.0

# Sampler configuration
otel.traces.sampler=on
otel.traces.sampler.arg=1.0

# Span limits
otel.traces.span.limit=1000

# Resource attributes
otel.resource.attributes=service.namespace=default,deployment.environment=production

# Batch configuration
otel.bsp.schedule.delay=5000
otel.bsp.max.queue.size=2048
otel.bsp.max.export.batch.size=512
```

### Environment Variables

```bash
# OpenTelemetry SDK configuration
export OTEL_SERVICE_NAME="my-service"
export OTEL_SERVICE_VERSION="1.0.0"
export OTEL_TRACES_EXPORTER="otlp"
export OTEL_EXPORTER_OTLP_ENDPOINT="http://jaeger:4317"
export OTEL_TRACES_SAMPLER="on"
export OTEL_TRACES_SAMPLER_ARG="1.0"
export OTEL_RESOURCE_ATTRIBUTES="service.namespace=default,deployment.environment=production"

# Java system properties
java -Dotel.service.name=my-service \
     -Dotel.traces.exporter=otlp \
     -Dotel.exporter.otlp.endpoint=http://jaeger:4317 \
     -jar myapp.jar
```

### domain.xml Configuration

```xml
<configs>
    <config name="server-config">
        <!-- Enable OpenTelemetry in nucleus/opentracing module -->
        <opentracing-configuration
            enabled="true">
        </opentracing-configuration>
    </config>
</configs>
```

## Semantic Attributes

### HTTP Attributes (Automatically Set)

| Attribute | Description | Example |
|-----------|-------------|---------|
| `http.method` | HTTP method | GET, POST |
| `http.url` | Full HTTP URL | http://example.com/api/users |
| `http.status_code` | HTTP status code | 200, 404, 500 |
| `http.scheme` | HTTP scheme | http, https |
| `http.host` | HTTP host | example.com |
| `net.peer.name` | Remote host name | api.example.com |
| `net.peer.port` | Remote port | 443 |

### Error Attributes

| Attribute | Description |
|-----------|-------------|
| `error` | Set to `true` when span has error |
| `exception.type` | Exception class name |
| `exception.message` | Exception message |
| `exception.stacktrace` | Exception stack trace |
| `exception.escaped` | True if error escaped span boundary |

## Tracing Predicate

You can configure a tracing predicate to selectively trace JAX-RS client calls:

```java
import jakarta.ws.rs.client.ClientRequestContext;
import java.util.function.Predicate;

public class CustomTracingPredicate implements Predicate<ClientRequestContext> {

    @Override
    public boolean test(ClientRequestContext requestContext) {
        // Only trace calls to specific hosts
        String host = requestContext.getUri().getHost();
        return host.endsWith("example.com");
    }
}
```

Register the predicate:

```java
import org.glassfish.jersey.client.ClientConfig;
import fish.payara.microprofile.telemetry.tracing.jaxrs.client.JaxrsClientRequestTelemetryFilter;

ClientConfig config = new ClientConfig();
config.setProperty(JaxrsClientRequestTelemetryFilter.REQUEST_CONTEXT_TRACING_PREDICATE,
        new CustomTracingPredicate());
```

## Package Structure

```
microprofile/telemetry/
└── src/main/java/fish/payara/microprofile/telemetry/tracing/
    ├── TelemetryCdiExtension.java               # CDI extension
    ├── PayaraTracingServices.java              # Service locator helper
    ├── WithSpanMethodInterceptor.java          # @WithSpan interceptor
    ├── WithSpanMethodInterceptorBean.java      # Interceptor bean
    ├── OpenTelemetryTracerProducer.java        # OpenTelemetry producers
    ├── OpenTracingTracerProducer.java          # OpenTracing producers
    ├── OpenTracingCdiUtils.java               # OpenTracing utilities
    ├── jaxrs/
    │   ├── JerseyOpenTelemetryAutoDiscoverable.java
    │   ├── OpenTelemetryRequestEventListener.java    # Server tracing
    │   ├── OpenTelemetryApplicationEventListener.java
    │   ├── OpenTelemetryPreInvocationInterceptor.java
    │   ├── OpenTracingHelper.java
    │   ├── ResourceCache.java
    │   └── client/
    │       ├── JaxrsClientRequestTelemetryFilter.java  # Client tracing
    │       ├── JaxrsClientRequestTracingDiscoverable.java
    │       ├── RestClientTelemetryListener.java
    │       ├── TracedMethodFilter.java
    │       └── AsyncContextPropagator.java         # Async support
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `microprofile-telemetry-api` | MicroProfile Telemetry API |
| `micrometer-core` | OpenTelemetry integration |
| `opentelemetry-sdk` | OpenTelemetry SDK |
| `opentelemetry-instrumentation-annotations` | @WithSpan annotation |
| `nucleus/opentracing` | OpenTelemetryService integration |
| `nucleus/requesttracing` | Payara request tracing integration |

## Notes

- **Server-Level Tracing** - When `otel.sdk.disabled=true` (default), uses server-level OpenTelemetry configuration
- **Application-Level Tracing** - Set `otel.sdk.disabled=false` to use application-specific configuration
- **JAX-RS Exclusion** - JAX-RS and JAX-WS methods are handled by their respective filters, not @WithSpan interceptor
- **Async Support** - CompletionStage results keep span open until completion
- **Context Propagation** - W3C Trace Context headers automatically propagated
- **OpenTracing Bridge** - Supports both OpenTelemetry and OpenTracing APIs
- **Payara Integration** - Integrates with Payara Request Tracing Service
- **Attribute Forwarding** - Injected Span/Baggage forward to current context (time-sensitive)

## Troubleshooting

### Spans Not Appearing

1. Check if OpenTelemetry is enabled:
   ```bash
   asadmin get-opentracing-configuration --enabled=true
   ```

2. Check application configuration:
   ```properties
   # For application-managed telemetry
   otel.sdk.disabled=false
   otel.traces.exporter=otlp
   ```

3. Check exporter endpoint is reachable

### Missing HTTP Attributes

Ensure JAX-RS tracing filters are registered. They should auto-discover via:

- `JaxrsClientRequestTelemetryFilter` - For client calls
- `OpenTelemetryRequestEventListener` - For server requests

### Context Not Propagating

Check that:
1. Headers are not being filtered by proxies/load balancers
2. W3C Trace Context format is supported by both services
3. Same propagator configuration on both services

### Application-Scoped Tracing Not Working

1. Verify `otel.sdk.disabled=false`
2. Check for conflicting configuration
3. Ensure application-scoped tracer is being initialized:
   ```java
   @Inject
   private Tracer tracer; // Should use app tracer
   ```
