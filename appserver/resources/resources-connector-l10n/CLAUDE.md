# CLAUDE.md - Resources Connector Localization

This file provides guidance for working with the `appserver/resources/resources-connector-l10n` module - Localization for resources connector.

## Module Overview

The resources-connector-l10n module provides localized messages and logging for the resources connector module. This module contains no Java source code - only localization resource files.

**Purpose:** Provides localized error messages and log output for international users.

## Build Commands

```bash
# Build resources-connector-l10n module
mvn -DskipTests clean package -f appserver/resources/resources-connector-l10n/pom.xml
```

## Module Contents

This is a localization-only module with:

```
resources-connector-l10n/
└── src/main/resources/
    └── org/
        └── glassfish/
            └── resources/
                └── LocalStrings.properties
                └── LocalStrings_*.properties
```

### Localization Files

| Language | File | Purpose |
|----------|------|---------|
| English | LocalStrings.properties | Default messages |
| Other | LocalStrings_*.properties | Translated messages |

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `resources-connector` | Parent module being localized |

## Notes

- **Localization Module** - Contains only resource bundles
- **No Java Code** - Translated messages only
- **Parent Module** - resources-connector is the functional module
