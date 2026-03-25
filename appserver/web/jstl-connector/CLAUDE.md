# CLAUDE.md - JSTL Connector

This file provides guidance for working with the `appserver/web/jstl-connector` module - Jakarta Standard Tag Library (JSTL) integration connector.

## Module Overview

The jstl-connector module provides integration between GlassFish/Payara and JSTL (Jakarta Standard Tag Library). Its primary purpose is to register JSTL Tag Library Descriptors (TLDs) so that JSP pages can use JSTL tags.

**Key Components:**
- **GlassFishTldProvider** - JSTL TLD registration

## Build Commands

```bash
# Build jstl-connector module
mvn -DskipTests clean package -f appserver/web/jstl-connector/pom.xml
```

## Architecture

```
JSP Page with JSTL Tags
       │
       ▼
[JSP Compiler]
       │
       ├─→ Needs TLD for tag libraries
       │
       ▼
[GlassFishTldProvider]
       │
       ├─→ Registers JSTL Core TLD
       ├─→ Registers JSTL Format TLD
       ├─→ Registers JSTL SQL TLD
       ├─→ Registers JSTL XML TLD
       └─→ Registers JSTL Functions TLD
       │
       ▼
[JSTL Implementation]
```

## JSTL Tag Libraries

### Core Library (c:)

```jsp
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!-- Variables -->
<c:set var="name" value="John"/>
<c:out value="${name}"/>

<!-- Conditionals -->
<c:if test="${name eq 'John'}">
    Hello John!
</c:if>

<c:choose>
    <c:when test="${role eq 'admin'}">Admin</c:when>
    <c:otherwise>User</c:otherwise>
</c:choose>

<!-- Loops -->
<c:forEach var="item" items="${items}">
    ${item.name}
</c:forEach>

<c:forTokens var="token" items="a,b,c" delims=",">
    ${token}
</c:forTokens>

<!-- URLs -->
<c:url var="link" value="/products"/>
<a href="${link}">Products</a>
```

### Format Library (fmt:)

```jsp
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<!-- Numbers -->
<fmt:formatNumber value="${price}" type="currency"/>
<fmt:parseNumber var="num" value="1,234.56"/>

<!-- Dates -->
<fmt:formatDate value="${date}" pattern="yyyy-MM-dd"/>
<fmt:timeZone value="America/New_York">
    <fmt:formatDate value="${date}" type="both"/>
</fmt:timeZone>

<!-- Messages -->
<fmt:setBundle basename="messages"/>
<fmt:message key="welcome"/>

<!-- Request Encoding -->
<fmt:requestEncoding value="UTF-8"/>
```

### SQL Library (sql:)

```jsp
<%@ taglib prefix="sql" uri="http://java.sun.com/jsp/jstl/sql" %>

<!-- Query -->
<sql:query var="users" dataSource="jdbc/myDS">
    SELECT * FROM users WHERE status = ?
    <sql:param value="${status}"/>
</sql:query>

<c:forEach var="user" items="${users.rows}">
    ${user.name}
</c:forEach>

<!-- Update -->
<sql:update dataSource="jdbc/myDS">
    INSERT INTO users (name, email) VALUES (?, ?)
    <sql:param value="${name}"/>
    <sql:param value="${email}"/>
</sql:update>
```

### XML Library (x:)

```jsp
<%@ taglib prefix="x" uri="http://java.sun.com/jsp/jstl/xml" %>

<!-- Parse XML -->
<x:parse var="doc">
    <root>
        <item>Value 1</item>
        <item>Value 2</item>
    </root>
</x:parse>

<!-- XPath -->
<x:forEach select="$doc//item">
    <x:out select="."/>
</x:forEach>

<!-- Transform -->
<x:transform xml="${xml}" xslt="${xslt}"/>
```

### Functions Library (fn:)

```jsp
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

${fn:contains(text, "search")}
${fn:startsWith(text, "prefix")}
${fn:endsWith(text, "suffix")}
${fn:escapeXml(text)}
${fn:length(collection)}
${fn:toUpperCase(text)}
${fn:toLowerCase(text)}
${fn:trim(text)}
${fn:replace(text, "old", "new")}
${fn:split(text, ",")}
${fn:join(array, ",")}
```

## TLD Provider Implementation

### GlassFishTldProvider

```java
@Service
public class GlassFishTldProvider implements TldProvider {

    @Override
    public List<String> getTldPaths() {
        return Arrays.asList(
            "META-INF/jstl-c.tld",      // Core
            "META-INF/jstl-fmt.tld",    // Format
            "META-INF/jstl-sql.tld",    // SQL
            "META-INF/jstl-x.tld",      // XML
            "META-INF/jstl-fn.tld"      // Functions
        );
    }

    @Override
    public String getName() {
        return "jstl";
    }

    @Override
    public URL getTldResource(String path) {
        // Return TLD resource from classpath
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.servlet-api` | Servlet API |
| `jakarta.jsp-api` | JSP API |
| `jakarta.jstl-api` | JSTL API |

## Notes

- **TLD Registration** - Registers JSTL tag library descriptors
- **JSP Integration** - Enables JSTL tags in JSP pages
- **JSTL 3.0** - Jakarta EE 10 JSTL specification
- **Five Libraries** - Core, Format, SQL, XML, Functions
