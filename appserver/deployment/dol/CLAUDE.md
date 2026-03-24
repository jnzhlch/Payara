# CLAUDE.md - Deployment Object Library

This file provides guidance for working with the `dol` module - Deployment Object Library (DOL).

## Module Overview

The Deployment Object Library (DOL) provides the in-memory representation of Jakarta EE application deployment descriptors. It parses XML descriptors and annotations, builds metadata objects, and provides validation for all application types.

## Build Commands

```bash
# Build DOL module
mvn -DskipTests clean package -f appserver/deployment/dol/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Name**: "Deployment Object Library"
- **Purpose**: Application metadata descriptors

## Core Descriptors

### Root Descriptors

```
RootDeploymentDescriptor (Abstract)
        │
        ├── Application (EAR root)
        │
        └── BundleDescriptor (Module base)
                │
                ├── WebBundleDescriptor
                ├── EjbBundleDescriptor
                ├── ApplicationClientDescriptor
                └── ConnectorDescriptor
```

### Application Descriptor

The root descriptor for EAR files:

```java
public class Application extends CommonResourceBundleDescriptor {
    // Modules in this application
    private Set<ModuleDescriptor<BundleDescriptor>> modules;

    // Registration name (unique deployment name)
    private String registrationName;

    // Library directory (default: "lib")
    private String libraryDirectory;

    // Initialize modules in order
    private boolean initializeInOrder;

    // Unique ID for EJB optimizations
    private long uniqueId;

    // Virtual = standalone module wrapper
    private boolean virtual;

    // Security role mapping
    private SecurityRoleMapper roleMapper;

    // Entity manager factories
    private Map<String, EntityManagerFactory> entityManagerFactories;
}
```

**Key Methods:**
- `addModule(ModuleDescriptor)` - Add module to application
- `getModuleByUri(String)` - Lookup module by URI
- `getEjbByName(String)` - Find EJB by name
- `getBundleDescriptors(Class<T>)` - Get modules by type
- `setUniqueId(long)` - Set application unique ID

### BundleDescriptor

Abstract base for all modules:

```java
public abstract class BundleDescriptor extends RootDeploymentDescriptor {
    // Parent application
    private Application application;

    // Security roles
    private Set<Role> roles;

    // Message destinations
    private Set<MessageDestinationDescriptor> messageDestinations;

    // Web services
    private WebServicesDescriptor webServices;

    // Managed beans
    private Set<ManagedBeanDescriptor> managedBeans;

    // Entity manager factories (module level)
    private Map<String, EntityManagerFactory> entityManagerFactories;

    // Compatibility settings
    private String compatValue;

    // Keep state flag
    private boolean keepState;
}
```

### WebBundleDescriptor

Web application metadata:

```java
public abstract class WebBundleDescriptor extends CommonResourceBundleDescriptor {
    // Context root
    private String contextRoot;

    // Web components (servlets, filters, etc.)
    private Set<WebComponentDescriptor> webComponentDescriptors;

    // Session configuration
    private SessionConfig sessionConfig;

    // JSP configuration
    private JspConfigDescriptor jspConfig;

    // Character encoding
    private String requestCharacterEncoding;
    private String responseCharacterEncoding;

    // Conflict detection flags
    private boolean conflictLoginConfig;
    private boolean conflictDataSourceDefinition;
    // ... more conflict flags
}
```

### EjbBundleDescriptor

EJB module metadata:

```java
public abstract class EjbBundleDescriptor extends CommonResourceBundleDescriptor {
    // Interceptors
    public abstract Set<EjbInterceptor> getInterceptors();

    // EJBs in this module
    public abstract Set<? extends EjbDescriptor> getEjbs();

    // Find EJB by name
    public abstract EjbDescriptor getEjbByName(String name);

    // EJB service references
    public abstract Set<ServiceReferenceDescriptor> getEjbServiceReferenceDescriptors();

    // Disable non-portable JNDI names
    public abstract Boolean getDisableNonportableJndiNames();
}
```

### EjbDescriptor

Individual EJB metadata:

```java
public abstract class EjbDescriptor extends Descriptor {
    // EJB name
    private String name;

    // EJB type (SESSION, ENTITY, MESSAGE)
    private EjbSessionDescriptor.SessionType type;

    // Home/Remote interfaces
    private String homeClassName;
    private String remoteClassName;
    private String localHomeClassName;
    private String localClassName;

    // Business interfaces
    private Set<String> businessInterfaces;

    // Transaction type
    private String transactionType;

    // JNDI names
    private String jndiName;

    // Unique ID (for pass-by-reference optimization)
    private long uniqueId;

    // Interceptors
    private Set<EjbInterceptor> interceptorChain;

    // Security
    private MethodPermission[] methodPermissions;
}
```

### ConnectorDescriptor

Resource adapter metadata:

```java
public class ConnectorDescriptor extends BundleDescriptor {
    // Resource adapter version
    private String resourceAdapterVersion;

    // Vendor name
    private String vendorName;

    // Outbound resource adapter
    private OutboundResourceAdapter outboundResourceAdapter;

    // Inbound resource adapter
    private InboundResourceAdapter inboundResourceAdapter;

    // Admin objects
    private Set<AdminObject> adminObjects;

    // Required work context
    private Set<String> requiredWorkContext;

    // Security permissions
    private PermissionsDescriptor permissionConfigs;
}
```

## Descriptor Relationships

### Module to Application

```
Application (EAR)
       │
       ├── ModuleDescriptor<WebBundleDescriptor>
       │       └── web-module.war
       │
       ├── ModuleDescriptor<EjbBundleDescriptor>
       │       └── ejb-module.jar
       │
       └── ModuleDescriptor<ConnectorDescriptor>
               └── rar-module.rar
```

### Reference Hierarchy

```
Application
       │
       ├── EjbReferences (app-level)
       ├── ResourceReferences
       └── ServiceReferences
               │
       └── BundleDescriptor
               │
               ├── EjbReferences (module-level)
               ├── ResourceReferences
               └── ServiceReferences
```

## JNDI Environment

### JndiNameEnvironment

Interface for components with JNDI resources:

```java
public interface JndiNameEnvironment {
    // Environment entries
    Set<EnvironmentProperty> getEnvironmentProperties();

    // EJB references
    Set<EjbReference> getEjbReferenceDescriptors();

    // Resource references
    Set<ResourceReferenceDescriptor> getResourceReferenceDescriptors();

    // Resource environment references
    Set<ResourceEnvReferenceDescriptor> getResourceEnvReferenceDescriptors();

    // Service references
    Set<ServiceReferenceDescriptor> getServiceReferenceDescriptors();

    // Persistence references
    Set<EntityManagerFactoryReferenceDescriptor> getEntityManagerFactoryReferenceDescriptors();
    Set<EntityManagerReferenceDescriptor> getEntityManagerReferenceDescriptors();
}
```

## Resource Descriptors

### ResourceReferenceDescriptor

```java
public class ResourceReferenceDescriptor extends EnvironmentProperty {
    // Resource type (DataSource, JMS, etc.)
    private String type;

    // Authentication (APPLICATION, CONTAINER)
    private String authentication;

    // Sharing scope (Shareable, Unshareable)
    private String sharingScope;

    // JNDI name of linked resource
    private String jndiName;
}
```

### EjbReferenceDescriptor

```java
public class EjbReferenceDescriptor extends EnvironmentProperty {
    // Home interface
    private String homeClassName;

    // Remote/local interface
    private String interfaceClassName;

    // EJB link (to another EJB in same app)
    private String linkName;

    // JNDI lookup name
    private String jndiName;

    // Reference type (SESSION, ENTITY)
    private String type;
}
```

## Security Descriptors

### Role Mapping

```java
public interface SecurityRoleMapper {
    void setName(String appName);

    // Map role to principal/group
    void assignRole(String roleName, Principal p);
    void assignRole(String roleName, Group g);

    // Get principal/group for role
    List<Principal> getPrincipalsByRole(String roleName);
    List<Group> getGroupsByRole(String roleName);
}
```

### Permission Descriptors

```java
public class PermissionsDescriptor {
    private Set<PermissionItemDescriptor> permissionItems;

    public void addPermission(PermissionItemDescriptor permission);
    public Set<PermissionItemDescriptor> getPermissionItems();
}

public class PermissionItemDescriptor {
    private String action;  // e.g., "read,write"
    private String name;    // e.g., "file:/tmp/*"
    private String className; // e.g., "java.io.FilePermission"
}
```

## Web Components

### WebComponentDescriptor

```java
public class WebComponentDescriptor {
    // Servlet name
    private String canonicalName;

    // Servlet class
    private String webComponentImplementation;

    // JSP file (if JSP)
    private String jspFile;

    // URL mappings
    private Set<String> urlPatterns;

    // Init parameters
    private Set<EnvironmentProperty> initializationParameters;

    // Async supported
    private boolean isAsyncSupported;

    // Load on startup
    private int loadOnStartUp;
}
```

## Persistence Descriptors

### PersistenceUnitDescriptor

```java
public class PersistenceUnitDescriptor {
    // PU name
    private String name;

    // Persistence provider
    private String provider;

    // JTA vs resource-local
    private String transactionType;

    // DataSource JNDI
    private String jtaDataSource;
    private String nonJtaDataSource;

    // Entity classes
    private Set<String> classes;

    // Mapping files
    private List<String> mappingFiles;

    // Shared cache mode
    private String sharedCacheMode;

    // Validation mode
    private String validationMode;

    // Properties
    private Properties properties;
}
```

## Service References

### ServiceReferenceDescriptor

```java
public class ServiceReferenceDescriptor extends EnvironmentProperty {
    // WSDL location
    private String wsdlFileLocation;

    // Service interface
    private String serviceInterface;

    // SEI (Service Endpoint Interface)
    private String serviceEndpointInterface;

    // Port QNames
    private Set<QName> ports;

    // Handler chain
    private List<HandlerChain> handlerChain;

    // MTOM enabled
    private boolean mtomEnabled;

    // WSDL publish location
    private String wsdlPublishLocation;
}
```

## Lifecycle Callbacks

### LifecycleCallbackDescriptor

```java
public class LifecycleCallbackDescriptor {
    // Callback type (PostConstruct, PreDestroy, etc.)
    private String lifecycleCallbackType;

    // Method name
    private String lifecycleCallbackMethod;

    // Class containing the method
    private String lifecycleCallbackClass;

    // Injection targets
    private Set<InjectionTarget> injectionTargets;
}
```

## Annotation Processing

### MetadataSource

```java
public enum MetadataSource {
    XML,            // From deployment descriptor
    ANNOTATION,     // From annotation
    RUNTIME         // From runtime API
}
```

### Annotation Scanning

DOL processes annotations through:
1. **Class scanning** - Scan classes for annotations
2. **Descriptor building** - Build descriptors from annotations
3. **Merging** - Merge with XML descriptors
4. **Validation** - Validate complete descriptor

## Descriptor Visitors

### Visitor Pattern

```java
public interface DescriptorVisitor {
    void visit(Application app);
    void visit(BundleDescriptor bundle);
    void visit(WebBundleDescriptor webBundle);
    void visit(EjbDescriptor ejb);
    // ... more visit methods
}
```

### Common Visitors

| Visitor | Purpose |
|---------|---------|
| `ApplicationValidator` | Validate application |
| `ComponentVisitor` | Visit individual components |
| `DescriptorArchivist` | Read/write descriptors |

## Descriptor Utilities

### DOLUtils

```java
public class DOLUtils {
    // Get archive type
    public static ArchiveType earType();
    public static ArchiveType warType();
    public static ArchiveType ejbType();
    public static ArchiveType rarType();
    public static ArchiveType carType();

    // Convert descriptor
    public static ArchiveType getModuleType(BundleDescriptor bundle);

    // Logger
    public static Logger getDefaultLogger();
}
```

## Package Structure

```
com.sun.enterprise.deployment/
├── Application.java                         # EAR root descriptor
├── BundleDescriptor.java                    # Module base descriptor
├── WebBundleDescriptor.java                 # Web app descriptor
├── EjbBundleDescriptor.java                 # EJB module descriptor
├── EjbDescriptor.java                       # Individual EJB
├── EjbSessionDescriptor.java                # Session EJB
├── EjbMessageBeanDescriptor.java            # MDB
├── ApplicationClientDescriptor.java         # App client descriptor
├── ConnectorDescriptor.java                 # RAR descriptor
├── CommonResourceBundleDescriptor.java      # Common base
├── DescriptorConstants.java                 # Constants
├── roles/                                   # Security roles
├── types/                                   # Type interfaces
├── util/                                    # Utilities
├── node/                                    # XML parsing nodes
└── archivist/                               # Descriptor readers/writers
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | Public APIs |
| `internal-api` | Internal server APIs |
| `deployment-common` | Shared deployment utilities |
| `config-api` | Configuration |
| `jakarta.annotation-api` | Annotation support |
| `jakarta.ejb-api` | EJB annotations |
| `jakarta.servlet-api` | Servlet annotations |
| `jakarta.jms-api` | JMS annotations |

## Related Modules

- `deployment-javaee-core` - DOL provider service
- `deployment-client` - Deployment client
- `appserver/web` - Web container
- `appserver/ejb` - EJB container
