# CLAUDE.md - MicroProfile REST Client

This file provides guidance for working with the `microprofile/rest-client` module - MicroProfile REST Client integration with Payara.

## Module Overview

The rest-client module integrates the MicroProfile REST Client specification with Payara's web container. It prevents REST client interfaces from being incorrectly registered as servlets.

**Key Features:**
- **Servlet Container Initializer Blacklist** - Prevents REST client interfaces from being treated as servlets
- **Clean Integration** - Ensures clean integration between MP REST Client and Payara web container

## Build Commands

```bash
# Build rest-client module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/rest-client/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/rest-client/pom.xml
```

## Core Components

### RestClientServletContainerInitializerBlacklist

```java
public class RestClientServletContainerInitializerBlacklist
        implements ServletContainerInitializerBlacklist {

    @Override
    public void removeServletContainerInitializers(
            Map<Class<? extends ServletContainerInitializer>,
                Set<Class<?>>> initializerMap) {

        for (Map.Entry<Class<? extends ServletContainerInitializer>,
                      Set<Class<?>>> e : initializerMap.entrySet()) {

            Class<? extends ServletContainerInitializer> initializer = e.getKey();

            if (e.getValue() != null) {
                // Filter out @RegisterRestClient annotated interfaces
                initializerMap.put(initializer,
                    e.getValue().stream()
                        .filter(clazz -> !clazz.isAnnotationPresent(RegisterRestClient.class))
                        .collect(Collectors.toSet()));
            }
        }
    }
}
```

**Purpose:** Removes REST client interfaces from the servlet container initializer list to prevent duplicate mappings.

### Problem Solved

Without this blacklist:

```
@RegisterRestClient
@Path("/api/users")
public interface UserServiceClient {
    @GET
    User getUser(@PathParam("id") String id);
}

// Problem: Jersey tries to register this interface as a servlet
// Result: Duplicate mapping error (interface vs implementation)
```

With this blacklist:

```
// @RegisterRestClient interfaces are filtered out
// Only actual REST endpoint implementations are registered
// No duplicate mapping errors
```

## Package Structure

```
microprofile/rest-client/
└── src/main/java/fish/payara/microprofile/restclient/
    └── RestClientServletContainerInitializerBlacklist.java
```

## Usage

### REST Client Interface

```java
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@RegisterRestClient(baseUri = "https://api.example.com")
@Path("/users")
public interface UserServiceClient {

    @GET
    @Path("/{id}")
    User getUser(@PathParam("id") String id);
}
```

### Inject and Use

```java
import org.eclipse.microprofile.rest.client.inject.RestClient;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserService {

    @Inject
    @RestClient
    private UserServiceClient userClient;

    public User fetchUser(String id) {
        return userClient.getUser(id);
    }
}
```

### Configuration

```properties
# Override base URI via config
org.example.UserServiceClient/mp-rest/url=https://api.prod.example.com

# Set connection timeout
org.example.UserServiceClient/mp-rest/connectTimeout=5000

# Set read timeout
org.example.UserServiceClient/mp-rest/readTimeout=10000
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `microprofile-rest-client-api` | MicroProfile REST Client API |
| `web-loader` | Servlet container initializer blacklist SPI |
| `jakarta.ws.rs` | JAX-RS API |

## Integration Points

The module integrates with:

1. **Servlet Container Initializer** - Filters out REST client interfaces
2. **Jersey** - Standard MP REST Client implementation
3. **CDI** - For @RestClient injection

## Notes

- **Minimal Module** - Contains only one class for blacklist functionality
- **Standard Implementation** - Uses MicroProfile RestClient from SmallRye/RESTEasy
- **Servlet Blacklist** - Prevents REST client interfaces from being registered as servlets
- **No Custom Implementation** - Relies on standard MP RestClient implementation
