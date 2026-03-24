# CLAUDE.md - DOL Localization

This file provides guidance for working with the `dol-l10n` module - Deployment Object Library localization resources.

## Module Overview

The dol-l10n module provides localized message resources for the Deployment Object Library (DOL).

## Build Commands

```bash
# Build dol-l10n module
mvn -DskipTests clean package -f appserver/deployment/dol-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Localization only

## Contents

Localized resources for:
- Descriptor parsing messages
- Validation error messages
- Deployment descriptor exceptions
- Archivist messages

## Related Modules

- `deployment/dol` - Main DOL module
