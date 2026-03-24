# CLAUDE.md - Weld Integration

This file provides guidance for working with the `appserver/web/weld-integration` module - CDI (Weld) integration with Payara Server.

## Module Overview

The weld-integration module provides the integration between Weld (CDI reference implementation) and Payara Server. It handles CDI deployment, bean discovery, and services integration (EJB, JTA, JPA, etc.).

**Key Components:**
- **WeldDeployer** - Deploys CDI containers
- **WeldContainer** - CDI container integration
- **BeanDeploymentArchiveImpl** - Bean discovery
- **CDI Extensions** - Transaction, persistence, HK2 integration

## Build Commands

```bash
# Build weld-integration module
mvn -DskipTests clean package -f appserver/web/weld-integration/pom.xml
```

## Module Contents

```
weld-integration/
└── src/main/java/org/glassfish/
    ├── weld/
    │   ├── WeldDeployer.java
    │   ├── WeldContainer.java
    │   ├── BeanDeploymentArchiveImpl.java
    │   ├── DeploymentImpl.java
    │   └── services/         # Weld services implementation
    │       ├── InjectionServicesImpl.java
    │       ├── EjbServicesImpl.java
    │       ├── TransactionServicesImpl.java
    │       ├── ProxyServicesImpl.java
    │       └── SecurityServicesImpl.java
    ├── cdi/
    │   ├── transaction/      # @Transactional and @TransactionScoped
    │   ├── persistence/      # CDI persistence extension
    │   └── hk2/             # HK2 integration
    └── weld/jsf/            # JSF integration
```

## WeldDeployer

### Deployment Process

```java
@Service
public class WeldDeployer extends AbstractDeployer<WeldContainer, WeldApplicationContainer> {

    @Override
    public boolean prepare(DeploymentContext context) {
        // Check for beans.xml
        if (!hasBeansXml(context)) {
            return false;
        }

        // Scan for CDI-enabled archives
        List<BeanDeploymentArchive> archives = scanBeanArchives(context);

        // Create Weld deployment
        DeploymentImpl deployment = new DeploymentImpl(archives, context);

        // Store in context
        context.addTransientAppMetaData(DeploymentImpl.class.getName(), deployment);

        return true;
    }

    @Override
    public WeldApplicationContainer load(WeldContainer container, DeploymentContext context) {
        // Get Weld deployment
        DeploymentImpl deployment = context.getTransientAppMetaData(
            DeploymentImpl.class.getName());

        // Bootstrap Weld
        WeldBootstrap bootstrap = new WeldBootstrap();
        bootstrap.startContainer(context.getAppName(), Environments.SERVLET, deployment);
        bootstrap.startInitialization();
        bootstrap.deployBeans();
        bootstrap.validateBeans();
        bootstrap.endInitialization();

        // Create application container
        WeldApplicationContainer appContainer = new WeldApplicationContainer(
            bootstrap, deployment, context);

        return appContainer;
    }

    @Override
    public void unload(WeldApplicationContainer appContainer, DeploymentContext context) {
        // Shutdown Weld
        appContainer.getBootstrap().shutdown();
    }

    private boolean hasBeansXml(DeploymentContext context) {
        // Check for META-INF/beans.xml or WEB-INF/beans.xml
        return context.getSource().exists("META-INF/beans.xml") ||
               context.getSource().exists("WEB-INF/beans.xml");
    }
}
```

## BeanDeploymentArchiveImpl

```java
public class BeanDeploymentArchiveImpl implements BeanDeploymentArchive {

    private final String id;
    private final Set<Class<?>> beanClasses;
    private final Set<URL> beansXmlUrls;
    private final List<URL> moduleUrls;

    public BeanDeploymentArchiveImpl(ReadableArchive archive, String id) {
        this.id = id;

        // Scan for bean classes
        this.beanClasses = scanBeanClasses(archive);

        // Find beans.xml
        this.beansXmlUrls = findBeansXml(archive);

        // Get module URLs (JARs, directories)
        this.moduleUrls = getModuleUrls(archive);
    }

    @Override
    public Collection<Class<?>> getBeanClasses() {
        return beanClasses;
    }

    @Override
    public Collection<URL> getBeansXmlUrls() {
        return beansXmlUrls;
    }

    @Override
    public Iterable<URL> getModuleUrls() {
        return moduleUrls;
    }

    @Override
    public String getId() {
        return id;
    }
}
```

## Weld Services

### InjectionServicesImpl

```java
@Service
public class InjectionServicesImpl implements InjectionServices {

    @Inject
    private Habitat habitat;

    @Override
    public <T> T aroundInject(InjectionTarget<T> injectionTarget) {
        return new InjectionTargetWrapper<T>(injectionTarget) {
            @Override
            public void inject(T instance, CreationalContext<T> ctx) {
                // First, let CDI inject
                super.inject(instance, ctx);

                // Then, let HK2 inject (@Inject with HK2)
                hk2Inject(instance);
            }
        };
    }

    @Override
    public <T> EnhancedAnnotatedType<T> getEnhancedAnnotatedType(Class<T> type) {
        // Get enhanced type for CDI processing
    }

    @Override
    public InjectableMethod<Object> getInjectableMethod(Method method) {
        // Get injectable method for HK2 integration
    }

    private <T> void hk2Inject(T instance) {
        // Use HK2 ServiceLocator to inject
        ServiceLocator locator = habitat.getServiceLocator();
        locator.inject(instance);
    }
}
```

### EjbServicesImpl

```java
@Service
public class EjbServicesImpl implements EjbServices {

    @Inject
    private EjbContainer ejbContainer;

    @Override
    public Object resolveEjb(EjbDescriptor ejbDescriptor) {
        // Resolve EJB reference
        String jndiName = getJndiName(ejbDescriptor);

        try {
            return new InitialContext().lookup(jndiName);
        } catch (NamingException e) {
            throw new EjbException(e);
        }
    }

    @Override
    public void registerInterceptors(InterceptorResolutionService interceptorResolutionService) {
        // Register CDI interceptors for EJBs
    }

    private String getJndiName(EjbDescriptor descriptor) {
        // Return portable JNDI name
        return "java:global/" + descriptor.getApplicationName() + "/" +
               descriptor.getModuleName() + "/" + descriptor.getBeanName();
    }
}
```

### TransactionServicesImpl

```java
@Service
public class TransactionServicesImpl implements TransactionServices {

    @Inject
    private TransactionManager transactionManager;

    @Override
    public void registerSynchronization(Synchronization synchronization) {
        try {
            Transaction tx = transactionManager.getTransaction();
            if (tx != null) {
                tx.registerSynchronization(synchronization);
            }
        } catch (Exception e) {
            throw new TransactionException(e);
        }
    }

    @Override
    public boolean isTransactionActive() {
        try {
            return transactionManager.getStatus() == Status.STATUS_ACTIVE;
        } catch (SystemException e) {
            return false;
        }
    }

    @Override
    public UserTransaction getUserTransaction() {
        return com.sun.enterprise.transaction.api.JavaEETransactionManager.Simplified.getUserTransaction();
    }
}
```

## @Transactional Extension

### TransactionalExtension

```java
public class TransactionalExtension implements Extension {

    public void processAnnotatedType(@Observes ProcessAnnotatedType<?> event) {
        // Process @Transactional annotation
        AnnotatedType<?> annotatedType = event.getAnnotatedType();

        if (hasTransactionalAnnotation(annotatedType)) {
            // Add interceptor binding
            AnnotatedTypeBuilder<?> builder = new AnnotatedTypeBuilder<>(annotatedType);
            builder.add(new TransactionalInterceptorBinding());
            event.setAnnotatedType(builder.build());
        }
    }

    private boolean hasTransactionalAnnotation(AnnotatedType<?> type) {
        return type.isAnnotationPresent(Transactional.class) ||
               hasMethodWithTransactional(type);
    }
}
```

### Transactional Interceptors

```java
@Interceptor
@Transactional(Transactional.TxType.REQUIRED)
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
public class TransactionalInterceptorRequired extends TransactionalInterceptorBase {

    @Override
    protected Object intercept(Transactional transactional,
                              InvocationContext ctx) throws Exception {
        UserTransaction ut = getUserTransaction();

        // Check if transaction exists
        if (ut.getStatus() == Status.STATUS_NO_TRANSACTION) {
            ut.begin();
            return proceedAndCommit(ut, ctx);
        } else {
            // Use existing transaction
            return ctx.proceed();
        }
    }
}

@Interceptor
@Transactional(Transactional.TxType.REQUIRES_NEW)
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
public class TransactionalInterceptorRequiresNew extends TransactionalInterceptorBase {

    @Override
    protected Object intercept(Transactional transactional,
                              InvocationContext ctx) throws Exception {
        UserTransaction ut = getUserTransaction();

        // Always start new transaction
        ut.begin();
        return proceedAndCommit(ut, ctx);
    }
}
```

## @TransactionScoped Extension

### TransactionScopedContextImpl

```java
public class TransactionScopedContextImpl extends AbstractMapContext
        implements PassivatingContext<TransactionScopedContextImpl> {

    private final TransactionServices transactionServices;

    @Override
    protected boolean isActive() {
        return transactionServices.isTransactionActive();
    }

    @Override
    protected <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        Transaction transaction = getCurrentTransaction();

        // Get or create bean for this transaction
        Map<Object, Object> transactionStore = getTransactionStore(transaction);
        Object bean = transactionStore.get(contextual);

        if (bean == null) {
            bean = contextual.create(creationalContext);
            transactionStore.put(contextual, bean);
        }

        return (T) bean;
    }

    private Map<Object, Object> getTransactionStore(Transaction transaction) {
        // Get/store bean map per transaction
        return transaction.getResourceMap();
    }
}
```

### TransactionScopedCDIEventHelper

```java
public class TransactionScopedCDIEventHelperImpl implements TransactionScopedCDIEventHelper {

    public void fireTransactionEvents(Transaction tx, TransactionEventType type) {
        BeanManager beanManager = getBeanManager();

        // Fire transaction events
        beanManager.fireEvent(new TransactionEvent(tx, type));

        // Cleanup transaction-scoped beans on completion
        if (type == TransactionEventType.COMPLETED ||
            type == TransactionEventType.ROLLED_BACK) {
            cleanupTransactionScopedBeans(tx);
        }
    }
}
```

## Persistence Extension

```java
public class PersistenceExtension implements Extension {

    public void processAnnotatedType(@Observes ProcessAnnotatedType<?> event) {
        // Process @PersistenceUnit and @PersistenceContext
    }

    public void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager bm) {
        // Register EntityManager and EntityManagerFactory producers
    }
}

public class EntityManagerProducer {

    @PersistenceUnit
    @Produces
    @ConversationScoped
    private EntityManagerFactory emf;

    @PersistenceContext
    @Produces
    @RequestScoped
    private EntityManager em;
}
```

## HK2 Integration

### HK2IntegrationExtension

```java
@Extension
public class HK2IntegrationExtension implements Extension {

    public void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager bm) {
        // Integrate HK2 services with CDI
        HK2ContextBridge bridge = new HK2ContextBridge(bm);
        abd.addContext(bridge);
    }

    public void processInjectionTarget(@Observes ProcessInjectionTarget<?> event) {
        // Wrap injection target for HK2 integration
        InjectionTarget<?> original = event.getInjectionTarget();
        HK2InjectionTargetWrapper wrapper = new HK2InjectionTargetWrapper(original);
        event.setInjectionTarget(wrapper);
    }
}
```

## JSF Integration

### WeldApplicationFactory

```java
public class WeldApplicationFactory extends ApplicationFactory {

    private ApplicationFactory parentFactory;

    @Override
    public Application getApplication() throws FacesException {
        Application app = parentFactory.getApplication();

        // Wrap for CDI injection support
        return new WeldApplication(app);
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `weld` | CDI implementation |
| `cdi-api` | CDI API |
| `ejb` | EJB integration |
| `transaction-internal-api` | JTA integration |
| `jpa` | JPA integration |
| `hk2` | HK2 integration |

## Notes

- **CDI 4.0** - Jakarta CDI 4.0 support
- **Bean Discovery** - Automatic bean discovery via beans.xml
- **@Transactional** - Declarative transaction control
- **@TransactionScoped** - Transaction-scoped beans
- **EJB Integration** - @EJB injection in CDI beans
- **JPA Integration** - @PersistenceUnit/@PersistenceContext producers
- **HK2 Bridge** - HK2 and CDI context bridging
