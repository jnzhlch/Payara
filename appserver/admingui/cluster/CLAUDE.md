# CLAUDE.md - Cluster Plugin

This file provides guidance for working with the `cluster` module - clustering console plugin.

## Module Overview

The cluster plugin provides console pages for managing Payara clusters, standalone instances, and Hazelcast-based distributed caching.

## Build Commands

```bash
# Build cluster plugin
mvn -DskipTests clean package -f appserver/admingui/cluster/pom.xml
```

## Key Components

### Console Provider

Located in `org.glassfish.cluster.admingui`:

```java
@Service
public class ClusterConsolePlugin implements ConsoleProvider {
    @Override
    public URL getConfiguration() {
        return null;
    }
}
```

### Handlers

Located in `fish.payara.cluster.admingui`:

| Handler | Purpose |
|---------|---------|
| `PayaraClusterHandlers` | Payara-specific cluster operations |
| `PayaraRestApiHandlers` | REST API handlers for cluster |

## Integration Points

The cluster plugin defines integration points for:
- Cluster management (create/delete clusters)
- Instance management (start/stop instances)
- Hazelcast configuration
- Cluster member management
- GMS (Group Management Service) settings

## Package Structure

```
org.glassfish.cluster.admingui/
└── ClusterConsolePlugin.java     # Plugin provider

fish.payara.cluster.admingui/
├── PayaraClusterHandlers.java    # Payara cluster handlers
└── rest/
    └── PayaraRestApiHandlers.java # REST handlers
```

## Dependencies

- `console-plugin-service` - Plugin infrastructure
- `console-common` - Shared utilities
- `faces-compat` - JSF compatibility

## Related Modules

- `common` - Shared handlers (ClusterHandler)
- `nucleus/cluster` - Cluster infrastructure
- `nucleus/payara-modules/hazelcast-bootstrap` - Hazelcast integration
