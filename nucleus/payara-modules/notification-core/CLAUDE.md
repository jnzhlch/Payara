# CLAUDE.md - Notification Core

This file provides guidance for working with the `notification-core` module - Payara's notification system for distributing alerts and events.

## Module Overview

The `notification-core` module provides a flexible notification system that receives events from various Payara services (health checks, request tracing) and distributes them to configured notifiers (log, event bus, CDI events).

## Build Commands

```bash
# Build notification-core module
mvn -DskipTests clean package -f nucleus/payara-modules/notification-core/pom.xml

# Build with tests
mvn clean package -f nucleus/payara-modules/notification-core/pom.xml
```

## Architecture

### Service Components

```
NotificationService (NotifierManager)
        │
        ├─→ Configuration
        │      └─→ NotificationServiceConfiguration
        │
        ├─→ Notifier implementations
        │      ├─→ LogNotifier (log file output)
        │      ├─→ EventBusNotifier (internal event distribution)
        │      └─── (JMS/CDI notifiers in separate modules)
        │
        ├─→ NotifierHandler
        │      ├─→ Discovers notifier services
        │      └─→ Routes notifications to notifiers
        │
        └─→ HK2 Messaging
               └─→ @MessageReceiver integration
```

### Notification Flow

```
Event Source (HealthCheckService, RequestTracingService)
        │
        ├─→ Create PayaraNotification
        │      ├─→ EventLevel (INFO, WARNING, CRITICAL)
        │      ├─→ Subject
        │      └─→ Message
        │
        ├─→ Send to NotificationService
        │      │
        │      ├─→ LogNotifier → writes to server.log
        │      ├─→ EventBusNotifier → publishes to Hazelcast
        │      └─→ CDIEventNotifier → fires CDI event
        │
        └─→ Each Notifier handles notification
               └─→ Delivers to target system
```

## Core Interfaces

### NotifierManager

```java
public interface NotifierManager {

    /**
     * Send notification to all configured notifiers
     */
    void notify(PayaraNotification notification);

    /**
     * Send notification to specific notifier
     */
    void notify(PayaraNotification notification, String notifierName);
}
```

### PayaraNotifier

```java
public interface PayaraNotifier {

    /**
     * Called to deliver notification
     */
    void notify(NotificationEvent notification);

    /**
     * Get notifier configuration
     */
    PayaraNotifierConfiguration getConfiguration();

    /**
     * Get notifier name
     */
    String getName();
}
```

### NotificationEvent

```java
public class NotificationEvent {

    private final EventLevel level;       // INFO, WARNING, CRITICAL
    private final String subject;
    private final String message;
    private final long timestamp;

    // EventLevel enum
    public enum EventLevel {
        INFO,
        WARNING,
        CRITICAL
    }
}
```

## Built-in Notifiers

### LogNotifier

Writes notifications to server log:

```java
@Service(name = "log-notifier")
public class LogNotifier implements PayaraNotifier {

    @Override
    public void notify(NotificationEvent event) {
        Logger logger = Logger.getLogger("PayaraNotifications");

        Level level = mapToLogLevel(event.getLevel());
        logger.log(level, "{0}: {1}",
            new Object[]{event.getSubject(), event.getMessage()});
    }
}
```

### EventBusNotifier

Publishes to Hazelcast event bus:

```java
@Service(name = "eventbus-notifier")
public class EventBusNotifier implements PayaraNotifier {

    @Inject
    private EventBus eventBus;

    @Override
    public void notify(NotificationEvent event) {
        // Publish to cluster-wide topic
        eventBus.publish(
            PayaraNotificationService.NOTIFICATION_TOPIC,
            event
        );
    }
}
```

## Using Notification Service

### Sending Notifications

```java
@Service
public class MyService {

    @Inject
    private NotificationService notificationService;

    public void doSomething() {
        try {
            // Do work
        } catch (Exception e) {
            // Send notification on failure
            PayaraNotification notification =
                PayaraNotificationFactory.create(
                    EventLevel.WARNING,
                    "Operation failed",
                    e.getMessage()
                );

            notificationService.notify(notification);
        }
    }
}
```

### With @MessageReceiver

```java
@Service
@MessageReceiver
public class MyNotificationReceiver {

    public void receive(PayaraNotification notification) {
        // Automatically called when notification sent
        handleNotification(notification);
    }
}
```

## Configuration

### Enable Notification Service

```bash
# Enable notification service
asadmin notification-configure --enabled true
```

### Configure Notifier

```bash
# Configure log notifier
asadmin notification-notifier-configure \
    --notifierType LOG \
    --enabled true

# Configure additional notifiers (in other modules)
asadmin notification-notifier-configure \
    --notifierType CDI \
    --enabled true
```

### List Notifiers

```bash
# List all configured notifiers
asadmin list-notifiers

# Get notification configuration
asadmin get-notification-configuration
```

## Admin Commands

### Configuration Commands

```bash
# Set notification service configuration
asadmin set-notification-configuration \
    --enabled=true \
    --log-notifier-enabled=true

# Set log notifier configuration
asadmin set-log-notifier-configuration \
    --enabled=true \
    --useSeparateLogFile=false
```

### Query Commands

```bash
# Get configuration
asadmin get-notification-configuration

# Get log notifier configuration
asadmin get-log-notifier-configuration

# List notifiers
asadmin list-notifiers

# Test notifier
asadmin test-notifier --notifierType LOG \
    --subject "Test Subject" \
    --message "Test Message"
```

## Creating Custom Notifiers

### Notifier Implementation

```java
package com.example.notification;

import fish.payara.internal.notification.PayaraNotification;
import fish.payara.internal.notification.PayaraNotifier;
import fish.payara.internal.notification.PayaraNotifierConfiguration;
import org.jvnet.hk2.annotations.Service;

@Service(name = "my-custom-notifier")
public class MyCustomNotifier implements PayaraNotifier {

    private MyNotifierConfiguration config;

    @Override
    public void notify(NotificationEvent event) {
        // Deliver notification
        if (config.isEnabled()) {
            sendToTarget(event);
        }
    }

    @Override
    public PayaraNotifierConfiguration getConfiguration() {
        return config;
    }

    @Override
    public String getName() {
        return "My Custom Notifier";
    }

    private void sendToTarget(NotificationEvent event) {
        // Implementation-specific delivery
        System.out.println(event.getSubject() + ": " + event.getMessage());
    }
}
```

### Notifier Configuration

```java
@Configured
public interface MyNotifierConfiguration extends PayaraNotifierConfiguration {

    @Attribute(defaultValue = "true")
    boolean isEnabled();

    @Attribute
    String getTargetEndpoint();

    void setTargetEndpoint(String endpoint);
}
```

### Admin Command for Configuration

```java
@Service(name = "set-my-notifier-configuration")
@PerLookup
@ExecuteOn(RuntimeType.DAS)
public class SetMyNotifierConfiguration implements AdminCommand {

    @Param(name = "enabled", optional = true)
    private Boolean enabled;

    @Param(name = "targetEndpoint", optional = true)
    private String targetEndpoint;

    @Inject
    private ServiceLocator habitat;

    @Override
    public void execute(AdminCommandContext context) {
        // Update configuration using ConfigSupport
        ConfigSupport.apply(new SingleConfigCode<MyNotifierConfiguration>() {
            @Override
            public Object run(MyNotifierConfiguration config) {
                if (enabled != null) {
                    config.setEnabled(enabled);
                }
                if (targetEndpoint != null) {
                    config.setTargetEndpoint(targetEndpoint);
                }
                return null;
            }
        }, configuration);
    }
}
```

## Notification Events

### Event Types

```java
// Health check failure
HealthCheckNotificationEvent
├── checkerName
├── thresholdValue
└── actualValue

// Request tracing alert
RequestTracingNotificationEvent
├── traceId
├── duration
└── thresholdBreached

// Custom event
PayaraNotification
├── level (INFO, WARNING, CRITICAL)
├── subject
└── message
```

### Receiving Notification Events

```java
// Subscribe via EventBus
@Service
@MessageReceiver
@SubscribeTo(value = PayaraNotificationService.NOTIFICATION_TOPIC,
              scope = Scope.CLUSTER)
public class NotificationEventListener {

    public void onNotification(NotificationEvent event) {
        if (event.getLevel() == EventLevel.CRITICAL) {
            // Handle critical notifications
            alertAdmin(event);
        }
    }
}
```

## Notification Factory

```java
// Create notification with factory
PayaraNotification notification = PayaraNotificationFactory.create(
    EventLevel.WARNING,
    "Custom Subject",
    "Custom message text"
);

// Send notification
notificationService.notify(notification);
```

## Best Practices

1. **Use appropriate event levels** - CRITICAL for service-impacting issues
2. **Keep messages concise** - Notifications should be actionable
3. **Don't spam** - Avoid sending notifications for routine operations
4. **Handle notifier failures** - Notifiers should not throw exceptions
5. **Use separate log files if needed** - Configure LogNotifier with dedicated file

## Package Structure

```
fish.payara.nucleus.notification/
├── NotificationService.java           # Main service
├── NotifierHandler.java              # Notifier discovery
├── log/
│   ├── LogNotifier.java              # Log implementation
│   └── LogNotifierConfiguration.java
└── admin/
    ├── NotificationConfigurer.java
    ├── GetNotificationConfigurationCommand.java
    ├── SetNotificationConfiguration.java
    ├── NotifierServiceLister.java
    └── TestNotifier.java
```

## Related Modules

- **healthcheck-core** - Health check notifications
- **requesttracing-core** - Request tracing notifications
- **notification-eventbus-core** - EventBus notifier
- **notification-cdi-eventbus-core** - CDI event notifier

## Troubleshooting

### Notifications Not Being Sent

```bash
# Check service is enabled
asadmin get-notification-configuration

# Check notifier is enabled
asadmin list-notifiers

# Test notifier
asadmin test-notifier --notifierType LOG
```

### EventBus Notifications Not Received

```bash
# Check Hazelcast is enabled
asadmin get-hazelcast-configuration

# Check event bus topic subscription
# (depends on your event bus setup)
```

### Log File Not Created

```bash
# Check separate log file configuration
asadmin get-log-notifier-configuration

# Check file permissions
ls -la domains/domain1/logs/
```

## Notes

- NotificationService is a @MessageReceiver - can receive notifications via HK2 messaging
- Notifiers are discovered via HK2 service locator
- Built-in notifiers: Log, EventBus (CDI in separate module)
- Notifications are asynchronous - sending doesn't block
