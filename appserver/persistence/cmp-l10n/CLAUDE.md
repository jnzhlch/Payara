# CLAUDE.md - CMP Localization

This file provides guidance for working with the `persistence/cmp-l10n` module - Localization for CMP module.

## Module Overview

The cmp-l10n module provides localized messages and logging for the CMP (Container Managed Persistence) module.

**Purpose:** Provides localized error messages and log output for CMP-related operations.

## Build Commands

```bash
# Build cmp-l10n module
mvn -DskipTests clean package -f appserver/persistence/cmp-l10n/pom.xml
```

## Submodules

```
cmp-l10n/
├── ejb-mapping-l10n/        # EJB mapping localization
├── enhancer-l10n/            # Bytecode enhancer localization
├── generator-database-l10n/ # Database generator localization
├── model-l10n/                # CMP model localization
├── support-ejb-l10n/         # EJB support localization
├── support-sqlstore-l10n/    # SQL store support localization
└── utility-l10n/             # Utility localization
```

## Module Contents

Each submodule contains:

```
<module>-l10n/
└── src/main/resources/
    └── org/
        └── glassfish/
            └── persistence/
                └── cmp/
                    └── LocalStrings.properties
                    └── LocalStrings_*.properties
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `cmp` | Parent CMP module being localized |
| `cmp/*` | Specific CMP submodule being localized |

## Notes

- **Localization Module** - Contains only resource bundles
- **No Java Code** - Translated messages only
- **Parent Modules** - Corresponds to cmp submodules
