# CLAUDE.md - EJB Timer Service Application

This file provides guidance for working with the `ejb-timer-service-app` module - EJB timer service application.

## Module Overview

The ejb-timer-service-app module provides a timer service application that can be deployed for persistent EJB timer management. It's a WAR file that provides administrative and monitoring interfaces for EJB timers.

## Build Commands

```bash
# Build ejb-timer-service-app module
mvn -DskipTests clean package -f appserver/ejb/ejb-timer-service-app/pom.xml
```

## Module Type

- **Packaging**: `war`
- **Name**: "EJB Timer Service Application"

## Deployment

The timer service application is deployed as:
```
$PAYARA_HOME/glassfish/domains/domain1/autodeploy/ejb-timer-service-app.war
```

### Context Root

```
/ejb-timer-service
```

## Timer Management

### Timer Monitoring

The application provides monitoring for:
- Active timers
- Expired timers
- Cancelled timers
- Timer execution statistics

### Admin Endpoints

```
GET  /ejb-timer-service/timers       - List all timers
GET  /ejb-timer-service/timers/{id} - Get timer details
POST /ejb-timer-service/timers/{id}/cancel - Cancel timer
```

## Configuration

```xml
<ejb-container>
    <ejb-timer-service>
        <enabled>true</enabled>
        <timer-datasource>jdbc/__TimerPool</timer-datasource>
    </ejb-timer-service>
</ejb-container>
```

## Dependencies

| Dependency | Scope | Purpose |
|------------|-------|---------|
| `ejb-full-container` | provided | Container integration |

## Related Modules

- `ejb-container` - Timer service implementation
- `ejb-timer-databases` | Persistent storage
