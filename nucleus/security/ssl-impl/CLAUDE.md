# CLAUDE.md - SSL Implementation

This file provides guidance for working with the `nucleus/security/ssl-impl` module - Payara's SSL/TLS implementation.

## Module Overview

The SSL implementation module provides SSL/TLS infrastructure for Payara, including:
- KeyStore and TrustStore management
- SSL context initialization
- Certificate handling
- Master password management
- Unified X509 KeyManager and TrustManager

## Build Commands

```bash
# Build the SSL implementation module
mvn -DskipTests clean package -f nucleus/security/ssl-impl/pom.xml

# Build with tests
mvn clean package -f nucleus/security/ssl-impl/pom.xml
```

## Architecture

### SSL Support Architecture

```
┌────────────────────────────────────────────────────────────┐
│                   Application Layer                        │
│  (HTTP listeners, IIOP, Admin console)                      │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                  SecuritySupport (API)                     │
│  - getKeyStores() / getTrustStores()                        │
│  - getKeyManagers() / getTrustManagers()                    │
│  - verifyMasterPassword()                                   │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                 SecuritySupportImpl                         │
│  - Loads keystores/truststores                              │
│  - Manages SSL contexts                                     │
│  - Handles master password                                  │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   SSL Managers                              │
├────────────────────────────────────────────────────────────┤
│  UnifiedX509KeyManager    │  UnifiedX509TrustManager       │
│  - Combines multiple       │  - Combines multiple          │
│    keystores                │    truststores                │
└────────────────────────────────────────────────────────────┘
                              │
┌────────────────────────────────────────────────────────────┐
│                   Java SSL Layer                            │
│  (JSSE, KeyStore, TrustStore, SSLContext)                   │
└────────────────────────────────────────────────────────────┘
```

## Key Components

### SecuritySupport (Contract)

Located in `com.sun.enterprise.server.pluggable`:

The main contract for SSL support. Provides access to keystores, truststores, and their managers.

```java
@Contract
public abstract class SecuritySupport {
    // Keystore operations
    abstract KeyStore[] getKeyStores();
    abstract KeyStore getKeyStore(String token);
    abstract KeyManager[] getKeyManagers(String algorithm);

    // Truststore operations
    abstract KeyStore[] getTrustStores();
    abstract KeyStore getTrustStore(String token);
    abstract TrustManager[] getTrustManagers(String algorithm);

    // Password operations
    abstract boolean verifyMasterPassword(char[] masterPass);
    abstract PrivateKey getPrivateKeyForAlias(String alias, int keystoreIndex);

    // Utility operations
    abstract void reset();
    abstract KeyStore loadNullStore(String type, int index);
    abstract String[] getTokenNames();
}
```

### SecuritySupportImpl

Located in `com.sun.enterprise.security.ssl.impl`:

The main implementation of SecuritySupport. Handles:
- Loading keystores and truststores from files
- Managing master password
- Creating KeyManager and TrustManager instances
- Certificate expiration checking

#### System Properties

| Property | Purpose | Default |
|----------|---------|---------|
| `javax.net.ssl.keyStore` | Keystore file path | `{domain}/config/keystore.jks` |
| `javax.net.ssl.keyStorePassword` | Keystore password | `changeit` |
| `javax.net.ssl.trustStore` | Truststore file path | `{domain}/config/cacerts.jks` |
| `javax.net.ssl.trustStorePassword` | Truststore password | `changeit` |
| `javax.net.ssl.keyStoreType` | Keystore type | `JKS` |
| `javax.net.ssl.trustStoreType` | Truststore type | `JKS` |
| `fish.payara.ssl.additionalKeyStores` | Additional keystores (semicolon-separated) | - |
| `fish.payara.ssl.additionalTrustStores` | Additional truststores (semicolon-separated) | - |

### UnifiedX509KeyManager

Located in `com.sun.enterprise.security.ssl.manager`:

Combines multiple X509KeyManagers into one. Supports:
- Multiple keystores with different aliases
- Client and server alias selection
- Certificate chain retrieval
- Private key access

```java
public class UnifiedX509KeyManager implements X509KeyManager {
    // Combines multiple key managers
    public UnifiedX509KeyManager(X509KeyManager[] mgrs, String[] tokenNames);

    // X509KeyManager methods
    String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket);
    String chooseServerAlias(String keyType, Principal[] issuers, Socket socket);
    X509Certificate[] getCertificateChain(String alias);
    PrivateKey getPrivateKey(String alias);
    // ...
}
```

### UnifiedX509TrustManager

Located in `com.sun.enterprise.security.ssl.manager`:

Combines multiple X509TrustManagers into one. Supports:
- Multiple truststores
- Certificate validation
- Client and server certificate checking

### MasterPasswordImpl

Located in `com.sun.enterprise.security.ssl.impl`:

Provides access to the master password used to encrypt keystores.

```java
@Service
@Singleton
public class MasterPasswordImpl implements MasterPassword {
    @Override
    public PasswordAdapter getMasterPasswordAdapter();
    @Override
    public char[] getMasterPassword();
}
```

## Package Structure

```
com.sun.enterprise.security.ssl/
├── impl/
│   ├── SecuritySupportImpl.java     # Main SSL support implementation
│   └── MasterPasswordImpl.java       # Master password provider
├── manager/
│   ├── UnifiedX509KeyManager.java    # Combined key manager
│   └── UnifiedX509TrustManager.java  # Combined trust manager
└── ...

com.sun.enterprise.server.pluggable/
└── SecuritySupport.java              # SSL support contract
```

## Usage Examples

### Getting SecuritySupport Instance

```java
import com.sun.enterprise.server.pluggable.SecuritySupport;

// Get default instance
SecuritySupport securitySupport = SecuritySupport.getDefaultInstance();
```

### Accessing Keystores

```java
// Get all keystores
KeyStore[] keystores = securitySupport.getKeyStores();

// Get specific keystore by token
KeyStore keystore = securitySupport.getKeyStore("token1");

// Get private key for alias
PrivateKey privateKey = securitySupport.getPrivateKeyForAlias("s1as", 0);
```

### Creating SSL Context

```java
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.SSLContext;

// Get key and trust managers
KeyManager[] keyManagers = securitySupport.getKeyManagers("PKIX");
TrustManager[] trustManagers = securitySupport.getTrustManagers("PKIX");

// Create SSL context
SSLContext sslContext = SSLContext.getInstance("TLS");
sslContext.init(keyManagers, trustManagers, null);

// Create SSL socket factory
SSLSocketFactory socketFactory = sslContext.getSocketFactory();
```

### Verifying Master Password

```java
// Verify master password is correct
boolean valid = securitySupport.verifyMasterPassword("changeit".toCharArray());
```

### Resetting SSL Configuration

```java
// Reload keystores after configuration changes
securitySupport.reset();
```

## Certificate Management

### Keystore Locations

| Keystore | Default Location | Purpose |
|----------|------------------|---------|
| Keystore | `{domain}/config/keystore.jks` | Server certificates and private keys |
| Truststore | `{domain}/config/cacerts.jks` | Trusted CA certificates |
| Keyfile | `{domain}/config/keyfile` | File realm user credentials |

### Using keytool

```bash
# List certificates in keystore
keytool -list -v -keystore keystore.jks

# Generate new key pair
keytool -genkeypair -alias s1as -keyalg RSA -keysize 2048 \
    -validity 365 -keystore keystore.jks

# Generate CSR
keytool -certreq -alias s1as -file s1as.csr -keystore keystore.jks

# Import signed certificate
keytool -import -alias s1as -file s1as.crt -keystore keystore.jks

# Import CA certificate into truststore
keytool -import -alias myca -file myca.crt -keystore cacerts.jks

# Delete certificate
keytool -delete -alias oldalias -keystore keystore.jks

# Change keystore password
keytool -storepasswd -keystore keystore.jks

# Change key password
keytool -keypasswd -alias s1as -keystore keystore.jks
```

## Configuration

### SSL Network Listener

```bash
# Create SSL-enabled network listener
asadmin create-network-listener \
    --listenerport 8181 \
    --protocol sec-admin-listener \
    --securityenabled=true

# Configure SSL settings
asadmin set server-config.network-config.protocols.protocol.sec-admin-listener.ssl \
    cert-nickname=s1as \
    ssl2-enabled=false \
    ssl3-enabled=false \
    tls-enabled=true \
    tls1.1-enabled=true \
    tls1.2-enabled=true \
    tls1.3-enabled=true
```

### JVM Options for SSL

```bash
# Set keystore
asadmin create-jvm-options \
    "-Djavax.net.ssl.keyStore=\${com.sun.aas.instanceRoot}/config/keystore.jks"

# Set truststore
asadmin create-jvm-options \
    "-Djavax.net.ssl.trustStore=\${com.sun.aas.instanceRoot}/config/cacerts.jks"

# Add additional keystores
asadmin create-jvm-options \
    "-Dfish.payara.ssl.additionalKeyStores=/path/to/additional.jks"
```

## Dependencies

Key dependencies:
- `hk2-core` - Dependency injection
- `internal-api` - Internal APIs
- `common-util` - Common utilities
- `glassfish-api` - Public APIs

## Integration Points

The SSL implementation integrates with:
- **Grizzly** - HTTP/IIOP SSL layer
- **Admin REST** - Secure admin communication
- **Security Core** - Master password and security services
- **Config API** - SSL configuration

## Troubleshooting

### Enable SSL Debug

```bash
asadmin create-jvm-options "-Djavax.net.debug=ssl,handshake"
```

### Certificate Issues

```bash
# Check certificate expiration
keytool -list -v -keystore keystore.jks | grep "Valid from"

# Verify certificate chain
keytool -list -v -keystore keystore.jks -alias s1as
```

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `SSLHandshakeException: no cipher suites in common` | Mismatched cipher suites | Check TLS version and cipher configuration |
| `SSLHandshakeException: sun.security.validator.ValidatorException` | Untrusted certificate | Import CA certificate into truststore |
| `IOException: Keystore was tampered with` | Wrong password | Verify master password |
| `CertificateExpiredException` | Certificate expired | Renew certificate |
