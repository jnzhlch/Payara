# CLAUDE.md - Payara Theme

This file provides guidance for working with the `payara-theme` module - Payara branding and theme.

## Module Overview

The payara-theme module provides Payara-specific branding, theme customization, and visual styling for the admin console.

## Build Commands

```bash
# Build payara-theme
mvn -DskipTests clean package -f appserver/admingui/payara-theme/pom.xml
```

## Key Components

### Theme Plugin

Located in `org.glassfish.admingui.customtheme`:

- `ThemePlugin` - Registers the Payara theme

## Branding Resources

Located in `src/main/resources/branding/`:

| File | Purpose |
|------|---------|
| `favicon.inc` | Favicon icon |
| `loginform.inc` | Login page form |
| `loginimage.inc` | Login page image |
| `masthead.inc` | Header branding |
| `restart.inc` | Restart dialog |
| `shutdown.inc` | Shutdown dialog |
| `upsellee.inc` | Upsell for EE |
| `upsellpe.inc` | Upsell for PE |
| `versioninfo.inc` | Version information |

## Images

Located in `src/main/resources/images/`:

| Image | Purpose |
|-------|---------|
| `favicon.ico` | Favicon |
| `backimage.png` | Background image |
| `login-backimage-open.png` | Login background |
| `login-product_name_open.png` | Login logo |
| `masthead-product_name_open-new.png` | Header logo |
| `VersionProductName.png` | Version product name |

## Configuration

### Theme Properties

Located in `src/main/resources/customtheme.properties`:

Theme customization properties for colors, fonts, and styling.

### Console Config

Located in `src/main/resources/META-INF/admingui/console-config.xml`:

Defines theme integration points.

## Localization

Located in `src/main/resources/org/glassfish/admingui/community-theme/`:

- `Strings.properties` - Theme-related strings

## Package Structure

```
org.glassfish.admingui.customtheme/
└── ThemePlugin.java              # Theme registration
```

## Customization

### Changing Colors

Edit `customtheme.properties`:

```properties
# Primary colors
theme.primary=...
theme.secondary=...

# Font settings
theme.font.family=...
```

### Adding Images

Place new images in `src/main/resources/images/`

### Modifying Branding

Edit the include files in `src/main/resources/branding/`

## Dependencies

- `console-plugin-service` - Plugin infrastructure

## Related Modules

- `core` - Core theme handlers
- `common` - Shared console utilities

## Integration Points

The theme applies to:
- All console pages via branding includes
- Login page
- Header/masthead
- Dialogs (restart, shutdown)
