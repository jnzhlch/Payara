# CLAUDE.md - MicroProfile OpenTracing

This file provides guidance for working with the `microprofile/opentracing` module - MicroProfile OpenTracing API implementation (deprecated in favor of OpenTelemetry).

## Module Overview

The microprofile/opentracing module implements the MicroProfile OpenTracing specification, providing distributed tracing capabilities using the OpenTracing API. It provides CDI interceptors for automatic span creation and integration with JAX-RS applications.

> **Note:** OpenTracing has been merged into OpenTelemetry. This module is legacy. New applications should use MicroProfile Telemetry (OpenTelemetry).

**Key Features:**
- **@Traced Annotation** - Automatic span creation for CDI beans
- **JAX-RS Integration** - Automatic tracing of REST endpoints
- **JAX-WS Integration** - Automatic tracing of SOAP web services
- **Configuration Override** - Per-method tracing enable/disable via config

## Build Commands

```bash
# Build opentracing module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/opentracing/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/opentracing/pom.xml
```

## Core Components

### @Traced Annotation

```java
@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Traced {

    /**
     * Whether tracing is enabled. Can be overridden by config.
     */
    boolean value() default true;

    /**
     * Operation name for the span. Empty means use method name.
     */
    String operationName() default "";
}
```

**Usage Examples:**

```java
// Enable tracing for all methods in a class
@Traced
@ApplicationScoped
public class UserService {
    public User getUser(String id) { ... }  // Automatically traced
}

// Disable tracing for specific method
@Traced(false)
public void sensitiveOperation() { ... }

// Custom operation name
@Traced(operationName = "calculateDiscount")
public BigDecimal calculateDiscount(Order order) { ... }
```

### TracedInterceptor

```java
@Interceptor
@Traced
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 10)
public class TracedInterceptor {

    @Inject
    private Tracer tracer;

    @AroundInvoke
    public Object traceCdiCall(final InvocationContext invocationContext) throws Exception {
        // Skip JAX-RS and JAX-WS (handled separately)
        if (isJaxRsMethod(invocationContext) || isWebServiceMethod(invocationContext)) {
            return invocationContext.proceed();
        }

        // Get annotation and check config override
        final Traced traced = getAnnotation(beanManager, Traced.class, invocationContext);
        final boolean tracingEnabled = getConfigOverrideValue(
            invocationContext, "tracing", Boolean.class
        ).orElse(traced.value());

        if (!tracingEnabled) {
            return invocationContext.proceed();
        }

        // Build and start span
        final String operationName = getOperationName(traced, invocationContext);
        final SpanBuilder spanBuilder = tracer.buildSpan(operationName);

        // Get parent span from JAX-RS if available
        tracer.activeSpan().ifPresent(spanBuilder::asChildOf);

        final Span span = spanBuilder.start();
        try (Scope scope = tracer.scopeManager().activate(span)) {
            return invocationContext.proceed();
        } catch (Exception e) {
            // Tag error on span
            span.setTag(Tags.ERROR.getKey(), true);
            throw e;
        } finally {
            span.finish();
        }
    }
}
```

**Key Behaviors:**
1. **Skips JAX-RS/JAX-WS** - These are traced by their own filters
2. **Config Override** - `config.property` can override annotation value
3. **Error Tagging** - Automatically tags errors on spans
4. **Parent Span** - Uses active span as parent if available

### OpenTracingCdiExtension

```java
public class OpenTracingCdiExtension implements Extension {

    <T> void processAnnotatedType(@Observes ProcessAnnotatedType<T> pat) {
        // Registers @Traced as an interceptor binding
        AnnotatedType<T> annotatedType = pat.getAnnotatedType();
        if (annotatedType.isAnnotationPresent(Traced.class)) {
            // Enable interception for @Traced classes
            pat.configureAnnotatedType()
                .add(TracedLiteral.INSTANCE);
        }
    }

    void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager bm) {
        // Register interceptor beans
        abd.addBean(new TracedInterceptorBean(bm));
    }
}
```

### OpenTracingCdiUtils

Utility class for annotation handling and config lookup:

```java
public final class OpenTracingCdiUtils {

    public static Optional<Boolean> getConfigOverrideValue(
            InvocationContext invocationContext,
            String configKey,
            Class<Boolean> type) {
        // Builds config key: <fully-qualified-class-name>/<method-name>/<key>
        // Example: com.example.UserService/getUser/tracing
    }

    public static String getOperationName(
            Traced traced,
            InvocationContext invocationContext) {
        // Uses operationName from annotation, or method name if empty
    }

    public static boolean isJaxRsMethod(InvocationContext ic) {
        // Checks if method is in a JAX-RS resource class
    }

    public static boolean isWebServiceMethod(InvocationContext ic) {
        // Checks if method is a JAX-WS web service method
    }
}
```

## Package Structure

```
microprofile/opentracing/
└── src/main/java/fish/payara/microprofile/opentracing/
    ├── OpenTracingCdiExtension.java           # CDI extension
    ├── OpenTracingCdiUtils.java               # Utilities
    ├── Traced.java                            # @Traced annotation
    ├── TracedInterceptor.java                 # Interceptor implementation
    └── TracedInterceptorBean.java             # Bean definition
```

## Configuration

Tracing can be controlled via MicroProfile Config:

```properties
# Global tracing enable/disable
fish.payara.opentracing.enabled=true

# Per-method override (format: className/methodName/property)
com.example.MyService/myMethod/tracing=false
com.example.MyService/calculate/operationName=customName
```

## JAX-RS Integration

JAX-RS endpoints are automatically traced via the JAX-RS container filter (in separate integration module):

```java
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    @GET
    public List<User> getUsers() { ... }  // Auto-traced with span "GET /users"

    @POST
    @Traced(operationName = "createUser")
    public Response createUser(User user) { ... }  // Custom operation name
}
```

## JAX-WS Integration

SOAP web services are also automatically traced:

```java
@WebService
public class CustomerService {

    @WebMethod
    public Customer getCustomer(String id) { ... }  // Auto-traced
}
```

## Span Context Propagation

OpenTracing uses W3C Trace Context for propagation:

**Headers:**
- `traceparent` - Trace ID, span ID, and sampling flags
- `tracestate` - Vendor-specific trace state

```java
// Outbound request (handled by JAX-RS client filter)
client.target("http://service/api")
    .request()
    .get();  // Trace context automatically added

// Inbound request (handled by server filter)
@GET
public Response handle() { ... }  // Trace context automatically extracted
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `microprofile-config` | Configuration for tracing settings |
| `jakarta.cdi` | CDI for interceptor injection |
| `opentracing-api` | OpenTracing API |
| `jaxrs-api` | JAX-RS integration |

## Comparison with Telemetry

| Feature | OpenTracing | OpenTelemetry (Telemetry) |
|---------|-------------|---------------------------|
| Annotation | `@Traced` | `@WithSpan` |
| API | OpenTracing | OpenTelemetry |
| Status | Deprecated | Recommended |
| Spec Version | MP 1.x | MP 3.x+ |

## Notes

- **Legacy Module** - Use MicroProfile Telemetry for new projects
- **Interceptor Priority** - `PLATFORM_BEFORE + 10` ensures early execution
- **Automatic Skips** - JAX-RS and JAX-WS have their own tracing filters
- **Error Handling** - Exceptions are automatically tagged on spans
