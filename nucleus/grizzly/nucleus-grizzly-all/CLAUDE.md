# CLAUDE.md - Nucleus Grizzly All

This file provides guidance for working with the `nucleus-grizzly-all` module - combined Grizzly JAR bundle.

## Module Overview

The `nucleus-grizzly-all` module creates a single OSGi bundle that combines all Grizzly JAR dependencies into one convenient package. This simplifies deployment and classpath management for Payara.

## Build Commands

```bash
# Build nucleus-grizzly-all
mvn -DskipTests clean package -f nucleus/grizzly/nucleus-grizzly-all/pom.xml

# Build with tests
mvn clean package -f nucleus/grizzly/nucleus-grizzly-all/pom.xml
```

## Purpose

### Why Combine Grizzly JARs?

Grizzly framework consists of multiple modules:
- `grizzly-framework` - Core framework
- `grizzly-http-server` - HTTP server
- `grizzly-http` - HTTP protocol
- `grizzly-portunif` - Port unification
- `grizzly-http2` - HTTP/2 support
- `tls-sni` - TLS SNI extension

Instead of deploying each as a separate OSGi bundle, this module:
1. **Packages all Grizzly JARs** into a single bundle
2. **Re-exports packages** for other modules to use
3. **Simplifies classpath** - one JAR instead of many
4. **Ensures version compatibility** - all Grizzly modules from same version

## Maven Configuration

### Dependency Information

```xml
<dependency>
    <groupId>fish.payara.nucleus.grizzly</groupId>
    <artifactId>nucleus-grizzly-all</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Bundle Packaging

The module uses `maven-bundle-plugin` to create OSGi bundle:

```xml
<plugin>
    <groupId>org.apache.felix</groupId>
    <artifactId>maven-bundle-plugin</artifactId>
    <configuration>
        <instructions>
            <Export-Package>
                org.glassfish.grizzly.*,
                org.glassfish.grizzly.http.*,
                org.glassfish.grizzly.http.server.*,
                org.glassfish.grizzly.http2.*,
                org.glassfish.grizzly.portunif.*,
                org.glassfish.grizzly.ssl.*
            </Export-Package>
        </instructions>
    </configuration>
</plugin>
```

## Grizzly Components Included

### Core Framework

- **grizzly-framework** - Core NIO framework, buffers, filters
- **grizzly-http** - HTTP protocol implementation
- **grizzly-http-server** - HTTP server with request handling

### Extensions

- **grizzly-portunif** - Port unification (multiple protocols on one port)
- **grizzly-http2** - HTTP/2 protocol support
- **tls-sni** - TLS Server Name Indication extension

### Package Exports

| Package | Purpose |
|---------|---------|
| `org.glassfish.grizzly` | Core framework (filters, buffers, I/O) |
| `org.glassfish.grizzly.http` | HTTP protocol |
| `org.glassfish.grizzly.http.server` | HTTP server |
| `org.glassfish.grizzly.http.util` | HTTP utilities |
| `org.glassfish.grizzly.http2` | HTTP/2 support |
| `org.glassfish.grizzly.portunif` | Port unification |
| `org.glassfish.grizzly.ssl` | SSL/TLS support |
| `org.glassfish.grizzly.nio` | NIO transport |
| `org.glassfish.grizzly.threadpool` | Thread pools |

## Usage

### In Payara Modules

```xml
<dependency>
    <groupId>fish.payara.nucleus.grizzly</groupId>
    <artifactId>nucleus-grizzly-all</artifactId>
    <scope>provided</scope>
</dependency>
```

### Import Packages

```java
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.filterchain.FilterChain;
```

## Relationship to Other Modules

### Direct Dependencies

- **grizzly-framework** (bundled)
- **grizzly-http-server** (bundled)
- **grizzly-http** (bundled)
- **grizzly-portunif** (bundled)
- **grizzly-http2** (bundled)
- **tls-sni** (bundled)

### Dependent Modules

Modules that use Grizzly via this bundle:
- **grizzly/config** - Uses Grizzly HTTP server
- **appserver/web** - Web container integration
- **appserver/web-glue** - Web infrastructure

## Deployment Location

The bundle is deployed to:
```
{payara-install}/glassfish/modules/nucleus-grizzly-all.jar
```

## Version Information

The Grizzly version is determined by the Payara version:
- Payara 6.x uses Grizzly 4.x
- Check `pom.xml` for exact Grizzly version

## Common Issues

### Package Not Found

If getting package not found errors:

1. Check bundle is installed:
   ```bash
   ls -la {payara-install}/glassfish/modules/nucleus-grizzly-all.jar
   ```

2. Check OSGi bundle is active:
   ```bash
   asadmin list-bundles | grep grizzly
   ```

3. Check package exports:
   ```bash
   asadmin show-package-exports | grep grizzly
   ```

### Version Conflicts

If encountering version conflicts:

1. Check dependency tree:
   ```bash
   mvn dependency:tree -f nucleus/grizzly/nucleus-grizzly-all/pom.xml
   ```

2. Ensure no other Grizzly JARs on classpath

3. Verify bundle version matches Payara version

## Notes

- This is a packaging module - no Java source code
- All functionality comes from bundled Grizzly JARs
- For Grizzly feature documentation, see Grizzly project docs
- For Payara-specific Grizzly usage, see `grizzly/config/CLAUDE.md`

## Related Modules

- **grizzly/config** - Payara Grizzly configuration layer
- **nucleus/core/kernel** - Grizzly integration with server lifecycle
