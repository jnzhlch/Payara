# CLAUDE.md - MicroProfile REST Client SSL

This file provides guidance for working with the `microprofile/rest-client-ssl` module - SSL/TLS certificate configuration for MicroProfile REST Client.

## Module Overview

The rest-client-ssl module provides SSL/TLS certificate support for MicroProfile REST Client by allowing certificate aliases to be configured via MP Config or RestClientBuilder properties.

**Key Features:**
- **Certificate Alias Support** - Use Payara password aliases for client certificates
- **MP Config Integration** - Configure certificates via MicroProfile Config
- **Custom KeyManager** - Per-client certificate selection

## Build Commands

```bash
# Build rest-client-ssl module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/rest-client-ssl/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/rest-client-ssl/pom.xml
```

## Core Components

### RestClientSslContextAliasListener

```java
public class RestClientSslContextAliasListener implements RestClientListener {

    @Override
    public void onNewClient(Class<?> serviceInterface, RestClientBuilder builder) {
        // Check for alias in builder configuration
        Object aliasProperty = builder.getConfiguration()
            .getProperty(REST_CLIENT_CERTIFICATE_ALIAS);

        if (aliasProperty instanceof String) {
            String alias = (String) aliasProperty;
            SSLContext customSSLContext = buildSSlContext(alias);
            if (customSSLContext != null) {
                builder.sslContext(customSSLContext);
            }
        } else {
            // Check for alias in MP Config
            Config config = ConfigProvider.getConfig();
            String alias = config.getValue(MP_CONFIG_CLIENT_CERTIFICATE_ALIAS, String.class);
            if (alias != null) {
                SSLContext customSSLContext = buildSSlContext(alias);
                if (customSSLContext != null) {
                    builder.sslContext(customSSLContext);
                }
            }
        }
    }

    protected SSLContext buildSSlContext(String alias) {
        KeyManager[] managers = getKeyManagers();
        KeyStore[] keyStores = getKeyStores();

        for (KeyStore ks : keyStores) {
            if (ks.containsAlias(alias)) {
                X509KeyManager customKeyManager = new SingleCertificateKeyManager(
                    alias,
                    defaultKeyManager
                );
                SSLContext customSSLContext = SSLContext.getInstance("TLS");
                customSSLContext.init(new KeyManager[]{customKeyManager}, null, null);
                return customSSLContext;
            }
        }
        return null;
    }
}
```

### SingleCertificateKeyManager

Custom KeyManager that selects a specific certificate alias:

```java
private static class SingleCertificateKeyManager implements X509KeyManager {

    private final String alias;
    private final X509KeyManager keyManager;

    @Override
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
        return alias;  // Always use configured alias
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        return keyManager.getCertificateChain(alias);
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        return keyManager.getPrivateKey(alias);
    }
}
```

## Package Structure

```
microprofile/rest-client-ssl/
└── src/main/java/fish/payara/microprofile/jaxrs/client/ssl/
    └── RestClientSslContextAliasListener.java
```

## Configuration Methods

### Method 1: RestClientBuilder Property

```java
@RegisterRestClient
public interface ApiService {
    // ...
}

// Set certificate alias via builder
RestClientBuilder builder = RestClientBuilder.newBuilder()
    .baseUri(URI.create("https://api.example.com"))
    .property(REST_CLIENT_CERTIFICATE_ALIAS, "my-client-cert");

ApiService client = builder.build(ApiService.class);
```

### Method 2: MP Config Property

```properties
# Global certificate for all REST clients
mp.rest.client.certificate.alias=my-client-cert

# Or per-client
org.example.ApiService/mp-rest/client.certificate.alias=my-client-cert
```

### Method 3: Create Password Alias

```bash
# Create password alias for certificate
asadmin create-password-alias my-client-cert
```

## Usage Examples

### REST Client with Mutual TLS

```java
import org.eclipse.microprofile.rest.client.inject.RestClient;
import jakarta.inject.Inject;

@ApplicationScoped
public class SecureApiService {

    @Inject
    @RestClient
    private ApiService apiClient;

    public Response callSecureEndpoint() {
        // Uses configured certificate for mutual TLS
        return apiClient.getSecureData();
    }
}
```

### Configuration

```properties
# Application configuration
org.example.ApiService/mp-rest/url=https://secure-api.example.com
org.example.ApiService/mp-rest/client.certificate.alias=api-client-cert

# Or globally
mp.rest.client.certificate.alias=api-client-cert
```

## Configuration Properties

| Property | Type | Description |
|----------|------|-------------|
| `REST_CLIENT_CERTIFICATE_ALIAS` | String | Builder property for certificate alias |
| `MP_CONFIG_CLIENT_CERTIFICATE_ALIAS` | String | MP Config key for certificate alias |
| `mp.rest/client.certificate.alias` | String | Global certificate alias |

## SSL Context Creation Flow

```
REST Client Creation
        │
        ▼
[RestClientListener.onNewClient()]
        │
        ├─→ Check builder property
        │   └─→ REST_CLIENT_CERTIFICATE_ALIAS
        │
        ├─→ Check MP Config
        │   └─→ MP_CONFIG_CLIENT_CERTIFICATE_ALIAS
        │
        ▼
[buildSSLContext(alias)]
        │
        ├─→ Get KeyManagers from SSLUtils
        ├─→ Get KeyStores from SSLUtils
        ├─→ Find alias in KeyStores
        │
        ▼
[SingleCertificateKeyManager]
        │
        ├─→ Wraps default X509KeyManager
        └─→ Always selects configured alias
        │
        ▼
[SSLContext] → Set on RestClientBuilder
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `microprofile-rest-client-api` | MicroProfile REST Client API |
| `microprofile-config-api` | Configuration for certificate alias |
| `security` | SSLUtils for KeyManager/KeyStore access |

## Notes

- **Mutual TLS** - Enables mTLS (mutual TLS) for REST client connections
- **Alias Lookup** - Certificate must exist in Payara's keystore
- **Priority** - Builder property takes precedence over MP Config
- **Logging** - Fine-grained logging for troubleshooting SSL issues
- **Single Certificate** - Each client uses one configured certificate alias
