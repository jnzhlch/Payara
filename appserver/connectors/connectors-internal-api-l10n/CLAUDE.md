# CLAUDE.md - Connectors Internal API L10N

This file provides guidance for working with the `connectors-internal-api-l10n` module - localization resources for connectors internal API.

## Module Overview

The connectors-internal-api-l10n module provides localized message resources for the connectors internal API.

## Build Commands

```bash
# Build connectors-internal-api-l10n
mvn -DskipTests clean package -f appserver/connectors/connectors-internal-api-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Localization only

## Contents

Localized resources for:
- API exception messages
- Internal API logging

## Related Modules

- `connectors-internal-api` - Main internal API module
