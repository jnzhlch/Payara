# CLAUDE.md - JTA-XA

This file provides guidance for working with the `appserver/transaction/jta-xa` module - JTA XA resource delegation.

## Module Overview

The jta-xa module provides XA (eXtended Architecture) resource delegation support for JTA, enabling distributed transactions across multiple resources (databases, JMS, etc.).

**Key Components:**
- **JavaEETransactionManagerXADelegate** - XA resource management delegation
- **TransactionsRecoveryEventListenerImpl** - Transaction recovery event handling

## Build Commands

```bash
# Build jta-xa module
mvn -DskipTests clean package -f appserver/transaction/jta-xa/pom.xml
```

## Module Contents

```
jta-xa/src/main/java/com/sun/enterprise/transaction/xa/
├── JavaEETransactionManagerXADelegate.java
└── TransactionsRecoveryEventListenerImpl.java
```

## XA Resource Delegation

```java
public class JavaEETransactionManagerXADelegate {

    private TransactionManager transactionManager;

    public void enlistResource(Transaction tx, XAResource xaRes) {
        try {
            // Enlist XA resource in transaction
            tx.enlistResource(xaRes);
        } catch (Exception e) {
            throw new SystemException("Failed to enlist resource: " + e.getMessage());
        }
    }

    public void delistResource(Transaction tx, XAResource xaRes) {
        try {
            // Delist XA resource from transaction
            tx.delistResource(xaRes, XAResource.TMSUCCESS);
        } catch (Exception e) {
            throw new SystemException("Failed to delist resource: " + e.getMessage());
        }
    }

    public void recover() {
        // Recover in-doubt transactions
        List<Xid> inDoubt = getInDoubtTransactions();
        for (Xid xid : inDoubt) {
            commitOrRollback(xid);
        }
    }
}
```

## XA Resource Lifecycle

```
Resource enlistment
       │
       ▼
[enlistResource()]  → Associate XA resource with transaction
       │
       ├─→ xaRes.start(xid)  - Begin association
       ├─→ Transaction work
       └─→ xaRes.end(xid)    - End association
       │
       ▼
[Prepare Phase]
       │
       ├─→ xaRes.prepare(xid) - Vote to commit
       ├─→ XA_OK - Prepared
       ├─→ XA_RDONLY - Read-only
       └─→ XA_RB - Rollback
       │
       ▼
[Commit Phase]
       │
       └─→ xaRes.commit(xid, onePhase) - Commit changes
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.transaction-api` | JTA API |
| `jta` | Core JTA implementation |
| `transaction-internal-api` | Internal transaction APIs |

## Notes

- **XA** - eXtended Architecture for distributed transactions
- **Two-Phase Commit** - XA 2PC protocol
- **Resource Enlistment** - Associating resources with transactions
- **Recovery** - Automatic recovery of in-doubt transactions
- **Delegation** - Delegates XA operations to transaction manager
