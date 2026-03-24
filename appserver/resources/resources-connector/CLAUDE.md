# CLAUDE.md - Resources Connector

This file provides guidance for working with the `appserver/resources/resources-connector` module - Resource APIs, admin CLI, and configuration beans.

## Module Overview

The resources-connector module provides the core APIs, admin CLI commands, and configuration beans for resource management in Payara Server. It serves as the foundation for resource deployers.

**Key Components:**
- **Resource APIs** - JavaEEResource, Resource constants, ResourcesRegistry
- **Admin CLI** - Commands for creating/deleting/listing resources
- **Configuration Beans** - CustomResource, ExternalJndiResource
- **ObjectFactories** - Factories for creating custom resource instances
- **JNDI Proxy** - Proxy for external JNDI lookups

## Build Commands

```bash
# Build resources-connector module
mvn -DskipTests clean package -f appserver/resources/resources-connector/pom.xml
```

## Module Contents

```
resources-connector/
└── src/main/java/org/glassfish/resources/
    ├── admin/cli/
    │   ├── AddResources.java
    │   ├── CreateCustomResource.java
    │   ├── CreateJndiResource.java
    │   ├── DeleteCustomResource.java
    │   ├── DeleteJndiResource.java
    │   ├── ListCustomResources.java
    │   ├── ListJndiResources.java
    │   ├── ResourceFactory.java
    │   ├── ResourceManager.java
    │   └── ResourcesXMLParser.java
    ├── api/
    │   ├── JavaEEResource.java
    │   ├── JavaEEResourceBase.java
    │   ├── Resource.java
    │   ├── ResourceConstants.java
    │   ├── ResourcePropertyImpl.java
    │   └── ResourcesRegistry.java
    ├── config/
    │   ├── CustomResource.java
    │   └── ExternalJndiResource.java
    ├── custom/factory/
    │   ├── JavaBeanFactory.java
    │   ├── PrimitivesAndStringFactory.java
    │   ├── PropertiesFactory.java
    │   └── URLObjectFactory.java
    └── naming/
        ├── JndiProxyObjectFactory.java
        ├── SerializableObjectRefAddr.java
        └── ProxyRefAddr.java
```

## Resource APIs

### JavaEEResource Interface

```java
public interface JavaEEResource extends Serializable {

    // Resource types
    int CUSTOM_RESOURCE = 0;
    int JDBC_RESOURCE = 1;
    int JDBC_CONNECTION_POOL = 2;
    int MAIL_RESOURCE = 4;
    int JNDI_RESOURCE = 5;
    int CONNECTOR_RESOURCE = 6;
    int ADMIN_OBJECT_RESOURCE = 7;
    int CONNECTOR_CONNECTION_POOL = 8;
    int RESOURCE_ADAPTER_CONFIG = 9;
    int CONNECTOR_WORK_SECURITY_MAP = 10;
    int PERSISTENCE_MANAGER_CONFIG_RESOURCE = 11;
    int EXTERNAL_JNDI_RESOURCE = 12;

    ResourceInfo getResourceInfo();
    void setResourceInfo(ResourceInfo resourceInfo);
    int getType();
    String getDescription();
    void setDescription(String description);
    List<ResourceProperty> getProperties();
    void addProperty(ResourceProperty property);
    void removeProperty(ResourceProperty property);
    JavaEEResource clone(ResourceInfo resourceInfo);
    boolean isEnabled();
    void setEnabled(boolean enabled);
}
```

### Resource Constants

```java
public class Resource {
    // Resource types
    public static final String CUSTOM_RESOURCE = "custom-resource";
    public static final String MAIL_RESOURCE = "mail-resource";
    public static final String EXTERNAL_JNDI_RESOURCE = "external-jndi-resource";
    public static final String JDBC_RESOURCE = "jdbc-resource";
    public static final String CONNECTOR_RESOURCE = "connector-resource";

    // Bindable resources (can be referenced by applications)
    public static final List<String> BINDABLE_RESOURCES = Arrays.asList(
        JDBC_RESOURCE,
        JDBC_CONNECTION_POOL,
        CONNECTOR_RESOURCE,
        ADMIN_OBJECT_RESOURCE,
        CONNECTOR_CONNECTION_POOL,
        MAIL_RESOURCE,
        CUSTOM_RESOURCE,
        EXTERNAL_JNDI_RESOURCE
    );
}
```

### ResourcesRegistry

Registry for storing resources during deployment:

```java
public class ResourcesRegistry {
    private static Map<String, Map<String, Resources>> registry = new HashMap<>();

    public static void putResources(String appName, Map<String, Resources> allResources) {
        registry.put(appName, allResources);
    }

    public static Map<String, Resources> getResources(String appName) {
        return registry.get(appName);
    }

    public static Map<String, Resources> remove(String appName) {
        return registry.remove(appName);
    }

    public static void clear() {
        registry.clear();
    }
}
```

## Admin CLI Commands

### Create Custom Resource

```bash
asadmin create-custom-resource \
    --resType java.lang.String \
    --factoryClass org.glassfish.resources.custom.factory.PrimitivesAndStringFactory \
    --description "My Custom Resource" \
    --property value=HelloWorld \
    myApp/custom/MyString
```

### Delete Custom Resource

```bash
asadmin delete-custom-resource myApp/custom/MyString
```

### List Custom Resources

```bash
asadmin list-custom-resources
```

### Create External JNDI Resource

```bash
asadmin create-jndi-resource \
    --jndilookupname jms/MyQueue \
    --factoryclass org.apache.activemq.jndi.ActiveMQInitialContextFactory \
    --providercurl tcp://localhost:61616 \
    --restype jakarta.jms.Queue \
    jms/ExternalQueue
```

### Delete External JNDI Resource

```bash
asadmin delete-jndi-resource jms/ExternalQueue
```

### List JNDI Resources

```bash
asadmin list-jndi-resources
```

### Add Resources from XML

```bash
asadmin add-resources path/to/glassfish-resources.xml
```

## ResourcesXMLParser

Parses `glassfish-resources.xml` and `payara-resources.xml`:

```java
public class ResourcesXMLParser {

    private File file;
    private String scope; // "java:app/" or "java:module/"

    public List<org.glassfish.resources.api.Resource> getResourcesList() {
        // Parse XML and return list of resources
    }

    public static List<Resource> getNonConnectorResourcesList(
            List<Resource> resources, boolean... filters) {
        // Return non-connector resources (custom, mail, jndi, etc.)
    }

    public static List<Resource> getConnectorResourcesList(
            List<Resource> resources, boolean... filters) {
        // Return connector resources (rar, pool, admin object, etc.)
    }
}
```

## Custom Resource Factories

### PrimitivesAndStringFactory

Factory for primitive values and strings:

```java
public class PrimitivesAndStringFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                   Hashtable<?, ?> environment) throws Exception {
        Reference ref = (Reference) obj;
        String type = ref.getClassName();
        String value = extractRefAddr(ref, "value");

        if ("java.lang.String".equals(type)) {
            return value;
        } else if ("java.lang.Integer".equals(type) || "int".equals(type)) {
            return Integer.valueOf(value);
        } else if ("java.lang.Boolean".equals(type) || "boolean".equals(type)) {
            return Boolean.valueOf(value);
        } else if ("java.lang.Long".equals(type) || "long".equals(type)) {
            return Long.valueOf(value);
        } else if ("java.lang.Double".equals(type) || "double".equals(type)) {
            return Double.valueOf(value);
        } else if ("java.lang.Float".equals(type) || "float".equals(type)) {
            return Float.valueOf(value);
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
}
```

### PropertiesFactory

Factory for Properties objects:

```java
public class PropertiesFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                   Hashtable<?, ?> environment) throws Exception {
        Reference ref = (Reference) obj;
        Properties props = new Properties();

        // Add all RefAddrs as properties
        for (int i = 0; i < ref.size(); i++) {
            RefAddr addr = ref.get(i);
            props.setProperty(addr.getType(), (String) addr.getContent());
        }

        return props;
    }
}
```

### URLObjectFactory

Factory for URL objects:

```java
public class URLObjectFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                   Hashtable<?, ?> environment) throws Exception {
        Reference ref = (Reference) obj;
        String url = extractRefAddr(ref, "url");
        return new URL(url);
    }
}
```

### JavaBeanFactory

Factory for JavaBean objects:

```java
public class JavaBeanFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                   Hashtable<?, ?> environment) throws Exception {
        Reference ref = (Reference) obj;
        String className = ref.getClassName();

        // Create instance
        Class<?> clazz = Class.forName(className);
        Object bean = clazz.getDeclaredConstructor().newInstance();

        // Set properties via introspection
        BeanInfo bi = Introspector.getBeanInfo(clazz);
        for (PropertyDescriptor pd : bi.getPropertyDescriptors()) {
            String propName = pd.getName();
            String value = extractRefAddr(ref, propName);
            if (value != null && pd.getWriteMethod() != null) {
                // Convert string to property type and set
                Object convertedValue = convertValue(value, pd.getPropertyType());
                pd.getWriteMethod().invoke(bean, convertedValue);
            }
        }

        return bean;
    }
}
```

## JndiProxyObjectFactory

ObjectFactory for external JNDI resources:

```java
public class JndiProxyObjectFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                   Hashtable<?, ?> environment) throws Exception {
        Reference ref = (Reference) obj;

        // Extract external JNDI configuration
        String jndiLookupName = extractRefAddr(ref, JNDI_LOOKUP_NAME);
        String factoryClass = extractRefAddr(ref, JNDI_CONTEXT_FACTORY);
        String providerUrl = extractRefAddr(ref, JNDI_PROVIDER_URL);
        String username = extractRefAddr(ref, JNDI_SECURITY_PRINCIPAL);
        String password = extractRefAddr(ref, JNDI_SECURITY_CREDENTIALS);

        // Create external JNDI environment
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, factoryClass);
        env.put(Context.PROVIDER_URL, providerUrl);
        if (username != null) {
            env.put(Context.SECURITY_PRINCIPAL, username);
            env.put(Context.SECURITY_CREDENTIALS, password);
        }

        // Create context and lookup
        Context ctx = new InitialContext(env);
        return ctx.lookup(jndiLookupName);
    }
}
```

## ResourceFactory

Factory for resource managers:

```java
public class ResourceFactory {

    public ResourceManager getResourceManager(Resource resource) {
        String type = resource.getType();

        if (CUSTOM_RESOURCE.equals(type)) {
            return new CustomResourceManager();
        } else if (MAIL_RESOURCE.equals(type)) {
            return new JavaMailResourceManager();
        } else if (EXTERNAL_JNDI_RESOURCE.equals(type)) {
            return new JndiResourceManager();
        } else if (JDBC_RESOURCE.equals(type)) {
            return new JdbcResourceManager();
        } else if (CONNECTOR_RESOURCE.equals(type)) {
            return new ConnectorResourceManager();
        }
        throw new IllegalArgumentException("Unknown resource type: " + type);
    }
}
```

## Configuration Beans

### CustomResource Config Bean

```java
public interface CustomResource extends ConfigBean, Resource {

    String getJndiName();
    void setJndiName(String value);

    String getResType();
    void setResType(String value);

    String getFactoryClass();
    void setFactoryClass(String value);

    String getEnabled();
    void setEnabled(String value);

    List<Property> getProperty();
}
```

### ExternalJndiResource Config Bean

```java
public interface ExternalJndiResource extends ConfigBean, Resource {

    String getJndiName();
    void setJndiName(String value);

    String getJndiLookupName();
    void setJndiLookupName(String value);

    String getFactoryClass();
    void setFactoryClass(String value);

    String getProviderUrl();
    void setProviderUrl(String value);

    String getResType();
    void setResType(String value);
}
```

## glassfish-resources.xml Example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE resources PUBLIC
    "-//GlassFish.org//DTD GlassFish Application Server 3.1 Resource Definitions //EN"
    "https://glassfish.org/dtds/glassfish-resources_1_5.dtd">
<resources>
    <!-- Custom String Resource -->
    <custom-resource
        jndi-name="myApp/custom/MyString"
        res-type="java.lang.String"
        factory-class="org.glassfish.resources.custom.factory.PrimitivesAndStringFactory">
        <property name="value" value="Hello World"/>
    </custom-resource>

    <!-- Custom Properties Resource -->
    <custom-resource
        jndi-name="myApp/custom/MyProperties"
        res-type="java.util.Properties"
        factory-class="org.glassfish.resources.custom.factory.PropertiesFactory">
        <property name="key1" value="value1"/>
        <property name="key2" value="value2"/>
    </custom-resource>

    <!-- External JNDI Resource -->
    <external-jndi-resource
        jndi-name="myApp/jms/ExternalQueue"
        jndi-lookup-name="jms/MyQueue"
        res-type="jakarta.jms.Queue"
        factory-class="org.apache.activemq.jndi.ActiveMQInitialContextFactory"
        provider-url="tcp://localhost:61616"/>
</resources>
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `config-api` | Configuration beans (ConfigBean, Property) |
| `glassfish-api` | Public APIs |
| `resourcebase/resources` | Base resource infrastructure |

## Notes

- **Custom Resources** - User-defined resources with ObjectFactory
- **External JNDI** - Proxy to external JNDI sources (e.g., external JMS)
- **Built-in Factories** - PrimitivesAndStringFactory, PropertiesFactory, URLObjectFactory, JavaBeanFactory
- **Add Resources** - Bulk resource creation from XML files
- **Resource Identity** - JNDI name + application name + module name
