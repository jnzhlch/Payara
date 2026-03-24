# CLAUDE.md - Web Services Connector

This file provides guidance for working with the `appserver/webservices/connector` module - Web services sniffer and container.

## Module Overview

The connector module provides the web services sniffer for detecting JAX-WS/JAX-RS applications and the web services container for deployment integration.

**Key Components:**
- **WebServicesSniffer** - Detects web service applications
- **WebServicesContainer** - Web services container

## Build Commands

```bash
# Build connector module
mvn -DskipTests clean package -f appserver/webservices/connector/pom.xml
```

## Module Contents

```
connector/
└── src/main/java/org/glassfish/webservices/connector/
    ├── WebServicesSniffer.java
    └── WebServicesContainer.java
```

## WebServicesSniffer

```java
@Service
@Sorted
public class WebServicesSniffer implements Sniffer {

    @Override
    public String[] getContainersNames() {
        return new String[] { WebServicesContainer.class.getName() };
    }

    @Override
    public boolean handles(DeploymentContext context) {
        // Check for web service annotations or descriptors
        return hasWebServiceAnnotations(context) ||
               hasWebServiceDescriptors(context);
    }

    @Override
    public String[] getAnnotationNames() {
        return new String[] {
            "jakarta.jws.WebService",
            "jakarta.xml.ws.WebServiceProvider",
            "jakarta.servlet.http.HttpServlet",
            "jakarta.ws.rs.Path"
        };
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `deployment-common` | Sniffer infrastructure |
| `metro-glue` | JAX-WS integration |
| `jsr109-impl` | JSR-109 implementation |

## Notes

- **Sniffer** - Detects JAX-WS/JAX-RS applications
- **Annotations** - @WebService, @WebServiceProvider, @Path
- **Descriptors** - webservices.xml, sun-jaxws.xml
