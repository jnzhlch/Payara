# CLAUDE.md - Web Services

This file provides guidance for working with the `appserver/webservices` module - JAX-WS (Metro) and JAX-RS (Jersey) web services support.

## Module Overview

The webservices module provides Payara Server's web services implementation based on Metro (JAX-WS) and Jersey (JAX-RS). It handles SOAP and REST web services deployment, endpoint publishing, and client support.

**Key Components:**
- **metro-glue** - Metro (JAX-WS) deployment integration
- **jsr109-impl** - JSR-109 web services implementation
- **connector** - Web services connector/sniffer
- **soap-tcp** - SOAP over TCP support

## Build Commands

```bash
# Build entire webservices module
mvn -DskipTests clean package -f appserver/webservices/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/webservices/metro-glue/pom.xml
mvn -DskipTests clean package -f appserver/webservices/jsr109-impl/pom.xml
```

## Architecture

### Module Structure

```
webservices/
├── metro-fragments/        # Metro library fragments
├── metro-glue/             # Metro deployment integration
├── connector/              # Web services sniffer/connector
├── jsr109-impl/            # JSR-109 implementation
├── soap-tcp/               # SOAP over TCP
└── webservices-scripts/    # Administration scripts
```

### Web Services Stack

```
Application (JAX-WS / JAX-RS)
       │
       ▼
[Metro Glue / JSR-109]
       │
       ├─→ Metro (JAX-WS) - SOAP services
       │   ├─→ @WebService, @SOAPMessageHandlers
       │   ├─→ wsimport tool support
       │   └─→ JAXB for XML binding
       │
       └─→ Jersey (JAX-RS) - REST services
           ├─→ @Path, @GET, @POST
           ├─→ JSON/XML support
           └─→ Client API
```

## JAX-WS (Metro)

### @WebService Endpoint

```java
@WebService(
    serviceName = "UserService",
    portName = "UserServicePort",
    targetNamespace = "http://example.com/ws"
)
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT)
public class UserService {

    @WebMethod
    public String getUser(@WebParam(name = "userId") String userId) {
        // Implementation
        return "User: " + userId;
    }

    @WebMethod
    public void createUser(@WebParam(name = "user") User user) {
        // Create user
    }
}
```

### SOAP Message Handlers

```java
@SOAPMessageHandlers({
    @SOAPMessageHandler(className = "com.example.LoggingHandler"),
    @SOAPMessageHandler(className = "com.example.SecurityHandler")
})
public class UserService {
    // Web service implementation
}
```

### Handler Chain

```java
public class LoggingHandler implements SOAPHandler<SOAPMessageContext> {

    @Override
    public boolean handleMessage(SOAPMessageContext context) {
        Boolean outbound = (Boolean) context.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);

        if (outbound) {
            // Log outgoing message
            logMessage(context.getMessage());
        } else {
            // Log incoming message
            logMessage(context.getMessage());
        }

        return true;
    }

    private void logMessage(SOAPMessage message) {
        try {
            message.writeTo(System.out);
        } catch (SOAPException | IOException e) {
            e.printStackTrace();
        }
    }
}
```

## JAX-RS (Jersey)

### REST Endpoint

```java
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @GET
    @Path("/{id}")
    public Response getUser(@PathParam("id") Long id) {
        User user = userService.findById(id);
        return Response.ok(user).build();
    }

    @POST
    public Response createUser(User user) {
        User created = userService.create(user);
        return Response.status(Response.Status.CREATED)
                       .entity(created)
                       .build();
    }

    @PUT
    @Path("/{id}")
    public Response updateUser(@PathParam("id") Long id, User user) {
        user.setId(id);
        User updated = userService.update(user);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteUser(@PathParam("id") Long id) {
        userService.delete(id);
        return Response.noContent().build();
    }
}
```

### JAX-RS Application

```java
@ApplicationPath("/api")
public class MyApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(UserResource.class);
        classes.add(ProductResource.class);
        return classes;
    }
}
```

### Dependency Injection

```java
@Path("/users")
@RequestScoped
public class UserResource {

    @Inject
    private UserService userService;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;
}
```

## JSR-109 Implementation

### Deployment Descriptor

```xml
<?xml version="1.0" encoding="UTF-8"?>
<webservices xmlns="http://java.sun.com/xml/ns/j2ee"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee
                                http://www.ibm.com/websphere/appserver/schemas/5.0/webservices.xsd"
            version="1.1">
    <webservice-description>
        <webservice-description-name>UserService</webservice-description-name>
        <port-component>
            <port-component-name>UserServicePort</port-component-name>
            <wsdl-port>UserServicePort</wsdl-port>
            <service-endpoint-interface>com.example.UserService</service-endpoint-interface>
            <service-impl-bean>
                <ejb-link>UserServiceBean</ejb-link>
            </service-impl-bean>
        </port-component>
    </webservice-description>
</webservices>
```

## SOAP over TCP

### Configuration

```xml
<web-service-endpoint>
    <endpoint-address-uri>http://localhost:8080/UserService</endpoint-address-uri>
    <soap-tcp-transport-enabled>true</soap-tcp-transport-enabled>
</web-service-endpoint>
```

## Client Support

### JAX-WS Client

```java
// Dynamic proxy
URL wsdlLocation = new URL("http://example.com/ws/UserService?wsdl");
QName serviceName = new QName("http://example.com/ws", "UserService");
Service service = Service.create(wsdlLocation, serviceName);
UserService port = service.getPort(UserService.class);

// Invoke web service
String result = port.getUser("123");

// Generated client (via wsimport)
UserServiceService service = new UserServiceService();
UserService port = service.getUserServicePort();
String result = port.getUser("123");
```

### JAX-RS Client

```java
// Client API
Client client = ClientBuilder.newClient();

String result = client.target("http://localhost:8080/api/users/{id}")
                     .resolveTemplate("id", 123)
                     .request(MediaType.APPLICATION_JSON)
                     .get(String.class);

// Typed response
User user = client.target("http://localhost:8080/api/users")
                  .request(MediaType.APPLICATION_JSON)
                  .post(Entity.entity(newUser, MediaType.APPLICATION_JSON), User.class);
```

## Web Services Sniffer

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
        // Check for web service annotations
        ArchiveType type = context.getArchiveHandler().getArchiveType();
        if (DOLUtils.warType().equals(type) || DOLUtils.ejbType().equals(type)) {
            return hasWebServiceAnnotations(context);
        }
        return false;
    }

    private boolean hasWebServiceAnnotations(DeploymentContext context) {
        // Scan for @WebService, @WebServiceProvider
        return context.getAnnotations().hasAnnotation(
            "jakarta.jws.WebService"
        ) || context.getAnnotations().hasAnnotation(
            "jakarta.xml.ws.WebServiceProvider"
        );
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `metro` | JAX-WS implementation |
| `jersey` | JAX-RS implementation |
| `jaxb-runtime` | XML binding |
| `deployment-common` | Deployment integration |
| `web` | Web container integration |
| `ejb` | EJB web services |

## Notes

- **JAX-WS 2.x** - Jakarta SOAP with Attachments API
- **JAX-RS 3.x** - Jakarta RESTful Web Services
- **Metro** - JAX-WS reference implementation
- **Jersey** - JAX-RS reference implementation
- **SOAP** - XML-based web services
- **REST** - Resource-based web services
- **WS-* Standards** - WS-Security, WS-ReliableMessaging, etc.
- **Client Support** - Both JAX-WS and JAX-RS clients
