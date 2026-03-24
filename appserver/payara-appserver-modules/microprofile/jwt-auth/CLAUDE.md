# CLAUDE.md - MicroProfile JWT Auth

This file provides guidance for working with the `microprofile/jwt-auth` module - MicroProfile JWT Authentication implementation.

## Module Overview

The microprofile/jwt-auth module provides an implementation of the MicroProfile JWT Auth API for Payara Server. It enables JWT-based authentication for JAX-RS applications using Jakarta Security (Soteria).

**Key Features:**
- **JWT Token Validation** - Validate and parse JWT tokens from HTTP requests
- **@LoginConfig** - Configure JWT authentication mechanism
- **Claim Injection** - Inject JWT claims using @Claim qualifier
- **Role Mapping** - Map JWT claims to Jakarta Security roles
- **Multiple Token Sources** - Authorization header or Cookie
- **Public Key Configuration** - Configure RSA/ECDSA public keys for verification

## Build Commands

```bash
# Build microprofile/jwt-auth module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/jwt-auth/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/jwt-auth/pom.xml
```

## Architecture

### JWT Authentication Flow

```
HTTP Request with JWT
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│       JWTAuthenticationMechanism.validateRequest()               │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Extract JWT token from request                         │ │
│  │     - Check Authorization header (default)                │ │
│  │     - Check Cookie header (if configured)                │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Create SignedJWTCredential                              │ │
│  │     credential = new SignedJWTCredential(token)          │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Validate with IdentityStore                            │ │
│  │     result = identityStoreHandler.validate(credential)  │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  4. If VALID, set principal and notify container           │ │
│  │     clientSubject.getPrincipals().add(principal)         │ │
│  │     return notifyContainerAboutLogin(result)              │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  5. If INVALID, return 401 Unauthorized                    │ │
│  │     return responseUnauthorized()                          │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│           SignedJWTIdentityStore                               │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Parse JWT token                                      │ │
│  │     JwtTokenParser.parse(token)                          │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Verify signature with public key                      │ │
│  │     Read from mp.jwt.verify.publickey or location        │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Extract claims and create principal                    │ │
│  │     principal = new SignedJWTPrincipal(claims, roles)    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### CDI Extension Flow

```
Application Deployment
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│           JwtAuthCdiExtension (CDI Extension)                   │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. register(@Observes BeforeBeanDiscovery)              │ │
│  │     - Register InjectionPointGenerator                   │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. findLoginConfigAnnotation(@Observes ProcessBean)     │ │
│  │     - Find @LoginConfig(authMethod = "MP-JWT")           │ │
│  │     - Set flag to install authentication mechanism         │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. findRoles(@Observes ProcessManagedBean)              │ │
│  │     - Collect all @RolesAllowed values                   │ │
│  │     - Store for programmatic role declaration             │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  4. checkInjectIntoRightScope(@Observes ProcessInjectionTarget)│ │
│  │     - Validate @Claim injection points                     │ │
│  │     - Reject injection into @SessionScoped               │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  5. installMechanismIfNeeded(@Observes AfterBeanDiscovery)│ │
│  │     - Validate configuration (public key)                 │ │
│  │     - Install JWTAuthenticationMechanism                 │ │
│  │     - Install SignedJWTIdentityStore                    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

## Core Components

### JWTAuthenticationMechanism

```java
public class JWTAuthenticationMechanism implements HttpAuthenticationMechanism {

    public static final String CONFIG_TOKEN_HEADER_AUTHORIZATION = "Authorization";
    public static final String CONFIG_TOKEN_HEADER_COOKIE = "Cookie";

    private final String configJwtTokenHeader;
    private final String configJwtTokenCookie;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request,
                                           HttpServletResponse response,
                                           HttpMessageContext httpMessageContext)
            throws AuthenticationException {

        IdentityStoreHandler identityStoreHandler =
            CDI.current().select(IdentityStoreHandler.class).get();

        SignedJWTCredential credential = getCredential(request);

        if (credential != null) {
            CredentialValidationResult result =
                identityStoreHandler.validate(credential);

            if (result.getStatus() == VALID) {
                httpMessageContext.getClientSubject()
                    .getPrincipals()
                    .add(result.getCallerPrincipal());
                return httpMessageContext.notifyContainerAboutLogin(result);
            }

            return httpMessageContext.responseUnauthorized();
        }

        return httpMessageContext.doNothing();
    }

    private SignedJWTCredential getCredential(HttpServletRequest request) {
        if (CONFIG_TOKEN_HEADER_AUTHORIZATION.equals(configJwtTokenHeader)) {
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                return new SignedJWTCredential(
                    authorizationHeader.substring("Bearer ".length()));
            }
        } else {
            // Cookie-based token extraction
            String bearerMark = ";" + configJwtTokenCookie + "=";
            String cookieHeader = request.getHeader("Cookie");
            if (cookieHeader != null && cookieHeader.contains(bearerMark)) {
                String token = cookieHeader.substring(
                    cookieHeader.indexOf(bearerMark) + bearerMark.length());
                return new SignedJWTCredential(token);
            }
        }
        return null;
    }
}
```

**Purpose:** Jakarta Security HttpAuthenticationMechanism that extracts JWT tokens and validates them.

### SignedJWTIdentityStore

```java
public class SignedJWTIdentityStore implements IdentityStore {

    @Override
    public CredentialValidationResult validate(SignedJWTCredential credential) {
        try {
            // Parse JWT token
            JWT token = JwtTokenParser.parse(credential.getSignedJWT());

            // Verify signature
            if (!verifySignature(token)) {
                return CredentialValidationResult.INVALID_RESULT;
            }

            // Extract claims
            Set<String> roles = extractRoles(token);
            JsonWebTokenImpl principal = new JsonWebTokenImpl(
                token, roles);

            return CredentialValidationResult.builder()
                .principal(principal)
                .status(VALID)
                .build();

        } catch (JWTProcessingException e) {
            return CredentialValidationResult.INVALID_RESULT;
        }
    }

    private boolean verifySignature(JWT token) {
        // Get public key from configuration
        String publicKey = config.getValue(VERIFIER_PUBLIC_KEY, String.class);
        String publicKeyLocation = config.getValue(
            VERIFIER_PUBLIC_KEY_LOCATION, String.class);

        // Load public key from store
        PublicKey key = JwtPublicKeyStore.loadPublicKey(
            publicKey, publicKeyLocation);

        // Verify JWT signature
        return token.verify(key);
    }
}
```

**Purpose:** IdentityStore that validates JWT tokens and creates caller principals with roles.

### JwtAuthCdiExtension

```java
public class JwtAuthCdiExtension implements Extension {

    private boolean addJWTAuthenticationMechanism;
    private final Set<String> roles = new HashSet<>();

    public void findLoginConfigAnnotation(@Observes ProcessBean<?> event,
                                         BeanManager beanManager) {
        LoginConfig loginConfig = event.getAnnotated()
            .getAnnotation(LoginConfig.class);

        if (loginConfig != null &&
            loginConfig.authMethod().equals("MP-JWT")) {
            addJWTAuthenticationMechanism = true;
        }
    }

    public void findRoles(@Observes ProcessManagedBean<?> event,
                          BeanManager beanManager) {
        // Find all @RolesAllowed annotations
        List<Annotated> annotatedElements = new ArrayList<>(
            event.getAnnotatedBeanClass().getMethods());
        annotatedElements.add(event.getAnnotatedBeanClass());

        for (Annotated annotated : annotatedElements) {
            RolesAllowed rolesAllowed = annotated
                .getAnnotation(RolesAllowed.class);
            if (rolesAllowed != null) {
                roles.addAll(Arrays.asList(rolesAllowed.value()));
            }
        }
    }

    public void checkInjectIntoRightScope(
            @Observes ProcessInjectionTarget<?> event,
            BeanManager beanManager) {

        for (InjectionPoint injectionPoint :
                event.getInjectionTarget().getInjectionPoints()) {

            Claim claim = hasClaim(injectionPoint);
            if (claim != null) {
                // MP-JWT 1.0 7.1.3: @Claim cannot be injected into @SessionScoped
                Class<?> scope = injectionPoint.getBean().getScope();

                if (scope != null && scope.equals(SessionScoped.class)) {
                    throw new DeploymentException(
                        "Can't inject using @Claim in @SessionScoped");
                }

                // Claim value must match standard or be empty
                if (!claim.value().equals("") &&
                    claim.standard() != UNKNOWN &&
                    !claim.value().equals(claim.standard().name())) {
                    throw new DeploymentException(
                        "Claim value must equal claim standard or be empty");
                }
            }
        }
    }

    public void installMechanismIfNeeded(
            @Observes AfterBeanDiscovery event,
            BeanManager beanManager) {

        if (addJWTAuthenticationMechanism) {
            validateConfigValue();
            CdiInitEventHandler.installAuthenticationMechanism(event);
        }
    }

    private void validateConfigValue() {
        Config config = ConfigProvider.getConfig();

        // Both properties cannot be defined simultaneously
        if (config.getOptionalValue(VERIFIER_PUBLIC_KEY, String.class).isPresent() &&
            config.getOptionalValue(VERIFIER_PUBLIC_KEY_LOCATION, String.class).isPresent()) {
            throw new DeploymentException(
                "Both mp.jwt.verify.publickey and mp.jwt.verify.publickey.location must not be defined"
            );
        }
    }
}
```

**Purpose:** CDI extension that installs JWT authentication mechanism and validates configuration.

## Usage Examples

### Basic JWT Authentication

```java
import org.eclipse.microprofile.auth.LoginConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@LoginConfig(authMethod = "MP-JWT")
@ApplicationScoped
@Path("/api")
public class ProtectedResource {

    @GET
    @RolesAllowed("USER")
    public Response getUserData() {
        // Only accessible with valid JWT containing "USER" role
        return Response.ok("Protected data").build();
    }
}
```

### Claim Injection

```java
import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.Claims;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class UserInfoService {

    @Inject
    @Claim("upn")
    private String username;

    @Inject
    @Claim(standard = Claims.email)
    private String email;

    @Inject
    @Claim("custom_role")
    private String customRole;

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getCustomRole() {
        return customRole;
    }
}
```

### Inject JsonWebToken

```java
import org.eclipse.microprofile.jwt.JsonWebToken;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class TokenService {

    @Inject
    private JsonWebToken jwt;

    public String getTokenSubject() {
        return jwt.getSubject();
    }

    public String getTokenIssuer() {
        return jwt.getIssuer();
    }

    public String getName() {
        return jwt.getName();
    }

    public Set<String> getGroups() {
        return jwt.getGroups();
    }

    public Long getExpirationTime() {
        return jwt.getExpirationTime();
    }
}
```

## Configuration

### MicroProfile Config Properties

```properties
# JWT verification
mp.jwt.verify.publickey=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
mp.jwt.verify.publickey.location=/META-INF/jwt/publicKey.pem
mp.jwt.decrypt.key.location=/META-INF/jwt/privateKey.pem

# Token header configuration
mp.jwt.token.header=Authorization
mp.jwt.token.cookie=Bearer

# Custom claim names
mp.jwt.token.issuer=example.com
mp.jwt.token.audience=my-app
```

### domain.xml Configuration

```xml
<configs>
    <config name="server-config">
        <microprofile-jwt-auth-configuration
            enabled="true">
        </microprofile-jwt-auth-configuration>
    </config>
</configs>
```

### JWT Token Format

```
Header:
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key1"
}

Payload:
{
  "sub": "user123",
  "upn": "johndoe",
  "iss": "https://example.com",
  "aud": "my-app",
  "exp": 1516239022,
  "iat": 1516232422,
  "groups": ["USER", "ADMIN"],
  "custom_role": "manager"
}

Signature:
<signature>
```

## Token Sources

### Authorization Header (Default)

```http
GET /api/resource HTTP/1.1
Host: example.com
Authorization: Bearer <jwt_token>
```

### Cookie Header

```properties
# Configure cookie-based tokens
mp.jwt.token.header=Cookie
mp.jwt.token.cookie=jwt_token
```

```http
GET /api/resource HTTP/1.1
Host: example.com
Cookie: $Version=1; jwt_token=<jwt_token>
```

## Role Mapping

### JWT Groups to Jakarta Security Roles

```java
// JWT payload contains "groups" claim:
{
  "groups": ["USER", "ADMIN", "MODERATOR"]
}

// @RolesAllowed will check these groups
@RolesAllowed("ADMIN")
public Response adminMethod() { ... }
```

### Programmatic Role Declaration

```java
public class RolesDeclarationInitializer implements ServletContainerInitializer {

    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
        // Programmatically declare roles
        ctx.declareRoles("USER", "ADMIN", "MODERATOR");
    }
}
```

## Public Key Configuration

### Inline Public Key (MP-JWT 1.1+)

```properties
# RSA Public Key (PEM format)
mp.jwt.verify.publickey=-----BEGIN PUBLIC KEY-----\\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\\n-----END PUBLIC KEY-----

# ECDSA Public Key
mp.jwt.verify.publickey=MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...
```

### Public Key from Location

```properties
# File path
mp.jwt.verify.publickey.location=/META-INF/jwt/publicKey.pem

# Classpath resource
mp.jwt.verify.publickey.location=classpath:/keys/publicKey.pem

# URL
mp.jwt.verify.publickey.location=https://example.com/keys/publicKey.pem
```

### Key Store Configuration

```properties
# Key store properties
mp.jwt.verify.key.location=/META-INF/keystore.jks
mp.jwt.verify.key.password=keystorepassword
mp.jwt.verify.key.id=jwt-key
```

## Package Structure

```
microprofile/jwt-auth/
└── src/main/java/fish/payara/microprofile/jwtauth/
    ├── activation/
    │   ├── JwtAuthApplicationContainer.java    # Application lifecycle
    │   ├── JwtAuthContainer.java               # Container
    │   ├── JwtAuthDeployer.java                # Deployer
    │   └── JwtAuthSniffer.java                 # Sniffer
    ├── cdi/
    │   ├── JwtAuthCdiExtension.java            # CDI extension
    │   └── InjectionPointGenerator.java        # Injection point generator
    ├── eesecurity/
    │   ├── JWTAuthenticationMechanism.java      # Auth mechanism
    │   ├── SignedJWTIdentityStore.java         # Identity store
    │   ├── SignedJWTCredential.java            # Credential
    │   ├── JwtPublicKeyStore.java               # Public key loader
    │   ├── JwtPrivateKeyStore.java              # Private key loader
    │   └── KeyLoadingCache.java                 # Key cache
    ├── jaxrs/
    │   ├── RolesAllowedDynamicFeature.java       # JAX-RS integration
    │   ├── RolesAllowedRequestFilter.java        # Request filter
    │   └── DenyAllRequestFilter.java             # Security filter
    ├── jwt/
    │   ├── JsonWebTokenImpl.java                # JWT implementation
    │   ├── ClaimValueImpl.java                  # Claim value
    │   ├── ClaimAnnotationLiteral.java          # Qualifier
    │   └── JwtTokenParser.java                   # Token parser
    └── RolesDeclarationInitializer.java          # Role declaration
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `microprofile-jwt-auth-api` | MicroProfile JWT Auth API |
| `jakarta.security-api` | Jakarta Security API |
| `jakarta.enterprise` | CDI API |
| `jakarta.ws.rs` | JAX-RS API |
| `nucleus/microprofile/config` | Configuration support |

## Notes

- **MP-JWT 1.0/1.1/1.2** - Supports multiple MicroProfile JWT Auth spec versions
- **Soteria Integration** - Uses GlassFish Soteria for Jakarta Security
- **Signature Algorithms** - Supports RSA (RS256/RS384/RS512) and ECDSA (ES256/ES384/ES512)
- **Token Extraction** - Supports Authorization header (default) or Cookie
- **Claim Injection** - Only @RequestScoped and @ApplicationScoped supported (not @SessionScoped)
- **Role Mapping** - JWT "groups" claim maps to Jakarta Security roles
- **Key Caching** - Public keys are cached for performance
- **Configuration Validation** - Validates that only one public key source is configured
