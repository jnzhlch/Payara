# CLAUDE.md - Admin Core

This file provides guidance for working with the `admin-core` module.

## Module Overview

The admin-core module provides the `AdminContext` interface, which defines the environment for administration operations in Payara Server.

## Build Commands

```bash
# Build admin-core
mvn -DskipTests clean package -f appserver/admin/admin-core/pom.xml
```

## Architecture

### AdminContext Interface

Located in `com.sun.enterprise.admin`:

```java
public interface AdminContext {
    // MBeanServer access
    MBeanServer getMBeanServer();
    void setMBeanServer(MBeanServer mbs);

    // Domain and server names
    String getDomainName();
    void setDomainName(String domainName);
    String getServerName();
    void setServerName(String serverName);

    // MBean registry URLs
    URL getAdminMBeanRegistryURL();
    URL getRuntimeMBeanRegistryURL();

    // Admin logging
    Logger getAdminLogger();
    void setAdminLogger(Logger logger);

    // MBeanServer interceptor
    Interceptor getMBeanServerInterceptor();
    void setMBeanServerInterceptor(Interceptor interceptor);

    // Dotted name MBean implementation
    String getDottedNameMBeanImplClassName();
}
```

## Package Structure

```
com.sun.enterprise.admin/
└── AdminContext.java    # Admin environment interface
```

## Dependencies

- `admin-util` - Admin utilities including proxy interceptor
- `common-util` - Common utilities

## Integration Points

The `AdminContext` is used by:
- Management infrastructure for MBean operations
- Configuration management
- Monitoring and logging
- Remote admin operations
