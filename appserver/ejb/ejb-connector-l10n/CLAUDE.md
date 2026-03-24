# CLAUDE.md - EJB Connector Localization

This file provides guidance for working with the `ejb-connector-l10n` module - EJB connector localization resources.

## Module Overview

The ejb-connector-l10n module provides localized message resources for the EJB connector/sniffer.

## Build Commands

```bash
# Build ejb-connector-l10n module
mvn -DskipTests clean package -f appserver/ejb/ejb-connector-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Localization only

## Contents

Localized resources for:
- Sniffer detection messages
- Container registration messages

## Related Modules

- `gf-ejb-connector` - Main connector module
