# CLAUDE.md - MicroProfile OpenAPI

This file provides guidance for working with the `microprofile/openapi` module - MicroProfile OpenAPI implementation.

## Module Overview

The microprofile/openapi module provides an implementation of the MicroProfile OpenAPI API for Payara Server. It enables applications to expose OpenAPI (Swagger) documentation for their JAX-RS endpoints automatically or through static files.

**Key Features:**
- **Automatic Documentation** - Generates OpenAPI documents from JAX-RS annotations
- **OpenAPI Annotations** - Supports @OpenAPIDefinition, @Operation, @Schema, @APIResponse, etc.
- **Static File Support** - Reads openapi.yaml/openapi.json from META-INF
- **Multiple Formats** - Returns JSON or YAML format
- **OASFilter** - Custom filtering of generated documents
- **Bean Validation** - Integrates with Jakarta Bean Validation annotations
- **Multi-Application** - Merges documents from multiple deployed applications

## Build Commands

```bash
# Build microprofile/openapi module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/openapi/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/openapi/pom.xml
```

## Architecture

### OpenAPI Document Generation Flow

```
HTTP Request GET /openapi
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│              OpenApiResource.getResponse()                     │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Check if OpenAPI service is enabled                 │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Get merged document from OpenApiService             │ │
│  │     getDocument() → merges all app documents           │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Return JSON or YAML based on Accept header          │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│              OpenApiService.getDocument()                      │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  For each registered application:                       │ │
│  │  ┌────────────────────────────────────────────────────┐ │ │
│  │  │  OpenAPISupplier.get() - Build document            │ │ │
│  │  │                                                    │ │ │
│  │  │  PROCESSOR CHAIN:                                 │ │ │
│  │  │  1. FileProcessor - Read static file              │ │ │
│  │  │  2. ModelReaderProcessor - Read model reader      │ │ │
│  │  │  3. ApplicationProcessor - Scan annotations       │ │ │
│  │  │  4. ConfigPropertyProcessor - Apply config        │ │ │
│  │  │  5. FilterProcessor - Apply OASFilter             │ │ │
│  │  │  6. BaseProcessor - Set defaults and servers      │ │ │
│  │  └────────────────────────────────────────────────────┘ │ │
│  │                                                         │ │
│  │  Merge documents with OpenAPIImpl.merge()              │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### ApplicationProcessor Scanning Flow

```
ApplicationProcessor.process()
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│            OpenApiWalker.accept(visitor)                       │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  For each allowed ClassModel:                            │ │
│  │  ┌────────────────────────────────────────────────────┐ │ │
│  │  │  1. Scan @OpenAPIDefinition on class               │ │ │
│  │  │  2. Scan JAX-RS annotations (@GET, @POST, etc.)    │ │ │
│  │  │  3. Scan @Operation on methods                     │ │ │
│  │  │  4. Scan @Parameter on method parameters           │ │ │
│  │  │  5. Scan @APIResponse/@APIResponses                │ │ │
│  │  │  6. Scan @RequestBody                              │ │ │
│  │  │  7. Scan @Schema on classes/fields/methods         │ │ │
│  │  │  8. Scan Bean Validation annotations              │ │ │
│  │  └────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### Schema Generation Flow

```
@Schema / Entity Class
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│            visitSchemaClass()                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Get or create schema in components/schemas           │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Parse @Schema annotation                            │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. For each non-transient field:                        │ │
│  │     - Create schema property                             │ │
│  │     - Apply Bean Validation constraints                 │ │
│  │     - Add to properties map                             │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  4. If superclass exists (and not java.*):              │ │
│  │     - Add allOf reference to parent                     │ │
│  │     - Merge parent properties                           │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

## Core Components

### OpenApiService

```java
@Service(name = "microprofile-openapi-service")
@Singleton
public class OpenApiService {

    private boolean enabled;
    private boolean securityEnabled;
    private boolean withCorsHeaders;

    private volatile OpenAPI cachedResult;
    private Map<String, OpenAPISupplier> documents;

    public void registerApp(String applicationId, DeploymentContext ctx) {
        final WebBundleDescriptorImpl descriptor = ctx.getModuleMetaData(WebBundleDescriptorImpl.class);
        final String contextRoot = descriptor.getContextRoot();
        final ReadableArchive archive = ctx.getSource();
        final ClassLoader classLoader = ctx.getClassLoader();
        documents.put(applicationId, new OpenAPISupplier(applicationId, contextRoot, archive, classLoader));
        cachedResult = null; // Invalidate cache
    }

    public synchronized OpenAPI getDocument() throws OpenAPIBuildException, IOException, CloneNotSupportedException {
        if (documents.isEmpty()) {
            return null;
        }
        if (cachedResult != null) {
            return cachedResult;
        }
        // Merge all application documents
        OpenAPI result = null;
        Iterator<OpenAPISupplier> iterator = documents.values().iterator();
        do {
            OpenAPI next = iterator.next().get();
            if (result == null) {
                result = ((OpenAPIImpl) next).clone();
            } else {
                OpenAPIImpl.merge(next, result, true, null);
            }
        } while (iterator.hasNext());

        this.cachedResult = result;
        return result;
    }
}
```

**Purpose:** Central service for managing OpenAPI documents per application and merging them.

### OpenApiResource

```java
@Path("/")
public class OpenApiResource {

    @GET
    @Produces({ APPLICATION_YAML, APPLICATION_JSON })
    public Response getResponse(@Context HttpServletRequest request,
                                @Context HttpServletResponse response) throws IOException {
        OpenApiService openApiService = OpenApiService.getInstance();

        if (!openApiService.isEnabled()) {
            response.sendError(FORBIDDEN.getStatusCode(), "MicroProfile OpenAPI Service is disabled.");
            return Response.status(FORBIDDEN).build();
        }

        OpenAPI document = openApiService.getDocument();
        if (document == null) {
            OpenAPI result = new BaseProcessor(new ArrayList<>()).process(new OpenAPIImpl(), null);
            return Response.status(Status.NOT_FOUND).entity(result).build();
        }

        if (((OpenAPIImpl) document).getEndpoints() != null) {
            document.addExtension("x-endpoints", ((OpenAPIImpl) document).getEndpoints());
        }

        return Response.ok(document).build();
    }
}
```

**Purpose:** JAX-RS endpoint that serves the OpenAPI document at `/openapi`.

### OASProcessor Chain

```java
public interface OASProcessor {
    OpenAPI process(OpenAPI api, OpenApiConfiguration config);
}

// Execution order in OpenAPISupplier:
// 1. FileProcessor - Reads static openapi.yaml/json
FileProcessor.process() {
    // Load from META-INF/openapi.yaml or META-INF/openapi.json
    URL fileUrl = getFirstValidOpenApiResource(appClassLoader, "../../");
    OpenAPI readResult = mapper.readValue(file, OpenAPIImpl.class);
    merge(readResult, api, true);
}

// 2. ModelReaderProcessor - Reads custom OASModelReader
ModelReaderProcessor.process() {
    OASModelReader reader = config.getModelReader().newInstance();
    reader.buildModel(api, config);
}

// 3. ApplicationProcessor - Scans JAX-RS and OpenAPI annotations
ApplicationProcessor.process() {
    OpenApiWalker walker = new OpenApiWalker<>(api, allTypes, allowedTypes, appClassLoader, scanBeanValidation);
    walker.accept(this);
}

// 4. ConfigPropertyProcessor - Applies MP Config properties
ConfigPropertyProcessor.process() {
    // Apply properties like:
    // - mp.openapi.servers
    // - mp.openapi.operations.<operationId>.servers
    // - mpogeneous.path.<path>.servers
}

// 5. FilterProcessor - Applies OASFilter
FilterProcessor.process() {
    OASFilter filter = config.getFilter().newInstance();
    return filterObject(api); // Recursively filters all model elements
}

// 6. BaseProcessor - Sets defaults and servers
BaseProcessor.process() {
    if (api.getOpenapi() == null) {
        api.setOpenapi("3.0.0");
    }
    if (api.getInfo() == null) {
        api.setInfo(new InfoImpl().title("Deployed Resources").version("1.0.0"));
    }
    if (api.getServers().isEmpty()) {
        api.addServer(new ServerImpl().url(baseURL.toString()));
    }
}
```

**Purpose:** Chain of processors that build and modify the OpenAPI document.

### OpenApiWalker

```java
public class OpenApiWalker<E extends AnnotatedElement> implements ApiWalker {

    private final Set<Type> allowedTypes;
    private final OpenApiContext context;

    public void accept(ApiVisitor visitor) {
        for (Type type : allowedTypes) {
            if (type instanceof ClassModel) {
                processAnnotation((ClassModel) type, visitor);
            }
        }
        syncSchemas();
    }

    private void processAnnotation(ClassModel annotatedClass, ApiVisitor visitor) {
        // Visit class
        visitor.visitOpenAPI(annotation, annotatedClass, context);

        // Visit methods
        for (MethodModel method : annotatedClass.getMethods()) {
            visitor.visitGET(...);
            visitor.visitPOST(...);
            visitor.visitOperation(...);
            visitor.visitParameter(...);
            // etc.
        }

        // Visit fields
        for (FieldModel field : annotatedClass.getFields()) {
            visitor.visitSchema(...);
        }
    }
}
```

**Purpose:** Visitor pattern implementation that scans classes for JAX-RS and OpenAPI annotations.

### ApplicationProcessor

```java
public class ApplicationProcessor implements OASProcessor, ApiVisitor {

    // JAX-RS method handlers
    @Override
    public void visitGET(AnnotationModel get, MethodModel element, ApiContext context) {
        PathItem pathItem = context.getApi().getPaths().getPathItems()
            .getOrDefault(context.getPath(), new PathItemImpl());
        context.getApi().getPaths().addPathItem(context.getPath(), pathItem);

        OperationImpl operation = new OperationImpl();
        pathItem.setGET(operation);
        operation.setOperationId(element.getName());
        insertDefaultResponse(context, operation, element);
    }

    @Override
    public void visitPOST(AnnotationModel post, MethodModel element, ApiContext context) {
        // Similar to GET, but also adds request body
        insertDefaultRequestBody(context, operation, element);
    }

    // Parameter handlers
    @Override
    public void visitQueryParam(AnnotationModel param, AnnotatedElement element, ApiContext context) {
        addParameter(element, context, param.getValue("value", String.class), In.QUERY, null);
    }

    @Override
    public void visitPathParam(AnnotationModel param, AnnotatedElement element, ApiContext context) {
        addParameter(element, context, param.getValue("value", String.class), In.PATH, true);
    }

    // Bean Validation handlers
    @Override
    public void visitNotEmpty(AnnotationModel param, AnnotatedElement element, ApiContext context) {
        handleBeanValidationAnnotation(param, element, context, BeanValidationType.NOT_EMPTY);
    }

    @Override
    public void visitSize(AnnotationModel param, AnnotatedElement element, ApiContext context) {
        handleBeanValidationAnnotation(param, element, context, BeanValidationType.SIZE);
    }
}
```

**Purpose:** Processes JAX-RS and OpenAPI annotations to build the OpenAPI model.

## Usage Examples

### Basic JAX-RS with Automatic Documentation

```java
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/users")
public class UserResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> getUsers() {
        return userService.findAll();
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public User getUser(@jakarta.ws.rs.PathParam("id") Long id) {
        return userService.findById(id);
    }
}
```

**Generates:**
- GET `/users` - Returns list of users
- GET `/users/{id}` - Returns single user with path parameter

### Using OpenAPI Annotations

```java
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Path("/users")
public class UserResource {

    @GET
    @Path("/{id}")
    @Operation(
        summary = "Get user by ID",
        description = "Returns a single user with the given ID",
        operationId = "getUserById",
        tags = {"users"}
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "User found",
            content = @Content(schema = @Schema(implementation = User.class))
        ),
        @APIResponse(
            responseCode = "404",
            description = "User not found"
        )
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUser(
            @Parameter(
                name = "id",
                description = "User ID",
                required = true,
                example = "123"
            )
            @jakarta.ws.rs.PathParam("id") Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(user).build();
    }
}
```

### Schema Documentation

```java
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.validation.constraints.*;
import jakarta.persistence.*;

@Schema(description = "User entity")
@Entity
public class User {

    @Schema(
        description = "User ID",
        example = "123",
        required = true
    )
    @Id
    private Long id;

    @Schema(
        description = "User email address",
        example = "user@example.com",
        required = true,
        minLength = 5,
        maxLength = 100
    )
    @NotBlank
    @Email
    @Size(min = 5, max = 100)
    private String email;

    @Schema(
        description = "User age",
        example = "25",
        minimum = "18",
        maximum = "120"
    )
    @Min(18)
    @Max(120)
    private Integer age;

    @Schema(
        description = "User roles",
        required = true
    )
    @NotEmpty
    private List<String> roles;
}
```

### Request Body Documentation

```java
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@POST
@Operation(summary = "Create a new user")
@APIResponse(responseCode = "201", description = "User created")
@APIResponse(responseCode = "400", description = "Invalid input")
public Response createUser(
        @RequestBody(
            description = "User to create",
            required = true,
            content = @Content(
                schema = @Schema(implementation = User.class),
                example = "{\"email\": \"user@example.com\", \"age\": 25}"
            )
        )
        User user) {
    userService.create(user);
    return Response.status(Response.Status.CREATED).entity(user).build();
}
```

### Application-Level OpenAPI Definition

```java
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.servers.Server;

@OpenAPIDefinition(
    info = @Info(
        title = "User Management API",
        version = "1.0.0",
        description = "API for managing users",
        contact = @Contact(
            name = "API Support",
            email = "support@example.com",
            url = "https://example.com/support"
        ),
        license = @License(
            name = "Apache 2.0",
            url = "https://www.apache.org/licenses/LICENSE-2.0.html"
        )
    ),
    servers = {
        @Server(
            url = "https://api.example.com",
            description = "Production server"
        ),
        @Server(
            url = "https://staging-api.example.com",
            description = "Staging server"
        )
    }
)
@ApplicationPath("/api")
public class MyApplication extends jakarta.ws.rs.core.Application {
}
```

### Security Scheme Documentation

```java
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.security.OAuthFlow;
import org.eclipse.microprofile.openapi.annotations.security.OAuthFlows;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@SecuritySchemes({
    @SecurityScheme(
        securitySchemeName = "oauth2",
        type = SecurityScheme.Type.OAUTH2,
        description = "OAuth2 authentication",
        flows = @OAuthFlows(
            implicit = @OAuthFlow(
                authorizationUrl = "https://auth.example.com/authorize",
                scopes = {
                    @org.eclipse.microprofile.openapi.annotations.security.OAuthScope(
                        name = "read:users",
                        description = "Read users"
                    ),
                    @org.eclipse.microprofile.openapi.annotations.security.OAuthScope(
                        name = "write:users",
                        description = "Write users"
                    )
                }
            )
        )
    )
})

@Path("/admin")
@SecurityRequirement(name = "oauth2", scopes = {"write:users"})
public class AdminResource {
    // Protected endpoints
}
```

### Custom OASFilter

```java
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;

@Provider
public class CustomOASFilter implements OASFilter {

    @Override
    public Operation filterOperation(Operation operation) {
        // Remove internal operations
        if (operation.getOperationId() != null
                && operation.getOperationId().startsWith("_")) {
            return null; // This removes the operation
        }
        return operation;
    }

    @Override
    public Parameter filterParameter(Parameter parameter) {
        // Hide certain parameters
        if ("internalKey".equals(parameter.getName())) {
            return null;
        }
        return parameter;
    }

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        // Add custom extension
        openAPI.addExtension("x-custom-value", "custom");
    }
}
```

**Configure OASFilter:**

```properties
# META-INF/microprofile-config.properties
mp.openapi.filter=com.example.CustomOASFilter
```

### Static OpenAPI File

**META-INF/openapi.yaml:**

```yaml
openapi: 3.0.0
info:
  title: My API
  version: 1.0.0
paths:
  /users:
    get:
      summary: Get all users
      responses:
        '200':
          description: Successful response
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/User'
components:
  schemas:
    User:
      type: object
      properties:
        id:
          type: integer
        email:
          type: string
```

## HTTP Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /openapi` | OpenAPI document (default endpoint) |
| `Accept: application/json` | Returns JSON format |
| `Accept: application/yaml` | Returns YAML format |

### Response Format

**JSON Example:**
```json
{
  "openapi": "3.0.0",
  "info": {
    "title": "Deployed Resources",
    "version": "1.0.0"
  },
  "paths": {
    "/users": {
      "get": {
        "operationId": "getUsers",
        "responses": {
          "default": {
            "description": "Default Response",
            "content": {
              "*/*": {
                "schema": {
                  "type": "array"
                }
              }
            }
          }
        }
      }
    }
  },
  "servers": [
    {
      "url": "http://localhost:8080",
      "description": "Default Server."
    }
  ]
}
```

## Configuration

### domain.xml Configuration

```xml
<configs>
    <config name="server-config">
        <microprofile-openapi-configuration
            enabled="true"
            security-enabled="false"
            cors-headers="true">
        </microprofile-openapi-configuration>
    </config>
</configs>
```

### Admin Commands

```bash
# Enable OpenAPI
asadmin set-openapi-configuration --enabled=true

# Disable OpenAPI
asadmin set-openapi-configuration --enabled=false

# Enable security
asadmin set-openapi-configuration --security-enabled=true

# Enable CORS headers
asadmin set-openapi-configuration --cors-headers=true

# Get configuration
asadmin get-openapi-configuration
```

### MicroProfile Config Properties

```properties
# Disable annotation scanning
mp.openapi.scan.disable=true

# Disable Bean Validation annotation scanning
mp.openapi.scan.bean.validation.disable=true

# Disable schema scanning for specific packages
mp.openapi.scan.exclude.classes=com.example.internal.*

# OASFilter class
mp.openapi.filter=com.example.CustomOASFilter

# OASModelReader class
mp.openapi.model.reader=com.example.CustomModelReader

# Servers
mp.openapi.servers=http://api.example.com,https://api.example.com

# Path-specific servers
mp.openapi.servers.path./api/v1=http://v1.api.example.com

# Operation-specific servers
mp.openapi.servers.operation.getUserById=http://user.api.example.com
```

## Bean Validation Integration

The OpenAPI module automatically integrates with Jakarta Bean Validation annotations:

| Annotation | OpenAPI Effect |
|------------|----------------|
| `@NotNull` / `@NotEmpty` / `@NotBlank` | Adds `required: true` |
| `@Size(min=5, max=100)` | Sets `minLength: 5, maxLength: 100` |
| `@Min(18)` / `@Max(120)` | Sets `minimum: 18` / `maximum: 120` |
| `@DecimalMin("0.0")` / `@DecimalMax("100.0")` | Sets min/max with inclusive/exclusive |
| `@Pattern("\\S+")` | Sets pattern |
| `@Email` | Sets format to email |
| `@Negative` / `@Positive` | Sets exclusive max/min |

## Package Structure

```
microprofile/openapi/
└── src/main/java/fish/payara/microprofile/openapi/
    ├── activation/
    │   ├── OpenApiApplicationContainer.java    # Application lifecycle
    │   ├── OpenApiContainer.java               # Container
    │   ├── OpenApiDeployer.java                # Deployer
    │   └── OpenApiSniffer.java                 # Sniffer
    ├── api/
    │   ├── OpenAPIBuildException.java          # Build exception
    │   ├── processor/
    │   │   └── OASProcessor.java               # Processor interface
    │   └── visitor/
    │       ├── ApiContext.java                 # Context interface
    │       ├── ApiVisitor.java                 # Visitor interface
    │       └── ApiWalker.java                  # Walker interface
    ├── impl/
    │   ├── OpenApiService.java                 # Main service
    │   ├── OpenAPISupplier.java                # Document supplier
    │   ├── config/
    │   │   └── OpenApiConfiguration.java        # Configuration
    │   ├── model/                              # Model implementations
    │   │   ├── OpenAPIImpl.java
    │   │   ├── OperationImpl.java
    │   │   ├── PathItemImpl.java
    │   │   ├── SchemaImpl.java
    │   │   └── ...
    │   ├── processor/
    │   │   ├── ApplicationProcessor.java       # Annotation scanner
    │   │   ├── BaseProcessor.java              # Default values
    │   │   ├── ConfigPropertyProcessor.java    # MP Config support
    │   │   ├── FileProcessor.java              # Static file reader
    │   │   ├── FilterProcessor.java            # OASFilter support
    │   │   └── ModelReaderProcessor.java       # OASModelReader support
    │   ├── rest/
    │   │   └── app/
    │   │       ├── service/
    │   │       │   └── OpenApiResource.java    # JAX-RS endpoint
    │   │       └── provider/
    │   │           ├── ObjectMapperFactory.java
    │   │           ├── JsonWriter.java
    │   │           └── YamlWriter.java
    │   ├── visitor/
    │   │   ├── OpenApiContext.java             # Context implementation
    │   │   └── OpenApiWalker.java              # Walker implementation
    │   └── admin/
    │       ├── GetOpenApiConfigurationCommand.java
    │       ├── OpenApiServiceConfiguration.java
    │       └── SetOpenApiConfigurationCommand.java
    └── rest/
        └── app/
            └── OpenApiApplication.java         # JAX-RS application
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `microprofile-openapi-api` | MicroProfile OpenAPI API |
| `jakarta.ws.rs` | JAX-RS API |
| `jackson` | JSON/YAML processing |
| `glassfish-api` | GlassFish public APIs |
| `hk2-class-model` | Class model scanning |

## Notes

- **Static Files** - Reads from `META-INF/openapi.yaml` or `META-INF/openapi.json`
- **Merge Strategy** - Static files are merged with annotation-based documentation
- **Caching** - OpenAPI documents are cached and invalidated on application deployment changes
- **Multi-Application** - Documents from all applications are merged into one
- **Server URLs** - Automatically detected from deployed application context roots
- **Exception Mappers** - Automatically documented as error responses
- **Enum Types** - Automatically documented with all enum values
- **Inheritance** - Superclass fields are included via `allOf` references
- **Hidden Elements** - Use `hidden = true` on `@Schema` or `@Parameter` to exclude
- **X-Endpoints Extension** - Custom extension listing all endpoint URLs
