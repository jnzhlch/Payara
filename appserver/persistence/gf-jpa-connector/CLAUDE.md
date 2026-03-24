# CLAUDE.md - JPA Connector

This file provides guidance for working with the `persistence/gf-jpa-connector` module - GlassFish JPA runtime connector.

## Module Overview

The gf-jpa-connector module connects the JPA runtime with GlassFish deployment infrastructure. It provides the integration layer between JPA applications and the GlassFish kernel.

**Key Features:**
- **Deployment Integration** - Connects JPA with deployment framework
- **DOL Integration** - Parses persistence.xml descriptors
- **Connector Runtime** - DataSource lookup integration

## Build Commands

```bash
# Build gf-jpa-connector module
mvn -DskipTests clean package -f appserver/persistence/gf-jpa-connector/pom.xml
```

## Module Contents

This is a lightweight integration module with minimal Java code:

```
gf-jpa-connector/
└── (configuration and resources only)
```

### Resources

```properties
# Version and provider information
```

## Integration Points

### Deployment Integration

```
JPA Application (persistence.xml)
       │
       ▼
[DOL - Deployment Object Library]
       │
       ├─→ Parse persistence.xml
       ├─→ Create PersistenceUnitDescriptor
       └─→ Create PersistenceUnitsDescriptor
       │
       ▼
[JPA Container]
       │
       └─→ Create EntityManagerFactory
```

### DataSource Integration

```java
// JNDI lookup for DataSource
DataSource ds = (DataSource) new InitialContext()
    .lookup("java:comp/env/jdbc/MyDataSource");

// JTA DataSource for JPA
<persistence-unit name="myPU" transaction-type="JTA">
    <jta-data-source>jdbc/__default</jta-data-source>
</persistence-unit>
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `dol` | PersistenceUnitDescriptor parsing |
| `deployment-common` | Deployment integration |
| `common-util` | Utility classes |
| `container-common` | Container integration |
| `glassfish-api` | Public APIs |
| `internal-api` | Internal APIs |

## Notes

- **Lightweight Module** - Integration layer with minimal code
- **DOL Integration** - Uses deployment object library for parsing
- **Connector Runtime** - Delegates to connectors module for DataSource
