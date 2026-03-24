# CLAUDE.md - Appserver Payara Modules

This file provides guidance for working with the `appserver/payara-appserver-modules` module - Payara-specific application server features and MicroProfile implementations.

## Module Overview

The appserver/payara-appserver-modules module contains Payara Server's proprietary features and extensions beyond standard Jakarta EE. This includes MicroProfile implementations, clustering features, monitoring, health checks, and Payara Micro integration.

**Key Areas:**
- **MicroProfile** - Config, Health Check, Metrics, Fault Tolerance, JWT Auth, OpenAPI, REST Client, Telemetry
- **Clustering** - Hazelcast-based EJB timers, EclipseLink coordination, distributed caching
- **Monitoring** - JMX monitoring service, health check integration
- **Security** - CDI authorization, JASPIC utilities, YubiKey authentication
- **Payara Micro** - Micro-specific services and CDI extensions

## 子目录文档索引 (Subdirectory Documentation)

### MicroProfile 模块

| 子目录 | 文档位置 | 说明 |
|--------|----------|------|
| **microprofile/config** | [microprofile/config/CLAUDE.md](microprofile/config/CLAUDE.md) | MicroProfile Config API - @ConfigProperty注入 |
| **microprofile/healthcheck** | [microprofile/healthcheck/CLAUDE.md](microprofile/healthcheck/CLAUDE.md) | MicroProfile Health Check API - 健康检查端点 |
| **microprofile/metrics** | [microprofile/metrics/CLAUDE.md](microprofile/metrics/CLAUDE.md) | MicroProfile Metrics API - 指标收集 |
| **microprofile/jwt-auth** | [microprofile/jwt-auth/CLAUDE.md](microprofile/jwt-auth/CLAUDE.md) | MicroProfile JWT Auth API - JWT认证 |
| **microprofile/fault-tolerance** | [microprofile/fault-tolerance/CLAUDE.md](microprofile/fault-tolerance/CLAUDE.md) | MicroProfile Fault Tolerance API - 容错策略 |
| **microprofile/openapi** | [microprofile/openapi/CLAUDE.md](microprofile/openapi/CLAUDE.md) | MicroProfile OpenAPI - OpenAPI文档生成 |
| **microprofile/telemetry** | [microprofile/telemetry/CLAUDE.md](microprofile/telemetry/CLAUDE.md) | MicroProfile Telemetry - OpenTelemetry链路追踪 |
| **microprofile/opentracing** | [microprofile/opentracing/CLAUDE.md](microprofile/opentracing/CLAUDE.md) | MicroProfile OpenTracing - OpenTracing链路追踪(已弃用) |
| **microprofile/config-extensions** | [microprofile/config-extensions/CLAUDE.md](microprofile/config-extensions/CLAUDE.md) | MicroProfile Config扩展 - 云密钥、TOML、LDAP等 |
| **microprofile/opentracing-jaxws** | [microprofile/opentracing-jaxws/CLAUDE.md](microprofile/opentracing-jaxws/CLAUDE.md) | MicroProfile OpenTracing JAX-WS集成 |
| **microprofile/microprofile-connector** | [microprofile/microprofile-connector/CLAUDE.md](microprofile/microprofile-connector/CLAUDE.md) | MicroProfile应用部署基础设施 |
| **microprofile/rest-client** | [microprofile/rest-client/CLAUDE.md](microprofile/rest-client/CLAUDE.md) | MicroProfile REST Client集成 |
| **microprofile/rest-client-ssl** | [microprofile/rest-client-ssl/CLAUDE.md](microprofile/rest-client-ssl/CLAUDE.md) | REST Client SSL/TLS证书配置 |

### 其他模块

| 子目录 | 文档位置 | 说明 |
|--------|----------|------|
| **hazelcast-ejb-timer** | [hazelcast-ejb-timer/CLAUDE.md](hazelcast-ejb-timer/CLAUDE.md) | Hazelcast分布式EJB定时器 |
| **jmx-monitoring-service** | [jmx-monitoring-service/CLAUDE.md](jmx-monitoring-service/CLAUDE.md) | JMX属性监控服务 |
| **hazelcast-eclipselink-coordination** | [hazelcast-eclipselink-coordination/CLAUDE.md](hazelcast-eclipselink-coordination/CLAUDE.md) | JPA二级缓存协调 |
| **payara-jsr107** | [payara-jsr107/CLAUDE.md](payara-jsr107/CLAUDE.md) | JCache (JSR-107) CDI集成 |
| **healthcheck-checker** | [healthcheck-checker/CLAUDE.md](healthcheck-checker/CLAUDE.md) | MicroProfile健康检查服务 |
| **healthcheck-metrics** | [healthcheck-metrics/CLAUDE.md](healthcheck-metrics/CLAUDE.md) | MicroProfile指标健康检查 |
| **cdi-auth-roles** | [cdi-auth-roles/CLAUDE.md](cdi-auth-roles/CLAUDE.md) | CDI角色授权拦截器 |

## Build Commands

```bash
# Build entire payara-appserver-modules
mvn -DskipTests clean package -f appserver/payara-appserver-modules/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/payara-appserver-modules/payara-jsr107/pom.xml
mvn -DskipTests clean package -f appserver/payara-appserver-modules/hazelcast-ejb-timer/pom.xml
mvn -DskipTests clean package -f appserver/payara-appserver-modules/jmx-monitoring-service/pom.xml
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/pom.xml

# Build MicroProfile submodules
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/config/pom.xml
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/healthcheck/pom.xml
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/metrics/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/hazelcast-ejb-timer/pom.xml
```

## Architecture

### Module Structure

```
payara-appserver-modules/
├── MicroProfile/                    # MicroProfile implementations
│   ├── config                       # MP Config API
│   ├── config-extensions           # Extended config sources (AWS, Azure, GCP, etc.)
│   ├── healthcheck                  # MP Health Check API
│   ├── metrics                      # MP Metrics API
│   ├── fault-tolerance             # MP Fault Tolerance API
│   ├── jwt-auth                     # MP JWT Authentication
│   ├── openapi                      # MP OpenAPI (Swagger)
│   ├── rest-client                  # MP REST Client
│   ├── rest-client-ssl              # SSL configuration for REST Client
│   ├── telemetry                    # MP Telemetry (OpenTelemetry)
│   ├── opentracing                  # MP OpenTracing
│   ├── opentracing-jaxws            # JAX-WS tracing
│   ├── microprofile-common          # Shared utilities
│   └── microprofile-connector       # Connector integration
│
├── Clustering Features/
│   ├── hazelcast-ejb-timer          # Hazelcast-backed EJB timers
│   ├── hazelcast-eclipselink-coordination  # JPA L2 cache coordination
│   └── payara-jsr107                # JCache (JSR-107) CDI integration
│
├── Monitoring/
│   ├── jmx-monitoring-service       # JMX attribute monitoring
│   ├── healthcheck-checker          # Health check service integration
│   └── healthcheck-metrics          # Health check metrics bridge
│
├── Security/
│   ├── cdi-auth-roles               # CDI @RolesAllowed interception
│   ├── jaspic-servlet-utils         # JASPIC authentication helpers
│   └── yubikey-authentication       # YubiKey two-factor auth
│
├── Payara Micro/
│   ├── payara-micro-service         # Micro-specific services
│   ├── payara-micro-cdi             # Micro CDI extensions
│   └── environment-warning          # Environment validation
│
├── Request Tracing/
│   ├── jaxrs-client-tracing         # JAX-RS client tracing
│   └── notification-jms-core        # JMS notification backend
│
└── REST API/
    └── payara-rest-endpoints        # Payara REST endpoints
```

## MicroProfile Config

### CDI Integration

```java
// ConfigCdiExtension - CDI extension for @ConfigProperty
@ConfigProperty(name = "my.property")
private String myValue;

// @ConfigProperties for group injection
@ConfigProperties(prefix = "database")
public class DatabaseConfig {
    String url;
    String username;
    String password;
}
```

### Config Sources

Payara extends MP Config with additional sources:

| Source | Configuration | Purpose |
|--------|---------------|---------|
| **System Properties** | Default | Highest precedence |
| **Environment Variables** | Default | Environment-based config |
| **META-INF/microprofile-config.properties** | Default | Application defaults |
| **TOML Config Source** | `set-toml-configuration` | TOML file format |
| **LDAP** | `set-ldap-config-source-configuration` | LDAP directory config |
| **AWS Secrets Manager** | `set-aws-secrets-config-source-configuration` | AWS secrets |
| **Azure Key Vault** | `set-azure-secrets-config-source-configuration` | Azure secrets |
| **GCP Secret Manager** | `set-gcp-secrets-config-source-configuration` | GCP secrets |
| **HashiCorp Vault** | `set-hashicorp-secrets-config-source-configuration` | Vault secrets |
| **DynamoDB** | `set-dynamodb-config-source-configuration` | DynamoDB config |

### Config Administration

```bash
# Configure TOML config source
asadmin set-toml-configuration --enabled=true
    --dynamic=true --directory=/path/to/config

# Configure LDAP config source
asadmin set-ldap-config-source-configuration
    --url=ldap://localhost:389
    --base-dn=dc=example,dc=com

# Enable AWS Secrets Manager
asadmin set-aws-secrets-config-source-configuration
    --enabled=true
    --region=us-east-1
    --access-key=xxx
    --secret-key=xxx
```

## MicroProfile Health Check

### Health Check Integration

```java
// Health check procedure
@Health
public class MyHealthCheck implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("database")
            .up()
            .withData("host", "localhost")
            .build();
    }
}
```

### Health Check Service

```java
// MicroProfileHealthChecker - Service-based health checking
@Service
public class MicroProfileHealthChecker {
    // Checks registered health check services
    // Can be enabled/disabled via asadmin
}
```

### Configuration

```bash
# Enable health check service
asadmin set-microprofile-health-checker-configuration
    --enabled=true

# Set execution options
asadmin set-microprofile-health-checker-configuration
    --enabled=true
    --timeunit=SECONDS
    --timeout=30
```

## MicroProfile Metrics

### Metrics Integration

```java
// Application metrics
@Inject
private MetricRegistry registry;

@Counted
@Timed
public void myMethod() {
    // Automatically counted and timed
}
```

### Metrics Bridge to Health Check

```java
// MicroProfileMetricsCheck - Bridges metrics to health checks
// Monitors specific metrics and creates health check responses
```

## Hazelcast EJB Timer

### Distributed Timer Store

```java
// HazelcastTimerStore - Cluster-aware EJB timer implementation
public class HazelcastTimerStore extends NonPersistentEJBTimerService
        implements ClusterListener, MessageReceiver<EjbTimerEvent> {

    // Uses Hazelcast IMap for distributed storage
    // Automatic timer migration on cluster changes
    // Supports persistent and non-persistent timers
}
```

### Timer Caches

| Cache | Purpose |
|-------|---------|
| `HZEjbTmerCache` | Primary timer storage by timer ID |
| `HZEjbTmerContainerCache` | Timer index by container ID |
| `HZEjbTmerApplicationCache` | Timer index by application ID |

### Timer Migration

```java
// Automatic migration on cluster member removal
@Override
public void memberRemoved(MemberEvent event) {
    // Detect failed instance
    // Restore timers owned by that instance
    // Update ownership in distributed cache
}
```

## Hazelcast EclipseLink Coordination

### Distributed Cache Coordination

```java
// HazelcastPublishingTransportManager
public class HazelcastPublishingTransportManager {
    // Publishes cache change events to Hazelcast topic
    // Coordinates JPA 2nd-level cache across cluster
}
```

### Components

| Class | Purpose |
|-------|---------|
| `HazelcastTopic` | Distributed topic for cache events |
| `HazelcastPayload` | Cache event payload wrapper |
| `HazelcastTopicRemoteConnection` | Remote topic connection |
| `HazelcastTopicStorage` | Topic storage management |

## JCache (JSR-107) CDI Integration

### Cache Interceptor

```java
// @CacheResult annotation support
@CacheResult(cacheName = "userCache")
public User getUser(String id) {
    return userRepository.findById(id);
}

// Automatic cache key generation and lookup
```

### CDI Injection

```java
@Inject
private Cache<String, User> userCache;

// Standard JCache API usage
User user = userCache.get(userId);
if (user == null) {
    user = loadUser(userId);
    userCache.put(userId, user);
}
```

## JMX Monitoring Service

### Monitoring Architecture

```java
// JMXMonitoringService - Monitors MBean attributes
@Service
@RunLevel(StartupRunLevel.VAL)
public class JMXMonitoringService {
    // Monitors configured MBeans
    // Logs attribute values at fixed intervals
    // Sends notifications via event bus
}
```

### Configuration

```java
// MonitoringServiceConfiguration
public interface MonitoringServiceConfiguration {
    String getEnabled();              // Enable/disable monitoring
    String getLogFrequency();          // Logging frequency
    String getLogFrequencyUnit();     // SECONDS, MINUTES, HOURS
    String getAmx();                  // Enable AMX monitoring
    List<MonitoredAttribute> getMonitoredAttributes();
    List<String> getNotifierList();   // Notification services
}
```

### Monitored Attributes

```java
// MonitoredAttribute
public interface MonitoredAttribute {
    String getObjectName();           // MBean object name
    String getAttributeName();        // Attribute name
    String getEnabled();              // Enable/disable monitoring
}
```

### Administration

```bash
# Enable JMX monitoring
asadmin set-jmx-monitoring-configuration --enabled=true

# Add monitored attribute
asadmin set-jmx-monitoring-configuration
    --monitoredattribute=amx:JvmRuntime/type=heap-name=used
    --enabled=true

# Configure notification
asadmin set-jmx-monitoring-configuration
    --notifierlist=LOG,NOTIFIER_SERVICE
```

## CDI Authorization

### @RolesAllowed Interceptor

```java
// RolesPermittedInterceptor
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
@RolesAllowed
public class RolesPermittedInterceptor {
    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        // Enforces @RolesAllowed annotation
        // Supports Payara's extended role mapping
    }
}
```

### Features

- **Call stack introspection** - Determines EJB/JAX-RS context
- **Dynamic role checking** - Supports role mapping
- **Exception mapping** - CallerAccessExceptionMapper

## Request Tracing

### JAX-RS Client Tracing

```java
// PayaraTracingServices - Tracing propagation
// Automatically adds tracing headers to JAX-RS client calls
// Integrates with Payara request tracing service
```

### Tracing Flow

```
Incoming Request
       │
       ▼
[Request Tracing Service] → Generates trace ID
       │
       ├─→ JAX-RS Client Call
       │   └─→ [PayaraTracingServices] → Adds trace headers
       │
       └─→ Response with trace information
```

## Payara Micro Services

### PayaraInstance Service

```java
// PayaraInstanceImpl - Instance descriptor
@Service
public class PayaraInstanceImpl {
    // Provides instance metadata
    // Cluster membership information
    // Hazelcast integration
}
```

### Micro-Specific Features

| Feature | Description |
|---------|-------------|
| **Auto-binding** | Automatic HTTP port binding |
| **Uber-jar** | Single JAR deployment |
| **Embedded** | Embedded API support |
| **Deployment** | Simplified deployment without domain.xml |

## Package Structure

```
appserver/payara-appserver-modules/
├── microprofile/                        # MicroProfile implementations
│   ├── config/
│   │   ├── src/main/java/fish/payara/microprofile/config/
│   │   │   ├── activation/           # Deployment integration
│   │   │   ├── cdi/                  # CDI extension
│   │   │   └── spi/                  # Config SPI implementation
│   ├── config-extensions/
│   │   ├── aws/                       # AWS Secrets Manager
│   │   ├── azure/                     # Azure Key Vault
│   │   ├── gcp/                       # GCP Secret Manager
│   │   ├── dynamodb/                  # DynamoDB config
│   │   ├── hashicorp/                 # HashiCorp Vault
│   │   ├── ldap/                      # LDAP directory
│   │   └── toml/                      # TOML files
│   ├── healthcheck/
│   │   └── src/main/java/fish/payara/microprofile/
│   │       └── healthcheck/
│   ├── metrics/
│   ├── fault-tolerance/
│   ├── jwt-auth/
│   ├── openapi/
│   ├── rest-client/
│   ├── telemetry/
│   ├── opentracing/
│   └── opentracing-jaxws/
│
├── hazelcast-ejb-timer/
│   └── src/main/java/fish/payara/ejb/timer/hazelcast/
│       ├── HazelcastTimerStore.java
│       ├── HZTimer.java
│       ├── EjbTimerEvent.java
│       └── DataGridEJBTimerService.java
│
├── hazelcast-eclipselink-coordination/
│   └── src/main/java/fish/payara/persistence/eclipselink/cache/coordination/
│       ├── HazelcastPublishingTransportManager.java
│       ├── HazelcastTopic.java
│       └── HazelcastPayload.java
│
├── payara-jsr107/
│   └── src/main/java/fish/payara/cdi/jsr107/
│       ├── CacheLookupInterceptor.java
│       ├── CacheResultInterceptor.java
│       └── ...
│
├── jmx-monitoring-service/
│   └── src/main/java/fish/payara/jmx/monitoring/
│       ├── JMXMonitoringService.java
│       ├── JMXMonitoringFormatter.java
│       ├── JMXMonitoringJob.java
│       ├── admin/
│       └── configuration/
│
├── healthcheck-checker/
│   └── src/main/java/fish/payara/healthcheck/mphealth/
│       ├── MicroProfileHealthChecker.java
│       └── ...
│
├── healthcheck-metrics/
│   └── src/main/java/fish/payara/healthcheck/microprofile/metrics/
│       ├── MicroProfileMetricsCheck.java
│       └── ...
│
├── cdi-auth-roles/
│   └── src/main/java/fish/payara/appserver/cdi/auth/roles/
│       ├── RolesPermittedInterceptor.java
│       ├── RolesCDIExtension.java
│       └── ...
│
├── jaxrs-client-tracing/
│   └── src/main/java/fish/payara/requesttracing/jaxrs/client/
│       └── PayaraTracingServices.java
│
├── payara-micro-service/
│   └── src/main/java/fish/payara/appserver/micro/
│       └── ...
│
└── payara-rest-endpoints/
    └── src/main/java/fish/payara/rest/
        └── ...
```

## Module Dependencies

### Common Dependencies

| Dependency | Purpose |
|------------|---------|
| `hazelcast-bootstrap` | Hazelcast integration |
| `healthcheck-core` | Health check framework |
| `requesttracing-core` | Request tracing service |
| `notification-core` | Notification service |
| `glassfish-api` | GlassFish public APIs |
| `container-common` | Container utilities |

### MicroProfile-Specific

| Module | Key Dependencies |
|--------|----------------|
| `config` | `microprofile-config-api`, Payara Config SPI |
| `healthcheck` | `microprofile-health-api`, `healthcheck-core` |
| `metrics` | `microprofile-metrics-api`, Payara Metrics |
| `fault-tolerance` | `microprofile-fault-tolerance-api`, Hystrix |
| `jwt-auth` | `microprofile-jwt-auth-api`, `mp-jwt` |
| `openapi` | `microprofile-openapi-api`, SmallRye OpenAPI |
| `rest-client` | `microprofile-rest-client-api`, RESTEasy |
| `telemetry` | `micrometer-core`, OpenTelemetry |

## Administration Commands

### MicroProfile Config

```bash
# List config sources
asadmin list-config-sources

# Get TOML configuration
asadmin get-toml-configuration

# Set TOML configuration
asadmin set-toml-configuration --enabled=true
    --dynamic=true --directory=/path/to/config

# Get AWS Secrets configuration
asadmin get-aws-secrets-config-source-configuration

# Set AWS Secrets configuration
asadmin set-aws-secrets-config-source-configuration
    --enabled=true --region=us-east-1
```

### JMX Monitoring

```bash
# Enable on DAS
asadmin enable-jmx-monitoring-service-on-das

# Enable on instance
asadmin enable-jmx-monitoring-service-on-instance instance-name

# Get configuration
asadmin get-jmx-monitoring-configuration

# Set configuration
asadmin set-jmx-monitoring-configuration --enabled=true
    --logfrequency=30 --logfrequencyunit=SECONDS
```

### Health Check

```bash
# Enable MP Health Check checker
asadmin set-microprofile-health-checker-configuration
    --enabled=true --timeout=30

# List health checks
asadmin list-health-checks
```

## Related Modules

- `nucleus/payara-modules/hazelcast-bootstrap` - Hazelcast initialization
- `nucleus/payara-modules/healthcheck-core` - Health check framework
- `nucleus/payara-modules/requesttracing-core` - Request tracing service
- `nucleus/payara-modules/notification-core` - Notification service
- `appserver/persistence/eclipselink` - EclipseLink JPA provider
- `appserver/ejb/ejb-container` - EJB timer service integration
- `appserver/web/weld-integration` - CDI/Weld integration

## Notes

- **Hazelcast EJB Timer** requires Hazelcast to be enabled in the cluster
- **Config sources** are consulted in order of precedence (system props > env vars > files > extended sources)
- **JMX Monitoring** uses PayaraExecutorService for scheduled execution
- **Health Check Checker** integrates with the healthcheck-core service
- **Metrics Bridge** allows health checks to be exposed as metrics
- **CDI Auth Roles** uses interceptor chain for authorization
- **Request Tracing** integrates with Payara's distributed tracing service
- **Payara Micro** services provide embedded/micro-specific functionality
- **Clustering features** require Payara Cluster (Hazelcast) to be enabled
