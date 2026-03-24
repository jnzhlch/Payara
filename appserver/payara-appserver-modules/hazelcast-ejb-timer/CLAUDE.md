# Hazelcast EJB Timer - 分布式 EJB 定时器架构

## 概述

`hazelcast-ejb-timer` 模块提供基于 Hazelcast 的分布式 EJB 定时器实现，支持集群环境下的定时器持久化和故障迁移。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    HazelcastTimerStore                         │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  EJB 定时器服务                                          │ │
│  │  ├─→ _createTimer()                                     │ │
│  │  ├─→ cancelTimer()                                      │ │
│  │  ├─→ getNextTimeout()                                   │ │
│  │  ├─→ migrateTimers()                                    │ │
│  │  └─→ restoreTimers()                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Hazelcast 分布式缓存                        │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  IMap<String, HZTimer>                                   │ │
│  │  名称: HZEjbTmerCache                                    │ │
│  │  用途: 主定时器存储 (按 timerId)                         │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  IMap<Long, Set<TimerPrimaryKey>>                        │ │
│  │  名称: HZEjbTmerContainerCache                            │ │
│  │  用途: 按容器 ID 索引                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  IMap<Long, Set<TimerPrimaryKey>>                        │ │
│  │  名称: HZEjbTmerApplicationCache                         │ │
│  │  用途: 按应用 ID 索引                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    集群事件处理                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  ClusterListener                                        │ │
│  │  ├─→ memberAdded()                                      │ │
│  │  └─→ memberRemoved() - 触发定时器迁移                  │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  MessageReceiver<EjbTimerEvent>                         │ │
│  │  └─→ receiveMessage() - 处理定时器迁移通知            │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## HazelcastTimerStore

### 核心职责

```java
/**
 * 基于 Hazelcast 的 EJB 定时器存储
 * 实现集群感知的定时器持久化
 */
public class HazelcastTimerStore extends NonPersistentEJBTimerService
        implements ClusterListener, MessageReceiver<EjbTimerEvent> {

    // 缓存名称常量
    private static final String EJB_TIMER_CACHE_NAME = "HZEjbTmerCache";
    private static final String EJB_TIMER_CONTAINER_CACHE_NAME = "HZEjbTmerContainerCache";
    private static final String EJB_TIMER_APPLICAION_CACHE_NAME = "HZEjbTmerApplicationCache";

    // Hazelcast 分布式映射
    private final IMap<String, HZTimer> pkCache;              // 主存储
    private final IMap<Long, Set<TimerPrimaryKey>> containerCache; // 容器索引
    private final IMap<Long, Set<TimerPrimaryKey>> applicationCache; // 应用索引

    private final String serverName;       // 服务器名称
    private final HazelcastInstance hazelcast;
}
```

### 初始化流程

```java
/**
 * 初始化定时器存储
 */
static void init(HazelcastCore core) {
    try {
        // 1. 创建 HazelcastTimerStore 实例
        HazelcastTimerStore store = new HazelcastTimerStore(core);

        // 2. 注册集群监听器
        PayaraCluster cluster = Globals.getDefaultBaseServiceLocator()
            .getService(PayaraCluster.class);
        cluster.addClusterListener(store);

        // 3. 设置为持久化定时器服务
        EJBTimerService.setPersistentTimerService(store);

        // 4. 注册消息接收器
        cluster.getEventBus().addMessageReceiver(
            EjbTimerEvent.EJB_TIMER_EVENTS_TOPIC, store);
    } catch (Exception ex) {
        Logger.getLogger(HazelcastTimerStore.class.getName())
            .log(Level.WARNING, "Problem when initialising Timer Store", ex);
    }
}

/**
 * 构造函数
 */
public HazelcastTimerStore(HazelcastCore core) throws Exception {
    // 1. 验证 Hazelcast 已启用
    if (!core.isEnabled()) {
        throw new Exception("Hazelcast MUST be enabled when using the HazelcastTimerStore");
    }

    // 2. 获取 Hazelcast 实例
    hazelcast = core.getInstance();

    // 3. 获取分布式映射
    pkCache = hazelcast.getMap(EJB_TIMER_CACHE_NAME);
    containerCache = hazelcast.getMap(EJB_TIMER_CONTAINER_CACHE_NAME);
    applicationCache = hazelcast.getMap(EJB_TIMER_APPLICAION_CACHE_NAME);

    // 4. 获取服务器名称
    serverName = core.getAttribute(
        hazelcast.getCluster().getLocalMember().getUuid(),
        HazelcastCore.INSTANCE_ATTRIBUTE);

    // 5. 设置所有者
    this.ownerIdOfThisServer_ = serverName;
    this.domainName_ = hazelcast.getConfig().getClusterName();

    // 6. 启用定时器重调度
    super.enableRescheduleTimers();
}
```

### 创建定时器

```java
/**
 * 创建定时器
 */
@Override
protected void _createTimer(TimerPrimaryKey timerId,
                             long containerId,
                             long applicationId,
                             Object timedObjectPrimaryKey,
                             String server_name,
                             Date initialExpiration,
                             long intervalDuration,
                             EJBTimerSchedule schedule,
                             TimerConfig timerConfig) throws Exception {

    if (timerConfig.isPersistent()) {
        // 1. 创建 HZTimer 对象
        HZTimer hzTimer = new HZTimer(
            timerId, containerId, applicationId,
            timedObjectPrimaryKey, server_name, server_name,
            initialExpiration, intervalDuration,
            schedule, timerConfig
        );

        // 2. 存储到主缓存
        pkCache.put(timerId.timerId, hzTimer);

        // 3. 添加到容器索引
        Set<TimerPrimaryKey> keysForContainer = containerCache.get(containerId);
        if (keysForContainer == null) {
            keysForContainer = new HashSet<>();
        }
        keysForContainer.add(timerId);
        containerCache.put(containerId, keysForContainer);

        // 4. 添加到应用索引
        Set<TimerPrimaryKey> keysForApp = applicationCache.get(applicationId);
        if (keysForApp == null) {
            keysForApp = new HashSet<>();
        }
        keysForApp.add(timerId);
        applicationCache.put(applicationId, keysForApp);

        // 5. 添加事务同步
        TransactionManager tm = ejbContainerUtil.getTransactionManager();
        boolean localTx = tm.getTransaction() == null;

        if (localTx) {
            tm.begin();
        }

        addTimerSynchronization(null,
            timerId.getTimerId(), initialExpiration,
            containerId, ownerIdOfThisServer_, true);

        if (localTx) {
            tm.commit();
        }
    } else {
        // 非持久化定时器
        addTimerSynchronization(null,
            timerId.getTimerId(), initialExpiration,
            containerId, ownerIdOfThisServer_, false);
    }
}
```

### 定时器迁移

```java
/**
 * 迁移定时器
 * 当集群成员失效时，自动迁移定时器到幸存实例
 */
@Override
public int migrateTimers(String fromOwnerId) {
    String ownerIdOfThisServer = getOwnerIdOfThisServer();

    // 1. 验证不是迁移到自身
    if (fromOwnerId.equals(ownerIdOfThisServer)) {
        throw new IllegalStateException(
            "Attempt to migrate timers from " + ownerIdOfThisServer + " to itself");
    }

    logger.log(Level.INFO, "Beginning timer migration process from owner {0} to {1}",
        new Object[]{fromOwnerId, ownerIdOfThisServer});

    TransactionManager tm = ejbContainerUtil.getTransactionManager();
    HashMap<String, HZTimer> toRestore = new HashMap<>();
    int totalTimersMigrated = 0;

    // 2. 收集需要迁移的定时器
    for (String pk : pkCache.keySet()) {
        HZTimer hZTimer = pkCache.get(pk);
        if (hZTimer.getOwnerId().equals(fromOwnerId)) {
            toRestore.put(pk, hZTimer);
            // 更新所有者
            hZTimer.setOwnerId(ownerIdOfThisServer);
            hZTimer.setMemberName(serverName);
        }
    }

    // 3. 更新分布式缓存
    for (Entry<String, HZTimer> entry : toRestore.entrySet()) {
        pkCache.put(entry.getKey(), entry.getValue());
        totalTimersMigrated++;
    }

    if (totalTimersMigrated > 0) {
        boolean success = false;
        try {
            logger.log(Level.INFO, "Timer migration phase 1 complete. " +
                "Changed ownership of {0} timers. Now reactivating timers...",
                toRestore.size());

            // 4. 通知容器
            _notifyContainers(toRestore.values());

            // 5. 恢复定时器
            tm.begin();
            _restoreTimers(toRestore.values());
            success = true;

            // 6. 通知源实例清除本地缓存
            _notifyMigratedFromInstance(fromOwnerId);

        } finally {
            try {
                tm.commit();
            } catch (Exception re) {
                if (success) {
                    throw createEJBException(re);
                }
            }
        }
    }

    return totalTimersMigrated;
}
```

### 集群成员移除处理

```java
/**
 * 集群成员移除事件处理
 * 自动检测并迁移失效实例的定时器
 */
@Override
public void memberRemoved(MemberEvent event) {
    // 使用分布式锁防止并发迁移
    FencedLock hazelcastLock = hazelcast.getCPSubsystem().getLock("EJB-TIMER-LOCK");
    hazelcastLock.lock();
    try {
        // 1. 收集失效成员的定时器
        Collection<HZTimer> allTimers = pkCache.values();
        Collection<HZTimer> removedTimers = new HashSet<>();
        for (HZTimer timer : allTimers) {
            if (timer.getMemberName().equals(event.getServer())) {
                removedTimers.add(timer);
            }
        }

        // 2. 恢复定时器到当前实例
        if (!removedTimers.isEmpty()) {
            logger.log(Level.INFO, "==> Restoring Timers ... ");
            Collection<HZTimer> restored = _restoreTimers(removedTimers);

            // 3. 更新分布式缓存中的所有者
            for (HZTimer timer : restored) {
                pkCache.put(timer.getKey().getTimerId(), timer);
            }
            logger.log(Level.INFO, "<== ... Timers Restored.");
        }
    } finally {
        hazelcastLock.unlock();
    }
}
```

### 定时器恢复

```java
/**
 * 恢复定时器
 * 在服务器启动或迁移后恢复定时器
 */
private Collection<HZTimer> _restoreTimers(Collection<HZTimer> timersEligibleForRestoration) {
    Map<RuntimeTimerState, Date> timersToRestore = new HashMap<>();
    Set<TimerPrimaryKey> timerIdsToRemove = new HashSet<>();
    Set<HZTimer> result = new HashSet<>();

    for (HZTimer timer : timersEligibleForRestoration) {
        TimerPrimaryKey timerId = timer.getKey();

        // 1. 检查是否已恢复
        if (getTimerState(timerId) != null) {
            result.add(timer);
            continue;
        }

        long containerId = timer.getContainerId();

        // 2. 验证容器仍然存在
        BaseContainer container = getContainer(containerId);
        if (container == null) {
            logger.log(Level.FINE, "Skipping timer {0} for container that is not up: {1}",
                new Object[]{timerId, containerId});
            continue;
        }

        // 3. 更新 applicationId (兼容旧版本)
        long appid = timer.getApplicationId();
        if (appid == 0) {
            timer.setApplicationId(container.getApplicationId());
        }

        // 4. 确保所有者是当前服务器
        if (!timer.getMemberName().equals(serverName)) {
            timer.setMemberName(serverName);
        }

        // 5. 创建运行时定时器状态
        Date initialExpiration = timer.getInitialExpiration();
        Object timedObjectPrimaryKey = null;

        if (container.getContainerType() == BaseContainer.ContainerType.ENTITY) {
            timedObjectPrimaryKey = timer.getTimedObjectPk();
        }

        RuntimeTimerState timerState = new RuntimeTimerState(
            timerId, initialExpiration,
            timer.getIntervalDuration(), container,
            timedObjectPrimaryKey,
            timer.getSchedule(),
            null, true
        );

        timerCache_.addTimer(timerId, timerState);

        // 6. 计算下次超时时间
        Date expirationTime = initialExpiration;
        Date now = new Date();

        if (timerState.isPeriodic()) {
            Date lastExpiration = timer.getLastExpiration();
            EJBTimerSchedule ts = timer.getSchedule();

            // 处理错过的超时
            if ((lastExpiration == null) && now.after(initialExpiration)) {
                logger.log(Level.INFO, "Rescheduling missed expiration for periodic timer {0}. " +
                    "Timer expirations should have been delivered starting at {1}",
                    new Object[]{timerState, initialExpiration});
                // 保持 initialExpiration 触发立即执行
            } else if ((lastExpiration != null) &&
                ((ts != null && ts.getNextTimeout(lastExpiration).getTimeInMillis() < now.getTime())
                || ((ts == null) && now.getTime() - lastExpiration.getTime() > timer.getIntervalDuration()))) {
                logger.log(Level.INFO, "Rescheduling missed expiration for periodic timer {0}. " +
                    "Last timer expiration occurred at {1}",
                    new Object[]{timerState, lastExpiration});
                // 保持 initialExpiration 触发立即执行
            } else {
                expirationTime = calcNextFixedRateExpiration(timerState);
            }
        }

        // 7. 移除永不到期的定时器
        if (expirationTime == null) {
            logger.log(Level.INFO, "Removing schedule-based timer {0} that will never expire again",
                timerState);
            timerIdsToRemove.add(timerId);
        } else {
            timersToRestore.put(timerState, expirationTime);
            result.add(timer);
        }
    }

    // 8. 移除无效定时器
    removeTimers(timerIdsToRemove);

    // 9. 调度恢复的定时器
    for (Map.Entry<RuntimeTimerState, Date> next : timersToRestore.entrySet()) {
        RuntimeTimerState nextTimer = next.getKey();
        TimerPrimaryKey timerId = nextTimer.getTimerId();
        Date expiration = next.getValue();
        scheduleTask(timerId, expiration);
        logger.log(Level.FINE, "EJBTimerService.restoreTimers(), scheduling timer {0}", nextTimer);
    }

    return result;
}
```

### 销毁定时器

```java
/**
 * 销毁应用的所有定时器
 */
@Override
public void destroyAllTimers(long applicationId) {
    // 1. 获取应用的所有定时器
    Set<TimerPrimaryKey> timerIds = applicationCache.get(applicationId);

    if (timerIds == null || timerIds.isEmpty()) {
        logger.log(Level.INFO, "No timers to be deleted for id: {0}", applicationId);
        return;
    }

    // 2. 从主缓存删除
    for (TimerPrimaryKey timerId : timerIds) {
        pkCache.remove(timerId.timerId);
    }

    logger.log(Level.INFO, "Destroyed {0} timers for application {1}",
        new Object[]{timerIds.size(), applicationId});

    // 3. 清空应用索引
    timerIds.clear();
    applicationCache.remove(applicationId);
}

/**
 * 销毁容器的所有定时器
 */
@Override
public void destroyTimers(long containerId) {
    Set<TimerPrimaryKey> timerIds = containerCache.get(containerId);

    if (timerIds == null || timerIds.isEmpty()) {
        logger.log(Level.INFO, "No timers to be deleted for id: {0}", containerId);
        return;
    }

    logger.log(Level.INFO, "Destroyed {0} timers for container {1}",
        new Object[]{timerIds.size(), containerId});

    timerIds.clear();
    containerCache.put(containerId, timerIds);
}

/**
 * 移除单个定时器
 */
private void removeTimer(HZTimer timer) {
    pkCache.remove(timer.getKey().timerId);

    // 从应用索引移除
    Set<TimerPrimaryKey> keys = applicationCache.get(timer.getApplicationId());
    if (keys != null) {
        keys.remove(timer.getKey());
        applicationCache.put(timer.getApplicationId(), keys);
    }

    // 从容器索引移除
    keys = containerCache.get(timer.getContainerId());
    if (keys != null) {
        keys.remove(timer.getKey());
        containerCache.put(timer.getContainerId(), keys);
    }
}
```

### HZTimer 数据结构

```java
/**
 * Hazelcast 定时器数据结构
 */
public class HZTimer implements Serializable {
    private TimerPrimaryKey key;
    private long containerId;
    private long applicationId;
    private Object timedObjectPk;   // 实体 Bean 主键
    private String ownerId;          // 定时器所有者
    private String memberName;       // 成员名称
    private Date initialExpiration;
    private long intervalDuration;
    private EJBTimerSchedule schedule;
    private TimerConfig timerConfig;
    private Date lastExpiration;

    // 序列化支持
    private static final long serialVersionUID = 1L;
}
```

## EjbTimerEvent

### 事件类型

```java
/**
 * EJB 定时器事件
 * 用于集群间定时器迁移通知
 */
public class EjbTimerEvent implements Serializable {
    public enum Event {
        MIGRATED  // 定时器已迁移到其他实例
    }

    private Event eventType;
    private InstanceDescriptor id;  // 源实例描述符

    /**
     * 接收定时器迁移事件
     */
    @Override
    public void receiveMessage(ClusterMessage<EjbTimerEvent> ejbTimerEvent) {
        if (ejbTimerEvent.getPayload().getEventType().equals(Event.MIGRATED)) {
            PayaraInstanceImpl instance = Globals.getDefaultBaseServiceLocator()
                .getService(PayaraInstanceImpl.class);

            // 如果是源实例，清除本地定时器缓存
            if (ejbTimerEvent.getPayload().getId().equals(instance.getLocalDescriptor())) {
                removeLocalTimers();
            }
        }
    }
}
```

## 定时器调度

```java
/**
 * 创建调度定时器
 */
@Override
public void createSchedules(long containerId,
                             long applicationId,
                             Map<MethodDescriptor, List<ScheduledTimerDescriptor>> methodDescriptorSchedules,
                             String server_name) {
    TransactionManager tm = ejbContainerUtil.getTransactionManager();
    try {
        tm.begin();

        // 1. 检查是否已有定时器
        Set<TimerPrimaryKey> keys = containerCache.get(containerId);

        if (keys == null || keys.isEmpty()) {
            // 2. 创建调度定时器
            createSchedules(containerId, applicationId,
                methodDescriptorSchedules, null, server_name, false, true);
        }

        tm.commit();

    } catch (Exception e) {
        recoverAndCreateSchedulesError(e, tm);
    }
}

/**
 * 恢复和创建调度定时器
 */
@Override
protected Map<TimerPrimaryKey, Method> recoverAndCreateSchedules(
        long containerId,
        long applicationId,
        Map<Method, List<ScheduledTimerDescriptor>> schedules,
        boolean deploy) {

    Map<TimerPrimaryKey, Method> result = new HashMap<>();
    boolean lostCluster = false;
    Set<HZTimer> activeTimers = new HashSet<>();

    // 1. 获取容器的所有定时器
    Set<TimerPrimaryKey> containerKeys = containerCache.get(containerId);
    Set<TimerPrimaryKey> deadKeys = new HashSet<>();
    Set<HZTimer> timers = new HashSet<>();

    if (containerKeys != null) {
        for (TimerPrimaryKey containerKey : containerKeys) {
            HZTimer timer = pkCache.get(containerKey.timerId);
            if (timer != null) {
                if (timer.getMemberName().equals(this.serverName)) {
                    activeTimers.add(timer);
                } else {
                    timers.add(timer); // 在其他实例上
                }
            } else {
                deadKeys.add(containerKey);
            }
        }

        // 2. 清理无效键
        if (!deadKeys.isEmpty()) {
            logger.log(Level.INFO, "Cleaning out {0} dead timer ids from Container Cache",
                deadKeys.size());
            for (TimerPrimaryKey deadKey : deadKeys) {
                containerKeys.remove(deadKey);
            }
            containerCache.put(containerId, containerKeys);
        }
    } else if (containerKeys == null && deploy == false) {
        // 3. 检测集群存储丢失
        logger.log(Level.INFO, "Looks like we lost the data grid storage will recreate timers");
        lostCluster = true;
    }

    // 4. 恢复活动定时器
    timers.addAll(_restoreTimers(activeTimers));

    // 5. 匹配调度定时器与 @Schedule 方法
    boolean schedulesExist = (schedules.size() > 0);
    for (HZTimer timer : timers) {
        EJBTimerSchedule ts = timer.getSchedule();
        if (ts != null && ts.isAutomatic() && schedulesExist) {
            Iterator<Map.Entry<Method, List<ScheduledTimerDescriptor>>> schedulesIterator =
                schedules.entrySet().iterator();

            while (schedulesIterator.hasNext()) {
                Map.Entry<Method, List<ScheduledTimerDescriptor>> entry = schedulesIterator.next();
                Method m = entry.getKey();

                if (m.getName().equals(ts.getTimerMethodName())
                        && m.getParameterTypes().length == ts.getMethodParamCount()) {
                    result.put(new TimerPrimaryKey(timer.getKey().getTimerId()), m);
                    schedulesIterator.remove();
                }
            }
        }
    }

    // 6. 创建未匹配的调度定时器
    try {
        if (!schedules.isEmpty()) {
            createSchedules(containerId, applicationId, schedules, result, serverName, true,
                (deploy && isDas) || lostCluster);
        }
    } catch (Exception ex) {
        Logger.getLogger(HazelcastTimerStore.class.getName()).log(Level.SEVERE, null, ex);
    }

    return result;
}
```

## 配置

### 启用 Hazelcast EJB Timer

```xml
<!-- domain.xml -->
<configs>
    <config name="server-config">
        <!-- 启用 Hazelcast -->
        <hazelcast-runtime-configuration>
            <enabled>true</enabled>
        </hazelcast-runtime-configuration>

        <!-- EJB 容器配置 -->
        <ejb-container>
            <!-- 使用 Hazelcast 定时器存储 -->
            <ejb-timer-service>databasedejbtimer</ejb-timer-service>
        </ejb-container>
    </config>
</configs>
```

### CLI 命令

```bash
# 启用 Hazelcast
asadmin set-hazelcast-runtime-configuration --enabled=true

# 列出定时器
asadmin list-timers

# 迁移定时器
asadmin migrate-timers --fromOwnerId=instance1

# 配置定时器存储
asadmin set-ejb-timer-service --timerdatabasename=TimerService
```

## 相关模块

- `nucleus/payara-modules/hazelcast-bootstrap` - Hazelcast 初始化
- `appserver/ejb/ejb-container` - EJB 容器
- `nucleus/cluster` - 集群基础设施
- `nucleus/eventbus` - 事件总线

## 注意事项

- **Hazelcast 依赖**: 必须启用 Hazelcast 才能使用 HazelcastTimerStore
- **分布式锁**: 使用 FencedLock 防止并发迁移
- **事务支持**: 持久化定时器操作支持事务
- **自动迁移**: 集群成员失效时自动迁移定时器
- **索引优化**: 使用容器和应用索引加速查询
- **错过处理**: 支持检测和恢复错过的定时器超时
