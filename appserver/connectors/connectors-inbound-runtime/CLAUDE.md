# CLAUDE.md - Connectors Inbound Runtime

This file provides guidance for working with the `connectors-inbound-runtime` module - JCA inbound messaging support.

## Module Overview

The connectors-inbound-runtime module provides support for inbound JCA connections, primarily used by Message-Driven Beans (MDB) to receive messages from JMS and other messaging providers.

## Build Commands

```bash
# Build connectors-inbound-runtime
mvn -DskipTests clean package -f appserver/connectors/connectors-inbound-runtime/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Purpose**: Inbound messaging (MDB integration)

## Inbound Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Message-Driven Bean (MDB)                    │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  MDB Container                                │
│  - Activates endpoints                                       │
│  - Delivers messages                                         │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  ResourceAdapter (JMS Provider)               │
│  - MessageEndpointFactory                                    │
│  - MessageEndpoint                                          │
└─────────────────────────────────────────────────────────────┘
```

## Core Components

### Endpoint Activation

```java
public class EndpointActivation {
    // Activate message endpoint
    public void activate(MessageEndpointFactory endpointFactory,
        ResourceAdapter bean);

    // Deactivate endpoint
    public void deactivate();
}
```

### Message Delivery

Flow:
1. ResourceAdapter delivers message to MessageEndpoint
2. MessageEndpoint.beforeDelivery()
3. MDB onMessage() called
4. MessageEndpoint.afterDelivery()

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.resource-api` - JCA 1.7 API |
| `connectors-internal-api` | Internal APIs |
| `deployment-common` | Deployment integration |

## Related Modules

- `connectors-runtime` - Core JCA runtime
- `appserver/ejb` - MDB container
- `work-management` | - Work execution
