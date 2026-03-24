# CLAUDE.md - EJB OpenTracing

This file provides guidance for working with the `ejb-opentracing` module - OpenTracing integration for EJB.

## Module Overview

The ejb-opentracing module provides OpenTracing distributed tracing support for EJB method invocations.

## Build Commands

```bash
# Build ejb-opentracing module
mvn -DskipTests clean package -f appserver/ejb/ejb-opentracing/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Purpose**: Distributed tracing for EJB

## Tracing Features

### EJB Invocation Tracing

Each EJB method call creates a span:
- **Operation name**: `EJB/{bean-class}/{method-name}`
- **Tags**: Application, module, bean, method
- **Logs**: Parameters, return value, exceptions

### Example Span

```
EJB/com.example.MyService/doWork
├── Tags
│   ├── ejb.type: Stateless
│   ├── ejb.name: MyService
│   └── ejb.method: doWork
└── Logs
    ├── parameter: "test"
    ├── return: "result"
    └── tx.id: 123456
```

## Configuration

```xml
<ejb-container>
    <tracing>
        <enabled>true</enabled>
        <include-parameters>true</include-parameters>
        <include-return-value>true</include-return-value>
    </tracing>
</ejb-container>
```

## Tracing Interceptor

```java
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 10)
public class TracingInterceptor implements EjbContainerInterceptor {
    @Inject
    private Tracer tracer;

    @Override
    public Object aroundInvoke(InvocationContext ctx) {
        Span span = tracer.buildSpan("EJB/" + getBeanName(ctx))
            .withTag("ejb.method", ctx.getMethod().getName())
            .start();

        try {
            Object result = ctx.proceed();
            span.log("return", result);
            return result;
        } catch (Exception e) {
            span.log("error", e);
            throw e;
        } finally {
            span.finish();
        }
    }
}
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `opentracing-adapter` | OpenTracing integration |
| `opentracing-api` | OpenTracing API |

## Related Modules

- `ejb-container` - Container integration
- `payara-modules/opentracing-adapter` | Tracing adapter
