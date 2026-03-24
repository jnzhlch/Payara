# CLAUDE.md - Web Naming

This file provides guidance for working with the `appserver/web/web-naming` module - JNDI naming for web applications.

## Module Overview

The web-naming module provides JNDI naming context support for web applications, implementing java:comp, java:module, java:app, and java:global namespaces.

**Key Components:**
- **JavaURLContextFactory** - JNDI context factory for java: namespace
- **NamingContext** - Naming context implementation

## Build Commands

```bash
# Build web-naming module
mvn -DskipTests clean package -f appserver/web/web-naming/pom.xml
```

## Module Contents

```
web-naming/
└── src/main/java/org/glassfish/web/
    └── naming/
        ├── JavaURLContextFactory.java
        ├── NamingContext.java
        └── ...
```

## JNDI Namespaces

```
java:comp/env/        - Component-scoped (servlet/filter)
java:module/          - Module-scoped (WAR)
java:app/             - Application-scoped (EAR)
java:global/          - Global-scoped (server)
```

## JavaURLContextFactory

```java
public class JavaURLContextFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name,
                                   Context nameCtx,
                                   Hashtable<?, ?> environment) {
        // Parse java: URL
        String url = obj.toString();

        if (url.startsWith("java:comp/")) {
            return createCompContext();
        } else if (url.startsWith("java:module/")) {
            return createModuleContext();
        } else if (url.startsWith("java:app/")) {
            return createAppContext();
        } else if (url.startsWith("java:global/")) {
            return createGlobalContext();
        }

        throw new NamingException("Invalid namespace: " + url);
    }

    private Context createCompContext() {
        // Create component-scoped context
        return new NamingContext("java:comp/env");
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-naming` | GlassFish naming infrastructure |

## Notes

- **JNDI Namespaces** - java:comp, java:module, java:app, java:global
- **Resource References** - @Resource annotation processing
- **Environment Entries** - @EJB, @PersistenceContext, etc.
