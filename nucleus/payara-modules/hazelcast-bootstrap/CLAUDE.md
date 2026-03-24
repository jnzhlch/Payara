# CLAUDE.md - Hazelcast Bootstrap

This file provides guidance for working with the `hazelcast-bootstrap` module - Payara's clustering infrastructure based on Hazelcast.

## Module Overview

The `hazelcast-bootstrap` module provides Hazelcast-based clustering for Payara, including distributed caching, event bus messaging, and cluster member discovery.

## Build Commands

```bash
# Build hazelcast-bootstrap module
mvn -DskipTests clean package -f nucleus/payara-modules/hazelcast-bootstrap/pom.xml

# Build with tests
mvn clean package -f nucleus/payara-modules/hazelcast-bootstrap/pom.xml
```

## Architecture

### Core Components

```
PayaraCluster (HazelcastInstance)
        │
        ├─→ Discovery
        │      ├─→ DomainDiscoveryStrategy (domain.xml config)
        │      ├─→ DnsDiscoveryService (DNS-based discovery)
        │      └─→ Multicast discovery
        │
        ├─→ Data Structures
        │      ├─→ IMap (distributed maps)
        │      ├─→ ICache (distributed caches)
        │      ├─→ IQueue (distributed queues)
        │      └─── ITopic (publish/subscribe)
        │
        ├─→ EventBus (ClusterMessage distribution)
        │      └─→ Topic-based messaging
        │
        ├─→ ClusterExecutionService
        │      └─→ Distributed task execution
        │
        └─→ HazelcastCore
               └─→ Singleton Hazelcast instance management
```

### Hazelcast Bootstrap Flow

```
Server Startup
        │
        ├─→ HazelcastCore @PostConstruct
        │      ├─→ Read configuration
        │      ├─→ Configure discovery
        │      ├─→ Create HazelcastInstance
        │      └─→ Initialize data structures
        │
        ├─→ EventBus initialization
        │      └─→ Create topics for messaging
        │
        └─→ Cluster member joins
               └─→ Member events fired
```

## Configuration

### Enable Clustering

```bash
# Enable Hazelcast
asadmin set-hazelcast-configuration --enabled=true

# Set member configuration
asadmin set-hazelcast-configuration \
    --memberGroup=cluster \
    --memberName=server1

# Configure discovery
asadmin set-hazelcast-configuration \
    --discoveryMode=DOMAIN \
    --interface=192.168.1.100
```

### Domain Configuration

In domain.xml:

```xml
<configs>
  <config name="server-config">
    <hazelcast-config>
      <enabled>true</enabled>
      <member-group>cluster</member-group>
      <member-name>server1</member-name>
      <discovery-mode>DOMAIN</discovery-mode>
      <interface>192.168.1.100</interface>
      <cluster-name>payara-cluster</cluster-name>
      <cluster-password>password</cluster-password>
    </hazelcast-config>
  </config>
</configs>
```

### Discovery Modes

| Mode | Description |
|------|-------------|
| `DOMAIN` | Uses domain.xml member configuration |
| `DNS` | Uses DNS service discovery |
| `MULTICAST` | Uses multicast discovery |

## Distributed Data Structures

### IMap (Distributed Map)

```java
@Inject
private HazelcastCore hazelcast;

public void putData(String key, String value) {
    HazelcastInstance instance = hazelcast.getInstance();
    IMap<String, String> map = instance.getMap("my-map");

    // Put value (replicated across cluster)
    map.put(key, value);

    // Get value
    String result = map.get(key);

    // Remove value
    map.remove(key);
}
```

### ICache (Distributed Cache)

```java
// Get cache
ICache<String, Object> cache = hazelcast.getInstance()
    .getCacheManager("my-cache-manager")
    .getCache("my-cache");

// Use cache
cache.put("key", "value");
Object value = cache.get("key");
```

### ITopic (Publish/Subscribe)

```java
// Get topic
ITopic<String> topic = hazelcast.getInstance().getTopic("my-topic");

// Publish message
topic.publish("Hello, cluster!");

// Subscribe to messages
topic.addMessageListener(message -> {
    System.out.println("Received: " + message.getMessageObject());
});
```

## EventBus System

### Publishing Events

```java
// Define custom cluster message
public class MyEvent extends ClusterMessage {
    private final String data;

    public MyEvent(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }
}

// Publish to cluster
@Service
public class MyEventPublisher {

    @Inject
    private EventBus eventBus;

    public void publishEvent(String data) {
        eventBus.publish("my-events", new MyEvent(data));
    }
}
```

### Subscribing to Events

```java
@Service
public class MyEventReceiver {

    @Inject
    private ServiceLocator habitat;

    @PostConstruct
    public void init() {
        // Get EventBus
        EventBus eventBus = habitat.getService(EventBus.class);

        // Subscribe to topic
        eventBus.subscribe("my-events", this::receiveEvent);
    }

    private void receiveEvent(ClusterMessage message) {
        if (message instanceof MyEvent) {
            MyEvent event = (MyEvent) message;
            System.out.println("Received: " + event.getData());
        }
    }
}
```

### Using @MessageReceiver

```java
@Service
@MessageReceiver
public class MyMessageReceiver {

    @SubscribeTo(value = "my-events", scope = Scope.CLUSTER)
    public void receive(MyEvent event) {
        // Automatically called when event published
        System.out.println("Received: " + event.getData());
    }
}
```

### Event Scope

```java
public enum Scope {
    LOCAL,      // Only this JVM instance
    CLUSTER,    // All cluster members
    DYNAMIC     // Determined at runtime
}
```

## Cluster Member Events

### Member Events

```java
@Service
public class ClusterEventListener implements ClusterListener {

    @Inject
    private Events events;

    @PostConstruct
    public void init() {
        // Register for Hazelcast member events
        events.register(new EventListener() {
            @Override
            public void event(Event event) {
                if (event.is(HazelcastEvents.MEMBER_ADDED)) {
                    memberAdded((MemberEvent) event);
                } else if (event.is(HazelcastEvents.MEMBER_REMOVED)) {
                    memberRemoved((MemberEvent) event);
                }
            }
        });
    }

    private void memberAdded(MemberEvent event) {
        // New member joined cluster
        System.out.println("Member added: " + event.getMember());
    }

    private void memberRemoved(MemberEvent event) {
        // Member left cluster
        System.out.println("Member removed: " + event.getMember());
    }
}
```

## Distributed Execution

### ClusterExecutionService

```java
@Inject
private ClusterExecutionService executorService;

public void executeOnCluster(String taskName) {
    // Execute task on all cluster members
    executorService.executeOnAllMembers(taskName);
}

public void executeOnMember(String taskName, String memberUUID) {
    // Execute task on specific member
    executorService.executeOnMember(taskName, memberUUID);
}
```

### Distributed Task

```java
@Service
public class MyClusteredTask implements Callable<String>, Serializable {

    @Override
    public String call() {
        // Task executed on cluster member
        return "Result from " + getMemberUuid();
    }
}
```

## Clustered Configuration

### Using ClusteredConfig

```java
@Service
public class MyService {

    @Inject
    private ClusteredConfig clusteredConfig;

    public void setConfig(String key, String value) {
        // Stored in Hazelcast, available to all cluster members
        clusteredConfig.set(key, value);
    }

    public String getConfig(String key) {
        return clusteredConfig.get(key);
    }

    // Watch for configuration changes
    public void watchConfig(String key, Consumer<String> callback) {
        clusteredConfig.addListener(key, callback);
    }
}
```

### Clustered Store

```java
@Inject
private ClusteredStore clusteredStore;

public void storeRequestData(String requestId, Object data) {
    // Store data in cluster
    clusteredStore.put(requestId, data);
}

public Object getRequestId(String requestId) {
    return clusteredStore.get(requestId);
}
```

## Admin Commands

### Hazelcast Configuration

```bash
# Get Hazelcast configuration
asadmin get-hazelcast-configuration

# Set Hazelcast configuration
asadmin set-hazelcast-configuration \
    --enabled=true \
    --memberGroup=cluster \
    --memberName=server1 \
    --interface=192.168.1.100

# Restart Hazelcast
asadmin restart-hazelcast
```

### Cache Management

```bash
# List caches
asadmin list-caches

# List cache keys
asadmin list-cache-keys --cacheName=my-cache

# Clear cache
asadmin clear-cache --cacheName=my-cache
```

### Cluster Management

```bash
# List cluster members
asadmin list-hazelcast-members

# Get cluster state
asadmin get-hazelcast-state

# Force cluster split-brain protection
asadmin enable-split-brain-protection
```

## Cluster Discovery

### Domain Discovery

Uses domain.xml configuration for static discovery:

```xml
<hazelcast-config>
    <discovery-mode>DOMAIN</discovery-mode>
    <interface>192.168.1.100</interface>
    <!-- Additional members configured in domain.xml -->
</hazelcast-config>
```

### DNS Discovery

```bash
# Configure DNS discovery
asadmin set-hazelcast-configuration \
    --discoveryMode=DNS \
    --dnsServiceName=payara-cluster \
    --dnsServiceType=tcp
```

### Discovery Strategy Implementation

```java
@Service
public class MyDiscoveryStrategy implements DiscoveryStrategy {

    @Override
    public Iterable<InetSocketAddress> discoveryAddresses() {
        // Return list of member addresses
        List<InetSocketAddress> addresses = new ArrayList<>();
        addresses.add(new InetSocketAddress("member1", 5700));
        addresses.add(new InetSocketAddress("member2", 5700));
        return addresses;
    }

    @Override
    public void destroy() {
        // Cleanup
    }
}
```

## Serialization

### PayaraHazelcastSerializer

Payara uses a custom serializer for efficient serialization:

```java
@Service
public class MySerializable implements Serializable {

    // Will be serialized using PayaraHazelcastSerializer
    private String data;

    // Getters and setters
}
```

### Custom Serialization

```java
public class MySerializer implements StreamSerializer<MyObject> {

    @Override
    public void write(ObjectDataOutput out, MyObject object) throws IOException {
        out.writeUTF(object.getData());
    }

    @Override
    public MyObject read(ObjectDataInput in) throws IOException {
        return new MyObject(in.readUTF());
    }

    @Override
    public int getTypeId() {
        return 1;  // Unique type ID
    }

    @Override
    public void destroy() {
    }
}
```

## Security

### Encryption

```xml
<hazelcast-config>
    <symmetric-encryption>
        <algorithm>AES/GCM/NoPadding</algorithm>
        <salt>base64-salt</salt>
        <password>base64-password</password>
        <iteration-count>1000</iteration-count>
    </symmetric-encryption>
</hazelcast-config>
```

### SSL Configuration

```xml
<hazelcast-config>
    <ssl enabled="true">
        <properties>
            <property name="protocol">TLSv1.2</property>
            <property name="keyStore">keystore.jks</property>
            <property name="keyStorePassword">password</property>
        </properties>
    </ssl>
</hazelcast-config>
```

## Package Structure

```
fish.payara.nucleus.{package}/
├── hazelcast/
│   ├── HazelcastCore.java           # Singleton instance
│   ├── admin/                       # Admin commands
│   ├── encryption/                  # Security
│   └── PayaraHazelcastTenantFactory.java
├── cluster/
│   ├── PayaraCluster.java           # Cluster wrapper
│   └── ClusterListener.java
├── eventbus/
│   ├── EventBus.java                # Cluster messaging
│   └── MessageReceiver.java
├── store/
│   └── ClusteredStore.java          # Distributed store
├── exec/
│   └── ClusterExecutionService.java # Distributed tasks
└── config/
    └── ClusteredConfig.java         # Clustered config
```

## Related Modules

- **healthcheck-core** - Cluster health monitoring
- **notification-core** - Cluster notifications
- **requesttracing-core** - Distributed request tracing
- **payara-executor-service** - Cluster-aware executor

## Best Practices

1. **Use appropriate data structures** - IMap for general data, ICache for high-read scenarios
2. **Handle member departure** - Plan for members leaving the cluster
3. **Consider network partition** - Enable split-brain protection for production
4. **Configure sensible timeouts** - Don't let operations hang indefinitely
5. **Test with multiple members** - Verify cluster behavior in realistic scenarios

## Troubleshooting

### Cluster Not Forming

```bash
# Check Hazelcast is enabled
asadmin get-hazelcast-configuration

# Check member list
asadmin list-hazelcast-members

# Check network connectivity
telnet member1 5701
telnet member2 5701
```

### Split Brain Issues

```bash
# Enable split brain protection
asadmin set-hazelcast-configuration \
    --split-brain-protection-enabled=true

# Check cluster state
asadmin get-hazelcast-state
```

### Memory Issues

```bash
# Check Hazelcast memory usage
asadmin get-hazelcast-configuration | grep memory

# Adjust memory configuration
asadmin set-hazelcast-configuration \
    --max-size=256 \
    --max-size-unit=MEGABYTES
```
