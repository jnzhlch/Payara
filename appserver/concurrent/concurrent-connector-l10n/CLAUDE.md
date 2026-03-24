# CLAUDE.md - Concurrent Connector L10N

This file provides guidance for working with the `concurrent-connector-l10n` module - localization resources for concurrent connector.

## Module Overview

The concurrent-connector-l10n module provides localized message resources for the concurrent connector configuration beans.

## Build Commands

```bash
# Build concurrent-connector-l10n
mvn -DskipTests clean package -f appserver/concurrent/concurrent-connector-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Localization only

## Contents

Localized resources for:
- Configuration validation messages
- Admin command descriptions
- Error messages

## Integration

Used by `concurrent-connector` for localized configuration messages.

## Related Modules

- `concurrent-connector` - Main connector module
