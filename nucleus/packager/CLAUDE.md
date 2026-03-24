# CLAUDE.md - Nucleus Packager

This file provides guidance for working with the Nucleus Packager - the assembly and distribution build system.

## Module Overview

The packager creates the Payara distribution by assembling modules into packages, creating the directory structure, and generating installers.

## Package Structure

```
packager/
├── nucleus/                      # Core nucleus package
│   ├── src/
│   │   └── main/
│   │       └── resources/
│   │           └── assemble/     # Assembly configuration
├── nucleus-cluster/              # Clustering support
├── nucleus-common/               # Common libraries
├── nucleus-corba-base/           # CORBA support
├── nucleus-felix/                # OSGi framework (Felix)
├── nucleus-grizzly/              # HTTP layer (Grizzly)
├── nucleus-hk2/                  # HK2 injection framework
├── nucleus-jersey/               # JAX-RS (Jersey)
├── nucleus-jmx/                  # JMX support
├── nucleus-management/           # Management modules
├── nucleus-osgi/                 # OSGi core
└── ... (various feature packages)
```

## Build Commands

```bash
# Build all packages
mvn -DskipTests clean package -f nucleus/packager/pom.xml

# Build specific package
mvn -DskipTests clean package -f nucleus/packager/nucleus/pom.xml
```

## Assembly Process

1. **Collect dependencies** - Maven resolves module dependencies
2. **Apply assembly rules** - `src/main/resources/assemble/*.xml`
3. **Package structure** - Creates directory layout
4. **Output** - Generates `.zip` distributions

## Assembly Configuration

Each package has an assembly descriptor:

```xml
<assembly>
    <id>nucleus</id>
    <formats>
        <format>zip</format>
    </formats>
    <!-- Module dependencies and file layout -->
</assembly>
```

## Package Dependencies

Packages reference each other via Maven:

```xml
<dependency>
    <groupId>fish.payara.server.internal.packager</groupId>
    <artifactId>nucleus-common</artifactId>
    <version>${project.version}</version>
    <type>zip</type>
</dependency>
```

## Distribution Output

Built packages create:
- `nucleus/distributions/payara/target/payara.zip` - Full server
- `nucleus/distributions/payara/target/stage/` - Staged directory

## Key Concepts

- **Package** - Logical grouping of modules
- **Assembly** - Maven Assembly plugin configuration
- **Feature pack** - Optional functionality (clustering, etc.)
- **Distribution** - Final deliverable (zip, installer)

## Modifying Packages

To add a new module to a package:
1. Add Maven dependency to package's `pom.xml`
2. Update assembly descriptor (if needed)
3. Rebuild the package
