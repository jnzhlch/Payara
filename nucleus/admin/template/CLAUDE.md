# CLAUDE.md - Admin Template

This file provides guidance for working with the Admin Template module - domain templates and default configuration.

## Module Overview

The Template module provides the default domain template used when creating new Payara domains. It contains the default `domain.xml`, security artifacts, logging configuration, and static resources.

## Template Structure

```
template/src/main/
├── assembly/
│   └── nucleus-domain.xml      # Assembly descriptor
├── resources/
│   ├── config/
│   │   ├── domain.xml          # Default domain configuration
│   │   ├── logging.properties  # Logging configuration
│   │   ├── keyfile             # User authentication
│   │   ├── keystore.p12        # SSL keystore
│   │   ├── cacerts.p12         # CA certificates
│   │   ├── login.conf          # JAAS login configuration
│   │   ├── server.policy       # Security policy
│   │   ├── javaee.server.policy
│   │   └── restrict.server.policy
│   └── docroot/                # Static web resources
│       ├── css/
│       ├── fonts/
│       └── images/
```

## Build Commands

```bash
# Build template module
mvn -DskipTests clean package -f nucleus/admin/template/pom.xml
```

## Domain Template

### Default Configuration (`domain.xml`)

The template provides a complete default domain configuration:

```xml
<domain log-root="${com.sun.aas.instanceRoot}/logs">
    <configs>
        <config name="server-config">
            <!-- Java configuration -->
            <java-config ...></java-config>

            <!-- HTTP service -->
            <http-service>
                <http-listener id="http-listener-1"
                    port="8080" .../>
                <http-listener id="http-listener-2"
                    port="8181" .../>
            </http-service>

            <!-- Security -->
            <security-service ...></security-service>

            <!-- Transaction service -->
            <transaction-service ...></transaction-service>

            <!-- And more... -->
        </config>
    </configs>

    <servers>
        <server name="server" config-ref="server-config">
            <resource-ref ref="jdbc/__TimerPool"/>
            <resource-ref ref="jdbc/__default"/>
        </server>
    </servers>
</domain>
```

## Configuration Files

### logging.properties

Default logging configuration:
- Console handler
- File handler (server.log)
- Log levels
- Formatter settings

### keyfile

User authentication file:
```
admin;{SSHA512}hashed_password;asadmin
```

### login.conf

JAAS login configuration:
- `adminLoginModule` - Admin authentication
- `clientLoginModule` - Client authentication
- `fileRealm` - File-based realm

### server.policy

Java security policy for the server.

## Customizing Templates

### Creating Custom Domain Template

1. Copy default template
2. Modify `domain.xml` with desired defaults
3. Update assembly descriptor
4. Package as custom template

### Template Tokens

Templates use `${token}` syntax for placeholders:

| Token | Purpose |
|-------|---------|
| `${com.sun.aas.instanceRoot}` | Domain root directory |
| `${com.sun.aas.installRoot}` | Installation root |
| `${com.sun.aas.javaRoot}` | Java home |

## Assembly Descriptor

`nucleus-domain.xml` defines how the template package is assembled:

```xml
<assembly>
    <id>nucleus-domain</id>
    <formats>
        <format>jar</format>
    </formats>
    <includeBaseDirectory>false</includeBaseDirectory>
    <fileSets>
        <!-- Configuration files -->
        <fileSet>
            <directory>src/main/resources/config</directory>
            <outputDirectory>config</outputDirectory>
        </fileSet>
        <!-- Static resources -->
        <fileSet>
            <directory>src/main/resources/docroot</directory>
            <outputDirectory>docroot</outputDirectory>
        </fileSet>
    </fileSets>
</assembly>
```

## Default Ports

| Service | Port |
|---------|------|
| Admin | 4848 |
| HTTP | 8080 |
| HTTPS | 8181 |
| JMS | 7676 |
| IIOP | 3700 |
| IIOP/SSL | 3820 |
| JMX | 8686 |

## Security Artifacts

### SSL Certificates

- **keystore.p12** - Server SSL certificate
- **cacerts.p12** - Trusted CA certificates
- Default password: `changeit` (should be changed)

### User Management

Default admin user:
- Username: `admin`
- Password: (empty, set on first use)

## Static Resources

The `docroot/` directory contains:
- CSS files for welcome page
- Fonts for branding
- Images for UI

These are served from `http://localhost:8080/`

## Domain Creation Process

When `create-domain` is executed:

1. Extract template jar to target location
2. Replace tokens with actual values
3. Generate unique IDs
4. Set file permissions
5. Initialize security

## Key Files Reference

| File | Purpose |
|------|---------|
| `domain.xml` | Main domain configuration |
| `logging.properties` | Logging setup |
| `keyfile` | Admin user credentials |
| `login.conf` | JAAS configuration |
| `server.policy` | Java security policy |
| `keystore.p12` | SSL certificate |
| `cacerts.p12` | CA certificates |

## Architecture Notes

1. **Packaged as JAR** - Template is distributed as JAR file
2. **Token replacement** - Tokens replaced at domain creation
3. **Immutable** - Template shouldn't be modified in place
4. **Customizable** - Can create custom templates for specific needs

## Modifying Default Configuration

### Changing Default Ports

Edit `domain.xml` template:

```xml
<http-listener id="http-listener-1"
    port="9090" .../>  <!-- Changed from 8080 -->
```

### Adding Default Resources

```xml
<resources>
    <jdbc-connection-pool name="my-default-pool" ...>
        <property name="url" value="jdbc:h2:mem:test"/>
    </jdbc-connection-pool>
    <jdbc-resource jndi-name="jdbc/myDS"
        pool-name="my-default-pool"/>
</resources>
```

### Custom JVM Options

```xml
<java-config>
    <jvm-options>
        <jvm-option>-Xmx512m</jvm-option>
        <jvm-option>-Dmy.property=default</jvm-option>
    </jvm-options>
</java-config>
```

## Payara-Specific Configuration

### Payara Branding

The template includes Payara-specific:
- Branding information
- Version properties
- Payara logo and CSS
- Payara default JVM options

### MicroProfile Defaults

Template enables:
- MicroProfile Config
- MicroProfile Health
- MicroProfile Metrics
- MicroProfile OpenAPI
