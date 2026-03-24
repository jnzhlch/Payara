# CLAUDE.md - Healthcheck Metrics

This file provides guidance for working with the `healthcheck-metrics` module - MicroProfile Metrics integration with Payara Health Check service.

## Module Overview

The healthcheck-metrics module provides a Payara health check service that monitors MicroProfile Metrics. It allows specific metrics to be monitored and exposes their values through the health check system.

**Key Features:**
- **Metric Monitoring** - Monitor specific MicroProfile metrics
- **Health Integration** - Expose metric values as health check entries
- **Filtered Export** - Only export configured monitored metrics
- **Multi-Context** - Supports metrics from multiple application contexts

## Build Commands

```bash
# Build healthcheck-metrics module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/healthcheck-metrics/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/healthcheck-metrics/pom.xml
```

## Architecture

### Health Check Flow

```
HealthCheckService Scheduled Execution
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│          MicroProfileMetricsCheck.doCheckInternal()             │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Get monitored metrics from options                   │ │
│  │     List<MonitoredMetric> monitoredMetrics = options...   │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Write metrics to buffer                              │ │
│  │     String data = write()                                │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Create health check result                           │ │
│  │     if (data.isEmpty()) {                                │ │
│  │       WARNING - "The metrics you have added..."          │ │
│  │     } else {                                             │ │
│  │       GOOD - data (metric values)                        │ │
│  │     }                                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│                     write()                                     │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Filter metrics by monitored metric names             │ │
│  │     FilteredMetricsExporter(buffer, metricNames)         │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Write metrics for all contexts                       │ │
│  │     writer.write()                                       │ │
│  │     - Uses MetricsService.getContextNames()              │ │
│  │     - Uses metricsService::getContext for each context   │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Get and clean buffer content                         │ │
│  │     buffer.toString().trim().replaceAll(",$", "")        │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

## Core Components

### MicroProfileMetricsCheck

```java
@Service(name = "healthcheck-mpmetrics")
@RunLevel(StartupRunLevel.VAL)
public class MicroProfileMetricsCheck
        extends BaseHealthCheck<HealthCheckMicroProfileMetricstExecutionOptions,
                                MicroProfileMetricsChecker> {

    @Inject
    private MetricsService metricsService;

    private StringWriterProxy buffer;
    private MetricsWriter writer;

    @Override
    public synchronized HealthCheckMicroProfileMetricstExecutionOptions
            constructOptions(MicroProfileMetricsChecker checker) {
        // Build set of metric names to monitor
        Set<String> metricNames = new HashSet<>();
        for (MonitoredMetric metric : checker.getMonitoredMetrics()) {
            metricNames.add(metric.getMetricName());
        }

        // Create filtered metrics writer
        this.buffer = new StringWriterProxy();
        this.writer = new MetricsWriterImpl(
            new FilteredMetricsExporter(buffer, metricNames),
            metricsService.getContextNames(),
            metricsService::getContext
        );

        return new HealthCheckMicroProfileMetricstExecutionOptions(
            Boolean.valueOf(checker.getEnabled()),
            Long.parseLong(checker.getTime()),
            asTimeUnit(checker.getUnit()),
            Boolean.valueOf(checker.getAddToMicroProfileHealth()),
            checker.getMonitoredMetrics()
        );
    }

    @Override
    protected HealthCheckResult doCheckInternal() {
        List<MonitoredMetric> monitoredMetrics = options.getMonitoredMetrics();
        HealthCheckResult result = new HealthCheckResult();

        try {
            if (monitoredMetrics != null && !monitoredMetrics.isEmpty()) {
                final String data = write();
                result.add(new HealthCheckResultEntry(
                    data.isEmpty() ? WARNING : GOOD,
                    data.isEmpty()
                        ? "The metrics you have added for monitoring doesn't exist"
                        : data
                ));
            } else {
                result.add(new HealthCheckResultEntry(CRITICAL,
                    "No metric has been added for monitoring."));
            }
        } catch (IOException ex) {
            result.add(new HealthCheckResultEntry(CRITICAL,
                "Failed to write metrics to stream."));
        }
        return result;
    }

    private synchronized String write() throws IOException {
        writer.write();
        final String result = buffer.toString().trim().replaceAll(",$", "");
        this.buffer.clear();
        return result;
    }
}
```

**Purpose:** Health check service that monitors MicroProfile metrics and exposes them as health check data.

### StringWriterProxy

```java
public class StringWriterProxy {
    private final StringWriter delegate;

    public StringWriterProxy() {
        this.delegate = new StringWriter();
    }

    public Writer getWriter() {
        return delegate;
    }

    public String toString() {
        return delegate.toString();
    }

    public void clear() {
        delegate.getBuffer().setLength(0);
    }
}
```

**Purpose:** Wrapper around StringWriter that provides buffer clearing functionality.

### FilteredMetricsExporter

```java
// From microprofile/metrics module
public class FilteredMetricsExporter implements MetricsExporter {
    private final Writer writer;
    private final Set<String> metricNames;  // Only export these metrics

    @Override
    public void export(Map<String, Metric> metrics) {
        for (Map.Entry<String, Metric> entry : metrics.entrySet()) {
            if (metricNames.contains(entry.getKey())) {
                // Write metric to output
                writer.write(entry.getKey() + " " + entry.getValue());
            }
        }
    }
}
```

**Purpose:** Filters metrics to only export configured monitored metric names.

## Configuration

### Domain Configuration

```xml
<domain>
    <configs>
        <config name="server-config">
            <healthcheck-service-configuration>
                <checker name="MicroProfileMetricsChecker" enabled="true"
                         time="60" unit="SECONDS"
                         add-to-microprofile-health="false">
                    <monitored-metrics>
                        <monitored-metric name="builtin.thread.count"/>
                        <monitored-metric name="application.api.requests.count"/>
                        <monitored-metric name="application.api.requests.time"/>
                    </monitored-metrics>
                </checker>
            </healthcheck-service-configuration>
        </config>
    </configs>
</domain>
```

## Administration Commands

```bash
# Enable MP Metrics checker with monitored metrics
asadmin set-healthcheck-microprofile-metrics-checker-configuration
    --enabled=true
    --time=60
    --unit=SECONDS
    --monitoredmetric=builtin.thread.count
    --monitoredmetric=application.api.requests.count
    --dynamic=true

# Add another metric to monitor
asadmin set-healthcheck-microprofile-metrics-checker-configuration
    --monitoredmetric=application.database.connections.active
    --dynamic=true

# Disable MP Metrics checker
asadmin set-healthcheck-microprofile-metrics-checker-configuration
    --enabled=false
    --dynamic=true

# Get current configuration
asadmin get-healthcheck-microprofile-metrics-checker-configuration
```

## Health Status Mapping

| Condition | Health Status | Message |
|-----------|---------------|---------|
| Metrics found and exported | GOOD | Metric data (JSON/OpenMetrics format) |
| No monitored metrics found | WARNING | "The metrics you have added for monitoring doesn't exist" |
| No metrics configured | CRITICAL | "No metric has been added for monitoring." |
| Write exception | CRITICAL | "Failed to write metrics to stream." |

## Metric Format

Metrics are exported in the format used by the MetricsWriter (typically OpenMetrics or JSON format):

```
# OpenMetrics format example
builtin_thread_count 42
application_api_requests_count{method="GET",path="/api/users"} 1234
application_api_requests_time{method="GET",path="/api/users",quantile="0.5"} 23.5
```

## Notification Levels

```java
@Override
protected EventLevel createNotificationEventLevel(HealthCheckResultStatus checkResult) {
    return EventLevel.INFO;  // Always INFO regardless of status
}
```

## Package Structure

```
healthcheck-metrics/
└── src/main/java/fish/payara/healthcheck/microprofile/metrics/
    ├── MicroProfileMetricsCheck.java               # Main checker service
    ├── HealthCheckMicroProfileMetricstExecutionOptions.java  # Execution options
    └── StringWriterProxy.java                      # Writer wrapper
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `healthcheck-core` | Base health check framework |
| `microprofile/metrics` | MetricsService and MetricsWriter |
| `nucleus/healthcheck` | Health check SPI and configuration |

## Notes

- **Synchronized Access** - The write() method is synchronized to prevent concurrent access
- **Buffer Reuse** - The buffer is cleared and reused between health check executions
- **Metric Filtering** - Only configured monitored metrics are exported
- **Multi-Context** - Supports metrics from all application contexts registered with MetricsService
- **No Threshold Checking** - This checker only exposes metric values, doesn't evaluate thresholds
- **Dynamic Updates** - Can add/remove monitored metrics at runtime with `--dynamic=true`
