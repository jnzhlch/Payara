# CLAUDE.md - Work Management L10N

This file provides guidance for working with the `work-management-l10n` module - localization resources for work management.

## Module Overview

The work-management-l10n module provides localized message resources for JCA Work Management.

## Build Commands

```bash
# Build work-management-l10n
mvn -DskipTests clean package -f appserver/connectors/work-management-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Localization only

## Contents

Localized resources for:
- Work submission messages
- Work execution logging
- Work manager errors

## Related Modules

- `work-management` - Main work management module
