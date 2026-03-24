# CLAUDE.md - Health Check Core

This file provides guidance for working with the `healthcheck-core` module - Payara's health monitoring system.

## Module Overview

The `healthcheck-core` module provides a configurable health monitoring system that periodically checks server resources and can trigger notifications when thresholds are breached.

## Build Commands

```bash
# Build healthcheck-core module
mvn -DskipTests clean package -f nucleus/payara-modules/healthcheck-core/pom.xml

# Build with tests
mvn clean package -f nucleus/payara-modules/healthcheck-core/pom.xml
```

## Architecture

### Service Components

```
HealthCheckService (@RunLevel(StartupRunLevel.VAL))
        │
        ├─→ Configuration
        │      └─→ HealthCheckServiceConfiguration
        │
        ├─→ HealthCheckTask
        │      ├─→ Scheduled execution
        │      ├─→ Runs all enabled checkers
        │      └─→ Stores historical results
        │
        ├─→ Checker implementations (preliminary package)
        │      ├─→ CpuUsageHealthCheck
        │      ├─→ HeapMemoryUsageHealthCheck
        │      ├─→ MachineMemoryUsageHealthCheck
        │      ├─→ GarbageCollectorHealthCheck
        │      ├─→ HoggingThreadsHealthCheck
        │      └─→ StuckThreadsHealthCheck
        │
        └─→ NotificationService
               └─→ Notified on health check failure
```

### Health Check Flow

```
Scheduled Execution (every N seconds)
        │
        ├─→ For each enabled checker:
        │      ├─→ Call perform() method
        │      ├─→ Collect HealthCheckResult
        │      ├─→ Check threshold
        │      └─→ Store result historically
        │
        └─→ On threshold breach
               ├─→ Send notification
               └─→ Trigger configured actions
```

## Built-in Health Checkers

### CpuUsageHealthCheck

Checks CPU usage of the JVM process.

```java
@CheckerType("cpu")
public class CpuUsageHealthCheck extends BaseThresholdHealthCheck {

    @Override
    protected HealthCheckResult doCheck() {
        // Gets CPU usage via OperatingSystemMXBean
        double cpuUsage = getSystemCpuLoad();

        if (cpuUsage > getThreshold()) {
            return HealthCheckResult.failure(
                "CPU usage (" + cpuUsage + "%) exceeds threshold"
            );
        }
        return HealthCheckResult.success();
    }
}
```

### HeapMemoryUsageHealthCheck

Checks heap memory usage.

```java
@CheckerType("heap")
public class HeapMemoryUsageHealthCheck extends BaseThresholdHealthCheck {

    @Override
    protected HealthCheckResult doCheck() {
        MemoryMXBean memoryBean = ...;
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        double usedPercentage = (double) heapUsage.getUsed() / heapUsage.getMax() * 100;

        if (usedPercentage > getThreshold()) {
            return HealthCheckResult.failure(
                "Heap memory usage (" + usedPercentage + "%) exceeds threshold"
            );
        }
        return HealthCheckResult.success();
    }
}
```

### MachineMemoryUsageHealthCheck

Checks machine-level memory usage.

### GarbageCollectorHealthCheck

Checks GC time percentage.

### HoggingThreadsHealthCheck

Detects threads consuming excessive CPU time.

```java
@CheckerType("hogging")
public class HoggingThreadsHealthCheck extends BaseHealthCheck {

    private int staleTime = 60;  // seconds

    @Override
    protected HealthCheckResult doCheck() {
        // Detect threads that have been running > staleTime
        List<Long> hoggingThreadIds = detectHoggingThreads();

        if (!hoggingThreadIds.isEmpty()) {
            return HealthCheckResult.failure(
                hoggingThreadIds.size() + " hogging threads detected"
            );
        }
        return HealthCheckResult.success();
    }
}
```

### StuckThreadsHealthCheck

Detects threads stuck in wait/sleep.

## Configuration

### Enable Health Check Service

```bash
# Enable health check system
asadmin set-healthcheck-service-configuration \
    --enabled=true \
    --dynamic=true \
    --time=10 \
    --unit=seconds
```

### Configure Individual Checkers

```bash
# CPU checker with threshold
asadmin set-healthcheck-service-configuration \
    --checkerName=cpu \
    --enabled=true \
    --threshold=80 \
    --thresholdUnit=PERCENTAGE

# Heap memory checker
asadmin set-healthcheck-service-configuration \
    --checkerName=heap \
    --enabled=true \
    --threshold=90 \
    --thresholdUnit=PERCENTAGE

# Hogging threads checker
asadmin set-healthcheck-service-configuration \
    --checkerName=hogging \
    --enabled=true \
    --time=30 \
    --unit=seconds
```

### List Health Checks

```bash
# List all health checkers
asadmin list-healthcheck-services

# Get health check configuration
asadmin get-healthcheck-configuration

# Get health check status
asadmin get-healthcheck --target server
```

## Extending Health Checks

### Creating Custom Checker

```java
package com.example.healthcheck;

import fish.payara.nucleus.healthcheck.HealthCheckResult;
import fish.payara.nucleus.healthcheck.preliminary.BaseHealthCheck;
import org.jvnet.hk2.annotations.Service;

@Service(name = "my-health-check")
public class MyHealthCheck extends BaseHealthCheck {

    @Override
    public String getName() {
        return "My Custom Health Check";
    }

    @Override
    public HealthCheckResult perform() {
        try {
            // Perform health check logic
            int metricValue = checkMetric();

            if (metricValue > 100) {
                return HealthCheckResult.failure(
                    "Metric value too high: " + metricValue
                );
            }

            return HealthCheckResult.success(
                "Metric value acceptable: " + metricValue
            );

        } catch (Exception e) {
            return HealthCheckResult.error(e);
        }
    }

    private int checkMetric() {
        // Implement health check logic
        return 0;
    }
}
```

### Threshold-Based Checker

```java
@Service(name = "my-threshold-check")
public class MyThresholdHealthCheck extends BaseThresholdHealthCheck {

    public MyThresholdHealthCheck() {
        // Use threshold from configuration
    }

    @Override
    protected HealthCheckResult doCheck() {
        double value = getMetricValue();

        if (value > getThreshold()) {
            return HealthCheckResult.failure(
                String.format("Value %.2f exceeds threshold %.2f",
                    value, getThreshold())
            );
        }

        return HealthCheckResult.success();
    }

    private double getMetricValue() {
        // Return metric value
        return 0.0;
    }
}
```

## Health Check Result

```java
public class HealthCheckResult {
    private final HealthCheckStatus status;
    private final String message;

    // Status values
    public enum HealthCheckStatus {
        OK,        // Health check passed
        WARNING,   // Threshold breached but not critical
        CRITICAL,  // Critical failure
        ERROR      // Error during check
    }

    // Factory methods
    public static HealthCheckResult success() {
        return new HealthCheckResult(HealthCheckStatus.OK, null);
    }

    public static HealthCheckResult success(String message) {
        return new HealthCheckResult(HealthCheckStatus.OK, message);
    }

    public static HealthCheckResult failure(String message) {
        return new HealthCheckResult(HealthCheckStatus.CRITICAL, message);
    }

    public static HealthCheckResult error(Throwable t) {
        return new HealthCheckResult(HealthCheckStatus.ERROR, t.getMessage());
    }
}
```

## Historical Health Checks

### Storing Results

```java
@Inject
private HealthCheckService healthCheckService;

// Health check results are automatically stored
// in HistoricHealthCheckEventStore

// Maximum history size configured via
// --historic-trace-size option
```

### Querying Historical Results

```bash
# List historic health checks
asadmin list-historic-healthchecks

# Options:
# --target server
# --checkerName cpu
# --from "2025-01-01T00:00:00"
# --to "2025-01-31T23:59:59"
```

## Health Check Events

### Event Publishing

Health checks publish events to the notification system:

```java
PayaraNotification notification = new PayaraNotification(
    PayaraNotification.EventLevel.CRITICAL,
    "Health Check Failure: " + checkerName,
    healthCheckResult.getMessage()
);

notificationService.notify(notification);
```

### Receiving Health Check Events

```java
@Service
@MessageReceiver
@SubscribeTo(value = PayaraHealthCheckServiceEvents.HEALTH_CHECK_EVENT_TOPIC)
public class HealthCheckEventListener {

    public void receive(PayaraNotification notification) {
        if (notification.getLevel() == EventLevel.CRITICAL) {
            // Handle critical health check failure
            String message = notification.getMessage();
            alertAdmin(message);
        }
    }
}
```

## Admin Commands

### Configuration Commands

```bash
# Set health check service configuration
asadmin set-healthcheck-service-configuration \
    --enabled=true \
    --dynamic=true \
    --time=30 \
    --unit=seconds \
    --historic-trace-size=100

# Set specific checker configuration
asadmin set-healthcheck-service-configuration \
    --checkerName=cpu \
    --enabled=true \
    --threshold=80 \
    --thresholdUnit=PERCENTAGE \
    --time=10 \
    --unit=seconds
```

### Query Commands

```bash
# Get configuration
asadmin get-healthcheck-configuration

# List health check services
asadmin list-healthcheck-services

# Get health check status
asadmin get-healthcheck --target server

# List historic health checks
asadmin list-historic-healthchecks \
    --checkerName=cpu \
    --target server
```

## Configuration Hierarchy

```
domain.xml
├── configs
│   └── config
│       └── healthcheck-service-configuration
│           ├── enabled (boolean)
│           ├── dynamic (boolean)
│           ├── time (int)
│           ├── unit (string)
│           ├── historic-trace-size (int)
│           └── checker*
│               ├── enabled (boolean)
│               ├── name (string)
│               ├── threshold (double)
│               ├── thresholdUnit (string)
│               ├── time (int)
│               └── unit (string)
```

## Package Structure

```
fish.payara.nucleus.healthcheck/
├── HealthCheckService.java           # Main service
├── HealthCheckResult.java            # Result object
├── HealthCheckTask.java              # Scheduled task
├── HistoricHealthCheckEventStore.java
├── configuration/
│   ├── Checker.java                  # Base config
│   ├── CheckerType.java              # Checker types
│   ├── HealthCheckServiceConfiguration.java
│   └── {Checker}Checker.java         # Specific configs
├── preliminary/
│   ├── BaseHealthCheck.java          # Base class
│   ├── BaseThresholdHealthCheck.java
│   └── {Type}HealthCheck.java        # Implementations
└── admin/
    ├── SetHealthCheckConfiguration.java
    ├── GetHealthCheckConfiguration.java
    └── ListHealthCheckServices.java
```

## Related Modules

- **notification-core** - Health check notifications
- **hazelcast-bootstrap** - Clustered health checks
- **healthcheck-cpool** - Connection pool health checks
- **healthcheck-stuck** - Stuck thread health checks

## Best Practices

1. **Use appropriate thresholds** - Set realistic thresholds based on monitoring
2. **Check execution time** - Health checks should complete quickly
3. **Handle exceptions** - Always return HealthCheckResult.error(e) on exception
4. **Use meaningful messages** - Messages should explain the failure clearly
5. **Consider cluster impact** - In clustered environments, checks run on all instances

## Troubleshooting

### Health Check Not Running

```bash
# Check if service is enabled
asadmin get-healthcheck-configuration

# Check if checker is enabled
asadmin list-healthcheck-services

# Check service log level
tail -f domains/domain1/logs/server.log | grep healthcheck
```

### Threshold Not Triggering

```bash
# Check threshold value
asadmin get-healthcheck-configuration --checkerName=cpu

# Check threshold unit (PERCENTAGE vs BYTES)
asadmin get "healthcheck-service-configuration.checker.cpu.threshold-unit"
```

### Performance Impact

```bash
# Increase check interval if needed
asadmin set-healthcheck-service-configuration --time=60 --unit=seconds

# Disable specific checkers
asadmin set-healthcheck-service-configuration \
    --checkerName=gpu \
    --enabled=false
```
