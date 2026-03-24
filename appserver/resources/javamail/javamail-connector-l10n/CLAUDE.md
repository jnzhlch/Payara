# CLAUDE.md - JavaMail Connector Localization

This file provides guidance for working with the `appserver/resources/javamail/javamail-connector-l10n` module - Localization for JavaMail connector.

## Module Overview

The javamail-connector-l10n module provides localized messages and logging for the JavaMail connector module. This module contains no Java source code - only localization resource files.

**Purpose:** Provides localized error messages and log output for international users.

## Build Commands

```bash
# Build javamail-connector-l10n module
mvn -DskipTests clean package -f appserver/resources/javamail/javamail-connector-l10n/pom.xml
```

## Module Contents

This is a localization-only module with:

```
javamail-connector-l10n/
└── src/main/resources/
    └── org/
        └── glassfish/
            └── resources/
                └── javamail/
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
| `javamail-connector` | Parent module being localized |

## Notes

- **Localization Module** - Contains only resource bundles
- **No Java Code** - Translated messages only
- **Parent Module** - javamail-connector is the functional module
