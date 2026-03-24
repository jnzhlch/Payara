# CLAUDE.md - Logging

This file provides guidance for working with the `logging` module - Payara's logging infrastructure.

## Module Overview

The `logging` module provides Payara's logging infrastructure based on `java.util.logging` (JUL). It handles:

- Log formatters (Uniform, ODL, JSON, ANSI)
- File and console handlers
- Log rotation
- Runtime log level changes
- Log file parsing (log viewer backend)
- Syslog support

## Build Commands

```bash
# Build logging module
mvn -DskipTests clean package -f nucleus/core/logging/pom.xml

# Build with tests
mvn clean package -f nucleus/core/logging/pom.xml
```

## Architecture

### Logging Stack

```
Application logs
    │
    ├─→ java.util.logging (JUL)
    │
    ├─→ LogManagerService (HK2 service)
    │      ├─→ GFFileHandler (file output)
    │      ├─→ ConsoleHandler (console output)
    │      └─→ SyslogHandler (syslog output)
    │
    ├─→ Formatter
    │      ├─→ UniformLogFormatter
    │      ├─→ ODLLogFormatter
    │      ├─→ JSONLogFormatter
    │      └─→ AnsiColorFormatter
    │
    └─→ Log files
           ├─→ server.log
           ├─── access.log
           └─── server.log_XXXX (rotated)
```

## Configuration

### logging.properties

Located in `domains/domain1/config/logging.properties`:

```properties
# Global log level
.level=INFO

# Handler configuration
handlers=java.util.logging.ConsoleHandler, com.sun.enterprise.server.logging.GFFileHandler

# File handler
com.sun.enterprise.server.logging.GFFileHandler.level=ALL
com.sun.enterprise.server.logging.GFFileHandler.formatter=com.sun.enterprise.server.logging.UniformLogFormatter
com.sun.enterprise.server.logging.GFFileHandler.file=${com.sun.aas.instanceRoot}/logs/server.log
com.sun.enterprise.server.logging.GFFileHandler.rotationLimitInBytes=2000000
com.sun.enterprise.server.logging.GFFileHandler.flushFrequency=1
com.sun.enterprise.server.logging.GFFileHandler.logtoConsole=true
com.sun.enterprise.server.logging.GFFileHandler.rotationTimelimitInMinutes=0

# Specific loggers
javax.enterprise.system.container.level=INFO
javax.enterprise.system.core.level=INFO
org.glassfish.level=INFO
```

### Runtime Configuration

```java
// LogManagerService
LogManagerService logManager = habitat.getService(LogManagerService.class);

// Set log level
logManager.setLoggerLevel("org.glassfish.kernel", "FINE");

// Get log level
String level = logManager.getLoggerLevel("org.glassfish.kernel");
```

## Log Formatters

### UniformLogFormatter (Default)

```
[2025-03-24T10:30:45.123+0000] [INFO] [] [javax.enterprise.system] [tid: _ThreadID=1 _ThreadName=main] [timeMillis: 1711273845123] [levelValue: 800] [[
  Server startup done in 10,234 ms]]
```

### ODLLogFormatter (Oracle Diagnostic Logging)

```
[2025-03-24T10:30:45.123+00:00] [INFO] [AS-INIT-00100] [javax.enterprise.system.core] [tid: _ThreadID=1 _ThreadName=main] [timeMillis: 1711273845123] [levelValue: 800] [CLASSNAME: com.sun.enterprise.v3.server.AppServerStartup] [METHOD: startup] [[
  Server startup done]]
```

### JSONLogFormatter

```json
{
  "timestamp": "2025-03-24T10:30:45.123+0000",
  "level": "INFO",
  "loggerName": "javax.enterprise.system",
  "threadId": 1,
  "threadName": "main",
  "message": "Server startup done",
  "levelValue": 800
}
```

### AnsiColorFormatter

Colored console output:
- INFO: Blue
- WARNING: Yellow
- SEVERE: Red

## File Handling

### GFFileHandler

```java
// File handler features
GFFileHandler handler = new GFFileHandler();

// Rotation by size
handler.setRotationLimitInBytes(2000000); // 2MB

// Rotation by time
handler.setRotationTimeLimitInMinutes(1440); // 24 hours

// Console echo
handler.setLogToConsole(true);

// Flush frequency
handler.setFlushFrequency(1); // Flush every record
```

### Log Rotation

```bash
# Manual rotation via asadmin
asadmin rotate-log

# Or programmatically
LogManagerService logManager = habitat.getService(LogManagerService.class);
logManager.rotateLog();
```

### Rotation Timer

```java
// Automatic rotation
LogRotationTimer timer = new LogRotationTimer();
timer.startTimer(); // Starts background rotation thread
```

## Admin Commands

### Log Level Management

```bash
# List loggers
asadmin list-loggers

# List logger levels
asadmin list-logger-levels

# Set log level
asadmin set-log-level javax.enterprise.system.core=FINE

# Delete log level (reverts to default)
asadmin delete-log-level javax.enterprise.system.core
```

### Log Attributes

```bash
# List log attributes
asadmin list-log-attributes

# Set log attributes
asadmin set-log-attributes rotationLimitInBytes=5000000

# Set log format
asadmin set-log-file-format json

# Load defaults
asadmin load-default-log-attributes
asadmin load-default-log-levels
```

### Log Operations

```bash
# Collect log files
asadmin collect-log-files

# Get log file for instance
asadmin get-log-file --target server-instance

# Rotate log
asadmin rotate-log
```

## Log Parsing

### Log Parser API

```java
// Parse log files
LogParser parser = LogParserFactory.createParser(logFile);

parser.parse(new LogParserListener() {
    @Override
    public void onLogEntry(ParsedLogRecord record) {
        String timestamp = record.getTimestamp();
        String level = record.getLevel();
        String message = record.getMessage();
        String loggerName = record.getLoggerName();

        // Process log entry
    }

    @Override
    public void onException(Exception e) {
        // Handle parse errors
    }
});
```

### Parser Types

| Parser | Description |
|--------|-------------|
| `UniformLogParser` | Parse Uniform log format |
| `ODLLogParser` | Parse Oracle Diagnostic Logging format |
| `RawLogParser` | Parse raw/unknown format |

### Log Filter

```java
// Filter log records
LogFilter filter = new LogFilter();
filter.setStartDate(startDate);
filter.setEndDate(endDate);
filter.setLogLevel(Level.SEVERE);

// Apply filter
List<ParsedLogRecord> records = parser.parse(filter);
```

## Logging in Code

### Standard JUL

```java
import java.util.logging.Logger;

public class MyClass {
    private static final Logger LOG = Logger.getLogger(MyClass.class.getName());

    public void doSomething() {
        LOG.info("Doing something");
        LOG.log(Level.FINE, "Detailed debug info: {0}", detail);
        LOG.log(Level.WARNING, "Warning: {0}", warning);
        LOG.log(Level.SEVERE, "Error occurred", exception);
    }
}
```

### Log Facade

```java
// LogFacade for convenience
import com.sun.enterprise.server.logging.LogFacade;

public class MyClass {
    private static final Logger LOG = LogFacade.getLogger(MyClass.class);

    // Or using @LogMessageInfo annotations
    @LogMessageInfo(message = "Server {0} started in {1} ms")
    private static final String SERVER_STARTED = "AS-STARTED-00001";

    public void logStartup(String server, long ms) {
        LOG.log(Level.INFO, SERVER_STARTED, server, ms);
    }
}
```

## Syslog Handler

### Syslog Configuration

```java
// SyslogHandler
SyslogHandler handler = new SyslogHandler();
handler.setHost("syslog.example.com");
handler.setPort(514);
handler.setFacility(Syslog.Facility.LOCAL0);

// Register handler
Logger logger = Logger.getLogger("");
logger.addHandler(handler);
```

## Payara Extensions

### JSON Log Formatter

```bash
# Enable JSON format
asadmin set-log-file-format json
```

### Notification File Handler

```java
// PayaraNotificationFileHandler
// Sends notifications to Payara notification system
```

### Custom Handlers

```java
// Create custom handler
@Service
public class MyLogHandler extends Handler {

    @Override
    public void publish(LogRecord record) {
        // Custom log handling
    }

    @Override
    public void flush() {
        // Flush buffers
    }

    @Override
    public void close() throws SecurityException {
        // Cleanup
    }
}
```

## Log Levels

| Level | Value | Usage |
|-------|-------|-------|
| SEVERE | 1000 | Errors |
| WARNING | 900 | Warnings |
| INFO | 800 | Informational |
| CONFIG | 700 | Configuration |
| FINE | 500 | Debug (basic) |
| FINER | 400 | Debug (detailed) |
| FINEST | 300 | Debug (verbose) |

## Best Practices

### 1. Use Appropriate Log Levels

```java
LOG.severe("System critical error: " + error);  // Errors requiring attention
LOG.warning("Configuration issue: " + issue);    // Potential problems
LOG.info("Server started on port " + port);      // Normal operations
LOG.fine("Processing request: " + request);      // Debug information
```

### 2. Parameterized Messages

```java
// Good - parameterized
LOG.log(Level.INFO, "Processing {0} for user {1}", action, user);

// Avoid - string concatenation (even when not logged)
LOG.info("Processing " + action + " for user " + user);
```

### 3. Exception Logging

```java
// Include exception as last parameter
LOG.log(Level.SEVERE, "Failed to process request", exception);

// For exception with message
LOG.log(Level.WARNING, "Timeout waiting for {0}", resource, exception);
```

### 4. Logger Naming

```java
// Use class name
private static final Logger LOG = Logger.getLogger(MyClass.class.getName());

// Or package name
private static final Logger LOG = Logger.getLogger("com.example.myapp");
```

## Related Modules

- **kernel** - Kernel services that use logging
- **admin/cli** - CLI log commands
- **common-util** - Logging utilities

## Common Patterns

### 1. Component-Specific Logger

```java
public class MyComponent {
    private static final String LOG_KEY = "javax.enterprise.system.mycomponent";
    private static final Logger LOG = Logger.getLogger(LOG_KEY);
}
```

### 2. Conditional Logging

```java
if (LOG.isLoggable(Level.FINE)) {
    // Expensive operation only logged if FINE enabled
    LOG.log(Level.FINE, "Expensive debug info: {0}", computeExpensiveDebugInfo());
}
```

### 3. Runtime Log Level Change

```java
// Change log level at runtime
LogManagerService logMgr = habitat.getService(LogManagerService.class);
logMgr.setLoggerLevel("org.glassfish.kernel", "FINE");
```

### 4. Custom Formatter

```java
@Service
public class MyFormatter extends Formatter {

    @Override
    public String format(LogRecord record) {
        return String.format("[%s] %s: %s%n",
            record.getLevel(),
            record.getLoggerName(),
            record.getMessage());
    }
}
```
