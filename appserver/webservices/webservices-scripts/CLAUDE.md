# CLAUDE.md - Web Services Scripts

This file provides guidance for working with the `appserver/webservices/webservices-scripts` module - Command-line tools for JAX-WS and JAXB development.

## Module Overview

The webservices-scripts module provides command-line scripts for web services development. These scripts are packaged into the Payara distribution's `bin/` directory, providing developers with tools for:
- JAX-WS client generation (wsimport)
- JAX-WS service generation (wsgen)
- JAX-RPC compilation (wscompile, wsdeploy)
- JAXB schema processing (xjc, schemagen)

**Purpose:** Developer tools for SOAP web services and XML binding.

## Build Commands

```bash
# Build webservices-scripts module
mvn -DskipTests clean package -f appserver/webservices/webservices-scripts/pom.xml
```

## Distribution Fragment Packaging

```
webservices-scripts.jar
       │
       ▼ [Extracted during distribution assembly]
glassfish/bin/
       │
       ├── wsimport
       ├── wsgen
       ├── wscompile
       ├── wsdeploy
       ├── schemagen
       ├── xjc
       └── jdkcheck
```

## Available Scripts

### wsimport - JAX-WS Client Generator

Generate JAX-WS client artifacts from a WSDL file:

```bash
# Basic usage
wsimport -d src -p com.example.client http://example.com/ws/UserService?wsdl

# From local WSDL file
wsimport -d src -p com.example.client UserService.wsdl

# With additional options
wsimport -d src \
         -p com.example.client \
         -keep \
         -Xnocompile \
         http://example.com/ws/UserService?wsdl

# Generate from WSDL with binding customization
wsimport -d src \
         -b binding.xjb \
         -p com.example.client \
         UserService.wsdl
```

#### wsimport Options

| Option | Description |
|--------|-------------|
| `-d <directory>` | Output directory for generated files |
| `-p <package>` | Target package |
| `-keep` | Keep generated Java files |
| `-Xnocompile` | Don't compile generated Java files |
| `-b <binding>` | External JAX-WS or JAXB binding file |
| `-catalog` | Catalog file to resolve external entity references |
| `-extension` | Allow vendor extensions |
| `-target` | JAX-WS spec version (2.0, 2.1, 2.2) |

#### Generated Artifacts

```
com/example/client/
├── UserService.java           # Service interface
├── UserServiceService.java    # Service factory
├── User.java                  # JAXB types
├── ObjectFactory.java          # JAXB factory
└── package-info.java           # Package info
```

### wsgen - JAX-WS Service Generator

Generate WSDL and artifacts from a JAX-WS service class:

```bash
# Generate from service class
wsgen -d src -cp classes com.example.UserService

# Generate WSDL only
wsgen -d src -cp classes -wsdl com.example.UserService

# Generate with specific service name
wsgen -d src -cp classes \
      -servicename UserServiceService \
      -portname UserServicePort \
      com.example.UserService

# Keep generated source
wsgen -d src -cp classes -keep com.example.UserService
```

#### wsgen Options

| Option | Description |
|--------|-------------|
| `-d <directory>` | Output directory |
| `-cp <classpath>` | Classpath for service class |
| `-wsdl` | Generate WSDL file |
| `-keep` | Keep generated Java files |
| `-r <directory>` | Resource directory for WSDL |
| `-servicename` | Specify service name |
| `-portname` | Specify port name |
| `-xjc` | Pass options to XJC (for schema generation) |

### xjc - JAXB Schema Compiler

Generate Java classes from XML Schema:

```bash
# Generate from XSD
xjc -d src -p com.example.schema schema.xsd

# Generate with binding customization
xjc -d src -p com.example.schema -b binding.xjb schema.xsd

# Generate from multiple schemas
xjc -d src schema1.xsd schema2.xsd

# Generate with episode file for reuse
xjc -d src -episode episode.xjb schema.xsd
```

#### xjc Options

| Option | Description |
|--------|-------------|
| `-d <directory>` | Output directory |
| `-p <package>` | Target package |
| `-b <binding>` | Binding customization file |
| `-nv` | Do not perform strict validation |
| `-extension` | Allow vendor extensions |
| `-episode <file>` | Generate episode file for reuse |
| `-xmlschema` | Treat input as W3C XML Schema (default) |

#### Generated Classes

```
com/example/schema/
├── User.java                 # JAXB type
├── ObjectFactory.java         # Factory
└── package-info.java          # Package namespace
```

### schemagen - JAXB Schema Generator

Generate XML Schema from Java classes:

```bash
# Generate schema from classes
schemagen -d src com.example.User com.example.Address

# Generate to specific directory
schemagen -d src -cp classes com.example.*

# Generate with specific namespace
schemagen -d src com.example.User
```

#### schemagen Options

| Option | Description |
|--------|-------------|
| `-d <directory>` | Output directory |
| `-cp <classpath>` | Classpath for Java classes |
| `-episode <file>` | Generate episode file |

### wscompile - JAX-RPC Compiler

Legacy JAX-RPC compilation tool:

```bash
# Compile JAX-RPC service
wscompile -define -d src -mapping mapping.xml config.xml

# Generate from WSDL
wscompile -gen:server -d src config.xml
```

### wsdeploy - JAX-RPC Deployer

Legacy JAX-RPC deployment tool:

```bash
# Deploy JAX-RPC service
wsdeploy -o output.war input.war
```

## Usage Examples

### Creating a JAX-WS Client

```bash
# 1. Generate client from WSDL
wsimport -d src -p com.example.client \
    http://example.com/ws/UserService?wsdl

# 2. Compile generated classes
cd src
javac com/example/client/*.java

# 3. Use in your application
```

```java
// Use generated client
UserServiceService service = new UserServiceService();
UserService port = service.getUserServicePort();

String result = port.getUser("123");
```

### Creating a JAX-WS Service

```bash
# 1. Create service class
# UserService.java

# 2. Generate artifacts
wsgen -d src -cp classes com.example.UserService

# 3. Deploy WAR containing generated artifacts
```

### JAXB XML Binding

```bash
# 1. Generate from schema
xjc -d src -p com.example.model schema.xsd

# 2. Compile
cd src
javac com/example/model/*.java

# 3. Use in application
```

```java
// Unmarshal XML
JAXBContext jc = JAXBContext.newInstance("com.example.model");
Unmarshaller u = jc.createUnmarshaller();
User user = (User) u.unmarshal(new File("user.xml"));

# Marshal to XML
Marshaller m = jc.createMarshaller();
m.marshal(user, System.out);
```

## Script Location

After installation:
```
$PAYARA_HOME/bin/
├── wsimport / wsimport.bat
├── wsgen / wsgen.bat
├── wscompile / wscompile.bat
├── wsdeploy / wsdeploy.bat
├── schemagen / schemagen.bat
├── xjc / xjc.bat
└── jdkcheck / jdkcheck.bat
```

## Classpath Configuration

Scripts automatically configure classpath with:
- `webservices-osgi.jar` - JAX-WS implementation
- `jaxb-osgi.jar` - JAXB implementation
- `jakarta.xml.bind-api.jar` - Jakarta XML Binding API
- `jakarta.xml.ws-api.jar` - Jakarta XML Web Services API
- `jakarta.jws-api.jar` - Jakarta JWS API
- `webservices-api-osgi.jar` - Web Services API
- `jakarta.activation-api.jar` - Jakarta Activation API
- `angus-activation.jar` - Activation implementation

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `webservices-osgi` | JAX-WS tools implementation |
| `jaxb-osgi` | JAXB tools implementation |

## Notes

- **Distribution Fragment** - Scripts packaged into distribution
- **Developer Tools** - For development-time use only
- **JAX-WS 2.x** - Current web services standard
- **JAX-RPC** - Legacy support (deprecated)
- **JAXB 4.x** - Jakarta XML Binding
