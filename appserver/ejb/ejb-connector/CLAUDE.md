# CLAUDE.md - EJB Connector

This file provides guidance for working with the `gf-ejb-connector` module - EJB container connector/sniffer.

## Module Overview

The gf-ejb-connector module provides the deployment connector/sniffer that detects EJB JAR files and registers the EJB container with the deployment infrastructure.

## Build Commands

```bash
# Build gf-ejb-connector module
mvn -DskipTests clean package -f appserver/ejb/ejb-connector/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar`
- **Name**: "EJB Container connector for Glassfish"

## Sniffer Function

The EJB sniffer:
1. Detects EJB JAR files (contains ejb-jar.xml or @Stateless/@Stateful/@Singleton classes)
2. Registers EJB container for deployment
3. Provides metadata to deployment system

### Sniffer Registration

```java
@Service
@Sniffer
public class EjbSniffer implements Sniffer {
    @Override
    public String[] getURLPatterns() {
        return new String[] { "ejb-jar.xml", "META-INF/ejb-jar.xml" };
    }

    @Override
    public Container[] getContainers() {
        return new Container[] { getEjbContainer() };
    }

    @Override
    public boolean handles(DeploymentContext context) {
        // Check if this is an EJB JAR
        return hasEjbComponents(context);
    }
}
```

## Deployment Detection

### Archive Detection

```
Archive (JAR)
       │
   [Has ejb-jar.xml?]
       │ Yes ──────► EJB Archive
       │ No
   [Has @Stateless?]
       │ Yes ──────► EJB Archive
       │ No
   [Has @Stateful?]
       │ Yes ──────► EJB Archive
       │ No
   [Has @Singleton?]
       │ Yes ──────► EJB Archive
       │ No
   Not EJB Archive
```

### Container Registration

```java
@Service
public class EjbContainerDeployer {
    @Inject
    private EjbSniffer sniffer;

    @Inject
    private BaseContainerFactory containerFactory;

    public void deploy(DeploymentContext context) {
        if (sniffer.handles(context)) {
            // Create container
            BaseContainer container = containerFactory.createContainer(
                context.getDescriptor(EjbDescriptor.class)
            );

            // Deploy
            container.initialize(context.getDescriptor(EjbDescriptor.class));
        }
    }
}
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.ejb-api` | EJB API |
| `internal-api` | Internal APIs |
| `glassfish-api` | Public APIs |
| `deployment-common` | Deployment infrastructure |
| `hk2-core` | Dependency injection |

## Related Modules

- `ejb-container` - Container implementation
- `deployment-javaee-core` - Deployment services
