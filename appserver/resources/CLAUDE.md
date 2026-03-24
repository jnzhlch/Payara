# CLAUDE.md - Resources

This file provides guidance for working with the `appserver/resources` module - JNDI resources, custom resources, mail sessions, and external JNDI.

## Module Overview

The resources module provides JNDI-based resource management for Payara Server, including custom resources, JavaMail sessions, external JNDI resources, and the infrastructure for deploying resources from application archives.

**Key Areas:**
- **Resources Runtime** - Deployers for custom and external JNDI resources
- **JavaMail** - JavaMail session resource support
- **Resources Connector** - Resource APIs, admin CLI, and configuration

## Build Commands

```bash
# Build entire resources module
mvn -DskipTests clean package -f appserver/resources/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/resources/resources-runtime/pom.xml
mvn -DskipTests clean package -f appserver/resources/javamail/pom.xml
mvn -DskipTests clean package -f appserver/resources/resources-connector/pom.xml
```

## Architecture

### Module Structure

```
resources/
├── javamail/                    # JavaMail resources
│   ├── javamail-connector/      # Admin CLI and config
│   ├── javamail-runtime/        # Runtime deployer
│   └── javamail-connector-l10n/ # Localization
├── resources-connector/          # Resource APIs and CLI
├── resources-connector-l10n/     # Localization
└── resources-runtime/            # Runtime deployers
```

## Resources Runtime

### ResourcesDeployer

Handles application-scoped resources from `glassfish-resources.xml` or `payara-resources.xml`:

```java
@Service
public class ResourcesDeployer extends JavaEEDeployer<ResourcesContainer, ResourcesApplication>
        implements PostConstruct, PreDestroy, EventListener {

    @Override
    public void event(Event event) {
        if (event.is(Deployment.DEPLOYMENT_BEFORE_CLASSLOADER_CREATION)) {
            DeploymentContext dc = (DeploymentContext) event.hook();
            processResources(dc, deployParams);
        }
    }
}
```

### Deployment Flow

```
Application Deployment
       │
       ▼
[ResourcesDeployer.processArchive()]
       │
       ├─→ Parse glassfish-resources.xml/payara-resources.xml
       ├─→ Separate connector and non-connector resources
       │
       ▼
[ResourcesDeployer.createResources()]
       │
       ├─→ Create app-scoped resources
       ├─→ Create module-scoped resources
       │
       ▼
[ResourcesDeployer.deployResources()]
       │
       ├─→ Deploy connector resources
       └─→ Deploy non-connector resources
```

### Resource Types

```java
// Resource types defined in Resource.java
public static final String CUSTOM_RESOURCE = "custom-resource";
public static final String MAIL_RESOURCE = "mail-resource";
public static final String EXTERNAL_JNDI_RESOURCE = "external-jndi-resource";
public static final String CONNECTOR_RESOURCE = "connector-resource";
public static final String JDBC_RESOURCE = "jdbc-resource";
```

### CustomResourceDeployer

Deploys custom JNDI resources with user-defined object factories:

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

### ExternalJndiResourceDeployer

Deploys external JNDI resource proxies:

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
        ref.add(new StringRefAddr(JNDI_LOOKUP_NAME, externalJndiResource.getJndiLookupName()));
        ref.add(new StringRefAddr(JNDI_CONTEXT_FACTORY, externalJndiResource.getFactoryClass()));
        ref.add(new StringRefAddr(JNDI_PROVIDER_URL, externalJndiResource.getProviderUrl()));

        // Publish to JNDI
        cns.publishObject(resourceInfo, ref, true);
    }
}
```

### Resource Scopes

Resources can be deployed at different scopes:

```
Global Resources (domain.xml)
       │
       ├─→ Available to all applications
       └─→ Configured via asadmin

App-Scoped Resources
       │
       ├─→ Available only to specific application
       ├─→ Defined in META-INF/payara-resources.xml
       └─→ Undeployed when application is undeployed

Module-Scoped Resources
       │
       ├─→ Available only to specific module (in EAR)
       ├─→ Defined in module's META-INF/payara-resources.xml
       └─→ Undeployed when module is undeployed
```

## JavaMail Support

### MailResourceDeployer

Deploys JavaMail session resources:

```java
@Service
@ResourceDeployerInfo(MailResource.class)
@Singleton
public class MailResourceDeployer extends GlobalResourceDeployer
        implements ResourceDeployer {

    public void installMailResource(MailBean mailResource, ResourceInfo resourceInfo) {
        // Create MailConfiguration
        MailConfiguration config = new MailConfiguration(mailResource);

        // Create JNDI Reference
        Reference ref = new Reference(jakarta.mail.Session.class.getName(),
                                     MailNamingObjectFactory.class.getName(), null);
        ref.add(new SerializableObjectRefAddr("jndiName", config));

        // Publish to JNDI
        namingService.publishObject(resourceInfo, ref, true);
    }
}
```

### Mail Session Configuration

```java
// MailBean configuration
MailBean mailResource = new MailBean(resourceInfo);
mailResource.setStoreProtocol("imap");
mailResource.setTransportProtocol("smtp");
mailResource.setMailHost("smtp.example.com");
mailResource.setUsername("user");
mailResource.setPassword("password");
mailResource.setMailFrom("noreply@example.com");
mailResource.setDebug(true);
mailResource.setAuth(true);
```

### @MailSessionDefinition Support

Java EE annotations for mail sessions:

```java
@MailSessionDefinition(
    name = "java:mail/MyMail",
    host = "smtp.example.com",
    user = "user",
    password = "password",
    from = "noreply@example.com"
)
@MailSessionDefinitions({
    @MailSessionDefinition(name = "java:mail/Mail1", ...),
    @MailSessionDefinition(name = "java:mail/Mail2", ...)
})
```

## Resources Connector

### ResourceFactory

Factory for creating resource managers:

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
        }
        // ... other resource types
    }
}
```

### ResourceManager Interface

Base interface for resource management:

```java
public interface ResourceManager {
    Resource createConfigBean(Resources resources, HashMap attrList,
                              Properties props, boolean validate);
    void deleteResource(Resource resource) throws Exception;
}
```

### ResourcesXMLParser

Payses `glassfish-resources.xml` and `payara-resources.xml`:

```java
public class ResourcesXMLParser {

    public List<org.glassfish.resources.api.Resource> getResourcesList() {
        // Parse XML and return list of resources
    }

    public static List<Resource> getNonConnectorResourcesList(
            List<Resource> resources, boolean... filters) {
        // Filter non-connector resources
    }

    public static List<Resource> getConnectorResourcesList(
            List<Resource> resources, boolean... filters) {
        // Filter connector resources
    }
}
```

### JndiProxyObjectFactory

JNDI ObjectFactory for external JNDI resources:

```java
public class JndiProxyObjectFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                   Hashtable<?, ?> environment) throws Exception {
        // Extract external JNDI configuration from Reference
        Reference ref = (Reference) obj;
        String jndiLookupName = extractRefAddr(ref, JNDI_LOOKUP_NAME);
        String factoryClass = extractRefAddr(ref, JNDI_CONTEXT_FACTORY);
        String providerUrl = extractRefAddr(ref, JNDI_PROVIDER_URL);

        // Create external JNDI context
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, factoryClass);
        env.put(Context.PROVIDER_URL, providerUrl);

        Context ctx = new InitialContext(env);
        return ctx.lookup(jndiLookupName);
    }
}
```

## Package Structure

```
appserver/resources/
├── resources-runtime/
│   └── src/main/java/org/glassfish/resources/
│       ├── deployer/
│       │   ├── CustomResourceDeployer.java
│       │   ├── ExternalJndiResourceDeployer.java
│       │   └── GlobalResourceDeployer.java
│       ├── module/
│       │   ├── ResourcesDeployer.java
│       │   ├── ResourcesContainer.java
│       │   ├── ResourcesApplication.java
│       │   └── ResourceUtilities.java
│       └── beans/
│           ├── CustomResource.java
│           └── ExternalJndiResource.java
│
├── javamail/javamail-runtime/
│   └── src/main/java/org/glassfish/resources/javamail/
│       ├── deployer/
│       │   └── MailResourceDeployer.java
│       ├── beans/
│       │   └── MailBean.java
│       ├── naming/
│       │   └── MailNamingObjectFactory.java
│       └── annotation/handler/
│           ├── MailSessionDefinitionHandler.java
│           └── MailSessionDefinitionsHandler.java
│
├── javamail/javamail-connector/
│   └── src/main/java/org/glassfish/resources/javamail/
│       ├── admin/cli/
│       │   ├── CreateJavaMailResource.java
│       │   ├── DeleteJavaMailResource.java
│       │   ├── ListJavaMailResources.java
│       │   └── JavaMailResourceManager.java
│       ├── config/
│       │   └── MailResource.java
│       └── MailSessionAuthenticator.java
│
└── resources-connector/
    └── src/main/java/org/glassfish/resources/
        ├── admin/cli/
        │   ├── AddResources.java
        │   ├── CreateCustomResource.java
        │   ├── CreateJndiResource.java
        │   ├── ResourceFactory.java
        │   └── ResourcesXMLParser.java
        ├── api/
        │   ├── JavaEEResource.java
        │   ├── JavaEEResourceBase.java
        │   ├── Resource.java
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

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `dol` | Deployment Object Library |
| `deployment-javaee-core` | Java EE deployment integration |
| `config-api` | Configuration beans |
| `glassfish-api` | Public APIs |
| `resourcebase/resources` | Base resource infrastructure |

## Notes

- **JNDI Resources** - Core JNDI-based resource management
- **App-Scoped Resources** - Resources bundled in application archives
- **Custom Resources** - User-defined resources with ObjectFactory
- **External JNDI** - Proxy to external JNDI sources
- **JavaMail** - Jakarta Mail session support
- **Preserve Resources** - Resources can be preserved across redeployments
- **Resource Conflict Detection** - Validates resource uniqueness
