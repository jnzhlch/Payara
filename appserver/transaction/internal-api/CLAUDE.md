# CLAUDE.md - Transaction Internal API

This file provides guidance for working with the `appserver/transaction/internal-api` module - Internal transaction APIs.

## Module Overview

The internal-api module provides internal transaction APIs used by other Payara modules for transaction integration.

**Key Components:**
- **TransactionManager** - Internal transaction manager interface
- **TransactionSynchronizationRegistry** - Synchronization registry

## Build Commands

```bash
# Build internal-api module
mvn -DskipTests clean package -f appserver/transaction/internal-api/pom.xml
```

## Module Contents

```
internal-api/
└── src/main/java/org/glassfish/transaction/...
    ├── TransactionManager.java
    ├── TransactionSynchronizationRegistry.java
    └── ...
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.transaction-api` | JTA API |

## Notes

- **Internal API** - For Payara internal use only
- **Not Public** - Not part of public API
- **Integration** - Used by EJB, JPA, JMS, etc.
