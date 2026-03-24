# CLAUDE.md - Java EE Core Localization

This file provides guidance for working with the `javaee-core-l10n` module - Java EE Core deployment localization resources.

## Module Overview

The javaee-core-l10n module provides localized message resources for the Java EE Core deployment services.

## Build Commands

```bash
# Build javaee-core-l10n module
mvn -DskipTests clean package -f appserver/deployment/javaee-core-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Localization only

## Contents

Localized resources for:
- DolProvider messages
- Deployer messages
- Application deployment status
- Command execution feedback

## Related Modules

- `deployment/javaee-core` - Main Java EE Core module
