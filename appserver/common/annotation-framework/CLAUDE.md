# CLAUDE.md - Annotation Framework

This file provides guidance for working with the `annotation-framework` module - annotation processing for Jakarta EE.

## Module Overview

The annotation-framework module provides the infrastructure for scanning and processing Jakarta EE annotations during application deployment.

## Build Commands

```bash
# Build annotation-framework
mvn -DskipTests clean package -f appserver/common/annotation-framework/pom.xml
```

## Core Components

### AnnotationProcessor

Main annotation processing engine:

```java
AnnotationProcessor processor = // get processor
ProcessingContext context = processor.createContext();
context.setClassLoader(appClassLoader);
context.setErrorHandler(errorHandler);
ProcessingResult result = processor.process(context);
```

**Key Methods:**
- `createContext()` - Create new processing context
- `process(ProcessingContext)` - Execute annotation processing
- `pushAnnotationHandler()` - Register annotation handler
- `popAnnotationHandler()` - Unregister handler

### AnnotationHandler

Per-annotation type processing:

```java
@AnnotationHandlerFor(EJB.class)
public class EJBAnnotationHandler implements AnnotationHandler {
    public HandlerProcessingResult process(AnnotationInfo info) {
        // Process @EJB annotation
    }
}
```

### ProcessingContext

Configuration for annotation processing:

| Property | Purpose |
|----------|---------|
| `ClassLoader` - Application class loader |
| `Scanner` - Class scanning source |
| `ErrorHandler` - Error handling strategy |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Deployment Process                         │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    AnnotationProcessor                        │
│  - Scans classes for annotations                              │
│  - Matches annotations to handlers                            │
│  - Invokes handlers in registration order                     │
└─────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
    @EJB Handler        @Resource Handler    @WebService Handler
         │                    │                    │
    EJB Metadata        Resource Metadata    Web Service Metadata
```

## Processing Flow

1. **Scanning** - Scanner provides classes to process
2. **Annotation Discovery** - Find all annotations on each class
3. **Handler Selection** - Match annotations to registered handlers
4. **Invocation** - Call handlers in LIFO order (last pushed = first called)
5. **Result Collection** - Aggregate handler results

## Handler Registration

```java
// Register handlers (LIFO - last registered = first called)
processor.pushAnnotationHandler(new EJBAnnotationHandler());
processor.pushAnnotationHandler(new ResourceAnnotationHandler());
processor.pushAnnotationHandler(new WebServiceRefHandler());
```

## Annotation Types

### Common Processed Annotations

| Annotation | Handler Location |
|------------|------------------|
| `@EJB` | EJB container |
| `@Resource` | Resource injection |
| `@WebServiceRef` | Web services |
| `@PersistenceContext` | JPA |
| `@DataSourceDefinition` | JDBC |
| `@PostConstruct` / `@PreDestroy` | Lifecycle |

## Processing Context

### AnnotationContext

Contains information about annotation instance:

```java
public interface AnnotationContext {
    AnnotatedElement getAnnotatedElement();  // Field, Method, Class
    Class<?> getAnnotationType();            // Annotation class
    Annotation getAnnotation();              // Annotation instance
    ProcessingContext getProcessingContext(); // Parent context
}
```

### ComponentInfo

Metadata about processed component:

```java
public interface ComponentInfo {
    Class<?> getComponentClass();
    Annotation[] getAnnotations();
    // Additional metadata
}
```

## Error Handling

### ErrorHandler

Processes annotation errors:

```java
public interface ErrorHandler {
    void error(AnnotationInfo info, ProcessingError error);
}
```

### Result Types

| Result | Description |
|--------|-------------|
| `PROCESSED` - Annotation successfully processed |
| `DEFERRED` - Processing deferred to later phase |
| `FAILED` - Processing failed |

## Package Structure

```
org.glassfish.apf/
├── AnnotationProcessor.java          # Main processor
├── AnnotationHandler.java            # Handler interface
├── AnnotationHandlerFor.java         # Handler annotation
├── AnnotationInfo.java               # Annotation metadata
├── ProcessingContext.java            # Context configuration
├── ProcessingResult.java             # Processing results
├── ResultType.java                   # Result enum
├── Scanner.java                      # Class scanner
├── factory/
│   └── Factory.java                  # Handler factory
└── impl/
    ├── AnnotationProcessorImpl.java  # Default implementation
    └── AnnotationUtils.java          # Utility methods
```

## Integration Points

### Container Integration

Each container registers its annotation handlers:

```java
// EJB Container
processor.pushAnnotationHandler(new EJBAnnotationHandler());
processor.pushAnnotationHandler(new StatefulHandler());
processor.pushAnnotationHandler(new StatelessHandler());

// Web Container
processor.pushAnnotationHandler(new WebServletHandler());
processor.pushAnnotationHandler(new WebFilterHandler());
```

### Deployment Integration

Called during deployment scanning:

```
Deployment Scanner
       ↓
DOL (Deployment Object Library)
       ↓
AnnotationProcessor
       ↓
Container-specific handlers
```

## Skip List

Resource: `src/main/java/org/glassfish/apf/skip-annotation-class-list`

Lists annotations to skip during processing (optimization).

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `class-model` | Class file parsing |
| `glassfish-api` - Public APIs |
| `common-util` | Utilities |
| `internal-api` - Internal APIs |

## Related Modules

- `appserver/deployment/dol` - Deployment descriptors
- `appserver/ejb` - EJB annotation handlers
- `appserver/web` - Web annotation handlers
