# CLAUDE.md - Realm Stores

This file provides guidance for working with the `appserver/security/realm-stores` module - Identity stores for Jakarta EE Security.

## Module Overview

The realm-stores module provides Jakarta EE Security IdentityStore implementations for Payara's authentication realms (File, Certificate, PAM, Solaris).

**Key Components:**
- **FileRealmIdentityStore** - File-based identity store
- **CertificateRealmIdentityStore** - Certificate identity store
- **PamRealmIdentityStore** - PAM identity store
- **SolarisRealmIdentityStore** - Solaris identity store
- **RealmExtension** - CDI extension for realm configuration

## Build Commands

```bash
# Build realm-stores module
mvn -DskipTests clean package -f appserver/security/realm-stores/pom.xml
```

## Module Contents

```
realm-stores/src/main/java/fish/payara/security/realm/
├── identitystores/
│   ├── FileRealmIdentityStore.java
│   ├── CertificateRealmIdentityStore.java
│   ├── PamRealmIdentityStore.java
│   └── SolarisRealmIdentityStore.java
├── config/
│   ├── FileRealmIdentityStoreConfiguration.java
│   ├── CertificateRealmIdentityStoreConfiguration.java
│   ├── PamRealmIdentityStoreConfiguration.java
│   └── RealmConfiguration.java
├── mechanisms/
│   └── CertificateAuthenticationMechanism.java
└── cdi/
    └── RealmExtension.java
```

## FileRealmIdentityStore

```java
@ApplicationScoped
public class FileRealmIdentityStore implements IdentityStore {

    @Inject
    private FileRealmIdentityStoreConfiguration config;

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (credential instanceof UsernamePasswordCredential) {
            UsernamePasswordCredential upc = (UsernamePasswordCredential) credential;

            // Load keyfile
            FileRealm realm = new FileRealm();
            realm.load(config.getKeyFile());

            // Authenticate
            Principal principal = realm.authenticate(upc.getUsername(),
                                                      upc.getPasswordAsString());

            if (principal != null) {
                Set<String> groups = getGroups(realm, principal);
                return CredentialValidationResult.success(principal.getName(), groups);
            }
        }

        return CredentialValidationResult.INVALID_RESULT;
    }

    private Set<String> getGroups(FileRealm realm, Principal principal) {
        // Get groups from principal
        String[] groups = principal.getGroups();
        return new HashSet<>(Arrays.asList(groups));
    }
}
```

## CertificateRealmIdentityStore

```java
@ApplicationScoped
public class CertificateRealmIdentityStore implements IdentityStore {

    @Inject
    private CertificateRealmIdentityStoreConfiguration config;

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (credential instanceof X509CertificateCredential) {
            X509CertificateCredential x509Credential = (X509CertificateCredential) credential;
            X509Certificate[] certificates = x509Credential.getCertificates();

            // Validate certificate chain
            if (validateCertificateChain(certificates)) {
                // Extract principal from certificate
                X509Certificate cert = certificates[0];
                Principal principal = cert.getSubjectX500Principal();

                // Get groups from certificate or config
                Set<String> groups = getGroups(cert);

                return CredentialValidationResult.success(principal.getName(), groups);
            }
        }

        return CredentialValidationResult.INVALID_RESULT;
    }
}
```

## PamRealmIdentityStore

```java
@ApplicationScoped
public class PamRealmIdentityStore implements IdentityStore {

    @Inject
    private PamRealmIdentityStoreConfiguration config;

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (credential instanceof UsernamePasswordCredential) {
            UsernamePasswordCredential upc = (UsernamePasswordCredential) credential;

            // Use PAM (Pluggable Authentication Modules)
            int result = pamAuthenticate(upc.getUsername(),
                                           upc.getPasswordAsString(),
                                           config.getServiceName());

            if (result == PAM_SUCCESS) {
                // Load groups from PAM
                Set<String> groups = loadPamGroups(upc.getUsername());
                return CredentialValidationResult.success(upc.getUsername(), groups);
            }
        }

        return CredentialValidationResult.INVALID_RESULT;
    }

    private native int pamAuthenticate(String username, String password, String serviceName);
    private native Set<String> loadPamGroups(String username);
}
```

## CDI Extension

```java
public class RealmExtension implements Extension {

    public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery bbd) {
        // Add realm qualifiers
    }

    public void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager bm) {
        // Register realm identity stores based on configuration
    }
}
```

## Configuration

```java
@ApplicationScoped
public class FileRealmIdentityStoreConfiguration {

    @Inject
    @ConfigProperty(name = "security.realm.file.keyfile")
    private String keyFile;

    public String getKeyFile() {
        return keyFile;
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.security.enterprise-api` | Jakarta EE Security API |
| `jakarta.enterprise.cdi-api` | CDI API |
| `payara-api` | Payara API |

## Notes

- **Jakarta EE Security** - Uses Jakarta EE Security IdentityStore API
- **File Realm** - Reads keyfile for authentication
- **Certificate Realm** - X.509 certificate authentication
- **PAM Realm** - Native PAM authentication
- **Solaris Realm** - Solaris-specific authentication
