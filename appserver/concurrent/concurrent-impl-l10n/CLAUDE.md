# CLAUDE.md - Concurrent Implementation L10N

This file provides guidance for working with the `concurrent-impl-l10n` module - localization resources for concurrent implementation.

## Module Overview

The concurrent-impl-l10n module provides localized message resources for the Concurrency Utilities implementation.

## Build Commands

```bash
# Build concurrent-impl-l10n
mvn -DskipTests clean package -f appserver/concurrent/concurrent-impl-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Localization only

## Contents

Localized resources for:
- Log messages
- Admin command output
- Error messages
- Monitoring descriptions
- Deployment messages

## Integration

Used by `concurrent-impl` for localized logging and messages.

## Related Modules

- `concurrent-impl` - Main implementation module
