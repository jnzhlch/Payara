# CLAUDE.md - EJB Container Localization

This file provides guidance for working with the `ejb-container-l10n` module - EJB container localization resources.

## Module Overview

The ejb-container-l10n module provides localized message resources for the EJB container.

## Build Commands

```bash
# Build ejb-container-l10n module
mvn -DskipTests clean package -f appserver/ejb/ejb-container-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Localization only

## Contents

Localized resources for:
- EJB container messages
- Deployment error messages
- Timer service messages
- Invocation error messages

## Related Modules

- `ejb-container` - Main container module
