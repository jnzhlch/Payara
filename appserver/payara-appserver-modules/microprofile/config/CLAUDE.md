# CLAUDE.md - MicroProfile Config

This file provides guidance for working with the `microprofile/config` module - MicroProfile Config API implementation.

## Module Overview

The microprofile/config module provides an implementation of the MicroProfile Config API for Payara Server. It enables applications to externalize configuration from application code and inject configuration values using CDI.

**Key Features:**
- **@ConfigProperty Injection** - Inject configuration values into CDI beans
- **@ConfigProperties** - Group multiple configuration properties into a POJO
- **Config Sources** - Multiple configuration sources with precedence ordering
- **Converters** - Automatic type conversion for configuration values
- **Default Values** - Support for default configuration values
- **Optional Support** - Optional<T> for null-safe configuration access

## Build Commands

```bash
# Build microprofile/config module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/config/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/config/pom.xml
```

## Architecture

### CDI Extension Flow

```
Application Deployment
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│          ConfigCdiExtension (CDI Extension)                     │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. beforeBeanDiscovery                                   │ │
│  │     - Register ConfigProducer bean                        │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. validateInjectionPoint(@Observes ProcessInjectionPoint)│ │
│  │     - Validate @ConfigProperty injection points          │ │
│  │     - Check converter availability                       │ │
│  │     - Check primitive value availability                  │ │
│  │     - Add deployment errors if validation fails          │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. storeConfigPropertiesType(@Observes ProcessAnnotatedType)│ │
│  │     - Find @ConfigProperties annotated classes           │ │
│  │     - Store for later bean creation                      │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  4. addDynamicProducers(@Observes AfterBeanDiscovery)     │ │
│  │     - Get Config instance                                │ │
│  │     - Get all registered converter types                 │ │
│  │     - Create synthetic beans for each converter type     │ │
│  │     - Create beans for ConfigProperties classes          │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│            ConfigPropertyProducer                              │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  getGenericProperty(InjectionPoint ip)                   │ │
│  │  1. Create ConfigPropertyModel from injection point      │ │
│  │  2. Resolve property name from @ConfigProperty           │ │
│  │  3. Get default value from @ConfigProperty               │ │
│  │  4. Convert value to injection point type                │ │
│  │  5. Return converted value                              │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### Config Source Resolution Order

```
┌────────────────────────────────────────────────────────────────┐
│                 Config Source Precedence                        │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. System Properties (highest precedence)               │ │
│  │     -Dproperty=value                                     │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Environment Variables                                │ │
│  │     export PROPERTY=value                                │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Payara Extended Config Sources                       │ │
│  │     - TOML files                                         │ │
│  │     - LDAP directory                                     │ │
│  │     - AWS Secrets Manager                                │ │
│  │     - Azure Key Vault                                    │ │
│  │     - GCP Secret Manager                                 │ │
│  │     - HashiCorp Vault                                    │ │
│  │     - DynamoDB                                           │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  4. Application Config                                   │ │
│  │     - META-INF/microprofile-config.properties           │ │
│  │     - WEB-INF/classes/META-INF/...                       │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  5. Default Values (lowest precedence)                   │ │
│  │     @ConfigProperty(defaultValue = "...")                │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

## Core Components

### ConfigCdiExtension

```java
public class ConfigCdiExtension implements Extension {

    // Validate @ConfigProperty injection points during deployment
    public void validateInjectionPoint(@Observes ProcessInjectionPoint<?, ?> pip) {
        ConfigProperty property = pip.getInjectionPoint()
            .getAnnotated().getAnnotation(ConfigProperty.class);

        if (property != null) {
            try {
                // Attempt to resolve the injection point
                ConfigPropertyProducer.getGenericProperty(pip.getInjectionPoint());
            } catch (Throwable de) {
                // Add deployment error if resolution fails
                pip.addDefinitionError(new DeploymentException(
                    "Deployment Failure for ConfigProperty " + property.name() +
                    " Reason " + de.getMessage(), de));
            }
        }
    }

    // Register ConfigProducer bean
    public void createConfigProducer(@Observes BeforeBeanDiscovery event, BeanManager bm) {
        AnnotatedType<ConfigProducer> at = bm.createAnnotatedType(ConfigProducer.class);
        event.addAnnotatedType(at, ConfigProducer.class.getName());
    }

    // Create synthetic beans for each converter type
    public void addDynamicProducers(@Observes AfterBeanDiscovery event, BeanManager bm) {
        Config config = ConfigProvider.getConfig();
        if (config instanceof PayaraConfig) {
            // Get all converter types from config
            Set<Type> types = ((PayaraConfig) config).getConverterTypes();

            // Create a synthetic bean for each type
            for (Type converterType : types) {
                Bean<?> bean = bm.createBean(
                    new TypesBeanAttributes<Object>(propertyBeanAttributes) {
                        @Override
                        public Set<Type> getTypes() {
                            // Return the type this bean can produce
                            return typesForConverter(converterType);
                        }
                    },
                    ConfigPropertyProducer.class,
                    bm.getProducerFactory(propertyProducerMethod, null)
                );
                event.addBean(bean);
            }
        }
    }
}
```

**Purpose:** CDI extension that validates injection points and creates synthetic beans for type-safe configuration injection.

### ConfigPropertyProducer

```java
public class ConfigPropertyProducer {

    @ConfigProperty
    @Dependent
    public static final Object getGenericProperty(InjectionPoint ip) {
        ConfigPropertyModel property = new ConfigPropertyModel(ip);
        return getGenericPropertyFromModel(property);
    }

    public static final Object getGenericPropertyFromModel(ConfigPropertyModel property) {
        Config config = ConfigProvider.getConfig();
        String name = property.getName();
        Type type = property.getInjectionPoint().getType();
        String defaultValue = property.getDefaultValue();

        if (type instanceof Class) {
            if (type == OptionalDouble.class) {
                return config.getValue(name, ConfigValueResolver.class)
                    .throwOnFailedConversion()
                    .withDefault(defaultValue)
                    .as(OptionalDouble.class)
                    .orElse(OptionalDouble.empty());
            } else {
                return config.getValue(name, ConfigValueResolver.class)
                    .throwOnMissingProperty(defaultValue == null)
                    .throwOnFailedConversion()
                    .withDefault(defaultValue)
                    .withPolicy(FAIL)
                    .as((Class<?>)type)
                    .get();
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType)type;
            Type rawType = ptype.getRawType();

            if (List.class.equals(rawType)) {
                return config.getValue(name, ConfigValueResolver.class)
                    .asList(getElementTypeFrom(ptype));
            } else if (Set.class.equals(rawType)) {
                return config.getValue(name, ConfigValueResolver.class)
                    .asSet(getElementTypeFrom(ptype));
            } else if (Optional.class.equals(rawType)) {
                return config.getValue(name, ConfigValueResolver.class)
                    .throwOnMissingProperty(false)
                    .as(getElementTypeFrom(ptype));
            } else if (Supplier.class.equals(rawType)) {
                return config.getValue(name, ConfigValueResolver.class)
                    .asSupplier(getElementTypeFrom(ptype));
            }
        }
    }
}
```

**Purpose:** Producer method that resolves configuration values and converts them to the injection point type.

### ConfigPropertiesProducer

```java
public class ConfigPropertiesProducer {

    @ConfigProperties
    @Dependent
    public static final Object getGenericObject(InjectionPoint ip) {
        // Create instance of @ConfigProperties annotated class
        // Inject configuration values into fields
        // Return configured instance
    }
}
```

**Purpose:** Producer method for @ConfigProperties annotated classes.

## Usage Examples

### @ConfigProperty Injection

```java
@ApplicationScoped
public class DatabaseService {

    // Basic injection with default value
    @Inject
    @ConfigProperty(name = "database.url", defaultValue = "jdbc:mysql://localhost:3306/db")
    private String databaseUrl;

    // Primitive type with default value
    @Inject
    @ConfigProperty(name = "database.pool.size", defaultValue = "10")
    private int poolSize;

    // Boolean configuration
    @Inject
    @ConfigProperty(name = "database.ssl.enabled", defaultValue = "false")
    private boolean sslEnabled;

    // Optional value (no exception if not found)
    @Inject
    @ConfigProperty(name = "database.timeout")
    private Optional<Integer> timeout;

    // List of values (comma-separated)
    @Inject
    @ConfigProperty(name = "database.servers")
    private List<String> servers;

    // Set of values
    @Inject
    @ConfigProperty(name = "database.allowed.ciphers")
    private Set<String> allowedCiphers;

    // Supplier for lazy resolution
    @Inject
    @ConfigProperty(name = "dynamic.config")
    private Supplier<String> dynamicConfig;
}
```

### @ConfigProperties Grouping

```java
// Configuration class
@ConfigProperties(prefix = "database")
public class DatabaseConfig {
    private String url = "jdbc:mysql://localhost:3306/db";
    private String username;
    private String password;
    private int poolSize = 10;
    private boolean sslEnabled = false;

    // Getters and setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    // ...
}

// Usage
@ApplicationScoped
public class DatabaseService {
    @Inject
    private DatabaseConfig databaseConfig;

    public void connect() {
        String url = databaseConfig.getUrl();
        String username = databaseConfig.getUsername();
        // ...
    }
}
```

### Configuration Files

**META-INF/microprofile-config.properties:**
```properties
# Database configuration
database.url=jdbc:mysql://prod-server:3306/mydb
database.username=admin
database.password=secret
database.pool.size=50
database.ssl.enabled=true

# List values (comma-separated)
database.servers=server1,server2,server3

# Set values (comma-separated)
database.allowed.ciphers=AES256,GCM,CHACHA20
```

**System Properties Override:**
```bash
java -Ddatabase.url=jdbc:mysql://localhost:3306/testdb -jar myapp.jar
```

**Environment Variables Override:**
```bash
export DATABASE_USERNAME=produser
export DATABASE_PASSWORD=prodpass
java -jar myapp.jar
```

### Programmatic Config Access

```java
@ApplicationScoped
public class ConfigService {

    @Inject
    private Config config;

    public void getConfigValue() {
        // Get value with default
        String value = config.getValue("my.property", String.class);

        // Get optional value
        Optional<String> optionalValue = config.getOptionalValue("my.property", String.class);

        // Get property names
        Iterable<String> propertyNames = config.getPropertyNames();

        // Iterate over all config sources
        for (ConfigSource source : config.getConfigSources()) {
            System.out.println(source.getName() + ": " + source.getOrdinal());
        }
    }
}
```

## Deployment Integration

### ConfigContainer

```java
public class ConfigContainer extends ApplicationContainer {

    @Override
    public boolean start(ApplicationContext startupContext) {
        // Register application class loader with ConfigProviderResolver
        ClassLoader appClassLoader = getContext().getClassLoader();
        ConfigProviderResolver.instance()
            .registerConfig(config, appClassLoader);
        return true;
    }

    @Override
    public boolean stop(ApplicationContext stopContext) {
        // Unregister config on application undeploy
        ClassLoader appClassLoader = getContext().getClassLoader();
        ConfigProviderResolver.instance()
            .releaseConfig(config, appClassLoader);
        return true;
    }
}
```

**Purpose:** Manages Config lifecycle per application deployment.

## Package Structure

```
microprofile/config/
└── src/main/java/fish/payara/microprofile/config/
    ├── activation/
    │   ├── ConfigApplicationContainer.java    # Application lifecycle
    │   ├── ConfigContainer.java               # Container management
    │   ├── ConfigDeployer.java                # Deployment handler
    │   └── ConfigSniffer.java                 # Deployment detection
    ├── cdi/
    │   ├── ConfigCdiExtension.java            # CDI extension
    │   ├── ConfigProducer.java                # Config/Optional producer
    │   ├── ConfigPropertiesProducer.java      # ConfigProperties producer
    │   ├── ConfigPropertyProducer.java        # Property producer
    │   ├── ConfigPropertyModel.java           # Injection point model
    │   └── TypesBeanAttributes.java          # Bean attributes customization
    └── spi/ (uses nucleus/microprofile/config/spi)
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `nucleus/microprofile/config` | Core config SPI and implementation |
| `microprofile-config-api` | MicroProfile Config API |
| `cdi-api` | CDI API |
| `jakarta.inject` | Jakarta Inject API |

## Notes

- **Validation at Deployment** - Config injection points are validated during deployment
- **Converter Registration** - Custom converters are automatically registered from Config
- **Array Support** - Supports array injection (e.g., String[], int[])
- **Primitive Wrappers** - Supports both primitives and wrapper types
- **Null Handling** - Primitives without defaults cause deployment failure
- **Dynamic Configuration** - Supplier<T> provides dynamic value resolution
- **Application Isolation** - Each application has its own Config instance
