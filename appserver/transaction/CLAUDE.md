# CLAUDE.md - Transaction

This file provides guidance for working with the `appserver/transaction` module - JTA (Java Transaction API) implementation.

## Module Overview

The transaction module provides Payara Server's JTA transaction manager implementation, supporting distributed transactions, transaction recovery, and integration with Jakarta EE components.

**Key Components:**
- **jta** - JTA transaction manager
- **jts** - JTS (CORBA-based transactions)
- **internal-api** - Transaction internal APIs

## Build Commands

```bash
# Build entire transaction module
mvn -DskipTests clean package -f appserver/transaction/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/transaction/jta/pom.xml
mvn -DskipTests clean package -f appserver/transaction/jts/pom.xml
```

## Architecture

### Module Structure

```
transaction/
├── jta/                    # JTA transaction manager
├── jts/                    # JTS (CORBA transactions)
├── jta-xa/                 # XA resource delegation
├── internal-api/           # Internal transaction APIs
├── jta-l10n/               # JTA localization
├── jts-l10n/               # JTS localization
└── internal-api-l10n/      # Internal API localization
```

### Transaction Flow

```
Application (@Transactional)
       │
       ▼
[TransactionInterceptor]
       │
       ├─→ Begin transaction (if needed)
       │
       ▼
[Method Execution]
       │
       ├─→ EJB calls (propagates TX)
       ├─→ JPA operations (uses TX)
       ├─→ JMS operations (uses TX)
       └─→ Resource enlistment
       │
       ▼
[Commit/Rollback]
```

## JTA Transaction Manager

### Transaction Management

```java
@Service
public class JavaEETransactionManager implements TransactionManager, UserTransaction {

    // Transaction status constants
    int STATUS_ACTIVE = Status.STATUS_ACTIVE;
    int STATUS_COMMITTED = Status.STATUS_COMMITTED;
    int STATUS_ROLLED_BACK = Status.STATUS_ROLLED_BACK;
    int STATUS_NO_TRANSACTION = Status.STATUS_NO_TRANSACTION;

    @Override
    public void begin() throws NotSupportedException, SystemException {
        // Begin new transaction
        Transaction tx = createTransaction();
        setCurrentTransaction(tx);
    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException,
            HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {
        Transaction tx = getCurrentTransaction();

        // Delist resources
        delistResources(tx);

        // Commit transaction
        if (tx.getStatus() == STATUS_ACTIVE) {
            tx.commit();
        }

        setCurrentTransaction(null);
    }

    @Override
    public void rollback() throws IllegalStateException, SecurityException, SystemException {
        Transaction tx = getCurrentTransaction();

        // Rollback transaction
        tx.rollback();

        setCurrentTransaction(null);
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException, SystemException {
        Transaction tx = getCurrentTransaction();
        tx.setRollbackOnly();
    }

    @Override
    public int getStatus() throws SystemException {
        Transaction tx = getCurrentTransaction();
        if (tx == null) {
            return STATUS_NO_TRANSACTION;
        }
        return tx.getStatus();
    }

    @Override
    public Transaction getTransaction() throws SystemException {
        return getCurrentTransaction();
    }

    @Override
    public void setTransactionTimeout(int seconds) throws SystemException {
        // Set transaction timeout
    }
}
```

### Resource Enlistment

```java
public class JavaEETransaction implements Transaction {

    private List<XAResource> enlistedResources = new ArrayList<>();
    private List<Synchronization> synchronizations = new ArrayList<>();

    @Override
    public boolean enlistResource(XAResource xaRes) throws RollbackException,
            IllegalStateException, SystemException {
        if (!enlistedResources.contains(xaRes)) {
            // Start association with resource
            xaRes.start(getXid(), XAResource.TMNOFLAGS);
            enlistedResources.add(xaRes);
            return true;
        }
        return false;
    }

    @Override
    public boolean delistResource(XAResource xaRes, int flag) throws IllegalStateException,
            SystemException {
        if (enlistedResources.contains(xaRes)) {
            // End association with resource
            xaRes.end(getXid(), flag);
            enlistedResources.remove(xaRes);
            return true;
        }
        return false;
    }

    @Override
    public void registerSynchronization(Synchronization sync) throws RollbackException,
            IllegalStateException, SystemException {
        synchronizations.add(sync);
    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException,
            HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {

        // Phase 1: Prepare
        for (XAResource xaRes : enlistedResources) {
            int prepareResult = xaRes.prepare(getXid());
            if (prepareResult == XAResource.XA_RDONLY) {
                // Read-only, no need to commit
            } else if (prepareResult == XAResource.XA_OK) {
                // Prepared successfully
            } else {
                throw new RollbackException("Prepare failed");
            }
        }

        // Phase 2: Commit
        for (XAResource xaRes : enlistedResources) {
            xaRes.commit(getXid(), false);
        }

        // Notify synchronizations
        for (Synchronization sync : synchronizations) {
            sync.afterCompletion(Status.STATUS_COMMITTED);
        }
    }

    @Override
    public void rollback() throws IllegalStateException, SystemException {

        // Rollback all resources
        for (XAResource xaRes : enlistedResources) {
            xaRes.rollback(getXid());
        }

        // Notify synchronizations
        for (Synchronization sync : synchronizations) {
            sync.afterCompletion(Status.STATUS_ROLLED_BACK);
        }
    }
}
```

## @Transactional Support

### CDI Interceptors

```java
@Transactional(Transactional.TxType.REQUIRES_NEW)
public class UserService {

    @Inject
    private UserRepository repository;

    public User createUser(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        return repository.save(user);
    }
}
```

### Transaction Attributes

```java
@Transactional(TxType.REQUIRED)       // Join existing or create new (default)
@Transactional(TxType.REQUIRES_NEW)    // Always create new
@Transactional(TxType.MANDATORY)       // Must have existing TX
@Transactional(TxType.SUPPORTS)        // Use existing TX if available
@Transactional(TxType.NOT_SUPPORTED)  // Suspend existing TX
@Transactional(TxType.NEVER)           // Must not have TX
```

## JTA Support

### UserTransaction

```java
@Resource
private UserTransaction userTransaction;

public void transferMoney(String fromAccount, String toAccount, BigDecimal amount) {
    try {
        userTransaction.begin();

        // Debit from account
        accountService.debit(fromAccount, amount);

        // Credit to account
        accountService.credit(toAccount, amount);

        userTransaction.commit();
    } catch (Exception e) {
        try {
            userTransaction.rollback();
        } catch (SystemException se) {
            // Handle rollback error
        }
    }
}
```

### TransactionSynchronizationRegistry

```java
@Resource
private TransactionSynchronizationRegistry tsr;

public void storeObject(Object obj) {
    // Store object in transaction-scoped map
    tsr.putResource("myObject", obj);
}

public Object retrieveObject() {
    // Retrieve object from transaction-scoped map
    return tsr.getResource("myObject");
}
```

## JTS Support

JTS (Java Transaction Service) provides CORBA-based distributed transactions:

```
Application on Instance A
       │
       ▼
[JTS Transaction]
       │
       ├─→ Local Resource (Database A)
       ├─→ Remote Resource (Database B on Instance B)
       └─→ Remote Resource (Database C on Instance C)
       │
       ▼
[Two-Phase Commit across cluster]
```

## Recovery

### Transaction Recovery

```java
@Service
public class TransactionRecoveryManager {

    public void recover() {
        // Recover in-doubt transactions after server crash
        List<Xid> inDoubtTransactions = getInDoubtTransactions();

        for (Xid xid : inDoubtTransactions) {
            // Commit or rollback based on heuristic
            if (canCommit(xid)) {
                commitRecovered(xid);
            } else {
                rollbackRecovered(xid);
            }
        }
    }
}
```

## Configuration

### domain.xml

```xml
<transaction-service>
    <automatic-recovery>true</automatic-recovery>
    <timeout-in-seconds>300</timeout-in-seconds>
    <tx-log-dir>${com.sun.aas.instanceRoot}/logs/tx</tx-log-dir>
    <heuristic-decision>rollback</heuristic-decision>
    <keypoint-interval>2048</keypoint-interval>
    <retry-timeout-in-seconds>600</retry-timeout-in-seconds>
    <commit-retry-timeout-in-seconds>600</commit-retry-timeout-in-seconds>
</transaction-service>
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.transaction-api` | JTA API |
| `internal-api` | Internal transaction APIs |

## Notes

- **JTA 2.0** - Jakarta Transactions API
- **Two-Phase Commit** - 2PC for distributed transactions
- **Recovery** - Automatic transaction recovery
- **Timeout** - Configurable transaction timeout
- **Heuristics** - Handling heuristic outcomes
- **JTS** - Optional CORBA-based transactions
