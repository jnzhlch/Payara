# CLAUDE.md - Weld Integration Test Fragment

This file provides guidance for working with the `appserver/web/weld-integration-test-fragment` module - OSGi fragment bundle for Weld testing.

## Module Overview

The weld-integration-test-fragment module is an OSGi fragment bundle that extends the Weld OSGi bundle to export additional packages required by Weld Arquillian tests and other testing scenarios.

**Purpose:** Export Weld test packages for Arquillian testing integration.

## Build Commands

```bash
# Build weld-integration-test-fragment module
mvn -DskipTests clean package -f appserver/web/weld-integration-test-fragment/pom.xml
```

## OSGi Fragment Bundle

### Fragment Host

```xml
<Fragment-Host>org.jboss.weld.osgi-bundle</Fragment-Host>
```

This fragment extends the Weld OSGi bundle for testing purposes.

### Exported Packages

```xml
<Export-Package>
    <!-- Literal annotations -->
    org.jboss.weld.literal,

    <!-- Logging -->
    org.jboss.weld.logging,
    org.jboss.weld.logging.messages,

    <!-- Metadata validation -->
    org.jboss.metadata.validation,

    <!-- Interceptor -->
    org.jboss.weld.bean.interceptor,

    <!-- Metadata -->
    org.jboss.weld.metadata,
    org.jboss.weld.metadata.cache,

    <!-- Resources -->
    org.jboss.weld.resources,

    <!-- Test support -->
    org.jboss.weld.test,
    org.jboss.weld.tests,
    org.jboss.weld.tests.extensions,
    org.jboss.weld.tests.extensions.injectionTarget,

    <!-- Exceptions -->
    org.jboss.weld.exceptions
</Export-Package>
```

## Architecture

```
Weld OSGi Bundle (org.jboss.weld.osgi-bundle)
       │
       ▼
[Weld Test Fragment]
       │
       ├─→ Exports test-related packages
       ├─→ Enables Arquillian testing
       └─→ Provides test utilities
```

## Purpose of Exported Packages

### Test Support

- `org.jboss.weld.test` - Test utilities
- `org.jboss.weld.tests` - Test framework
- `org.jboss.weld.tests.extensions` - Extension testing

### Metadata and Validation

- `org.jboss.weld.metadata` - Metadata handling
- `org.jboss.weld.metadata.cache` - Metadata caching
- `org.jboss.weld.metadata.validation` - Validation utilities

### Interceptor Testing

- `org.jboss.weld.bean.interceptor` - Interceptor bean testing

### Logging

- `org.jboss.weld.logging` - Logging utilities
- `org.jboss.weld.logging.messages` - Log messages

### Resources

- `org.jboss.weld.resources` - Resource loading for tests

## Arquillian Integration

This fragment enables Arquillian testing with Weld in OSGi:

```java
@RunWith(Arquillian.class)
public class WeldIntegrationTest {

    @Inject
    private MyService myService;

    @Test
    public void testInjection() {
        assertNotNull(myService);
        // Test CDI injection in Arquillian
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `weld-osgi-bundle` | Fragment host (Weld OSGi bundle) |

## Notes

- **Fragment Bundle** - Extends Weld OSGi bundle
- **Testing** - For Weld Arquillian tests
- **Test Utilities** - Exports test support packages
- **OSGi Only** - Used in OSGi/GlassFish module system
