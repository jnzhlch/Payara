# CLAUDE.md - JTA

This file provides guidance for working with the `appserver/transaction/jta` module - JTA transaction manager implementation.

## Module Overview

The jta module provides the JTA (Java Transaction API) transaction manager implementation for Payara Server, supporting distributed transactions with XA resources.

**Key Components:**
- **JavaEETransactionManager** - Transaction manager
- **JavaEETransaction** - Transaction implementation
- **XAResourceWrapper** - XA resource handling

## Build Commands

```bash
# Build jta module
mvn -DskipTests clean package -f appserver/transaction/jta/pom.xml
```

## Module Contents

```
jta/
└── src/main/java/com/sun/enterprise/transaction/
    ├── JavaEETransactionManager.java
    ├── JavaEETransaction.java
    ├── XAResourceWrapper.java
    └── ...
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.transaction-api` | JTA API |
| `transaction-internal-api` | Internal APIs |

## Notes

- **JTA 2.0** - Jakarta Transactions API
- **XA Support** - XAResource enlistment/delistment
- **Recovery** - Transaction recovery after crash
- **Two-Phase Commit** - 2PC protocol for distributed TX
