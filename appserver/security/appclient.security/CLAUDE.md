# CLAUDE.md - App Client Security

This file provides guidance for working with the `appserver/security/appclient.security` module - Application client security.

## Module Overview

The appclient.security module provides security support for Java EE application clients.

**Key Components:**
- **App client authentication**
- **JAAS login for app clients**

## Build Commands

```bash
# Build appclient.security module
mvn -DskipTests clean package -f appserver/security/appclient.security/pom.xml
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `security-ee` | Core security classes |

## Notes

- **App Client** - Security for standalone clients
- **JAAS** - Java Authentication and Authorization Service
