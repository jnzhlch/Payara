# CLAUDE.md - JPA Container Localization

This file provides guidance for working with the `persistence/jpa-container-l10n` module - Localization for JPA container.

## Module Overview

The jpa-container-l10n module provides localized messages and logging for the JPA container. This module contains no Java source code - only localization resource files.

**Purpose:** Provides localized error messages and log output for international users.

## Build Commands

```bash
# Build jpa-container-l10n module
mvn -DskipTests clean package -f appserver/persistence/jpa-container-l10n/pom.xml
```

## Module Contents

This is a localization-only module with:

```
jpa-container-l10n/
└── src/main/resources/
    └── org/
        └── glassfish/
            └── persistence/
                └── jpa/
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
| `jpa-container` | Parent module being localized |

## Notes

- **Localization Module** - Contains only resource bundles
- **No Java Code** - Translated messages only
- **Parent Module** - jpa-container is the functional module
