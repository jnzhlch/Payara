# CLAUDE.md - Admin Util

This file provides guidance for working with the Admin Util module - shared utilities and helper classes for admin operations.

## Module Overview

The Util module provides common utilities used across admin modules including command model parsing, line token replacement, and various helper functions.

## Key Components

### Command Model Utilities

**Command metadata handling:**
- `CommandModel` - Command metadata
- `ParamModel` - Parameter metadata
- `CommandModelData` - Command model implementations
- `ParamModelData` - Parameter model implementations

### Token Replacement

**Configuration token processing:**
- `LineTokenReplacer` - Line-based token replacement
- `TokenValue` - Single token-value pair
- `TokenValueSet` - Collection of tokens

### String Utilities

**String manipulation:**
- `StringHelper` - String operations
- `GenericCommandModel` - Generic command model

## Build Commands

```bash
# Build util module
mvn -DskipTests clean package -f nucleus/admin/util/pom.xml
```

## Token Replacement

### Basic Token Replacement

```java
import com.sun.enterprise.admin.util.LineTokenReplacer;
import com.sun.enterprise.admin.util.TokenValueSet;

// Create token set
TokenValueSet tokens = new TokenValueSet();
tokens.add("SERVER_NAME", "my-server");
tokens.add("PORT", "8080");

// Replace tokens in string
LineTokenReplacer replacer = new LineTokenReplacer(tokens);
String result = replacer.replace("Server: ${SERVER_NAME}, Port: ${PORT}");
// Result: "Server: my-server, Port: 8080"
```

### Token Escaping

Escape token syntax with `$${` to produce literal `${`.

## Command Model

### Creating a Command Model

```java
import org.glassfish.common.util.admin.CommandModelImpl;
import org.glassfish.common.util.admin.MapInjectionResolver;

// Create command model
CommandModel model = new CommandModelImpl("my-command");

// Add parameters
model.addParameter(new ParamModelData(
    "name", String.class, true, null));

model.addParameter(new ParamModelData(
    "optional", String.class, false, "default-value"));
```

### Parameter Resolution

```java
// Map-based parameter resolution
Map<String, Object> params = new HashMap<>();
params.put("name", "value");
params.put("optional", "custom-value");

MapInjectionResolver resolver =
    new MapInjectionResolver(model, params);

// Resolve parameters
String name = resolver.getValue("name", String.class);
```

## Instance Registration

### Instance Registration Parameters

```java
import com.sun.enterprise.config.util.RegisterInstanceCommandParameters;

RegisterInstanceCommandParameters params =
    new RegisterInstanceCommandParameters();
params.setName("instance1");
params.setConfigName("default-config");
params.setNodeAgents("localhost-domain1");
params.setStandAlone(true);
```

## Name Generator

### Generate Unique Names

```java
import fish.payara.api.admin.config.NameGenerator;

// Generate system name
String name = NameGenerator.generateSystemName(
    "my-component", "my-cluster");

// Result: "my-component-my-cluster-<sequence>"
```

## Line Token Replacement

### Multi-line Processing

```java
String config = """
    server.name=${SERVER_NAME}
    server.port=${PORT}
    server.enabled=${ENABLED}
    """;

LineTokenReplacer replacer = new LineTokenReplacer(tokens);
String processed = replacer.replace(config);
```

## Property Expansion

### System Property Expansion

```java
import com.sun.enterprise.admin.util.PropertyExpander;

// Expand system properties
String result = PropertyExpander.expand(
    "${user.home}/payara");
// Result: "/Users/username/payara"
```

## String Helper

### String Operations

```java
import com.sun.enterprise.admin.util.StringHelper;

// Quote string if needed
String quoted = StringHelper.quote("value with spaces");

// Join strings
String joined = StringHelper.join(",", list);

// Split and trim
String[] parts = StringHelper.split("a, b, c", ",");
```

## Key Utilities

| Class | Purpose |
|-------|---------|
| `LineTokenReplacer` | Token replacement in strings |
| `TokenValueSet` | Collection of token-value pairs |
| `CommandModelImpl` | Command model implementation |
| `ParamModelData` | Parameter model implementation |
| `MapInjectionResolver` | Map-based parameter resolution |
| `NameGenerator` | Generate unique names |
| `PropertyExpander` | Expand system properties |
| `StringHelper` | String utility methods |

## Common Patterns

### Token Replacement in Configuration

```java
// Read template
String template = readTemplate("config.template");

// Set up tokens
TokenValueSet tokens = new TokenValueSet();
tokens.add("DOMAIN_NAME", domain.getName());
tokens.add("ADMIN_PORT", domain.getAdminPort());
tokens.add("INSTANCE_PORT", domain.getInstancePort());

// Replace tokens
LineTokenReplacer replacer = new LineTokenReplacer(tokens);
String config = replacer.replace(template);

// Write result
writeConfig(config);
```

### Command Parameter Processing

```java
// Get command model
CommandModel model = command.getCommandModel();

// Get parameters from user
Map<String, Object> userParams = getUserParameters();

// Create resolver
MapInjectionResolver resolver =
    new MapInjectionResolver(model, userParams);

// Inject into command
command.inject(resolver);
```

## Architecture Notes

1. **Shared utilities** - Used by multiple admin modules
2. **Stateless** - Most util classes are stateless
3. **No dependencies** - Minimal dependencies on other modules
4. **Well-tested** - Extensive test coverage

## Testing Utilities

```bash
# Run util tests
mvn test -f nucleus/admin/util/pom.xml
```
