# CLAUDE.md - Web GUI Plugin Common

This file provides guidance for working with the `appserver/web/gui-plugin-common` module - Common admin GUI commands for web module configuration.

## Module Overview

The gui-plugin-common module provides asadmin commands used by the admin console (GUI) to configure web module parameters at runtime. These commands allow dynamic modification of context parameters and environment entries.

**Key Components:**
- **WebContextParamCommand** - Context parameter commands
- **WebEnvEntryCommand** - Environment entry commands

## Build Commands

```bash
# Build gui-plugin-common module
mvn -DskipTests clean package -f appserver/web/gui-plugin-common/pom.xml
```

## CLI Commands

### Context Parameters

Context parameters are initialization parameters for a web application, equivalent to `<context-param>` in web.xml:

#### Set Context Parameter

```bash
asadmin set-web-context-param --contextparam_name=apiUrl \
    --contextparam_value=https://api.example.com \
    myApp
```

#### List Context Parameters

```bash
asadmin list-web-context-params myApp
```

#### Unset Context Parameter

```bash
asadmin unset-web-context-param --contextparam_name=apiUrl myApp
```

### Environment Entries

Environment entries are environment-dependent resources looked up via JNDI:

#### Set Environment Entry

```bash
asadmin set-web-env-entry --env_entry_name=maxConnections \
    --env_entry_type=java.lang.Integer \
    --env_entry_value=100 \
    myApp
```

#### List Environment Entries

```bash
asadmin list-web-env-entries myApp
```

#### Unset Environment Entry

```bash
asadmin unset-web-env-entry --env_entry_name=maxConnections myApp
```

## Command Implementation

### WebContextParamCommand

```java
@Service
@Scoped(PerLookup.class)
public abstract class WebContextParamCommand extends WebModuleConfigCommand {

    @Param(name = "contextparam_name", alias = "name")
    protected String paramName;

    @Param(name = "contextparam_value", alias = "value")
    protected String paramValue;

    @Override
    protected void executeCommand(WebModuleConfig config) {
        // Get or create context params
        List<ContextParam> params = config.getContextParam();

        // Check if param exists
        for (ContextParam param : params) {
            if (param.getName().equals(paramName)) {
                // Update existing
                param.setValue(paramValue);
                return;
            }
        }

        // Add new param
        ContextParam newParam = new ContextParam();
        newParam.setName(paramName);
        newParam.setValue(paramValue);
        params.add(newParam);
    }
}
```

### WebEnvEntryCommand

```java
@Service
@Scoped(PerLookup.class)
public abstract class WebEnvEntryCommand extends WebModuleConfigCommand {

    @Param(name = "env_entry_name", alias = "name")
    protected String entryName;

    @Param(name = "env_entry_type", alias = "type")
    protected String entryType;

    @Param(name = "env_entry_value", alias = "value")
    protected String entryValue;

    @Override
    protected void executeCommand(WebModuleConfig config) {
        // Get or create env entries
        List<EnvEntry> entries = config.getEnvEntry();

        // Check if entry exists
        for (EnvEntry entry : entries) {
            if (entry.getName().equals(entryName)) {
                // Update existing
                entry.setType(entryType);
                entry.setValue(entryValue);
                return;
            }
        }

        // Add new entry
        EnvEntry newEntry = new EnvEntry();
        newEntry.setName(entryName);
        newEntry.setType(entryType);
        newEntry.setValue(entryValue);
        entries.add(newEntry);
    }
}
```

## Configuration Structure

### domain.xml

```xml
<applications>
    <application name="myApp">
        <module>
            <web-module context-root="/myApp">
                <web-module-config>
                    <!-- Context Parameters -->
                    <context-param>
                        <param-name>apiUrl</param-name>
                        <param-value>https://api.example.com</param-value>
                    </context-param>
                    <context-param>
                        <param-name>theme</param-name>
                        <param-value>dark</param-value>
                    </context-param>

                    <!-- Environment Entries -->
                    <env-entry>
                        <env-entry-name>maxConnections</env-entry-name>
                        <env-entry-type>java.lang.Integer</env-entry-type>
                        <env-entry-value>100</env-entry-value>
                    </env-entry>
                    <env-entry>
                        <env-entry-name>timeout</env-entry-name>
                        <env-entry-type>java.lang.Integer</env-entry-type>
                        <env-entry-value>30</env-entry-value>
                    </env-entry>
                </web-module-config>
            </web-module>
        </module>
    </application>
</applications>
```

## Programmatic Access

### Accessing Context Parameters

```java
@WebServlet("/config")
public class ConfigServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        ServletContext context = getServletContext();

        String apiUrl = context.getInitParameter("apiUrl");
        String theme = context.getInitParameter("theme");

        resp.setContentType("application/json");
        resp.getWriter().write("{\"apiUrl\":\"" + apiUrl + "\",\"theme\":\"" + theme + "\"}");
    }
}
```

### Accessing Environment Entries

```java
@WebServlet("/env")
public class EnvServlet extends HttpServlet {

    @Resource(lookup = "maxConnections")
    private Integer maxConnections;

    @Resource(lookup = "timeout")
    private Integer timeout;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        // Or via JNDI lookup
        InitialContext ic = new InitialContext();
        Integer maxConnections = (Integer) ic.lookup("java:comp/env/maxConnections");

        resp.setContentType("application/json");
        resp.getWriter().write("{\"maxConnections\":" + maxConnections + "}");
    }
}
```

## Usage Examples

### Dynamic API URL Configuration

```bash
# Development
asadmin set-web-context-param --contextparam_name=apiUrl \
    --contextparam_value=http://localhost:8080/api devApp

# Staging
asadmin set-web-context-param --contextparam_name=apiUrl \
    --contextparam_value=https://staging-api.example.com stagingApp

# Production
asadmin set-web-context-param --contextparam_name=apiUrl \
    --contextparam_value=https://api.example.com prodApp
```

### Feature Toggles

```bash
# Enable new feature
asadmin set-web-context-param --contextparam_name=feature.new.enabled \
    --contextparam_value=true myApp

# Disable feature
asadmin set-web-context-param --contextparam_name=feature.new.enabled \
    --contextparam_value=false myApp
```

### Resource Limits

```bash
# Set connection pool size
asadmin set-web-env-entry --env_entry_name=maxConnections \
    --env_entry_type=java.lang.Integer \
    --env_entry_value=200 myApp

# Set timeout
asadmin set-web-env-entry --env_entry_name=requestTimeout \
    --env_entry_type=java.lang.Integer \
    --env_entry_value=60 myApp
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `glassfish-api` | GlassFish internal APIs |
| `config-api` | Configuration API |
| `admin-cli` | Admin CLI framework |

## Notes

- **Runtime Configuration** - Changes without redeployment
- **GUI Integration** - Used by admin console
- **Persistent** - Changes saved to domain.xml
- **Application Scope** - Parameters are per-application
