# CLAUDE.md - Security All

This file provides guidance for working with the `appserver/security/security-all` module - Security aggregator module.

## Module Overview

The security-all module is a packaging module that aggregates all security modules into a single distribution.

**Purpose:** Provides a complete security package for Payara Server.

## Build Commands

```bash
# Build security-all module
mvn -DskipTests clean package -f appserver/security/security-all/pom.xml
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `core-ee` | Core security classes |
| `webintegration` | Web security integration |
| `realm-stores` | Identity stores |
| `ejb.security` | EJB/IIOP security |
| `jacc.provider.inmemory` | JACC provider |
| `webservices.security` | Web services security |

## Notes

- **Aggregator** - Packages all security modules
- **Distribution** - Used in server distributions
