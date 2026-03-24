# CLAUDE.md - Deployment Schemas

This file provides guidance for working with the `schemas` module - XML schema deployment descriptors.

## Module Overview

The schemas module contains XML Schema Definition (XSD) files for validating Jakarta EE deployment descriptors. These are the current standard for descriptor validation.

## Build Commands

```bash
# Build schemas module
mvn -DskipTests clean package -f appserver/deployment/schemas/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: XML schema files for deployment descriptor validation

## Core Platform Schemas

### Jakarta EE Platform Schemas

| Schema | Version | Namespace |
|--------|---------|-----------|
| `javaee_5.xsd` | Java EE 5 | `http://java.sun.com/xml/ns/javaee` |
| `javaee_6.xsd` | Java EE 6 | `http://java.sun.com/xml/ns/javaee` |
| `javaee_7.xsd` | Java EE 7 | `http://xmlns.jcp.org/xml/ns/javaee` |
| `javaee_8.xsd` | Java EE 8 | `http://xmlns.jcp.org/xml/ns/javaee` |
| `jakartaee_9.xsd` | Jakarta EE 9 | `https://jakarta.ee/xml/ns/jakartaee` |
| `jakartaee_10.xsd` | Jakarta EE 10 | `https://jakarta.ee/xml/ns/jakartaee` |
| `jakartaee_11.xsd` | Jakarta EE 11 | `https://jakarta.ee/xml/ns/jakartaee` |

### J2EE 1.4 Schemas

| Schema | Purpose |
|--------|---------|
| `j2ee_1_4.xsd` | J2EE 1.4 base schema |
| `j2ee_web_services_1_1.xsd` | JAX-RPC web services |
| `j2ee_web_services_client_1_1.xsd` | JAX-RPC client |
| `j2ee_jaxrpc_mapping_1_1.xsd` | JAX-RPC mapping |

## Application Schemas

### Application (EAR)

| Schema | Version | Description |
|--------|---------|-------------|
| `application_1_4.xsd` | J2EE 1.4 | EAR descriptor |
| `application_5.xsd` | Java EE 5 | EAR descriptor |
| `application_6.xsd` | Java EE 6 | EAR descriptor |
| `application_7.xsd` | Java EE 7 | EAR descriptor |
| `application_8.xsd` | Java EE 8 | EAR descriptor |
| `application_9.xsd` | Jakarta EE 9 | EAR descriptor |
| `application_10.xsd` | Jakarta EE 10 | EAR descriptor |
| `application_11.xsd` | Jakarta EE 11 | EAR descriptor |

### Example application.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<application xmlns="https://jakarta.ee/xml/ns/jakartaee"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
    https://jakarta.ee/xml/ns/jakartaee/application_11.xsd"
    version="11">
    <application-name>myapp</application-name>
    <module>
        <web>
            <web-uri>myweb.war</web-uri>
            <context-root>/myapp</context-root>
        </web>
    </module>
    <module>
        <ejb>myejb.jar</ejb>
    </module>
    <library-directory>lib</library-directory>
</application>
```

## Web Application Schemas

### Web (WAR)

| Schema | Version | Servlet Version |
|--------|---------|-----------------|
| `web-app_2_4.xsd` | Servlet 2.4 | J2EE 1.4 |
| `web-app_2_5.xsd` | Servlet 2.5 | Java EE 5 |
| `web-app_3_0.xsd` | Servlet 3.0 | Java EE 6 |
| `web-app_3_1.xsd` | Servlet 3.1 | Java EE 7 |
| `web-app_4_0.xsd` | Servlet 4.0 | Java EE 8 |
| `web-app_5_0.xsd` | Servlet 5.0 | Jakarta EE 9 |
| `web-app_6_0.xsd` | Servlet 6.0 | Jakarta EE 10 |
| `web-app_6_1.xsd` | Servlet 6.1 | Jakarta EE 11 |

### Web Common Schemas

Shared web components:

| Schema | Version | Description |
|--------|---------|-------------|
| `web-common_3_0.xsd` | Java EE 6 | Common web elements |
| `web-common_3_1.xsd` | Java EE 7 | Common web elements |
| `web-common_4_0.xsd` | Java EE 8 | Common web elements |
| `web-common_5_0.xsd` | Jakarta EE 9 | Common web elements |
| `web-common_6_0.xsd` | Jakarta EE 10 | Common web elements |
| `web-common_6_1.xsd` | Jakarta EE 11 | Common web elements |

### Example web.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
    https://jakarta.ee/xml/ns/jakartaee/web-app_6_1.xsd"
    version="6.1">
    <servlet>
        <servlet-name>MyServlet</servlet-name>
        <servlet-class>com.example.MyServlet</servlet-class>
    </servlet>
    <servlet-mapping>
        <servlet-name>MyServlet</servlet-name>
        <url-pattern>/myservlet</url-pattern>
    </servlet-mapping>
</web-app>
```

## EJB Schemas

### EJB

| Schema | Version | EJB Version |
|--------|---------|-------------|
| `ejb-jar_2_1.xsd` | EJB 2.1 | J2EE 1.4 |
| `ejb-jar_3_0.xsd` | EJB 3.0 | Java EE 5 |
| `ejb-jar_3_1.xsd` | EJB 3.1 | Java EE 6 |
| `ejb-jar_3_2.xsd` | EJB 3.2 | Java EE 7 |
| `ejb-jar_4_0.xsd` | EJB 4.0 | Jakarta EE 9 |
| `ejb-jar_4_1.xsd` | EJB 4.1 | Jakarta EE 10/11 |

### Example ejb-jar.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ejb-jar xmlns="https://jakarta.ee/xml/ns/jakartaee"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
    https://jakarta.ee/xml/ns/jakartaee/ejb-jar_4_1.xsd"
    version="4.1">
    <enterprise-beans>
        <session>
            <ejb-name>MyEJB</ejb-name>
            <ejb-class>com.example.MyEJB</ejb-class>
            <session-type>Stateless</session-type>
        </session>
    </enterprise-beans>
</ejb-jar>
```

## JPA Schemas

### Persistence

| Schema | Version | JPA Version |
|--------|---------|-------------|
| `persistence_1_0.xsd` | JPA 1.0 | Java EE 5 |
| `persistence_2_0.xsd` | JPA 2.0 | Java EE 6 |
| `persistence_3_0.xsd` | JPA 3.0 | Jakarta EE 9/10/11 |

### ORM

| Schema | Version |
|--------|---------|
| `orm_1_0.xsd` | JPA 1.0 |
| `orm_3_0.xsd` | JPA 3.0 |
| `orm_3_1.xsd` | JPA 3.1 |

### Example persistence.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns="https://jakarta.ee/xml/ns/persistence"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence
    https://jakarta.ee/xml/ns/persistence/persistence_3_0.xsd"
    version="3.0">
    <persistence-unit name="myPU">
        <jta-data-source>jdbc/__default</jta-data-source>
        <class>com.example.MyEntity</class>
    </persistence-unit>
</persistence>
```

## Bean Validation Schemas

| Schema | Version |
|--------|---------|
| `validation-configuration-3.0.xsd` | Bean Validation 3.0 |
| `validation-mapping-3.0.xsd` | Bean Validation 3.0 |

## CDI Schemas

| Schema | Version |
|--------|---------|
| `beans_1_0.xsd` | CDI 1.0 (Java EE 6) |
| `beans_3_0.xsd` | CDI 3.0 (Jakarta EE 10) |
| `beans_4_0.xsd` | CDI 4.0 (Jakarta EE 11) |

### Example beans.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
    https://jakarta.ee/xml/ns/jakartaee/beans_4_0.xsd"
    version="4.0">
    <scan>
        <exclude name="com.example.ignored.*"/>
    </scan>
</beans>
```

## JSP Schemas

| Schema | Version |
|--------|---------|
| `jsp_2_0.xsd` | JSP 2.0 |
| `jsp_2_1.xsd` | JSP 2.1 |
| `jsp_2_2.xsd` | JSP 2.2 |
| `jsp_2_3.xsd` | JSP 2.3 |
| `jsp_3_0.xsd` | JSP 3.0 |
| `jsp_3_1.xsd` | JSP 3.1 |
| `jsp_4_0.xsd` | JSP 4.0 |

## Facelet Tag Library Schemas

| Schema | Version |
|--------|---------|
| `web-facelettaglibrary_2_0.xsd` | Facelets 2.0 |
| `web-facelettaglibrary_2_2.xsd` | Facelets 2.2 |
| `web-facelettaglibrary_2_3.xsd` | Facelets 2.3 |
| `web-facelettaglibrary_3_0.xsd` | Facelets 3.0 |
| `web-facelettaglibrary_4_0.xsd` | Facelets 4.0 |
| `web-facelettaglibrary_4_1.xsd` | Facelets 4.1 |

## Faces Configuration Schemas

| Schema | Version |
|--------|---------|
| `web-facesconfig_1_2.xsd` | JSF 1.2 |
| `web-facesconfig_2_0.xsd` | JSF 2.0 |
| `web-facesconfig_2_1.xsd` | JSF 2.1 |
| `web-facesconfig_2_2.xsd` | JSF 2.2 |
| `web-facesconfig_2_3.xsd` | JSF 2.3 |
| `web-facesconfig_3_0.xsd` | Jakarta Faces 3.0 |
| `web-facesconfig_4_0.xsd` | Jakarta Faces 4.0 |
| `web-facesconfig_4_1.xsd` | Jakarta Faces 4.1 |

## Batch Schemas

| Schema | Version |
|--------|---------|
| `jobXML_2_0.xsd` | Batch 2.0 (Jakarta EE 10) |
| `batchXML_2_0.xsd` | Batch 2.0 |

## Application Client Schemas

| Schema | Version |
|--------|---------|
| `application-client_1_4.xsd` | J2EE 1.4 |
| `application-client_5.xsd` | Java EE 5 |
| `application-client_6.xsd` | Java EE 6 |
| `application-client_7.xsd` | Java EE 7 |
| `application-client_8.xsd` | Java EE 8 |
| `application-client_9.xsd` | Jakarta EE 9 |
| `application-client_10.xsd` | Jakarta EE 10 |
| `application-client_11.xsd` | Jakarta EE 11 |

## Connector Schemas

| Schema | Version |
|--------|---------|
| `connector_2_0.xsd` | JCA 2.0 (Java EE 7) |
| `connector_2_1.xsd` | JCA 2.1 (Jakarta EE 10) |

### Example ra.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<connector xmlns="https://jakarta.ee/xml/ns/jakartaee"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
    https://jakarta.ee/xml/ns/jakartaee/connector_2_1.xsd"
    version="2.1">
    <resourceadapter>
        <outbound-resourceadapter>
            <connection-definition>
                <managedconnectionfactory-class>
                    com.example.MyManagedConnectionFactory
                </managedconnectionfactory-class>
            </connection-definition>
        </outbound-resourceadapter>
    </resourceadapter>
</connector>
```

## Web Services Schemas

| Schema | Version | Purpose |
|--------|---------|---------|
| `javaee_web_services_1_2.xsd` | JAX-WS 2.0 | Web services |
| `javaee_web_services_1_3.xsd` | JAX-WS 2.1 | Web services |
| `javaee_web_services_1_4.xsd` | JAX-WS 2.2 | Web services |
| `javaee_web_services_client_1_2.xsd` | JAX-WS 2.0 | Web service client |
| `javaee_web_services_client_1_3.xsd` | JAX-WS 2.1 | Web service client |
| `javaee_web_services_client_1_4.xsd` | JAX-WS 2.2 | Web service client |
| `jakartaee_web_services_2_0.xsd` | Jakarta EE 9 | Web services |
| `jakartaee_web_services_client_2_0.xsd` | Jakarta EE 9 | Web service client |
| `jakartaee_web_services_metadata_handler_3_0.xsd` | Jakarta EE 10 | Handler metadata |

## Permissions Schemas

| Schema | Version |
|--------|---------|
| `permissions_7.xsd` | Java EE 7 |
| `permissions_9.xsd` | Jakarta EE 9 |
| `permissions_10.xsd` | Jakarta EE 10 |

## Data Types Schema

| Schema | Purpose |
|--------|---------|
| `datatypes.dtd` | Common DTD data types |

## JAX-RPC Mapping Schema

| Schema | Purpose |
|--------|---------|
| `jax-rpc-ri-config.xsd` | JAX-PC RI config |

## File Location

Schemas are installed at:
```
$PAYARA_HOME/glassfish/lib/schemas/
```

## Schema Resolution

### Namespace Mapping

```
https://jakarta.ee/xml/ns/jakartaee
    → glassfish/lib/schemas/jakartaee_11.xsd

http://xmlns.jcp.org/xml/ns/javaee
    → glassfish/lib/schemas/javaee_8.xsd
```

### Resolution in Code

```java
// Schema resolver
public class SchemaResolver {
    public Schema loadSchema(String namespace, String schemaLocation) {
        String schemaPath = "/glassfish/lib/schemas/" +
            schemaLocation.substring(schemaLocation.lastIndexOf('/') + 1);
        URL schemaUrl = getClass().getResource(schemaPath);
        return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(schemaUrl);
    }
}
```

## Version Detection

### Schema Version from Namespace

```java
// Detect Jakarta EE version
private String detectJavaEEVersion(String namespace) {
    if (namespace.contains("jakarta.ee")) {
        // Jakarta EE 9+
        if (namespace.contains("jakartaee_11")) return "11";
        if (namespace.contains("jakartaee_10")) return "10";
        return "9";
    } else if (namespace.contains("xmlns.jcp.org")) {
        // Java EE 7-8
        if (namespace.contains("javaee_8")) return "8";
        if (namespace.contains("javaee_7")) return "7";
        return "6";
    } else {
        // Java EE 5-6
        return "5";
    }
}
```

## Related Modules

- `deployment/dtds` - Legacy DTD files
- `deployment/dol` - Descriptor parsing
- `deployment/javaee-core` - Deployment services

## Dependencies

This module contains only XSD resources with no code dependencies.
