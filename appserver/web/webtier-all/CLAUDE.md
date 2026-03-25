# CLAUDE.md - Web Tier All

This file provides guidance for working with the `appserver/web/webtier-all` module - Aggregator POM for web-related implementations.

## Module Overview

The webtier-all module is an aggregator POM that collects all web-related Payara/GlassFish implementations into a single dependency. This is used when you need all web container components as a single dependency.

**Purpose:** Convenience dependency for complete web tier.

## Build Commands

```bash
# Build webtier-all module (aggregator)
mvn -DskipTests clean package -f appserver/web/webtier-all/pom.xml
```

## Included Modules

```xml
<dependencies>
    <!-- Web container core -->
    <dependency>
        <artifactId>web-core</artifactId>
    </dependency>

    <!-- Web deployment integration -->
    <dependency>
        <artifactId>web-glue</artifactId>
    </dependency>

    <!-- Security integration -->
    <dependency>
        <artifactId>websecurity</artifactId>
    </dependency>

    <!-- JSTL integration -->
    <dependency>
        <artifactId>jstl-connector</artifactId>
    </dependency>

    <!-- Jersey MVC integration -->
    <dependency>
        <artifactId>jersey-mvc-connector</artifactId>
    </dependency>

    <!-- JSF integration -->
    <dependency>
        <artifactId>jsf-connector</artifactId>
    </dependency>

    <!-- JSP caching integration -->
    <dependency>
        <artifactId>jspcaching-connector</artifactId>
    </dependency>

    <!-- CLI commands -->
    <dependency>
        <artifactId>web-cli</artifactId>
    </dependency>

    <!-- Web connector -->
    <dependency>
        <artifactId>gf-web-connector</artifactId>
    </dependency>

    <!-- Embedded API -->
    <dependency>
        <artifactId>web-embed-api</artifactId>
    </dependency>

    <!-- High availability -->
    <dependency>
        <artifactId>web-ha</artifactId>
    </dependency>

    <!-- GUI plugin -->
    <dependency>
        <artifactId>web-gui-plugin-common</artifactId>
    </dependency>
</dependencies>
```

## Usage

### As a Dependency

```xml
<dependency>
    <groupId>fish.payara.server.internal.web</groupId>
    <artifactId>webtier-all</artifactId>
    <version>${project.version}</version>
    <type>pom</type>
</dependency>
```

### Distribution Packaging

This module is typically used in:
- **Payara Server** distribution packaging
- **Payara Web Profile** distribution packaging
- **Embedded Payara** aggregation

## Module Structure

```
webtier-all/
└── pom.xml (aggregator)
```

## Notes

- **Aggregator Only** - No source code, just POM
- **Convenience Dependency** - All web components in one
- **Distribution Use** - Used in distribution creation
