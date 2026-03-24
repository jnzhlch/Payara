# CLAUDE.md - Common Util

This file provides guidance for working with the `common-util` module - shared utilities and infrastructure.

## Module Overview

The `common-util` module provides foundational utilities used across all of Payara. These utilities handle caching, logging, I/O, process management, security, XML parsing, and more.

**Key categories:**
- Caching implementations (LRU, bounded, multi-key)
- Logging infrastructure and configuration
- I/O utilities (SmartFile, FileUtils)
- Process management (ProcessManager, command execution)
- XML parsing (MiniXmlParser for domain.xml)
- Security utilities (password handling, SSL)
- GlassFish system utilities

## Build Commands

```bash
# Build this module only
mvn -DskipTests clean package -f nucleus/common/common-util/pom.xml

# Build with tests
mvn clean package -f nucleus/common/common-util/pom.xml

# Run specific test
mvn test -Dtest=DurationTest -f nucleus/common/common-util/pom.xml
```

## Caching Utilities

### Cache Implementations

```java
// Simple LRU cache
LruCache<String, Object> cache = new LruCache<>(1000);
cache.put("key", value);
Object result = cache.get("key");

// Bounded multi-LRU cache (multiple keys)
BoundedMultiLruCache cache = new BoundedMultiLruCache(1000);
cache.put(key1, key2, value);

// Multi-LRU cache (unbounded)
MultiLruCache cache = new MultiLruCache();
```

### JMX Monitored Caches

All caches have JMX-monitored variants:

```java
// JmxLruCache - Exposes cache stats via JMX
JmxLruCache<String, Object> cache = new JmxLruCache<>("myCache", 1000);

// Register MBean
cache.registerMBean();

// Access via JConsole for cache statistics
```

**Cache JMX MBeans:**
- `JmxLruCache` - Single-key LRU stats
- `JmxBoundedMultiLruCache` - Multi-key bounded stats
- `JmxMultiLruCache` - Multi-key stats

## Logging Infrastructure

### LoggingConfig

```java
// Configure logging programmatically
LoggingConfig config = LoggingConfigFactory.createLoggingConfig();
config.updateLoggingProperties(properties);

// Get log levels
Map<String, String> levels = config.getLoggingProperties();

// Set log level
config.setLogLevel("javax.enterprise.system.ssl", "FINE");
```

### Log Files and Output

```java
// LoggingOutputStream - redirect System.out/err to logging
LoggingOutputStream los = new LoggingOutputStream(logger, Level.INFO);

// GFLogRecord - Custom LogRecord with additional fields
GFLogRecord record = new GFLogRecord(Level.INFO, "Message");
record.setSuppliedKey("LOG123");
```

## I/O Utilities

### SmartFile

```java
// SmartFile - File with enhanced path handling
SmartFile file = new SmartFile("/path/to/file");

// Path operations
SmartFile parent = file.getParent();
SmartFile child = file.getChild("subdir");

// Safe operations (handles nulls, relative paths)
SmartFile resolved = SmartFile.safeResolve(base, relative);
```

### FileUtils

```io
// File operations with proper error handling
FileUtils.deleteTree(directory);  // Recursive delete
FileUtils.copyFile(source, target);
FileUtils.mkdirs(directory);

// Server directories
ServerDirs dirs = new ServerDirs(new File("/path/to/domain"));
File appsDir = dirs.getApplicationsDir();  // applications/
File logsDir = dirs.getLogsDir();          // logs/
```

## Process Management

### ProcessManager

```java
// Execute external processes
ProcessManager pm = new ProcessManager("java", "-version");
pm.execute();
int exitCode = pm.getExitCode();
String output = pm.getOutput();

// With timeout (2 minutes)
ProcessManager pm = new ProcessManager(120000, "command", "arg1", "arg2");
pm.execute();

// Get streams
ProcessStreamDrainer stdout = pm.getStdOut();
ProcessStreamDrainer stderr = pm.getStdErr();
```

### LocalAdminCommand

```java
// Run asadmin commands programmatically
LocalAdminCommand cmd = new LocalAdminCommand("list-applications");
cmd.execute();
String output = cmd.getOutput();

// With arguments
cmd = new LocalAdminCommand("create-jdbc-connection-pool",
    "--datasourceclassname=org.apache.derby.jdbc.EmbeddedDataSource",
    "myPool");
cmd.execute();
```

### Process Utilities

```java
// Jps - find Java processes
List<Jps.ProcessInfo> processes = Jps.getJps();
for (Jps.ProcessInfo pi : processes) {
    System.out.println("PID: " + pi.pid + ", Class: " + pi.mainClass);
}

// Jstack - dump thread stacks
String stack = Jstack.getStackTrace(pid);
```

## XML Parsing

### MiniXmlParser

Lightweight XML parser for domain.xml (used during server bootstrap):

```java
// Parse domain.xml
MiniXmlParser parser = new MiniXmlParser(new File("domain.xml"));

// Get cluster info
List<ParsedCluster> clusters = parser.getClusters();

// Get system properties
Properties sysProps = parser.getSystemProperties();

// Get server info
String configName = parser.getConfigForServer("server");
```

### TokenResolver

Replace system property tokens in configuration:

```java
// Resolve ${...} tokens
TokenResolver resolver = new TokenResolver();
String resolved = resolver.resolve("${com.sun.aas.instanceRoot}/apps");

// With custom properties
Properties props = new Properties();
props.put("my.token", "value");
resolver.setTokenProperties(props);
```

## Security Utilities

### Password Handling

```java
// PasswordAdapter - encrypt/decrypt passwords
PasswordAdapter pwAdapter = new PasswordAdapter(true);  // true = encrypt
String encrypted = pwAdapter.encryptPassword("secret");
String decrypted = pwAdapter.decryptPassword(encrypted);

// PasswordAliasStore - store password aliases
PasswordAliasStore aliasStore = new DomainScopedPasswordAliasStore();
aliasStore.put("myAlias", "secret");
char[] password = aliasStore.get("myAlias");
```

### SSL/TLS

```java
// AsadminSecurityUtil - SSL setup for admin commands
AsadminSecurityUtil.setSSLProperties(trustStore, password);

// Truststore management
AsadminTruststore truststore = new AsadminTruststore();
truststore.setTrustStorePath("/path/to/cacerts.jks");
```

## GlassFish System Utilities

### ASenvPropertyReader

```java
// Read asenv.conf/asenv.bat
ASenvPropertyReader reader = new ASenvPropertyReader();
String instanceRoot = reader.get("AS_IMQ_LIB");  // Get env variable
```

### GFSystem / GFLauncherUtils

```java
// GlassFish system information
GFSystem gfSystem = new GFSystemImpl();
File installRoot = gfSystem.getInstallRoot();
File domainDir = gfSystem.getDomainRoot();

// Launch utilities
GFLauncherUtils utils = new GFLauncherUtils();
String javaExe = utils.resolveJavaExe();
```

## Utility Classes

### StringUtils

```java
// String manipulation
String camelCase = StringUtils.toCamelCase("my-property");
String kebabCase = StringUtils.toKebabCase("myProperty");

// Check empty
boolean isEmpty = StringUtils.isEmpty(str);
boolean isNotEmpty = StringUtils.isNotEmpty(str);
```

### BeanUtils

```java
// JavaBean manipulation
Object value = BeanUtils.getProperty(bean, "propertyName");
BeanUtils.setProperty(bean, "propertyName", value);

// Property descriptor
PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(bean.getClass(), "prop");
```

### NetUtils

```java
// Network utilities
boolean isLocalHost = NetUtils.isLocalHost("hostname");
boolean isPortAvailable = NetUtils.isPortFree(8080);

// Find free port
int port = NetUtils.getFreePort();
```

## Duration / Time Utilities

```java
// Duration - human-readable durations
Duration d = Duration.parse("30s");      // 30 seconds
Duration d2 = Duration.parse("1h30m");   // 1 hour 30 minutes
long millis = d.toMilliseconds();

// NanoDuration - nanosecond precision
NanoDuration nd = NanoDuration.parse("500ms");
long nanos = nd.toNanoseconds();
```

## Collections

### CollectionUtils

```java
// Collection utilities
List<String> list = CollectionUtils.toList(array);
Map<String, String> map = CollectionUtils.toMap(keyValuePairs);
```

### ManifestUtils

```java
// JAR manifest utilities
Attributes attrs = ManifestUtils.getMainAttributes(jarFile);
String mainClass = ManifestUtils.getMainClass(attrs);
```

## Testing

### Test Utilities

```java
// ServerDirs for testing
ServerDirs testDirs = ServerDirs.makeTestServerDirs();

// Clean up
testDirs.cleanUp();
```

## Architecture Notes

**Package structure:**
- `com.sun.enterprise.util.*` - General utilities
- `com.sun.enterprise.universal.*` - Universal utilities (process, I/O, XML)
- `com.sun.common.util.logging.*` - Logging infrastructure
- `org.glassfish.common.util.*` - GlassFish-specific utilities
- `com.sun.enterprise.admin.monitor.callflow.*` - Call flow monitoring
- `com.sun.enterprise.loader.*` - Class loading utilities

**Key patterns:**
- Static utility classes (no instantiation)
- Factory methods for creation
- Builder patterns for complex objects
- JMX MBeans for monitoring utilities

## Dependencies

**Runtime:**
- `glassfish-api` - Public APIs
- HK2 `hk2-core` - Dependency injection
- OSGi core/enterprise - Bundle support
- JLine - Optional console support

**Optional/Test:**
- Mockito - Testing

## Related Modules

- `glassfish-api` - Public APIs that use these utilities
- `internal-api` - Internal APIs using these utilities
- All other modules depend on common-util

## Common Patterns

### 1. Safe File Operations

```java
// Use SmartFile for null-safe path operations
SmartFile file = SmartFile.safeResolve(baseDir, "subdir/file.txt");
if (file != null && file.exists()) {
    // Process file
}
```

### 2. Execute System Commands

```java
ProcessManager pm = new ProcessManager("ls", "-la");
pm.execute();
if (pm.getExitCode() == 0) {
    System.out.println(pm.getOutput());
} else {
    System.err.println(pm.getError());
}
```

### 3. Parse domain.xml

```java
MiniXmlParser parser = new MiniXmlParser(
    new File("/opt/payara/glassfish/domains/domain1/config/domain.xml")
);

String serverConfig = parser.getConfigForServer("server");
List<String> servers = parser.getServersInConfig(serverConfig);
```

### 4. Use JMX-Monitored Cache

```java
// Create cache with JMX monitoring
JmxLruCache<String, Object> cache = new JmxLruCache<>("myCache", 10000);
cache.registerMBean();  // Registers MBean

// Now monitor via JConsole
// MBean: com.sun.appserv:type=LruCache,name=myCache
```
