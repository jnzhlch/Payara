# CLAUDE.md - JSP Caching Connector

This file provides guidance for working with the `appserver/web/jspcaching-connector` module - JSP caching integration connector.

## Module Overview

The jspcaching-connector module provides JSP caching integration, registering the TLD (Tag Library Descriptor) for JSP caching tags. This enables caching of JSP fragments and page output.

**Key Components:**
- **GlassFishTldProvider** - JSP caching TLD registration

## Build Commands

```bash
# Build jspcaching-connector module
mvn -DskipTests clean package -f appserver/web/jspcaching-connector/pom.xml
```

## Architecture

```
JSP Page
       │
       ▼
[JSP Caching Tags]
       │
       ├─→ <cache:cache> - Cache content
       ├─→ <cache:flush> - Flush cache
       └─→ <cache:fragment> - Cache fragments
       │
       ▼
[Cache Store]
       │
       ├─→ Memory-based cache
       ├─→ Distributed cache (Hazelcast)
       └─→ Configurable TTL
```

## JSP Caching Tags

### Cache Tag

```jsp
<%@ taglib prefix="cache" uri="http://java.sun.com/jsp/jstl/cache" %>

<!-- Cache block for 60 seconds -->
<cache:cache key="userList" timeout="60">
    <c:forEach var="user" items="${userService.allUsers}">
        ${user.name}<br/>
    </c:forEach>
</cache:cache>

<!-- Cache with custom scope -->
<cache:cache key="product_${productId}" scope="session" timeout="300">
    <h2>${product.name}</h2>
    <p>${product.description}</p>
    <p>Price: $${product.price}</p>
</cache:cache>
```

### Flush Tag

```jsp
<!-- Flush specific cache entry -->
<cache:flush key="userList"/>

<!-- Flush all caches in scope -->
<cache:flush scope="application"/>

<!-- Flush all caches -->
<cache:flush/>
```

### Fragment Caching

```jsp
<!-- Cache a page fragment -->
<cache:fragment id="header">
    <header>
        <h1>My Website</h1>
        <nav>
            <a href="/">Home</a>
            <a href="/products">Products</a>
            <a href="/about">About</a>
        </nav>
    </header>
</cache:fragment>

<!-- Use cached fragment -->
<cache:useFragment id="header"/>
```

## Cache Configuration

### Enable Caching in web.xml

```xml
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         version="6.0">

    <!-- JSP configuration -->
    <jsp-config>
        <jsp-property-group>
            <url-pattern>*.jsp</url-pattern>
            <enable-caching>true</enable-caching>
        </jsp-property-group>
    </jsp-config>

    <!-- Cache configuration context params -->
    <context-param>
        <param-name>org.glassfish.jsp.cache.defaultTimeout</param-name>
        <param-value>300</param-value>
    </context-param>

    <context-param>
        <param-name>org.glassfish.jsp.cache.enabled</param-name>
        <param-value>true</param-value>
    </context-param>
</web-app>
```

## Cache Scopes

| Scope | Description | Duration |
|-------|-------------|----------|
| `application` | Shared across all users | Until server restart |
| `session` | Per user session | Until session expires |
| `request` | Per HTTP request | Until request completes |

## Caching Strategies

### Time-Based Caching

```jsp
<!-- Cache for 5 minutes -->
<cache:cache key="latestNews" timeout="300">
    <c:forEach var="news" items="${newsService.latest}">
        <h3>${news.title}</h3>
        <p>${news.summary}</p>
    </c:forEach>
</cache:cache>
```

### Dynamic Key Caching

```jsp
<!-- Cache per product -->
<cache:cache key="product_${product.id}" timeout="3600">
    <div class="product">
        <h2>${product.name}</h2>
        <p>${product.description}</p>
        <p>$${product.price}</p>
    </div>
</cache:cache>

<!-- Cache per user -->
<cache:cache key="userProfile_${user.id}" scope="session" timeout="600">
    <h1>Welcome, ${user.name}!</h1>
    <p>Email: ${user.email}</p>
</cache:cache>
```

### Conditional Caching

```jsp
<!-- Only cache if user is not admin -->
<c:if test="${not user.admin}">
    <cache:cache key="publicData" timeout="300">
        <!-- Public content -->
    </cache:cache>
</c:if>

<c:if test="${user.admin}">
    <!-- Admin content - never cached -->
</c:if>
```

## Cache Invalidation

### Manual Flush

```jsp
<!-- After data update -->
<%
    productDAO.update(product);
%>
<cache:flush key="product_${product.id}"/>

<!-- After batch update -->
<%
    newsService.refreshAll();
%>
<cache:flush key="latestNews"/>
```

### Programmatic Flush

```java
@WebServlet("/admin/update")
public class UpdateServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        // Update data
        productService.updateProduct(product);

        // Invalidate cache
        CacheManager cacheManager = CacheManager.getInstance();
        cacheManager.flush("product_" + product.getId());
    }
}
```

## Performance Monitoring

### Cache Statistics

```jsp
<%
    CacheManager cacheManager = CacheManager.getInstance();
    CacheStats stats = cacheManager.getStats();
%>

<h2>Cache Statistics</h2>
<ul>
    <li>Hits: <%= stats.getHits() %></li>
    <li>Misses: <%= stats.getMisses() %></li>
    <li>Hit Ratio: <%= stats.getHitRatio() %>%</li>
    <li>Entries: <%= stats.getEntryCount() %></li>
</ul>
```

## Distributed Caching

### Hazelcast Integration

```xml
<context-param>
    <param-name>org.glassfish.jsp.cache.type</param-name>
    <param-value>hazelcast</param-value>
</context-param>

<context-param>
    <param-name>org.glassfish.jsp.cache.hazelcast.config</param-name>
    <param-value>hazelcast-cache-config.xml</param-value>
</context-param>
```

### Cache Configuration

```xml
<!-- hazelcast-cache-config.xml -->
<hazelcast>
    <cache name="jsp-cache">
        <eviction size="1000" max-size-policy="ENTRY_COUNT"/>
        <time-to-live-seconds>300</time-to-live-seconds>
    </cache>
</hazelcast>
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.servlet-api` | Servlet API |
| `jakarta.jsp-api` | JSP API |
| `wasp` | JSP implementation |

## Notes

- **Fragment Caching** - Cache portions of JSP pages
- **Multiple Scopes** - Application, session, request
- **Distributed Cache** - Hazelcast integration for clustering
- **TLD Registration** - Registers JSP caching tag library descriptor
