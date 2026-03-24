# CLAUDE.md - GlassFish Naming L10N

This file provides guidance for working with the `glassfish-naming-l10n` module - localization resources for naming service.

## Module Overview

The glassfish-naming-l10n module provides localized message resources for the JNDI naming service.

## Build Commands

```bash
# Build glassfish-naming-l10n
mvn -DskipTests clean package -f appserver/common/glassfish-naming-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)

## Contents

Localized resources for:
- JNDI lookup messages
- Naming exception messages
- Remote/IIOP communication errors
- Cluster naming messages

## Integration

Used by `glassfish-naming` for localized error messages and logging.

## Related Modules

- `glassfish-naming` - Main naming service module
