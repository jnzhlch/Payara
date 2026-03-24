# CLAUDE.md - JTS

This file provides guidance for working with the `appserver/transaction/jts` module - JTS (Java Transaction Service) implementation.

## Module Overview

The jts module provides the JTS (Java Transaction Service) implementation for CORBA-based distributed transactions, supporting transactions across multiple JVMs.

**Key Components:**
- **JTSTransactionManager** - JTS transaction manager
- **OTS (Object Transaction Service)** - CORBA transaction service

## Build Commands

```bash
# Build jts module
mvn -DskipTests clean package -f appserver/transaction/jts/pom.xml
```

## Module Contents

```
jts/
└── src/main/java/com/sun/enterprise/transaction/jts/
    ├── JTSTransactionManager.java
    └── ...
```

## JTS Architecture

```
Application (Instance A)
       │
       ▼
[JTS Transaction]
       │
       ├─→ Local Resource (Database)
       │
       └─→ CORBA OTS
           │
           ├─→ Remote Instance B
           └─→ Remote Instance C
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.transaction-api` | JTA API |
| `orb` | CORBA ORB for JTS |
| `transaction-internal-api` | Internal APIs |

## Notes

- **JTS** - CORBA-based distributed transactions
- **OTS** - Object Transaction Service
- **IIOP** - CORBA protocol for inter-JVM communication
- **Multi-JVM** - Transactions across multiple JVMs
