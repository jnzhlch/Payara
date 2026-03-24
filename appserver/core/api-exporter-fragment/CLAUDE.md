# CLAUDE.md - API Exporter Fragment

This file provides guidance for working with the `api-exporter-fragment` module - Jakarta EE API package imports.

## Module Overview

The api-exporter-fragment module is an OSGi fragment bundle that attaches to the api-exporter bundle to statically import Jakarta EE APIs for the Common Class Loader.

## Build Commands

```bash
# Build api-exporter-fragment
mvn -DskipTests clean package -f appserver/core/api-exporter-fragment/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (OSGi fragment bundle)
- **Fragment Host**: `GlassFish-Application-Common-Module`
- **Purpose**: Static package imports for Common Class Loader

## OSGi Fragment Configuration

```
Bundle-SymbolicName: GlassFish-Application-Common-Module-Fragment
Bundle-ManifestVersion: 2
Fragment-Host: GlassFish-Application-Common-Module
Import-Package: [extensive list of Jakarta EE packages]
resolution:=optional
```

## Package Categories

### Jakarta EE Platform APIs

| API | Packages |
|-----|----------|
| **Jakarta Activation** | jakarta.activation |
| **Jakarta Annotation** | jakarta.annotation.* |
| **Jakarta CDI** | jakarta.enterprise.*, jakarta.inject.* |
| **Jakarta Interceptors** | jakarta.interceptor |
| **Jakarta JSON-P** | jakarta.json |
| **Jakarta Mail** | jakarta.mail.* |
| **Jakarta Concurrency** | jakarta.enterprise.concurrent.* |

### Container APIs

| Container | Packages |
|----------|----------|
| **EJB** | jakarta.ejb.* |
| **JPA** | jakarta.persistence.* |
| **JMS** | jakarta.jms.* |
| **JCA** | jakarta.resource.* |
| **JTA** | jakarta.transaction.* |
| **Servlet** | jakarta.servlet.* |
| **JAX-RS** | jakarta.ws.rs.* |
| **Bean Validation** | jakarta.validation.* |
| **Faces** | jakarta.faces.* |

### Web Services APIs

| API | Packages |
|-----|----------|
| **JAX-WS** | jakarta.xml.ws.*, jakarta.ws.* |
| **JAXB** | jakarta.xml.bind.* |
| **SOAP** | jakarta.soap.* |
| **WSA** | jakarta.ws.rs.ext.* |

### Processing APIs

| API | Packages |
|-----|----------|
| **JAXB** | jakarta.xml.bind.* |
| **JAXP** | javax.xml.*, org.w3c.dom.*, org.xml.sax.* |
| **StAX** | javax.xml.stream.* |
| **XSLT** | javax.xml.transform.* |
| **XPATH** | javax.xml.xpath.* |
| **DOM** | org.w3c.dom.*, org.xml.sax.* |

### CORBA APIs

| API | Packages |
|-----|----------|
| **CORBA** | org.omg.CORBA.* |
| **Naming** | org.omg.CosNaming.* |
| **Portable Interceptor** | org.omg.PortableInterceptor.* |
| **IOP** | org.omg.IOP.* |
| **Dynamic Any** | org.omg.DynamicAny.* |

### Security APIs

| API | Packages |
|-----|----------|
| **Authentication** | jakarta.authentication.* |
| **Authorization** | jakarta.authorization.* |
| **JAAS** | javax.security.auth.* |

### Management APIs

| API | Packages |
|-----|----------|
| **JMX** | javax.management.* |
| **JMX Remote** | javax.management.remote.* |
| **JMX Standard** | javax.management.j2ee.* |

### Utility APIs

| API | Packages |
|-----|----------|
| **Image I/O** | javax.imageio.* |
| **Sound** | javax.sound.* |
| **Print** | javax.print.* |
| **Scripting** | javax.script.* |
| **Tools** | javax.tools.* |
| **Swing** | javax.swing.* |

## Import Strategy

### Static vs Dynamic Imports

```
Before (Dynamic):
├── ClassLoader asks for package
├── OSGi resolves dynamically
├── Performance overhead
└── Potential resolution failures

After (Static):
├── All packages pre-imported
├── No resolution overhead
└── Optional resolution prevents failures
```

### Resolution Strategy

```
Import-Package: ... resolution:=optional
```

- `optional` = Package may be absent
- Prevents startup failure if package missing
- Allows graceful degradation

## Benefits

| Benefit | Description |
|---------|-------------|
| **Performance** | Eliminates dynamic resolution overhead |
| **Reliability** | Avoids runtime resolution failures |
| **Simplicity** | Common CL sees all EE APIs |
| **Flexibility** | Optional resolution for extensibility |

## Common Class Loader

The fragment attaches to api-exporter bundle which is loaded by Common Class Loader:

```
Bootstrap ClassLoader
       │
Extension ClassLoader
       │
Common Class Loader (includes api-exporter + fragment)
       │
Application ClassLoaders (per application)
```

## Build Configuration

### Maven Jar Plugin

```xml
<plugin>
    <artifactId>maven-jar-plugin</artifactId>
    <configuration>
        <archive>
            <manifestEntries>
                <Fragment-Host>
                    GlassFish-Application-Common-Module
                </Fragment-Host>
                <Import-Package>
                    [300+ Jakarta EE packages...]
                </Import-Package>
            </manifestEntries>
        </archive>
    </configuration>
</plugin>
```

## Package Management

When adding new Jakarta EE APIs:
1. Add package to `Import-Package` list
2. Ensure resolution is `optional`
3. Test with and without the package present

## Dependencies

This module has no compile dependencies - it only contains OSGi manifest configuration.

## Related Modules

- `nucleus/common/glassloader` - Class loader infrastructure
- All Jakarta EE modules - APIs imported by this fragment
