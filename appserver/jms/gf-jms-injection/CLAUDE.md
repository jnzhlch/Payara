# JMS Injection - CDI 注入架构

## 概述

`gf-jms-injection` 模块提供 JMS 2.0/2.1 的 CDI (Contexts and Dependency Injection) 集成，支持通过 `@Inject` 注入 `JMSContext` 和相关资源。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    JMS CDI Extension                            │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  JMSCDIExtension                                         │ │
│  │  ┌────────────────────────────────────────────────────┐ │ │
│  │  │  CDI Portable Extension                            │ │ │
│  │  │  ├─→ beforeBeanDiscovery()                        │ │ │
│  │  │  ├─→ processInjectionTarget()                     │ │ │
│  │  │  ├─→ afterBeanDiscovery()                         │ │ │
│  │  │  └─→ Registers beans:                            │ │ │
│  │  │      - RequestedJMSContextManager                 │ │ │
│  │  │      - TransactedJMSContextManager                │ │ │
│  │  │      - InjectableJMSContext                        │ │ │
│  │  └────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Context Managers                             │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  AbstractJMSContextManager                              │ │
│  │  ├─→ JMSContextEntry map                               │ │
│  │  ├─→ getContext()                                      │ │
│  │  ├─→ cleanup()                                         │ │
│  │  └─→ delegate()                                        │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  RequestedJMSContextManager                             │ │
│  │  └─→ @RequestScoped                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  TransactedJMSContextManager                            │ │
│  │  └─→ @TransactionScoped                                │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    InjectableJMSContext                         │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  ForwardingJMSContext                                   │ │
│  │  ├─→ delegate() ──→ 根据事务状态选择管理器              │ │
│  │  ├─→ In transaction ──→ TransactedJMSContextManager     │ │
│  │  └─→ No transaction ──→ RequestedJMSContextManager      │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## JMSCDIExtension

### 核心 CDI 扩展

```java
/*
 * CDI 便携式扩展，注册系统级 JMSContext Bean
 * 可被所有应用程序注入使用
 */
public class JMSCDIExtension implements Extension {

    /**
     * 创建本地 Bean
     */
    private Bean createLocalBean(BeanManager beanManager, Class beanClass) {
        AnnotatedType annotatedType = beanManager.createAnnotatedType(beanClass);
        LocalPassivationCapableBean localBean =
            new LocalPassivationCapableBean(beanClass);
        InjectionTargetFactory injectionTargetFactory =
            beanManager.getInjectionTargetFactory(annotatedType);
        localBean.setInjectionTarget(
            injectionTargetFactory.createInjectionTarget(localBean));
        return localBean;
    }

    /**
     * Bean 发现后注册 JMS 相关 Bean
     */
    public void afterBeanDiscovery(
            @Observes AfterBeanDiscovery afterBeanDiscoveryEvent,
            BeanManager beanManager) {

        // 注册 RequestScoped 管理器
        Bean requestManagerBean =
            createLocalBean(beanManager, RequestedJMSContextManager.class);
        afterBeanDiscoveryEvent.addBean(requestManagerBean);

        // 注册 TransactionScoped 管理器
        Bean transactionManagerBean =
            createLocalBean(beanManager, TransactedJMSContextManager.class);
        afterBeanDiscoveryEvent.addBean(transactionManagerBean);

        // 注册 Dependent 上下文包装器
        Bean contextBean =
            createLocalBean(beanManager, InjectableJMSContext.class);
        afterBeanDiscoveryEvent.addBean(contextBean);
    }
}
```

### LocalPassivationCapableBean

```java
public static class LocalPassivationCapableBean
        implements Bean, PassivationCapable {

    private String id = UUID.randomUUID().toString();
    private Class beanClass;
    private InjectionTarget injectionTarget;

    @Override
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return injectionTarget.getInjectionPoints();
    }

    @Override
    public String getName() {
        return beanClass.getName();
    }

    @Override
    public Set<Annotation> getQualifiers() {
        Set<Annotation> qualifiers = new HashSet<Annotation>();
        qualifiers.add(getDefaultAnnotationLiteral());
        qualifiers.add(getAnyAnnotationLiteral());
        return qualifiers;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        if (beanClass.isAnnotationPresent(RequestScoped.class))
            return RequestScoped.class;
        else if (beanClass.isAnnotationPresent(TransactionScoped.class))
            return TransactionScoped.class;
        else
            return Dependent.class;
    }

    @Override
    public Object create(CreationalContext ctx) {
        Object instance = injectionTarget.produce(ctx);
        injectionTarget.inject(instance, ctx);
        injectionTarget.postConstruct(instance);
        return instance;
    }

    @Override
    public void destroy(Object instance, CreationalContext ctx) {
        injectionTarget.preDestroy(instance);
        injectionTarget.dispose(instance);
        ctx.release();
    }

    @Override
    public String getId() {
        return id;
    }
}
```

## InjectableJMSContext

### 核心 JMSContext 包装器

```java
/**
 * JMSContext 包装器，用户通过注入获取
 * 读取注入点的元数据，委托给 JMSContextManager
 */
public class InjectableJMSContext extends ForwardingJMSContext
        implements Serializable {

    private final String ipId;  // 注入点 ID
    private final String id;    // 作用域 ID

    @Inject
    private transient Instance<TransactedJMSContextManager> tm;

    private final RequestedJMSContextManager requestedManager;
    private TransactedJMSContextManager transactedManager;

    private final JMSContextMetadata metadata;
    private transient ConnectionFactory connectionFactory;
    private transient ConnectionFactory connectionFactoryPM;

    @Inject
    public InjectableJMSContext(InjectionPoint ip,
                               RequestedJMSContextManager rm) {
        getTransactionManager();

        // 读取注入点注解
        JMSConnectionFactory jmsConnectionFactoryAnnot =
            ip.getAnnotated().getAnnotation(JMSConnectionFactory.class);
        JMSSessionMode sessionModeAnnot =
            ip.getAnnotated().getAnnotation(JMSSessionMode.class);
        JMSPasswordCredential credentialAnnot =
            ip.getAnnotated().getAnnotation(JMSPasswordCredential.class);

        ipId = UUID.randomUUID().toString();
        this.requestedManager = rm;
        metadata = new JMSContextMetadata(
            jmsConnectionFactoryAnnot,
            sessionModeAnnot,
            credentialAnnot);
        id = metadata.getFingerPrint();
    }
```

### 委托选择

```java
    @Override
    protected JMSContext delegate() {
        AbstractJMSContextManager manager = requestedManager;
        boolean isInTransaction = isInTransaction();

        // 根据事务状态选择管理器
        if (isInTransaction)
            manager = getTransactedManager();

        try {
            return manager.getContext(ipId, id, metadata,
                                     getConnectionFactory(isInTransaction));
        } catch (ContextNotActiveException e) {
            throw new RuntimeException(
                "An injected JMSContext cannot be used when there is " +
                "neither a transaction or a valid request scope.", e);
        }
    }

    private boolean isInTransaction() {
        boolean isInTransaction = false;
        try {
            Transaction txn = getTransactionManager().getTransaction();
            if (txn != null)
                isInTransaction = true;
        } catch (SystemException e) {
            throw new RuntimeException(
                "Failed to detect transaction status of current thread.", e);
        }
        return isInTransaction;
    }
}
```

### ConnectionFactory 获取

```java
    private ConnectionFactory getConnectionFactory(boolean isInTransaction) {
        ConnectionFactory cachedCF = null;
        boolean usePMResource = isInTransaction &&
            (usePMResourceInTransaction || connectionFactoryPM != null);

        if (usePMResource) {
            cachedCF = connectionFactoryPM;
        } else {
            cachedCF = connectionFactory;
        }

        if (cachedCF == null) {
            String jndiName;
            if (metadata.getLookup() == null) {
                // 使用平台默认连接工厂
                jndiName = JMSContextMetadata.DEFAULT_CONNECTION_FACTORY;
                // DEFAULT_CONNECTION_FACTORY = "java:comp/DefaultJMSConnectionFactory"
            } else {
                jndiName = metadata.getLookup();
            }

            InitialContext initialContext = new InitialContext();
            try {
                // 检查是否为 __PM 资源
                boolean isPMName = jndiName.endsWith("__pm");
                if (isPMName) {
                    jndiName = jndiName.substring(0, jndiName.length() - 4);
                }

                cachedCF = (ConnectionFactory) initialContext.lookup(jndiName);

                // 事务中使用 PM 资源
                if (isInTransaction && (usePMResourceInTransaction || isPMName)) {
                    jndiName = ConnectorsUtil.getPMJndiName(jndiName);
                    cachedCF = (ConnectionFactory) initialContext.lookup(jndiName);
                    usePMResource = true;
                }
            } finally {
                initialContext.close();
            }

            if (usePMResource)
                connectionFactoryPM = cachedCF;
            else
                connectionFactory = cachedCF;
        }
        return cachedCF;
    }
```

### 生命周期管理

```java
    @PreDestroy
    public void cleanup() {
        cleanupManager(requestedManager);
        cleanupManager(getTransactedManager());
    }

    private void cleanupManager(AbstractJMSContextManager manager) {
        try {
            manager.cleanup();
        } catch (ContextNotActiveException cnae) {
            // 应用程序卸载时忽略 ContextNotActiveException
        } catch (Throwable t) {
            logger.log(Level.SEVERE,
                "Failed to cleaning up {0} JMSContext wrapper with id {1} " +
                "and metadata [{2}]. Reason: {3}.",
                new Object[]{
                    manager.getType(),
                    ipId,
                    metadata.getLookup(),
                    t.toString()
                });
        }
    }
}
```

## JMSContextMetadata

### 元数据封装

```java
/**
 * 序列化对象，保存在注入点指定的 JMSContext 信息
 */
public class JMSContextMetadata implements Serializable {
    public final static String DEFAULT_CONNECTION_FACTORY =
        "java:comp/DefaultJMSConnectionFactory";

    private final String lookup;
    private final int sessionMode;
    private final String userName;
    private final String password;
    private String fingerPrint;

    JMSContextMetadata(JMSConnectionFactory jmsConnectionFactoryAnnot,
                      JMSSessionMode sessionModeAnnot,
                      JMSPasswordCredential credentialAnnot) {
        if (jmsConnectionFactoryAnnot == null) {
            lookup = null;
        } else {
            lookup = jmsConnectionFactoryAnnot.value().trim();
        }

        if (sessionModeAnnot == null) {
            sessionMode = JMSContext.AUTO_ACKNOWLEDGE;
        } else {
            sessionMode = sessionModeAnnot.value();
        }

        if (credentialAnnot == null) {
            userName = null;
            password = null;
        } else {
            userName = credentialAnnot.userName();
            password = getUnAliasedPwd(credentialAnnot.password());
        }
    }

    /**
     * 生成指纹用于缓存键
     * 使用 MD5 哈希所有元数据属性
     */
    public String getFingerPrint() {
        if (fingerPrint == null) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte delimeter = (byte) '|';

                String cf = (lookup == null) ?
                    DEFAULT_CONNECTION_FACTORY : lookup;
                md.update(cf.getBytes("ISO-8859-1"));
                md.update(delimeter);
                md.update((byte) sessionMode);
                md.update(delimeter);

                if (userName != null)
                    md.update(userName.getBytes("ISO-8859-1"));
                md.update(delimeter);

                if (password != null)
                    md.update(password.getBytes("ISO-8859-1"));
                md.update(delimeter);

                byte[] result = md.digest();
                StringBuilder buff = new StringBuilder();
                for(int i = 0; i < result.length; i++) {
                    String byteStr = Integer.toHexString(result[i] & 0xFF);
                    if(byteStr.length() < 2)
                        buff.append('0');
                    buff.append(byteStr);
                }
                fingerPrint = buff.toString();
            } catch (Exception e) {
                throw new RuntimeException(
                    "Couldn't make digest of JMSContextMetadata content", e);
            }
        }
        return fingerPrint;
    }
}
```

## Context Managers

### AbstractJMSContextManager

```java
/**
 * JMSContext 管理器抽象基类
 * 管理 JMSContext 实例的创建、缓存和清理
 */
public abstract class AbstractJMSContextManager {
    // JMSContextEntry 映射
    // Key: metadata.getFingerPrint()
    // Value: JMSContextEntry 包含实际的 JMSContext
    protected Map<String, JMSContextEntry> contexts;

    public AbstractJMSContextManager() {
        contexts = new HashMap<>();
    }

    /**
     * 获取或创建 JMSContext
     */
    public JMSContext getContext(String ipId,
                                String id,
                                JMSContextMetadata metadata,
                                ConnectionFactory connectionFactory) {
        String key = metadata.getFingerPrint();

        JMSContextEntry entry = contexts.get(key);
        if (entry == null) {
            JMSContext context = createJMSContext(metadata, connectionFactory);
            entry = new JMSContextEntry(context, ipId, id);
            contexts.put(key, entry);
        }

        entry.incrementRefCount();
        return entry.getJMSContext();
    }

    /**
     * 创建新的 JMSContext
     */
    private JMSContext createJMSContext(JMSContextMetadata metadata,
                                       ConnectionFactory connectionFactory) {
        JMSContext context;
        String userName = metadata.getUserName();
        String password = metadata.getPassword();

        if (userName != null && password != null) {
            context = connectionFactory.createContext(
                userName, password, metadata.getSessionMode());
        } else {
            context = connectionFactory.createContext(
                metadata.getSessionMode());
        }

        if (metadata.getClientId() != null) {
            context.setClientID(metadata.getClientId());
        }

        return context;
    }

    /**
     * 清理所有上下文
     */
    public void cleanup() {
        for (JMSContextEntry entry : contexts.values()) {
            if (entry.getRefCount() == 0) {
                entry.getJMSContext().close();
            }
        }
        contexts.clear();
    }

    public abstract String getType();
}
```

### RequestedJMSContextManager

```java
@RequestScoped
public class RequestedJMSContextManager extends AbstractJMSContextManager {
    public RequestedJMSContextManager() {
        super();
    }

    public String getType() {
        return "RequestScoped";
    }
}
```

### TransactedJMSContextManager

```java
@TransactionScoped
public class TransactedJMSContextManager extends AbstractJMSContextManager {
    public TransactedJMSContextManager() {
        super();
    }

    public String getType() {
        return "TransactionScoped";
    }
}
```

## JMSContextEntry

```java
/**
 * JMSContext 条目，跟踪引用计数
 */
public class JMSContextEntry {
    private final JMSContext jmsContext;
    private final String ipId;     // 注入点 ID
    private final String id;       // 元数据指纹
    private int refCount;          // 引用计数

    public JMSContextEntry(JMSContext jmsContext, String ipId, String id) {
        this.jmsContext = jmsContext;
        this.ipId = ipId;
        this.id = id;
        this.refCount = 0;
    }

    public void incrementRefCount() {
        refCount++;
    }

    public void decrementRefCount() {
        if (refCount > 0)
            refCount--;
    }

    public int getRefCount() {
        return refCount;
    }

    public JMSContext getJMSContext() {
        return jmsContext;
    }
}
```

## 注入流程

```
@Inject 调用
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│  CDI 容器查找 InjectableJMSContext Bean                        │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  InjectableJMSContext 构造函数                            │ │
│  │  ├─→ 读取 InjectionPoint                                  │ │
│  │  ├─→ 提取注解:                                            │ │
│  │  │   - @JMSConnectionFactory                              │ │
│  │  │   - @JMSSessionMode                                   │ │
│  │  │   - @JMSPasswordCredential                            │ │
│  │  └─→ 创建 JMSContextMetadata                             │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  调用 delegate() 方法                                            │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  检查事务状态                                             │ │
│  │  ├─→ isInTransaction()?                                  │ │
│  │  │   ├─→ Yes: 使用 TransactedJMSContextManager           │ │
│  │  │   └─→ No: 使用 RequestedJMSContextManager             │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  管理器获取 JMSContext                                           │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  AbstractJMSContextManager.getContext()                  │ │
│  │  ├─→ 计算指纹 (metadata.getFingerPrint())               │ │
│  │  ├─→ 检查缓存                                           │ │
│  │  ├─→ 未命中: 创建新 JMSContext                          │ │
│  │  └─→ 返回 JMSContext                                     │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## 使用示例

### 基本注入

```java
// 使用默认连接工厂
@Inject
private JMSContext context;

// 使用指定连接工厂
@Inject
@JMSConnectionFactory("java:global/jms/MyConnectionFactory")
private JMSContext context;

// 设置会话模式
@Inject
@JMSConnectionFactory("java:global/jms/MyConnectionFactory")
@JMSSessionMode(JMSContext.DUPS_OK_ACKNOWLEDGE)
private JMSContext context;

// 使用凭证
@Inject
@JMSConnectionFactory("java:global/jms/MyConnectionFactory")
@JMSPasswordCredential(userName = "user", password = "pwd")
private JMSContext context;
```

### 事务集成

```java
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public void sendMessageInTransaction() {
    // 在事务中运行
    // JMSContext 自动使用 TransactionScoped 管理器
    context.createProducer().send(queue, message);
    // 消息在事务提交时发送
}
```

### JMS 2.0 简化 API

```java
@Inject
@JMSConnectionFactory("java:global/jms/MyConnectionFactory")
private JMSContext context;

@Inject
private Queue myQueue;

public void sendSimpleMessage() {
    // JMS 2.0 简化 API
    context.createProducer().send(myQueue, "Hello World");

    // 使用 MessageBuilder
    context.createProducer().send(myQueue,
        context.createTextMessage("Hello"));
}

public String receiveMessage() {
    // JMS 2.0 简化接收
    return context.createConsumer(myQueue).receiveBody(String.class);
}
```

### 作用域说明

| 作用域 | 管理器 | 说明 |
|-------|--------|------|
| `@RequestScoped` | `RequestedJMSContextManager` | 每个 HTTP 请求一个 JMSContext |
| `@TransactionScoped` | `TransactedJMSContextManager` | 每个事务一个 JMSContext |
| `@Dependent` | `InjectableJMSContext` | 依赖注入点的作用域 |

## __PM 资源支持

```java
// 系统属性控制是否在事务中使用 PM 资源
private static final boolean usePMResourceInTransaction =
    Boolean.parseBoolean(
        System.getProperty(
            "org.glassfish.jms.skip-resource-registration-in-transaction",
            "true"));

// __PM 资源不参与事务管理
// 在事务中需要使用 __PM 资源来避免 XA 双重注册问题

// 资源命名:
// 正常资源: jms/MyFactory
// PM 资源: jms/MyFactory__pm
```

## 相关模块

- `jms-core` - JMS 资源适配器核心
- `gf-jms-connector` - JMS 连接器配置
- `jms-handlers` - 注解处理器
- `weld-integration` - CDI/Weld 集成
