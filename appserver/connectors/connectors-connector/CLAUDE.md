# CLAUDE.md - Connectors Connector

This file provides guidance for working with the `connectors-connector` module - RAR deployment detection.

## Module Overview

The connectors-connector module provides the deployment infrastructure for identifying and processing Resource Adapter Archive (RAR) files.

## Build Commands

```bash
# Build connectors-connector
mvn -DskipTests clean package -f appserver/connectors/connectors-connector/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Purpose**: Deployment glue code for RAR files

## Core Components

### RarDetector

Detects RAR files during deployment:

```java
@Service
public class RarDetector implements Detector {
    @Override
    public boolean handles(File archive) {
        // Check if archive is a RAR
        // Returns true for .rar files or with META-INF/ra.xml
    }
}
```

### RarSniffer

Application type detection:

```java
@Service
public class RarSniffer implements Sniffer {
    @Override
    public String[] getContainersFeature() {
        return new String[] { "connector" };
    }

    @Override
    public Container[] getContainers() {
        return new Container[] { connectorContainer };
    }
}
```

### RarType

Marks application type:

```java
public class RarType extends ApplicationType {
    public static final RarType RAR = new RarType();
}
```

## Deployment Flow

```
Archive File (.rar)
       │
   RarDetector
       │
   [is RAR?]
       │
   RarSniffer
       │
   RarType marker
       │
   ConnectorDeployer
```

## Package Structure

```
com.sun.enterprise.connectors.connector.module/
├── RarDetector.java                   # RAR file detection
├── RarSniffer.java                    # Application type sniffing
└── RarType.java                       # Type marker
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `hk2-core` | Dependency injection |
| `connectors-internal-api` | Connector APIs |
| `common-util` | Utilities |
| `internal-api` - Server APIs |
| `glassfish-api` | Public APIs |
| `deployment-common` | Deployment infrastructure |

## Related Modules

- `connectors-runtime` - Uses detector/sniffer
- `deployment/*` - Deployment integration
