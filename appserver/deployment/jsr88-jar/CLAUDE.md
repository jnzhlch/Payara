# CLAUDE.md - JSR-88 JAR

This file provides guidance for working with the `jsr88-jar` module - JSR-88 deployment support packaging.

## Module Overview

The jsr88-jar module packages JSR-88 (Java EE Deployment API) support as a distributable JAR and distribution fragment. It provides the deployment factory registration for tool integration.

## Build Commands

```bash
# Build jsr88-jar module
mvn -DskipTests clean package -f appserver/deployment/jsr88-jar/pom.xml
```

## Module Structure

This is a parent POM module with two submodules:

| Submodule | Purpose |
|-----------|---------|
| `sun-as-jsr88-dm` | JSR-88 deployment factory manifest |
| `sun-as-jsr88-dm-frag` | Distribution fragment |

## sun-as-jsr88-dm

### Module Type

- **Packaging**: `jar` (manifest-only JAR)
- **Name**: "JSR-88 implementation declaration JAR"
- **Purpose**: JSR-88 DeploymentFactory registration

### Manifest Entries

The JAR contains only a MANIFEST.MF with JSR-88 registration:

```
Manifest-Version: 1.0
J2EE-DeploymentFactory-Implementation-Class: org.glassfish.deployapi.SunDeploymentFactory
Specification-Title: Java Platform, Enterprise Edition Specification
Specification-Vendor: Sun Microsystems, Inc.
Specification-Version: 6.0
Implementation-Vendor: Sun Microsystems, Inc.
Implementation-Version: 3.0
Implementation-Title: Glassfish Application Server
```

### DeploymentFactory Registration

The manifest entry `J2EE-DeploymentFactory-Implementation-Class` enables JSR-88 tool discovery:

```java
// Tools use ServiceLoader to find DeploymentFactory implementations
DeploymentFactory factory = DeploymentFactoryManager.getDeploymentFactory("deployer:payara:...");
```

## sun-as-jsr88-dm-frag

### Module Type

- **Packaging**: `distribution-fragment`
- **Name**: "JSR-88 dist. fragment"
- **Purpose**: Adds JSR-88 JAR to distribution

### Distribution Fragment

Distribution fragments are extracted during distribution assembly:

```
# Build places JAR in distribution
target/classes/glassfish/lib/deployment/sun-as-jsr88-dm.jar

# Final installation location
$PAYARA_HOME/glassfish/lib/deployment/sun-as-jsr88-dm.jar
```

### Fragment Pattern

```xml
<!-- Dependencies are included in distribution -->
<dependencies>
    <dependency>
        <groupId>fish.payara.server.internal.deployment</groupId>
        <artifactId>sun-as-jsr88-dm</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

## JSR-88 Discovery

### Service Registration

JSR-88 tools discover Payara through:

1. **JAR in classpath** - Include `sun-as-jsr88-dm.jar`
2. **Manifest reading** - Read deployment factory class
3. **Factory instantiation** - Create `SunDeploymentFactory`
4. **URI validation** - Check if factory handles URI

### URI Pattern

Payara handles these URI patterns:

```
deployer:payara:...
deployer:Sun:...         (Legacy)
deployer:GlassFish:...   (Legacy)
```

## Package Structure

### Installed Location

```
$PAYARA_HOME/
└── glassfish/
    └── lib/
        └── deployment/
            └── sun-as-jsr88-dm.jar
```

### JAR Contents

```
sun-as-jsr88-dm.jar
└── META-INF/
    └── MANIFEST.MF
        └── J2EE-DeploymentFactory-Implementation-Class
```

## Distribution Assembly

### Maven Build Process

1. **Build sun-as-jsr88-dm** - Create manifest-only JAR
2. **Build sun-as-jsr88-dm-frag** - Copy JAR to fragment location
3. **Assemble distribution** - Extract fragment to final location

### Ant Task

```xml
<resolveArtifact artifactId="sun-as-jsr88-dm"
    tofile="target/classes/glassfish/lib/deployment/sun-as-jsr88-dm.jar" />
```

## Tool Integration

### IDE Integration

IDEs that support JSR-88 can automatically discover Payara:

```
# Add Payara server in IDE
Server > Add Server > Payara

# IDE uses JSR-88
DeploymentFactoryManager.getDeploymentFactory("deployer:payara:localhost:4848")
```

### Maven Plugin

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>cargo-maven2-plugin</artifactId>
    <configuration>
        <container>
            <containerId>payara</containerId>
            <type>remote</type>
        </container>
    </configuration>
</plugin>
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `deployment-client` | DeploymentFactory implementation |

## Related Modules

- `deployment/client` - DeploymentFactory implementation
- `deployment/javaee-full` - JSR-88 support

## JSR-88 Specification

| Specification | Version |
|---------------|---------|
| JSR-88 | J2EE 1.4 / Java EE 5+ |
| Deployment API | 1.2+ |

## Extension Points

### Custom DeploymentFactory

To add custom deployment factories:

1. Create factory class implementing `DeploymentFactory`
2. Create manifest-only JAR with factory class
3. Add JAR to `$PAYARA_HOME/glassfish/lib/deployment/`
4. Tools will discover factory automatically
