# CLAUDE.md - Oracle JDBC Driver Packages

This file provides guidance for working with the `persistence/oracle-jdbc-driver-packages` module - Oracle JDBC drivers.

## Module Overview

The oracle-jdbc-driver-packages module bundles Oracle JDBC drivers for Payara Server. This allows applications to use Oracle databases with JPA and JDBC.

**Purpose:** Provides Oracle JDBC drivers as a convenience for Payara users.

## Build Commands

```bash
# Build oracle-jdbc-driver-packages module
mvn -DskipTests clean package -f appserver/persistence/oracle-jdbc-driver-packages/pom.xml
```

## Module Contents

This module packages Oracle JDBC drivers:

```
oracle-jdbc-driver-packages/
└── (Oracle JDBC JARs bundled during build)
```

## Usage

### DataSource Configuration

```xml
<!-- domain.xml or glassfish-resources.xml -->
<resources>
    <jdbc-resource pool-name="OraclePool"
        jndi-name="jdbc/__default"
        object-type="user">
        <property name="url" value="jdbc:oracle:thin:@localhost:1521:ORCL"/>
        <property name="user" value="scott"/>
        <property name="password" value="tiger"/>
        <property name="driverClassName" value="oracle.jdbc.OracleDriver"/>
    </jdbc-resource>
</resources>
```

### persistence.xml

```xml
<persistence-unit name="myPU" transaction-type="JTA">
    <jta-data-source>jdbc/__default</jta-data-source>

    <properties>
        <!-- Oracle-specific properties -->
        <property name="eclipselink.target-database" value="Oracle"/>
        <property name="eclipselink.orm.register-query-metrics" value="false"/>
    </properties>
</persistence-unit>
```

## Oracle JDBC Driver Versions

The specific Oracle JDBC driver version is determined by the Payara version and build configuration.

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jdbc-runtime` | JDBC connection pooling |

## Notes

- **Packaging Module** - Bundles third-party Oracle JDBC drivers
- **Oracle License** - Oracle drivers have their own license terms
- **Alternative** - Users can provide their own Oracle JDBC drivers
- **Default DataSource** - Can be configured as default JDBC pool
