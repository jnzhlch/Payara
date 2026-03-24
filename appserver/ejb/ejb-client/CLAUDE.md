# CLAUDE.md - EJB Client

This file provides guidance for working with the `ejb-client` module - Core EJB client APIs.

## Module Overview

The ejb-client module provides the client-side APIs and interfaces for EJB remoting, including support for remote EJB invocation and narrow() operations.

## Build Commands

```bash
# Build ejb-client module
mvn -DskipTests clean package -f appserver/ejb/ejb-client/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Name**: "GlassFish Core EJB Client"

## Client APIs

### Remote Interfaces

```java
// Remote EJB interface
@Remote
public interface MyServiceRemote extends EJBObject {
    String doWork(String param);
}

// Client code
Properties props = new Properties();
props.put(Context.PROVIDER_URL, "iiop://localhost:3700");

InitialContext ic = new InitialContext(props);
Object obj = ic.lookup("java:global/myapp/MyService!com.example.MyServiceRemote");

MyServiceRemote service = (MyServiceRemote) PortableRemoteObject.narrow(
    obj, MyServiceRemote.class
);

String result = service.doWork("test");
```

### PortableRemoteObject

```java
// CORBA narrow operation
MyRemoteInterface narrow = (MyRemoteInterface)
    PortableRemoteObject.narrow(obj, MyRemoteInterface.class);
```

### Handle (Remote Reference)

```java
// Get handle to EJB
Handle handle = service.getHandle();

// Serialize handle (e.g., save to session)
byte[] serialized = serialize(handle);

// Later, deserialize and use
Handle restored = deserialize(serialized);
MyServiceRemote newProxy = (MyServiceRemote) restored.getEJBObject();
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.ejb-api` | EJB client API |
| `glassfish-corba-omgapi` | CORBA/IIOP support |

## Related Modules

- `ejb-container` - Server-side container
- `orb-connector` | IIOP protocol

## Protocol Support

- **IIOP/CORBA** - Default remote protocol
- **HTTP** - Via ejb-http-remoting module
