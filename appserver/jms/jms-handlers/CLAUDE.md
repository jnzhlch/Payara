# JMS Handlers - 注解处理器架构

## 概述

`jms-handlers` 模块提供 JMS 2.0/2.1 注解定义处理器，用于处理部署时发现的 `@JMSConnectionFactoryDefinition` 和 `@JMSDestinationDefinition` 注解。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Annotation Processing                        │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  @JMSConnectionFactoryDefinition                          │ │
│  │  ┌────────────────────────────────────────────────────┐  │ │
│  │  │  JMSConnectionFactoryDefinitionHandler             │  │ │
│  │  │  - processAnnotation()                             │  │ │
│  │  │  - createDescriptor()                              │  │ │
│  │  │  - merge()                                         │  │ │
│  │  │  └─→ JMSConnectionFactoryDefinitionDescriptor      │  │ │
│  │  └────────────────────────────────────────────────────┘  │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  @JMSDestinationDefinition                               │ │
│  │  ┌────────────────────────────────────────────────────┐  │ │
│  │  │  JMSDestinationDefinitionHandler                   │  │ │
│  │  │  - processAnnotation()                             │  │ │
│  │  │  - createDescriptor()                              │  │ │
│  │  │  - merge()                                         │  │ │
│  │  │  └─→ JMSDestinationDefinitionDescriptor            │  │ │
│  │  └────────────────────────────────────────────────────┘  │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Deployment Context                           │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  ResourceContainerContext                                │ │
│  │  ├─→ EjbBundleContext                                    │ │
│  │  ├─→ EjbContext                                          │ │
│  │  ├─→ EjbInterceptorContext                               │ │
│  │  ├─→ WebBundleContext                                    │ │
│  │  ├─→ WebComponentsContext                                │ │
│  │  └─→ WebComponentContext                                 │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## JMSConnectionFactoryDefinitionHandler

### 核心职责

```java
@Service
@AnnotationHandlerFor(JMSConnectionFactoryDefinition.class)
public class JMSConnectionFactoryDefinitionHandler
        extends AbstractResourceHandler {

    /**
     * 处理 @JMSConnectionFactoryDefinition 注解
     */
    protected HandlerProcessingResult processAnnotation(
            JMSConnectionFactoryDefinition jmsConnectionFactoryDefnAn,
            AnnotationInfo aiInfo,
            ResourceContainerContext[] rcContexts) {

        // 1. 确定注解类类型
        Class<?> annotatedClass = (Class<?>)aiInfo.getAnnotatedElement();
        boolean warClass = isAWebComponentClass(annotations);
        boolean ejbClass = isAEjbComponentClass(annotations);

        // 2. 验证上下文
        for (ResourceContainerContext context : rcContexts) {
            if (!canProcessAnnotation(annotatedClass, ejbClass, warClass, context)) {
                return getDefaultProcessedResult();
            }

            // 3. 创建描述符
            JMSConnectionFactoryDefinitionDescriptor desc =
                createDescriptor(jmsConnectionFactoryDefnAn);

            // 4. 检查是否已存在
            Set<ResourceDescriptor> jmscfdDescs =
                context.getResourceDescriptors(JavaEEResourceType.JMSCFDD);

            if (isDefinitionAlreadyPresent(jmscfdDescs, desc)) {
                merge(jmscfdDescs, jmsConnectionFactoryDefnAn);
            } else {
                setDefaultTransactionSupport(desc);
                context.addResourceDescriptor(desc);
            }
        }
        return getDefaultProcessedResult();
    }
}
```

### 描述符创建

```java
private JMSConnectionFactoryDefinitionDescriptor createDescriptor(
        JMSConnectionFactoryDefinition defn) {

    JMSConnectionFactoryDefinitionDescriptor desc =
        new JMSConnectionFactoryDefinitionDescriptor();
    desc.setMetadataSource(MetadataSource.ANNOTATION);

    // 基本属性
    desc.setName(defn.name());
    desc.setInterfaceName(defn.interfaceName());

    // 可选属性
    if (isValid(defn.className())) {
        desc.setClassName(defn.className());
    }
    if (isValid(defn.description())) {
        desc.setDescription(defn.description());
    }
    if (isValid(defn.resourceAdapter())) {
        desc.setResourceAdapter(defn.resourceAdapter());
    }

    // 连接属性
    if (isValid(defn.user())) {
        desc.setUser(defn.user());
    }
    if (defn.password() != null) {
        desc.setPassword(defn.password());
    }

    // 客户端 ID
    if (isValid(defn.clientId())) {
        desc.setClientId(defn.clientId());
    }

    // 事务支持
    desc.setTransactional(defn.transactional());

    // 连接池配置
    if (defn.maxPoolSize() >= 0) {
        desc.setMaxPoolSize(defn.maxPoolSize());
    }
    if (defn.minPoolSize() >= 0) {
        desc.setMinPoolSize(defn.minPoolSize());
    }

    // 自定义属性
    if (defn.properties() != null) {
        Properties properties = desc.getProperties();
        String[] defnProperties = defn.properties();
        for (String property : defnProperties) {
            int index = property.indexOf('=');
            if (index > 0 && index < property.length() - 1) {
                String name = property.substring(0, index).trim();
                String value = property.substring(index + 1).trim();
                properties.put(name, value);
            }
        }
    }

    return desc;
}
```

### 默认事务支持

```java
private void setDefaultTransactionSupport(
        JMSConnectionFactoryDefinitionDescriptor desc) {
    Properties props = desc.getProperties();
    if (props.get("org.glassfish.connector-connection-pool.transaction-support") == null)
        props.put("org.glassfish.connector-connection-pool.transaction-support",
                  "XATransaction");
}
```

## JMSDestinationDefinitionHandler

### 核心职责

```java
@Service
@AnnotationHandlerFor(JMSDestinationDefinition.class)
public class JMSDestinationDefinitionHandler
        extends AbstractResourceHandler {

    /**
     * 处理 @JMSDestinationDefinition 注解
     */
    protected HandlerProcessingResult processAnnotation(
            JMSDestinationDefinition jmsDestinationDefnAn,
            AnnotationInfo aiInfo,
            ResourceContainerContext[] rcContexts) {

        Class<?> annotatedClass = (Class<?>)aiInfo.getAnnotatedElement();
        boolean warClass = isAWebComponentClass(annotations);
        boolean ejbClass = isAEjbComponentClass(annotations);

        for (ResourceContainerContext context : rcContexts) {
            if (!canProcessAnnotation(annotatedClass, ejbClass, warClass, context)) {
                return getDefaultProcessedResult();
            }

            // 创建目标描述符
            JMSDestinationDefinitionDescriptor desc =
                createDescriptor(jmsDestinationDefnAn);

            Set<ResourceDescriptor> jmsddDescs =
                context.getResourceDescriptors(JavaEEResourceType.JMSDD);

            if (isDefinitionAlreadyPresent(jmsddDescs, desc)) {
                merge(jmsddDescs, jmsDestinationDefnAn);
            } else {
                context.addResourceDescriptor(desc);
            }
        }
        return getDefaultProcessedResult();
    }
}
```

### 描述符创建

```java
private JMSDestinationDefinitionDescriptor createDescriptor(
        JMSDestinationDefinition defn) {

    JMSDestinationDefinitionDescriptor desc =
        new JMSDestinationDefinitionDescriptor();
    desc.setMetadataSource(MetadataSource.ANNOTATION);

    // 基本属性
    desc.setName(defn.name());
    desc.setInterfaceName(defn.interfaceName());

    // 可选属性
    if (isValid(defn.className())) {
        desc.setClassName(defn.className());
    }
    if (isValid(defn.description())) {
        desc.setDescription(defn.description());
    }
    if (isValid(defn.resourceAdapter())) {
        desc.setResourceAdapter(defn.resourceAdapter());
    }

    // 目标名称
    if (isValid(defn.destinationName())) {
        desc.setDestinationName(defn.destinationName());
    }

    // 自定义属性
    if (defn.properties() != null) {
        Properties properties = desc.getProperties();
        for (String property : defn.properties()) {
            // 解析 key=value 格式
            int index = property.indexOf('=');
            if (index > 0 && index < property.length() - 1) {
                String name = property.substring(0, index).trim();
                String value = property.substring(index + 1).trim();
                properties.put(name, value);
            }
        }
    }

    return desc;
}
```

## 上下文验证

### EJB/Web 组件检测

```java
/**
 * 确定类是否为 EJB 组件
 */
private boolean isAEjbComponentClass(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
        if (annotation.annotationType().getName().startsWith("jakarta.ejb.")) {
            return true;
        }
    }
    return false;
}

/**
 * 确定类是否为 Web 组件
 */
private boolean isAWebComponentClass(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
        String typeName = annotation.annotationType().getName();
        if (typeName.equals("jakarta.servlet.http.WebServlet") ||
            typeName.equals("jakarta.servlet.annotation.WebFilter") ||
            typeName.equals("jakarta.servlet.annotation.WebListener")) {
            return true;
        }
    }
    return false;
}
```

### 上下文匹配

```java
private boolean canProcessAnnotation(Class<?> annotatedClass,
                                     boolean ejbClass,
                                     boolean warClass,
                                     ResourceContainerContext context) {
    // EJB 类必须与 EJB 上下文匹配
    if (ejbClass) {
        if (!(context instanceof EjbBundleContext ||
              context instanceof EjbContext ||
              context instanceof EjbInterceptorContext)) {
            return false;
        }
    }

    // Web 类必须与 Web 上下文匹配
    else if (warClass) {
        if (!(context instanceof WebBundleContext ||
              context instanceof WebComponentsContext ||
              context instanceof WebComponentContext)) {
            return false;
        }
    }

    // WAR 中的 EJB 处理
    else if (context instanceof WebBundleContext) {
        WebBundleDescriptor webBundleDescriptor =
            ((WebBundleContext) context).getDescriptor();
        Collection<RootDeploymentDescriptor> extDesc =
            webBundleDescriptor.getExtensionsDescriptors();

        for (RootDeploymentDescriptor desc : extDesc) {
            if (desc instanceof EjbBundleDescriptor) {
                EjbBundleDescriptor ejbBundleDesc = (EjbBundleDescriptor)desc;
                EjbDescriptor[] ejbDescs =
                    ejbBundleDesc.getEjbByClassName(annotatedClass.getName());
                if (ejbDescs != null && ejbDescs.length > 0) {
                    return false; // EJB 类不应在 Web 上下文中处理
                }
            }
        }
    }

    return true;
}
```

## 处理器生命周期

```
应用部署
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│  扫描注解                                                       │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  @JMSConnectionFactoryDefinition                          │ │
│  │  @JMSDestinationDefinition                               │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  调用处理器                                                     │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  AnnotationProcessor                                     │ │
│  │  ├─→ JMSConnectionFactoryDefinitionHandler               │ │
│  │  └─→ JMSDestinationDefinitionHandler                    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  创建描述符                                                     │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  JMSConnectionFactoryDefinitionDescriptor                │ │
│  │  JMSDestinationDefinitionDescriptor                      │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  添加到部署上下文                                               │
│  ResourceContainerContext.addResourceDescriptor()              │
└─────────────────────────────────────────────────────────────────┘
```

## 注解合并策略

```java
private void merge(Set<ResourceDescriptor> descriptors,
                   JMSConnectionFactoryDefinition defn) {

    for (ResourceDescriptor descriptor : descriptors) {
        if (descriptor instanceof JMSConnectionFactoryDefinitionDescriptor) {
            JMSConnectionFactoryDefinitionDescriptor desc =
                (JMSConnectionFactoryDefinitionDescriptor)descriptor;

            if (desc.getName().equals(defn.name())) {
                // 只合并未设置的值
                if (desc.getInterfaceName() == null) {
                    desc.setInterfaceName(defn.interfaceName());
                }
                if (desc.getClassName() == null && isValid(defn.className())) {
                    desc.setClassName(defn.className());
                }
                if (desc.getDescription() == null && isValid(defn.description())) {
                    desc.setDescription(defn.description());
                }
                // ... 其他属性

                // 属性合并 (只添加不存在的键)
                Properties properties = desc.getProperties();
                for (String property : defn.properties()) {
                    int index = property.indexOf('=');
                    if (index > 0 && index < property.length() - 1) {
                        String name = property.substring(0, index).trim();
                        if (properties.get(name) == null) {
                            String value = property.substring(index + 1).trim();
                            properties.put(name, value);
                        }
                    }
                }
                break;
            }
        }
    }
}
```

## 使用示例

### @JMSConnectionFactoryDefinition

```java
@JMSConnectionFactoryDefinition(
    name = "java:global/jms/MyConnectionFactory",
    interfaceName = "jakarta.jms.ConnectionFactory",
    resourceAdapter = "jmsra",
    className = "com.sun.messaging.jms.ConnectionFactory",
    user = "admin",
    password = "admin",
    clientId = "MyClient",
    transactional = true,
    maxPoolSize = 30,
    minPoolSize = 10,
    properties = {
        "imqAddressList=mq://localhost:7676",
        "imqReconnectEnabled=true"
    }
)
public class MyApplication {
    // ConnectionFactory 可用: java:global/jms/MyConnectionFactory
}
```

### @JMSDestinationDefinition

```java
@JMSDestinationDefinition(
    name = "java:global/jms/MyQueue",
    interfaceName = "jakarta.jms.Queue",
    resourceAdapter = "jmsra",
    destinationName = "MyPhysicalQueue",
    properties = {
        "Name=MyPhysicalQueue",
        "imqDestinationName=MyPhysicalQueue"
    }
)
public class MyApplication {
    // Queue 可用: java:global/jms/MyQueue
}
```

### @JMSConnectionFactoryDefinitions (复数)

```java
@JMSConnectionFactoryDefinitions({
    @JMSConnectionFactoryDefinition(
        name = "java:global/jms/Factory1",
        interfaceName = "jakarta.jms.ConnectionFactory"
    ),
    @JMSConnectionFactoryDefinition(
        name = "java:global/jms/Factory2",
        interfaceName = "jakarta.jms.QueueConnectionFactory"
    )
})
public class MyApplication {
    // 多个工厂可用
}
```

## 处理器选择

| 注解类型 | 处理器 | 描述符类型 |
|---------|--------|-----------|
| `@JMSConnectionFactoryDefinition` | `JMSConnectionFactoryDefinitionHandler` | `JMSConnectionFactoryDefinitionDescriptor` |
| `@JMSConnectionFactoryDefinitions` | `JMSConnectionFactoryDefinitionsHandler` | 多个工厂描述符 |
| `@JMSDestinationDefinition` | `JMSDestinationDefinitionHandler` | `JMSDestinationDefinitionDescriptor` |
| `@JMSDestinationDefinitions` | `JMSDestinationDefinitionsHandler` | 多个目标描述符 |

## 相关模块

- `jms-core` - JMS 资源适配器核心
- `gf-jms-connector` - JMS 连接器配置
- `gf-jms-injection` - CDI 注入支持
- `deployment/javaee-core` - 注解处理基础设施
