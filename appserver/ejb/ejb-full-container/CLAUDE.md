# CLAUDE.md - EJB Full Container

This file provides guidance for working with the `ejb-full-container` module - Full profile EJB container add-ons.

## Module Overview

The ejb-full-container module provides EJB container features that are specific to the Java EE Full Profile, primarily enhanced MDB (Message-Driven Bean) timer support.

## Build Commands

```bash
# Build ejb-full-container module
mvn -DskipTests clean package -f appserver/ejb/ejb-full-container/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Name**: "Full EJB Container add-ons"
- **Purpose**: Java EE Full Profile EJB features

## Full Profile Features

### MDB Timer Support

Message-driven beans can use @Schedule/@Timeout in Java EE Full Profile:

```java
@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup",
                              propertyValue = "jms/myQueue")
})
public class TimedMessageBean implements MessageListener {
    @Override
    public void onMessage(Message message) {
        // Process message
    }

    @Schedule(hour = "*", minute = "*/10")
    public void periodicTask() {
        // Runs every 10 minutes
    }

    @Timeout
    public void handleTimer(Timer timer) {
        // Handle programmatic timer
    }
}
```

### Timer Integration

The full container integrates:
- **EJBTimerService** - With MDB lifecycle
- **JCA WorkManager** - For timer delivery to MDBs
- **Transaction Manager** - For timer transaction support

### Dependencies

| Dependency | Purpose |
|------------|---------|
| `ejb-container` | Core container |
| `jakarta.jms-api` | JMS for MDBs |
| `opentracing-api` | Distributed tracing |

## Related Modules

- `ejb-container` - Core container
- `ejb-timer-service-app` - Timer service application
- `connectors` - JCA integration
