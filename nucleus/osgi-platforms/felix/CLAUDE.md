# CLAUDE.md - Apache Felix Distribution

This file provides guidance for working with the `felix` module - Apache Felix OSGi framework distribution fragment.

## Module Overview

The `felix` module creates a distribution fragment containing Apache Felix OSGi framework and associated configuration files. It packages Felix as part of Payara's runtime.

## Build Commands

```bash
# Build felix module
mvn -DskipTests clean package -f nucleus/osgi-platforms/felix/pom.xml
```

## Architecture

### Distribution Fragment Structure

```
{payara-install}/
├── osgi/
│   └── felix/
│       ├── bin/
│       │   └── felix.jar              # Felix framework JAR
│       └── conf/
│           └── osgi.properties        # OSGi configuration
└── modules/
    ├── org.apache.felix.main.jar     # Felix main bundle
    ├── org.apache.felix.bundlerepository.jar
    └── autostart/
        └── org.apache.felix.scr.jar   # Declarative Services (auto-start)
```

### Build Process

```
felix/pom.xml
        │
        ├─→ maven-dependency-plugin
        │      ├─→ Copies org.apache.felix.main → osgi/felix/bin/felix.jar
        │      └─→ Copy felix bundles → modules/
        │
        ├─→ maven-antrun-extended-plugin
        │      ├─→ Runs build.xml
        │      └─→ Creates felix.zip distribution
        │
        └─→ felix.zip
               └─→ Deployed as distribution-fragment
```

## Configuration

### osgi.properties

Located in `src/main/resources/config/osgi.properties`:

```properties
# Boot delegation for special packages
org.osgi.framework.bootdelegation=oracle.sql, oracle.sql.*, \
    com.sun.btrace, com.sun.btrace.*, \
    org.netbeans.lib.profiler, org.netbeans.lib.profiler.*, \
    jdk.internal.reflect, jdk.internal.reflect.*, jdk.net

# Use application classloader as parent
org.osgi.framework.bundle.parent=app

# OSGi cache location (set by platform helper)
# org.osgi.framework.storage=${com.sun.aas.instanceRoot}/osgi-cache/

# Auto-start bundles
glassfish.osgi.ondemand=false
com.sun.enterprise.hk2.repositories=${com.sun.aas.installRootURI}/modules/
```

### Boot Delegation

Packages loaded from boot classpath instead of bundle classloaders:

```properties
# For EclipseLink JDBC driver issues
eclipselink.bootdelegation=oracle.sql, oracle.sql.*, oracle.jdbc, oracle.jdbc.*

# For NetBeans profiler
org.netbeans.lib.profiler, org.netbeans.lib.profiler.*

# For BTrace
com.sun.btrace, com.sun.btrace.*

# For JDK internals
jdk.internal.reflect, jdk.internal.reflect.*, jdk.net
```

### Parent ClassLoader

```properties
# Use application classloader (not boot classloader)
org.osgi.framework.bundle.parent=app
```

This allows OSGi to access classes from the system classpath.

## Auto-Start Bundles

### Autostart Directory

Bundles in `{payara-install}/modules/autostart/` start automatically:

```
modules/autostart/
└── org.apache.felix.scr.jar     # Declarative Services runtime
```

### Configuring Auto-Start

```properties
# In osgi.properties
felix.auto.start.1= \
    file:${com.sun.aas.installRoot}/modules/autostart/org.apache.felix.scr.jar

felix.auto.start.2= \
    file:${com.sun.aas.installRoot}/modules/org.apache.felix.configadmin.jar
```

## Maven Configuration

### Distribution Fragment Packaging

```xml
<packaging>distribution-fragment</packaging>
```

### Dependency Copy

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <executions>
        <execution>
            <phase>validate</phase>
            <goals>
                <goal>copy</goal>
            </goals>
            <configuration>
                <artifactItems>
                    <!-- Felix main JAR -->
                    <artifactItem>
                        <groupId>org.apache.felix</groupId>
                        <artifactId>org.apache.felix.main</artifactId>
                        <outputDirectory>${felix.outdir}/bin</outputDirectory>
                        <destFileName>felix.jar</destFileName>
                    </artifactItem>
                    <!-- Felix bundles -->
                    <artifactItem>
                        <groupId>org.apache.felix</groupId>
                        <artifactId>org.apache.felix.bundlerepository</artifactId>
                        <outputDirectory>${glassfish.modulesdir}</outputDirectory>
                    </artifactItem>
                </artifactItems>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Ant Build

```xml
<plugin>
    <groupId>org.jvnet.maven-antrun-extended-plugin</groupId>
    <artifactId>maven-antrun-extended-plugin</artifactId>
    <configuration>
        <tasks>
            <ant dir="." antfile="build.xml" target="create.bundle" />
        </tasks>
    </configuration>
</plugin>
```

## build.xml

```xml
<project name="felix distribution fragment creation" default="create.bundle">
    <target name="create.bundle">
        <!-- Create distribution ZIP -->
        <zip destfile="target/felix.zip" basedir="target/classes/"
             excludes="**/.ade_path/**" />
        <attachArtifact file="target/felix.zip"/>
    </target>
</project>
```

## Felix Version

The Apache Felix version is managed by Payara's dependency management:

```xml
<dependency>
    <groupId>org.apache.felix</groupId>
    <artifactId>org.apache.felix.main</artifactId>
    <version>${felix.version}</version>
</dependency>
```

Check the main Payara POM for the current Felix version.

## Related Modules

- **osgi-container** - OSGi bundle deployment container
- **osgi-cli-remote** - Remote OSGi shell access
- **core/bootstrap** - OSGi framework initialization

## Runtime Usage

### Starting Felix Directly

```bash
# Start Felix framework standalone
java -jar {payara-install}/osgi/felix/bin/felix.jar

# Felix will start and provide a shell
-> help
-> lb
-> exit
```

### Embedded in Payara

Felix is embedded in Payara and started during server initialization:

```java
// In bootstrap code
FrameworkFactory factory = ...;
Map<String, String> config = new HashMap<>();
config.put("org.osgi.framework.storage", osgiCacheDir);

Framework framework = factory.newFramework(config);
framework.start();
```

## Troubleshooting

### Cache Issues

```bash
# Clear OSGi cache
rm -rf {domain}/osgi-cache/
```

### Bundle Not Starting

```properties
# Enable Felix debug
felix.log.level=4

# Check auto-start configuration
felix.auto.start.1=...
```

### Class Loading Issues

```properties
# Add boot delegation for problematic packages
org.osgi.framework.bootdelegation=com.example.*,com.example
```

## Notes

- This is a distribution fragment, not a code module
- Packages Apache Felix JARs into Payara layout
- Configuration files control Felix behavior
- Auto-start bundles determine initial OSGi services
