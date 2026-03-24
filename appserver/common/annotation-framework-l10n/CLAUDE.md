# CLAUDE.md - Annotation Framework L10N

This file provides guidance for working with the `annotation-framework-l10n` module - localization resources for the annotation framework.

## Module Overview

The annotation-framework-l10n module provides localized message resources (logging, exception messages) for the annotation framework.

## Build Commands

```bash
# Build annotation-framework-l10n
mvn -DskipTests clean package -f appserver/common/annotation-framework-l10n/pom.xml
```

## Module Type

- **Packaging**: `glassfish-jar` (resource jar)

## Contents

Localized resources for annotation framework:
- Log messages
- Exception messages
- Error descriptions

## Integration

Used by `annotation-framework` for localized output.

## Related Modules

- `annotation-framework` - Main annotation processing module
