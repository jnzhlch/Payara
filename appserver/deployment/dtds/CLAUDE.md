# CLAUDE.md - Deployment DTDs

This file provides guidance for working with the `dtds` module - Legacy DTD deployment descriptors.

## Module Overview

The dtds module contains legacy Document Type Definition (DTD) files for validating Java EE deployment descriptors. These are for backward compatibility with older Java EE applications.

## Build Commands

```bash
# Build dtds module
mvn -DskipTests clean package -f appserver/deployment/dtds/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Legacy DTD files for deployment descriptor validation

## DTD Files

### Standard Java EE DTDs

| DTD | Version | Purpose |
|-----|---------|---------|
| `application_1_2.dtd` | J2EE 1.2 | Application descriptor |
| `application_1_3.dtd` | J2EE 1.3 | Application descriptor |
| `ejb-jar_1_1.dtd` | EJB 1.1 | EJB descriptor |
| `ejb-jar_2_0.dtd` | EJB 2.0 | EJB descriptor |
| `web-app_2_2.dtd` | Servlet 2.2 | Web descriptor |
| `web-app_2_3.dtd` | Servlet 2.3 | Web descriptor |
| `application-client_1_2.dtd` | J2EE 1.2 | App client descriptor |
| `application-client_1_3.dtd` | J2EE 1.3 | App client descriptor |
| `web-jsptaglibrary_1_1.dtd` | JSP 1.1 | Tag library descriptor |
| `web-jsptaglibrary_1_2.dtd` | JSP 1.2 | Tag library descriptor |

### GlassFish/Payara Specific DTDs

| DTD | Purpose |
|-----|---------|
| `glassfish-application_6_0-1.dtd` | Application config |
| `glassfish-web-app_3_0-1.dtd` | Web container config |
| `glassfish-ejb-jar_3_1-1.dtd` | EJB container config |
| `glassfish-application-client_6_0-1.dtd` | App client config |
| `glassfish-application-client-container_1_3.dtd` | App client container |
| `payara-web-app_4.dtd` | Payara web config |

### Sun/GlassFish Legacy DTDs

| DTD | Version | Purpose |
|-----|---------|---------|
| `sun-application_1_3-0.dtd` | 1.3 | Application config |
| `sun-application_1_4-0.dtd` | 1.4 | Application config |
| `sun-application_5_0-0.dtd` | 5.0 | Java EE 5 config |
| `sun-application_6_0-0.dtd` | 6.0 | Java EE 6 config |
| `sun-web-app_2_3-0.dtd` | 2.3 | Web config |
| `sun-web-app_2_4-0.dtd` | 2.4 | Web config |
| `sun-web-app_2_5-0.dtd` | 2.5 | Web config |
| `sun-web-app_3_0-0.dtd` | 3.0 | Web config (Java EE 6) |
| `sun-ejb-jar_2_0-0.dtd` | 2.0 | EJB config |
| `sun-ejb-jar_2_1-0.dtd` | 2.1 | EJB config |
| `sun-ejb-jar_3_0-0.dtd` | 3.0 | EJB config (Java EE 5) |
| `sun-ejb-jar_3_1-0.dtd` | 3.1 | EJB config (Java EE 6) |
| `sun-cmp-mapping_1_0.dtd` | 1.0 | CMP mapping |
| `sun-cmp-mapping_1_1.dtd` | 1.1 | CMP mapping |
| `sun-cmp-mapping_1_2.dtd` | 1.2 | CMP mapping |
| `sun-loadbalancer_1_0.dtd` | 1.0 | Load balancer config |
| `sun-loadbalancer_1_1.dtd` | 1.1 | Load balancer config |

### Application Client Container DTDs

| DTD | Purpose |
|-----|---------|
| `sun-application-client-container_1_0.dtd` | ACC config 1.0 |
| `sun-application-client-container_1_1.dtd` | ACC config 1.1 |
| `sun-application-client-container_1_2.dtd` | ACC config 1.2 |
| `glassfish-application-client-container_1_3.dtd` | ACC config 1.3 |

### Application Client DTDs

| DTD | Version | Purpose |
|-----|---------|---------|
| `sun-application-client_1_3-0.dtd` | 1.3 | App client config |
| `sun-application-client_1_4-0.dtd` | 1.4 | App client config |
| `sun-application-client_1_4-1.dtd` | 1.4.1 | App client config |
| `sun-application-client_5_0-0.dtd` | 5.0 | Java EE 5 config |
| `sun-application-client_6_0-0.dtd` | 6.0 | Java EE 6 config |
| `glassfish-application-client_6_0-1.dtd` | 6.0.1 | Updated config |
| `glassfish-application-client_6_0-2.dtd` | 6.0.2 | Updated config |

## DTD Usage

### DOCTYPE Declaration

Applications using DTDs declare them in XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE web-app PUBLIC
    "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
    "http://java.sun.com/dtd/web-app_2_3.dtd">
<web-app>
    ...
</web-app>
```

### Validation Flow

```
Deployment Descriptor (XML)
       │
   [Parse DOCTYPE]
       │
   [Resolve DTD location]
       │
   [Load DTD from classpath]
       │
   [Validate against DTD]
       │
   Validated Descriptor
```

## Migration to XSD

### DTD vs XSD

| Feature | DTD | XSD |
|---------|-----|-----|
| **Syntax** | Different from XML | XML-based |
| **Namespaces** | No | Yes |
| **Data Types** | Limited | Rich types |
| **Extensibility** | Limited | Good |
| **Java EE Version** | 1.2 - 1.3 | 1.4+ |

### Example Migration

**DTD (old):**
```xml
<?xml version="1.0"?>
<!DOCTYPE ejb-jar PUBLIC
    "-//Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2.0//EN"
    "http://java.sun.com/dtd/ejb-jar_2_0.dtd">
<ejb-jar>
    <enterprise-beans>
        <session>
            <ejb-name>MyEJB</ejb-name>
            <home>com.example.MyHome</home>
            <remote>com.example.MyRemote</remote>
            <session-type>Stateless</session-type>
        </session>
    </enterprise-beans>
</ejb-jar>
```

**XSD (new):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<ejb-jar xmlns="http://java.sun.com/xml/ns/javaee"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://java.sun.com/xml/ns/javaee
    http://java.sun.com/xml/ns/javaee/ejb-jar_3_0.xsd"
    version="3.0">
    <enterprise-beans>
        <session>
            <ejb-name>MyEJB</ejb-name>
            <business-remote>com.example.MyRemote</business-remote>
            <session-type>Stateless</session-type>
        </session>
    </enterprise-beans>
</ejb-jar>
```

## File Location

DTDs are installed at:
```
$PAYARA_HOME/glassfish/lib/dtds/
```

## DTD Resolution

### Public ID to System Mapping

```
-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN
    → glassfish/lib/dtds/web-app_2_3.dtd

-//Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2.0//EN
    → glassfish/lib/dtds/ejb-jar_2_0.dtd
```

### Resolution in Code

```java
// EntityResolver for DTD resolution
public class DTDEntityResolver implements EntityResolver {
    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
        // Load DTD from classpath
        String dtdPath = "/glassfish/lib/dtds/" + getDTDName(publicId);
        URL dtdUrl = getClass().getResource(dtdPath);
        return new InputSource(dtdUrl.toString());
    }
}
```

## Backward Compatibility

### Support for Legacy Applications

Payara maintains support for:
1. **J2EE 1.3 applications** - Using DTDs
2. **J2EE 1.4 applications** - Using XSDs
3. **Java EE 5+ applications** - Using annotations + XSDs

### Descriptor Version Detection

```java
// Determine descriptor version
if (descriptor.getDoctype() != null) {
    // DTD-based (J2EE 1.3)
    version = detectDTDVersion(descriptor.getDoctype());
} else if (descriptor.getSchemaLocation() != null) {
    // XSD-based (J2EE 1.4+)
    version = detectXSDVersion(descriptor.getSchemaLocation());
} else {
    // Annotation-based (Java EE 5+)
    version = "6.0";  // Default to Java EE 6
}
```

## Payara-Specific Extensions

### Payara Web App DTD

```xml
<!DOCTYPE payara-web-app PUBLIC
    "-//Payara Foundation//DTD Payara Web Application 4//EN"
    "glassfish/lib/dtds/payara-web-app_4.dtd">
<payara-web-app>
    <!-- Payara-specific web configuration -->
    <context-root>/myapp</context-root>
    <request-character-encoding>UTF-8</request-character-encoding>
</payara-web-app>
```

## Related Modules

- `deployment/schemas` - XSD schema files (newer)
- `deployment/dol` - Descriptor parsing
- `deployment/javaee-core` - Deployment services

## Dependencies

This module contains only DTD resources with no code dependencies.
