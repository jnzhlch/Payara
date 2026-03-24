# CLAUDE.md - Config API

This file provides guidance for working with the Config API module - configuration management for Payara.

## Module Overview

The Config API provides configuration management through POJO-based config beans that map to domain.xml. It supports modularity, validation, change notification, and dynamic reconfiguration.

## Key Components

### Config Beans

**Configuration is POJO-based** - Plain Old Java Objects annotated to map to domain.xml:

```java
@Configured
public interface ServerConfig {
    @Attribute
    String getConfigName();

    @Attribute(defaultValue = "true")
    boolean isAdminEnabled();

    @Element
    List<JvmOption> getJvmOption();
}
```

**Key annotations:**
- `@Configured` - Marks a config bean interface
- `@Attribute` - XML attribute mapping
- `@Element` - XML child element mapping
- `@DuckTyped` - Generated methods

### Config Modularity (`modularity/`)

**Support for modular configuration:**
- `ConfigBeanInstaller` - Installs config beans for modules
- `ConfigModularityUtils` - Utilities for modular config
- `ConfigModularityJustInTimeInjectionResolver` - JIT resolution

**Modularity commands:**
- `CreateModuleConfigCommand` - Create module config
- `DeleteModuleConfigCommand` - Delete module config
- `GetActiveConfigCommand` - Get active configuration

### Customization (`customization/`)

**Configuration customization:**
- `CustomizationTokensProvider` - Token replacement
- `ConfigBeanDefaultValue` - Default values
- `PortTypeDetails` - Port configuration
- `FileTypeDetails` - File path configuration

### Parser (`parser/`)

**Configuration parsing:**
- `ConfigurationParser` - Parse domain.xml
- XML schema validation
- Config bean instantiation

## Build Commands

```bash
# Build config-api module
mvn -DskipTests clean package -f nucleus/admin/config-api/pom.xml

# Build with tests
mvn clean package -f nucleus/admin/config-api/pom.xml
```

## Config Bean Pattern

### Simple Config Bean

```java
package com.sun.enterprise.config.serverbeans;

import org.jvnet.hk2.config.Attribute;
import org.jvnet.hk2.config.Configured;
import org.jvnet.hk2.config.Element;

@Configured
public interface MyServiceConfig {
    @Attribute(defaultValue = "8080")
    String getPort();

    void setPort(String port);

    @Attribute(defaultValue = "true")
    boolean isEnabled();

    void setEnabled(boolean enabled);

    @Element
    List<Property> getProperty();
}
```

### Config Bean with Duck Typing

```java
@Configured
public interface MyConfig {
    @DuckTyped
    String getComputedValue();  // Generated method
}
```

### Implementing DuckTyped Method

```java
public class MyConfigDuck {
    public static String getComputedValue(MyConfig config) {
        return config.getName() + "-" + config.getPort();
    }
}
```

## Domain.xml Structure

```xml
<domain>
    <configs>
        <config name="server-config">
            <my-service port="8080" enabled="true">
                <property name="foo" value="bar"/>
            </my-service>
        </config>
    </configs>
</domain>
```

## Configuration Changes

### Programmatic Changes

```java
@Inject
private ConfigSupport configSupport;

public void updateConfig() throws TransactionFailure {
    ConfigSupport.apply(new SingleConfigCode<MyServiceConfig>() {
        @Override
        public Object run(MyServiceConfig config) {
            config.setPort("9090");
            return null;
        }
    }, myConfigBean);
}
```

### Transaction Support

```java
ConfigSupport.apply(new ConfigCode() {
    @Override
    public Object run(ConfigBeanProxy... proxies) {
        // Multiple changes in one transaction
        return null;
    }
});
```

## Configuration Listeners

Listen to configuration changes:

```java
public class MyConfigListener implements ConfigListener {
    @Override
    public UnprocessedChangeEvents changed(
            UnprocessedChangeEvents events) {
        // Handle configuration changes
        return null;
    }
}
```

Register via `@Service` and `@ConfigListener`.

## Key Interfaces

- `ConfigBeanProxy` - Base for all config beans
- `ConfigSupport` - Configuration modification utilities
- `ConfigListener` - Configuration change notification
- `ConfigView` - Read-only configuration view
- `Transaction` - Configuration transaction

## Querying Configuration

```java
@Inject
private Domain domain;

public void findConfig() {
    // Get server config
    Server server = domain.getServerNamed("server");
    Config config = server.getConfig();

    // Find specific config bean
    MyServiceConfig myConfig =
        config.getExtensionByType(MyServiceConfig.class);

    // Get properties
    List<Property> props = myConfig.getProperty();
}
```

## Environment Configuration

Configuration sources (in order of precedence):
1. System properties
2. Domain.xml (domain.xml)
3. Default values from annotations

## Modularity

Modules can provide their own configuration:

1. Create config bean interface
2. Add `@CustomConfiguration` annotation
3. Provide XML schema fragment
4. Register via ConfigBeanInstaller

## Key Packages

- `com.sun.enterprise.config.serverbeans` - Core server config beans
- `com.sun.enterprise.config.modularity` - Modular config support
- `org.jvnet.hk2.config` - HK2 config framework
- `fish.payara.config` - Payara-specific config extensions

## Architecture Notes

1. **HK2-based** - Config beans are HK2 services
2. **Lazy loading** - Config loaded on demand
3. **Type-safe** - POJO interfaces, not raw XML
4. **Transactional** - Changes atomic via transactions
5. **Observable** - Listeners for change notification
