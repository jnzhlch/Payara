# CLAUDE.md - Connectors Runtime L10N

This file provides guidance for working with the `connectors-runtime-l10n` module - localization resources for connectors runtime.

## Module Overview

The connectors-runtime-l10n module provides localized message resources for the JCA runtime.

## Build Commands

```bash
# Build connectors-runtime-l10n
mvn -DskipTests clean package -f appserver/connectors/connectors-runtime-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Localization only

## Contents

Localized resources for:
- Log messages
- Exception messages
- Deployment messages
- Runtime error messages

## Related Modules

- `connectors-runtime` - Main runtime module
