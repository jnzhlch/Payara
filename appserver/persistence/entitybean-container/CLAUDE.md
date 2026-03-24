# CLAUDE.md - Entity Bean Container

This file provides guidance for working with the `persistence/entitybean-container` module - EJB 2.x Entity Bean container implementation.

## Module Overview

The entitybean-container module provides the EJB 2.x Entity Bean container for Payara Server. This is a legacy technology (superseded by JPA), but still supported for backward compatibility.

**Key Features:**
- **EJB 2.x Entity Beans** - Container-managed persistence for entity beans
- **Bean Lifecycle** - ejbCreate, ejbActivate, ejbPassivate, ejbRemove
- **Pool Management** - Entity bean instance pooling
- **Cache Support** - EJBObjectCache for entity instances
- **Read-Only Beans** - Optimized beans that don't modify state
- **Clustering** - Passivation/activation for distributed caches

## Build Commands

```bash
# Build entitybean-container module
mvn -DskipTests clean package -f appserver/persistence/entitybean-container/pom.xml

# Run tests
mvn test -f appserver/persistence/entitybean-container/pom.xml
```

## Core Components

### EntityContainer

```java
public class EntityContainer extends BaseContainer {

    @Override
    protected void instantiateEJB() throws Exception {
        // Create new entity bean instance
        ejb = createEJBInstance();
    }

    @Override
    protected void initializeEJB() throws Exception {
        // Initialize entity bean state from database
        EntityContextImpl ctx = (EntityContextImpl) context;
        ctx.setEJBObject(createEJBObject());

        // For container-managed persistence, call ejbLoad
        if (isContainerManaged()) {
            callEjbLoad(ctx);
        }
    }

    // Passivation for clustering
    protected void passivateEJB(EntityContextImpl ctx) {
        // Serialize bean state for replication
        // Called before bean is removed from cache
    }

    protected EntityContextImpl activateEJB(Object primaryKey) {
        // Restore bean from passivated state
        // Or call ejbLoad to refresh from database
    }

    @Override
    public void invoke(Transaction tx, Method method, Object[] args) throws Throwable {
        // Handle findByPrimaryKey, getPrimaryKey, ejbSelect, etc.
    }
}
```

### EntityContextImpl

Entity bean context:

```java
public class EntityContextImpl extends EJBContextImpl {

    private EJBLocalRemoteObject ejbObject;    // EJBObject/EJBLocalObject
    private Object primaryKey;                   // Primary key
    private boolean isDirty;                     // Modified flag

    @Override
    public Object getPrimaryKey() {
        return primaryKey;
    }

    @Override
    public EntityContextImpl getEntityContext() {
        return this;
    }
}
```

### ReadOnlyBeanContainer

Optimized container for read-only entity beans:

```java
public class ReadOnlyBeanContainer extends EntityContainer {

    @Override
    protected boolean isReadOnlyBean() {
        return true;
    }

    @Override
    public void refreshAllReadOnlyBeans() {
        // Refresh all read-only beans
        notifyReadOnlyBeanNotifiers();
    }

    // Read-only beans skip:
    // - ejbStore (no writes)
    // - Dirty checking
    // - Transaction synchronization for updates
}
```

## Bean Caching

### EJBObjectCache

Entity bean instance caching:

```java
public interface EJBObjectCache extends Cache {
    void add(Object primaryKey, EJBLocalRemoteObject ejbObject);
    EJBLocalRemoteObject remove(Object primaryKey);
    EJBLocalRemoteObject get(Object primaryKey);
    void clear();
}
```

### Cache Implementations

```java
// FIFOEJBObjectCache - First-in-first-out eviction
public class FIFOEJBObjectCache extends BaseCache {

    public FIFOEJBObjectCache(int maxEntries) {
        super(maxEntries);
    }

    @Override
    protected void overflow(Object primaryKey, Object value) {
        // Remove oldest entry when cache is full
        removeOldest();
    }
}

// UnboundedEJBObjectCache - No size limit
public class UnboundedEJBObjectCache extends BaseCache {

    public UnboundedEJBObjectCache() {
        super(-1); // Unlimited size
    }
}
```

### Cache Configuration

```xml
<!-- sun-ejb-jar.xml -->
<enterprise-beans>
    <entity>
        <ejb-name>CustomerBean</ejb-name>
        <cache>
            <max-cache-size>1000</max-cache-size>
            <is-read-only-bean>true</is-read-only-bean>
            <cache-idle-timeout-in-seconds>300</cache-idle-timeout-in-seconds>
        </cache>
    </entity>
</enterprise-beans>
```

## Distributed Caching

### DistributedReadOnlyBeanService

Clustered read-only bean refresh:

```java
public class DistributedReadOnlyBeanServiceImpl implements DistributedReadOnlyBeanService {

    @Override
    public void refreshReadOnlyBeans(String appName, String moduleName) {
        // Send refresh event to cluster members
        sendRefreshEvent(appName, moduleName);
    }

    @Override
    public void onRefresh(ReadOnlyBeanRefreshEvent event) {
        // Handle refresh event from cluster
        refreshBeans(event.getAppName(), event.getModuleName());
    }
}
```

### ReadOnlyBeanNotifier

Notifies beans of data changes:

```java
public interface ReadOnlyBeanNotifier {
    void refreshBean(long timestamp);
}

public class ReadOnlyBeanLocalNotifierImpl implements ReadOnlyBeanNotifier {
    @Override
    public void refreshBean(long timestamp) {
        // Invalidate cached bean state
        // Next access will reload from database
    }
}
```

## Package Structure

```
entitybean-container/
└── src/main/java/
    ├── org/glassfish/persistence/ejb/entitybean/container/
    │   ├── EntityContainer.java              # Main container class
    │   ├── EntityContextImpl.java             # Entity context
    │   ├── EntityContainerFactory.java        # Container factory
    │   ├── ReadOnlyBeanContainer.java         # Read-only container
    │   ├── ReadOnlyEJBHomeImpl.java          # Home interface
    │   ├── ReadOnlyContextImpl.java           # Read-only context
    │   ├── cache/
    │   │   ├── EJBObjectCache.java           # Cache interface
    │   │   ├── FIFOEJBObjectCache.java       # FIFO cache
    │   │   └── UnboundedEJBObjectCache.java   # Unlimited cache
    │   └── distributed/
    │       ├── DistributedEJBService.java
    │       ├── DistributedReadOnlyBeanService.java
    │       ├── ReadOnlyBeanRefreshEventHandler.java
    │       └── ReadOnlyBeanMessageCallBack.java
    │
    └── com/sun/appserv/ejb/
        └── ReadOnlyBeanHelper.java           # Helper utilities
```

## EJB 2.x Entity Bean Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                    Entity Bean Lifecycle                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────┐    ┌──────────┐    ┌──────────────┐      │
│  │  Pool     │───→│ ejbCreate │───→│ ejbPostCreate │      │
│  │  (Ready)  │    │ (Init)   │    │  (Callback)   │      │
│  └───────────┘    └──────────┘    └──────────────┘      │
│       │                                           │          │
│       │                                           ▼          │
│       │                                    ┌───────────┐  │
│       └───────────────────────────────────│ ejbActive  │  │
│       ▲                                    │ (In Cache) │  │
│       │                                    └───────────┘  │
│       │                                           │          │
│  ┌───────────────────────────────────────────┴──────────┐  │
│  │                    Invocation                           │  │
│  │  findByPrimaryKey | getPrimaryKey | ejbSelect         │  │
│  └──────────────────────────────────────────────────────┘  │
│       │                                           │          │
│       ▼                                           ▼          │
│  ┌───────────┐                             ┌───────────┐  │
│  │ ejbStore  │◄──dirty───────► │ ejbLoad   │  │
│  │ (Update)  │                             │ (Refresh) │  │
│  └───────────┘                             └───────────┘  │
│       │                                           │          │
│       ▼                                           ▼          │
│  ┌───────────┐                             ┌───────────┐  │
│  │ejbRemove  │                             │ejbPassivate│  │
│  │  (Delete) │                             │(Clustered) │  │
│  └───────────┘                             └───────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `ejb-container` | Base EJB container classes |
| `connectors-internal-api` | DataSource integration |
| `container-common` | Container utilities |
| `payara-modules/hazelcast-bootstrap` | Distributed caching |

## Notes

- **EJB 2.x** - Legacy technology (use JPA for new code)
- **Container-Managed** - Container handles all persistence operations
- **Bean-Managed** - Application handles persistence (rare)
- **Read-Only** - Optimized for queries (no state tracking)
- **Clustering** - Hazelcast-based cache coordination
- **Pool Management** - NonBlockingPool for entity instances
