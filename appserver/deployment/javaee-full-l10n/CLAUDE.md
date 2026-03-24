# CLAUDE.md - Java EE Full Localization

This file provides guidance for working with the `javaee-full-l10n` module - Java EE Full Profile deployment localization resources.

## Module Overview

The javaee-full-l10n module provides localized message resources for Java EE Full Profile deployment features.

## Build Commands

```bash
# Build javaee-full-l10n module
mvn -DskipTests clean package -f appserver/deployment/javaee-full-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Localization only

## Contents

Localized resources for:
- JSR-88 deployment messages
- DeploymentManager messages
- Progress tracking messages

## Related Modules

- `deployment/javaee-full` - Main Java EE Full module
