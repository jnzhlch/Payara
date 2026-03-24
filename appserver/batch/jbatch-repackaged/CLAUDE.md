# CLAUDE.md - JBatch Repackaged

This file provides guidance for working with the `jbatch-repackaged` module - IBM JBatch container repackaged as OSGi bundle.

## Module Overview

The jbatch-repackaged module repackages IBM JBatch container and SPI as an OSGi bundle for Payara Server. It combines the JBatch libraries and exports them for use by other batch modules.

## Module Name

- **Artifact ID**: `payara-jbatch`
- **Name**: IBM JBatch Combined

## Build Commands

```bash
# Build jbatch-repackaged
mvn -DskipTests clean package -f appserver/batch/jbatch-repackaged/pom.xml
```

## Build Process

1. **Unpack Dependencies** - Extracts `com.ibm.jbatch.container`
2. **Process HK2 Headers** - Generates HK2 locator configuration
3. **Create OSGi Bundle** - Packages as OSGi bundle with proper imports/exports

## OSGi Configuration

### Exported Packages

```
com.ibm.jbatch.jsl.model
com.ibm.jbatch.spi.services
com.ibm.jbatch.container.services
com.ibm.jbatch.container.servicesmanager
com.ibm.jbatch.container.cdi
com.ibm.jbatch.container.context.impl
com.ibm.jbatch.container.impl
com.ibm.jbatch.container.jobinstance
com.ibm.jbatch.container.persistence
com.ibm.jbatch.container.util
com.ibm.jbatch.container.exception
com.ibm.jbatch.container.status
com.ibm.jbatch.spi
```

### Import Packages

```
jakarta.batch.api
jakarta.batch.api.chunk
jakarta.batch.api.chunk.listener
jakarta.batch.api.listener
jakarta.batch.api.partition
jakarta.batch.operations
jakarta.batch.runtime
jakarta.batch.runtime.context
jakarta.inject
javax.naming
javax.sql
jakarta.transaction
jakarta.xml.bind
...
```

## Dependencies

- `com.ibm.jbatch.spi` - JBatch SPI
- `com.ibm.jbatch.container` - JBatch container

## Integration Points

Used by:
- `glassfish-batch-connector` - GlassFish batch integration
- `hazelcast-jbatch-store` - Hazelcast persistence

## Package Structure

The module is a repackaging of IBM JBatch, so the original package structure is preserved:
- `com.ibm.jbatch.*` - JBatch container classes
- JSL (Job Specification Language) model classes
- SPI (Service Provider Interface) classes

## Related Modules

- `glassfish-batch-connector` - Uses JBatch services
- `hazelcast-jbatch-store` - Integrates with JBatch SPI
