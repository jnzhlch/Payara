# CLAUDE.md - DataProvider

This file provides guidance for working with the `dataprovider` module - Woodstock DataProvider repackaged.

## Module Overview

The dataprovider module repackages the Woodstock DataProvider as an OSGi bundle for use in the admin console. The DataProvider component handles data binding and table display in JSF pages.

## Build Commands

```bash
# Build dataprovider
mvn -DskipTests clean package -f appserver/admingui/dataprovider/pom.xml
```

## Architecture

### OSGi Bundle Configuration

The module uses `maven-bundle-plugin` to create an OSGi bundle:

```xml
<Embed-Dependency>artifactId=dataprovider;inline=true;</Embed-Dependency>
<Export-Package>com.sun.data.*; password="GlassFish"; mandatory:=password</Export-Package>
<Import-Package>com.sun.sql.*;resolution:=optional,*</Import-Package>
```

## Package Exports

The bundle exports `com.sun.data.*` packages with a password requirement ("GlassFish").

## Dependencies

- `dataprovider` (Woodstock) - Original DataProvider library

## Integration Points

Used by:
- `core` - Core console components
- `common` - Table components and data binding
- All console plugins using Woodstock components

## Related Modules

- `core` - Uses DataProvider for table components
- `common` - Uses DataProvider for data binding
