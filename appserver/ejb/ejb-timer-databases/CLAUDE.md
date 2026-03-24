# CLAUDE.md - EJB Timer Databases

This file provides guidance for working with the `ejb-timer-databases` module - Database-backed EJB timer storage.

## Module Overview

The ejb-timer-databases module provides JDBC-based persistent storage for EJB timers, enabling timer survival across server restarts and cluster-wide timer coordination.

## Build Commands

```bash
# Build ejb-timer-databases module
mvn -DskipTests clean package -f appserver/ejb/ejb-timer-databases/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Purpose**: Persistent EJB timer storage

## Timer Persistence

### Database Schema

```sql
CREATE TABLE timers (
    timer_id       VARCHAR(255) PRIMARY KEY,
    owner_id       VARCHAR(255),
    timed_object   VARCHAR(255),
    expiration     BIGINT,
    interval       BIGINT,
    info           VARBINARY(1024),
    state          SMALLINT
);

CREATE TABLE timer_config (
    timer_id       VARCHAR(255) PRIMARY KEY,
    config_key     VARCHAR(255),
    config_value   VARBINARY(1024)
);
```

### Configuration

```xml
<ejb-container>
    <ejb-timer-service>
        <!-- Timer datasource -->
        <timer-datasource>jdbc/__TimerPool</timer-datasource>

        <!-- Redelivery settings -->
        <max-redeliveries>1</max-redeliveries>
        <redelivery-interval-millis>5000</redelivery-interval-millis>

        <!-- Minimum delivery interval -->
        <min-delivery-interval-millis>1000</min-delivery-interval-millis>
    </ejb-timer-service>
</ejb-container>
```

### DataSource

```xml
<jdbc-connection-pool name="__TimerPool" datasource-classname="org.apache.derby.jdbc.EmbeddedDataSource">
    <property name="databaseName" value="memory:timers"/>
    <property name="createDatabase" value="create"/>
</jdbc-connection-pool>

<jdbc-resource pool-name="__TimerPool" jndi-name="jdbc/__TimerPool"/>
```

### Timer Lifecycle

```
@Schedule
       │
   [Create Timer]
       │
   [PersistentTimerService]
       │
   [Save to Database]
       │
   [Schedule In-Memory]
       │
   ┌─────▼────────┐
   │ Timer Expires│
   └─────┬────────┘
       │
   [Load from DB]
       │
   [Invoke Bean]
       │
   [Reschedule]
```

## Timer Restoration

On server startup, timers are restored:

```java
public class PersistentTimerService extends EJBTimerService {
    public void restoreTimers() {
        // Load timers for this server
        List<EJBTimer> timers = loadTimersFromDB();

        for (EJBTimer timer : timers) {
            // Add to cache
            timerCache_.add(timer);

            // Schedule in-memory reminder
            scheduleInMemory(timer);
        }
    }
}
```

## Cluster Support

### Timer Ownership

- Each timer has an `ownerId`
- Only owning server executes timer
- On server failure, timers redistributed

### Timer Redistribution

```
Server A (owner) fails
       │
   [Timers orphaned]
       │
┌──────▼──────────────┐
│ Cluster Manager      │
│ Detects failure      │
└──────┬──────────────┘
       │
   [Redistribute timers]
       │
┌──────▼──────────────┐
│ Server B             │
│ Becomes new owner    │
└──────────────────────┘
```

## Admin Commands

```bash
# List timers
asadmin list-timers

# Configure timer datasource
asadmin set ejb-container.ejb-timer-service.timer-datasource=jdbc/__TimerPool

# View timer stats
asadmin get -m timers.stats.*
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `ejb-container` | Container integration |
| `jdbc-runtime` | JDBC connection pooling |
| `transaction` | Transaction management |

## Related Modules

- `ejb-container` - Timer service implementation
- `payara-appserver-modules/hazelcast-ejb-timer` - Clustered timers
