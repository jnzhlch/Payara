# CLAUDE.md - Transaction Internal API Localization

This file provides guidance for working with the `appserver/transaction/internal-api-l10n` module - Localization for transaction internal API module.

## Module Overview

The internal-api-l10n module provides localized messages and logging for the transaction internal API module. This module contains no Java source code - only localization resource files.

**Purpose:** Provides localized error messages and log output for international users.

## Build Commands

```bash
# Build internal-api-l10n module
mvn -DskipTests clean package -f appserver/transaction/internal-api-l10n/pom.xml
```

## Module Contents

This is a localization-only module with:

```
internal-api-l10n/
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
| `internal-api` | Parent module being localized |

## Notes

- **Localization Module** - Contains only resource bundles
- **No Java Code** - Translated messages only
- **Parent Module** - internal-api is the functional module
