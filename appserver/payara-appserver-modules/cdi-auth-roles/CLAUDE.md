# CLAUDE.md - CDI Auth Roles

This file provides guidance for working with the `cdi-auth-roles` module - CDI interceptor for role-based authorization.

## Module Overview

The cdi-auth-roles module provides a CDI interceptor that enforces role-based authorization using the `@RolesPermitted` annotation. It extends Jakarta EE security by providing fine-grained authorization at the method level with support for EL expressions and flexible role semantics.

**Key Features:**
- **@RolesPermitted Annotation** - Declare required roles on classes or methods
- **AND/OR Semantics** - Require all roles (AND) or any role (OR)
- **EL Expression Support** - Use EL expressions in role names
- **JAX-RS Integration** - Works with JAX-RS endpoints
- **CDI Interceptor** - Standard CDI interceptor chain integration
- **Auto-Authentication** - Triggers authentication if not authenticated

## Build Commands

```bash
# Build cdi-auth-roles module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/cdi-auth-roles/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/cdi-auth-roles/pom.xml
```

## Architecture

### Interceptor Flow

```
Method Invocation with @RolesPermitted
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│           RolesPermittedInterceptor.method()                   │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Get @RolesPermitted annotation                       │ │
│  │     - Check Weld interceptor bindings                    │ │
│  │     - Check method annotation                            │ │
│  │     - Check class annotation                             │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Check access permitted                               │ │
│  │     isAccessPermitted = checkAccessPermitted(roles, ctx)  │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. If not permitted, throw exception                    │ │
│  │     throw new CallerAccessException(...)                 │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  4. If permitted, proceed                                │ │
│  │     return ctx.proceed()                                 │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│          checkAccessPermitted(roles, ctx)                       │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Authenticate if not authenticated                     │ │
│  │     authenticate(roles.value())                          │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Create ELProcessor if EL expressions present         │ │
│  │     if (hasAnyELExpression(roles.value())) { ... }        │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Check roles based on semantics                       │ │
│  │     if (OR semantics) {                                  │ │
│  │       // Return true if ANY role matches                 │ │
│  │     } else if (AND semantics) {                          │ │
│  │       // Return true if ALL roles match                  │ │
│  │     }                                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### Authentication Flow

```
authenticate(roles)
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│  1. Check if HttpServletRequest available                       │
│  2. Check if caller authenticated (principal != null)           │
│  3. If not authenticated and request/response available:        │
│     ┌──────────────────────────────────────────────────────┐   │
│     │  securityContext.authenticate(request, response,     │   │
│     │      withParams())                                   │   │
│     └──────────────────────────────────────────────────────┘   │
│     ┌──────────────────────────────────────────────────────┐   │
│     │  Handle authentication status:                       │   │
│     │  - NOT_DONE → throw NotAuthorizedException           │   │
│     │  - SEND_FAILURE → throw NotAuthorizedException       │   │
│     │  - SUCCESS but no principal → throw NotAuthorized... │   │
│     └──────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
```

## Core Components

### RolesPermittedInterceptor

```java
@Interceptor
@RolesPermitted
@Priority(Interceptor.Priority.PLATFORM_AFTER + 1000)
public class RolesPermittedInterceptor implements Serializable {

    private final Bean<?> interceptedBean;
    private final NonSerializableProperties lazyProperties;

    @Context
    private transient HttpServletRequest request;

    @Context
    private transient HttpServletResponse response;

    @AroundInvoke
    public Object method(InvocationContext invocationContext) throws Exception {
        RolesPermitted roles = getRolesPermitted(invocationContext);

        boolean isAccessPermitted = checkAccessPermitted(roles, invocationContext);
        if (!isAccessPermitted) {
            throw new CallerAccessException(
                "Caller was not permitted access to a protected resource");
        }

        return invocationContext.proceed();
    }

    public boolean checkAccessPermitted(RolesPermitted roles,
                                        InvocationContext invocationContext) {
        // Authenticate first
        authenticate(roles.value());

        // Setup EL processor if needed
        ELProcessor eLProcessor = null;
        if (hasAnyELExpression(roles.value())) {
            eLProcessor = getElProcessor(invocationContext);
        }

        List<String> permittedRoles = asList(roles.value());
        final SecurityContext securityContext = lazyProperties.getSecurityContext();

        if (OR.equals(roles.semantics())) {
            // Any role match is sufficient
            for (String role : permittedRoles) {
                if (eLProcessor != null && hasAnyELExpression(role)) {
                    role = evalELExpression(eLProcessor, role);
                }
                if (securityContext.isCallerInRole(role)) {
                    return true;
                }
            }
            return false;
        } else if (AND.equals(roles.semantics())) {
            // All roles must match
            for (String role : permittedRoles) {
                if (eLProcessor != null && hasAnyELExpression(role)) {
                    role = evalELExpression(eLProcessor, role);
                }
                if (!securityContext.isCallerInRole(role)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private void authenticate(String[] roles) {
        final SecurityContext securityContext = lazyProperties.getSecurityContext();

        if (request != null && response != null
                && roles.length > 0 && !isAuthenticated(securityContext)) {
            AuthenticationStatus status =
                securityContext.authenticate(request, response, withParams());

            if (status == NOT_DONE || status == SEND_FAILURE) {
                throw new NotAuthorizedException(
                    "Authentication resulted in " + status,
                    Response.status(Response.Status.UNAUTHORIZED).build()
                );
            }
        }
    }
}
```

**Purpose:** CDI interceptor that enforces @RolesPermitted authorization.

### RolesCDIExtension

```java
public class RolesCDIExtension implements Extension {

    void beforeBeanDiscovery(@Observes BeforeBeanDiscovery beforeBeanDiscovery,
                             BeanManager beanManager) {
        // Register @RolesPermitted as interceptor binding
        beforeBeanDiscovery.addInterceptorBinding(RolesPermitted.class);

        // Register the interceptor
        beforeBeanDiscovery.addAnnotatedType(
            beanManager.createAnnotatedType(RolesPermittedInterceptor.class),
            "RolesCDIExtension " + RolesPermittedInterceptor.class.getName()
        );
    }
}
```

**Purpose:** CDI extension that registers the @RolesPermitted interceptor binding and interceptor class.

### CallerAccessExceptionMapper

```java
@Provider
public class CallerAccessExceptionMapper implements ExceptionMapper<CallerAccessException> {

    @Override
    public Response toResponse(CallerAccessException cae) {
        return Response.status(FORBIDDEN)
                .entity(cae.getMessage())
                .build();
    }
}
```

**Purpose:** JAX-RS exception mapper that converts CallerAccessException to HTTP 403 FORBIDDEN response.

### NonSerializableProperties

```java
public class NonSerializableProperties implements Serializable {
    private transient BeanManager beanManager;
    private transient SecurityContext securityContext;

    public BeanManager getBeanManager() {
        if (beanManager == null) {
            beanManager = CDI.current().getBeanManager();
        }
        return beanManager;
    }

    public SecurityContext getSecurityContext() {
        if (securityContext == null) {
            securityContext = CDI.current().select(SecurityContext.class).get();
        }
        return securityContext;
    }
}
```

**Purpose:** Holds non-serializable references (BeanManager, SecurityContext) that are lazily initialized after deserialization.

## Usage Examples

### Basic Role Check (OR Semantics)

```java
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    @GET
    @RolesPermitted({"ADMIN", "USER_MANAGER"})
    public Response getAllUsers() {
        // Accessible if caller has EITHER "ADMIN" OR "USER_MANAGER" role
        List<User> users = userService.findAll();
        return Response.ok(users).build();
    }
}
```

### AND Semantics (All Roles Required)

```java
@POST
@RolesPermitted(value = {"ADMIN", "BILLING"}, semantics = LogicalOperator.AND)
public Response createInvoice(Invoice invoice) {
    // Accessible only if caller has BOTH "ADMIN" AND "BILLING" roles
    invoiceService.create(invoice);
    return Response.status(Status.CREATED).build();
}
```

### Class-Level Annotation

```java
@RolesPermitted("ADMIN")
@Path("/api/admin")
public class AdminResource {

    @GET
    public Response getAdminData() {
        // Requires ADMIN role
        return Response.ok(adminService.getData()).build();
    }

    @GET
    @Path("/reports")
    @RolesPermitted({"ADMIN", "REPORTER"})  // Override with additional role
    public Response getReports() {
        // Requires ADMIN OR REPORTER role
        return Response.ok(reportService.getReports()).build();
    }
}
```

### EL Expression Support

```java
@POST
@RolesPermitted("#{self.owner}")
public Response updateOwnData(@PathParam("id") String id, Data data) {
    // Role name is resolved from the target object's owner property
    // If data.getOwner() returns "john", checks for role "john"
    dataService.update(id, data);
    return Response.ok().build();
}

@GET
@RolesPermitted({"#{param.region}Admin"})
public Response getRegionalData(@Named("region") String region) {
    // Parameter is available in EL as "param"
    // If region = "eu", checks for role "euAdmin"
    return Response.ok(dataService.getRegionalData(region)).build();
}
```

### CDI Bean Annotation

```java
@ApplicationScoped
public class SecureService {

    @RolesPermitted("USER")
    public void doSomething() {
        // Secure method
    }

    @RolesPermitted(value = {"ADMIN", "MODERATOR"}, semantics = LogicalOperator.OR)
    public void doAdminTask() {
        // Secure method with multiple roles
    }
}
```

## Annotation Reference

### @RolesPermitted

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | String[] | {} | Required role names (can include EL expressions) |
| `semantics` | LogicalOperator | OR | AND = all roles required, OR = any role sufficient |

### LogicalOperator Enum

| Value | Description |
|-------|-------------|
| `OR` | Caller must have at least one of the specified roles (default) |
| `AND` | Caller must have all of the specified roles |

## EL Expression Context

The following variables are available in EL expressions:

| Variable | Description |
|----------|-------------|
| `self` | The target instance (the bean being intercepted) |
| `param` | The single method parameter (if only one parameter exists) |
| `@Named` parameters | Method parameters annotated with `@Named` |

## Exception Handling

### CallerAccessException

Thrown when the caller doesn't have the required roles:

```java
if (!isAccessPermitted) {
    throw new CallerAccessException(
        "Caller was not permitted access to a protected resource");
}
```

### JAX-RS Response Mapping

The `CallerAccessExceptionMapper` converts the exception to:

```java
HTTP 403 FORBIDDEN
Content-Type: text/plain
Body: "Caller was not permitted access to a protected resource"
```

### NotAuthorizedException

Thrown when authentication fails:

```java
if (status == NOT_DONE || status == SEND_FAILURE) {
    throw new NotAuthorizedException(
        "Authentication resulted in " + status,
        Response.status(Response.Status.UNAUTHORIZED).build()
    );
}
```

## Priority

The interceptor uses `PLATFORM_AFTER + 1000` priority:

```java
@Priority(Interceptor.Priority.PLATFORM_AFTER + 1000)
```

This ensures:
- Runs after most platform interceptors
- Can be overridden by application interceptors with higher priority

## Package Structure

```
cdi-auth-roles/
└── src/main/java/fish/payara/appserver/cdi/auth/roles/
    ├── RolesPermittedInterceptor.java           # Main interceptor
    ├── NonSerializableProperties.java           # Lazy property holder
    ├── CallerAccessExceptionAutoDiscoverable.java  # JAX-RS auto-discovery
    ├── CallerAccessExceptionMapper.java         # JAX-RS exception mapper
    └── extension/
        └── RolesCDIExtension.java               # CDI extension
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.security.enterprise` | Jakarta Security API (SecurityContext) |
| `jakarta.interceptor` | CDI interceptor API |
| `jakarta.ws.rs` | JAX-RS API |
| `jakarta.enterprise` | CDI API |

## Integration Points

### Jakarta Security

Uses `SecurityContext` for authentication and role checking:

```java
@Inject
private SecurityContext securityContext;

boolean isInRole = securityContext.isCallerInRole("ADMIN");
Principal caller = securityContext.getCallerPrincipal();
```

### Soteria

Integrates with GlassFish Soteria (Jakarta Security implementation):
- Uses `Soteria` EL expression evaluation
- Uses `AnnotationELPProcessor` for EL processing
- Uses `CdiUtils` for annotation resolution

### Weld

Uses Weld-specific features:
- `@Intercepted` for bean injection
- Weld interceptor bindings from context data
- Stereotype support for annotation discovery

## Notes

- **Serializable** - Interceptor implements Serializable for session replication
- **Transient Fields** - HttpServletRequest/Response are transient, recreated via CDI
- **Lazy Properties** - BeanManager and SecurityContext lazily loaded after deserialization
- **Weld Bindings** - First checks Weld interceptor bindings for accurate annotation resolution
- **Stereotype Support** - Supports @RolesPermitted on stereotypes
- **Authentication Trigger** - Automatically triggers authentication if not authenticated
- **EL Safety** - EL processor only created when EL expressions detected
