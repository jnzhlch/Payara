# CLAUDE.md - Web Services Security

This file provides guidance for working with the `appserver/security/webservices.security` module - Web services security implementation.

## Module Overview

The webservices.security module provides security for JAX-WS web services including WS-Security, XML signature, and encryption.

**Key Components:**
- **WS-Security** - SOAP message security
- **XML Signature** - XML digital signatures
- **XML Encryption** - XML encryption

## Build Commands

```bash
# Build webservices.security module
mvn -DskipTests clean package -f appserver/security/webservices.security/pom.xml
```

## Module Contents

```
webservices.security/
└── src/main/java/com/sun/xml/ws/security/...
    ├── ...
```

## Web Services Security

```
SOAP Message
       │
       ▼
[WS-Security Layer]
       │
       ├─→ XML Signature
       ├─→ XML Encryption
       ├─→ Username Token
       └─→ Timestamp
       │
       ▼
[Secure SOAP Message]
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `metro` | JAX-WS implementation |
| `xmlsec** | XML security |

## Notes

- **WS-Security** - SOAP message security standards
- **XML Signature** - Digital signatures for XML
- **XML Encryption** - XML data encryption
