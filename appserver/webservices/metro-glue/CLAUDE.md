# CLAUDE.md - Metro Glue

This file provides guidance for working with the `appserver/webservices/metro-glue` module - Metro (JAX-WS) deployment integration.

## Module Overview

The metro-glue module provides the integration layer between Metro (JAX-WS implementation) and Payara Server's deployment infrastructure.

**Key Components:**
- **JAXWSDeployer** - Deploys JAX-WS endpoints
- **JAXWSServlet** - HTTP servlet for JAX-WS

## Build Commands

```bash
# Build metro-glue module
mvn -DskipTests clean package -f appserver/webservices/metro-glue/pom.xml
```

## Module Contents

```
metro-glue/
└── src/main/java/org/glassfish/webservices/...
    ├── deployment/
    │   └── JAXWSDeployer.java
    └── ...
```

## JAXWSDeployer

```java
@Service
public class JAXWSDeployer extends AbstractDeployer<JAXWSCContainer, JAXWSApplicationContainer> {

    @Override
    public boolean prepare(DeploymentContext context) {
        // Scan for @WebService endpoints
        List<WebServiceDescriptor> descriptors = scanWebServiceEndpoints(context);

        // Generate WSDL if needed
        for (WebServiceDescriptor desc : descriptors) {
            if (desc.getWsdlFile() == null) {
                generateWsdl(desc);
            }
        }

        return !descriptors.isEmpty();
    }

    @Override
    public JAXWSApplicationContainer load(JAXWSCContainer container, DeploymentContext context) {
        // Deploy JAX-WS endpoints
        deployEndpoints(context);
    }
}
```

## Endpoint Publishing

```java
// Endpoint is published as HTTP servlet
@WebServlet("/UserService")
public class UserServiceServlet extends WSServlet {

    @Override
    public void init(ServletConfig config) throws ServletException {
        // Get endpoint instance
        UserService endpoint = new UserService();

        // Publish endpoint
        Endpoint endpointImpl = Endpoint.create(endpoint);
        endpointImpl.publish("/UserService");
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `metro` | JAX-WS implementation |
| `web` | Servlet container integration |
| `deployment-common` | Deployment infrastructure |

## Notes

- **JAX-WS** - SOAP web services
- **Metro** - JAX-WS implementation
- **WSDL** - Web Service Description Language
- **SOAP** - XML-based messaging
