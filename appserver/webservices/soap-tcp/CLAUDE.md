# CLAUDE.md - SOAP over TCP

This file provides guidance for working with the `appserver/webservices/soap-tcp` module - SOAP over TCP transport.

## Module Overview

The soap-tcp module provides SOAP over TCP transport implementation for JAX-WS, allowing SOAP messages to be sent over TCP instead of HTTP.

**Key Components:**
- **SOAPTCPProtocol** - TCP protocol handler
- **SOAPTCPEndpoint** - TCP endpoint

## Build Commands

```bash
# Build soap-tcp module
mvn -DskipTests clean package -f appserver/webservices/soap-tcp/pom.xml
```

## Module Contents

```
soap-tcp/
└── src/main/java/org/glassfish/webservices/soap/...
    ├── SOAPTCPProtocol.java
    └── SOAPTCPEndpoint.java
```

## SOAP over TCP

### Configuration

```xml
<web-service-endpoint>
    <endpoint-address-uri>soap.tcp://localhost:9090/MyService</endpoint-address-uri>
    <soap-tcp-transport-enabled>true</soap-tcp-transport-enabled>
</web-service-endpoint>
```

### Client Usage

```java
// Use TCP transport
QName serviceName = new QName("http://example.com/ws", "UserService");
Service service = Service.create(wsdlLocation, serviceName);

// Enable TCP
BindingProvider bp = (BindingProvider) service.getPort(UserService.class);
bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                           "soap.tcp://localhost:9090/UserService");
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `metro` | JAX-WS implementation |

## Notes

- **TCP Transport** - SOAP over TCP (not HTTP)
- **Performance** - More efficient than HTTP for some scenarios
- **Bidirectional** - Supports true bidirectional messaging
- **Limited** - Less common than HTTP transport
