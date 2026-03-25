# CLAUDE.md - JTS Localization

This file provides guidance for working with the `appserver/transaction/jts-l10n` module - Localization for JTS module.

## Module Overview

The jts-l10n module provides localized messages and logging for the JTS (Java Transaction Service) module. This module contains no Java source code - only localization resource files.

**Purpose:** Provides localized error messages and log output for international users.

## Build Commands

```bash
# Build jts-l10n module
mvn -DskipTests clean package -f appserver/transaction/jts-l10n/pom.xml
```

## Module Contents

This is a localization-only module with:

```
jts-l10n/
└── src/main/resources/
    └── org/
        └── glassfish/
            └── transaction/
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
| `jts` | Parent module being localized |

## Notes

- **Localization Module** - Contains only resource bundles
- **No Java Code** - Translated messages only
- **Parent Module** - jts is the functional module
