# JMX Monitoring Service - JMX 监控服务架构

## 概述

`jmx-monitoring-service` 模块提供 JMX MBean 属性监控功能，定期轮询配置的 MBean 属性并记录其值或发送通知。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    JMXMonitoringService                         │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  监控服务管理                                             │ │
│  │  ├─→ bootstrapMonitoringService()                       │ │
│  │  ├─→ buildJobs()                                        │ │
│  │  ├─→ shutdownMonitoringService()                        │ │
│  │  └─→ setEnabled(Boolean)                                │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    JMXMonitoringFormatter                      │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Runnable 监控任务                                        │ │
│  │  ├─→ run() - 定期执行                                    │ │
│  │  ├─→ collectMonitoringData()                            │ │
│  │  └─→ publishNotifications()                             │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    JMXMonitoringJob                            │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  监控作业                                                 │ │
│  │  ├─→ ObjectName - MBean 对象名称                         │ │
│  │  └─→ attributes[] - 监控的属性列表                        │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    MonitoringServiceConfiguration               │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  配置属性                                                 │ │
│  │  ├─→ enabled - 启用/禁用监控                             │ │
│  │  ├─→ logFrequency - 日志频率                             │ │
│  │  ├─→ logFrequencyUnit - 时间单位                         │ │
│  │  ├─→ amx - 启用 AMX                                      │ │
│  │  ├─→ monitoredAttributes - 监控属性列表                  │ │
│  │  └─→ notifierList - 通知服务列表                         │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## JMXMonitoringService

### 核心职责

```java
@Service(name = "payara-monitoring")
@RunLevel(StartupRunLevel.VAL)
public class JMXMonitoringService implements EventListener {

    @Inject
    @Named(ServerEnvironment.DEFAULT_INSTANCE_NAME)
    @Optional
    MonitoringServiceConfiguration configuration;

    @Inject
    private PayaraExecutorService executor;

    @Inject
    private Topic<PayaraNotification> notificationEventBus;

    @Inject
    private Events events;

    private JMXMonitoringFormatter formatter;
    private boolean enabled;
    private ScheduledFuture<?> monitoringFuture;
    private final Set<String> enabledNotifiers = new LinkedHashSet<>();

    /**
     * 启动监控服务
     * 在 SERVER_READY 事件时触发
     */
    @Override
    public void event(Event event) {
        if (event.is(EventTypes.SERVER_READY)) {
            if (configuration != null &&
                Boolean.valueOf(configuration.getEnabled())) {
                enabled = Boolean.valueOf(configuration.getEnabled());
                bootstrapMonitoringService();
            }
        } else if (event.is(EventTypes.SERVER_SHUTDOWN)) {
            shutdownMonitoringService();
        }
    }
}
```

### 启动流程

```java
/**
 * 启动监控服务
 */
public void bootstrapMonitoringService() {
    if (configuration != null &&
        configuration.getEnabled().equalsIgnoreCase("true")) {

        // 1. 确保没有多个监控服务运行
        shutdownMonitoringService();

        final MBeanServer server = getPlatformMBeanServer();

        // 2. 创建监控作业列表
        List<JMXMonitoringJob> jobs = buildJobs();

        // 3. 创建格式化器
        formatter = new JMXMonitoringFormatter(
            server, jobs, this, notificationEventBus,
            notificationFactory, enabledNotifiers
        );

        Logger.getLogger(JMXMonitoringService.class.getName())
            .log(Level.INFO, "JMX Monitoring Service will startup");

        // 4. 启用 AMX (如果配置)
        if (Boolean.valueOf(configuration.getAmx())) {
            AMXConfiguration amxConfig = habitat.getService(AMXConfiguration.class);
            try {
                amxConfig.setEnabled(String.valueOf(configuration.getAmx()));
                configuration.setAmx(null);
            } catch (PropertyVetoException ex) {
                Logger.getLogger(JMXMonitoringService.class.getName())
                    .log(Level.SEVERE, null, ex);
            }
        }

        // 5. 调度定期执行
        long initialDelay = monitoringDelay * 1000;
        long period = TimeUnit.MILLISECONDS.convert(
            Long.valueOf(configuration.getLogFrequency()),
            TimeUnit.valueOf(configuration.getLogFrequencyUnit())
        );

        monitoringFuture = executor.scheduleAtFixedRate(
            formatter, initialDelay, period, TimeUnit.MILLISECONDS
        );

        // 6. 启动通知器列表
        bootstrapNotifierList();
    }
}

/**
 * 构建监控作业
 * 从配置的 MonitoredAttribute 列表构建 JMXMonitoringJob 列表
 */
private List<JMXMonitoringJob> buildJobs() {
    List<JMXMonitoringJob> jobs = new LinkedList<>();

    for (MonitoredAttribute mbean : configuration.getMonitoredAttributes()) {
        boolean exists = false;

        // 检查是否已有相同 MBean 的作业
        for (JMXMonitoringJob job : jobs) {
            if (job.getMBean().getCanonicalKeyPropertyListString()
                    .equals(mbean.getObjectName())) {
                job.addAttribute(mbean.getAttributeName());
                exists = true;
                break;
            }
        }

        // 创建新作业
        if (!exists) {
            ObjectName name;
            List<String> list;
            try {
                name = new ObjectName(mbean.getObjectName());
                list = new LinkedList<>();
                list.add(mbean.getAttributeName());
                jobs.add(new JMXMonitoringJob(name, list));
            } catch (MalformedObjectNameException ex) {
                Logger.getLogger(JMXMonitoringService.class.getName())
                    .log(Level.SEVERE, null, ex);
            }
        }
    }
    return jobs;
}
```

### 启用/禁用服务

```java
/**
 * 设置服务启用状态
 */
public void setEnabled(Boolean enabled) {
    // 重置延迟以加快启动
    amxBootDelay = 0;
    monitoringDelay = amxBootDelay + 5;

    if (enabled) {
        this.enabled = enabled;
        bootstrapMonitoringService();
    } else if (this.enabled && !enabled) {
        this.enabled = enabled;
        shutdownMonitoringService();
    }
}

/**
 * 关闭监控服务
 */
private void shutdownMonitoringService() {
    if (monitoringFuture != null) {
        Logger.getLogger(JMXMonitoringService.class.getName())
            .log(Level.INFO, "JMX Monitoring Service will shutdown");
        monitoringFuture.cancel(false);
        monitoringFuture = null;
    }
}
```

## JMXMonitoringFormatter

### 核心职责

```java
/**
 * JMX 监控格式化器
 * 定期执行，收集监控数据并格式化输出
 */
public class JMXMonitoringFormatter implements Runnable {

    private final MBeanServer server;
    private final List<JMXMonitoringJob> jobs;
    private final Topic<PayaraNotification> notificationEventBus;
    private final PayaraNotificationFactory notificationFactory;
    private final Set<String> enabledNotifiers;

    /**
     * 执行监控
     */
    @Override
    public void run() {
        try {
            collectMonitoringData();
        } catch (Exception ex) {
            Logger.getLogger(JMXMonitoringFormatter.class.getName())
                .log(Level.SEVERE, "Error collecting monitoring data", ex);
        }
    }

    /**
     * 收集监控数据
     */
    private void collectMonitoringData() {
        for (JMXMonitoringJob job : jobs) {
            try {
                // 1. 获取 MBean 对象
                ObjectName mbean = job.getMBean();

                // 2. 获取属性值
                for (String attribute : job.getAttributes()) {
                    Object value = server.getAttribute(mbean, attribute);

                    // 3. 格式化输出
                    String formattedValue = formatValue(value);
                    logMonitoringData(mbean, attribute, formattedValue);

                    // 4. 发布通知
                    if (!enabledNotifiers.isEmpty()) {
                        publishNotification(mbean, attribute, formattedValue);
                    }
                }
            } catch (Exception ex) {
                Logger.getLogger(JMXMonitoringFormatter.class.getName())
                    .log(Level.WARNING, "Error monitoring MBean: " + job.getMBean(), ex);
            }
        }
    }

    /**
     * 发布通知
     */
    private void publishNotification(ObjectName mbean, String attribute, String value) {
        PayaraNotification notification = notificationFactory
            .createNotification()
            .setType("JMX_MONITORING")
            .setSource("JMXMonitoringService")
            .setData(Map.of(
                "mbean", mbean.toString(),
                "attribute", attribute,
                "value", value,
                "timestamp", Instant.now().toString()
            ));

        notificationEventBus.publish(notification);
    }

    /**
     * 格式化值
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }

        // 处理数组
        if (value.getClass().isArray()) {
            if (value instanceof Object[]) {
                return Arrays.toString((Object[]) value);
            } else if (value instanceof long[]) {
                return Arrays.toString((long[]) value);
            } else if (value instanceof int[]) {
                return Arrays.toString((int[]) value);
            }
            // ... 其他数组类型
        }

        // 处理 CompositeData
        if (value instanceof CompositeData) {
            return formatCompositeData((CompositeData) value);
        }

        return value.toString();
    }

    /**
     * 格式化 CompositeData (TabularData)
     */
    private String formatCompositeData(CompositeData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Object key : data.getCompositeType().keySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(key).append("=").append(data.get(key));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 记录监控数据
     */
    private void logMonitoringData(ObjectName mbean, String attribute, String value) {
        Logger.getLogger("payara-monitoring-service").log(Level.INFO,
            "{0}#{1}={2}",
            new Object[]{mbean.toString(), attribute, value}
        );
    }
}
```

## JMXMonitoringJob

### 数据结构

```java
/**
 * JMX 监控作业
 * 表示一个 MBean 及其要监控的属性
 */
public class JMXMonitoringJob {

    private final ObjectName mbean;
    private final List<String> attributes;

    /**
     * 创建监控作业
     */
    public JMXMonitoringJob(ObjectName mbean, List<String> attributes) {
        this.mbean = mbean;
        this.attributes = new LinkedList<>(attributes);
    }

    /**
     * 获取 MBean
     */
    public ObjectName getMBean() {
        return mbean;
    }

    /**
     * 添加属性
     */
    public void addAttribute(String attribute) {
        attributes.add(attribute);
    }

    /**
     * 获取属性列表
     */
    public List<String> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }
}
```

## 配置接口

### MonitoringServiceConfiguration

```java
@Configured
public interface MonitoringServiceConfiguration {

    /**
     * 启用监控服务
     * 默认: false
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getEnabled();

    /**
     * 日志频率
     * 监控数据记录频率
     * 默认: 30
     */
    @Attribute(defaultValue = "30")
    String getLogFrequency();

    /**
     * 日志频率单位
     * SECONDS, MINUTES, HOURS
     * 默认: SECONDS
     */
    @Attribute(defaultValue = "SECONDS")
    String getLogFrequencyUnit();

    /**
     * 启用 AMX
     * 默认: false
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getAmx();

    /**
     * 监控属性列表
     */
    @Element
    List<MonitoredAttribute> getMonitoredAttributes();

    /**
     * 通知服务列表
     * LOG, NOTIFIER_SERVICE 等
     */
    @Element
    List<String> getNotifierList();
}
```

### MonitoredAttribute

```java
@Configured
public interface MonitoredAttribute {

    /**
     * MBean 对象名称
     * 例如: amx:JvmRuntime/type=Heap/name=used
     */
    @Attribute(key = true)
    String getObjectName();

    /**
     * 属性名称
     * 例如: used, max, min
     */
    @Attribute(key = true)
    String getAttributeName();

    /**
     * 启用监控
     * 默认: true
     */
    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getEnabled();
}
```

## CLI 命令

### EnableJMXMonitoringServiceOnDas

```java
@Service(name = "enable-jmx-monitoring-service-on-das")
@PerLookup
@I18n("enable.jmx.monitoring.service.on.das")
@ExecuteOn({RuntimeType.DAS})
public class EnableJMXMonitoringServiceOnDas implements AdminCommand {

    @Override
    public void execute(AdminCommandContext context) {
        // 1. 获取 DAS 配置
        Config config = getDasConfig();
        MonitoringServiceConfiguration monitoringConfig =
            config.getExtensionByType(MonitoringServiceConfiguration.class);

        // 2. 启用监控
        if (monitoringConfig == null) {
            monitoringConfig = config.createChild(MonitoringServiceConfiguration.class);
        }

        monitoringConfig.setEnabled("true");

        // 3. 启动服务
        JMXMonitoringService service = habitat.getService(JMXMonitoringService.class);
        service.setEnabled(true);

        ActionReport report = context.getActionReport();
        report.setMessage("JMX Monitoring Service enabled on DAS");
        report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
    }
}
```

### EnableJMXMonitoringServiceOnInstance

```java
@Service(name = "enable-jmx-monitoring-service-on-instance")
@PerLookup
@I18n("enable.jmx.monitoring.service.on.instance")
@ExecuteOn({RuntimeType.DAS})
public class EnableJMXMonitoringServiceOnInstance implements AdminCommand {

    @Param(name = "target", optional = false)
    String target;

    @Override
    public void execute(AdminCommandContext context) {
        // 1. 获取实例配置
        Config config = getConfig(target);
        MonitoringServiceConfiguration monitoringConfig =
            config.getExtensionByType(MonitoringServiceConfiguration.class);

        // 2. 启用监控
        if (monitoringConfig == null) {
            monitoringConfig = config.createChild(MonitoringServiceConfiguration.class);
        }

        monitoringConfig.setEnabled("true");

        // 3. 通过运行时启用
        // (需要实例运行或重启后生效)
    }
}
```

### GetJMXMonitoringConfiguration

```java
@Service(name = "get-jmx-monitoring-configuration")
@PerLookup
@I18n("get.jmx.monitoring.configuration")
@ExecuteOn({RuntimeType.DAS})
public class GetJMXMonitoringConfiguration implements AdminCommand {

    @Param(name = "target", optional = true, defaultValue = "server-config")
    String target;

    @Override
    public void execute(AdminCommandContext context) {
        ActionReport report = context.getActionReport();
        Properties props = new Properties();

        // 1. 获取配置
        MonitoringServiceConfiguration config = getMonitoringConfiguration(target);

        // 2. 提取属性
        props.setProperty("enabled", config.getEnabled());
        props.setProperty("logFrequency", config.getLogFrequency());
        props.setProperty("logFrequencyUnit", config.getLogFrequencyUnit());
        props.setProperty("amx", config.getAmx());

        // 3. 提取监控属性
        List<MonitoredAttribute> attributes = config.getMonitoredAttributes();
        StringBuilder sb = new StringBuilder();
        for (MonitoredAttribute attr : attributes) {
            sb.append(attr.getObjectName()).append("#")
              .append(attr.getAttributeName()).append("\n");
        }
        props.setProperty("monitoredAttributes", sb.toString());

        // 4. 提取通知器列表
        if (config.getNotifierList() != null) {
            props.setProperty("notifierList",
                String.join(",", config.getNotifierList()));
        }

        report.setExtraProperties(props);
        report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
    }
}
```

### SetJMXMonitoringConfiguration

```java
@Service(name = "set-jmx-monitoring-configuration")
@PerLookup
@I18n("set.jmx.monitoring.configuration")
@ExecuteOn({RuntimeType.DAS})
public class SetJMXMonitoringConfiguration implements AdminCommand {

    @Param(name = "enabled", optional = true)
    String enabled;

    @Param(name = "logfrequency", optional = true)
    String logFrequency;

    @Param(name = "logfrequencyunit", optional = true,
            acceptableValues = "SECONDS,MINUTES,HOURS")
    String logFrequencyUnit;

    @Param(name = "amx", optional = true)
    String amx;

    @Param(name = "monitoredattribute", optional = true, multiple = true)
    List<String> monitoredAttributes;

    @Param(name = "notifierlist", optional = true)
    String notifierList;

    @Param(name = "target", optional = true, defaultValue = "server-config")
    String target;

    @Override
    public void execute(AdminCommandContext context) {
        // 1. 获取配置
        Config config = getConfig(target);
        MonitoringServiceConfiguration monitoringConfig =
            config.getExtensionByType(MonitoringServiceConfiguration.class);

        // 2. 更新属性
        if (enabled != null) {
            monitoringConfig.setEnabled(enabled);
        }

        if (logFrequency != null) {
            monitoringConfig.setLogFrequency(logFrequency);
        }

        if (logFrequencyUnit != null) {
            monitoringConfig.setLogFrequencyUnit(logFrequencyUnit);
        }

        if (amx != null) {
            monitoringConfig.setAmx(amx);
        }

        // 3. 添加监控属性
        if (monitoredAttributes != null && !monitoredAttributes.isEmpty()) {
            for (String attrSpec : monitoredAttributes) {
                // 格式: objectName#attributeName
                String[] parts = attrSpec.split("#");
                if (parts.length == 2) {
                    MonitoredAttribute attr = monitoringConfig
                        .createChild(MonitoredAttribute.class);
                    attr.setObjectName(parts[0]);
                    attr.setAttributeName(parts[1]);
                    monitoringConfig.getMonitoredAttributes().add(attr);
                }
            }
        }

        // 4. 设置通知器列表
        if (notifierList != null) {
            List<String> notifiers = Arrays.asList(notifierList.split(","));
            monitoringConfig.getNotifierList().clear();
            monitoringConfig.getNotifierList().addAll(notifiers);
        }

        ActionReport report = context.getActionReport();
        report.setMessage("JMX Monitoring Service configuration updated");
        report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
    }
}
```

## 配置示例

### 基本 JMX 监控配置

```xml
<monitoring-service-configuration enabled="true"
    logfrequency="30"
    logfrequencyunit="SECONDS"
    amx="false">

    <!-- 监控堆内存使用 -->
    <monitored-attribute
        object-name="amx:JvmRuntime/type=Heap,name=used"
        attribute-name="used"
        enabled="true" />

    <!-- 监控线程数 -->
    <monitored-attribute
        object-name="amx:JvmRuntime/type=Threading,name=threadcount"
        attribute-name="threadcount"
        enabled="true" />

    <!-- 监控 CPU 使用率 -->
    <monitored-attribute
        object-name="java.lang:type=OperatingSystem"
        attribute-name="ProcessCpuLoad"
        enabled="true" />
</monitoring-service-configuration>
```

### 启用通知

```xml
<monitoring-service-configuration enabled="true"
    notifier-list="LOG,NOTIFIER_SERVICE">
    <!-- ... 监控属性配置 ... -->
</monitoring-service-configuration>
```

## 常见 MBean

### JVM 内存监控

```
# 堆内存使用
amx:JvmRuntime/type=Heap,name=used
amx:JvmRuntime/type=Heap,name=max
amx:JvmRuntime/type=Heap,name=committed

# 非堆内存
amx:JvmMemory/type=NonHeap,name=used
amx:JvmMemory/type=NonHeap,name=max

# GC 信息
amx:GarbageCollector/name=PS MarkSweep
amx:GarbageCollector,name=PS Scavenge
```

### 线程监控

```
# 线程数
amx:JvmRuntime/type=Threading,name=threadcount

# 峰值线程数
amx:JvmRuntime/type=Threading,name=peakthreadcount

# 守护线程
amx:JvmRuntime/type=Threading,name=daemonthreadcount
```

### 运行时监控

```
# CPU 使用率
java.lang:type=OperatingSystem
- ProcessCpuLoad
- SystemCpuLoad

# 打开文件描述符
java.lang:type=OperatingSystem
- OpenFileDescriptorCount
- MaxFileDescriptorCount

# 类加载
java.lang:type=ClassLoading
- LoadedClassCount
- TotalLoadedClassCount
- UnloadedClassCount
```

### Tomcat/JMX 监控

```
# 请求处理时间
Catalina:type=GlobalRequestProcessor,name=http-nio-8080
- processingTime
- requestCount
- errorCount

# 线程池
Catalina:type=ThreadPool,name=http-nio-8080
- currentThreadCount
- currentThreadsBusy
- maxThreads
```

## 相关模块

- `nucleus/payara-modules/notification-core` - 通知服务
- `nucleus/admin/config-api` - 配置 API
- `nucleus/executorservice` - 执行器服务
- `appserver/admin/amx` - AMX (Application Management Extensions)
