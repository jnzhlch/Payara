# CLAUDE.md - Healthcheck Checker

This file provides guidance for working with the `healthcheck-checker` module - MicroProfile Health Check monitoring service.

## Module Overview

The healthcheck-checker module provides a Payara health check service that monitors MicroProfile Health Check endpoints of instances in a domain. It runs on the DAS (Domain Administration Server) and pings health endpoints of all instances to verify their status.

**Key Features:**
- **Distributed Health Monitoring** - Monitors MP Health endpoints across all domain instances
- **HTTP Status Mapping** - Maps HTTP status codes to health check results
- **Timeout Support** - Configurable timeout for health check calls
- **Cluster Support** - Works with both Payara Micro and standalone instances
- **Service Integration** - Integrates with Payara HealthCheckService

## Build Commands

```bash
# Build healthcheck-checker module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/healthcheck-checker/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/healthcheck-checker/pom.xml
```

## Architecture

### Health Check Flow

```
DAS HealthCheckService
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│          MicroProfileHealthChecker.doCheckInternal()           │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Check if running on DAS (only DAS runs this check)   │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Get timeout from configuration                        │ │
│  │     timeoutMillis = options.getTimeout()                 │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Ping all instances                                   │ │
│  │     tasks = pingAllInstances(timeoutMillis)              │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  4. Wait for results with timeout                        │ │
│  │     statusCode = task.get(timeoutMillis, MILLISECONDS)   │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  5. Map status code to health check entry                │ │
│  │     entryFromHttpStatusCode(statusCode)                  │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│                    pingAllInstances()                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  For each Server in domain.getServers():                  │ │
│  │  1. Get MicroprofileHealthCheckConfiguration             │ │
│  │  2. If enabled, build URI and ping endpoint               │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  For each InstanceDescriptor in clustered Payaras:        │ │
│  │  1. Execute get-microprofile-healthcheck-configuration    │ │
│  │  2. Parse response to get enabled status and endpoint     │ │
│  │  3. If enabled, build URI and ping endpoint               │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│                    pingHealthEndpoint()                         │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Open HTTP connection to URI                           │ │
│  │  2. Send GET request with Accept: application/json        │ │
│  │  3. Return HTTP status code                               │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### HTTP Status Code Mapping

```
┌────────────────────────────────────────────────────────────────┐
│              entryFromHttpStatusCode(statusCode)                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  200 → GOOD "UP"                                         │ │
│  │  - All health checks passed                              │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  503 → WARNING "DOWN"                                    │ │
│  │  - Service is down (at least one check failed)           │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  500 → CRITICAL "FAILURE"                                │ │
│  │  - Health check error                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Other → CHECK_ERROR "UNKNOWN RESPONSE"                  │ │
│  │  - Unexpected status code                                │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

## Core Components

### MicroProfileHealthChecker

```java
@Service(name = "healthcheck-mp")
@RunLevel(10)
public class MicroProfileHealthChecker
        extends BaseHealthCheck<HealthCheckTimeoutExecutionOptions,
                                MicroProfileHealthCheckerConfiguration> {

    @Inject
    private PayaraExecutorService payaraExecutorService;

    @Inject
    private PayaraInstanceImpl payaraMicro;

    @Override
    protected HealthCheckResult doCheckInternal() {
        // Only runs on DAS
        if (!envrionment.isDas()) {
            return new HealthCheckResult();
        }

        long timeoutMillis = options.getTimeout();
        Map<String, Future<Integer>> tasks = pingAllInstances(timeoutMillis);

        for (Future<Integer> task : tasks.values()) {
            try {
                int statusCode = task.get(timeoutMillis, MILLISECONDS);
                result.add(entryFromHttpStatusCode(statusCode));
            } catch (TimeoutException ex) {
                result.add(new HealthCheckResultEntry(CRITICAL,
                    "UNABLE TO CONNECT - " + ex.toString()));
            } catch (ExecutionException ex) {
                // Handle URISyntaxException, ProcessingException, etc.
            }
        }
        return result;
    }

    private Map<String, Future<Integer>> pingAllInstances(long timeoutMillis) {
        Map<String, Future<Integer>> tasks = new ConcurrentHashMap<>();

        // Check standard servers
        for (Server server : domain.getServers().getServer()) {
            tasks.put(server.getName(), payaraExecutorService.submit(() -> {
                MicroprofileHealthCheckConfiguration config =
                    server.getConfig().getExtensionByType(
                        MicroprofileHealthCheckConfiguration.class);
                if (config != null && Boolean.valueOf(config.getEnabled())) {
                    return pingHealthEndpoint(buildURI(server, config.getEndpoint()));
                }
                return -1;
            }));
        }

        // Check clustered Payara instances
        for (InstanceDescriptor instance : payaraMicro.getClusteredPayaras()) {
            tasks.put(instance.getInstanceName(), payaraExecutorService.submit(() -> {
                // Get MP Health config via asadmin
                ClusterCommandResult result = configs.get(instance.getMemberUUID())
                    .get(timeoutMillis, MILLISECONDS);
                // Parse and ping if enabled
                return pingHealthEndpoint(buildURI(instance, endpoint));
            }));
        }

        return tasks;
    }

    private static int pingHealthEndpoint(URI remote) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) remote.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        int statusCode = conn.getResponseCode();
        conn.disconnect();
        return statusCode;
    }
}
```

**Purpose:** Service that monitors MicroProfile Health Check endpoints of all instances in the domain.

### HealthCheckTimeoutExecutionOptions

```java
public class HealthCheckTimeoutExecutionOptions {
    private final boolean enabled;
    private final long time;
    private final TimeUnit unit;
    private final boolean addToMicroProfileHealth;
    private final long timeout;  // Request timeout in milliseconds

    public HealthCheckTimeoutExecutionOptions(boolean enabled, long time,
            TimeUnit unit, boolean addToMicroProfileHealth, long timeout) {
        this.enabled = enabled;
        this.time = time;
        this.unit = unit;
        this.addToMicroProfileHealth = addToMicroProfileHealth;
        this.timeout = timeout;
    }
}
```

**Purpose:** Configuration options for the MP Health checker including timeout value.

### SetMicroProfileHealthCheckerConfiguration

```java
@Service(name = "set-healthcheck-microprofile-health-checker-configuration")
@PerLookup
@ExecuteOn({RuntimeType.DAS})
@TargetType({CommandTarget.DAS, CommandTarget.CONFIG})
public class SetMicroProfileHealthCheckerConfiguration implements AdminCommand {

    @Param(name = "enabled", optional = false)
    private Boolean enabled;

    @Param(name = "add-to-microprofile-health", optional = true,
           defaultValue = "false")
    private Boolean addToMicroProfileHealth;

    @Param(name = "time", optional = true)
    @Min(value = 1)
    private String time;

    @Param(name = "unit", optional = true,
           acceptableValues = "DAYS,HOURS,MICROSECONDS,MILLISECONDS,MINUTES,NANOSECONDS,SECONDS")
    private String unit;

    @Param(name = "timeout", optional = true)
    @Min(value = 1)
    private String timeout;

    @Param(name = "dynamic", optional = true, defaultValue = "false")
    protected Boolean dynamic;

    @Override
    public void execute(AdminCommandContext context) {
        // Get or create checker configuration
        MicroProfileHealthCheckerConfiguration config =
            healthCheckServiceConfiguration.getCheckerByType(
                MicroProfileHealthCheckerConfiguration.class);

        // Apply values
        if (enabled != null) {
            config.setEnabled(enabled.toString());
        }
        if (timeout != null) {
            config.setTimeout(timeout);
        }
        // ... other values

        // If dynamic, restart the service
        if (dynamic) {
            service.setOptions(service.constructOptions(config));
            healthCheckService.registerCheck(config.getName(), service);
            healthCheckService.reboot();
        }
    }
}
```

**Purpose:** Admin command to configure the MicroProfile Health checker.

## Configuration

### Domain Configuration

```xml
<domain>
    <configs>
        <config name="server-config">
            <healthcheck-service-configuration>
                <checker name="MicroProfileHealthChecker" enabled="true"
                         time="30" unit="SECONDS" timeout="5000"
                         add-to-microprofile-health="false">
                </checker>
            </healthcheck-service-configuration>
        </config>
    </configs>
</domain>
```

### MicroProfile Health Check Configuration

```xml
<config name="server-config">
    <microprofile-healthcheck-configuration enabled="true"
                                            endpoint="health">
    </microprofile-healthcheck-configuration>
</config>
```

## Administration Commands

```bash
# Enable MP Health checker
asadmin set-microprofile-health-checker-configuration
    --enabled=true
    --time=30
    --unit=SECONDS
    --timeout=5000
    --dynamic=true

# Disable MP Health checker
asadmin set-microprofile-health-checker-configuration
    --enabled=false
    --dynamic=true

# Add to MicroProfile Health endpoint
asadmin set-microprofile-health-checker-configuration
    --enabled=true
    --add-to-microprofile-health=true
    --dynamic=true

# Get current configuration
asadmin get-microprofile-health-checker-configuration

# List all health checks
asadmin list-health-checks
```

## HTTP Status Code to Health Status Mapping

| HTTP Status | Health Status | Description |
|-------------|---------------|-------------|
| 200 | GOOD | UP - All health checks passed |
| 503 | WARNING | DOWN - Service is down |
| 500 | CRITICAL | FAILURE - Health check error |
| Other | CHECK_ERROR | UNKNOWN RESPONSE - Unexpected status |

## Error Handling

### Connection Errors

```java
// URISyntaxException
result.add(new HealthCheckResultEntry(CHECK_ERROR,
    "INVALID ENDPOINT: " + ((URISyntaxException) cause).getInput()));

// ProcessingException (network error)
result.add(new HealthCheckResultEntry(CRITICAL,
    "UNABLE TO CONNECT - " + cause.getMessage()));

// TimeoutException
result.add(new HealthCheckResultEntry(CRITICAL,
    "UNABLE TO CONNECT - " + ex.toString()));
```

### Negative Status Code

```java
// If MP Health is disabled on instance, returns -1
if (healthCheckConfig != null && Boolean.valueOf(healthCheckConfig.getEnabled())) {
    return pingHealthEndpoint(buildURI(server, endpoint));
}
return -1;  // Not added to results
```

## Notification Levels

```java
@Override
protected EventLevel createNotificationEventLevel(HealthCheckResultStatus checkResult) {
    if (checkResult == HealthCheckResultStatus.FINE ||
        checkResult == HealthCheckResultStatus.GOOD) {
        return EventLevel.INFO;
    }
    return EventLevel.WARNING;
}
```

## Package Structure

```
healthcheck-checker/
└── src/main/java/fish/payara/healthcheck/mphealth/
    ├── MicroProfileHealthChecker.java              # Main checker service
    ├── SetMicroProfileHealthCheckerConfiguration.java  # Admin command
    └── HealthCheckTimeoutExecutionOptions.java     # Execution options
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `healthcheck-core` | Base health check framework |
| `payara-micro-service` | Payara Micro instance descriptors |
| `nucleus/healthcheck` | Health check SPI and configuration |
| `microprofile/healthcheck` | MP Health Check configuration |

## Notes

- **DAS Only** - This checker only runs on the Domain Administration Server
- **Timeout Handling** - Requests that exceed timeout are marked as CRITICAL
- **Cluster Support** - Supports both standalone servers and Payara Micro instances
- **HTTP Protocol** - Uses HTTP/HTTPS based on server configuration
- **Port Resolution** - Automatically resolves instance ports from configuration
- **Dynamic Configuration** - Can be reconfigured at runtime with `--dynamic=true`
- **Add to MP Health** - When enabled, results are included in MP Health endpoint
