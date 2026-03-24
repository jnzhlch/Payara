# ORB Connector - ORB 连接器 API 架构

## 概述

`orb-connector` 模块提供 ORB 连接器的核心 API 和契约接口，定义了 ProtocolManager、RemoteReferenceFactory、GlassFishORBFactory 等关键接口，以及 CLI 管理命令。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    API 契约层 (@Contract)                       │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  GlassFishORBFactory                                     │ │
│  │  ├─→ createORB(Properties)                              │ │
│  │  ├─→ getOTSPolicyType()                                 │ │
│  │  ├─→ getCSIv2PolicyType()                               │ │
│  │  └─→ getCSIv2Props()                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  ProtocolManager                                         │ │
│  │  ├─→ initializePOAs()                                   │ │
│  │  ├─→ initializeNaming()                                 │ │
│  │  ├─→ getRemoteReferenceFactory()                        │ │
│  │  └─→ validateTargetObjectInterfaces()                   │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  RemoteReferenceFactory                                 │ │
│  │  ├─→ createRemoteReference(byte[])                      │ │
│  │  ├─→ createHomeReference(byte[])                        │ │
│  │  ├─→ destroyReference()                                 │ │
│  │  └─→ cleanupClass(Class)                                │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SPI 层                                       │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  EjbContainerFacade                                      │ │
│  │  ├─→ getTargetObject(byte[])                            │ │
│  │  ├─→ getEjbDescriptor()                                 │ │
│  │  └─→ releaseTargetObject()                              │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  EjbService                                              │ │
│  │  └─→ isEjbComponent(Class)                              │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    工具层                                       │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  GlassFishORBHelper                                      │ │
│  │  ├─→ getORB()                                           │ │
│  │  ├─→ getProtocolManager()                               │ │
│  │  └─→ setCSIv2Prop()                                     │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  IIOPSSLUtil                                             │ │
│  │  ├─→ getSSLContext()                                    │ │
│  │  ├─→ getSSLSocketFactory()                              │ │
│  │  └─→ configureSSL()                                     │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    CLI 命令层                                   │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  CreateIiopListener                                      │ │
│  │  DeleteIiopListener                                      │ │
│  │  ListIiopListeners                                       │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## GlassFishORBFactory

### 核心接口

```java
@Contract
public interface GlassFishORBFactory {
    /**
     * 服务器环境属性
     * 用于标识 ORB 是否运行在服务器环境中
     */
    public static final String ENV_IS_SERVER_PROPERTY =
        "com.sun.corba.ee.ORBEnvironmentIsGlassFishServer";

    /**
     * 创建 ORB 实例
     * @param props ORB 初始化属性
     * @return ORB 实例
     */
    public ORB createORB(Properties props);

    /**
     * 获取 OTS 策略类型
     * @return OTS 策略类型值
     */
    public int getOTSPolicyType();

    /**
     * 获取 CSIv2 策略类型
     * @return CSIv2 策略类型值
     */
    public int getCSIv2PolicyType();

    /**
     * 获取 CSIv2 属性
     * @return CSIv2 属性
     */
    public Properties getCSIv2Props();

    /**
     * 设置 CSIv2 属性
     * @param name 属性名称
     * @param value 属性值
     */
    public void setCSIv2Prop(String name, String value);

    /**
     * 获取 ORB 初始端口
     * @return ORB 初始端口
     */
    public int getORBInitialPort();

    /**
     * 获取 ORB 主机地址
     * @param orb ORB 实例
     * @return 主机地址
     */
    public String getORBHost(ORB orb);

    /**
     * 获取 ORB 端口
     * @param orb ORB 实例
     * @return 端口号
     */
    public int getORBPort(ORB orb);

    /**
     * 判断是否为 EJB 调用
     * @param sri 服务器请求信息
     * @return 是否为 EJB 调用
     */
    public boolean isEjbCall(ServerRequestInfo sri);

    /**
     * 获取 IIOP 端点
     * @return 端点字符串 (host:port,host:port,...)
     */
    public String getIIOPEndpoints();

    /**
     * 判断集群是否激活
     * @return 集群是否激活
     */
    boolean isClusterActive();
}
```

## ProtocolManager

### 核心接口

```java
@Contract
public interface ProtocolManager {
    /**
     * 初始化 ORB
     * @param o ORB 实例
     */
    void initialize(ORB o);

    /**
     * 初始化 POA (Portable Object Adapter)
     * 创建并激活必要的 POA 层次结构
     */
    public void initializePOAs() throws Exception;

    /**
     * 初始化命名服务
     */
    public void initializeNaming() throws Exception;

    /**
     * 初始化远程命名
     * @param remoteNamingProvider 远程命名提供者
     */
    public void initializeRemoteNaming(Remote remoteNamingProvider)
        throws Exception;

    /**
     * 获取远程引用工厂
     * @param container EJB 容器门面
     * @param remoteHomeView 是否为远程 Home 视图
     * @param id EJB 标识符
     * @return 远程引用工厂
     */
    RemoteReferenceFactory getRemoteReferenceFactory(
        EjbContainerFacade container,
        boolean remoteHomeView,
        String id);

    /**
     * 判断两个远程对象是否相同
     * @param obj1 远程对象 1
     * @param obj2 远程对象 2
     * @return 是否相同
     */
    boolean isIdentical(Remote obj1, Remote obj2);

    /**
     * 验证目标对象接口
     * 检查 RMI-IIOP 接口是否符合规范
     * @param targetObj 目标对象
     */
    void validateTargetObjectInterfaces(Remote targetObj);

    /**
     * 映射异常
     * 将 RMI 异常映射为协议特定的 (如 CORBA) 异常
     * @param exception 原始异常
     * @return 映射后的异常
     */
    Throwable mapException(Throwable exception);

    /**
     * 判断对象是否为 CORBA stub
     * @param obj 对象
     * @return 是否为 CORBA stub
     */
    boolean isStub(Object obj);

    /**
     * 判断对象是否为本地对象
     * @param obj 对象
     * @return 是否为本地对象
     */
    boolean isLocal(Object obj);

    /**
     * 获取对象 ID
     * @param obj CORBA 对象
     * @return 对象 ID
     */
    byte[] getObjectID(org.omg.CORBA.Object obj);

    /**
     * 连接对象到协议
     * @param remoteObj 远程对象
     */
    void connectObject(Remote remoteObj) throws RemoteException;

    /**
     * 获取 EJB 描述符
     * @param ejbKey EJB 密钥
     * @return EJB 描述符
     */
    EjbDescriptor getEjbDescriptor(byte[] ejbKey);
}
```

## RemoteReferenceFactory

### 核心接口

```java
public interface RemoteReferenceFactory {
    /**
     * 创建 EJBObject 远程引用
     * 用于执行远程方法调用
     *
     * @param instanceKey EJB 实例唯一标识符
     * @return 协议特定的 stub (已窄化为正确类型)
     */
    Remote createRemoteReference(byte[] instanceKey);

    /**
     * 创建 EJBHome 远程引用
     * 用于执行 Home 接口方法调用
     *
     * @param homeKey EJB Home 唯一标识符
     * @return 协议特定的 stub (已窄化为正确类型)
     */
    Remote createHomeReference(byte[] homeKey);

    /**
     * 销毁远程引用
     * 使 EJBObject/EJBHome 无法再用于远程调用
     * 销毁协议管理器维护的任何状态 (如 tie 对象)
     *
     * @param remoteRef 远程引用
     * @param remoteObj 对应的 servant
     */
    void destroyReference(Remote remoteRef, Remote remoteObj);

    /**
     * 销毁工厂本身
     * 在关闭/取消部署时调用
     * 工厂应释放所有资源
     */
    public void destroy();

    /**
     * 判断是否具有相同的容器 ID
     * @param ref CORBA 对象引用
     * @return 是否相同
     */
    public boolean hasSameContainerID(org.omg.CORBA.Object ref)
        throws Exception;

    /**
     * 设置 Repository IDs
     * @param homeIntf Home 接口类
     * @param remoteIntf Remote 接口类
     */
    public void setRepositoryIds(Class homeIntf, Class remoteIntf);

    /**
     * 清理类
     * @param clazz 要清理的类
     */
    public void cleanupClass(Class clazz);

    /**
     * 获取 CSIv2 策略类型
     * @return CSIv2 策略类型
     */
    public int getCSIv2PolicyType();
}
```

## IIOPConstants

### 常量定义

```java
public interface IIOPConstants {
    /**
     * 按值传递 ID
     * 参数通过序列化传递
     */
    public static final int PASS_BY_VALUE_ID = 0;

    /**
     * 按引用传递 ID
     * 参数通过引用传递 (仅限本地调用)
     */
    public static final int PASS_BY_REFERENCE_ID = 1;
}
```

## GlassFishORBHelper

### 工具类

```java
public class GlassFishORBHelper {
    private static GlassFishORBFactory orbFactory;
    private static ProtocolManager protocolManager;

    /**
     * 获取 ORB 工厂
     */
    public static GlassFishORBFactory getORBFactory() {
        return orbFactory;
    }

    /**
     * 设置 ORB 工厂
     */
    public static void setORBFactory(GlassFishORBFactory factory) {
        orbFactory = factory;
    }

    /**
     * 获取协议管理器
     */
    public static ProtocolManager getProtocolManager() {
        return protocolManager;
    }

    /**
     * 设置协议管理器
     */
    public static void setProtocolManager(ProtocolManager pm) {
        protocolManager = pm;
    }

    /**
     * 获取 ORB 实例
     */
    public static ORB getORB() {
        if (orbFactory != null) {
            // 通过 GlassFishORBFactory 获取 ORB
            // 实际实现在 orb-iiop 模块
            return orbFactory.getORB();
        }
        return null;
    }

    /**
     * 获取 CSIv2 属性
     */
    public static Properties getCSIv2Props() {
        if (orbFactory != null) {
            return orbFactory.getCSIv2Props();
        }
        return new Properties();
    }

    /**
     * 设置 CSIv2 属性
     */
    public static void setCSIv2Prop(String name, String value) {
        if (orbFactory != null) {
            orbFactory.setCSIv2Prop(name, value);
        }
    }

    /**
     * 获取 IIOP 端点
     */
    public static String getIIOPEndpoints() {
        if (orbFactory != null) {
            return orbFactory.getIIOPEndpoints();
        }
        return null;
    }

    /**
     * 判断集群是否激活
     */
    public static boolean isClusterActive() {
        if (orbFactory != null) {
            return orbFactory.isClusterActive();
        }
        return false;
    }
}
```

## EjbContainerFacade (SPI)

### 核心接口

```java
/**
 * EJB 容器门面 - SPI 接口
 * 允许 ORB 层与 EJB 容器交互
 */
@Contract
public interface EjbContainerFacade {
    /**
     * 获取目标对象
     * 根据实例密钥获取 EJB 实例
     *
     * @param instanceKey 实例密钥
     * @return EJB 实例
     */
    Object getTargetObject(byte[] instanceKey);

    /**
     * 释放目标对象
     * 完成调用后释放 EJB 实例
     *
     * @param instanceKey 实例密钥
     */
    void releaseTargetObject(byte[] instanceKey);

    /**
     * 获取 EJB 描述符
     * @return EJB 部署描述符
     */
    EjbDescriptor getEjbDescriptor();

    /**
     * 获取 EJB ID
     * @return EJB 唯一标识符
     */
    String getEjbId();

    /**
     * 获取 Home 接口类
     * @return Home 接口类
     */
    Class<?> getHomeInterface();

    /**
     * 获取 Remote 接口类
     * @return Remote 接口类
     */
    Class<?> getRemoteInterface();

    /**
     * 判断是否为会话 Bean
     * @return 是否为会话 Bean
     */
    boolean isSession();

    /**
     * 判断是否为实体 Bean
     * @return 是否为实体 Bean
     */
    boolean isEntity();

    /**
     * 判断是否为消息驱动 Bean
     * @return 是否为消息驱动 Bean
     */
    boolean isMessageDriven();
}
```

## EjbService (SPI)

### 核心接口

```java
/**
 * EJB 服务 - SPI 接口
 * 提供 EJB 相关的查询和验证服务
 */
@Contract
public interface EjbService {
    /**
     * 判断类是否为 EJB 组件
     * @param clazz 要检查的类
     * @return 是否为 EJB 组件
     */
    boolean isEjbComponent(Class<?> clazz);

    /**
     * 获取 EJB 描述符
     * @param className EJB 类名
     * @return EJB 描述符
     */
    EjbDescriptor getEjbDescriptor(String className);

    /**
     * 根据 JNDI 名称获取 EJB 描述符
     * @param jndiName JNDI 名称
     * @return EJB 描述符
     */
    EjbDescriptor getEjbDescriptorByJndiName(String jndiName);

    /**
     * 获取所有 EJB 描述符
     * @return EJB 描述符集合
     */
    Collection<EjbDescriptor> getAllEjbDescriptors();
}
```

## IIOPInterceptorFactory

### 拦截器工厂

```java
/**
 * IIOP 拦截器工厂
 * 创建 CORBA 便携式拦截器
 */
@Contract
public interface IIOPInterceptorFactory {
    /**
     * 创建 IOR 拦截器
     * 用于修改 IOR (Interoperable Object Reference)
     */
    IORInterceptor createIORInterceptor();

    /**
     * 创建请求拦截器
     * 用于拦截客户端/服务端请求
     */
    ClientRequestInterceptor createClientInterceptor();

    ServerRequestInterceptor createServerInterceptor();

    /**
     * 创建 CSIv2 拦截器
     * 用于安全层处理
     */
    ClientRequestInterceptor createCSIClientInterceptor();

    ServerRequestInterceptor createCSIServerInterceptor();

    /**
     * 创建 OTS 拦截器
     * 用于事务处理
     */
    ServerRequestInterceptor createOTSServerInterceptor();
}
```

## IIOPSSLUtil

### SSL 工具类

```java
/**
 * IIOP SSL 工具类
 * 提供 SSL/TLS 相关功能
 */
public class IIOPSSLUtil {
    /**
     * 获取 SSL 上下文
     * @return SSL 上下文
     */
    public static SSLContext getSSLContext() {
        // 1. 获取 SSL 配置
        Ssl sslConfig = getSslConfig();

        // 2. 获取密钥管理器
        KeyManager[] keyManagers = getKeyManagers(sslConfig);

        // 3. 获取信任管理器
        TrustManager[] trustManagers = getTrustManagers(sslConfig);

        // 4. 创建 SSL 上下文
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);

        return sslContext;
    }

    /**
     * 获取 SSL Socket 工厂
     * @return SSL Socket 工厂
     */
    public static SSLSocketFactory getSSLSocketFactory() {
        SSLContext sslContext = getSSLContext();
        return sslContext.getSocketFactory();
    }

    /**
     * 配置 SSL 参数
     * @param socket SSL Socket
     * @param sslConfig SSL 配置
     */
    public static void configureSSL(SSLSocket socket, Ssl sslConfig) {
        // 1. 设置启用的协议
        List<String> protocols = new ArrayList<>();
        if (Boolean.parseBoolean(sslConfig.getTlsEnabled())) {
            protocols.add("TLSv1.2");
            protocols.add("TLSv1.3");
        }
        if (Boolean.parseBoolean(sslConfig.getSsl3Enabled())) {
            protocols.add("SSLv3");
        }
        socket.setEnabledProtocols(protocols.toArray(new String[0]));

        // 2. 设置密码套件
        String[] cipherSuites = getCipherSuites(sslConfig);
        socket.setEnabledCipherSuites(cipherSuites);

        // 3. 设置客户端认证
        if (Boolean.parseBoolean(sslConfig.getClientAuthEnabled())) {
            socket.setNeedClientAuth(true);
        }
    }

    /**
     * 获取密钥管理器
     */
    private static KeyManager[] getKeyManagers(Ssl sslConfig) {
        // 1. 获取密钥库
        String keyStoreFile = sslConfig.getKeyStore();
        String keyStorePass = sslConfig.getKeyStorePassword();

        // 2. 加载密钥库
        KeyStore keyStore = loadKeyStore(keyStoreFile, keyStorePass);

        // 3. 创建密钥管理器工厂
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePass.toCharArray());

        return kmf.getKeyManagers();
    }

    /**
     * 获取信任管理器
     */
    private static TrustManager[] getTrustManagers(Ssl sslConfig) {
        // 类似 getKeyManagers，但使用信任库
        String trustStoreFile = sslConfig.getTrustStore();
        String trustStorePass = sslConfig.getTrustStorePassword();

        KeyStore trustStore = loadKeyStore(trustStoreFile, trustStorePass);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        return tmf.getTrustManagers();
    }
}
```

## ORBNamingProxy

### 命名代理

```java
/**
 * ORB 命名代理
 * 提供 JNDI/COS 命名服务的代理
 */
public class ORBNamingProxy implements Remote {
    private NamingContext namingContext;

    /**
     * 初始化命名代理
     */
    public void initialize(ORB orb) throws Exception {
        // 1. 获取命名服务引用
        org.omg.CORBA.Object obj =
            orb.resolve_initial_references("NameService");

        // 2. 窄化为 NamingContext
        namingContext = NamingContextHelper.narrow(obj);
    }

    /**
     * 绑定对象到命名
     */
    public void bind(NameComponent[] name, org.omg.CORBA.Object obj) {
        namingContext.bind(name, obj);
    }

    /**
     * 重新绑定对象到命名
     */
    public void rebind(NameComponent[] name, org.omg.CORBA.Object obj) {
        namingContext.rebind(name, obj);
    }

    /**
     * 从命名解析对象
     */
    public org.omg.CORBA.Object resolve(NameComponent[] name) {
        return namingContext.resolve(name);
    }

    /**
     * 从命名解除绑定
     */
    public void unbind(NameComponent[] name) {
        namingContext.unbind(name);
    }
}
```

## CLI 命令

### CreateIiopListener

```java
@Service(name="create-iiop-listener")
@PerLookup
@I18n("create.iiop.listener")
@ExecuteOn({RuntimeType.DAS})
@RestEndpoints({
    @RestEndpoint(configBean=IiopListener.class,
                 opType=RestEndpoint.OpType.POST,
                 path="create-iiop-listener",
                 description="create-iiop-listener")
})
public class CreateIiopListener implements AdminCommand {

    @Param(name="listeneraddress", optional=true)
    String address;

    @Param(name="iiopport", optional=true)
    String port;

    @Param(name="securityenabled", optional=true, defaultValue="false")
    String securityEnabled;

    @Param(name="enabled", optional=true, defaultValue="true")
    String enabled;

    @Param(name="lazyInit", optional=true, defaultValue="false")
    String lazyInit;

    @Param(name="listenerId", primary=true)
    String listenerId;

    @Param(optional=true)
    String target = "server-config";

    @Override
    public void execute(AdminCommandContext context) {
        // 1. 获取配置
        Config config = getConfig(target);
        IiopService iiopService = config.getExtensionByType(IiopService.class);

        // 2. 创建 IiopListener
        IiopListener listener = iiopService.createChild(IiopListener.class);
        listener.setId(listenerId);
        listener.setAddress(address != null ? address : "0.0.0.0");
        listener.setPort(port != null ? port : "3700");
        listener.setSecurityEnabled(securityEnabled);
        listener.setEnabled(enabled);
        listener.setLazyInit(lazyInit);

        // 3. 添加到配置
        iiopService.getIiopListener().add(listener);

        // 4. 如果需要 SSL，创建 SSL 配置
        if (Boolean.parseBoolean(securityEnabled)) {
            Ssl ssl = listener.createChild(Ssl.class);
            // 设置默认 SSL 参数
            ssl.setCertNickname("s1as");
            ssl.setSsl2Enabled("false");
            ssl.setSsl3Enabled("false");
            ssl.setTlsEnabled("true");
            listener.setSsl(ssl);
        }
    }
}
```

### DeleteIiopListener

```java
@Service(name="delete-iiop-listener")
@PerLookup
@I18n("delete.iiop.listener")
@ExecuteOn({RuntimeType.DAS})
public class DeleteIiopListener implements AdminCommand {

    @Param(name="listenerId", primary=true)
    String listenerId;

    @Param(optional=true)
    String target = "server-config";

    @Override
    public void execute(AdminCommandContext context) {
        // 1. 获取配置
        Config config = getConfig(target);
        IiopService iiopService = config.getExtensionByType(IiopService.class);

        // 2. 查找并删除监听器
        List<IiopListener> listeners = iiopService.getIiopListener();
        IiopListener toRemove = null;
        for (IiopListener listener : listeners) {
            if (listener.getId().equals(listenerId)) {
                toRemove = listener;
                break;
            }
        }

        if (toRemove != null) {
            listeners.remove(toRemove);
        }
    }
}
```

### ListIiopListeners

```java
@Service(name="list-iiop-listeners")
@PerLookup
@I18n("list.iiop.listeners")
@ExecuteOn({RuntimeType.DAS})
public class ListIiopListeners implements AdminCommand {

    @Param(optional=true)
    String target = "server-config";

    @Override
    public void execute(AdminCommandContext context) {
        ActionReport report = context.getActionReport();

        // 1. 获取配置
        Config config = getConfig(target);
        IiopService iiopService = config.getExtensionByType(IiopService.class);

        // 2. 获取所有监听器
        List<IiopListener> listeners = iiopService.getIiopListener();

        // 3. 输出监听器信息
        List<Map<String, String>> listenerInfo = new ArrayList<>();
        for (IiopListener listener : listeners) {
            Map<String, String> info = new HashMap<>();
            info.put("id", listener.getId());
            info.put("address", listener.getAddress());
            info.put("port", listener.getPort());
            info.put("securityEnabled", listener.getSecurityEnabled());
            info.put("enabled", listener.getEnabled());
            info.put("lazyInit", listener.getLazyInit());
            listenerInfo.add(info);
        }

        report.setMessage(listenerInfo.toString());
        report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
    }
}
```

## 监控工具

### ORBCommonStatsImpl

```java
/**
 * ORB 通用统计实现
 */
@Service
@Contract
public class ORBCommonStatsImpl implements ORBCommonStatsProvider {
    private AtomicInteger numRequests = new AtomicInteger(0);
    private AtomicInteger numErrors = new AtomicInteger(0);
    private AtomicLong totalResponseTime = new AtomicLong(0);

    /**
     * 记录请求
     */
    public void recordRequest(long responseTime) {
        numRequests.incrementAndGet();
        totalResponseTime.addAndGet(responseTime);
    }

    /**
     * 记录错误
     */
    public void recordError() {
        numErrors.incrementAndGet();
    }

    /**
     * 获取请求数
     */
    @Override
    public int getNumRequests() {
        return numRequests.get();
    }

    /**
     * 获取错误数
     */
    @Override
    public int getNumErrors() {
        return numErrors.get();
    }

    /**
     * 获取总响应时间
     */
    @Override
    public long getTotalResponseTime() {
        return totalResponseTime.get();
    }

    /**
     * 获取平均响应时间
     */
    public long getAverageResponseTime() {
        int requests = numRequests.get();
        if (requests == 0) {
            return 0;
        }
        return totalResponseTime.get() / requests;
    }
}
```

### S1ASThreadPoolManager

```java
/**
 * S1AS 线程池管理器
 * 管理 ORB 线程池
 */
@Service
public class S1ASThreadPoolManager {
    private ThreadPoolManager threadPoolManager;

    /**
     * 获取线程池管理器
     */
    public ThreadPoolManager getThreadPoolManager() {
        if (threadPoolManager == null) {
            threadPoolManager = createThreadPoolManager();
        }
        return threadPoolManager;
    }

    /**
     * 获取线程池数值 ID
     * @param threadPoolName 线程池名称
     * @return 线程池数值 ID
     */
    public int getThreadPoolNumericId(String threadPoolName) {
        ThreadPoolManager tpm = getThreadPoolManager();
        ThreadPool threadPool = tpm.getThreadPool(threadPoolName);
        if (threadPool != null) {
            return threadPool.getId();
        }
        return -1;
    }

    /**
     * 创建线程池管理器
     */
    private ThreadPoolManager createThreadPoolManager() {
        // 创建 GlassFish 线程池管理器
        ThreadPoolManager tpm = new ThreadPoolManager();

        // 配置默认线程池
        ThreadPool defaultPool = tpm.createThreadPool(
            "orb-default-thread-pool",
            5,      // 最小线程数
            200,    // 最大线程数
            0,      // 队列大小 (无界)
            60      // 空闲线程超时 (秒)
        );

        return tpm;
    }
}
```

## 监控常量

### MonitoringConstants

```java
/**
 * 监控常量定义
 */
public interface MonitoringConstants {
    /**
     * ORB 监控组件名称
     */
    String ORB_MONITORING_COMPONENT = "orb";

    /**
     * IIOP 监控组件名称
     */
    String IIOP_MONITORING_COMPONENT = "iiop";

    /**
     * 线程池监控组件名称
     */
    String THREADPOOL_MONITORING_COMPONENT = "threadpool";

    /**
     * 统计属性名称
     */
    String NUM_REQUESTS = "numRequests";
    String NUM_ERRORS = "numErrors";
    String TOTAL_RESPONSE_TIME = "totalResponseTime";
    String AVERAGE_RESPONSE_TIME = "averageResponseTime";

    /**
     * 线程池统计属性名称
     */
    String THREADPOOL_SIZE = "threadPoolSize";
    String THREADPOOL_ACTIVE_COUNT = "activeCount";
    String THREADPOOL_TASK_COUNT = "taskCount";
}
```

## 工具类

### IIOPUtils

```java
/**
 * IIOP 工具类
 */
public class IIOPUtils {
    /**
     * 判断是否运行在服务器环境
     */
    public static boolean isServer() {
        return Boolean.getBoolean(
            GlassFishORBFactory.ENV_IS_SERVER_PROPERTY);
    }

    /**
     * 判断是否为 CORBA 对象
     */
    public static boolean isCorbaObject(Object obj) {
        if (obj == null) {
            return false;
        }
        return org.omg.CORBA.Object.class.isInstance(obj);
    }

    /**
     * 判断是否为本地对象
     */
    public static boolean isLocalObject(ORB orb, Object obj) {
        if (!isCorbaObject(obj)) {
            return false;
        }

        try {
            org.omg.CORBA.Object corbaObj = (org.omg.CORBA.Object) obj;
            // 检查是否为本地对象
            return orb.isLocal(corbaObj);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 IOR 中提取对象 ID
     */
    public static byte[] extractObjectId(ORB orb,
                                         org.omg.CORBA.Object obj) {
        try {
            // 获取 IOR
            String iorString = orb.object_to_string(obj);

            // 解析 IOR
            IOR ior = IOR.parse(iorString);

            // 提取 Object Key
            return ior.getObjectKey();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查类是否为有效的 RMI-IIOP 远程接口
     */
    public static void isValidRemoteInterface(Class<?> clazz) {
        // 1. 必须是接口
        if (!clazz.isInterface()) {
            throw new IllegalArgumentException(
                "Class must be an interface: " + clazz.getName());
        }

        // 2. 必须继承 java.rmi.Remote
        if (!java.rmi.Remote.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(
                "Interface must extend java.rmi.Remote: " +
                clazz.getName());
        }

        // 3. 所有方法必须抛出 java.rmi.RemoteException
        for (Method method : clazz.getMethods()) {
            boolean throwsRemoteException = false;
            for (Class<?> exception : method.getExceptionTypes()) {
                if (java.rmi.RemoteException.class.isAssignableFrom(exception)) {
                    throwsRemoteException = true;
                    break;
                }
            }
            if (!throwsRemoteException) {
                throw new IllegalArgumentException(
                    "Method must throw java.rmi.RemoteException: " +
                    method.getName());
            }
        }
    }
}
```

## 相关模块

- `orb-iiop` - IIOP 协议实现
- `orb-enabler` - 配置和启动
- `appserver/ejb` - EJB 容器
- `glassfish-corba-orb` - GlassFish CORBA ORB
