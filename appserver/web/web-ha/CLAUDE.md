# CLAUDE.md - Web HA

This file provides guidance for working with the `appserver/web/web-ha` module - High availability for web sessions.

## Module Overview

The web-ha module provides distributed session management for web applications using Hazelcast for session replication across Payara cluster members.

**Key Components:**
- **ReplicatedSession** - Distributed session implementation
- **HazelcastSessionManager** - Hazelcast-based session manager
- **Session persistence** - Session failover support

## Build Commands

```bash
# Build web-ha module
mvn -DskipTests clean package -f appserver/web/web-ha/pom.xml
```

## Architecture

### Session Replication Flow

```
Request → Instance A
       │
       ├─→ Create/Update Session
       │
       ▼
[Hazelcast Distributed Cache]
       │
       ├─→ Replicate to Instance B
       ├─→ Replicate to Instance C
       └─→ Replicate to Instance D
       │
       ▼
Failover → Instance B/C/D
       │
       └─→ Session available (replicated)
```

## HazelcastSessionManager

```java
public class HazelcastSessionManager extends StandardManager {

    private IMap<String, SessionData> sessionMap;
    private String mapName = "web-sessions";

    @Override
    protected void startInternal() throws LifecycleException {
        super.startInternal();

        // Get Hazelcast instance
        HazelcastInstance hazelcast = getHazelcastInstance();

        // Get distributed map
        sessionMap = hazelcast.getMap(mapName);
    }

    @Override
    public Session createSession(String sessionId) {
        // Create replicated session
        ReplicatedSession session = new ReplicatedSession(this, sessionId);

        // Store in distributed map
        sessionMap.put(sessionId, session.getSessionData());

        return session;
    }

    @Override
    public Session findSession(String sessionId) {
        // Find from distributed map
        SessionData data = sessionMap.get(sessionId);
        if (data != null) {
            return new ReplicatedSession(this, data);
        }
        return null;
    }

    @Override
    public void remove(Session session) {
        // Remove from distributed map
        sessionMap.remove(session.getId());
    }
}
```

## ReplicatedSession

```java
public class ReplicatedSession extends StandardSession {

    private final IMap<String, SessionData> sessionMap;

    @Override
    public void setAttribute(String name, Object value) {
        super.setAttribute(name, value);

        // Trigger replication
        replicate();
    }

    @Override
    public void removeAttribute(String name) {
        super.removeAttribute(name);

        // Trigger replication
        replicate();
    }

    private void replicate() {
        // Create session data
        SessionData data = new SessionData();
        data.setId(getId());
        data.setCreationTime(getCreationTime());
        data.setLastAccessedTime(getLastAccessedTime());
        data.setMaxInactiveInterval(getMaxInactiveInterval());
        data.setAttributes(new HashMap<>(getAttributes()));

        // Replicate to Hazelcast
        sessionMap.put(getId(), data);
    }
}
```

## Configuration

### web.xml

```xml
<session-config>
    <session-timeout>30</session-timeout>
    <cookie-config>
        <http-only>true</http-only>
        <secure>true</secure>
    </cookie-config>
    <tracking-mode>COOKIE</tracking-mode>
</session-config>
```

### domain.xml

```xml
<configs>
    <config name="server-config">
        <web-container>
            <session-manager persistence-type="hazelcast">
                <manager-properties>
                    <property name="repl-enabled" value="true"/>
                    <property name="map-name" value="web-sessions"/>
                </manager-properties>
            </session-manager>
        </web-container>
    </config>
</configs>
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `hazelcast` | Distributed caching |
| `web-core` | Session manager base classes |

## Notes

- **Session Replication** - Automatic replication across cluster
- **Failover** - Sessions survive instance failures
- **Hazelcast** - Uses Hazelcast distributed map
- **Configurable** - Map name, replication settings
- **Performance** - Async replication for performance
