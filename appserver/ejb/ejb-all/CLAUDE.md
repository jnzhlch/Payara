# CLAUDE.md - EJB All

This file provides guidance for working with the `ejb-all` module - EJB tier aggregator.

## Module Overview

The ejb-all module bundles all EJB-related Maven modules together. Distributions that need EJB capability should depend on this module instead of pulling in individual EJB modules separately.

## Build Commands

```bash
# Build ejb-all module
mvn -DskipTests clean package -f appserver/ejb/ejb-all/pom.xml
```

## Module Type

- **Packaging**: `pom`
- **Name**: "EJB tier for GlassFish"
- **Purpose**: Aggregates all EJB modules

## Included Modules

```xml
<dependencies>
    <!-- Container connector/sniffer -->
    <dependency>
        <artifactId>gf-ejb-connector</artifactId>
    </dependency>

    <!-- Timer storage (distribution fragment) -->
    <dependency>
        <artifactId>ejb-timer-databases</artifactId>
        <type>distribution-fragment</type>
    </dependency>

    <!-- Internal APIs -->
    <dependency>
        <artifactId>ejb-internal-api</artifactId>
    </dependency>

    <!-- Core container -->
    <dependency>
        <artifactId>ejb-container</artifactId>
    </dependency>
</dependencies>
```

## Usage

Add this module to distributions that need full EJB support:

```xml
<dependency>
    <groupId>fish.payara.server.internal.ejb</groupId>
    <artifactId>ejb-all</artifactId>
    <version>${payara.version}</version>
</dependency>
```

## Related Modules

- All EJB submodules included transitively
