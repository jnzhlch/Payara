# CLAUDE.md - JACC Provider In-Memory

This file provides guidance for working with the `appserver/security/jacc.provider.inmemory` module - JACC policy provider implementation.

## Module Overview

The jacc.provider.inmemory module provides an in-memory JACC (Jakarta Authorization Contract for Containers) policy provider for Payara Server.

**Key Components:**
- **PolicyProvider** - JACC policy provider
- **PolicyConfiguration** - Policy configuration

## Build Commands

```bash
# Build jacc.provider.inmemory module
mvn -DskipTests clean package -f appserver/security/jacc.provider.inmemory/pom.xml
```

## Module Contents

```
jacc.provider.inmemory/
└── src/main/java/org/glassfish/web/jacc/
    ├── JaccPolicyFactory.java
    ├── JaccPolicyConfiguration.java
    └── ...
```

## JACC Policy Provider

```java
public class JaccPolicyProvider extends Policy {

    private Map<String, PermissionCollection> permissions = new ConcurrentHashMap<>();

    @Override
    public PermissionCollection getPermissions(CodeSource codesource) {
        // Get permissions for codesource
        PermissionCollection pc = permissions.get(codesource.getLocation());
        if (pc == null) {
            pc = new Permissions();
        }
        return pc;
    }

    @Override
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        return getPermissions(domain.getCodeSource());
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
        PermissionCollection pc = getPermissions(domain);
        return pc.implies(permission);
    }

    @Override
    public void refresh() {
        // Refresh policy
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.authorization-api` | Jakarta Authorization API |
| `exousia` | Authorization service |

## Notes

- **JACC** - Jakarta Authorization Contract for Containers
- **In-Memory** - Policy stored in memory
- **Policy Configuration** - Configured at deployment time
