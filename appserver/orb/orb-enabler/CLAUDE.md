# ORB Enabler - ORB 配置和启动架构

## 概述

`orb-enabler` 模块提供 ORB 的配置基础设施和启动服务，定义了 IiopService、IiopListener、Orb 等 HK2 配置 bean，以及 ORBConnectorStartup 服务用于 ORB 初始化。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    HK2 Configuration Layer                      │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  @Configured Interfaces                                 │ │
│  │  ├─→ IiopService                                        │ │
│  │  ├─→ IiopListener                                       │ │
│  │  └─→ Orb                                                │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Configuration Sources                        │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  domain.xml                                              │ │
│  │  <iiop-service>                                          │ │
│  │    <orb />                                               │ │
│  │    <iiop-listener />                                     │ │
│  │  </iiop-service>                                         │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Startup Layer                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  ORBConnectorStartup                                    │ │
│  │  ├─→ RunLevel.VAL (启动级别)                            │ │
│  │  ├─→ setORBSystemProperties()                           │ │
│  │  ├─→ initializeLazyListener()                           │ │
│  │  └─→ GlassFishORBManager.initORB()                      │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    GlassFish CORBA ORB                          │
└─────────────────────────────────────────────────────────────────┘
```

## IiopService

### 配置接口

```java
@Configured
public interface IiopService extends ConfigBeanProxy, ConfigExtension {

    /**
     * 客户端认证要求
     * 是否要求客户端提供证书
     * 默认: false
     */
    @Attribute(defaultValue="false", dataType=Boolean.class)
    String getClientAuthenticationRequired();

    /**
     * 设置客户端认证要求
     */
    void setClientAuthenticationRequired(String value)
        throws PropertyVetoException;

    /**
     * ORB 配置
     * 包含 ORB 级别的设置
     */
    @Element(required=true)
    Orb getOrb();

    /**
     * 设置 ORB 配置
     */
    void setOrb(Orb value) throws PropertyVetoException;

    /**
     * SSL 客户端配置
     * 用于出站 IIOP 连接的 SSL 设置
     */
    @Element
    SslClientConfig getSslClientConfig();

    /**
     * 设置 SSL 客户端配置
     */
    void setSslClientConfig(SslClientConfig value)
        throws PropertyVetoException;

    /**
     * IIOP 监听器列表
     * 定义所有 IIOP 端点
     */
    @Element
    List<IiopListener> getIiopListener();
}
```

### 配置示例

```xml
<!-- domain.xml -->
<iiop-service client-authentication-required="false">
    <orb max-connections="1024" message-fragment-size="1024">
        <property name="com.sun.CORBA.ORBActivation" value="true" />
    </orb>
    <ssl-client-config>
        <ssl cert-nickname="s1as"
             ssl2-enabled="false"
             ssl3-enabled="false"
             tls-enabled="true"
             client-auth-enabled="false" />
    </ssl-client-config>
    <iiop-listener id="orb-listener"
                   address="0.0.0.0"
                   port="3700"
                   security-enabled="false"
                   enabled="true"
                   lazy-init="false" />
</iiop-service>
```

## IiopListener

### 配置接口

```java
@Configured
public interface IiopListener extends ConfigBeanProxy {

    /**
     * 监听器 ID (主键)
     * 唯一标识此监听器
     */
    @Attribute(key=true)
    String getId();

    /**
     * 监听器地址
     * 绑定的 IP 地址或主机名
     * 默认: 从 host 继承
     */
    @Attribute(defaultValue="${j2ee.server.address}")
    String getAddress();

    /**
     * 监听器端口
     * IIOP 服务端口
     * 默认: 1072 (但实际上通常使用 3700)
     */
    @Attribute(defaultValue="1072")
    String getPort();

    /**
     * 是否启用 SSL
     * 启用后使用 SSL/TLS 加密连接
     * 默认: false
     */
    @Attribute(defaultValue="false", dataType=Boolean.class)
    String getSecurityEnabled();

    /**
     * 是否启用监听器
     * 默认: true
     */
    @Attribute(defaultValue="true", dataType=Boolean.class)
    String getEnabled();

    /**
     * 延迟初始化
     * 是否延迟 ORB 启动直到第一个请求
     * 默认: false
     */
    @Attribute(defaultValue="false", dataType=Boolean.class)
    String getLazyInit();

    /**
     * SSL 配置
     * 当 security-enabled="true" 时使用
     */
    @Element
    Ssl getSsl();

    /**
     * 自定义属性
     * 可以添加额外的配置
     */
    @Element
    List<Property> getProperty();
}
```

### 监听器类型

| 类型 | 端口 | SSL | 用途 |
|------|------|-----|------|
| 普通监听器 | 3700 | 否 | 标准 IIOP 连接 |
| SSL 监听器 | 3820 | 是 | 加密 IIOP 连接 |
| 延迟监听器 | 3700 | 否 | 延迟初始化的监听器 |

### 配置示例

```xml
<!-- 普通 IIOP 监听器 -->
<iiop-listener id="orb-listener"
               address="0.0.0.0"
               port="3700"
               security-enabled="false"
               enabled="true"
               lazy-init="false" />

<!-- SSL IIOP 监听器 -->
<iiop-listener id="orb-ssl-listener"
               address="0.0.0.0"
               port="3820"
               security-enabled="true"
               enabled="true">
    <ssl cert-nickname="s1as"
         ssl2-enabled="false"
         ssl3-enabled="false"
         tls-enabled="true"
         tls1.1-enabled="true"
         tls1.2-enabled="true"
         client-auth-enabled="false" />
</iiop-listener>

<!-- 延迟初始化监听器 -->
<iiop-listener id="orb-lazy"
               address="0.0.0.0"
               port="3700"
               lazy-init="true"
               enabled="true" />
```

## Orb

### 配置接口

```java
@Configured
public interface Orb extends ConfigBeanProxy, PropertyBag {

    /**
     * 最大连接数
     * ORB 允许的最大并发连接数
     * 默认: 1024
     */
    @Attribute(defaultValue="1024")
    String getMaxConnections();

    /**
     * 设置最大连接数
     */
    void setMaxConnections(String value) throws PropertyVetoException;

    /**
     * 消息片段大小
     * GIOP 消息的最大片段大小 (字节)
     * 默认: 1024
     */
    @Attribute(defaultValue="1024")
    String getMessageFragmentSize();

    /**
     * 设置消息片段大小
     */
    void setMessageFragmentSize(String value)
        throws PropertyVetoException;

    /**
     * 自定义属性
     * 可以添加任意 ORB 属性
     */
    @Element
    List<Property> getProperty();
}
```

### 常用属性

```xml
<orb max-connections="2048" message-fragment-size="2048">
    <!-- ORB 调试 -->
    <property name="com.sun.CORBA.ORBDebug" value="giop,transport" />

    <!-- ORB 激活 -->
    <property name="com.sun.CORBA.ORBAActivation" value="true" />

    <!-- 线程池设置 -->
    <property name="com.sun.CORBA.ORBThreadPoolSize" value="200" />

    <!-- 请求超时 -->
    <property name="com.sun.CORBA.ORBRequestTimeout" value="30000" />

    <!-- 本地优化 -->
    <property name="com.sun.CORBA.ORBLocalOptimization" value="true" />
</orb>
```

## ORBConnectorStartup

### 启动服务

```java
@Service
@RunLevel(RunLevel.VAL_VALUE) // 在验证级别启动
public class ORBConnectorStartup {

    /**
     * 启动时初始化 ORB
     */
    @PostConstruct
    public void postConstruct() {
        // 1. 设置 ORB 系统属性
        setORBSystemProperties();

        // 2. 初始化延迟监听器 (如果配置了)
        initializeLazyListener();
    }

    /**
     * 设置 ORB 系统属性
     */
    private void setORBSystemProperties() {
        // 1. 设置 ORB 类
        System.setProperty(
            ORBConstants.OMG_ORB_CLASS_PROPERTY,
            "com.sun.corba.ee.impl.orb.ORBImpl");

        System.setProperty(
            ORBConstants.OMG_ORB_SINGLETON_CLASS_PROPERTY,
            "com.sun.corba.ee.impl.orb.ORBSingleton");

        // 2. 设置 ORB Socket 工厂
        System.setProperty(
            ORBConstants.SUN_ORB_SOCKET_FACTORY_CLASS_PROPERTY,
            "com.sun.enterprise.iiop.IIOPSSLSocketFactory");

        // 3. 设置 RMI-IIOP 委托类
        System.setProperty(
            "javax.rmi.CORBA.UtilClass",
            "com.sun.corba.ee.impl.javax.rmi.CORBA.Util");

        System.setProperty(
            "javax.rmi.CORBA.StubClass",
            "com.sun.corba.ee.impl.javax.rmi.CORBA.StubDelegateImpl");

        System.setProperty(
            "javax.rmi.CORBA.PortableRemoteObjectClass",
            "com.sun.corba.ee.impl.javax.rmi.PortableRemoteObject");

        // 4. 设置 ORB 服务器环境
        System.setProperty(
            GlassFishORBFactory.ENV_IS_SERVER_PROPERTY,
            "true");
    }

    /**
     * 初始化延迟监听器
     * 如果有配置 lazy-init="true" 的监听器
     */
    private void initializeLazyListener() {
        // 1. 获取 IIOP 服务配置
        IiopService iiopService = getIiopService();

        // 2. 查找延迟监听器
        IiopListener lazyListener = null;
        for (IiopListener listener : iiopService.getIiopListener()) {
            if (Boolean.parseBoolean(listener.getLazyInit()) &&
                Boolean.parseBoolean(listener.getEnabled())) {
                lazyListener = listener;
                break;
            }
        }

        // 3. 如果找到，创建 Grizzly 代理
        if (lazyListener != null) {
            // 检查约束: 延迟监听器不能是 SSL
            if (Boolean.parseBoolean(lazyListener.getSecurityEnabled())) {
                throw new IllegalStateException(
                    "Lazy init not supported for SSL listeners");
            }

            // 创建 Grizzly 网络代理
            createGrizzlyProxy(lazyListener);
        }
    }

    /**
     * 创建 Grizzly 代理
     * 在第一个请求到达时触发实际 ORB 初始化
     */
    private void createGrizzlyProxy(IiopListener listener) {
        // 1. 获取监听器配置
        String address = listener.getAddress();
        int port = Integer.parseInt(listener.getPort());

        // 2. 创建 Grizzly 代理监听器
        NetworkListener proxyListener = new NetworkListener();
        proxyListener.setName("IIOP_PROXY_" + listener.getId());
        proxyListener.setAddress(address);
        proxyListener.setPort(port);

        // 3. 设置代理过滤器
        proxyListener.setProtocol("IIOP_PROXY");

        // 4. 注册激活回调
        // 当第一个请求到达时:
        // - 初始化实际 ORB
        // - 停止代理
        // - 启动真实 ORB 监听器
        registerActivationCallback(() -> {
            // 初始化 ORB
            GlassFishORBManager orbManager = getORBManager();
            orbManager.initORB();
        });
    }
}
```

## 延迟初始化流程

```
服务器启动
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│  ORBConnectorStartup.postConstruct()                          │
│  ├─→ setORBSystemProperties()                                │
│  └─→ initializeLazyListener()                                 │
└────────────────────────────┬────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
                    ▼                 ▼
             有 lazy-init?       无 lazy-init
                    │                 │
                    ▼                 ▼
┌────────────────────────┐   ┌──────────────────────────────────┐
│  创建 Grizzly 代理     │   │  ORB 正常初始化                  │
│  (不启动 ORB)          │   │                                  │
└────────────┬───────────┘   └──────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│  第一个 IIOP 请求到达                                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Grizzly 代理拦截请求                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────┬───────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│  触发 ORB 初始化                                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  GlassFishORBManager.initORB()                          │ │
│  │  ├─→ initProperties()                                  │ │
│  │  ├─→ setFOLBProperties()                               │ │
│  │  └─→ ORB.init()                                         │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────┬───────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│  停止 Grizzly 代理，启动真实 ORB 监听器                        │
└────────────┬───────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│  后续请求正常处理                                               │
└─────────────────────────────────────────────────────────────────┘
```

## 配置验证

### 监听器约束

```java
/**
 * 验证 IIOP 监听器配置
 */
public class IiopListenerValidator {
    /**
     * 验证监听器配置
     */
    public void validate(IiopListener listener) {
        // 1. 延迟初始化约束
        if (Boolean.parseBoolean(listener.getLazyInit())) {
            // 不能是 SSL 监听器
            if (Boolean.parseBoolean(listener.getSecurityEnabled())) {
                throw new IllegalStateException(
                    "Lazy init not supported for SSL listeners");
            }

            // 只能有一个延迟监听器
            if (hasOtherLazyListeners(listener)) {
                throw new IllegalStateException(
                    "Only one lazy-init listener allowed");
            }
        }

        // 2. 端口验证
        int port = Integer.parseInt(listener.getPort());
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                "Invalid port: " + port);
        }

        // 3. 地址验证
        String address = listener.getAddress();
        if (!isValidAddress(address)) {
            throw new IllegalArgumentException(
                "Invalid address: " + address);
        }
    }

    /**
     * 检查是否有其他延迟监听器
     */
    private boolean hasOtherLazyListeners(IiopListener current) {
        IiopService service = current.getParent(IiopService.class);
        for (IiopListener listener : service.getIiopListener()) {
            if (listener != current &&
                Boolean.parseBoolean(listener.getLazyInit())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证地址
     */
    private boolean isValidAddress(String address) {
        // 支持的系统属性
        if (address.startsWith("${") && address.endsWith("}")) {
            return true;
        }

        // 支持 0.0.0.0 (所有接口)
        if ("0.0.0.0".equals(address)) {
            return true;
        }

        // 支持 localhost
        if ("localhost".equalsIgnoreCase(address)) {
            return true;
        }

        // 支持 IPv4/IPv6 地址
        try {
            InetAddress.getByName(address);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

## 配置层次结构

```
domain.xml
├── <configs>
│   └── <config name="server-config">
│       └── <iiop-service>
│           ├── client-authentication-required="false"
│           │
│           ├── <orb>
│           │   ├── max-connections="1024"
│           │   ├── message-fragment-size="1024"
│           │   └── <property>...
│           │
│           ├── <ssl-client-config>
│           │   └── <ssl>...
│           │
│           └── <iiop-listener>[]
│               ├── [0] id="orb-listener"
│               │   ├── address="0.0.0.0"
│               │   ├── port="3700"
│               │   ├── security-enabled="false"
│               │   ├── enabled="true"
│               │   └── lazy-init="false"
│               │
│               └── [1] id="orb-ssl"
│                   ├── address="0.0.0.0"
│                   ├── port="3820"
│                   ├── security-enabled="true"
│                   ├── enabled="true"
│                   └── <ssl>
│                       ├── cert-nickname="s1as"
│                       ├── ssl2-enabled="false"
│                       ├── ssl3-enabled="false"
│                       └── tls-enabled="true"
```

## 配置更新

### 动态配置

```java
/**
 * IIOP 服务配置监听器
 * 监听配置变化并动态更新 ORB
 */
@Service
public class IiopServiceConfigListener implements ConfigListener {

    @Override
    public void eventsChanged(ConfigEvents events) {
        for (ConfigEvent event : events.getEvents()) {
            if (isIiopServiceEvent(event)) {
                handleIiopServiceChange(event);
            } else if (isIiopListenerEvent(event)) {
                handleIiopListenerChange(event);
            }
        }
    }

    /**
     * 处理 IIOP 服务配置变化
     */
    private void handleIiopServiceChange(ConfigEvent event) {
        if (event.isChange((IiopService service) ->
            service.getClientAuthenticationRequired())) {
            // 更新 CSIv2 客户端认证设置
            updateClientAuthentication();
        }

        if (event.isChange((IiopService service) ->
            service.getOrb().getMaxConnections())) {
            // 更新 ORB 最大连接数
            updateMaxConnections();
        }
    }

    /**
     * 处理 IIOP 监听器配置变化
     */
    private void handleIiopListenerChange(ConfigEvent event) {
        if (event.isChange((IiopListener listener) ->
            listener.getEnabled())) {
            // 启用/禁用监听器
            toggleListener(listener);
        }

        if (event.isChange((IiopListener listener) ->
            listener.getPort())) {
            // 端口变化需要重启 ORB
            restartORB();
        }
    }
}
```

## 集群配置

### 集群中的 IIOP 配置

```xml
<clusters>
    <cluster name="mycluster">
        <config name="mycluster-config">
            <iiop-service>
                <!-- 集群实例的 IIOP 配置 -->
                <orb max-connections="2048" />
                <iiop-listener id="orb-cluster"
                               address="${J2EE_SERVER_HOST}"
                               port="3700" />
            </iiop-service>

            <!-- 集群可用性配置 -->
            <availability-service>
                <iiop-availability>
                    <availability-enabled>true</availability-enabled>
                </iiop-availability>
            </availability-service>
        </config>

        <!-- 集群实例 -->
        <instances>
            <instance name="instance1" ref="instance1-config">
                <property name="ORBNetworkAddr" value="instance1.example.com" />
            </instance>
            <instance name="instance2" ref="instance2-config">
                <property name="ORBNetworkAddr" value="instance2.example.com" />
            </instance>
        </instances>
    </cluster>
</clusters>
```

## 故障排除

### 常见问题

**1. ORB 启动失败**

```bash
# 检查端口是否被占用
netstat -an | grep 3700

# 检查 IIOP 日志
asadmin log-details --level FINE | grep -i iiop
```

**2. 延迟初始化不工作**

```xml
<!-- 确保只有一个 lazy-init 监听器 -->
<iiop-listener id="orb-lazy" lazy-init="true" />

<!-- 延迟初始化不支持 SSL -->
<!-- 错误配置: -->
<iiop-listener id="orb-lazy-ssl"
               lazy-init="true"
               security-enabled="true" /> <!-- 不支持 -->
```

**3. SSL 配置问题**

```bash
# 检查证书别名
keytool -list -keystore $GF_HOME/domains/domain1/config/keystore.jks

# 测试 SSL 连接
openssl s_client -connect localhost:3820 -showcerts
```

## 相关模块

- `orb-iiop` - IIOP 协议实现
- `orb-connector` - 核心 ORB 连接器 API
- `nucleus/admin/config-api` - 配置 API 基础设施
- `nucleus/grizzly` - Grizzly 网络框架 (用于延迟初始化代理)
- `appserver/ejb` - EJB 容器 (使用 ORB 进行远程调用)
