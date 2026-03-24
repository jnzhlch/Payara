# CLAUDE.md - Container Common

This file provides guidance for working with the `container-common` module - shared infrastructure for Jakarta EE containers.

## Module Overview

The container-common module provides services shared across all Jakarta EE containers (Web, EJB, JPA), including dependency injection, interceptor chains, and component lifecycle management.

## Build Commands

```bash
# Build container-common
mvn -DskipTests clean package -f appserver/common/container-common/pom.xml
```

## Core Services

### InjectionManager

Handles dependency injection for all containers:

```java
@Inject
private InjectionManager injectionMgr;

// Inject resources into an instance
injectionMgr.injectInstance(instance, componentId);

// Inject with a specific class loader
injectionMgr.injectInstance(instance, componentId, classLoader);
```

**Injection Types:**
- `@Resource` - Resource references
- `@EJB` - EJB references
- `@PersistenceContext` - JPA EntityManagers
- `@PersistenceUnit` - EntityManagerFactories
- `@DataSourceDefinition` - JDBC data sources
- `@ManagedBean` - Managed bean references

### ComponentEnvManager

Manages java:comp/env namespace:

```java
ComponentEnvManager envMgr = // get service

// Bind to component environment
envMgr.bindToComponentNamespace(binding, componentId);

// Resolve JNDI name in component scope
Object resolved = envMgr.resolve(componentId, "java:comp/env/foo");
```

**Namespaces:**
- `java:comp/env` - Component scope
- `java:module` - Module scope
- `java:app` - Application scope

### Interceptor System

Container-agnostic interceptor framework:

```java
JavaEEInterceptorBuilder builder = // create builder
InterceptorInvoker invoker = builder.buildInvoker(targetClass, interceptorInfo);

// Invoke with interceptor chain
Object result =Invoker.invoke(target, method, args);
```

**Interceptor Types:**
- `@AroundInvoke` - Business method interception
- `@AroundTimeout` - Timer method interception
- `@PostConstruct` - After construction callback
- `@PreDestroy` - Before destruction callback
- `@PrePassivate` / `@PostActivate` - Stateful EJB passivation

### ManagedBeanManager

Lifecycle management for @ManagedBean:

```java
ManagedBeanManager mbMgr = // get service

// Register managed bean
mbMgr.registerManagedBean(ManagedBeanDescriptor);

// Create managed bean instance
Object bean = mbMgr.createManagedBean(className, componentName);
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Jakarta EE Container                     │
│  (Web / EJB / JPA)                                           │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Container Common Services                  │
├─────────────────────────────────────────────────────────────┤
│  InjectionManager  │  ComponentEnvManager  │  Interceptors  │
│  - @Inject         │  - java:comp/env      │  - Chains       │
│  - @Resource       │  - java:module        │  - Callbacks    │
│  - @EJB            │  - java:app           │                │
│  - JPA             │                       │                │
├─────────────────────────────────────────────────────────────┤
│  ManagedBeanManager │  EntityManagerWrapper │  CallFlow     │
├─────────────────────────────────────────────────────────────┤
│  JavaEEIOUtils     │  Naming Utilities     │  HA Support    │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│              Naming / Security / Transaction                 │
└─────────────────────────────────────────────────────────────┘
```

## JPA Integration

### EntityManager Wrappers

Proxies JPA EntityManagers for container integration:

| Wrapper | Purpose |
|---------|---------|
| `EntityManagerWrapper` | Container-managed EntityManager |
| `EntityManagerFactoryWrapper` - Factory proxy |
| `PhysicalEntityManagerWrapper` | Physical EntityManager access |
| `QueryWrapper` / `TypedQueryWrapper` | Query proxying |

```java
// Extended persistence context
EntityManagerWrapper wrapper = new EntityManagerWrapper(physicalEM);
wrapper.setComponentId(componentId);
```

## Interceptor Chains

### InterceptorInfo

Metadata for interceptor configuration:

```java
InterceptorInfo info = new InterceptorInfo();
info.setTargetClass(targetClass);
info.setInterceptorClasses(interceptorClasses);
info.setAroundInvokeMethods(methods);
```

### InterceptorInvoker

Executes interceptor chains:

```
Client Call
     ↓
Interceptor 1 (AroundInvoke)
     ↓
Interceptor 2 (AroundInvoke)
     ↓
...
     ↓
Target Method
     ↓
Interceptor 2 (return)
     ↓
Interceptor 1 (return)
     ↓
Return to Client
```

## Resource Proxies

Lazy resource initialization:

```java
CommonResourceProxy proxy = new CommonResourceProxy(jndiName);
// Lookups occur on first access, not during injection
```

**Benefits:**
- Deferred JNDI lookups
- Avoid circular dependencies
- Reduce startup time

## JavaEE IO Utils

Custom serialization for container-managed objects:

```java
JavaEEIOUtils ioUtils = // get service
JavaEEObjectOutputStream out = ioUtils.createObjectOutputStream(stream);
// Handles JNDI contexts, EntityManager references, etc.
```

**Supported Types:**
- JNDI Contexts
- EntityManager references
- EJB references
- UserTransaction

## High Availability

### HA Cookie Management

State tracking for session replication:

```java
HACookieInfo cookie = HACookieManager.createCookieInfo();
String cookieValue = cookie.getEncodedCookie();
```

### Replica Prediction

Determines target instance for requests:

```java
HAReplicaPredictor predictor = // get service
InstanceInfo replica = predictor.getReplica(cookie);
```

## Package Structure

```
com.sun.enterprise.container.common/
├── spi/
│   ├── JavaEEContainer.java             # Container SPI
│   ├── JavaEEInterceptorBuilder.java    # Interceptor builder
│   ├── InjectionManager.java            # Injection SPI
│   ├── ComponentEnvManager.java         # JNDI env manager
│   ├── ManagedBeanManager.java          # Managed bean SPI
│   └── util/
│       ├── JavaEEIOUtils.java           # Custom serialization
│       └── InterceptorInfo.java         # Interceptor metadata
├── impl/
│   ├── InjectionManagerImpl.java
│   ├── ComponentEnvManagerImpl.java
│   ├── ManagedBeanManagerImpl.java
│   ├── EntityManagerWrapper.java        # JPA wrappers
│   └── managedbean/
│       └── ManagedBeanManagerImpl.java
└── util/
    ├── ClusteredSingletonLookupImplBase.java
    ├── ComponentNamingUtilImpl.java
    └── JavaEEIOUtilsImpl.java

org.glassfish.javaee.services/
├── CommonResourceProxy.java             # Lazy resource proxy
└── JMSCFResourcePMProxy.java             # JMS proxy

org.glassfish.ha.common/
├── HACookieInfo.java
├── HACookieManager.java
└── HAReplicaPredictor.java
```

## Container Usage

### Web Container

```java
// Servlet injection
@Inject InjectionManager injectionMgr;
public void init() {
    injectionMgr.injectInstance(this, servletComponentId);
}
```

### EJB Container

```java
// EJB with interceptors
JavaEEInterceptorBuilder builder = ...
InterceptorInvoker invoker = builder.buildInvoker(ejbClass, interceptorInfo);
// EJB methods invoked through invoker
```

### JPA Container

```java
// EntityManager injection
EntityManagerWrapper emWrapper = ...
emWrapper.setComponentId(componentId);
injectionMgr.injectInstance(emWrapper, componentId);
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-naming` | JNDI integration |
| `glassfish-api` | Public APIs |
| `internal-api` | Internal APIs |
| `dol` | Deployment descriptors |
| `security` | Security integration |
| `hazelcast-bootstrap` | HA support (optional) |
| `soteria` | Jakarta Security |

## Related Modules

- `appserver/web` - Web container (uses injection, interceptors)
- `appserver/ejb` - EJB container (uses interceptors, JPA)
- `appserver/persistence` - JPA (uses EntityManager wrappers)
- `nucleus/security` - Security context propagation
