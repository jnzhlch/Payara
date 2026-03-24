# CLAUDE.md - HK2 Config

This file provides guidance for working with the `hk2-config` module - core HK2 configuration framework.

## Module Overview

The `hk2-config` module provides the core HK2 configuration framework that enables type-safe, transaction-based configuration management. This is the foundation for Payara's domain.xml configuration system.

## Build Commands

```bash
# Build hk2-config module
mvn -DskipTests clean package -f nucleus/hk2/hk2-config/pom.xml

# Build with tests
mvn clean package -f nucleus/hk2/hk2-config/pom.xml
```

## Architecture

### Configuration Layer

```
┌─────────────────────────────────────────────────────────────┐
│                     @Configured Interfaces                  │
│  (User-defined interfaces with @Element and @Attribute)      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    ConfigBeanProxy                          │
│              (Base interface for all config)                 │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       ConfigBean                            │
│              (Implementation via dynamic proxy)              │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                          Dom                                │
│              (Underlying configuration tree node)            │
└─────────────────────────────────────────────────────────────┘
```

### Transaction Flow

```
ConfigSupport.apply(code, configBean)
        │
        ├─→ Begin Transaction
        │      └─→ Acquire lock
        │
        ├─→ Execute ConfigCode.run()
        │      ├─→ Apply changes to config beans
        │      └─→ Validate (Bean Validation)
        │
        ├─→ Commit
        │      ├─→ Write to domain.xml
        │      ├─→ Notify ConfigListener instances
        │      └─→ Release lock
        │
        └─→ OR Rollback on exception
```

## Core Annotations

### @Configured

Marks an interface as a configuration bean that maps to domain.xml:

```java
@Configured
public interface ServerConfig extends ConfigBeanProxy {
    String getConfigFile();

    @Attribute(defaultValue = "false")
    boolean isReadOnly();

    @Element
    List<Property> getProperty();
}
```

**Properties:**
- `name()` - XML element name (default: inferred from class name)
- `local()` - Whether element is locally scoped

### @Attribute

Maps to an XML attribute:

```java
@Attribute(defaultValue = "8080")
@Min(value = 1)
@Max(value = 65535)
String getPort();

void setPort(String port);
```

### @Element

Maps to child XML elements:

```java
@Element(required = true)
List<Protocol> getProtocols();

@Element("*")
List<ConfigBeanProxy> getChildren();  // Wildcard accepts any type
```

### @DuckTyped

Enables duck-typed methods (methods implemented by the framework):

```java
@DuckTyped
default Protocol findProtocol(String name) {
    return getProtocols().stream()
        .filter(p -> name.equals(p.getName()))
        .findFirst().orElse(null);
}
```

## ConfigBeanProxy

### Base Interface

All configuration interfaces extend `ConfigBeanProxy`:

```java
@Configured
public interface MyConfig extends ConfigBeanProxy {
    // configuration methods
}
```

### Duck-Typed Methods

```java
// Get parent element
ConfigBeanProxy parent = config.getParent();

// Get typed parent
Http parentHttp = config.getParent(Http.class);

// Create child element
Ssl ssl = config.createChild(Ssl.class);

// Get underlying Dom
Dom dom = Dom.unwrap(config);

// Get proxy controller (for HK2 internals)
ProxyCtl controller = ((ProxyCtl) Proxy.getInvocationHandler(config));
```

## Transaction Management

### ConfigSupport

Static utility for configuration changes:

```java
// Single config object
ConfigSupport.apply(new SingleConfigCode<HttpListener>() {
    @Override
    public Object run(HttpListener listener) throws PropertyVetoException {
        listener.setPort("9090");
        return null;
    }
}, httpListener);

// Multiple config objects
ConfigSupport.apply(new ConfigCode() {
    @Override
    public Object run(ConfigBeanProxy... objects) throws TransactionFailure {
        HttpListener listener = (HttpListener) objects[0];
        NetworkConfig config = (NetworkConfig) objects[1];

        listener.setPort("9090");
        return null;
    }
}, listener, networkConfig);
```

### Transaction Directly

```java
@Inject
private Transactions transactions;

public void changeConfig() throws TransactionFailure {
    Transaction tx = transactions.beginTransaction();
    try {
        HttpListener listener = ...;
        listener.setPort("9090");

        PropertyChangeEvent[] events = tx.commit();
    } catch (Exception e) {
        tx.rollback();
        throw new TransactionFailure(e);
    }
}
```

## Configuration Listeners

### ConfigListener

Receives configuration change events:

```java
@Service
public class MyConfigListener implements ConfigListener {

    @Override
    public UnprocessedChangeEvents changed(PropertyChangeEvent[] events) {
        List<UnprocessedChangeEvent> unprocessed = new ArrayList<>();

        for (PropertyChangeEvent event : events) {
            if (event.getSource() instanceof HttpListener) {
                try {
                    handleHttpListenerChange(event);
                } catch (Exception e) {
                    // Return for retry later
                    unprocessed.add(new UnprocessedChangeEvent(event, e.getMessage()));
                }
            }
        }

        if (!unprocessed.isEmpty()) {
            return new UnprocessedChangeEvents(unprocessed);
        }
        return null;
    }

    private void handleHttpListenerChange(PropertyChangeEvent event) {
        String prop = event.getPropertyName();
        if ("port".equals(prop)) {
            String oldPort = (String) event.getOldValue();
            String newPort = (String) event.getNewValue();
            // Restart listener with new port
        }
    }
}
```

### TransactionListener

```java
@Service
public class MyTransactionListener implements TransactionListener {

    @Override
    public void transactionCommited(List<PropertyChangeEvent> changes) {
        // All changes committed successfully
    }

    @Override
    public void transactionRolledback(List<PropertyChangeEvent> changes) {
        // Transaction was rolled back
    }

    @Override
    public void unprocessedTransactedEvents(List<UnprocessedChangeEvents> events) {
        // Some events couldn't be processed, will retry
    }
}
```

## Dom and ConfigBean

### Working with Dom

```java
// Unwrap proxy to get Dom
Dom dom = Dom.unwrap(configBean);

// Get element name
String elementName = dom.getLeafElement();

// Get attributes
String attrValue = dom.getAttribute("port");

// Get parent
Dom parent = dom.parent();

// Get children
List<Dom> children = dom.nodeElements();

// Get location in XML
String location = dom.getLocation();
```

### ConfigBean

```java
// Get service locator
ServiceLocator habitat = configBean.getHabitat();

// Get proxy
ConfigBeanProxy proxy = dom.getProxy(configBeanType);

// Get underlying config bean
ConfigBean bean = (ConfigBean) Dom.unwrap(configProxy);

// Get raw value
Object value = dom.getRawAttributeValue("port");
```

## Configuration Reading/Writing

### ConfigurationPopulator

Read configuration from XML:

```java
@Inject
private ConfigurationPopulator populator;

public void readConfig(File configFile) throws Exception {
    DomDocument doc = ...;
    populator.populate(configFile, doc);
    Dom root = doc.getRoot();
}
```

### Writing Configuration

```java
// Write to XML file
DomDocument doc = dom.getDocument();
File targetFile = new File("domain.xml");
try (OutputStream os = new FileOutputStream(targetFile)) {
    doc.writeTo(os);
}
```

## Bean Validation

### Jakarta Validation Integration

```java
@Configured
public interface MyConfig extends ConfigBeanProxy {

    @NotNull
    @Size(min = 1, max = 50)
    @Attribute
    String getName();

    @Min(1)
    @Max(65535)
    @Attribute(defaultValue = "8080")
    int getPort();

    @Pattern(regexp = "[a-z]+")
    @Attribute
    String getMode();
}
```

### Custom Validation

```java
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PortValidator.class)
public @interface ValidPort {
    String message() default "Invalid port";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class PortValidator implements ConstraintValidator<ValidPort, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        try {
            int port = Integer.parseInt(value);
            return port > 0 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
```

## Injection and Resolvers

### InjectionTarget

```java
// For custom injection of config values
@Service
public class MyInjectionResolver implements InjectionResolver<String> {

    @Override
    public String resolve(InjectionTarget<String> target) {
        // Resolve injected value
        String value = target.getInjectedValue();
        return resolveValue(value);
    }
}
```

### VariableResolver

```java
// Resolve ${system.property} variables
@Service
public class MyVariableResolver implements VariableResolver {

    @Override
    public String resolve(String variable) {
        if (variable.startsWith("env.")) {
            return System.getenv(variable.substring(4));
        }
        return System.getProperty(variable);
    }
}
```

## Error Handling

### TransactionFailure

```java
try {
    ConfigSupport.apply(code, config);
} catch (TransactionFailure e) {
    // Transaction failed
    Throwable cause = e.getCause();
    if (cause instanceof PropertyVetoException) {
        // Validation failed
    }
}
```

### RetryableException

```java
// Throw for retry
throw new RetryableException("Temporary failure, will retry");
```

## Package Structure

| Package | Purpose |
|---------|---------|
| `org.jvnet.hk2.config` | Core config framework |
| `org.jvnet.hk2.config.provider` | Provider implementations |
| `org.jvnet.hk2.config.provider.internal` | Internal provider code |

## Related Modules

- **config-generator** - Code generation for ConfigInjector
- **config-types** - Common config types (Property, PropertyBag)
- **admin/config-api** - Config API integration

## Common Patterns

### 1. Read-Only Config View

```java
// Outside transaction, config is read-only
@Service
public class MyService {
    @Inject
    private MyConfig config;

    public void printConfig() {
        System.out.println(config.getName());  // OK
        config.setName("new");  // throws PropertyVetoException
    }
}
```

### 2. Lock Timeout Configuration

```java
// Set lock timeout (default 3 seconds)
System.setProperty("org.glassfish.hk2.config.locktimeout", "10");

// Or via annotation
@Configured
public interface MyConfig extends ConfigBeanProxy {
    @Units(Units.TimeUnit.SECONDS)
    @Attribute(defaultValue = "5")
    int getLockTimeout();
}
```

### 3. Config Extension Methods

```java
@Configured
public interface MyConfig extends ConfigBeanProxy {

    @ConfigExtensionMethod
    default String getComputedValue() {
        // Custom method computed from other attributes
        return getName() + "-" + getPort();
    }
}
```

## Troubleshooting

### Transaction Timeout

```java
// Increase lock timeout
ConfigSupport.lockTimeOutInSeconds = 10;
```

### Config Not Updating

```java
// Ensure transaction commits
try {
    ConfigSupport.apply(code, config);
} catch (TransactionFailure e) {
    // Check for validation errors
    e.printStackTrace();
}
```

### Proxy Issues

```java
// Always work with proxy, not unwrap
ConfigBeanProxy proxy = configBean;  // Correct
ConfigBean bean = (ConfigBean) Dom.unwrap(configBean);  // Internal use only
```
