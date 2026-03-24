# CLAUDE.md - Stats77 L10N

This file provides guidance for working with the `stats77-l10n` module - localization resources for JSR 77 statistics.

## Module Overview

The stats77-l10n module provides localized message resources for JSR 77 statistics.

## Build Commands

```bash
# Build stats77-l10n
mvn -DskipTests clean package -f appserver/common/stats77-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)

## Contents

Localized resources for:
- Statistic descriptions
- Error messages
- Monitoring messages

## Integration

Used by `stats77` for localized output.

## Related Modules

- `stats77` - Main statistics module
