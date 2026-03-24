# CLAUDE.md - Appserver EJB

This file provides guidance for working with the `appserver/ejb` module - Jakarta EE EJB (Enterprise Java Beans) container implementation.

## Build Commands

```bash
# Build entire EJB module
mvn -DskipTests clean package -f appserver/ejb/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/ejb/<submodule>/pom.xml
```

## Module Overview

The EJB module provides the complete Jakarta EE EJB container implementation including session beans, message-driven beans, timers, and remoting. It supports EJB 3.2 specification with Payara-specific enhancements.

## Module Structure

| Submodule | Purpose |
|-----------|---------|
| `ejb-container` | Core EJB container implementation |
| `ejb-full-container` | Full profile EJB add-ons (MDB timers) |
| `ejb-client` | EJB client APIs |
| `ejb-connector` | EJB container connector/sniffer |
| `ejb-internal-api` | Internal EJB APIs |
| `ejb-http-remoting` | HTTP-based EJB remoting |
| `ejb-timer-databases` | Database-backed timer storage |
| `ejb-timer-service-app` | Timer service application |
| `ejb-opentracing` | OpenTracing integration |
| `ejb-all` | Aggregated EJB JAR |
| `*-l10n` | Localization resources |

## EJB Container Architecture

### Container Hierarchy

```
BaseContainer (Abstract)
        │
        ├── StatelessContainer (Stateless Session Beans)
        ├── StatefulSessionContainer (Stateful Session Beans)
        ├── SingletonContainer (Singleton Session Beans)
        ├── MessageBeanContainer (Message-Driven Beans)
        └── EntityContainer (Entity Beans - legacy)
```

### Container Types

| Container | EJB Type | Description |
|-----------|----------|-------------|
| `StatelessContainer` | @Stateless | Pooled stateless beans |
| `StatefulSessionContainer` | @Stateful | Stateful with passivation |
| `SingletonContainer` | @Singleton | Application-scoped singleton |
| `MessageBeanContainer` | @MessageDriven | JMS consumers |

### BaseContainer

Abstract base for all EJB containers:

```java
public abstract class BaseContainer implements Container, EjbContainerFacade {
    // Container type
    protected ContainerType containerType;

    // EJB descriptor
    protected EjbDescriptor ejbDescriptor;

    // Security manager
    protected SecurityManager securityManager;

    // Transaction manager
    protected JavaEETransactionManager txManager;

    // Interceptor manager
    protected InterceptorManager interceptorManager;

    // Class loader
    protected ClassLoader classLoader;

    // JNDI naming
    protected GlassfishNamingManager namingManager;

    // Core operations
    public abstract void initialize(EjbDescriptor descriptor);
    public abstract Object invoke(EjbInvocation inv);
    public abstract void destroy();
}
```

## EJB Invocation Flow

### Request Processing

```
Client Request
       │
   [EJB Proxy/Wrapper]
       │
   [Invocation Handler]
       │
   [PreInvoke Interceptors]
       │
┌──────▼─────────┐
│  BaseContainer │
│                 │
│ 1. Security     │
│ 2. Transaction │
│ 3. Invoke Bean │
│ 4. PostInvoke  │
└──────┬─────────┘
       │
   [PostInvoke Interceptors]
       │
   Response to Client
```

### EjbInvocation

Represents a single EJB method invocation:

```java
public class EjbInvocation {
    // EJB being invoked
    protected BaseContainer container;

    // Bean instance
    protected Object ejb;

    // Method being invoked
    protected Method method;

    // Invocation context
    protected Context ctx;

    // Transaction context
    protected Transaction transaction;

    // Security context
    protected Object callerPrincipal;

    // Invocation lifecycle
    public void invoke() throws Throwable;
    public void preInvoke();
    public void postInvoke();
}
```

## Session Beans

### Stateless Session Beans

```java
@Stateless
public class MyService {
    @PostConstruct
    public void init() { }

    @PreDestroy
    public void destroy() { }

    public String doWork() {
        return "work done";
    }
}
```

**Container:** `StatelessContainer`
- Pool-based instance management
- No client-specific state
- Thread-safe by design

### Stateful Session Beans

```java
@Stateful(passivationCapable = true)
public class ShoppingCart {
    private List<Item> items;

    public void addItem(Item item) {
        items.add(item);
    }

    @PrePassivate
    private void passivate() { }

    @PostActivate
    private void activate() { }

    @Remove
    public void checkout() { }
}
```

**Container:** `StatefulSessionContainer`
- One instance per client
- Passivation/activation support
- Session replication in clusters

### Singleton Session Beans

```java
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
public class CacheService {
    private Map<String, Object> cache = new ConcurrentHashMap<>();

    @Lock(LockType.WRITE)
    public void put(String key, Object value) {
        cache.put(key, value);
    }

    @Lock(LockType.READ)
    public Object get(String key) {
        return cache.get(key);
    }

    @Startup
    @PostConstruct
    public void init() {
        // Initialize on startup
    }
}
```

**Container:** `SingletonContainer`
- Single instance per application
- Concurrency management
- Startup/Shutdown callbacks

### Singleton Concurrency

| Strategy | Description |
|----------|-------------|
| `CONTAINER` | Container-managed locking with @Lock |
| `BEAN` | Bean-managed with synchronized |

| Lock Type | Description |
|-----------|-------------|
| `READ` | Multiple readers, exclusive writers |
| `WRITE` | Exclusive access |

## Message-Driven Beans

### MDB Architecture

```
JMS Provider
       │
   [Deliver Message]
       │
┌──────▼─────────────┐
│ MessageBeanContainer│
│                     │
│ 1. Get instance     │
│ 2. Set message ctx  │
│ 3. Call onMessage() │
│ 4. Return instance  │
└─────────────────────┘
```

### MDB Example

```java
@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup",
                              propertyValue = "jms/myQueue"),
    @ActivationConfigProperty(propertyName = "destinationType",
                              propertyValue = "jakarta.jms.Queue")
})
public class MyMessageBean implements MessageListener {
    @Override
    public void onMessage(Message message) {
        // Process message
    }

    @PostConstruct
    public void init() { }

    @PreDestroy
    public void cleanup() { }
}
```

**Container:** `MessageBeanContainer`
- Uses JCA WorkManager for message delivery
- Container-managed transactions
- Thread pool for concurrent processing

## EJB Timer Service

### Timer Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     EJBTimerService                          │
│  - Central timer controller                                  │
│  - One instance per VM                                       │
│  - Schedules timer expirations                               │
└─────────────────────────────────────────────────────────────┘
                              │
    ┌─────────────────────────┼─────────────────────────┐
    │                         │                         │
┌───▼────┐              ┌─────▼────┐          ┌───────▼──────┐
│ In-Memory│            │Persistent│         │ Clustered    │
│ Timers  │            │  Timers  │         │   Timers     │
│(default)│            │ (DB)     │         │ (Hazelcast)  │
└─────────┘            └──────────┘         └──────────────┘
```

### Programmatic Timers

```java
@Singleton
public class TimerExample {
    @Resource
    TimerService timerService;

    @Schedule(hour = "*", minute = "*/5")
    public void runEvery5Minutes() {
        // Automatic scheduled execution
    }

    public void createTimer() {
        // Create single-action timer
        timerService.createSingleActionTimer(
            new Date(System.currentTimeMillis() + 5000),
            new TimerConfig(null, false)
        );

        // Create interval timer
        timerService.createIntervalTimer(
            0,           // initial delay
            10000,       // interval (ms)
            new TimerConfig(null, false)
        );

        // Create calendar timer
        timerService.createCalendarTimer(
            new ScheduleExpression()
                .hour("*")
                .minute("*/15")
                .dayOfWeek("Mon-Fri"),
            new TimerConfig(null, false)
        );
    }

    @Timeout
    public void timeoutHandler(Timer timer) {
        // Handle timer expiration
    }
}
```

### Timer Storage Backends

| Backend | Module | Description |
|---------|--------|-------------|
| **In-Memory** | ejb-container | Default, non-persistent |
| **Database** | ejb-timer-databases | JDBC-backed persistent |
| **Hazelcast** | Payara-specific | Distributed cluster timers |

### Timer Configuration

```xml
<ejb-container>
    <ejb-timer-service>
        <timer-datasource>jdbc/__TimerPool</timer-datasource>
        <min-delivery-interval-millis>1000</min-delivery-interval-millis>
        <max-redeliveries>1</max-redeliveries>
        <redelivery-interval-millis>5000</redelivery-interval-millis>
    </ejb-timer-service>
</ejb-container>
```

## Interceptors

### Interceptor Chain

```
┌─────────────────────────────────────────────────────────────┐
│                      Method Call                             │
└─────────────────────────────────────────────────────────────┘
                              │
    ┌─────────────────────────┼─────────────────────────┐
    │                         │                         │
┌───▼──────┐          ┌──────▼──────┐          ┌──────▼──────┐
│ @Around  │          │ @Around     │          │ @Around      │
│ Construct│          │ Invoke      │          │ Timeout      │
│          │          │            │          │              │
┌───┴──────┐          ┌──┴──────────┐          ┌──┴──────────┐
│  Class   │    ───►  │   Method    │    ───►  │   Target     │
│Level     │          │   Level     │          │   Method     │
└──────────┘          └─────────────┘          └─────────────┘
```

### Interceptor Types

| Type | Annotation | Scope |
|------|------------|-------|
| Class-level | `@Interceptor` | All bean methods |
| Method-level | `@Interceptors` | Specific methods |
| CDI | `@AroundInvoke` | Around method invocation |
| Lifecycle | `@PostConstruct`, `@PreDestroy` | Lifecycle callbacks |
| Transaction | `@AroundTimeout` | Timer callbacks |

### Example Interceptor

```java
@Interceptor
public class LoggingInterceptor {
    @AroundInvoke
    public Object log(InvocationContext ctx) throws Exception {
        String method = ctx.getMethod().getName();
        System.out.println("Entering: " + method);

        try {
            Object result = ctx.proceed();
            System.out.println("Exiting: " + method);
            return result;
        } catch (Exception e) {
            System.out.println("Exception in: " + method);
            throw e;
        }
    }
}

@Interceptors(LoggingInterceptor.class)
@Stateless
public class MyService {
    public String doWork() {
        return "work";
    }
}
```

## EJB Security

### Security Model

```
Client Request
       │
   [Authentication]
       │
   [Authorization]
       │
   ├── @RolesAllowed
   ├── @PermitAll
   ├── @DenyAll
   └── @RunAs
       │
   [Method Invocation]
```

### Security Annotations

```java
@Stateless
@RolesAllowed("admin")
@DeclareRoles({"admin", "user"})
public class SecureService {

    @RolesAllowed("admin")
    public void adminOnly() {
        // Only admins can call
    }

    @PermitAll
    public String getPublicInfo() {
        // Anyone can call
    }

    @DenyAll
    public void internal() {
        // No one can call directly
    }

    @RunAs("supervisor")
    public void runAsSupervisor() {
        // Executes as "supervisor" role
    }
}
```

## EJB Transactions

### Transaction Attributes

```java
@Stateless
public class TransactionalService {

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void defaultTx() {
        // Use existing or create new
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void newTx() {
        // Always create new
    }

    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public void mustHaveTx() {
        // Must have existing
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public void supportTx() {
        // Use existing if present
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void noTx() {
        // Suspend existing, no tx
    }

    @TransactionAttribute(TransactionAttributeType.NEVER)
    public void neverTx() {
        // Throw if tx exists
    }
}
```

## EJB Remoting

### Remoting Options

| Protocol | Module | Description |
|----------|--------|-------------|
| **IIOP** | orb-connector | CORBA/IIOP (legacy) |
| **HTTP** | ejb-http-remoting | HTTP-based (Payara) |

### HTTP Remoting (Payara)

```
Client Application
       │
   [HTTP POST /ejb/invoker]
       │
┌──────▼──────────────────┐
│  HTTP Endpoint          │
│  (ejb-http-remoting)    │
└──────┬──────────────────┘
       │
   [Deserialize Request]
       │
┌──────▼──────────────────┐
│  EJB Container          │
│  - Invoke Method        │
│  - Get Result           │
└──────┬──────────────────┘
       │
   [Serialize Response]
       │
   [HTTP Response]
```

### HTTP Remoting Configuration

```xml
<ejb-http-remoting>
    <enabled>true</enabled>
    <client-mapping>
        <context-root>/ejb-invoker</context-root>
    </client-mapping>
</ejb-http-remoting>
```

## EJB Context and Injection

### EJBContext

```java
@Stateless
public class ContextExample {
    @Resource
    private SessionContext context;

    public void example() {
        // Transaction info
        boolean rollback = context.getRollbackOnly();

        // Security info
        Principal caller = context.getCallerPrincipal();
        boolean isAdmin = context.isCallerInRole("admin");

        // Timer service
        TimerService timer = context.getTimerService();

        // User transaction
        UserTransaction utx = context.getUserTransaction();

        // Lookup
        DataSource ds = (DataSource) context.lookup("jdbc/myDB");
    }
}
```

### Resource Injection

```java
@Stateless
public class InjectionExample {
    // EJB reference
    @EJB
    private OtherService otherService;

    // JPA
    @PersistenceContext
    private EntityManager em;

    @PersistenceUnit
    private EntityManagerFactory emf;

    // DataSource
    @Resource(lookup = "jdbc/__default")
    private DataSource ds;

    // JMS
    @Resource(lookup = "jms/myQueue")
    private Queue queue;

    @Resource
    private JMSContext jmsContext;

    // Environment
    @Resource(name = "maxRetries")
    private int maxRetries;

    // Async
    @Asynchronous
    public Future<String> asyncWork() {
        // ...
        return new AsyncResult<String>("done");
    }
}
```

## Package Structure

### ejb-container

```
com/sun/ejb/
├── Container.java                       # Container interface
├── ComponentContext.java                # Component context
├── EjbInvocation.java                   # EJB invocation
├── InvocationInfo.java                  # Invocation metadata
├── containers/
│   ├── BaseContainer.java               # Abstract container base
│   ├── StatelessContainer.java          # Stateless container
│   ├── StatefulSessionContainer.java    # Stateful container
│   ├── SingletonContainer.java          # Singleton container
│   ├── MessageBeanContainer.java        # MDB container
│   ├── EJBTimerService.java             # Timer service
│   ├── EJBContextImpl.java              # EJBContext impl
│   ├── interceptors/                    # Interceptor framework
│   │   ├── InterceptorManager.java
│   │   ├── InterceptorInvocationHandler.java
│   │   └── CallbackChainImpl.java
│   └── util/
│       └── MethodMap.java               # Method metadata
└── codegen/                             # Code generation
    ├── RemoteGenerator.java             # Remote stubs
    ├── ServiceInterfaceGenerator.java   # Service interfaces
    └── EjbOptionalIntfGenerator.java    # Optional interfaces
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.ejb-api` | EJB API |
| `jakarta.jms-api` | JMS API (for MDBs) |
| `deployment/dol` | EJB descriptor parsing |
| `transaction-internal-api` | Transaction management |
| `security-ee` | Security integration |
| `orb-connector` | IIOP remoting |
| `monitoring-core` | Monitoring support |
| `opentracing-adapter` | Distributed tracing |

## EJB Configuration

### EJB Container Settings

```xml
<ejb-container>
    <!-- Session beans -->
    <session-store>${com.sun.aas.instanceRoot}/session-store</session-store>

    <!-- Pool settings -->
    <max-pool-size>64</max-pool-size>
    <max-wait-time-in-millis>60000</max-wait-time-in-millis>
    <pool-idle-timeout-in-seconds>600</pool-idle-timeout-in-seconds>

    <!-- Cache settings -->
    <cache-idle-timeout-in-seconds>600</cache-idle-timeout-in-seconds>
    <cache-resize-quantity>16</cache-resize-quantity>
    <max-cache-size>512</max-cache-size>
    <removal-timeout-in-seconds>3600</removal-timeout-in-seconds>

    <!-- Timer service -->
    <ejb-timer-service>
        <timer-datasource>jdbc/__TimerPool</timer-datasource>
        <min-delivery-interval-millis>1000</min-delivery-interval-millis>
        <max-redeliveries>1</max-redeliveries>
        <redelivery-interval-millis>5000</redelivery-interval-millis>
    </ejb-timer-service>
</ejb-container>
```

## Monitoring

### Probe Providers

- `EjbMonitoringProbeProvider` - EJB method invocation
- `EjbCacheProbeProvider` - Bean instance caching
- `EjbTimedObjectProbeProvider` - Timer events
- `EjbPoolStatsProvider` - Pool statistics

### Statistics

| Statistic | Description |
|-----------|-------------|
| `MethodReadyCount` | Beans ready for use |
| `MethodInvokedCount` | Total invocations |
| `MethodInvocationTime` | Invocation duration |
| `CacheHitCount` | Cache hits |
| `CacheMissCount` | Cache misses |

## Related Modules

- `deployment/dol` - EJB descriptor parsing
- `transaction` | Transaction management
- `connectors` - JCA integration for MDBs
- `nucleus/security` - Security integration
- `payara-appserver-modules/hazelcast-ejb-timer` - Distributed timers
