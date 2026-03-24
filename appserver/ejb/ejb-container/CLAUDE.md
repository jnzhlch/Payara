# CLAUDE.md - EJB Container

This file provides guidance for working with the `ejb-container` module - Core EJB container implementation.

## Module Overview

The ejb-container module provides the core EJB 3.2 container implementation for session beans, message-driven beans, and the EJB timer service.

## Build Commands

```bash
# Build ejb-container module
mvn -DskipTests clean package -f appserver/ejb/ejb-container/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Name**: "GlassFish Core EJB container implementation"

## Core Container Architecture

### BaseContainer

Abstract base class for all EJB containers:

```java
public abstract class BaseContainer implements Container, EjbContainerFacade {
    // Container types
    public enum ContainerType {
        STATELESS, STATEFUL, SINGLETON, MESSAGE_DRIVEN, ENTITY, READ_ONLY
    }

    // Key fields
    protected EjbDescriptor ejbDescriptor;
    protected SecurityManager securityManager;
    protected JavaEETransactionManager txManager;
    protected InterceptorManager interceptorManager;
    protected GlassfishNamingManager namingManager;

    // Core lifecycle
    public abstract void initialize(EjbDescriptor descriptor);
    public abstract Object invoke(EjbInvocation inv);
    public abstract void destroy();

    // Hook points
    protected abstract void postConstruct();
    protected abstract void preDestroy();
}
```

### Container Factory

```java
@Service
public class BaseContainerFactory {
    // Create appropriate container for EJB type
    public Container createContainer(EjbDescriptor descriptor) {
        switch (descriptor.getType()) {
            case SESSION:
                if (descriptor.isSingleton()) {
                    return new SingletonContainer();
                } else if (descriptor.isStateless()) {
                    return new StatelessContainer();
                } else {
                    return new StatefulSessionContainer();
                }
            case MESSAGE_DRIVEN:
                return new MessageBeanContainer();
            default:
                throw new IllegalArgumentException();
        }
    }
}
```

## Container Implementations

### StatelessContainer

Stateless session bean container:

```java
public class StatelessContainer extends BaseContainer {
    // Bean pool
    protected EjbPool pool;

    // Create pool on initialization
    @Override
    public void initialize(EjbDescriptor descriptor) {
        // Create bean pool
        pool = new EjbPool(getMaxPoolSize(), getPoolIdleTimeout());
    }

    @Override
    public Object invoke(EjbInvocation inv) {
        // Get bean from pool
        Object ejb = pool.get();

        try {
            // Invoke method
            return invokeMethod(ejb, inv);
        } finally {
            // Return bean to pool
            pool.release(ejb);
        }
    }
}
```

### StatefulSessionContainer

Stateful session bean container:

```java
public class StatefulSessionContainer extends BaseContainer {
    // Session cache
    private SessionCache sessionCache;

    // Passivation
    private PassivationManager passivationManager;

    // Create or retrieve session
    @Override
    public Object invoke(EjbInvocation inv) {
        // Get session from cache
        SessionContext ctx = sessionCache.get(inv.sessionKey);

        if (ctx == null) {
            ctx = createNewSession();
            sessionCache.put(inv.sessionKey, ctx);
        }

        // Invoke on session
        return invokeMethod(ctx.getBean(), inv);
    }

    // Passivation support
    public void passivate(SessionContext ctx) {
        passivationManager.passivate(ctx);
    }

    public void activate(SessionContext ctx) {
        passivationManager.activate(ctx);
    }
}
```

### SingletonContainer

Singleton session bean container:

```java
public class SingletonContainer extends BaseContainer {
    // Single instance
    private Object singletonInstance;

    // Concurrency
    private LockManager lockManager;

    // Cluster singleton
    private ClusteredSingletonLookup clusterLookup;

    @Override
    public void initialize(EjbDescriptor descriptor) {
        // Create singleton instance
        singletonInstance = createBean();

        // Initialize concurrency management
        lockManager = new LockManager(descriptor.getConcurrencyMode());

        // Handle cluster singleton
        if (descriptor.isClustered()) {
            clusterLookup = new ClusteredSingletonLookup();
        }

        // Call @PostConstruct
        postConstruct(singletonInstance);
    }

    @Override
    public Object invoke(EjbInvocation inv) {
        // Acquire lock if @Lock annotation
        if (inv.method.isLocked()) {
            lockManager.acquireLock(inv);
        }

        try {
            return invokeMethod(singletonInstance, inv);
        } finally {
            if (inv.method.isLocked()) {
                lockManager.releaseLock(inv);
            }
        }
    }
}
```

### MessageBeanContainer

Message-driven bean container:

```java
public class MessageBeanContainer extends BaseContainer {
    // JCA endpoint
    private MessageEndpointFactory endpointFactory;

    // Delivery pool
    private EjbThreadPoolExecutor deliveryPool;

    @Override
    public void initialize(EjbDescriptor descriptor) {
        // Create delivery pool
        deliveryPool = new EjbThreadPoolExecutor(
            descriptor.getMaxPoolSize(),
            descriptor.getCorePoolSize()
        );

        // Register with JCA resource adapter
        endpointFactory = new MessageEndpointFactory() {
            @Override
            public MessageEndpoint createEndpoint() {
                // Create bean instance
                Object ejb = createBean();

                // Create endpoint wrapper
                return new MessageEndpointWrapper(ejb, MessageBeanContainer.this);
            }
        };

        // Activate endpoint
        activateEndpoint(endpointFactory);
    }

    // Deliver message
    public void deliverMessage(Message message) {
        deliveryPool.submit(() -> {
            MessageEndpoint endpoint = endpointFactory.createEndpoint();
            endpoint.deliverMessage(message);
        });
    }
}
```

## EJB Invocation

### EjbInvocation

Represents a single EJB method invocation:

```java
public class EjbInvocation {
    // Container
    protected BaseContainer container;

    // Bean instance
    protected Object ejb;

    // Method info
    protected Method method;
    protected InvocationInfo invInfo;

    // Transaction
    protected Transaction transaction;
    protected boolean txAttr = false;

    // Security
    protected Object callerPrincipal;
    protected MethodPermission methodPermission;

    // Context
    protected Context context;
    protected EJBContextImpl ejbContext;

    // Invocation lifecycle
    public void invoke() throws Throwable {
        try {
            preInvoke();
            container.invoke(this);
        } finally {
            postInvoke();
        }
    }

    private void preInvoke() throws Throwable {
        // Check security
        container.authorize(this);

        // Begin transaction
        container.preInvokeTx(this);

        // Set context
        container.setEJBContext(this);
    }

    private void postInvoke() {
        // Complete transaction
        container.postInvokeTx(this);

        // Clear context
        container.clearEJBContext(this);
    }
}
```

### InvocationInfo

Metadata for method invocation:

```java
public class InvocationInfo {
    // Method info
    protected Method method;
    protected String methodName;

    // Transaction attribute
    protected int txAttr;

    // Security
    protected MethodPermission methodPermission;

    // Business interface
    protected Class<?> businessInterface;

    // Interceptors
    protected List<InterceptorDescriptor> classInterceptors;
    protected List<InterceptorDescriptor> methodInterceptors;

    // AroundInvoke/AroundTimeout chains
    protected List<AroundInvokeDescriptor> aroundInvokeChain;
    protected List<AroundTimeoutDescriptor> aroundTimeoutChain;

    // Async method
    protected boolean isAsync;

    // @Remote @Local views
    protected boolean isRemote;
    protected boolean isLocal;
}
```

## Interceptor Framework

### InterceptorManager

Manages interceptor chains:

```java
public class InterceptorManager {
    // Class-level interceptors
    private List<InterceptorDescriptor> classInterceptors;

    // Method-level interceptors
    private Map<Method, List<InterceptorDescriptor>> methodInterceptors;

    // Build interceptor chain
    public InterceptorChain createAroundInvokeChain(
            EjbInvocation inv) {

        InterceptorChain chain = new InterceptorChain();

        // Add class-level interceptors
        for (InterceptorDescriptor interceptor : classInterceptors) {
            chain.addInterceptor(interceptor);
        }

        // Add method-level interceptors
        List<InterceptorDescriptor> methodInts = methodInterceptors.get(inv.method);
        if (methodInts != null) {
            for (InterceptorDescriptor interceptor : methodInts) {
                chain.addInterceptor(interceptor);
            }
        }

        // Add bean method
        chain.addBeanMethod(inv.ejb, inv.method);

        return chain;
    }

    // Create callback chain
    public CallbackChain createCallbackChain(
            CallbackType type,
            Object bean) {

        CallbackChain chain = new CallbackChain();

        // Add lifecycle interceptors
        for (InterceptorDescriptor interceptor : classInterceptors) {
            chain.addCallback(interceptor, type);
        }

        // Add bean callback
        chain.addBeanCallback(bean, type);

        return chain;
    }
}
```

### Interceptor Invocation

```java
public class InterceptorInvocationHandler {
    public Object proceed(InvocationContext context) throws Exception {
        // Get next interceptor in chain
        InterceptorNode node = context.getNext();

        if (node == null) {
            // Call target method
            return context.getTargetMethod().invoke(
                context.getTarget(),
                context.getParameters()
            );
        } else {
            // Call interceptor
            return node.getAroundInvoke().invoke(context);
        }
    }
}
```

## EJB Timer Service

### EJBTimerService

Central timer service controller:

```java
public abstract class EJBTimerService {
    // Timer cache
    protected TimerCache timerCache_;

    // Owner ID for this server
    protected String ownerIdOfThisServer_;

    // Timer states
    public static final int STATE_ACTIVE = 0;
    public static final int STATE_CANCELLED = 1;

    // Create timer
    public Timer createTimer(TimerConfig timerConfig) {
        // Generate unique timer ID
        String timerId = generateTimerId();

        // Create timer
        EJBTimer timer = new EJBTimer(timerId, ownerIdOfThisServer_);

        // Schedule
        schedule(timer, timerConfig);

        // Cache
        timerCache_.add(timer);

        return timer;
    }

    // Schedule expiration
    protected void schedule(EJBTimer timer, TimerConfig config) {
        if (config.isPersistent()) {
            // Persistent scheduling
            schedulePersistent(timer);
        } else {
            // In-memory scheduling
            scheduleInMemory(timer);
        }
    }

    // Handle expiration
    public void timerExpired(EJBTimer timer) {
        // Get bean
        Object bean = getTimedObject(timer.getTimedObjectId());

        // Create invocation
        EjbInvocation inv = createInvocation(bean, timer.getMethod());

        // Invoke
        try {
            inv.invoke();
        } catch (Throwable t) {
            handleException(t, timer);
        }

        // Reschedule if interval timer
        if (timer.isIntervalTimer()) {
            scheduleNextExpiration(timer);
        }
    }
}
```

### Timer Storage Backends

#### In-Memory Timer

```java
public class NonPersistentEJBTimerService extends EJBTimerService {
    // In-memory timer storage
    private Map<String, EJBTimer> timers;

    // Uses JVM timer
    private java.util.Timer jvmTimer;

    @Override
    protected void scheduleInMemory(EJBTimer timer) {
        jvmTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                timerExpired(timer);
            }
        }, timer.getExpirationTime());
    }
}
```

#### Database-Backed Timer

```java
public class PersistentTimerService extends EJBTimerService {
    // DataSource
    private DataSource timerDataSource;

    @Override
    protected void schedulePersistent(EJBTimer timer) {
        // Save to database
        saveTimerToDB(timer);

        // Schedule memory-based reminder
        scheduleInMemory(timer);
    }

    private void saveTimerToDB(EJBTimer timer) {
        // Insert/update TIMERS table
        try (Connection conn = timerDataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO timers (timer_id, owner_id, expiration, ...) " +
                "VALUES (?, ?, ?, ...)"
            );
            stmt.setString(1, timer.getId());
            stmt.setString(2, ownerIdOfThisServer_);
            stmt.setTimestamp(3, timer.getExpiration());
            // ...
            stmt.execute();
        }
    }

    // Restore timers on startup
    public void restoreTimers() {
        try (Connection conn = timerDataSource.getConnection()) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                "SELECT * FROM timers WHERE owner_id = ?"
            );
            while (rs.next()) {
                EJBTimer timer = createTimerFromResultSet(rs);
                timerCache_.add(timer);
                scheduleInMemory(timer);
            }
        }
    }
}
```

## EJB Context

### EJBContextImpl

Implementation of EJBContext:

```java
public class EJBContextImpl implements EJBContext {
    // Container
    protected BaseContainer container;

    // Bean instance
    protected Object ejb;

    // Transaction
    protected Transaction transaction;

    // JNDI environment
    protected Context namingProxy;

    @Override
    public Object lookup(String name) {
        try {
            return namingProxy.lookup("java:comp/env/" + name);
        } catch (NamingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public UserTransaction getUserTransaction() {
        return container.getUserTransaction();
    }

    @Override
    public TimerService getTimerService() {
        return container.getTimerService();
    }

    @Override
    public Principal getCallerPrincipal() {
        return container.getCallerPrincipal();
    }

    @Override
    public boolean isCallerInRole(String role) {
        return container.isCallerInRole(role);
    }

    @Override
    public void setRollbackOnly() {
        transaction.setRollbackOnly();
    }

    @Override
    public boolean getRollbackOnly() {
        return transaction.getStatus() == Status.STATUS_MARKED_ROLLBACK;
    }
}
```

## Code Generation

### Remote Stub Generation

```java
public class RemoteGenerator {
    public void generateRemoteInterfaces(EjbDescriptor descriptor) {
        // Generate remote business interface wrapper
        Class<?> remoteIntf = descriptor.getRemoteInterface();

        // Generate client-side stub
        ClassGenerator generator = new ClassGenerator();
        generator.generateRemoteWrapper(remoteIntf);
    }
}
```

### Service Interface Generation

```java
public class ServiceInterfaceGenerator {
    public void generateServiceInterface(EjbDescriptor descriptor) {
        // Generate jakarta.ejb.Remote interface
        // or jakarta.ejb.Local interface
        // based on bean configuration
    }
}
```

## Package Structure

```
com/sun/ejb/
├── Container.java                       # Container interface
├── ComponentContext.java                # Component context
├── EjbInvocation.java                   # EJB invocation
├── InvocationInfo.java                  # Invocation metadata
├── MethodLockInfo.java                  # Concurrency lock info
├── EJBUtils.java                        # Utilities
├── PersistentTimerService.java          # Persistent timer service
├── codegen/                             # Code generation
│   ├── RemoteGenerator.java
│   ├── ServiceInterfaceGenerator.java
│   └── EjbOptionalIntfGenerator.java
└── containers/
    ├── BaseContainer.java               # Abstract base
    ├── BaseContainerFactory.java        # Factory
    ├── StatelessContainer.java          # Stateless container
    ├── StatefulSessionContainer.java    # Stateful container
    ├── StatefulContainerFactory.java    # Stateful factory
    ├── SingletonContainer.java          # Singleton container
    ├── SingletonContainerFactory.java    # Singleton factory
    ├── MessageBeanContainer.java        # MDB container
    ├── EJBTimerService.java             # Timer service
    ├── NonPersistentEJBTimerService.java # In-memory timers
    ├── EJBContextImpl.java              # EJBContext impl
    ├── SessionContextImpl.java          # SessionContext impl
    ├── SingletonContextImpl.java        # SingletonContext impl
    ├── interceptors/                    # Interceptor framework
    │   ├── InterceptorManager.java
    │   ├── InterceptorInvocationHandler.java
    │   ├── AroundInvokeInvocationContext.java
    │   └── CallbackChainImpl.java
    └── util/
        └── MethodMap.java               # Method metadata
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.ejb-api` | EJB API |
| `deployment/dol` | EJB descriptors |
| `transaction-internal-api` | Transactions |
| `security-ee` | Security |
| `internal-api` | Internal APIs |
| `kernel` | Container framework |
| `monitoring-core` | Monitoring |
| `orb-connector` | IIOP remoting |

## Related Modules

- `ejb-full-container` - Full profile add-ons
- `ejb-client` - Client APIs
- `ejb-timer-databases` - Database timers
- `ejb-http-remoting` - HTTP remoting
