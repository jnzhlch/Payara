# CLAUDE.md - Connectors Admin L10N

This file provides guidance for working with the `admin-l10n` module - localization resources for connectors admin.

## Module Overview

The admin-l10n module provides localized message resources for the JCA admin commands.

## Build Commands

```bash
# Build admin-l10n
mvn -DskipTests clean package -f appserver/connectors/admin-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Localization only

## Contents

Localized resources for:
- Admin command descriptions
- Error messages
- Success messages
- Help text

## Related Modules

- `admin` - Main admin module
