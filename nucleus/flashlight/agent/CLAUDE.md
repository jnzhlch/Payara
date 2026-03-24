# CLAUDE.md - Flashlight Agent

This file provides guidance for working with the `flashlight-agent` module - Java agent for bytecode instrumentation.

## Module Overview

The `flashlight-agent` module provides a Java agent (`Premain-Class` and `Agent-Class`) for runtime bytecode instrumentation. This enables:

- Dynamic bytecode transformation
- Probe method injection
- Class redefinition and retransformation

## Build Commands

```bash
# Build flashlight-agent module
mvn -DskipTests clean package -f nucleus/flashlight/agent/pom.xml

# Build with tests
mvn clean package -f nucleus/flashlight/agent/pom.xml
```

## Java Agent Configuration

### MANIFEST.MF Entries

```
Premain-Class: org.glassfish.flashlight.agent.ProbeAgentMain
Agent-Class: org.glassfish.flashlight.agent.ProbeAgentMain
Can-Redefine-Classes: true
Can-Retransform-Classes: true
```

### Using the Agent

```bash
# Add to JVM startup
java -javaagent:flashlight-agent.jar -jar myapp.jar

# Or with Payara
asadmin create-jvm-options \
    "-javaagent:${com.sun.aas.installRoot}/lib/flashlight-agent.jar"
```

## Agent Main Class

### ProbeAgentMain

```java
// Entry point for Java agent
public class ProbeAgentMain {

    // Premain-Class: invoked before main()
    public static void premain(String agentArgs, Instrumentation inst) {
        // Agent initialization
    }

    // Agent-Class: invoked after JVM startup
    public static void agentmain(String agentArgs, Instrumentation inst) {
        // Dynamic attach
    }
}
```

## Instrumentation

### Class File Transformer

```java
// Transform classes at load time
ClassFileTransformer transformer = new ClassFileTransformer() {

    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer
    ) {
        // Transform bytecode
        return transformedBytes;
    }
};

// Register transformer
inst.addTransformer(transformer);
```

### Retransformation

```java
// Retransform already loaded classes
Class<?>[] classes = inst.getAllLoadedClasses();
inst.retransformClasses(classes);
```

## Probe Method Injection

### Generating Probe Methods

```java
// Generate probe methods into target class
ProbeClientClassFileTransformer transformer =
    new ProbeClientClassFileTransformer(probeProvider);

// Apply transformation
byte[] transformed = transformer.transform(classBytes);
```

## Dynamic Attach

### Attaching to Running JVM

```java
// Attach to running JVM
VirtualMachine vm = VirtualMachine.attach(pid);

// Load agent
vm.loadAgent("flashlight-agent.jar", agentArgs);

// Detach
vm.detach();
```

## Package Structure

```
org.glassfish.flashlight.agent/
└── ProbeAgentMain.java    # Agent entry point
```

## Dependencies

**Runtime:**
- `management-api` - JMX management API

## Related Modules

- **flashlight/framework** - Core Flashlight infrastructure
- **payara-modules/monitoring** - Monitoring features

## Use Cases

### 1. Dynamic Tracing

```bash
# Attach agent to running server for debugging
java -jar flashlight-agent.jar <pid>

# Agent injects probe methods into running classes
```

### 2. Development Monitoring

```bash
# Start server with agent for development
asadmin create-jvm-options \
    "-javaagent:${com.sun.aas.installRoot}/lib/flashlight-agent.jar"
```

## Limitations

- **Platform dependent** - Some features require specific JVM/OS
- **Overhead** - Bytecode instrumentation adds runtime cost
- **Redeployment** - May require retransformation on redeploy

## Troubleshooting

### Agent Not Loading

```bash
# Check agent JAR exists
ls -la ${com.sun.aas.installRoot}/lib/flashlight-agent.jar

# Check JVM options
asadmin list-jvm-options | grep javaagent
```

### Class Transformation Failures

```bash
# Check agent logs
tail -f domains/domain1/logs/server.log | grep -i flashlight

# Enable debug for troubleshooting
asadmin create-jvm-options \
    "-Dorg.glassfish.flashlight.debug=true"
```

## Notes

- Flashlight agent is primarily used for development and debugging
- For production, consider Payara's MicroProfile monitoring instead
- Agent requires `Can-Redefine-Classes` and `Can-Retransform-Classes` permissions
