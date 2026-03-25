# CLAUDE.md - Web SSE

This file provides guidance for working with the `appserver/web/web-sse` module - Server-Sent Events (SSE) implementation.

## Module Overview

The web-sse module provides Server-Sent Events (SSE) support, allowing servers to push events to clients over HTTP. SSE is a one-way communication channel from server to client, ideal for real-time updates.

**Key Components:**
- **ServerSentEventHandler** - Base handler for SSE connections
- **ServerSentEventConnection** - Represents an SSE client connection
- **ServerSentEventServlet** - Servlet for handling SSE requests

## Build Commands

```bash
# Build web-sse module
mvn -DskipTests clean package -f appserver/web/web-sse/pom.xml
```

## Server-Sent Events Architecture

```
Client Browser
       │
       │ GET /events (text/event-stream)
       ▼
[ServerSentEventServlet]
       │
       ├─→ ServerSentEventHandler
       │     │
       │     ├─→ onConnecting() - Accept/reject connection
       │     ├─→ onConnected() - Connection established
       │     └─→ onClosed() - Connection closed
       │
       ▼
[ServerSentEventConnection]
       │
       ├─→ send(event) - Send event to client
       ├─→ sendData() - Send data
       ├─→ sendComment() - Send comment
       └─→ close() - Close connection
```

## SSE Handler

### Basic SSE Handler

```java
@Path("/events")
public class MySSEHandler extends ServerSentEventHandler {

    @Override
    public Status onConnecting(HttpServletRequest request) {
        // Decide whether to accept the connection
        String authHeader = request.getHeader("Authorization");
        if (isValidToken(authHeader)) {
            return Status.OK;
        }
        return Status.DONT_RECONNECT;
    }

    @Override
    public void onConnected(ServerSentEventConnection connection) {
        this.connection = connection;

        // Send initial event
        try {
            connection.send(new ServerSentEventData()
                .data("Connected to SSE")
                .event("connected")
                .id("0"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClosed() {
        // Cleanup when client disconnects
        System.out.println("Client disconnected");
    }

    public void broadcast(String message) throws IOException {
        if (connection != null && connection.isConnected()) {
            connection.send(new ServerSentEventData()
                .data(message)
                .event("message"));
        }
    }
}
```

### SSE Data Events

```java
ServerSentEventData event = new ServerSentEventData();

// Set data
event.data("Hello, World!");

// Set event type
event.event("message");

// Set event ID
event.id("123");

// Set retry interval (milliseconds)
event.retry(3000);

// Send event
connection.send(event);
```

### Sending Different Event Types

```java
// Text message
connection.send(new ServerSentEventData()
    .data("This is a message")
    .event("message"));

// JSON data
connection.send(new ServerSentEventData()
    .data("{\"temperature\":72.5,\"unit\":\"F\"}")
    .event("sensor-update"));

// Progress update
connection.send(new ServerSentEventData()
    .data("50% complete")
    .event("progress")
    .id("progress-50"));

// Comment (not sent to client event handler)
connection.sendComment("This is a server comment");
```

## CDI Integration

### CDI-Enabled SSE Handler

```java
@ServerSentEventHandlerPath("/events")
public class CdiSSEHandler extends ServerSentEventHandler {

    @Inject
    private EventService eventService;

    @Inject
    private UserService userService;

    @Override
    public void onConnected(ServerSentEventConnection connection) {
        this.connection = connection;

        // Start background task to push events
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<Event> events = eventService.getRecentEvents();
                for (Event event : events) {
                    connection.send(new ServerSentEventData()
                        .data(event.toJson())
                        .event(event.getType()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
}
```

## SSE vs WebSocket

| Feature | SSE | WebSocket |
|---------|-----|-----------|
| Direction | Server → Client only | Bidirectional |
| Protocol | HTTP | HTTP upgrade to WebSocket |
| Automatic Reconnect | Built-in | Manual |
| Binary Data | Base64 encoded | Native support |
| Browser Support | All modern browsers | Most modern browsers |

## Use Cases

### Real-Time Notifications

```java
@Path("/notifications")
public class NotificationHandler extends ServerSentEventHandler {

    @Inject
    private NotificationService notificationService;

    @Override
    public void onConnected(ServerSentEventConnection connection) {
        // Listen for notifications and push to client
        notificationService.addListener(notification -> {
            try {
                connection.send(new ServerSentEventData()
                    .data(notification.toJson())
                    .event("notification"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
```

### Live Score Updates

```java
@Path("/scores")
public class ScoreHandler extends ServerSentEventHandler {

    @Override
    public void onConnected(ServerSentEventConnection connection) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                ScoreBoard score = ScoreBoard.getCurrentScore();
                connection.send(new ServerSentEventData()
                    .data(score.toJson())
                    .event("score-update")
                    .retry(5000));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
}
```

### Progress Updates

```java
@Path("/progress")
public class ProgressHandler extends ServerSentEventHandler {

    private ServerSentEventConnection connection;

    @POST
    @Path("/start")
    public Response startProcessing() {
        new Thread(() -> {
            try {
                for (int i = 0; i <= 100; i += 10) {
                    connection.send(new ServerSentEventData()
                        .data(String.valueOf(i))
                        .event("progress")
                        .id("progress-" + i));
                    Thread.sleep(1000);
                }
                connection.send(new ServerSentEventData()
                    .data("Complete!")
                    .event("complete"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        return Response.accepted().build();
    }
}
```

## Client-Side JavaScript

```html
<script>
const eventSource = new EventSource('/events');

// Listen for all messages
eventSource.onmessage = function(event) {
    console.log('Message:', event.data);
};

// Listen for specific event type
eventSource.addEventListener('notification', function(event) {
    const notification = JSON.parse(event.data);
    showNotification(notification);
});

// Listen for connected event
eventSource.addEventListener('connected', function(event) {
    console.log('Connected to SSE');
});

// Handle connection open
eventSource.onopen = function() {
    console.log('SSE connection opened');
};

// Handle errors
eventSource.onerror = function(err) {
    console.error('SSE error:', err);
};

// Close connection
// eventSource.close();
</script>
```

## Configuration

### Enable SSE in web.xml

```xml
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         version="6.0">

    <servlet>
        <servlet-name>ServerSentEventServlet</servlet-name>
        <servlet-class>org.glassfish.sse.impl.ServerSentEventServlet</servlet-class>
        <async-supported>true</async-supported>
    </servlet>

    <servlet-mapping>
        <servlet-name>ServerSentEventServlet</servlet-name>
        <url-pattern>/events/*</url-pattern>
    </servlet-mapping>
</web-app>
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.servlet-api` | Servlet 6.0 API |
| `weld` | CDI integration |

## Notes

- **One-Way Communication** - SSE is server-to-client only
- **Automatic Reconnect** - Browsers auto-reconnect on disconnect
- **Text-Based** - Use base64 for binary data
- **Event-Stream** - Content-Type: text/event-stream
- **Last-Event-ID** - Support for resuming from last event
