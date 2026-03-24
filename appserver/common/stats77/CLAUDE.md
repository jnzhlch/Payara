# CLAUDE.md - Stats77

This file provides guidance for working with the `stats77` module - JSR 77 statistics support.

## Module Overview

The stats77 module provides the statistics model for JSR 77 (J2EE Management), supporting time-based, count-based, and range-based statistics for Java EE components.

## Build Commands

```bash
# Build stats77
mvn -DskipTests clean package -f appserver/common/stats77/pom.xml
```

## Statistics Model

### Statistic Hierarchy

```
Statistic (base interface)
    ├── CountStatistic
    ├── BoundaryStatistic
    ├── RangeStatistic (extends BoundaryStatistic)
    └── TimeStatistic (extends BoundaryStatistic)
```

### Statistic Types

| Type | Description | Use Case |
|------|-------------|----------|
| `CountStatistic` | Incrementing counter | Request count, error count |
| `BoundaryStatistic` | Single boundary value | Threshold tracking |
| `RangeStatistic` | Min/max/current | Pool size, queue length |
| `TimeStatistic` - Timing measurements | Response time, execution time |

## Core Interfaces

### Statistic

Base interface for all statistics:

```java
public interface Statistic {
    String getName();              // Statistic name
    String getDescription();       // Description
    String getUnit();              // Unit of measurement
    long getStartTime();           // When collection started
    long getLastSampleTime();      // Last sample time
}
```

### CountStatistic

Monotonically increasing counter:

```java
public interface CountStatistic extends Statistic {
    long getCount();               // Current count
    void setCount(long count);      // Update count
    void increment();               // Increment by 1
    void increment(long delta);     // Increment by delta
}
```

**Example Use:**
- Number of requests processed
- Number of errors encountered
- Number of active sessions

### BoundaryStatistic

Single boundary value:

```java
public interface BoundaryStatistic extends Statistic {
    long getLowerBound();           // Minimum value
    long getUpperBound();           // Maximum value
}
```

### RangeStatistic

Min/max/current values:

```java
public interface RangeStatistic extends BoundaryStatistic {
    long getCurrent();              // Current value
    long getHighWaterMark();        // Maximum observed
    long getLowWaterMark();         // Minimum observed
}
```

**Example Use:**
- Database pool size
- Queue length
- Thread pool size
- Memory usage

### TimeStatistic

Timing measurements:

```java
public interface TimeStatistic extends BoundaryStatistic {
    long getCount();                // Number of measurements
    long getMaxTime();              // Maximum time
    long getMinTime();              // Minimum time
    long getTotalTime();            // Cumulative time
}
```

**Derived Values:**
```java
double averageTime = (double)statistic.getTotalTime() / statistic.getCount();
```

**Example Use:**
- Servlet response time
- EJB method execution time
- Database query time

## Implementation

### StatisticImpl

Abstract base implementation:

```java
public abstract class StatisticImpl implements Statistic, Serializable {
    protected String name;
    protected String description;
    protected String unit;
    protected long startTime;
    protected long lastSampleTime;
}
```

### CountStatisticImpl

```java
public class CountStatisticImpl extends StatisticImpl implements CountStatistic {
    private long count;

    @Override
    public void increment() {
        this.count++;
        this.lastSampleTime = System.currentTimeMillis();
    }
}
```

### RangeStatisticImpl

```java
public class RangeStatisticImpl extends StatisticImpl implements RangeStatistic {
    private long current;
    private long lowWaterMark;
    private long highWaterMark;

    public void setCurrent(long current) {
        this.current = current;
        if (current > highWaterMark) highWaterMark = current;
        if (current < lowWaterMark) lowWaterMark = current;
    }
}
```

### TimeStatisticImpl

```java
public class TimeStatisticImpl extends StatisticImpl implements TimeStatistic {
    private long count;
    private long maxTime;
    private long minTime;
    private long totalTime;

    public void recordTime(long time) {
        count++;
        totalTime += time;
        if (time > maxTime) maxTime = time;
        if (time < minTime || count == 1) minTime = time;
    }
}
```

## Usage Pattern

### Creating Statistics

```java
// Create a statistic
CountStatistic requestCount = new CountStatisticImpl(
    "requestCount",
    "Number of requests processed",
    "count"
);

// Create time statistic
TimeStatistic responseTime = new TimeStatisticImpl(
    "responseTime",
    "Response time in milliseconds",
    "millisecond"
);
```

### Recording Data

```java
// Counter
requestCount.increment();
// or
requestCount.increment(5);

// Time measurement
long start = System.nanoTime();
// ... do work ...
long duration = System.nanoTime() - start;
responseTime.recordTime(duration);

// Range value
poolSizeStat.setCurrent(currentPoolSize);
```

### Resetting Statistics

```java
// Reset to initial state
statistic.reset();
```

## Integration with AMX

### JSR 77 Management

Statistics are exposed through AMX MBeans:

```java
@AMXMBean
public interface ServletStats {
    TimeStatistic getServiceTime();
    CountStatistic getInvocationCount();
    RangeStatistic getActiveSessions();
}
```

## Package Structure

```
org.glassfish.statistics/
├── Statistic.java                  # Base interface
├── CountStatistic.java
├── BoundaryStatistic.java
├── RangeStatistic.java
└── TimeStatistic.java

org.glassfish.statistics.impl/
├── StatisticImpl.java              # Base implementation
├── CountStatisticImpl.java
├── BoundaryStatisticImpl.java
├── RangeStatisticImpl.java
└── TimeStatisticImpl.java
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `internal-api` | Internal APIs |

## Related Modules

- `amx-javaee` - JSR 77 management (exposes statistics)
- `monitoring` - Payara monitoring service
- All containers (Web, EJB) - Collect component statistics
