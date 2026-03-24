# CLAUDE.md - Config Types

This file provides guidance for working with the `config-types` module - common HK2 configuration types.

## Module Overview

The `config-types` module provides common configuration types used throughout Payara for handling generic name-value properties. It extends the HK2 configuration framework with reusable property handling.

## Build Commands

```bash
# Build config-types module
mvn -DskipTests clean package -f nucleus/hk2/config-types/pom.xml

# Build with tests
mvn clean package -f nucleus/hk2/config-types/pom.xml
```

## Core Types

### Property

Represents a single name-value pair:

```java
@Configured
public interface Property extends ConfigBeanProxy {

    @Attribute(required = true, key = true)
    String getName();

    void setName(String value);

    @Attribute(required = true, dataType = String.class)
    String getValue();

    void setValue(String value);

    @Attribute(defaultValue = "false")
    boolean isInheritNested();

    void setInheritNested(boolean value);
}
```

### PropertyBag

Interface for objects containing nested properties:

```java
@Customizer(PropertyBagCustomizer.class)
public interface PropertyBag {

    @Element("property")
    List<Property> getProperty();

    @DuckTyped
    default String getPropertyValue(String name) {
        // Find property by name
        for (Property prop : getProperty()) {
            if (name.equals(prop.getName())) {
                return prop.getValue();
            }
        }
        return null;
    }
}
```

## Usage Patterns

### 1. Adding Properties to Config

```java
@Configured
public interface MyServiceConfig extends ConfigBeanProxy, PropertyBag {

    @Attribute
    String getServiceName();

    void setServiceName(String name);
}
```

### 2. Reading Property Values

```java
@Service
public class MyService {

    @Inject
    private MyServiceConfig config;

    public void init() {
        // Get property value
        String timeout = config.getPropertyValue("timeout");
        if (timeout != null) {
            // Use timeout value
        }
    }
}
```

### 3. Setting Properties via Transaction

```java
ConfigSupport.apply(new SingleConfigCode<MyServiceConfig>() {
    @Override
    public Object run(MyServiceConfig config) throws PropertyVetoException {
        // Create new property
        Property prop = config.createChild(Property.class);
        prop.setName("maxConnections");
        prop.setValue("100");

        // Add to property bag
        config.getProperty().add(prop);

        return null;
    }
}, myConfig);
```

### 4. Iterating Properties

```java
for (Property prop : config.getProperty()) {
    String name = prop.getName();
    String value = prop.getValue();

    System.out.println(name + " = " + value);
}
```

## Property Customization

### PropertyBagCustomizer

Customizes PropertyBag behavior:

```java
@Service
public class MyPropertyBagCustomizer extends PropertyBagCustomizerImpl {

    @Override
    public void customize(PropertyBag propertyBag) {
        // Custom initialization
    }
}
```

### Property Attributes

| Attribute | Description |
|-----------|-------------|
| `name` | Property name (required, key field) |
| `value` | Property value (required) |
| `inheritNested` | Whether to inherit from parent (default: false) |

## Domain.xml Representation

### XML Format

```xml
<my-service service-name="myService">
    <property name="timeout" value="30"/>
    <property name="maxConnections" value="100"/>
    <property name="enabled" value="true"/>
</my-service>
```

### With Inheritance

```xml
<config>
    <property name="defaultTimeout" value="30" inherit-nested="true"/>
</config>
```

## Common Use Cases

### 1. Extensible Configuration

Properties allow adding configuration without changing the schema:

```java
// No need to add new attributes
String customValue = config.getPropertyValue("custom.setting");
```

### 2. Default Values

```java
public int getTimeout() {
    String value = config.getPropertyValue("timeout");
    if (value != null) {
        return Integer.parseInt(value);
    }
    return 30;  // Default
}
```

### 3. Feature Flags

```java
public boolean isFeatureEnabled(String featureName) {
    String value = config.getPropertyValue(featureName + ".enabled");
    return Boolean.parseBoolean(value);
}
```

### 4. Type Conversion

```java
@Service
public class ConfigHelper {

    public static int getInt(PropertyBag config, String name, int defaultValue) {
        String value = config.getPropertyValue(name);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static boolean getBoolean(PropertyBag config, String name, boolean defaultValue) {
        String value = config.getPropertyValue(name);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }
}
```

## Property Pattern in Payara

### Common Property Names

| Property | Purpose |
|----------|---------|
| `instanceRoot` | Domain instance root directory |
| `javaRoot` | Java installation directory |
| `locale` | Default locale |
| `orbHost` | ORB host for IIOP |
| `orbPort` | ORB port for IIOP |

### System Property Resolution

```java
// Properties can reference system properties
<property name="${env.USER}_config" value="custom"/>
```

## Package Structure

```
org.jvnet.hk2.config.types/
├── Property.java              - Name-value pair
├── PropertyBag.java           - Container for properties
├── PropertyBagCustomizer.java - Customization hook
└── PropertyBagCustomizerImpl.java - Default implementation
```

## Related Modules

- **hk2-config** - Core configuration framework
- **config-generator** - Code generation support
- **admin/config-api** - Config API integration

## Best Practices

1. **Use for optional settings** - Properties work well for optional configuration
2. **Type conversion** - Always handle NumberFormatException
3. **Default values** - Provide sensible defaults when property not found
4. **Validation** - Validate property values in code, not schema
5. **Naming conventions** - Use consistent property naming (camelCase)

## Troubleshooting

### Property Not Found

```java
String value = config.getPropertyValue("myProp");
if (value == null) {
    // Property not configured
    LOG.log(Level.WARNING, "Property myProp not configured, using default");
    value = "default";
}
```

### Type Conversion Errors

```java
try {
    int value = Integer.parseInt(config.getPropertyValue("count"));
} catch (NumberFormatException e) {
    LOG.log(Level.WARNING, "Invalid count value");
}
```

## Notes

- PropertyBag is used extensively throughout Payara configuration
- Properties provide flexibility without schema changes
- Consider using typed attributes for stable configuration
