# CLAUDE.md - Deployment Client Localization

This file provides guidance for working with the `client-l10n` module - Deployment client localization resources.

## Module Overview

The client-l10n module provides localized message resources for the deployment client.

## Build Commands

```bash
# Build client-l10n module
mvn -DskipTests clean package -f appserver/deployment/client-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)
- **Purpose**: Localization only

## Contents

Localized resources for:
- DeploymentFacility messages
- Remote deployment messages
- Connection status messages
- Deployment progress messages

## Related Modules

- `deployment/client` - Main deployment client module
