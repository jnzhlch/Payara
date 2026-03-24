# CLAUDE.md - MicroProfile OpenTracing JAX-WS

This file provides guidance for working with the `microprofile/opentracing-jaxws` module - OpenTracing integration for JAX-WS (SOAP) web services.

## Module Overview

The opentracing-jaxws module provides distributed tracing for JAX-WS (SOAP) web services using the OpenTracing API. It integrates with Payara's request tracing service to automatically trace SOAP web service calls.

> **Note:** This module uses the deprecated OpenTracing API. New applications should use MicroProfile Telemetry (OpenTelemetry).

**Key Features:**
- **Automatic JAX-WS Tracing** - Traces all SOAP web service calls
- **@Traced Annotation Support** - Per-method tracing control
- **Span Context Propagation** - W3C Trace Context headers
- **Error Tagging** - Automatically tags SOAP faults

## Build Commands

```bash
# Build opentracing-jaxws module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/opentracing-jaxws/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/opentracing-jaxws/pom.xml
```

## Core Components

### JaxwsContainerRequestTracingFilter

```java
@Service(name = "jaxws-opentracing-filter")
public class JaxwsContainerRequestTracingFilter implements MonitorFilter {

    @Inject
    private RequestTracingService requestTracing;

    @Inject
    private OpenTracingService openTracing;

    @Override
    public void filterRequest(Packet pipeRequest, MonitorContext monitorContext) {
        // Called before JAX-WS method invocation
        // Creates and starts span
        if (isTraceInProgress() && shouldTrace()) {
            Tracer tracer = getTracer();
            SpanBuilder spanBuilder = tracer.buildSpan(operationName)
                    .withTag(SPAN_KIND.getKey(), SPAN_KIND_SERVER)
                    .withTag(HTTP_METHOD.getKey(), httpRequest.getMethod())
                    .withTag(HTTP_URL.getKey(), httpRequest.getRequestURL().toString())
                    .withTag(COMPONENT.getKey(), "jaxws");

            // Extract parent span context from headers
            SpanContext spanContext = tracer.extract(HTTP_HEADERS, headers);
            if (spanContext != null) {
                spanBuilder.asChildOf(spanContext);
            }

            Scope scope = tracer.activateSpan(spanBuilder.start());
            httpRequest.setAttribute(Scope.class.getName(), scope);
        }
    }

    @Override
    public void filterResponse(Packet pipeRequest, Packet pipeResponse, MonitorContext monitorContext) {
        // Called after JAX-WS method invocation
        // Tags response status and finishes span
        Span activeSpan = getTracer().activeSpan();
        if (activeSpan != null) {
            activeSpan.setTag(HTTP_STATUS.getKey(), statusCode);

            // Tag errors for SOAP faults
            if (isError(statusCode) || message.isFault()) {
                activeSpan.setTag(ERROR.getKey(), true);
                activeSpan.log(singletonMap("event", "error"));
            }

            activeSpan.finish();
        }
    }
}
```

**Key Behaviors:**
1. **Intercepts JAX-WS Calls** - Uses `MonitorFilter` to intercept all JAX-WS requests/responses
2. **Extracts Trace Context** - Reads W3C trace headers from incoming SOAP requests
3. **Creates Server Spans** - Each JAX-WS call creates a server span
4. **Tags SOAP Faults** - Automatically tags errors for SOAP faults

### OpenTracingJaxwsCdiUtils

Utility class for JAX-WS CDI integration:

```java
public class OpenTracingJaxwsCdiUtils {

    public static <A extends Annotation> A getAnnotation(
            BeanManager beanManager,
            Class<A> annotationClass,
            MonitorContext monitorContext) {
        // Gets annotation from JAX-WS method
        return OpenTracingCdiUtils.getAnnotation(
                beanManager, annotationClass,
                monitorContext.getImplementationClass(),
                monitorContext.getCallInfo().getMethod());
    }

    public static <A extends Annotation, T> Optional<T> getConfigOverrideValue(
            Class<A> annotationClass,
            String parameterName,
            MonitorContext monitorContext,
            Class<T> parameterType) {
        // Gets config override for JAX-WS method
        return OpenTracingCdiUtils.getConfigOverrideValue(
                annotationClass, parameterName,
                monitorContext.getCallInfo().getMethod(),
                parameterType);
    }
}
```

## Package Structure

```
microprofile/opentracing-jaxws/
└── src/main/java/fish/payara/opentracing/jaxws/
    ├── JaxwsContainerRequestTracingFilter.java    # JAX-WS tracing filter
    └── OpenTracingJaxwsCdiUtils.java               # CDI utilities
```

## Operation Name

The span operation name is determined by:

1. **@Traced(operationName)** - Annotation value takes precedence
2. **Config Override** - `Traced.operationName` config property
3. **HTTP Path Provider** - If `mp.opentracing.server.operation-name-provider=http-path`
4. **Fallback** - `METHOD:ClassName.methodName`

```java
// Examples:
// POST:com.example.CustomerService.getCustomer
// GET:/CustomerService/getCustomer (with http-path provider)
```

**Configuration:**
```properties
# Use HTTP path format
mp.opentracing.server.operation-name-provider=http-path
```

## @Traced Annotation

Control tracing per method:

```java
@WebService
public class CustomerService {

    @WebMethod
    @Traced  // Explicitly enable tracing
    public Customer getCustomer(String id) {
        // ...
    }

    @WebMethod
    @Traced(false)  // Disable tracing for this method
    public void sensitiveOperation() {
        // ...
    }

    @WebMethod
    @Traced(operationName = "getCustomerById")
    public Customer findById(String id) {
        // Custom operation name
    }
}
```

## Config Override

Per-method tracing control via config:

```properties
# Disable tracing for specific method
com.example.CustomerService/getCustomer/tracing=false

# Custom operation name
com.example.MyService/calculate/operationName=customCalculation
```

## Skip Patterns

Skip tracing for certain paths:

```properties
# Skip health check and metrics endpoints (default)
/mp.opentracing.server.skip-pattern=/health|/metrics.*

# Custom skip patterns
mp.opentracing.server.skip-pattern=/health|/metrics|/status.*
```

**Default Mandatory Skips:**
- `/health`
- `/metrics`
- `/metrics/base`
- `/metrics/vendor`
- `/metrics/application`

## Span Tags

JAX-WS spans include these tags:

| Tag | Value | Example |
|-----|-------|---------|
| `span.kind` | `"server"` | Always server |
| `component` | `"jaxws"` | JAX-WS component |
| `http.method` | HTTP method | `"POST"`, `"GET"` |
| `http.url` | Request URL | `"http://localhost:8080/CustomerService"` |
| `http.status_code` | HTTP status | `200`, `500` |
| `error` | `true` if error | Set on SOAP faults |

## SOAP Fault Handling

SOAP faults are automatically tagged as errors:

```java
if (message.isFault()) {
    activeSpan.setTag(ERROR.getKey(), true);
    activeSpan.log(singletonMap("error.object", getSOAPFault(message)));
}

private Object getErrorObject(Message message) {
    return message.copy()
            .readAsSOAPMessage()
            .getSOAPBody()
            .getFault();
}
```

## Trace Context Propagation

Incoming JAX-WS requests:

```
SOAP Request with Headers:
├── traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
└── tracestate: congo=B3E6C

↓ Extracted by tracer.extract()

JAX-WS Span:
├── Parent: traceparent span ID
└── Tags: http.method, http.url, component
```

## MonitorFilter Integration

The filter integrates with the JAX-WS monitoring infrastructure:

```java
public interface MonitorFilter {
    void filterRequest(Packet request, MonitorContext context);
    void filterResponse(Packet request, Packet response, MonitorContext context);
}
```

**MonitorContext provides:**
- `getImplementationClass()` - The JAX-WS service class
- `getCallInfo().getMethod()` - The invoked method
- `getSeiModel()` - JAX-WS model information

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `opentracing-api` | OpenTracing API |
| `opentracing-jaxws` | JAX-WS integration |
| `requesttracing-core` | Payara request tracing service |
| `microprofile-opentracing` | Shared OpenTracing utilities |
| `microprofile-config` | Configuration for tracing settings |

## Notes

- **Legacy Module** - Use MicroProfile Telemetry for new projects
- **MonitorFilter** - Uses JAX-WS container filter mechanism
- **SOAP Faults** - Automatically detected and tagged
- **Parent Span** - Extracts parent context from trace headers
- **CDI Optional** - Works even if CDI is not enabled
