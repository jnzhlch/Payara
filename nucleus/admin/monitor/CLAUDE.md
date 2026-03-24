# CLAUDE.md - Admin Monitor

This file provides guidance for working with the Admin Monitor module - monitoring infrastructure for Payara.

## Module Overview

The Monitor module provides the Monitoring and Performance Monitoring (MPM) infrastructure, enabling runtime monitoring of server components, call flow monitoring, and JMX integration.

## Key Components

### Monitoring Framework

**Core monitoring interfaces:**
- `Monitorable` - Objects that can be monitored
- `MonitoringSource` - Source of monitoring data
- `ProbeProvider` - JMX probe provider
- `StatsProvider` - Statistics provider

### Call Flow Monitoring

**Call flow tracking:**
- Request tracing through components
- Performance measurement
- Bottleneck identification

### JMX Integration

**JMX MBeans:**
- MBean registration
- JMX notifications
- MBean server integration

## Build Commands

```bash
# Build monitor module
mvn -DskipTests clean package -f nucleus/admin/monitor/pom.xml

# Build with tests
mvn clean package -f nucleus/admin/monitor/pom.xml
```

## Monitoring Pattern

### Creating a Monitorable Component

```java
import org.glassfish.api.monitoring.Monitorable;

@Service
public class MyComponent implements Monitorable {
    @Override
    public String getMonitorableId() {
        return "my-component";
    }

    @Override
    public String getMonitorableType() {
        return "custom";
    }

    @Override
    public String getDescription() {
        return "My custom monitorable component";
    }
}
```

### Providing Statistics

```java
import org.glassfish.external.statistics.Statistic;
import org.glassfish.external.statistics.impl.StatisticImpl;
import org.glassfish.external.statistics.annotations.Reset;
import org.glassfish.gmbal.Description;

@Service
@AMXMetadata(type = "my-component-mon")
public class MyComponentStats {
    private long requestCount = 0;

    @Description("Total request count")
    public long getRequestCount() {
        return requestCount;
    }

    @Reset
    public void reset() {
        requestCount = 0;
    }
}
```

### Probe Provider

```java
import org.glassfish.external.probe.provider.annotations.Probe;
import org.glassfish.external.probe.provider.annotations.ProbeProvider;

@ProbeProvider(moduleName = "my-module",
               probeProviderName = "my-component",
               monitorOn = true)
public class MyComponentProbeProvider {

    @Probe("myMethodEntered")
    public void myMethodEnteredEvent(String param) {
        // Fired when method is entered
    }

    @Probe("myMethodExited")
    public void myMethodExitedEvent(String param, boolean success) {
        // Fired when method exits
    }
}
```

## Monitoring Configuration

```bash
# Enable monitoring for a component
asadmin set-monitoring-level --module my-module --level HIGH

# Disable monitoring
asadmin set-monitoring-level --module my-module --level OFF

# List monitoring components
asadmin list-monitoring
```

## JMX Management

### Accessing via JConsole

```bash
# Start server with JMX enabled
./asadmin start-domain

# Connect with JConsole
jconsole localhost:8686
```

### MBean Naming

```
domain:type=my-component-mon,name=my-component
```

## Call Flow Monitoring

```bash
# Enable call flow monitoring
asadmin configure-call-flow-monitoring --enabled true

# View call flow data
asadmin get-call-flow-data
```

## Monitoring Levels

| Level | Description |
|-------|-------------|
| OFF | No monitoring |
| LOW | Basic statistics |
| HIGH | Detailed statistics with call flow |

## Key Packages

- `org.glassfish.api.monitoring` - Core monitoring API
- `org.glassfish.external.statistics` - Statistics interfaces
- `org.glassfish.external.probe.provider` - Probe provider annotations
- `org.glassfish.gmbal` - GlassFish MBean annotation library

## Architecture Notes

1. **HK2-based** - Monitorable components are HK2 services
2. **JMX standard** - Uses standard JMX for external monitoring
3. **Zero overhead when disabled** - Monitoring code only executes when enabled
4. **Dynamic enablement** - Can enable/disable at runtime
5. **Probe-based** - Uses probe providers for event generation
