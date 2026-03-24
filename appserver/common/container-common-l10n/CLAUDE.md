# CLAUDE.md - Container Common L10N

This file provides guidance for working with the `container-common-l10n` module - localization resources for container common.

## Module Overview

The container-common-l10n module provides localized message resources for container-common infrastructure.

## Build Commands

```bash
# Build container-common-l10n
mvn -DskipTests clean package -f appserver/common/container-common-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)

## Contents

Localized resources for:
- Injection messages
- Interceptor messages
- Component lifecycle messages
- JPA integration messages

## Integration

Used by `container-common` for localized output.

## Related Modules

- `container-common` - Main container common module
