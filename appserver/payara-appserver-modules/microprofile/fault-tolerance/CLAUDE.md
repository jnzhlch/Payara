# CLAUDE.md - MicroProfile Fault Tolerance

This file provides guidance for working with the `microprofile/fault-tolerance` module - MicroProfile Fault Tolerance implementation.

## Module Overview

The microprofile/fault-tolerance module provides an implementation of the MicroProfile Fault Tolerance API for Payara Server. It enables applications to build resilient services by adding fault tolerance policies using annotations.

**Key Features:**
- **@Retry** - Retry methods on failure
- **@Timeout** - Set timeout for method execution
- **@CircuitBreaker** - Circuit breaker pattern
- **@Bulkhead** - Limit concurrent executions
- **@Fallback** - Fallback method on failure
- **@Asynchronous** - Async execution with fault tolerance
- **Metrics Integration** - Fault tolerance metrics

## Build Commands

```bash
# Build microprofile/fault-tolerance module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/fault-tolerance/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/fault-tolerance/pom.xml
```

## Architecture

### Policy Chain Execution

```
Method Invocation with @Retry, @Timeout, @CircuitBreaker
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│           FaultToleranceInterceptor.intercept()                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Check if should intercept                             │ │
│  │     if (PAYARA_FAULT_TOLERANCE_INTERCEPTOR_EXECUTED) return │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Get FaultToleranceConfig                              │ │
│  │     config = faultToleranceService.getConfig(context)   │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Build Policy chain                                    │ │
│  │     policy = FaultTolerancePolicy.get(context, config)  │ │
│  │     Contains ordered policies:                           │ │
│  │     1. @Asynchronous (if present)                         │ │
│  │     2. @Bulkhead (if present)                             │ │
│  │      │
│  │     @CircuitBreaker (if present)                         │ │
│  │     │
│  │     @Retry (if present)                                   │ │
│  │     │
│  │     @Timeout (if present)                                 │ │
│  │     │
│  │     @Fallback (if present)                                │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  4. Execute policy chain                                  │ │
│  │     policy.proceed(context, methodContext)               │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### Policy Execution Flow

```
Policy Chain Execution
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│  @Asynchronous Policy (if present)                            │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  - Submit task to executor service                         │ │
│  │  - Execute remaining policies in async context            │ │
│  └──────────────────────────────────────────────────────────┘ │
│       │
│       ▼
┌────────────────────────────────────────────────────────────────┐
│  @Bulkhead Policy (if present)                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  - Try to acquire semaphore permit                        │ │
│  │  - If successful, proceed                                  │
│  │  - If failed, throw BulkheadException                  │ │
│  └──────────────────────────────────────────────────────────┘ │
│       │
│       ▼
┌────────────────────────────────────────────────────────────────┐
│  @CircuitBreaker Policy (if present)                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  - Check circuit state (OPEN/HALF_OPEN/CLOSED)          │ │
│  │  - If OPEN/HALF_OPEN and timeout passed, try to close    │ │
│  │  - If CLOSED, check if failure ratio threshold met        │ │
│  │  - If OPEN, throw CircuitBreakerOpenException           │ │
│  └──────────────────────────────────────────────────────────┘ │
│       │
│       ▼
┌────────────────────────────────────────────────────────────────┐
│  @Retry Policy (if present)                                   │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  - Execute method with retry logic                         │ │
│  │  - maxRetries, maxDuration, jitter, delay                │ │
│  │  - retryOn specific exceptions                            │ │
│  │  - abortOn specific exceptions                            │ │
│  └──────────────────────────────────────────────────────────┘ │
│       │
│       ▼
┌────────────────────────────────────────────────────────────────┐
│  @Timeout Policy (if present)                                 │
│  │  - Execute method with timeout                              │
│  │  - If timeout exceeded, throw TimeoutException           │
│  │  - Inner-most policy (executed last)                      │
│  └──────────────────────────────────────────────────────────┘ │
│       │
│       ▼
┌────────────────────────────────────────────────────────────────┐
│  @Fallback Policy (if present)                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  - If any exception thrown, call fallback method          │ │
│  │  - fallback method can be:                                │ │
│  │    - Same class, different method                         │ │
│  │    - Different class                                      │ │
│  │    - Handler instance                                     │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

## Core Components

### FaultToleranceInterceptor

```java
@Dependent
public class FaultToleranceInterceptor implements Stereotypes {

    @Inject
    private BeanManager beanManager;

    @Inject
    private Instance<RequestContextController> requestContextControllerInstance;

    private FaultToleranceService faultToleranceService;

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        if (!shouldIntercept(context)) {
            return context.proceed();
        }

        context.getContextData().put(PAYARA_FAULT_TOLERANCE_INTERCEPTOR_EXECUTED,
            Boolean.TRUE);

        try {
            AtomicReference<FaultToleranceConfig> lazyConfig = new AtomicReference<>();
            Supplier<FaultToleranceConfig> configSupplier = () ->
                lazyConfig.updateAndGet(value ->
                    value != null ? value :
                    faultToleranceService.getConfig(context, this));

            FaultTolerancePolicy policy = FaultTolerancePolicy.get(
                context, configSupplier);

            if (policy.isPresent) {
                return policy.proceed(context, () ->
                    faultToleranceService.getMethodContext(
                        context, policy, getRequestContextController()));
            }
        } catch (FaultToleranceDefinitionException e) {
            logger.log(Level.SEVERE,
                "Effective FT policy contains illegal values, falling back");
        }

        return context.proceed();
    }

    protected boolean shouldIntercept(InvocationContext invocationContext) {
        // Prevent double interception
        Map<String, Object> contextData = invocationContext.getContextData();
        if (contextData.get(PAYARA_FAULT_TOLERANCE_INTERCEPTOR_EXECUTED) != null) {
            return false;
        }
        return true;
    }
}
```

**Purpose:** CDI interceptor that applies fault tolerance policies to annotated methods.

### FaultTolerancePolicy

```java
public abstract class Policy {

    protected final boolean isPresent;
    protected final FaultToleranceConfig config;

    public Policy(FaultToleranceConfig config) {
        this.isPresent = config != null && config.isEnabled();
        this.config = config;
    }

    public static Policy get(InvocationContext context,
                             Supplier<FaultToleranceConfig> config) {
        // Build ordered chain of policies
        Policy chain = new PolicyChain(config.get());

        // Add policies in reverse order (innermost first)
        chain = TimeoutPolicy.create(context, config, chain);
        chain = RetryPolicy.create(context, config, chain);
        chain = CircuitBreakerPolicy.create(context, config, chain);
        chain = BulkheadPolicy.create(context, config, chain);
        chain = AsynchronousPolicy.create(context, config, chain);
        chain = FallbackPolicy.create(context, config, chain);

        return chain;
    }

    public abstract Object proceed(InvocationContext context,
                                 Supplier<FaultToleranceMethodContext> methodContext)
            throws Exception;
}
```

**Purpose:** Base class for all fault tolerance policies, implements policy chain pattern.

### CircuitBreakerPolicy

```java
public final class CircuitBreakerPolicy extends Policy {

    private final Class<? extends Throwable>[] failOn;
    private final Class<? extends Throwable>[] skipOn;
    public final long delay;
    public final ChronoUnit delayUnit;
    public final int requestVolumeThreshold;
    public final double failureRatio;
    public final int successThreshold;

    public CircuitBreakerPolicy(Method annotatedMethod,
            Class<? extends Throwable>[] failOn,
            Class<? extends Throwable>[] skipOn,
            long delay, ChronoUnit delayUnit,
            int requestVolumeThreshold,
            double failureRatio,
            int successThreshold) {

        // Validation
        checkAtLeast(0, annotatedMethod, CircuitBreaker.class, "delay", delay);
        checkAtLeast(1, annotatedMethod, CircuitBreaker.class, "requestVolumeThreshold",
            requestVolumeThreshold);
        checkAtLeast(0d, annotatedMethod, CircuitBreaker.class, "failureRatio", failureRatio);
        checkAtMost(1.0d, annotatedMethod, CircuitBreaker.class, "failureRatio", failureRatio);
        checkAtLeast(1, annotatedMethod, CircuitBreaker.class, "successThreshold",
            successThreshold);

        this.failOn = failOn;
        this.skipOn = skipOn;
        this.delay = delay;
        this.delayUnit = delayUnit;
        this.requestVolumeThreshold = requestVolumeThreshold;
        this.failureRatio = failureRatio;
        this.successThreshold = successThreshold;
    }

    public boolean isFailure(Throwable ex) {
        // Determine if exception is a failure
        return !Policy.isCaught(ex, skipOn) && Policy.isCaught(ex, failOn);
    }

    public static CircuitBreakerPolicy create(InvocationContext context,
                                               FaultToleranceConfig config) {
        if (config.isAnnotationPresent(CircuitBreaker.class) &&
            config.isEnabled(CircuitBreaker.class)) {

            CircuitBreaker annotation = config.getAnnotation(CircuitBreaker.class);

            return new CircuitBreakerPolicy(context.getMethod(),
                config.failOn(annotation),
                config.skipOn(annotation),
                config.delay(annotation),
                config.delayUnit(annotation),
                config.requestVolumeThreshold(annotation),
                config.failureRatio(annotation),
                config.successThreshold(annotation)
            );
        }
        return null;
    }
}
```

**Purpose:** Circuit breaker policy that prevents cascading failures.

### CircuitBreakerState

```java
public class CircuitBreakerState {

    private enum State {
        CLOSED,    // Normal operation, requests pass through
        OPEN,      // Circuit is open, requests fail fast
        HALF_OPEN  // Transition state, testing if service recovered
    }

    private volatile State state = State.CLOSED;
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public boolean allowRequest() {
        if (state == State.CLOSED) {
            return true;
        } else if (state == State.OPEN) {
            if (hasDelayPassed()) {
                // Transition to HALF_OPEN to test recovery
                state = State.HALF_OPEN;
                consecutiveSuccesses.set(0);
                return true;
            }
            return false;
        } else { // HALF_OPEN
            return true;
        }
    }

    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            if (consecutiveSuccesses.incrementAndGet() >= successThreshold) {
                // Recovery confirmed, close circuit
                state = State.CLOSED;
                consecutiveFailures.set(0);
            }
        } else if (state == State.CLOSED) {
            consecutiveFailures.set(0);
        }
    }

    public void recordFailure() {
        if (state == State.HALF_OPEN) {
            // Recovery failed, reopen circuit
            state = State.OPEN;
            recordOpenTime();
        } else if (state == State.CLOSED) {
            if (consecutiveFailures.incrementAndGet() >= requestVolumeThreshold &&
                calculateFailureRatio() >= failureRatio) {
                // Threshold met, open circuit
                state = State.OPEN;
                recordOpenTime();
            }
        }
    }
}
```

**Purpose:** State machine for circuit breaker behavior.

## Usage Examples

### @Retry Annotation

```java
import org.eclipse.microprofile.faulttolerance.Retry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PaymentService {

    @Inject
    private PaymentGateway gateway;

    @Retry(
        maxRetries = 3,
        delay = 1000,
        delayUnit = ChronoUnit.MILLIS,
        maxDuration = 10000,
        retryOn = { PaymentTransientException.class },
        abortOn = { PaymentPermanentException.class },
        jitter = 0.2
    )
    public void processPayment(Payment payment) {
        // Retry up to 3 times with 1 second delay
        // Abort on permanent exceptions
        // Retry on transient exceptions
        gateway.charge(payment);
    }

    @Retry(
        maxRetries = -1,  // Retry indefinitely
        delay = 500,
        jitter = 0.1
    )
    public void connectToService() {
        // Keep retrying until success
        service.connect();
    }
}
```

### @Timeout Annotation

```java
import org.eclipse.microprofile.faulttolerance.Timeout;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class ExternalApiService {

    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public String fetchData(String query) {
        // Method must complete within 5 seconds
        return externalApiClient.query(query);
    }

    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    public void quickHealthCheck() {
        // Fast health check with 2 second timeout
        checkServiceHealth();
    }
}
```

### @CircuitBreaker Annotation

```java
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DatabaseService {

    @Inject
    private DataSource dataSource;

    @CircuitBreaker(
        requestVolumeThreshold = 10,
        failureRatio = 0.5,
        delay = 10000,
        delayUnit = ChronoUnit.MILLIS,
        successThreshold = 3,
        failOn = { SQLException.class },
        skipOn = { SQLWarning.class },
        fallbackMethod = "getCachedData"
    )
    public List<Data> queryData(String query) {
        // Circuit breaker opens if:
        // - 50% failure rate over 10 requests
        // - Then stays open for 10 seconds
        // - Requires 3 consecutive successes to close
        return executeQuery(query);
    }

    public List<Data> getCachedData(String query) {
        // Fallback method when circuit is open
        return cache.get(query);
    }
}
```

### @Bulkhead Annotation

```java
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class ImageProcessingService {

    @Bulkhead(
        value = 10,
        waitingTaskQueue = 5
    )
    public byte[] processImage(byte[] imageData) {
        // Limit concurrent executions to 10
        // Queue up to 5 additional requests
        // Beyond 15, BulkheadException is thrown
        return imageProcessor.process(imageData);
    }

    @Asynchronous
    @Bulkhead(value = 5)
    public CompletableFuture<byte[]> processImageAsync(byte[] imageData) {
        // Async bulkhead limits concurrent async executions
        return CompletableFuture.completedFuture(
            imageProcessor.process(imageData)
        );
    }
}
```

### @Fallback Annotation

```java
import org.eclipse.microprofile.faulttolerance.Fallback;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RecommendationService {

    @Inject
    private RecommendationEngine engine;

    @Inject
    private CachedRecommendations cache;

    @Fallback(fallbackMethod = "getCachedRecommendations")
    public List<Recommendation> getRecommendations(User user) {
        // If this fails, fallback to cached version
        return engine.generate(user);
    }

    public List<Recommendation> getCachedRecommendations(User user) {
        // Fallback method
        return cache.get(user);
    }

    @Fallback(FallbackHandler.class)
    public List<Recommendation> getRecommendationsWithHandler(User user) {
        // Use fallback handler
        return Collections.emptyList();
    }

    public static class FallbackHandler
            implements FallbackHandler<List<Recommendation>> {

        @Override
        public List<Recommendation> handle(ExecutionContext context) {
            return Collections.emptyList();
        }
    }
}
```

### Combined Policies

```java
import org.eclipse.microprofile.faulttolerance.*;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class RobustService {

    @Inject
    private ExternalServiceClient client;

    @Retry(
        maxRetries = 3,
        delay = 500,
        jitter = 0.2
    )
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @CircuitBreaker(
        requestVolumeThreshold = 20,
        failureRatio = 0.5,
        delay = 30000,
        successThreshold = 5
    )
    @Bulkhead(value = 15, waitingTaskQueue = 10)
    @Fallback(fallbackMethod = "getDefaultValue")
    public String fetchData(String id) {
        // Apply all fault tolerance policies:
        // 1. Retry up to 3 times if fails
        // 2. Timeout after 10 seconds
        // 3. Circuit breaker if 50% failure rate over 20 requests
        // 4. Limit to 15 concurrent executions
        // 5. Use fallback if all else fails
        return client.fetchData(id);
    }

    private String getDefaultValue(String id) {
        return "DEFAULT";
    }
}
```

### @Asynchronous Annotation

```java
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class AsyncService {

    @Asynchronous
    @Retry(maxRetries = 2)
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @Fallback(fallbackMethod = "handleAsyncFailure")
    public CompletableFuture<String> processAsync(String data) {
        // Async execution with fault tolerance
        return CompletableFuture.supplyAsync(() -> {
            return heavyProcessing(data);
        });
    }

    private CompletableFuture<String> handleAsyncFailure(String data) {
        return CompletableFuture.completedFuture("FALLBACK");
    }
}
```

## Configuration

### MicroProfile Config

```properties
# Global fault tolerance configuration
ft.com.fasterxml.jackson.JacksonObjectMapper=
ft.nonFallback.asynchronous.enabled=true
ft.nonFallback.circuitbreaker.delayInMillis=5000
ft.nonFallback.circuitbreaker.requestVolumeThreshold=20
ft.nonFallback.circuitbreaker.failureRatio=0.5
ft.nonFallback.circuitbreaker.successThreshold=5
```

### Admin Commands

```bash
# Enable fault tolerance
asadmin set-fault-tolerance-configuration --enabled=true

# Disable fault tolerance
asadmin set-fault-tolerance-configuration --enabled=false

# Get configuration
asadmin get-fault-tolerance-configuration
```

## Metrics Integration

Fault tolerance policies generate MicroProfile Metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `ft.invocations.total` | Counter | Total invocations |
| `ft.invocations.failed.total` | Counter | Failed invocations |
| `ft.invocations.succeeded.total` | Counter | Successful invocations |
| `ft.invocations.failed.total{exceptiontype=...}` | Counter | Failures by exception type |
| `ft.circuitbreaker.opened.total` | Counter | Circuit opened count |
| `ft.circuitbreaker.halfopen.total` | Counter | Circuit half-open count |
| `ft.circuitbreaker.closed.total` | Counter | Circuit closed count |
| `ft.retry.retries.total` | Counter | Total retry count |
| `ft.retry.retries.total{method=...}` | Counter | Retries by method |
| `ft.timeout.calls.total` | Counter | Timeout calls |
| `ft.timeout.executionDuration` | Timer | Timeout execution duration |
| `ft.bulkhead.concurrency.current` | Gauge | Current concurrency |
| `ft.bulkhead.waiting.current` | Gauge | Waiting queue size |
| `ft.bulkhead.rejectedCalls.total` | Counter | Rejected call count |

## Package Structure

```
microprofile/fault-tolerance/
└── src/main/java/fish/payara/microprofile/faulttolerance/
    ├── FaultToleranceService.java               # Service interface
    ├── FaultToleranceConfig.java                # Config interface
    ├── FaultToleranceMethodContext.java         # Method context
    ├── FaultToleranceMetrics.java                # Metrics
    ├── activation/
    │   ├── FaultToleranceApplicationContainer.java # Application lifecycle
    │   ├── FaultToleranceContainer.java          # Container
    │   ├── FaultToleranceDeployer.java           # Deployer
    │   └── FaultToleranceSniffer.java            # Sniffer
    ├── admin/
    │   ├── GetFaultToleranceConfigurationCommand.java # Admin command
    │   └── SetFaultToleranceConfigurationCommand.java # Admin command
    ├── cdi/
    │   ├── FaultToleranceExtension.java           # CDI extension
    │   └── FaultToleranceInterceptor.java         # Interceptor
    ├── policy/
    │   ├── Policy.java                           # Base policy
    │   ├── AsynchronousPolicy.java               # @Asynchronous policy
    │   ├── BulkheadPolicy.java                   # @Bulkhead policy
    │   ├── CircuitBreakerPolicy.java             # @CircuitBreaker policy
    │   ├── FallbackPolicy.java                   # @Fallback policy
    │   ├── RetryPolicy.java                      # @Retry policy
    │   ├── TimeoutPolicy.java                    # @Timeout policy
    │   ├── StaticAnalysisContext.java            # Annotation analysis
    │   └── MethodLookupUtils.java                # Method lookup
    ├── service/
    │   ├── FaultToleranceServiceImpl.java         # Service impl
    │   ├── FaultToleranceExecutionContext.java    # Execution context
    │   ├── FaultToleranceMethodContextImpl.java  # Method context impl
    │   ├── FaultToleranceRequestTracing.java     # Request tracing
    │   ├── MethodFaultToleranceMetrics.java      # Method metrics
    │   ├── MethodKey.java                        # Method key
    │   ├── Stereotypes.java                       # Stereotype utils
    │   └── BindableFaultToleranceConfig.java     # Config binding
    └── state/
        ├── CircuitBreakerState.java              # Circuit state
        └── StateTime.java                          # Time utilities
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `microprofile-faulttolerance-api` | MicroProfile Fault Tolerance API |
| `cdi-api` | CDI API |
| `microprofile/metrics` | Metrics integration |

## Notes

- **Policy Order** - Policies are applied in specific order for correct behavior
- **Timeout Position** - @Timeout is always inner-most (executed last)
- **Retry Position** - @Retry wraps @Timeout (retries include timeout)
- **Async with FT** - @Asynchronous works with all other policies
- **Metrics** - All policies generate MicroProfile Metrics automatically
- **Stereoype Support** - Fault tolerance annotations work with stereotypes
- **Method Context** - Each method gets its own execution context
- **Double Interception** - Flag prevents duplicate policy execution
- **Configuration Hierarchy** - Method-level config > class-level config > global config
