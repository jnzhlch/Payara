# CLAUDE.md - MicroProfile Config Extensions

This file provides guidance for working with the `microprofile/config-extensions` module - Extended configuration sources for MicroProfile Config beyond standard sources.

## Module Overview

The config-extensions module provides additional configuration sources for MicroProfile Config, enabling integration with cloud secret managers, LDAP directories, TOML files, and databases. These sources extend Payara's configuration capabilities beyond the standard MP Config sources.

**Key Features:**
- **Cloud Secret Managers** - AWS Secrets Manager, Azure Key Vault, GCP Secret Manager, HashiCorp Vault
- **TOML Support** - File-based configuration with structured data
- **LDAP Integration** - Directory-based configuration
- **DynamoDB** - AWS DynamoDB as configuration store
- **OAuth2 Authentication** - Reusable OAuth2 client for cloud services

## Build Commands

```bash
# Build config-extensions module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/microprofile/config-extensions/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/microprofile/config-extensions/pom.xml
```

## Architecture

All config sources extend the base class `ConfiguredExtensionConfigSource<T>`:

```java
@Service(name = "<source-name>-config-source")
public class XXXConfigSource extends ConfiguredExtensionConfigSource<XXXConfigSourceConfiguration> {

    @Inject
    MicroprofileConfigConfiguration mpconfig;

    @Override
    public void bootstrap() {
        // Initialize connections, authentication
    }

    @Override
    public Map<String, String> getProperties() {
        // Fetch all properties from the source
    }

    @Override
    public String getValue(String propertyName) {
        // Fetch single property
    }

    @Override
    public boolean setValue(String key, String value) {
        // Optional: Write property back to source
    }

    @Override
    public String getName() {
        return "<source-name>";
    }

    @Override
    public int getOrdinal() {
        return Integer.parseInt(mpconfig.getCloudOrdinality());
    }
}
```

## Config Sources

### 1. AWS Secrets Manager

**Purpose:** Fetch secrets from AWS Secrets Manager

**Class:** `AWSSecretsConfigSource`

**Configuration:**
```java
public interface AWSSecretsConfigSourceConfiguration {
    String getSecretName();      // AWS secret name
    String getRegionName();      // AWS region (e.g., us-east-1)
    boolean isEnabled();         // Enable/disable source
}
```

**Authentication:**
Uses password aliases for AWS credentials:
```bash
asadmin create-password-alias AWS_ACCESS_KEY_ID
asadmin create-password-alias AWS_SECRET_ACCESS_KEY
```

**Secret Format:**
AWS Secrets Manager stores a JSON map:
```json
{
  "database.url": "jdbc:postgresql://localhost/db",
  "database.user": "admin",
  "database.password": "secret123"
}
```

**Usage:**
```bash
# Enable AWS Secrets Config Source
asadmin set-aws-secrets-config-source-configuration
    --enabled=true
    --secret-name=myapp/production
    --region-name=us-east-1
```

### 2. Azure Key Vault

**Purpose:** Fetch secrets from Azure Key Vault

**Class:** `AzureSecretsConfigSource`

**Configuration:**
```java
public interface AzureSecretsConfigSourceConfiguration {
    String getKeyVaultName();        // Azure Key Vault name
    String getTenantId();            // Azure AD tenant ID
    String getClientId();            // Azure AD client ID
    String getThumbprint();          // Certificate thumbprint
    String getPrivateKeyFilePath();  // Path to private key file
    boolean isEnabled();
}
```

**Authentication:**
Uses OAuth2 JWT signed with private key:
```bash
# Place private key file in config directory
# Configure Azure Key Vault integration
asadmin set-azure-secrets-config-source-configuration
    --enabled=true
    --key-vault-name=myvault
    --tenant-id=xxx
    --client-id=xxx
    --thumbprint=xxx
    --private-key-path=azure-key.pem
```

### 3. GCP Secret Manager

**Purpose:** Fetch secrets from Google Cloud Secret Manager

**Class:** `GCPSecretsConfigSource`

**Configuration:**
```java
public interface GCPSecretsConfigSourceConfiguration {
    String getProjectName();         // GCP project ID
    String getTokenFilePath();       // Path to service account JSON key
    boolean isEnabled();
}
```

**Authentication:**
Uses GCP service account JSON key file with OAuth2 JWT:
```bash
# Place service account JSON key in config directory
asadmin set-gcp-secrets-config-source-configuration
    --enabled=true
    --project-name=my-gcp-project
    --token-file-path=gcp-key.json
```

### 4. HashiCorp Vault

**Purpose:** Fetch secrets from HashiCorp Vault

**Class:** `HashiCorpSecretsConfigSource`

**Configuration:**
```java
public interface HashiCorpSecretsConfigSourceConfiguration {
    String getVaultAddress();        // Vault server URL
    String getSecretsEnginePath();   // Secrets engine (e.g., kv)
    String getSecretsPath();         // Path to secrets
    String getApiVersion();          // API version (1 or 2)
    boolean isEnabled();
}
```

**Authentication:**
Uses Vault token from password alias:
```bash
asadmin create-password-alias HASHICORP_VAULT_TOKEN
asadmin set-hashicorp-secrets-config-source-configuration
    --enabled=true
    --vault-address=https://vault.example.com
    --secrets-engine-path=kv
    --secrets-path=myapp
    --api-version=2
```

### 5. LDAP Config Source

**Purpose:** Fetch configuration from LDAP directory

**Class:** `LDAPConfigSource`

**Configuration:**
```java
public interface LDAPConfigSourceConfiguration {
    String getUrl();                 // LDAP server URL
    String getBindDn();              // Bind DN for authentication
    String getPassword();            // Bind password (alias)
    String getSearchBase();          // Search base DN
    String getSearchFilter();        // LDAP search filter
    boolean isEnabled();
}
```

**Usage:**
```bash
asadmin set-ldap-config-source-configuration
    --enabled=true
    --url=ldap://localhost:389
    --bind-dn=cn=admin,dc=example,dc=com
    --password-alias=LDAP_PASSWORD
    --search-base=ou=config,dc=example,dc=com
    --search-filter=(objectClass=configProperty)
```

### 6. TOML Config Source

**Purpose:** Load configuration from TOML files with support for nested structures

**Class:** `TOMLConfigSource`

**Configuration:**
```java
public interface TOMLConfigSourceConfiguration {
    String getPath();                // Path to TOML file
    int getDepth();                  // Maximum nesting depth
    boolean isEnabled();
}
```

**TOML File Format:**
```toml
# application.toml
[server]
host = "localhost"
port = 8080

[database]
url = "jdbc:postgresql://localhost/db"
[database.credentials]
username = "admin"
password = "secret"
```

**Flattened Properties:**
```
server.host = "localhost"
server.port = "8080"
database.url = "jdbc:postgresql://localhost/db"
database.credentials.username = "admin"
database.credentials.password = "secret"
```

**Usage:**
```bash
asadmin set-toml-configuration
    --enabled=true
    --path=application.toml
    --depth=10
```

### 7. DynamoDB Config Source

**Purpose:** Use AWS DynamoDB table as configuration store

**Class:** `DynamoDBConfigSource`

**Configuration:**
```java
public interface DynamoDBConfigSourceConfiguration {
    String getTableName();           // DynamoDB table name
    String getKeyColumnName();       // Primary key column name
    String getValueColumnName();     // Value column name
    String getRegionName();          // AWS region
    String getLimit();               // Scan limit
    boolean isEnabled();
}
```

**Supported Data Types:**
- String (S)
- Number (N)
- Binary (B)
- Boolean (BOOL)
- Null (NULL)

**Usage:**
```bash
asadmin set-dynamodb-config-source-configuration
    --enabled=true
    --table-name=config
    --key-column-name=configKey
    --value-column-name=configValue
    --region-name=us-east-1
    --limit=100
```

## Shared Components

### OAuth2Client

Shared OAuth2 authentication client used by Azure and GCP:

```java
public class OAuth2Client {

    private final String authUrl;
    private final Map<String, String> authData;
    private volatile Instant tokenExpiry;

    public Response authenticate() {
        // Perform OAuth2 token request
        // Cache token until expiry
    }

    public void expire(Duration duration) {
        // Set token expiry time
    }
}
```

**OAuth2 Flow:**
1. Build JWT with claims
2. Sign with private key
3. Exchange signed JWT for access token
4. Cache token until expiry
5. Use Bearer token for API calls

### AwsRequestBuilder

AWS request signing for AWS services:

```java
public class AwsRequestBuilder {

    private final String accessKeyId;
    private final String secretAccessKey;
    private String region;
    private String serviceName;
    private HttpMethod method;
    private Object data;

    public Response invoke() {
        // AWS Signature Version 4 signing
        // Execute signed request
    }
}
```

## Package Structure

```
microprofile/config-extensions/
└── src/main/java/fish/payara/microprofile/config/extensions/
    ├── aws/
    │   ├── AWSSecretsConfigSource.java
    │   ├── AWSSecretsConfigSourceConfiguration.java
    │   ├── SetAWSSecretsConfigSourceConfigurationCommand.java
    │   ├── GetAWSSecretsConfigSourceConfigurationCommand.java
    │   └── client/
    │       ├── AwsRequestBuilder.java
    │       └── AwsAuthFeature.java
    ├── azure/
    │   ├── AzureSecretsConfigSource.java
    │   ├── AzureSecretsConfigSourceConfiguration.java
    │   └── admin/
    ├── gcp/
    │   ├── GCPSecretsConfigSource.java
    │   ├── GCPSecretsConfigSourceConfiguration.java
    │   └── model/
    ├── hashicorp/
    │   ├── HashiCorpSecretsConfigSource.java
    │   ├── HashiCorpSecretsConfigSourceConfiguration.java
    │   └── admin/
    ├── ldap/
    │   ├── LDAPConfigSource.java
    │   ├── LDAPConfigSourceConfiguration.java
    │   ├── LDAPConfigSourceHelper.java
    │   └── admin/
    ├── toml/
    │   ├── TOMLConfigSource.java
    │   ├── TOMLConfigSourceConfiguration.java
    │   └── admin/
    ├── dynamodb/
    │   ├── DynamoDBConfigSource.java
    │   ├── DynamoDBConfigSourceConfiguration.java
    │   └── admin/
    └── oauth/
        └── OAuth2Client.java
```

## Ordinal Values

Config source priority is controlled by ordinal values:

| Source Type | Default Ordinal | Config Property |
|-------------|-----------------|-----------------|
| Cloud sources (AWS, Azure, GCP, HashiCorp) | 150 | `cloudOrdinality` |
| TOML | 100 | `tomlOrdinality` |
| LDAP | 100 | `ldapOrdinality` |

Higher ordinals take precedence. Cloud sources have lower priority than system properties (400) and env vars (300), but higher than default config files (100).

## Dynamic Reloading

Only TOML config source supports automatic file reloading:

```java
private long lastModified;

public Map<String, String> getProperties() {
    Path tomlFilePath = getFilePath();
    long tomlFileLastModified = tomlFilePath.toFile().lastModified();

    if (lastModified == tomlFileLastModified) {
        return properties;  // Use cached values
    }

    // Reload and parse TOML file
    properties.clear();
    lastModified = tomlFileLastModified;
    TomlMapper tomlMapper = new TomlMapper();
    Map<?, ?> config = tomlMapper.readValue(tomlFilePath.toFile(), Map.class);
    flattenToml(config, "", properties, 0, Integer.parseInt(configuration.getDepth()));
    return properties;
}
```

## Write Operations

Not all config sources support write operations:

| Source | setValue() | deleteValue() |
|--------|------------|---------------|
| AWS Secrets | Yes | Yes |
| Azure Key Vault | Yes | Yes |
| GCP Secret Manager | Yes | Yes |
| HashiCorp Vault | Yes | Yes |
| LDAP | No | No |
| TOML | No | No |
| DynamoDB | No | No |

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `microprofile-config-api` | MicroProfile Config API |
| `jackson-datatype-toml` | TOML parsing |
| `jaxrs-client` | REST client for cloud APIs |
| `nimbus-jose-jwt` | JWT signing for OAuth2 |

## Notes

- **Password Aliases** - Cloud credentials use Payara password aliases for security
- **Token Caching** - OAuth2 tokens are cached until expiry
- **Signature V4** - AWS requests use AWS Signature Version 4
- **TOML Flattening** - Nested TOML structures are flattened to dot-notation properties
- **Error Handling** - Misconfiguration is logged; empty properties returned
- **File Watching** - TOML source detects file changes and reloads automatically
