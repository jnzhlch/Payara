# CLAUDE.md - JSR-109 Implementation

This file provides guidance for working with the `appserver/webservices/jsr109-impl` module - JSR-109 web services implementation.

## Module Overview

The jsr109-impl module provides the JSR-109 implementation for deploying web services in Jakarta EE, including service implementation beans and service references.

**Key Components:**
- **ServiceReferenceDescriptor** - Service reference metadata
- **ServiceImplBean** - Service implementation bean

## Build Commands

```bash
# Build jsr109-impl module
mvn -DskipTests clean package -f appserver/webservices/jsr109-impl/pom.xml
```

## Module Contents

```
jsr109-impl/
└── src/main/java/org/glassfish/webservices/...
    ├── ServiceReferenceDescriptor.java
    └── ServiceImplBean.java
```

## JSR-109 Deployment Descriptors

```xml
<webservices>
    <webservice-description>
        <webservice-description-name>MyService</webservice-description-name>
        <port-component>
            <port-component-name>MyPort</port-component-name>
            <wsdl-port>MyPort</wsdl-port>
            <service-endpoint-interface>com.example.MyService</service-endpoint-interface>
            <service-impl-bean>
                <servlet-link>MyServlet</servlet-link>
            </service-impl-bean>
        </port-component>
    </webservice-description>
</webservices>
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `metro-glue` | Metro integration |
| `deployment-common` | Deployment descriptors |

## Notes

- **JSR-109** - Jakarta EE web services deployment
- **Service References** - @WebServiceRef
- **Service Impl Beans** - @WebService endpoint implementations
