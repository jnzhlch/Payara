# CLAUDE.md - Nucleus Payara Modules

This file provides guidance for working with the Payara-specific modules in Nucleus - features unique to Payara beyond the base GlassFish kernel.

## Module Overview

Payara modules extend Nucleus with Payara-specific features like health checks, notifications, request tracing, Hazelcast clustering, and MicroProfile support.

## Build Commands

```bash
# Build all Payara modules
mvn -DskipTests clean package -f nucleus/payara-modules/pom.xml

# Build specific module
mvn -DskipTests clean package -f nucleus/payara-modules/healthcheck-core/pom.xml

# Build with tests
mvn clean package -f nucleus/payara-modules/healthcheck-core/pom.xml
```

## Architecture

### Core Service Integration

```
┌─────────────────────────────────────────────────────────────────┐
│                      Payara Services                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │HealthCheck   │    │Notification  │    │RequestTracing│      │
│  │Service       │───▶│Service       │───▶│Service       │      │
│  │              │    │              │    │              │      │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘      │
│         │                   │                   │              │
│         ▼                   ▼                   ▼              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    EventBus (Hazelcast)                   │  │
│  └──────────────────────────────────────────────────────────┘  │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              Hazelcast Cluster (PayaraCluster)           │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### Service Bootstrap Flow

All Payara services follow the same bootstrap pattern:

```java
@Service(name = "service-name")
@RunLevel(StartupRunLevel.VAL)
public class MyService implements EventListener, ConfigListener {

    @Inject @Named(ServerEnvironment.DEFAULT_INSTANCE_NAME) @Optional
    MyServiceConfiguration configuration;

    @Inject
    private ServiceLocator habitat;

    @Inject
    private Events events;

    @PostConstruct
    public void postConstruct() {
        // Initialize service
    }

    @Override
    public void event(Event event) {
        if (event.is(EventTypes.SERVER_READY)) {
            // Start service
        }
    }

    @Override
    public UnprocessedChangeEvents changed(PropertyChangeEvent[] events) {
        // Handle configuration changes
    }
}
```

## Key Modules

### Health Check System (`healthcheck-core/`, `healthcheck-cpool/`, `healthcheck-stuck/`)

#### Architecture

```
HealthCheckService
        │
        ├─→ BaseHealthCheck implementations
        │      ├─→ CpuUsageHealthCheck
        │      ├─→ HeapMemoryUsageHealthCheck
        │      ├─→ MachineMemoryUsageHealthCheck
        │      ├─→ GarbageCollectorHealthCheck
        │      ├─→ HoggingThreadsHealthCheck
        │      └─→ StuckThreadsHealthCheck
        │
        ├─→ HealthCheckTask (scheduled execution)
        │      └─→ Runs each checker at configured interval
        │
        └─→ NotificationService
               └─→ Notified on health check failure
```

#### Health Check Types

| Checker | Description | Threshold |
|---------|-------------|-----------|
| `cpu` | CPU usage percentage | 0-100 |
| `heap` | Heap memory usage | 0-100 |
| `memory` | Machine memory usage | 0-100 |
| `gc` | Garbage collector time | 0-100 |
| `hogging` | Thread hogging detection | time threshold |
| `stuck` | Stuck thread detection | time threshold |
| `cpool` | Connection pool health | pool-specific |

#### Configuration

```bash
# Enable health check system
asadmin set-healthcheck-service-configuration --enabled=true --dynamic=true

# Configure specific checker
asadmin set-healthcheck-service-configuration \
    --checkerType=cpu \
    --enabled=true \
    --time=10 \
    --unit=seconds

# Configure with threshold
asadmin set-healthcheck-service-configuration \
    --checkerType=heap \
    --threshold=90 \
    --thresholdUnit=PERCENTAGE
```

#### Extending Health Checks

```java
@Service(name = "my-health-check")
public class MyHealthCheck extends BaseThresholdHealthCheck {

    @Override
    public String getName() {
        return "My Health Check";
    }

    @Override
    protected HealthCheckResult doCheck() {
        // Perform health check
        int value = getMetricValue();
        if (value > getThreshold()) {
            return HealthCheckResult.failure("Value too high: " + value);
        }
        return HealthCheckResult.success();
    }
}
```

### Notification System (`notification-core/`, `notification-eventbus-core/`, `notification-cdi-eventbus-core/`)

#### Architecture

```
NotificationService (NotifierManager)
        │
        ├─→ Notifier implementations
        │      ├─→ LogNotifier (log file output)
        │      ├─→ EventBusNotifier (internal event distribution)
        │      ├─→ CDIEventNotifier (CDI event firing)
        │      └─→ JMSNotifier (JMS messaging - in appserver)
        │
        ├─→ NotificationEvent generation
        │      ├─→ HealthCheckNotificationEvent
        │      ├─→ RequestTracingNotificationEvent
        │      └─→ Custom events
        │
        └─→ EventBus distribution
               └─→ Cluster-wide notification
```

#### Notification Flow

```java
// 1. Event occurs
PayaraNotification notification = PayaraNotificationFactory.create(
    EventLevel.WARNING,
    "Health check failed: CPU usage high"
);

// 2. Send to NotificationService
@MessageReceiver
public void notify(PayaraNotification notification) {
    // Distributes to all configured notifiers
}

// 3. Each notifier handles
LogNotifier → writes to log
EventBusNotifier → publishes to Hazelcast topic
CDIEventNotifier → fires CDI event
```

#### Configuration

```bash
# Enable notification service
asadmin notification-configure --enabled true

# Configure log notifier
asadmin notification-notifier-configure \
    --notifierType LOG \
    --enabled true

# Configure CDI notifier
asadmin notification-notifier-configure \
    --notifierType CDI \
    --enabled true
```

#### CDI Event Consumption

```java
// In application code
public class MyNotificationObserver {

    public void observeNotification(@Observes NotificationEvent event) {
        System.out.println("Notification: " + event.getMessage());
    }
}
```

### Request Tracing (`requesttracing-core/`)

#### Architecture

```
RequestTracingService
        │
        ├─→ RequestTrace
        │      ├─→ RequestTraceSpan
        │      │      ├─→ RequestTraceSpanLog
        │      │      └─→ Span timing data
        │      └─→ Trace metadata
        │
        ├─→ SampleFilter
        │      ├─→ AdaptiveSampleFilter
        │      └─→ Configurable sampling
        │
        ├─→ RequestTraceStore
        │      ├─→ In-memory store
        │      └─→ Clustered store (Hazelcast)
        │
        └─→ NotificationService
               └─→ Alert on threshold breach
```

#### Request Trace Structure

```java
RequestTrace
├── traceId (UUID)
├── startTime (long)
├── duration (long)
├── threadName (String)
├── applicationName (String)
└── spans[]
    ├── spanId (UUID)
    ├── spanName (String)
    ├── startTime (long)
    ├── duration (long)
    └── logs[]
        ├── timestamp (long)
        └── message (String)
```

#### Configuration

```bash
# Enable request tracing
asadmin set-requesttracing-configuration \
    --enabled=true \
    --thresholdUnit=seconds \
    --thresholdValue=5

# Configure adaptive sampling
asadmin set-requesttracing-configuration \
    --sampleType=ADAPTIVE \
    --adaptiveTarget=10 \
    --adaptiveTimeUnit=MINUTES

# Configure trace history
asadmin set-requesttracing-configuration \
    --historySize=100 \
    --adaptiveSamplingStrategy=MOVING_AVG_HALF_LIFE
```

#### Programmatic Tracing

```java
@Inject
private RequestTracingService requestTracingService;

public void traceOperation() {
    RequestTraceSpan span = requestTracingService.startSpan("my-operation");
    try {
        // Do work
        span.addLog("Processing step 1");
        // More work
        span.addLog("Processing step 2");
    } finally {
        requestTracingService.endSpan(span);
    }
}
```

### Hazelcast Clustering (`hazelcast-bootstrap/`)

#### Architecture

```
PayaraCluster (HazelcastInstance)
        │
        ├─→ Discovery
        │      ├─→ DomainDiscoveryStrategy
        │      ├─→ DnsDiscoveryService
        │      └─→ Multicast discovery
        │
        ├─→ Data Structures
        │      ├─→ IMap (distributed maps)
        │      ├─→ IQueue (distributed queues)
        │      └─→ ITopic (publish/subscribe)
        │
        ├─→ EventBus
        │      └─→ Cluster-wide messaging
        │
        └─→ ClusterExecutionService
               └─→ Distributed task execution
```

#### EventBus System

```java
// Publish event to cluster
EventBus eventBus = habitat.getService(EventBus.class);
eventBus.publish("my-topic", new ClusterMessage(payload));

// Subscribe to events
@MessageReceiver
@SubscribeTo(value = "my-topic", scope = Scope.CLUSTER)
public void receive(ClusterMessage message) {
    // Handle message from any cluster member
}
```

#### Configuration

```bash
# Get Hazelcast configuration
asadmin get-hazelcast-configuration

# Set Hazelcast configuration
asadmin set-hazelcast-configuration \
    --enabled=true \
    --memberGroup=cluster \
    --memberName=server1

# List cluster members
asadmin list-hazelcast-members

# Clear cache
asadmin clear-cache --name=my-cache
```

#### Clustered Data Access

```java
@Inject
private HazelcastCore hazelcast;

public void putData(String key, String value) {
    HazelcastInstance instance = hazelcast.getInstance();
    IMap<String, String> map = instance.getMap("my-map");
    map.put(key, value);
}
```

### Admin Command Recording (`asadmin-recorder/`, `asadmin-audit/`)

#### Command Recording

```bash
# Enable command recording
asadmin set-recorder-configuration --enabled=true

# List recorded commands
asadmin list-recorded-commands

# Replay commands
asadmin replay-recorded-commands --file=commands.txt
```

#### Audit Logging

```bash
# Configure audit logging
asadmin set-audit-configuration --enabled=true

# View audit log
tail -f domains/domain1/logs/audit.log
```

## Service Integration Patterns

### EventBus Messaging

```java
// Define custom event
public class MyEvent extends ClusterMessage {
    private final String data;

    public MyEvent(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }
}

// Publish event
@Service
public class MyEventPublisher {

    @Inject
    private EventBus eventBus;

    public void publishEvent(String data) {
        eventBus.publish("my-events", new MyEvent(data));
    }
}

// Subscribe to event
@Service
public class MyEventReceiver {

    @MessageReceiver
    @SubscribeTo(value = "my-events", scope = Scope.CLUSTER)
    public void receive(MyEvent event) {
        System.out.println("Received: " + event.getData());
    }
}
```

### Health Check with Notification

```java
@Service
public class MyHealthCheck extends BaseHealthCheck {

    @Inject
    private NotificationService notificationService;

    @Override
    public HealthCheckResult perform() {
        try {
            // Check health
            if (isHealthy()) {
                return HealthCheckResult.success();
            } else {
                // Send notification
                PayaraNotification notification = PayaraNotificationFactory.create(
                    EventLevel.WARNING,
                    "Health check failed"
                );
                notificationService.notify(notification);

                return HealthCheckResult.failure("Health check failed");
            }
        } catch (Exception e) {
            return HealthCheckResult.error(e);
        }
    }
}
```

### Configuration Bootstrap

All services support dynamic configuration changes:

```java
@Service
public class MyService implements ConfigListener {

    @Inject @Named(ServerEnvironment.DEFAULT_INSTANCE_NAME) @Optional
    MyServiceConfiguration configuration;

    @Override
    public UnprocessedChangeEvents changed(PropertyChangeEvent[] events) {
        for (PropertyChangeEvent event : events) {
            if ("enabled".equals(event.getPropertyName())) {
                boolean enabled = (Boolean) event.getNewValue();
                if (enabled) {
                    startService();
                } else {
                    stopService();
                }
            }
        }
        return null;
    }
}
```

## RunLevel Bootstrap

Payara services use HK2 runlevels for ordered startup:

```java
@Service(name = "my-service")
@RunLevel(StartupRunLevel.VAL)  // Start after server initialization
public class MyService {
    @PostConstruct
    public void postConstruct() {
        // Service initialization
    }
}
```

## Clustered Configuration

Services can use clustered configuration for HA:

```java
@Service
public class MyService {

    @Inject
    private ClusteredConfig clusteredConfig;

    public void setConfigValue(String key, String value) {
        // Stored in Hazelcast, available to all cluster members
        clusteredConfig.set(key, value);
    }

    public String getConfigValue(String key) {
        return clusteredConfig.get(key);
    }
}
```

## Package Structure

```
fish.payara.nucleus.{module}/
├── {module}/                    # Service implementation
│   ├── {Module}Service.java
│   ├── configuration/           # Configuration beans
│   ├── admin/                   # Admin commands
│   └── events/                  # Event definitions
└── healthcheck/preliminary/     # Built-in health checks
```

## Dependencies Between Modules

```
hazelcast-bootstrap (cluster infrastructure)
        │
        ├─→ healthcheck-core
        ├─→ notification-core
        ├─→ requesttracing-core
        └─→ payara-executor-service
```

## Related Modules

- **appserver/payara-appserver-modules** - Application-level Payara features
- **internal-api** - Shared Payara internal APIs
- **nucleus/core/kernel** - HK2 service integration
