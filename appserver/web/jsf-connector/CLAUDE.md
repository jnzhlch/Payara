# CLAUDE.md - JSF Connector

This file provides guidance for working with the `appserver/web/jsf-connector` module - Jakarta Server Faces (Mojarra) integration connector.

## Module Overview

The jsf-connector module provides integration between GlassFish/Payara and Mojarra (Jakarta Server Faces implementation). It handles JSF application detection, TLD (Tag Library Descriptor) registration, and injection provider setup.

**Key Components:**
- **MojarraSniffer** - JSF application type detection
- **GlassFishInjectionProvider** - CDI integration for JSF
- **GlassFishTldProvider** - JSF TLD registration

## Build Commands

```bash
# Build jsf-connector module
mvn -DskipTests clean package -f appserver/web/jsf-connector/pom.xml
```

## Architecture

```
JSF Application Deployment
       │
       ▼
[MojarraSniffer]
       │
       ├─→ Detects faces-config.xml
       ├─→ Detects .xhtml, .jsf, .faces files
       ├─→ Detects @FacesConfig, @FaceletsContext
       └─→ Registers JSF container
       │
       ▼
[JSF Container Integration]
       │
       ├─→ GlassFishInjectionProvider
       │     ├─→ CDI injection for JSF artifacts
       │     ├─→ @Inject in @FacesConverter, @FacesValidator
       │     └─→ @Inject in @ManagedBean (legacy)
       │
       ├─→ GlassFishTldProvider
       │     ├─→ Registers JSF TLDs
       │     └─→ Enables facelets tag libraries
       │
       └─→ Mojarra (JSF Implementation)
```

## JSF Detection

### MojarraSniffer

```java
@Service
public class MojarraSniffer implements Sniffer {

    @Override
    public boolean handles(String type) {
        return "jsf".equals(type) || "faces".equals(type);
    }

    @Override
    public String[] getContainers() {
        return new String[] { "com.sun.faces.application.Application" };
    }

    @Override
    public boolean handles(ReadableArchive archive) {
        // Detect JSF applications by:
        // 1. WEB-INF/faces-config.xml
        // 2. @FacesConfig annotation
        // 3. Facelets templates (.xhtml)
        return hasFacesConfig(archive) ||
               hasFacesAnnotation(archive) ||
               hasFaceletsFiles(archive);
    }
}
```

### Detection Methods

| Method | Description |
|--------|-------------|
| `WEB-INF/faces-config.xml` | JSF configuration file |
| `@FacesConfig` annotation | JSF 2.3+ annotation-based config |
| `.xhtml` files | Facelets views |
| `@FacesComponent` | Custom JSF components |
| `@FacesConverter` | JSF converters |
| `@FacesValidator` | JSF validators |

## CDI Integration for JSF

### GlassFishInjectionProvider

```java
public class GlassFishInjectionProvider extends InjectionProvider {

    private final WebBeanInjectionBuilder builder;

    @Override
    public Object inject(Object managedBean) throws InjectionException {
        // Perform CDI injection on JSF artifacts
        return builder.buildInjectionTarget(managedBean);
    }

    @Override
    public void invokePostConstruct(Object managedBean) {
        // Call @PostConstruct method
    }

    @Override
    public void invokePreDestroy(Object managedBean) {
        // Call @PreDestroy method
    }
}
```

### Injecting into JSF Artifacts

```java
@FacesConverter("userConverter")
@RequestScoped
public class UserConverter implements Converter {

    @Inject
    private UserService userService; // CDI injection

    @Override
    public Object getAsObject(FacesContext context, UIComponent component, String value) {
        return userService.findById(Long.valueOf(value));
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, Object value) {
        return value != null ? ((User) value).getId().toString() : "";
    }
}
```

```java
@FacesValidator("emailValidator")
@RequestScoped
public class EmailValidator implements Validator {

    @Inject
    private ValidationService validationService;

    @Override
    public void validate(FacesContext context, UIComponent component, Object value) {
        String email = (String) value;
        if (!validationService.isValidEmail(email)) {
            throw new ValidatorException(new FacesMessage("Invalid email"));
        }
    }
}
```

## JSF Configuration

### faces-config.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<faces-config xmlns="https://jakarta.ee/xml/ns/jakartaee"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                                  https://jakarta.ee/xml/ns/jakartaee/faces-config_4_0.xsd"
              version="4.0">

    <application>
        <default-render-kit-id>jakarta.faces.Basic</default-render-kit-id>
        <resource-handler>fish.payara.primefaces.PrimeFacesResourceHandler</resource-handler>
    </application>

    <converter>
        <converter-id>userConverter</converter-id>
        <converter-class>com.example.UserConverter</converter-class>
    </converter>

    <validator>
        <validator-id>emailValidator</validator-id>
        <validator-class>com.example.EmailValidator</validator-class>
    </validator>
</faces-config>
```

### Annotation-based Configuration (JSF 2.3+)

```java
@FacesConfig
@ApplicationScoped
public class MyFacesConfig {

    // Empty class activates JSF CDI integration
}
```

## JSF Facelets

### Facelets Template

```html
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:h="jakarta.faces.html"
      xmlns:f="jakarta.faces.core"
      xmlns:ui="jakarta.faces.facelets">

    <h:head>
        <title>My App</title>
    </h:head>

    <h:body>
        <ui:composition template="/WEB-INF/templates/layout.xhtml">
            <ui:define name="content">
                <h1>Welcome</h1>
                <h:form>
                    <h:inputText value="#{bean.name}" required="true"/>
                    <h:commandButton value="Submit" action="#{bean.submit}"/>
                </h:form>
            </ui:define>
        </ui:composition>
    </h:body>
</html>
```

### CDI Backing Bean

```java
@Named
@ViewScoped
public class Bean implements Serializable {

    @Inject
    private SomeService service;

    private String name;

    public String submit() {
        service.process(name);
        return "success";
    }

    // Getters and setters
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.faces-api` | Jakarta Faces API |
| `mojarra` | JSF implementation |
| `weld` | CDI integration |
| `jakarta.servlet-api` | Servlet API |

## Notes

- **Mojarra** - Jakarta Server Faces implementation
- **CDI Integration** - Full CDI support for JSF artifacts
- **Facelets** - Default view technology
- **JSF 4.0** - Jakarta EE 10 Faces specification
