# Hazelcast EclipseLink Coordination - JPA 二级缓存协调架构

## 概述

`hazelcast-eclipselink-coordination` 模块提供基于 Hazelcast 的 EclipseLink JPA 二级缓存分布式协调功能，实现集群环境下的缓存更新通知。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│          HazelcastPublishingTransportManager                   │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  EclipseLink BroadcastTransportManager                    │ │
│  │  ├─→ createLocalConnection()                             │ │
│  │  ├─→ removeLocalConnection()                            │ │
│  │  └─→ getConnectionsToExternalServicesForCommandPropagation()│ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│              HazelcastTopicRemoteConnection                    │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  BroadcastRemoteConnection + MessageReceiver              │ │
│  │  ├─→ executeCommandInternal() - 发布命令                │ │
│  │  └─→ receiveMessage() - 接收命令                         │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    HazelcastTopic                             │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Topic 抽象                                              │ │
│  │  ├─→ publish(HazelcastPayload)                          │ │
│  │  ├─→ hasPublished(HazelcastPayload) - 防止循环          │ │
│  │  └─→ destroy()                                          │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                 HazelcastTopicStorage                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  单例存储                                                │ │
│  │  ├─→ registerMessageReceiver()                          │ │
│  │  ├─→ removeMessageReceiver()                           │ │
│  │  ├─→ publish() - 通过 EventBus 发布                      │ │
│  │  └─→ process() - 异步处理接收的消息                      │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    HazelcastPayload                             │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  命令封装                                                │ │
│  │  ├─→ Command - 命令对象                                  │ │
│  │  └─→ Bytes - 序列化字节                                 │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## HazelcastPublishingTransportManager

### 核心职责

```java
/**
 * 基于 Hazelcast 的 BroadcastTransportManager
 * 用于 EclipseLink 缓存协调
 */
public class HazelcastPublishingTransportManager extends BroadcastTransportManager {

    /**
     * 与 Hazelcast topic 的连接
     */
    private HazelcastTopicRemoteConnection connection;

    /**
     * 初始化
     */
    public HazelcastPublishingTransportManager() {
        super();
        LOG.info("HazelcastPublishingTransportManager initialized.");
    }

    /**
     * 获取与外部服务的连接
     * 返回 Hazelcast topic 连接
     */
    @Override
    public Map<String, RemoteConnection> getConnectionsToExternalServicesForCommandPropagation() {
        if (this.connection != null) {
            return Collections.singletonMap(
                this.connection.getServiceId().getId(),
                this.connection
            );
        }
        return Collections.emptyMap();
    }

    /**
     * 创建本地连接
     */
    @Override
    public void createLocalConnection() {
        this.connection = new HazelcastTopicRemoteConnection(
            this.getRemoteCommandManager()
        );
    }

    /**
     * 移除本地连接
     */
    @Override
    public void removeLocalConnection() {
        if (this.connection != null) {
            this.connection.close();
            this.connection = null;
        }
    }
}
```

### 配置集成

```java
/**
 * EclipseLink 会话配置
 */
public class EclipseLinkConfigurator {

    /**
     * 配置缓存协调
     */
    public void configureCoordination(Session session) {
        // 1. 获取 RemoteCommandManager
        RemoteCommandManager rcm = session.getCommandManager();

        // 2. 设置传输管理器
        HazelcastPublishingTransportManager transportManager =
            new HazelcastPublishingTransportManager();

        rcm.setTransportManager(transportManager);

        // 3. 创建本地连接
        transportManager.createLocalConnection();

        LOG.info("EclipseLink cache coordination configured with Hazelcast");
    }

    /**
     * persistence.xml 配置示例
     */
    /*
    <persistence-unit name="myPU">
        <class>com.example.MyEntity</class>

        <properties>
            <!-- 启用二级缓存 -->
            <property name="eclipselink.cache.shared.default" value="true" />

            <!-- 启用缓存协调 -->
            <property name="eclipselink.cache.coordination" value="true" />

            <!-- 使用 Hazelcast 传输管理器 -->
            <property name="eclipselink.cache.coordination.transport"
                value="fish.payara.persistence.eclipselink.cache.coordination.HazelcastPublishingTransportManager" />

            <!-- 协调通道名称 -->
            <property name="eclipselink.cache.coordination.channel"
                value="eclipselink-cache-coordination" />
        </properties>
    </persistence-unit>
    */
}
```

## HazelcastTopicRemoteConnection

### 核心职责

```java
/**
 * Hazelcast Topic 远程连接
 * 实现 BroadcastRemoteConnection 和 MessageReceiver
 */
public class HazelcastTopicRemoteConnection extends BroadcastRemoteConnection
        implements MessageReceiver<HazelcastPayload> {

    /**
     * Topic 引用
     */
    private final HazelcastTopic topic;

    /**
     * 构造函数
     */
    HazelcastTopicRemoteConnection(RemoteCommandManager rcm) {
        super(rcm);
        // 创建 Hazelcast Topic
        this.topic = new HazelcastTopic(rcm.getChannel(), this);
    }

    /**
     * 执行命令内部实现
     * 发布命令到 Hazelcast topic
     */
    @Override
    protected Object executeCommandInternal(Object o) throws Exception {
        if (o != null) {
            Object[] debugInfo = null;

            // 调试日志
            if (this.rcm.shouldLogDebugMessage()) {
                debugInfo = this.logDebugBeforePublish(null);
            }

            // 根据类型发布
            if (Command.class.isAssignableFrom(o.getClass())) {
                // 发布命令对象
                this.topic.publish(new HazelcastPayload.Command((Command) o));
            } else if (o.getClass().isArray()) {
                // 发布字节序列化数据
                this.topic.publish(new HazelcastPayload.Bytes((byte[]) o));
            }

            if (debugInfo != null) {
                this.logDebugAfterPublish(debugInfo, "");
            }
        }
        return null;
    }

    /**
     * 关闭连接
     */
    @Override
    protected void closeInternal() throws Exception {
        this.topic.destroy();
    }

    /**
     * 接收消息
     * 处理来自 Hazelcast topic 的缓存更新通知
     */
    @Override
    public void receiveMessage(ClusterMessage<HazelcastPayload> message) {
        HazelcastPayload payload = message.getPayload();

        // 检查是否为循环消息
        if (!topic.hasPublished(payload)) {
            // 异步处理接收到的对象
            HazelcastTopicStorage.getInstance().process(
                () -> {
                    try {
                        this.processReceivedObject(
                            payload.getCommand(this.rcm),
                            payload.getId().toString()
                        );
                    } catch (Exception e) {
                        this.failDeserializeMessage(
                            payload.getId().toString(), e
                        );
                    }
                }
            );
        }
    }
}
```

### 缓存协调流程

```
实例 A 更新实体
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│  EclipseLink 会话                                              │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  实体更新检测                                           │ │
│  │  ├─→ flush() / commit()                                │ │
│  │  └─→ 检测二级缓存变化                                  │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────┬───────────────────────────────────────────────────┘
             │
             ▼
┌────────────────────────────────────────────────────────────────┐
│  RemoteCommandManager                                        │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  创建缓存更新命令                                       │ │
│  │  ├─→ CacheInvalidationCommand                          │ │
│  │  ├─→ CacheUpdateCommand                               │ │
│  │  └─→ RemoveCommand                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────┬───────────────────────────────────────────────────┘
             │
             ▼
┌────────────────────────────────────────────────────────────────┐
│  HazelcastPublishingTransportManager                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  publish()                                              │ │
│  │  └─→ 发送到 Hazelcast Topic                            │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────┬───────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│              Hazelcast EventBus                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  publish(topic, ClusterMessage)                           │ │
│  │  └─→ 分发到所有集群成员                                  │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────┬───────────────────────────────────────────────────┘
             │
             ├────────────────────────┬────────────────────────┐
             ▼                        ▼                        ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│     实例 A       │    │     实例 B       │    │     实例 C       │
│  (发布者)        │    │  (订阅者)        │    │  (订阅者)        │
└─────────────────┘    └─────────────────┘    └─────────────────┘
             │                        │                        │
             ▼                        ▼                        ▼
┌─────────────────────────────────────────────────────────────────┐
│  HazelcastTopicRemoteConnection.receiveMessage()               │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  检查循环 (hasPublished)                                 │ │
│  │  如果不是自身发布的消息:                               │ │
│  │  ├─→ processReceivedObject()                           │ │
│  │  └─→ executeCommand()                                  │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────┬───────────────────────────────────────────────────┘
             │
             ▼
┌────────────────────────────────────────────────────────────────┐
│  EclipseLink 缓存失效/更新                                   │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  移除/更新二级缓存中的实体                              │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## HazelcastTopic

### Topic 抽象

```java
/**
 * Hazelcast Topic 表示
 * 封装 Hazelcast topic 的使用
 */
final class HazelcastTopic {

    /**
     * Topic 名称
     */
    private final String name;

    /**
     * 消息监听器 ID (用于注销)
     */
    private final String messageListenerId;

    /**
     * 存储已发布消息的 ID
     * 用于防止循环处理
     */
    private final Set<UUID> publishedMessageIds = new ConcurrentSkipListSet<>();

    /**
     * 构造函数
     */
    HazelcastTopic(String name, MessageReceiver<HazelcastPayload> receiver) {
        this.name = name;
        // 注册消息接收器
        this.messageListenerId = getStorage().registerMessageReceiver(name, receiver);
    }

    /**
     * 发布命令
     */
    void publish(HazelcastPayload payload) {
        // 记录已发布消息
        publishedMessageIds.add(payload.getId());

        // 发布到 Hazelcast EventBus
        getStorage().publish(name, payload);
    }

    /**
     * 检查是否由本 topic 发布
     * 用于防止循环处理
     */
    boolean hasPublished(HazelcastPayload payload) {
        return publishedMessageIds.remove(payload.getId());
    }

    /**
     * 销毁 topic
     */
    void destroy() {
        publishedMessageIds.clear();
        getStorage().removeMessageReceiver(messageListenerId);
    }
}
```

### 防止循环处理

```
发布者检查:
┌─────────────────────────────────────────────────────────────────┐
│  发布消息前                                                  │
│  ├─→ publishedMessageIds.add(payload.getId())                │
│  └─→ 发布到 EventBus                                         │
└─────────────────────────────────────────────────────────────────┘

订阅者检查:
┌─────────────────────────────────────────────────────────────────┐
│  接收消息时                                                  │
│  ├─→ hasPublished(payload)                                  │
│  │   └─→ publishedMessageIds.remove(id)                      │
│  ├─→ 如果返回 true: 跳过处理 (循环消息)                      │
│  └─→ 如果返回 false: 处理消息                                 │
└─────────────────────────────────────────────────────────────────┘
```

## HazelcastTopicStorage

### 核心职责

```java
/**
 * Hazelcast Topic 存储
 * 管理 MessageReceiver 注册和消息发布
 */
@Service(name = "hazelcast-topic-storage")
@RunLevel(StartupRunLevel.VAL)
public class HazelcastTopicStorage implements EventListener {

    /**
     * 单例实例
     */
    private static HazelcastTopicStorage storage;

    /**
     * 消息接收器缓存
     * key: internalId
     * value: ReceiverMapping
     */
    private final Map<String, ReceiverMapping> messageReceiver = new ConcurrentHashMap<>();

    @Inject
    private EventBus eventBus;

    @Inject
    private PayaraExecutorService executorService;

    @Inject
    private Events events;

    /**
     * 事件处理
     * 管理 Hazelcast 生命周期
     */
    @Override
    public void event(Event event) {
        if (event.is(EventTypes.SERVER_SHUTDOWN)) {
            clearMessageReceivers();
        } else if (event.is(HazelcastEvents.HAZELCAST_SHUTDOWN_STARTED)) {
            // Hazelcast 关闭时注销所有接收器
            unregisterRegisteredReceivers();
        } else if (event.is(HazelcastEvents.HAZELCAST_BOOTSTRAP_COMPLETE)) {
            // Hazelcast 启动完成后注册未注册的接收器
            registerUnregisteredReceivers();
        }
    }

    /**
     * 注册消息接收器
     */
    String registerMessageReceiver(String topic, MessageReceiver<HazelcastPayload> receiver) {
        ReceiverMapping receiverMapping = new ReceiverMapping(topic, receiver);

        // 尝试注册 (如果 Hazelcast 已就绪)
        receiverMapping.setRegistered(
            eventBus.addMessageReceiver(topic, receiver)
        );

        // 存储映射
        messageReceiver.put(receiverMapping.getInternalId(), receiverMapping);

        return receiverMapping.getInternalId();
    }

    /**
     * 移除消息接收器
     */
    void removeMessageReceiver(String internalId) {
        ReceiverMapping removedReceiver = messageReceiver.remove(internalId);
        if (removedReceiver != null) {
            eventBus.removeMessageReceiver(
                removedReceiver.getTopic(),
                removedReceiver.getMessageReceiver()
            );
        }
    }

    /**
     * 发布消息
     */
    void publish(String topic, HazelcastPayload payload) {
        eventBus.publish(topic, new ClusterMessage<>(payload));
    }

    /**
     * 异步处理消息
     */
    Future<?> process(final Runnable work) {
        return executorService.submit(work);
    }
}
```

### Hazelcast 生命周期处理

```java
/**
 * 处理 Hazelcast 启动/关闭事件
 */

// 1. 注册未注册的接收器 (启动完成后)
private void registerUnregisteredReceivers() {
    messageReceiver.values().stream()
        .filter(mapping -> !mapping.isRegistered())
        .forEach(mapping -> mapping.setRegistered(
            eventBus.addMessageReceiver(
                mapping.getTopic(),
                mapping.getMessageReceiver()
            )
        ));
}

// 2. 注销已注册的接收器 (关闭前)
private void unregisterRegisteredReceivers() {
    messageReceiver.values().stream()
        .filter(ReceiverMapping::isRegistered)
        .forEach(mapping -> {
            eventBus.removeMessageReceiver(
                mapping.getTopic(),
                mapping.getMessageReceiver()
            );
            mapping.setRegistered(false);
        });
}

// 3. 清理所有监听器 (服务器关闭)
private void clearMessageReceivers() {
    messageReceiver.clear();
}
```

## HazelcastPayload

### 命令封装

```java
/**
 * Hazelcast 消息负载
 * 支持多种命令格式
 */
public abstract class HazelcastPayload implements Serializable {

    /**
     * 唯一消息 ID
     */
    private final UUID id = UUID.randomUUID();

    /**
     * 获取命令
     * 子类实现具体反序列化逻辑
     */
    public abstract Command getCommand(RemoteCommandManager rcm);

    /**
     * 字节数组负载
     * 用于序列化的命令
     */
    public static class Bytes extends HazelcastPayload {
        private final byte[] bytes;

        public Bytes(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public Command getCommand(RemoteCommandManager rcm) {
            Serializer serializer = rcm.getSerializer();
            if (serializer == null) {
                serializer = JavaSerializer.instance;
            }
            return (Command) serializer.deserialize(
                this.bytes,
                (AbstractSession) rcm.getCommandProcessor()
            );
        }
    }

    /**
     * 命令对象负载
     * 直接包含命令对象
     */
    public static class Command extends HazelcastPayload {
        private final Command command;

        public Command(Command command) {
            this.command = command;
        }

        @Override
        public Command getCommand(RemoteCommandManager rcm) {
            return this.command;
        }
    }
}
```

## ReceiverMapping

### 接收器映射

```java
/**
 * 接收器映射
 * 存储 topic、receiver 和注册状态
 */
class ReceiverMapping {

    private final String topic;
    private final MessageReceiver<HazelcastPayload> messageReceiver;
    private final String internalId;
    private boolean isRegistered;

    ReceiverMapping(String topic, MessageReceiver<HazelcastPayload> receiver) {
        this.topic = topic;
        this.messageReceiver = receiver;
        this.internalId = UUID.randomUUID().toString();
        this.isRegistered = false;
    }

    String getTopic() {
        return topic;
    }

    MessageReceiver<HazelcastPayload> getMessageReceiver() {
        return messageReceiver;
    }

    String getInternalId() {
        return internalId;
    }

    boolean isRegistered() {
        return isRegistered;
    }

    void setRegistered(boolean isRegistered) {
        this.isRegistered = isRegistered;
    }
}
```

## 配置

### persistence.xml 配置

```xml
<persistence-unit name="myPU" transaction-type="JTA">
    <jta-data-source>jdbc/__default</jta-data-source>

    <class>com.example.MyEntity</class>

    <shared-cache-mode>ENABLE_SELECTIVE</shared-cache-mode>

    <properties>
        <!-- 启用二级缓存 -->
        <property name="eclipselink.cache.shared.default" value="true" />

        <!-- 启用缓存协调 -->
        <property name="eclipselink.cache.coordination" value="true" />

        <!-- 使用 Hazelcast 传输管理器 -->
        <property name="eclipselink.cache.coordination.transport"
            value="fish.payara.persistence.eclipselink.cache.coordination.HazelcastPublishingTransportManager" />

        <!-- 协调通道名称 -->
        <property name="eclipselink.cache.coordination.channel"
            value="eclipselink-cache-coordination" />

        <!-- 序列化器 (可选) -->
        <property name="eclipselink.cache.coordination.serializer"
            value="org.eclipse.persistence.sessions.serializers.JavaSerializer" />
    </properties>
</persistence-unit>
```

### 实体缓存配置

```java
@Entity
@Cacheable(true)
@Cache(
    type = CacheType.SOFT_WEAK,
    size = 1000
)
public class MyEntity {

    @Id
    private Long id;

    private String name;

    // ...
}
```

## CLI 配置

```bash
# 启用 Hazelcast
asadmin set-hazelcast-runtime-configuration --enabled=true

# 启用 Hazelcast EclipseLink 协调
asadmin set-hazelcast-eclipse-link-coordination --enabled=true

# 配置协调通道
asadmin set-hazelcast-eclipse-link-coordination-channel --name=eclipselink-cache-coordination
```

## 相关模块

- `appserver/persistence/eclipselink` - EclipseLink JPA 提供者
- `nucleus/payara-modules/hazelcast-bootstrap` - Hazelcast 初始化
- `nucleus/eventbus` - 事件总线
- `nucleus/executorservice` - 执行器服务

## 注意事项

- **Hazelcast 依赖**: 必须启用 Hazelcast 才能使用缓存协调
- **循环防止**: 使用 publishedMessageIds 防止消息循环处理
- **异步处理**: 消息接收使用异步执行器处理
- **生命周期**: 监听 Hazelcast 启动/关闭事件动态注册/注销接收器
- **序列化**: 支持多种序列化方式 (Java, JSON 等)
- **通道隔离**: 不同应用可使用不同的协调通道
