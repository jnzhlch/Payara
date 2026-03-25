# CLAUDE.md - Jersey MVC Connector

This file provides guidance for working with the `appserver/web/jersey-mvc-connector` module - Jersey MVC (Model-View-Controller) JSP integration connector.

## Module Overview

The jersey-mvc-connector module provides integration between Jersey (JAX-RS) and JSP for MVC-style web applications. It registers the Jersey MVC TLD (Tag Library Descriptor) to enable JSP rendering for Jersey resources.

**Key Components:**
- **JerseyMvcTldProvider** - Jersey MVC TLD registration

## Build Commands

```bash
# Build jersey-mvc-connector module
mvn -DskipTests clean package -f appserver/web/jersey-mvc-connector/pom.xml
```

## Architecture

```
JAX-RS Resource
       │
       ▼
[Jersey MVC]
       │
       ├─→ Returns Viewable
       │
       ▼
[JSP Template]
       │
       ├─→ Resolved via TLD
       ├─→ Template path: /WEB-INF/views/
       └─→ Template processor
       │
       ▼
[Rendered HTML Response]
```

## Jersey MVC Pattern

### MVC Resource

```java
@Path("/users")
public class UserResource {

    @GET
    @Path("/{id}")
    @Produces(MediaType.TEXT_HTML)
    public Viewable getUser(@PathParam("id") Long id) {
        User user = userService.findById(id);

        // Return Viewable that points to JSP template
        return new Viewable("/user.jsp", user);
    }

    @POST
    public Response createUser(@FormParam("name") String name) {
        User user = userService.create(name);
        // Redirect to GET request
        return Response.seeOther(URI.create("/users/" + user.getId())).build();
    }
}
```

### JSP Template (user.jsp)

```jsp
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html>
<head>
    <title>User Profile</title>
</head>
<body>
    <h1>User Profile</h1>

    <!-- The model is available as request attribute "it" -->
    <p>Name: ${it.name}</p>
    <p>Email: ${it.email}</p>

    <form method="post" action="/users/${it.id}">
        <input type="submit" value="Update"/>
    </form>
</body>
</html>
```

## MVC Configuration

### Register MVC Feature

```java
@ApplicationPath("/api")
public class MyApplication extends ResourceConfig {

    public MyApplication() {
        // Register MVC feature
        packages("com.example.resources");
        register(MvcFeature.class);

        // Configure template processor
        property(JspMvcFeature.TEMPLATE_BASE_PATH, "/WEB-INF/views/");
    }
}
```

### web.xml Configuration

```xml
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         version="6.0">

    <servlet>
        <servlet-name>Jersey</servlet-name>
        <servlet-class>org.glassfish.jersey.servlet.ServletContainer</servlet-class>
        <init-param>
            <param-name>jersey.config.server.provider.packages</param-name>
            <param-value>com.example.resources</param-value>
        </init-param>
        <init-param>
            <param-name>jersey.config.server.mvc.templateBasePath.jsp</param-name>
            <param-value>/WEB-INF/views/</param-value>
        </init-param>
    </servlet>

    <servlet-mapping>
        <servlet-name>Jersey</servlet-name>
        <url-pattern>/api/*</url-pattern>
    </servlet-mapping>
</web-app>
```

## Template Models

### Using Default Model ("it")

```java
@Path("/product")
public class ProductResource {

    @GET
    @Path("/{id}")
    public Viewable getProduct(@PathParam("id") Long id) {
        Product product = productService.findById(id);
        // Model is automatically available as "it" in JSP
        return new Viewable("/product.jsp", product);
    }
}
```

```jsp
<!-- product.jsp -->
<h1>${it.name}</h1>
<p>Price: $${it.price}</p>
```

### Using Custom Model

```java
@Path("/dashboard")
public class DashboardResource {

    @GET
    public Viewable getDashboard() {
        Map<String, Object> model = new HashMap<>();
        model.put("user", getCurrentUser());
        model.put("stats", getStats());
        model.put("notifications", getNotifications());

        return new Viewable("/dashboard.jsp", model);
    }
}
```

```jsp
<!-- dashboard.jsp -->
<h1>Welcome ${user.name}!</h1>

<h2>Statistics</h2>
<c:forEach var="stat" items="${stats}">
    <p>${stat.key}: ${stat.value}</p>
</c:forEach>

<h2>Notifications</h2>
<ul>
<c:forEach var="notif" items="${notifications}">
    <li>${notif.message}</li>
</c:forEach>
</ul>
```

### Using Request Attributes

```java
@Path("/profile")
public class ProfileResource {

    @GET
    public Viewable getProfile(@Context HttpServletRequest request) {
        User user = getCurrentUser();
        List<String> roles = getRoles();

        // Manually set request attributes
        request.setAttribute("user", user);
        request.setAttribute("roles", roles);

        return new Viewable("/profile.jsp");
    }
}
```

## Template Resolution

### Template Base Path

```
WEB-INF/views/
├── user.jsp
├── product.jsp
├── dashboard.jsp
└── layouts/
    ├── main.jsp
    └── admin.jsp
```

### Absolute Template Path

```java
// Resolved to /WEB-INF/views/user.jsp
return new Viewable("/user.jsp", model);

// Resolved to /WEB-INF/views/layouts/main.jsp
return new Viewable("/layouts/main.jsp", model);
```

### Relative Template Path

```java
// Resolved relative to resource class package
// If resource is com.example.resources.UserResource
// Template is /WEB-INF/views/com/example/resources/user.jsp
return new Viewable("user.jsp", model);
```

## MVC with Form Processing

### Resource with Form

```java
@Path("/contact")
public class ContactResource {

    @GET
    public Viewable showForm() {
        return new Viewable("/contact.jsp");
    }

    @POST
    public Response submitForm(
            @FormParam("name") String name,
            @FormParam("email") String email,
            @FormParam("message") String message) {

        // Process form
        contactService.send(name, email, message);

        // Add flash message
        // Note: Requires flash scope implementation
        return Response.seeOther(URI.create("/contact/success")).build();
    }

    @GET
    @Path("/success")
    public Viewable success() {
        return new Viewable("/contact-success.jsp");
    }
}
```

### JSP Form Template

```jsp
<!-- contact.jsp -->
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

<!DOCTYPE html>
<html>
<head>
    <title>Contact Us</title>
</head>
<body>
    <h1>Contact Us</h1>

    <form method="post" action="/contact">
        <div>
            <label for="name">Name:</label>
            <input type="text" id="name" name="name" required/>
        </div>

        <div>
            <label for="email">Email:</label>
            <input type="email" id="email" name="email" required/>
        </div>

        <div>
            <label for="message">Message:</label>
            <textarea id="message" name="message" required></textarea>
        </div>

        <button type="submit">Send</button>
    </form>
</body>
</html>
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jersey-server` | Jersey server-side JAX-RS |
| `jersey-mvc` | Jersey MVC support |
| `jersey-mvc-jsp` | Jersey MVC JSP templates |
| `jakarta.servlet-api` | Servlet API |
| `jakarta.jsp-api` | JSP API |

## Notes

- **TLD Registration** - Registers Jersey MVC tag library descriptor
- **JSP Rendering** - Enables JSP templates for JAX-RS resources
- **Model Access** - Model available as "it" or custom attributes
- **Template Base Path** - Configurable template location (default: /WEB-INF/views/)
