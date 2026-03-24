# CLAUDE.md - Resources Runtime

This file provides guidance for working with the `appserver/resources/resources-runtime` module - Runtime deployers for custom and external JNDI resources.

## Module Overview

The resources-runtime module provides runtime deployers for custom resources and external JNDI resources, as well as the main ResourcesDeployer that handles application-scoped resources from deployment archives.

**Key Components:**
- **ResourcesDeployer** - Handles app-scoped resources from `glassfish-resources.xml`/`payara-resources.xml`
- **CustomResourceDeployer** - Deploys custom JNDI resources
- **ExternalJndiResourceDeployer** - Deploys external JNDI resource proxies

## Build Commands

```bash
# Build resources-runtime module
mvn -DskipTests clean package -f appserver/resources/resources-runtime/pom.xml
```

## Module Contents

```
resources-runtime/
└── src/main/java/org/glassfish/resources/
    ├── deployer/
    │   ├── CustomResourceDeployer.java
    │   ├── ExternalJndiResourceDeployer.java
    │   └── GlobalResourceDeployer.java
    ├── module/
    │   ├── ResourcesDeployer.java
    │   ├── ResourcesContainer.java
    │   ├── ResourcesApplication.java
    │   └── ResourceUtilities.java
    └── beans/
        ├── CustomResource.java
        └── ExternalJndiResource.java
```

## ResourcesDeployer

### Deployment Lifecycle

```java
@Service
public class ResourcesDeployer extends JavaEEDeployer<ResourcesContainer, ResourcesApplication>
        implements PostConstruct, PreDestroy, EventListener {

    @Override
    public void event(Event event) {
        if (event.is(Deployment.DEPLOYMENT_BEFORE_CLASSLOADER_CREATION)) {
            // Process resources before classloader creation
            processResources(dc, deployParams);
        } else if (event.is(Deployment.UNDEPLOYMENT_VALIDATION)) {
            // Preserve resources for potential redeploy
            preserveResources(dc, undeployCommandParameters);
        }
    }
}
```

### Resource Processing Flow

```
DEPLOYMENT_BEFORE_CLASSLOADER_CREATION
       │
       ▼
[processResources()]
       │
       ├─→ [processArchive()]
       │   ├─→ Retrieve all glassfish-resources.xml/payara-resources.xml
       │   ├─→ Parse XML files
       │   ├─→ Separate connector and non-connector resources
       │   └─→ Store in DeploymentContext metadata
       │
       ├─→ [createResources()]
       │   ├─→ Create app-scoped Resources config
       │   ├─→ Create module-scoped Resources config
       │   └─→ Store in domain.xml (transient)
       │
       └─→ [deployResources()]
           ├─→ Get ResourceDeployer for each resource
           └─→ Call deployResource()
```

### Application-Scoped Resources

Resources defined in `META-INF/payara-resources.xml` or `WEB-INF/payara-resources.xml`:

```xml
<!-- payara-resources.xml -->
<resources>
    <custom-resource jndi-name="myApp/custom/MyResource"
        res-type="java.lang.String"
        factory-class="org.glassfish.resources.custom.factory.PrimitivesAndStringFactory">
        <property name="value" value="Hello World"/>
    </custom-resource>

    <mail-resource jndi-name="mail/MyMailSession"
        host="smtp.example.com"
        user="user"
        from="noreply@example.com"/>
</resources>
```

### Resource Registry

Temporary storage for resources during deployment:

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
}
```

## CustomResourceDeployer

### Custom Resource Deployment

```java
@Service
@ResourceDeployerInfo(CustomResource.class)
@Singleton
public class CustomResourceDeployer implements ResourceDeployer {

    public void installCustomResource(CustomResource customRes, ResourceInfo resourceInfo) {
        // Create JNDI Reference with factory class
        Reference ref = new Reference(customRes.getResType(),
                                     customRes.getFactoryClass(), null);

        // Add properties as RefAddrs
        for (ResourceProperty prop : customRes.getProperties()) {
            ref.add(new StringRefAddr(prop.getName(), (String) prop.getValue()));
        }

        // Publish to JNDI
        cns.publishObject(resourceInfo, ref, true);
    }
}
```

### Custom Resource Bean

```java
public class CustomResource extends JavaEEResourceBase {

    private String resType_;      // Resource type (e.g., "java.lang.String")
    private String factoryClass_; // ObjectFactory class name

    @Override
    public int getType() {
        return JavaEEResource.CUSTOM_RESOURCE;
    }
}
```

## ExternalJndiResourceDeployer

### External JNDI Resource Deployment

```java
@Service
@ResourceDeployerInfo(ExternalJndiResource.class)
@Singleton
public class ExternalJndiResourceDeployer implements ResourceDeployer {

    public void installExternalJndiResource(ExternalJndiResource externalJndiResource,
                                           ResourceInfo resourceInfo) {
        // Create proxy reference for external JNDI lookup
        Reference ref = new Reference(externalJndiResource.getJndiLookupName(),
                                     JndiProxyObjectFactory.class.getName(), null);

        // Add external JNDI configuration
        ref.add(new StringRefAddr(JNDI_LOOKUP_NAME,
                                  externalJndiResource.getJndiLookupName()));
        ref.add(new StringRefAddr(JNDI_CONTEXT_FACTORY,
                                  externalJndiResource.getFactoryClass()));
        ref.add(new StringRefAddr(JNDI_PROVIDER_URL,
                                  externalJndiResource.getProviderUrl()));

        // Publish to JNDI
        cns.publishObject(resourceInfo, ref, true);
    }
}
```

### External JNDI Resource Configuration

```xml
<external-jndi-resource
    jndi-name="jms/ExternalQueue"
    jndi-lookup-name="jms/MyQueue"
    factory-class="org.apache.activemq.jndi.ActiveMQInitialContextFactory"
    provider-url="tcp://localhost:61616"/>
```

## Resource Deployer Interface

```java
public interface ResourceDeployer {
    void deployResource(Object resource) throws Exception;
    void deployResource(Object resource, String applicationName, String moduleName) throws Exception;
    void undeployResource(Object resource) throws Exception;
    void undeployResource(Object resource, String applicationName, String moduleName) throws Exception;
    void enableResource(Object resource) throws Exception;
    void disableResource(Object resource) throws Exception;
    boolean handles(Object resource);
    boolean canDeploy(boolean postApplicationDeployment, Collection<Resource> allResources, Resource resource);
}
```

## Deployment Phases

```
1. DEPLOYMENT_BEFORE_CLASSLOADER_CREATION
   - Process resources.xml
   - Create resource configuration
   - Deploy resources (pre-app)

2. DEPLOYMENT (Application loaded)
   - Application can access resources

3. UNDEPLOYMENT_VALIDATION
   - Preserve resources if preserveAppScopedResources=true

4. UNDEPLOYMENT_SUCCESS
   - Clean up resources from registry

5. DEPLOYMENT_FAILURE
   - Clean up deployed resources
   - Clean up preserved resources
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `resources-connector` | Resource APIs and configuration |
| `deployment-javaee-core` | JavaEE deployer base class |
| `config-api` | Configuration beans (Resource, Resources) |
| `glassfish-api` | Public APIs |
| `resourcebase/resources` | ResourceDeployer interface, ResourceNamingService |

## Notes

- **Pre-Classloader Deployment** - Resources deployed before application classloader creation
- **App-Scoped Resources** - Automatically undeployed with application
- **Resource Preservation** - Resources can be preserved across redeployments
- **Resource Cleanup** - Automatic cleanup on deployment failure
- **Connector Resources** - Deployed by connector resource deployers
- **Bindable Resources** - Resources that support binding to application components
