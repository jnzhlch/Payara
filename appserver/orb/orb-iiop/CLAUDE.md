# ORB IIOP - IIOP 协议实现架构

## 概述

`orb-iiop` 模块提供 IIOP (Internet Inter-ORB Protocol) 协议的核心实现，包括 POA (Portable Object Adapter) 管理、远程引用工厂创建、CSIv2 安全策略和 OTS (Object Transaction Service) 集成。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    GlassFishORBManager                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  ORB 初始化与管理                                         │ │
│  │  ├─→ initProperties()                                   │ │
│  │  ├─→ initORB()                                          │ │
│  │  ├─→ getORB()                                           │ │
│  │  └─→ setFOLBProperties()                                │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    POAProtocolMgr                               │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  POA 管理                                                 │ │
│  │  ├─→ initializePOAs()                                   │ │
│  │  ├─→ initializeNaming()                                 │ │
│  │  └─→ getRemoteReferenceFactory()                        │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                 POARemoteReferenceFactory                       │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  远程引用工厂                                             │ │
│  │  ├─→ createRemoteReference(byte[])                      │ │
│  │  ├─→ createHomeReference(byte[])                        │ │
│  │  ├─→ destroyReference()                                 │ │
│  │  └─→ ServantLocator (preinvoke/postinvoke)              │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│  CSIv2Policy  │   │ OTSPolicyImpl │   │ FOLB Support  │
│  (Security)   │   │ (Transaction) │   │ (Clustering)  │
└───────────────┘   └───────────────┘   └───────────────┘
```

## GlassFishORBManager

### 核心职责

```java
@Service
public class GlassFishORBManager implements GlassFishORBLifeCycleListener {
    private ORB orb;
    private Properties initProperties;
    private CSIv2Policy csiv2Policy;
    private GroupInfoService giService;
    private IiopFolbGmsClient folbGmsClient;

    /**
     * 初始化 ORB 属性
     * 读取 IiopService 和 IiopListener 配置
     */
    private void initProperties() {
        // 1. 读取 IIOP 服务配置
        IiopService iiopService = getIiopService();
        Orb orbConfig = iiopService.getOrb();

        // 2. 设置基本属性
        initProperties = new Properties();
        initProperties.put(ORBConstants.ORB_ID_PROPERTY,
            DEFAULT_SERVER_ID);

        // 3. 设置最大连接数
        String maxConnections = orbConfig.getMaxConnections();
        if (maxConnections != null) {
            initProperties.put(ORBConstants.HIGH_WATER_MARK_PROPERTY,
                maxConnections);
        }

        // 4. 设置消息片段大小
        String fragmentSize = orbConfig.getMessageFragmentSize();
        if (fragmentSize != null) {
            initProperties.put(
                ORBConstants.GIOP_FRAGMENT_SIZE_PROPERTY,
                fragmentSize);
        }

        // 5. 初始化 CSIv2 属性
        initCSIv2Props(iiopService);

        // 6. 处理自定义属性
        for (Property prop : orbConfig.getProperty()) {
            initProperties.put(prop.getName(), prop.getValue());
        }
    }

    /**
     * 初始化 ORB 实例
     */
    public void initORB(Properties props) {
        // 1. 设置 FOLB (故障转移/负载均衡) 属性
        setFOLBProperties(props);

        // 2. 创建 ORB
        orb = ORB.init(new String[0], props);

        // 3. 启动 glassfish-corba-orb OSGi bundle
        startCorbaBundle();

        // 4. 注册 GroupInfoService
        registerGroupInfoService();

        // 5. 创建 ReferenceFactoryManager
        createReferenceFactoryManager(orb);
    }

    /**
     * 设置 FOLB (故障转移/负载均衡) 属性
     */
    private void setFOLBProperties(Properties props) {
        // RFM (Reference Factory Manager) 属性
        props.put(ORBConstants.RFM_PROPERTY, "dummy");

        // 客户端 Group Manager
        props.setProperty(
            ORBConstants.USER_CONFIGURATOR_PREFIX +
            "com.sun.corba.ee.impl.folb.ClientGroupManager",
            "dummy");

        // 服务端 Group Manager
        props.setProperty(
            ORBConstants.USER_CONFIGURATOR_PREFIX +
            "com.sun.corba.ee.impl.folb.ServerGroupManager",
            "dummy");
    }
}
```

### ORB 常量

```java
public class GlassFishORBManager {
    // 策略类型
    public static final int OTS_POLICY_TYPE = SUNVMCID.value + 123;
    public static final int CSIv2_POLICY_TYPE = SUNVMCID.value + 124;
    public static final int REQUEST_DISPATCH_POLICY_TYPE = SUNVMCID.value + 125;
    public static final int SFSB_VERSION_POLICY_TYPE = SUNVMCID.value + 126;

    // 默认服务器 ID
    public static final String DEFAULT_SERVER_ID = "100";
    public static final String ACC_DEFAULT_SERVER_ID = "101";

    // 端口配置
    public static final String DEFAULT_ORB_INIT_PORT = "3700";
    public static final String DEFAULT_ORB_INIT_HOST = "localhost";

    // CSIv2 属性
    public static final String ORB_SSL_SERVER_REQUIRED =
        "com.sun.CSIV2.ssl.server.required";
    public static final String ORB_SSL_CLIENT_REQUIRED =
        "com.sun.CSIV2.ssl.client.required";
    public static final String ORB_CLIENT_AUTH_REQUIRED =
        "com.sun.CSIV2.client.authentication.required";
}
```

## POAProtocolMgr

### 核心职责

```java
@Service
public class POAProtocolMgr implements ProtocolManager {
    private ORB orb;
    private POA rootPOA;
    private POA ejbPOA;
    private ReferenceFactoryManager rfm;

    /**
     * 初始化 POA 层次结构
     */
    public void initializePOAs() throws Exception {
        // 1. 获取 RootPOA
        rootPOA = POAHelper.narrow(
            orb.resolve_initial_references("RootPOA"));
        rootPOA.the_POAManager().activate();

        // 2. 创建 EJB 专用 POA
        createEJBPOA();

        // 3. 创建命名服务 POA
        createNamingPOA();
    }

    /**
     * 创建 EJB 专用 POA
     */
    private void createEJBPOA() throws Exception {
        // 创建策略
        org.omg.CORBA.Policy[] policies = new org.omg.CORBA.Policy[5];

        // 线程策略: 单线程模型 (每个请求一个线程)
        policies[0] = rootPOA.create_thread_policy(
            ThreadPolicyValue.ORB_CTRL_MODEL);

        // 生命周期策略: 持久化对象
        policies[1] = rootPOA.create_lifespan_policy(
            LifespanPolicyValue.PERSISTENT);

        // ID 分配策略: 用户分配 ID
        policies[2] = rootPOA.create_id_assignment_policy(
            IdAssignmentPolicyValue.USER_ID);

        // Servant 策略: ServantLocator (按需激活)
        policies[3] = rootPOA.create_servant_retention_policy(
            ServantRetentionPolicyValue.NON_RETAIN);

        // 请求处理策略: 使用 ServantLocator
        policies[4] = rootPOA.create_request_processing_policy(
            RequestProcessingPolicyValue.USE_SERVANT_MANAGER);

        // 创建 POA
        ejbPOA = rootPOA.create_POA("EJBPOA", null, policies);
        ejbPOA.the_POAManager().activate();
    }

    /**
     * 获取远程引用工厂
     */
    public RemoteReferenceFactory getRemoteReferenceFactory(
            EjbContainerFacade container,
            boolean remoteHomeView,
            String id) {

        // 创建 POARemoteReferenceFactory
        POARemoteReferenceFactory factory =
            new POARemoteReferenceFactory(orb, ejbPOA, container);

        // 初始化工厂
        factory.initialize(id, remoteHomeView);

        return factory;
    }
}
```

## POARemoteReferenceFactory

### 核心职责

```java
public class POARemoteReferenceFactory
    implements RemoteReferenceFactory, ServantLocator {

    private ORB orb;
    private POA poa;
    private EjbContainerFacade container;
    private String ejbId;
    private byte[] ejbIdBytes;

    /**
     * EJB 密钥格式:
     * ┌──────────┬──────────────────┬────────────────────────┐
     * │ EJB ID   │ InstanceKey Len  │ InstanceKey             │
     * │ (8 bytes)│ (4 bytes)        │ (variable)              │
     * └──────────┴──────────────────┴────────────────────────┘
     */

    /**
     * 创建 EJBObject 远程引用
     */
    public Remote createRemoteReference(byte[] instanceKey) {
        // 1. 构造 EJB 密钥
        byte[] ejbKey = createEJBKey(ejbIdBytes, instanceKey);

        // 2. 创建 Object ID
        byte[] objectId = ejbKey;

        // 3. 创建 CORBA 对象引用
        org.omg.CORBA.Object obj = poa.create_reference_with_id(
            objectId, getRepositoryId());

        // 4. 窄化为特定类型
        return narrowRemote(obj);
    }

    /**
     * 创建 EJBHome 远程引用
     */
    public Remote createHomeReference(byte[] homeKey) {
        // 类似 createRemoteReference，但用于 Home 接口
        byte[] ejbKey = createEJBKey(ejbIdBytes, homeKey);
        org.omg.CORBA.Object obj = poa.create_reference_with_id(
            ejbKey, getHomeRepositoryId());
        return narrowHome(obj);
    }

    /**
     * 构造 EJB 密钥
     */
    private byte[] createEJBKey(byte[] ejbId, byte[] instanceKey) {
        byte[] result = new byte[12 + instanceKey.length];

        // 前 8 字节: EJB ID
        System.arraycopy(ejbId, 0, result, 0, 8);

        // 接下来 4 字节: InstanceKey 长度
        int len = instanceKey.length;
        result[8] = (byte) ((len >>> 24) & 0xFF);
        result[9] = (byte) ((len >>> 16) & 0xFF);
        result[10] = (byte) ((len >>> 8) & 0xFF);
        result[11] = (byte) (len & 0xFF);

        // 剩余字节: InstanceKey
        System.arraycopy(instanceKey, 0, result, 12, len);

        return result;
    }

    /**
     * ServantLocator.preinvoke 实现
     * 在每次请求调用前被 POA 调用
     */
    public Servant preinvoke(byte[] objectId, POA adapter,
                             String operation, CookieHolder cookie) {
        try {
            // 1. 从 EJB 密钥中提取 InstanceKey
            byte[] instanceKey = extractInstanceKey(objectId);

            // 2. 获取目标对象
            Object targetObj = container.getTargetObject(instanceKey);

            // 3. 获取 Tie (Servant)
            Tie tie = getTie(targetObj);

            // 4. 设置目标对象
            tie.setTarget(targetObj);

            return (Servant) tie;
        } catch (Exception e) {
            throw new FORWARD(e);
        }
    }

    /**
     * ServantLocator.postinvoke 实现
     * 在每次请求调用后被 POA 调用
     */
    public void postinvoke(byte[] objectId, POA adapter,
                          String operation, Object theCookie,
                          Servant theServant) {
        // 释放资源
        if (theServant instanceof Tie) {
            ((Tie) theServant).setTarget(null);
        }
    }
}
```

### Repository ID 管理

```java
public class POARemoteReferenceFactory {
    private String[] remoteRepositoryIds;
    private String[] homeRepositoryIds;

    /**
     * 设置 Repository IDs
     * 这些 ID 是动态生成的，基于接口类型
     */
    public void setRepositoryIds(Class homeIntf, Class remoteIntf) {
        // 生成 Home 接口 Repository IDs
        homeRepositoryIds = RMIUtil.getRepositoryIds(homeIntf);

        // 生成 Remote 接口 Repository IDs
        remoteRepositoryIds = RMIUtil.getRepositoryIds(remoteIntf);
    }

    /**
     * 获取 Repository ID
     */
    private String getRepositoryId() {
        return remoteRepositoryIds[0];
    }

    private String getHomeRepositoryId() {
        return homeRepositoryIds[0];
    }
}
```

## CSIv2Policy

### 核心职责

```java
/**
 * CSIv2 (Common Secure Inter-ORB Protocol) 策略
 * 用于 CORBA 安全互操作性
 */
public class CSIv2Policy extends org.omg.CORBA.LocalObject
                         implements org.omg.CORBA.Policy {

    private EjbDescriptor ejbDescriptor;

    public CSIv2Policy(EjbDescriptor ejbDescriptor) {
        this.ejbDescriptor = ejbDescriptor;
    }

    /**
     * 策略类型
     */
    public int policy_type() {
        return CSIv2_POLICY_TYPE;
    }

    /**
     * 获取 EJB 描述符
     * 包含安全配置信息
     */
    public EjbDescriptor getEjbDescriptor() {
        return ejbDescriptor;
    }

    /**
     * 检查是否需要 SSL
     */
    public boolean requiresSSL() {
        return ejbDescriptor.allMechanismsRequireSSL();
    }

    /**
     * 获取客户端认证要求
     */
    public boolean getClientAuthenticationRequired() {
        return ejbDescriptor.isClientAuthenticationRequired();
    }
}
```

### CSIv2 SSL Tagged Component

```java
/**
 * CSIv2 SSL Tagged Component 处理器
 * 用于在 IOR 中添加 SSL 信息
 */
public class CSIv2SSLTaggedComponentHandlerImpl {
    /**
     * 创建 SSL Tagged Component
     */
    public SSLTaggedComponent createSSLComponent(
            IiopListener listener) {
        // 1. 检查是否启用 SSL
        boolean securityEnabled =
            Boolean.parseBoolean(listener.getSecurityEnabled());
        if (!securityEnabled) {
            return null;
        }

        // 2. 获取 SSL 配置
        Ssl ssl = listener.getSsl();

        // 3. 创建 SSL Tagged Component
        short targetSupport = 0;
        short targetRequires = 0;

        // 设置 SSL 支持
        targetSupport |= SSL_REQUIRED;

        // 设置客户端认证
        if (Boolean.parseBoolean(ssl.getClientAuthEnabled())) {
            targetRequires |= CLIENT_AUTH_REQUIRED;
        }

        return new SSLTaggedComponent(
            targetSupport, targetRequires, (short) 0);
    }
}
```

## OTSPolicyImpl

### 核心职责

```java
/**
 * OTS (Object Transaction Service) 策略
 * 用于分布式事务管理
 */
public class OTSPolicyImpl extends LocalObject implements OTSPolicy {

    // 预定义策略实例
    public static final OTSPolicyImpl _ADAPTS =
        new OTSPolicyImpl(ADAPTS.value);
    public static final OTSPolicyImpl _FORBIDS =
        new OTSPolicyImpl(FORBIDS.value);
    public static final OTSPolicyImpl _REQUIRES =
        new OTSPolicyImpl(REQUIRES.value);

    private short value;

    /**
     * 事务策略值:
     * - ADAPTS: 容器适应事务上下文
     * - FORBIDS: 禁止事务
     * - REQUIRES: 必须有事务
     */

    public int policy_type() {
        return OTS_POLICY_TYPE.value;
    }

    public short value() {
        return this.value;
    }
}
```

## IiopFolbGmsClient

### 核心职责

```java
/**
 * FOLB (Failover Load Balancing) GMS (Group Management Service) 客户端
 * 监听集群事件并更新 IIOP 端点信息
 */
@Service
public class IiopFolbGmsClient implements ClusterListener {

    private PayaraCluster cluster;
    private GroupInfoService gis;
    private Map<String, ClusterInstanceInfo> currentMembers;

    /**
     * 构造函数
     */
    public IiopFolbGmsClient(ServiceLocator services) {
        this.services = services;
        this.cluster = services.getService(PayaraCluster.class);

        if (cluster != null && cluster.isEnabled()) {
            // 1. 获取配置信息
            domain = services.getService(Domain.class);
            nodes = services.getService(Nodes.class);
            myServer = servers.getServer(instanceName);

            // 2. 创建 GroupInfoService
            gis = new GroupInfoServiceGMSImpl();

            // 3. 初始化集群成员
            currentMembers = getAllClusterInstanceInfo();

            // 4. 注册集群监听器
            cluster.addClusterListener(this);
        }
    }

    /**
     * 集群成员添加事件
     */
    @Override
    public void memberAdded(MemberEvent event) {
        if (cluster.getUnderlyingHazelcastService()
                .getMemberGroup().equals(event.getServerGroup())) {
            addMember(event.getServer());
        }
    }

    /**
     * 集群成员移除事件
     */
    @Override
    public void memberRemoved(MemberEvent event) {
        if (cluster.getUnderlyingHazelcastService()
                .getMemberGroup().equals(event.getServerGroup())) {
            removeMember(event.getServer());
        }
    }

    /**
     * 获取 IIOP 端点信息
     * 返回格式: "host1:port1,host2:port2,..."
     */
    public String getIIOPEndpoints() {
        Map<String, ClusterInstanceInfo> cinfos =
            getAllClusterInstanceInfo();
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (ClusterInstanceInfo cinfo : cinfos.values()) {
            for (SocketInfo sinfo : cinfo.endpoints()) {
                // 只返回非 SSL 端点
                if (!sinfo.type().startsWith("SSL")) {
                    if (!first) {
                        result.append(',');
                    }
                    result.append(sinfo.host())
                          .append(':')
                          .append(sinfo.port());
                    first = false;
                }
            }
        }
        return result.toString();
    }

    /**
     * 获取集群实例信息
     */
    private ClusterInstanceInfo getClusterInstanceInfo(
            Server server, Config config,
            boolean assumeInstanceIsRunning) {

        // 1. 检查服务器状态
        if (!assumeInstanceIsRunning && !server.isRunning()) {
            return null;
        }

        // 2. 获取服务器名称和权重
        String name = server.getName();
        int weight = Integer.parseInt(server.getLbWeight());

        // 3. 解析主机地址
        String hostName = resolveHostAddress(server);

        // 4. 获取 IIOP 监听器
        IiopService iservice = config.getExtensionByType(
            IiopService.class);
        List<IiopListener> listeners = iservice.getIiopListener();

        // 5. 创建 Socket 信息
        List<SocketInfo> sinfos = new ArrayList<>();
        for (IiopListener il : listeners) {
            int port = resolvePort(server, il);
            SocketInfo sinfo = new SocketInfo(
                il.getId(), hostName, port);
            sinfos.add(sinfo);
        }

        // 6. 创建集群实例信息
        return new ClusterInstanceInfo(name, weight, sinfos);
    }
}
```

## IIOP 端点信息

### IIOPEndpointsInfo

```java
/**
 * IIOP 端点信息管理
 */
public class IIOPEndpointsInfo {
    /**
     * 获取端点列表
     * 用于 IOR 生成
     */
    public static List<SocketInfo> getEndpoints() {
        List<SocketInfo> endpoints = new ArrayList<>();

        // 从配置获取所有 IIOP 监听器
        List<IiopListener> listeners = getIiopListeners();

        for (IiopListener listener : listeners) {
            if (Boolean.parseBoolean(listener.getEnabled())) {
                String host = listener.getAddress();
                int port = Integer.parseInt(listener.getPort());
                String id = listener.getId();
                boolean ssl = Boolean.parseBoolean(
                    listener.getSecurityEnabled());

                String type = ssl ? "SSL" : "CLEAR_TEXT";
                endpoints.add(new SocketInfo(id, host, port, type));
            }
        }

        return endpoints;
    }
}
```

## 拦截器

### IORAddrAnyInterceptor

```java
/**
 * IOR 地址拦截器
 * 用于修改 IOR (Interoperable Object Reference) 中的地址信息
 */
public class IORAddrAnyInterceptor extends LocalInterceptor {
    /**
     * 在 IOR 创建时调用
     */
    @Override
    public void establish_components(IORInfo iorInfo) {
        // 1. 获取端点信息
        List<SocketInfo> endpoints = IIOPEndpointsInfo.getEndpoints();

        // 2. 添加 IIOP 端点到 IOR
        for (SocketInfo endpoint : endpoints) {
            IIOPAddress iiopAddress = new IIOPAddress(
                endpoint.host(), endpoint.port());

            IIOPProfile iiopProfile = new IIOPProfile(
                iiopAddress, getObjectId(iorInfo));

            iorInfo.add_ior_profile(iiopProfile);
        }

        // 3. 添加 CSIv2 SSL 组件
        if (requiresSSL()) {
            SSLTaggedComponent sslComponent =
                createSSLComponent();
            iorInfo.add_tagged_component(SSL_COMPONENT_ID,
                sslComponent);
        }
    }
}
```

## SSL Socket 工厂

### IIOPSSLSocketFactory

```java
/**
 * IIOP SSL Socket 工厂
 * 创建支持 SSL/TLS 的 IIOP 连接
 */
public class IIOPSSLSocketFactory implements ORBSocketFactory {
    /**
     * 创建 SSL Socket
     */
    @Override
    public Socket createSocket(InetAddress host, int port)
            throws IOException {
        // 1. 获取 SSL 上下文
        SSLContext sslContext = getSSLContext();

        // 2. 创建 SSL Socket 工厂
        SSLSocketFactory factory =
            sslContext.getSocketFactory();

        // 3. 创建 SSL Socket
        SSLSocket socket = (SSLSocket)
            factory.createSocket(host, port);

        // 4. 配置 SSL 参数
        configureSSL(socket);

        return socket;
    }

    /**
     * 配置 SSL 参数
     */
    private void configureSSL(SSLSocket socket) {
        // 启用 TLS
        socket.setEnabledProtocols(new String[]{
            "TLSv1.2", "TLSv1.3"
        });

        // 配置密码套件
        String[] cipherSuites = getEnabledCipherSuites();
        socket.setEnabledCipherSuites(cipherSuites);

        // 设置客户端认证
        if (clientAuthRequired()) {
            socket.setNeedClientAuth(true);
        }
    }
}
```

## HandleDelegate

### IIOPHandleDelegate

```java
/**
 * RMI-IIOP HandleDelegate 实现
 * 用于序列化和反序列化 EJB 句柄
 */
public class IIOPHandleDelegate implements HandleDelegate {
    /**
     * 从 IOR 读取句柄
     */
    @Override
    public Handle readIOR(InputStream is) {
        // 1. 读取 IOR
        IOR ior = IORHelper.read(is);

        // 2. 提取 Object Key
        byte[] objectKey = ior.getObjectKey();

        // 3. 解析 EJB 密钥
        EJBKey ejbKey = parseEJBKey(objectKey);

        // 4. 创建 Handle
        return new IIOPHandle(ejbKey.ejbId, ejbKey.instanceKey);
    }

    /**
     * 将句柄写入 IOR
     */
    @Override
    public void writeIOR(Handle handle, OutputStream os) {
        // 1. 获取 EJB 密钥
        IIOPHandle iiopHandle = (IIOPHandle) handle;

        // 2. 创建 Object Key
        byte[] objectKey = createEJBKey(
            iiopHandle.getEjbId(),
            iiopHandle.getInstanceKey());

        // 3. 创建 IOR
        IOR ior = createIOR(objectKey);

        // 4. 写入 IOR
        IORHelper.write(os, ior);
    }
}
```

## 命名服务

### NamingClusterInfoImpl

```java
/**
 * 命名服务集群信息实现
 */
public class NamingClusterInfoImpl implements NamingClusterInfo {
    /**
     * 获取集群端点
     */
    @Override
    public String getClusterEndpoints() {
        IiopFolbGmsClient gmsClient = getGmsClient();
        if (gmsClient != null) {
            return gmsClient.getIIOPEndpoints();
        }
        return null;
    }

    /**
     * 刷新集群信息
     */
    @Override
    public void refreshClusterInfo() {
        // 通知 GroupInfoService 观察者
        gmsClient.getGroupInfoService().notifyObservers();
    }
}
```

## 相关模块

- `orb-connector` - 核心 ORB 连接器 API
- `orb-enabler` - 配置和启动
- `appserver/ejb` - EJB 容器 (使用 ORB 进行远程调用)
- `glassfish-corba-orb` - GlassFish CORBA ORB 实现
- `nucleus/cluster` - 集群基础设施 (Hazelcast 集成)

## 配置示例

### 基本 IIOP 配置

```xml
<iiop-service>
    <orb max-connections="1024" message-fragment-size="1024" />
    <iiop-listener id="orb-listener"
                   address="0.0.0.0"
                   port="3700"
                   enabled="true"
                   security-enabled="false"
                   lazy-init="false" />
</iiop-service>
```

### SSL 配置

```xml
<iiop-service client-authentication-required="false">
    <ssl-client-config>
        <ssl cert-nickname="s1as"
             ssl2-enabled="false"
             ssl3-enabled="false"
             tls-enabled="true"
             tls1.1-enabled="true"
             tls1.2-enabled="true"
             client-auth-enabled="false" />
    </ssl-client-config>
    <iiop-listener id="orb-ssl"
                   port="3820"
                   security-enabled="true">
        <ssl cert-nickname="s1as"
             ssl3-enabled="false"
             tls-enabled="true" />
    </iiop-listener>
</iiop-service>
```

### 集群配置

```xml
<clusters>
    <cluster name="mycluster">
        <config name="mycluster-config">
            <iiop-service>
                <iiop-listener id="orb-cluster"
                               port="3700" />
            </iiop-service>
        </config>
    </cluster>
</clusters>
```
