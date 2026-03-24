# CLAUDE.md - Appserver High Availability

This file provides guidance for working with the `appserver/ha` module - High Availability backing store implementations.

## Module Overview

The appserver/ha module provides persistence implementations for the GlassFish HA (High Availability) framework. These backing stores are used to persist session data and other stateful information for web applications and EJBs in clustered environments.

**Note:** This module provides storage adapters for the HA framework. The core HA API is in `org.glassfish.ha:ha-api`, and web session replication uses these stores via `appserver/web/web-ha`.

## Sub-Modules

| Submodule | Purpose |
|-----------|---------|
| `ha-file-store` | File-based persistence (local disk) |
| `ha-hazelcast-store` | Hazelcast-based distributed persistence |

## Build Commands

```bash
# Build entire ha module
mvn -DskipTests clean package -f appserver/ha/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/ha/ha-file-store/pom.xml
mvn -DskipTests clean package -f appserver/ha/ha-hazelcast-store/pom.xml
```

## Architecture

### Backing Store Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│                     HA Application Layer                        │
│                  (web-ha, ejb-timer, etc.)                      │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   BackingStoreFactory API                       │
│  (createBackingStore, createBackingStoreTransaction)            │
└────────────────────────────┬────────────────────────────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
    ┌─────────────┐  ┌──────────────┐  ┌─────────────┐
    │ File Store  │  │ Hazelcast    │  │ Other       │
    │ (local)     │  │ (clustered)  │  │ Stores      │
    └─────────────┘  └──────────────┘  └─────────────┘
```

### Registration and Discovery

Stores are registered with `BackingStoreFactoryRegistry` and discovered by name:

```java
// Registered names
"file"        → FileBackingStoreFactory
"hazelcast"   → HazelcastBackingStoreFactoryProxy
```

## File-Based Store (ha-file-store)

### FileBackingStoreFactory

```java
@Service(name = "file")
public class FileBackingStoreFactory implements BackingStoreFactory {

    @Override
    public <K, V> BackingStore<K, V> createBackingStore(
            BackingStoreConfiguration<K, V> conf) {
        FileBackingStore<K, V> fs = new FileBackingStore<>();
        fs.initialize(conf);
        return fs;
    }

    @Override
    public BackingStoreTransaction createBackingStoreTransaction() {
        return new FileStoreTransaction(); // No-op for file store
    }
}
```

### FileBackingStore

**Storage Model:**
- Each key-value pair stored as a separate file
- File name = `key.toString()`
- Directory = `conf.getBaseDirectory()`
- File lastModified = timestamp for expiration

**Key Operations:**
```java
// Save session
String save(K key, V value, boolean isNew)

// Load session
V load(K key, String version)

// Remove session
void remove(K key)

// Update timestamp (for expiration)
void updateTimestamp(K key, long timestamp)

// Remove expired sessions
int removeExpired(long idleForMillis)

// Get count
int size()

// Destroy store
void destroy()
```

**Configuration:**
```java
BackingStoreConfiguration conf = new BackingStoreConfiguration();
conf.setStoreName("myStore");
conf.setBaseDirectory(new File("/path/to/session/store"));
conf.setInstanceName("server1");

// Optional: max idle timeout (default: 10 minutes)
conf.getVendorSpecificSettings().put("max.idle.timeout.in.seconds", "600");
```

**Persistence Characteristics:**
- **Scope:** Local server only (not replicated)
- **Performance:** Fast for local access, no network overhead
- **Durability:** Survives server restarts (disk-based)
- **Use Case:** Development, single-server deployments, persistence without clustering

## Hazelcast Store (ha-hazelcast-store)

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│              HazelcastBackingStoreFactoryProxy                  │
│           (Registered as "hazelcast")                           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│            HazelcastBackingStoreFactory                         │
│        (Registered as "hazelcast-factory")                      │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│              HazelcastBackingStore                              │
│                   ↓                                             │
│         fish.payara.nucleus.store.ClusteredStore                │
│                   ↓                                             │
│         Hazelcast IMap (Distributed Map)                        │
└─────────────────────────────────────────────────────────────────┘
```

### HazelcastBackingStoreFactory

```java
@Service(name = "hazelcast-factory")
public class HazelcastBackingStoreFactory implements BackingStoreFactory {

    @Inject
    HazelcastCore core;

    @Inject
    ClusteredStore clusteredStore;

    @Override
    public <K, V> BackingStore<K, V> createBackingStore(
            BackingStoreConfiguration<K, V> bsc) {
        return new HazelcastBackingStore<>(this, bsc.getStoreName(), clusteredStore);
    }

    @Override
    public BackingStoreTransaction createBackingStoreTransaction() {
        return new HazelcastBackingStoreTransaction(
            core.getInstance().newTransactionContext());
    }
}
```

### HazelcastBackingStoreFactoryProxy

The proxy service is registered at startup to ensure Hazelcast availability:

```java
@Service(name = "hazelcast")
@RunLevel(StartupRunLevel.VAL)
public class HazelcastBackingStoreFactoryProxy implements PostConstruct {

    @Override
    public <K, V> BackingStore<K, V> createBackingStore(
            BackingStoreConfiguration<K, V> bsc) {
        // Delegates to hazelcast-factory
        BackingStoreFactory storeFactory = habitat.getService(
            BackingStoreFactory.class, "hazelcast-factory");
        return storeFactory.createBackingStore(bsc);
    }

    @Override
    public void postConstruct() {
        BackingStoreFactoryRegistry.register("hazelcast", this);
    }
}
```

### HazelcastBackingStore

**Storage Model:**
- Uses distributed `IMap<String, Object>` from Hazelcast
- Each store name maps to a separate IMap
- Map key = session key, Map value = serialized session object

**Key Operations:**
```java
// Save to distributed map
String save(K key, V value, boolean isNew) {
    clusteredStore.set(storeName, key, value);
    return instanceName; // Returns Hazelcast instance ID
}

// Load from distributed map
V load(K key, String version) {
    return (V) clusteredStore.get(storeName, key);
}

// Remove from distributed map
void remove(K key) {
    clusteredStore.remove(storeName, key);
}

// Get map size
int size() {
    return clusteredStore.getMap(storeName).size();
}
```

**Initialization Check:**
```java
private void init() throws BackingStoreException {
    if (clusteredStore == null) {
        throw new BackingStoreException("Clustered Store not available");
    }
    if (!clusteredStore.isEnabled()) {
        throw new BackingStoreException("Hazelcast is not enabled");
    }
    instanceName = clusteredStore.getInstanceId().toString();
}
```

**Persistence Characteristics:**
- **Scope:** Cluster-wide (replicated across all instances)
- **Performance:** Network overhead, but scales horizontally
- **Durability:** Depends on Hazelcast configuration (in-memory, disk, or backup)
- **Use Case:** Production clusters, session replication, distributed caching

## Session Strategy Builder

### HazelcastReplicatedWebMethodSessionStrategyBuilder

```java
@Service(name = "hazelcast")
@PerLookup
public class HazelcastReplicatedWebMethodSessionStrategyBuilder
        extends ReplicatedWebMethodSessionStrategyBuilder {
    // Extends web-ha strategy builder for Hazelcast
}
```

This service integrates with the web container's session replication mechanism.

## Package Structure

```
appserver/ha/
├── ha-file-store/                                    # File-based persistence
│   └── src/main/java/org/glassfish/ha/store/adapter/file/
│       ├── FileBackingStore.java                    # Store implementation
│       ├── FileBackingStoreFactory.java             # Factory service
│       └── FileStoreTransaction.java                # Transaction (no-op)
│
└── ha-hazelcast-store/                              # Hazelcast persistence
    └── src/main/java/fish/payara/ha/hazelcast/store/
        ├── HazelcastBackingStore.java               # Store implementation
        ├── HazelcastBackingStoreFactory.java        # Factory service
        ├── HazelcastBackingStoreFactoryProxy.java   # Registry proxy
        ├── HazelcastBackingStoreTransaction.java    # Transaction wrapper
        └── HazelcastReplicatedWebMethodSessionStrategyBuilder.java
```

## Module Dependencies

### ha-file-store

| Dependency | Purpose |
|------------|---------|
| `ha-api` | BackingStore API interfaces |
| `hk2-core` | Dependency injection |

### ha-hazelcast-store

| Dependency | Purpose |
|------------|---------|
| `ha-api` | BackingStore API interfaces |
| `hk2-core` | Dependency injection |
| `glassfish-api` | GlassFish internal APIs |
| `hazelcast` | Distributed caching |
| `web-ha` | Web session replication |
| `hazelcast-bootstrap` | Hazelcast initialization |

## Comparison: File vs Hazelcast Store

| Feature | File Store | Hazelcast Store |
|---------|-----------|-----------------|
| **Scope** | Single server | Cluster-wide |
| **Replication** | None | Automatic |
| **Performance** | Fast (local I/O) | Medium (network) |
| **Scalability** | Vertical only | Horizontal |
| **Durability** | Disk-based | Configurable |
| **Configuration** | Simple directory | Hazelcast cluster |
| **Use Case** | Dev/test, single server | Production clusters |

## Configuring Persistence Type

The backing store is selected via the `persistence-type` configuration:

```xml
<!-- In glassfish-web.xml or domain.xml -->
<web-container>
    <session-config>
        <session-manager>
            <manager-properties>
                <property name="persistence-type" value="hazelcast"/>
                <!-- or -->
                <property name="persistence-type" value="file"/>
            </manager-properties>
        </session-manager>
    </session-config>
</web-container>
```

## Related Modules

- `appserver/web/web-ha` - Web session replication using these stores
- `nucleus/payara-modules/hazelcast-bootstrap` - Hazelcast initialization
- `appserver/ejb/ejb-timer-databases` - EJB timer persistence
- `org.glassfish.ha:ha-api` - Core HA API interfaces

## Notes

- File store is primarily for development and non-clustered deployments
- Hazelcast store requires Hazelcast to be enabled in the server
- Both stores implement the same `BackingStore` API for interchangeability
- Session expiration is handled differently (file modification time vs. Hazelcast TTL)
- File store transactions are no-ops (immediate write)
- Hazelcast supports true distributed transactions
