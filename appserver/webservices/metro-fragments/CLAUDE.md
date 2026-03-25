# CLAUDE.md - Metro Fragments

This file provides guidance for working with the `appserver/webservices/metro-fragments` module - Metro distribution fragments for Payara.

## Module Overview

The metro-fragments module is a distribution fragment that packages Metro-related applications and libraries for inclusion in the Payara server distribution. It uses the `distribution-fragment` packaging type, which means its contents are extracted into the final distribution during assembly.

**Purpose:** Package wstx-services.war for WS-Transaction support in Metro.

## Build Commands

```bash
# Build metro-fragments module
mvn -DskipTests clean package -f appserver/webservices/metro-fragments/pom.xml
```

## Distribution Fragment Packaging

This module uses `distribution-fragment` packaging:

```
metro-fragments.jar
       │
       ▼ [Extracted during distribution assembly]
glassfish/lib/install/applications/metro/
       │
       └── wstx-services.war
```

## Module Contents

### wstx-services.war

The WS-Transaction services application provides:
- WS-AT (Atomic Transaction) coordination
- WS-BA (Business Activity) coordination
- Transaction coordinator endpoints

### Installed Location

After installation, the WAR file is located at:
```
$PAYARA_HOME/glassfish/lib/install/applications/metro/wstx-services.war
```

## WS-Transaction Support

### WS-AtomicTransaction (WS-AT)

Enables distributed transaction coordination across web service boundaries:

```java
@WebService
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class TransactionalService {

    @WebMethod
    @Transactional
    public void transferFunds(
            @WebParam(name = "fromAccount") String from,
            @WebParam(name = "toAccount") String to,
            @WebParam(name = "amount") BigDecimal amount) {
        // Transaction coordinated via WS-AT
    }
}
```

### WS-BusinessActivity (WS-BA)

Enables long-running business activities:

```java
@WebService
public class BusinessProcessService {

    @WebMethod
    @BusinessActivity
    public void processOrder(@WebParam(name = "order") Order order) {
        // Business activity coordination
    }
}
```

## Configuration

### Enable WS-Transaction

```xml
<web-service-endpoint>
    <endpoint-address-uri>http://localhost:8080/myservice</endpoint-address-uri>
    <ws-at-enabled>true</ws-at-enabled>
    <ws-ba-enabled>false</ws-ba-enabled>
</web-service-endpoint>
```

### domain.xml Configuration

```xml
<configs>
    <config name="server-config">
        <transaction-service>
            <!-- Transaction service configuration -->
        </transaction-service>

        <web-container>
            <!-- WS-Transaction support is auto-deployed -->
        </web-container>
    </config>
</configs>
```

## Architecture

```
Payara Server Startup
       │
       ▼
[Auto-Deploy wstx-services.war]
       │
       ├─→ /wstx-services endpoint
       │     │
       │     ├─→ /AtomicTransaction
       │     ├─→ /Coordination
       │     └─→ /BusinessActivity
       │
       ▼
[WS-Transaction Coordinator]
       │
       ├─→ Receives transaction context
       ├─→ Coordinates with JTA transaction manager
       └─→ Propagates to other web services
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `wstx-services` | WS-Transaction services WAR |

## Notes

- **Distribution Fragment** - No Java code, only packaged resources
- **Auto-Deployed** - wstx-services.war auto-deployed on startup
- **WS-AT** - Atomic Transaction support
- **WS-BA** - Business Activity support
- **Metro Integration** - Integrates with Metro (JAX-WS) stack
