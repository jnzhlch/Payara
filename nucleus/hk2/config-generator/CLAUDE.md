# CLAUDE.md - Config Generator

This file provides guidance for working with the `config-generator` module - Maven plugin for generating HK2 configuration injectors.

## Module Overview

The `config-generator` module is a Maven plugin that generates `ConfigInjector` implementations for `@Configured` interfaces at compile time. This enables HK2 to create configuration bean proxies efficiently.

## Build Commands

```bash
# Build config-generator module
mvn -DskipTests clean package -f nucleus/hk2/config-generator/pom.xml
```

## Plugin Configuration

### Basic Usage

```xml
<plugin>
    <groupId>org.glassfish.hk2</groupId>
    <artifactId>config-generator</artifactId>
    <version>2.5.0-b53</version>
    <executions>
        <execution>
            <goals>
                <goal>generate-injectors</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <excludes>**/.ade_path/**</excludes>
    </configuration>
</plugin>
```

### For Test Classes

```xml
<execution>
    <id>generate-test-injectors</id>
    <goals>
        <goal>generate-test-injectors</goal>
    </goals>
</execution>
```

### Disable Generation

```xml
<plugin>
    <groupId>org.glassfish.hk2</groupId>
    <artifactId>config-generator</artifactId>
    <executions>
        <execution>
            <id>default-generate-injectors</id>
            <phase/>
            <goals/>
        </execution>
        <execution>
            <id>default-generate-test-injectors</id>
            <phase/>
            <goals/>
        </execution>
    </executions>
</plugin>
```

## Architecture

### Code Generation Flow

```
@Configured Interface
        │
        ├─→ Annotation Processing (javac -proc:only)
        │      ├─→ ConfigInjectorGenerator
        │      ├─→ Reads @Configured annotations
        │      └─→ Uses JCodeModel to generate Java
        │
        ├─→ Generated Files
        │      ├─→ ClassName__Injector.java
        │      ├─→ ClassName__Hash__.java
        │      └─→ Generated in target/generated-sources/
        │
        └─→ Compilation
               └─→ Generated files compiled with main sources
```

## Generated Classes

### ConfigInjector Implementation

For each `@Configured` interface, generates:

```java
// Input: @Configured interface
@Configured
public interface HttpListener extends ConfigBeanProxy {
    @Attribute
    String getPort();
    void setPort(String port);
}

// Generated: ConfigInjector implementation
public class HttpListener__Injector implements ConfigInjector<HttpListener> {
    @Override
    public HttpListener create(ConfigBeanProxy parent) {
        // Creates proxy instance
    }

    @Override
    public void destroy(HttpListener instance) {
        // Destroys proxy
    }

    // Additional generated methods...
}
```

### Hash Class

Generates a hash for configuration tracking:

```java
public interface HttpListener__Hash__ {
    static int getHash() {
        // Returns hash of configuration structure
        return 123456789;
    }
}
```

## Configuration Classes

### ConfigInjectorGenerator

Main annotation processor:

```java
@SupportedAnnotationTypes({"org.jvnet.hk2.config.Configured"})
@SupportedSourceVersion(SourceVersion.latest())
public class ConfigInjectorGenerator extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                         RoundEnvironment roundEnv) {
        // Generate ConfigInjector implementations
    }
}
```

### Maven Goals

| Goal | Phase | Description |
|------|-------|-------------|
| `generate-injectors` | `generate-sources` | Generate injectors for main sources |
| `generate-test-injectors` | `generate-test-sources` | Generate injectors for test sources |

## Generated Output Location

```
target/generated-sources/config-generator/main/java/
└── org/
    └── glassfish/
        └── flashlight/
            └── config/
                ├── HttpListener__Injector.java
                ├── HttpListener__Hash__.java
                ├── NetworkConfig__Injector.java
                └── ...
```

## Processing Details

### What Gets Processed

Classes annotated with `@Configured`:

```java
@Configured
public interface MyConfig extends ConfigBeanProxy {
    // Will be processed
}
```

### What Gets Generated

For each `@Configured` interface:
1. **Injector class** - Implements `ConfigInjector<T>`
2. **Hash interface** - Contains structure hash

### Exclusions

```xml
<configuration>
    <excludes>
        <exclude>**/.ade_path/**</exclude>
        <exclude>**/test/**</exclude>
    </excludes>
</configuration>
```

## Dependencies

### Required Dependencies

```xml
<dependency>
    <groupId>org.glassfish.hk2</groupId>
    <artifactId>hk2-utils</artifactId>
</dependency>
<dependency>
    <groupId>fish.payara.server.internal.hk2</groupId>
    <artifactId>hk2-config</artifactId>
</dependency>
<dependency>
    <groupId>org.glassfish.jaxb</groupId>
    <artifactId>codemodel</artifactId>
</dependency>
```

## Related Files

### exclude.xml

Defines which packages to exclude from processing:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE exclude-config>
<exclude-config>
    <exclude>
        <pattern>**/.ade_path/**</pattern>
    </exclude>
</exclude-config>
```

## Troubleshooting

### Generated Files Not Found

```bash
# Check generated directory
ls -la target/generated-sources/config-generator/

# Clean and rebuild
mvn clean compile -f nucleus/hk2/config-types/pom.xml
```

### Annotation Processing Issues

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <compilerArgument>-proc:none</compilerArgument>
    </configuration>
</plugin>
```

### Generated Code Not Compiling

```bash
# Check annotation processing is enabled
mvn clean compile -X | grep config-generator

# Verify hk2-config is on classpath
mvn dependency:tree
```

## Package Structure

| Package | Purpose |
|---------|---------|
| `org.jvnet.hk2.config.generator` | Generator core |
| `org.jvnet.hk2.config.generator.maven` | Maven plugin goals |

## Notes

- This is a Maven plugin, not a library module
- Uses JCodeModel (from JAXB) to generate Java code
- Runs during `generate-sources` phase
- Generated files are compiled with main sources
