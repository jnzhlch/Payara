# CLAUDE.md - Payara JSR-107

This file provides guidance for working with the `payara-jsr107` module - JCache (JSR-107) CDI integration with Hazelcast.

## Module Overview

The payara-jsr107 module provides CDI (Contexts and Dependency Injection) integration for JCache (JSR-107) using Hazelcast as the caching provider. It enables declarative caching through annotations and programmatic cache injection.

**Key Features:**
- **@CacheResult** - Cache method return values
- **@CachePut** - Update cache with method results
- **@CacheRemove** - Remove entries from cache
- **@CacheRemoveAll** - Remove all entries from cache
- **Cache Injection** - Inject Cache<K,V> via CDI
- **@NamedCache** - Configure cache properties via annotation
- **Exception Caching** - Cache exceptions separately
- **Hazelcast Integration** - Uses Hazelcast JCache provider

## Build Commands

```bash
# Build payara-jsr107 module
mvn -DskipTests clean package -f appserver/payara-appserver-modules/payara-jsr107/pom.xml

# Run tests
mvn test -f appserver/payara-appserver-modules/payara-jsr107/pom.xml
```

## Architecture

### CDI Extension Flow

```
Application Startup
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│           PayaraHazelcastCDIExtension                          │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  beforeBeanDiscovery(@Observes BeforeBeanDiscovery)       │ │
│  │  1. Register JSR107Producer for cache injection          │ │
│  │  2. Add interceptor bindings:                            │ │
│  │     - @CacheResult                                       │ │
│  │     - @CachePut                                          │ │
│  │     - @CacheRemove                                       │ │
│  │     - @CacheRemoveAll                                    │ │
│  │  3. Register interceptors:                               │ │
│  │     - CacheResultInterceptor                             │ │
│  │     - CachePutInterceptor                                │ │
│  │     - CacheRemoveInterceptor                             │ │
│  │     - CacheRemoveAllInterceptor                          │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│                  JSR107Producer                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Produces injectable artifacts:                          │ │
│  │  - CachingProvider                                       │ │
│  │  - CacheManager                                          │ │
│  │  - HazelcastInstance                                     │ │
│  │  - Cache<K,V> (with @NamedCache configuration)          │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### @CacheResult Interception Flow

```
Method Call with @CacheResult
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│          CacheResultInterceptor.cacheResult()                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Check isEnabled() - Hazelcast enabled?               │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. Create PayaraCacheKeyInvocationContext               │ │
│  │     - Resolve cache name                                 │ │
│  │     - Get CacheResolverFactory                           │ │
│  │     - Get CacheKeyGenerator                              │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Check skipGet flag                                   │ │
│  │     if (!skipGet) {                                      │ │
│  │       cache.get(key)                                     │ │
│  │       if (found) return cachedValue                      │ │
│  │     }                                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  4. Check exception cache (if configured)                │ │
│  │     exceptionCache.get(key)                              │ │
│  │     if (exception found) throw exception                 │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  5. Proceed method invocation                            │ │
│  │     result = ctx.proceed()                               │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  6. Cache result                                         │ │
│  │     cache.put(key, result)                               │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  7. Handle exceptions (if exceptionCacheName set)        │ │
│  │     if (shouldCacheException(e)) {                       │ │
│  │       exceptionCache.put(key, e)                         │ │
│  │     }                                                    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### Cache Key Generation

```
┌────────────────────────────────────────────────────────────────┐
│              PayaraCacheKeyGenerator                           │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Key Parameter Selection:                                │ │
│  │  1. Parameters annotated with @CacheKey                 │ │
│  │  2. All parameters EXCEPT those with @CacheValue         │ │
│  │  3. Default: All method parameters                       │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  GeneratedCacheKey format:                               │ │
│  │  - PayaraGeneratedCacheKey wraps parameter array        │ │
│  │  - Implements equals/hashCode based on all parameters    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### Cache Name Resolution

```
┌────────────────────────────────────────────────────────────────┐
│            Cache Name Resolution Order                         │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  1. Annotation cacheName                                 │ │
│  │     @CacheResult(cacheName = "myCache")                 │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  2. @CacheDefaults cacheName (class-level)               │ │
│  │     @CacheDefaults(cacheName = "classCache")            │ │
│  │     public class MyService { ... }                       │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  3. Generated cache name (default)                       │ │
│  │     Format: ClassName.methodName(param1,param2,...)     │ │
│  │     Example: com.example.Service.getUser(String,int)    │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

## Core Components

### PayaraHazelcastCDIExtension

```java
public class PayaraHazelcastCDIExtension implements Extension {
    void beforeBeanDiscovery(@Observes BeforeBeanDiscovery bbd, BeanManager bm) {
        // Register JSR107Producer for cache injection
        AnnotatedType<JSR107Producer> at = bm.createAnnotatedType(JSR107Producer.class);
        bbd.addAnnotatedType(at, JSR107Producer.class.getName());

        // Add interceptor bindings
        bbd.addInterceptorBinding(CacheResult.class);
        bbd.addInterceptorBinding(CachePut.class);
        bbd.addInterceptorBinding(CacheRemove.class);
        bbd.addInterceptorBinding(CacheRemoveAll.class);

        // Register interceptors
        bbd.addAnnotatedType(bm.createAnnotatedType(CacheResultInterceptor.class), ...);
        // ... other interceptors
    }
}
```

**Purpose:** CDI extension that registers caching interceptors and producers during bean discovery.

### JSR107Producer

```java
public class JSR107Producer {
    @Produces
    public <K,V> Cache<K, V> createCache(InjectionPoint ip) {
        // Default cache name = fully qualified class name
        String cacheName = ip.getMember().getDeclaringClass().getCanonicalName();

        // Check for @NamedCache qualifier
        NamedCache ncqualifier = ip.getAnnotated().getAnnotation(NamedCache.class);
        if (ncqualifier != null) {
            // Apply @NamedCache configuration
            cacheName = ncqualifier.cacheName();
            MutableConfiguration<K, V> config = new MutableConfiguration<>();
            config.setTypes(ncqualifier.keyClass(), ncqualifier.valueClass());

            // Configure expiry policy
            if (ncqualifier.expiryPolicyFactoryClass() != Object.class) {
                Factory factory = FactoryBuilder.factoryOf(ncqualifier.expiryPolicyFactoryClass());
                config.setExpiryPolicyFactory(factory);
            }

            // Configure cache writer/reader
            config.setCacheWriterFactory(FactoryBuilder.factoryOf(ncqualifier.cacheWriterFactoryClass()));
            config.setCacheLoaderFactory(FactoryBuilder.factoryOf(ncqualifier.cacheLoaderFactoryClass()));

            return manager.createCache(cacheName, config);
        }
    }
}
```

**Purpose:** Produces injectable Cache instances with configurable properties.

### CacheResultInterceptor

```java
@CacheResult
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_AFTER)
public class CacheResultInterceptor extends AbstractJSR107Interceptor {

    @AroundInvoke
    public Object cacheResult(InvocationContext ctx) throws Throwable {
        if (!isEnabled()) {
            return ctx.proceed();  // Hazelcast disabled
        }

        CacheResult annotation = ctx.getMethod().getAnnotation(CacheResult.class);
        PayaraCacheKeyInvocationContext<CacheResult> pctx =
            new PayaraCacheKeyInvocationContext<>(ctx, annotation);

        // Resolve cache
        CacheResolver cacheResolver = pctx.getFactory().getCacheResolver(pctx);
        Cache cache = cacheResolver.resolveCache(pctx);

        // Generate cache key
        GeneratedCacheKey key = pctx.getGenerator().generateCacheKey(pctx);

        // Try to get from cache
        if (!annotation.skipGet()) {
            Object cacheResult = cache.get(key);
            if (cacheResult != null) {
                return cacheResult;
            }

            // Check exception cache
            if (annotation.exceptionCacheName() != null) {
                Cache exceptionCache = pctx.getFactory()
                    .getExceptionCacheResolver(pctx).resolveCache(pctx);
                Throwable e = (Throwable) exceptionCache.get(key);
                if (e != null) {
                    throw e;
                }
            }
        }

        // Invoke method and cache result
        try {
            Object result = ctx.proceed();
            cache.put(key, result);
            return result;
        } catch (Throwable e) {
            if (annotation.exceptionCacheName() != null) {
                // Cache exception
                if (shouldICache(annotation.cachedExceptions(),
                               annotation.nonCachedExceptions(), e, true)) {
                    exceptionCache.put(key, e);
                }
            }
            throw e;
        }
    }
}
```

**Purpose:** Intercepts methods annotated with @CacheResult to cache return values.

### CachePutInterceptor

```java
@CachePut
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_AFTER)
public class CachePutInterceptor extends AbstractJSR107Interceptor {

    @AroundInvoke
    public Object cachePut(InvocationContext ctx) throws Throwable {
        CachePut annotation = ctx.getMethod().getAnnotation(CachePut.class);
        PayaraCacheKeyInvocationContext<CachePut> pctx =
            new PayaraCacheKeyInvocationContext<>(ctx, annotation);

        // Put before invocation if configured
        if (!annotation.afterInvocation()) {
            doPut(pctx);
        }

        Object result = ctx.proceed();

        // Put after invocation if configured
        if (annotation.afterInvocation()) {
            if (shouldICache(annotation.cacheFor(),
                           annotation.noCacheFor(), e, false)) {
                doPut(pctx);
            }
        }

        return result;
    }

    private void doPut(PayaraCacheKeyInvocationContext<CachePut> pctx) {
        GeneratedCacheKey key = pctx.getGenerator().generateCacheKey(pctx);
        Object value = pctx.getValueParameter().getValue();
        cache.put(key, value);
    }
}
```

**Purpose:** Intercepts methods annotated with @CachePut to update cache entries.

### CacheRemoveInterceptor

```java
@CacheRemove
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_AFTER)
public class CacheRemoveInterceptor extends AbstractJSR107Interceptor {

    @AroundInvoke
    public Object cacheRemove(InvocationContext ctx) throws Throwable {
        CacheRemove annotation = ctx.getMethod().getAnnotation(CacheRemove.class);
        PayaraCacheKeyInvocationContext<CacheRemove> pctx =
            new PayaraCacheKeyInvocationContext<>(ctx, annotation);

        // Remove before invocation if configured
        if (!annotation.afterInvocation()) {
            doRemove(pctx);
        }

        Object result = ctx.proceed();

        // Remove after invocation if configured
        if (annotation.afterInvocation()) {
            if (shouldIEvict(annotation.evictFor(),
                           annotation.noEvictFor(), e)) {
                doRemove(pctx);
            }
        }

        return result;
    }
}
```

**Purpose:** Intercepts methods annotated with @CacheRemove to evict cache entries.

### PayaraCacheKeyInvocationContext

```java
public class PayaraCacheKeyInvocationContext<A extends Annotation>
        implements CacheKeyInvocationContext<A> {

    public CacheInvocationParameter[] getKeyParameters() {
        // Priority 1: Parameters annotated with @CacheKey
        for (CacheInvocationParameter param : getAllParameters()) {
            if (hasAnnotation(param, CacheKey.class)) {
                result.add(param);
            }
        }

        if (result.isEmpty()) {
            // Priority 2: All parameters EXCEPT @CacheValue
            for (CacheInvocationParameter param : getAllParameters()) {
                if (!hasAnnotation(param, CacheValue.class)) {
                    result.add(param);
                }
            }
        }

        return result.toArray();
    }

    public String getCacheName() {
        // Resolution order:
        // 1. Annotation cacheName
        // 2. @CacheDefaults cacheName
        // 3. Generated: ClassName.methodName(paramTypes)
    }
}
```

**Purpose:** Provides cache invocation context including key parameters and cache name resolution.

### PayaraCacheResolverFactory

```java
public class PayaraCacheResolverFactory implements CacheResolverFactory, CacheResolver {

    public PayaraCacheResolverFactory() {
        cacheManager = HazelcastCore.getCore()
            .getCachingProvider().getCacheManager();
    }

    @Override
    public CacheResolver getCacheResolver(CacheMethodDetails<?> cmd) {
        String cacheName = cmd.getCacheName();
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            cache = cacheManager.createCache(cacheName,
                new MutableConfiguration<Object, Object>());
        }
        return new PayaraCacheResolver(cache);
    }

    @Override
    public <K, V> Cache<K, V> resolveCache(CacheInvocationContext<?> cic) {
        // Auto-create cache if not exists
        Cache<K,V> cache = cacheManager.getCache(cic.getCacheName());
        if (cache == null) {
            cacheManager.createCache(cic.getCacheName(),
                new MutableConfiguration<>());
            cache = cacheManager.getCache(cic.getCacheName());
        }
        return cache;
    }
}
```

**Purpose:** Resolves and creates caches dynamically using Hazelcast CacheManager.

## Usage Examples

### @CacheResult - Cache Method Results

```java
@ApplicationScoped
public class UserService {

    @CacheResult(cacheName = "userCache")
    public User getUser(String userId) {
        // Expensive database call
        return userRepository.findById(userId);
    }

    // With custom key generation
    @CacheResult(cacheName = "userCache")
    public User getUser(@CacheKey String userId,
                       @CacheKey String region) {
        // Key includes both userId and region
        return userRepository.findByRegion(userId, region);
    }

    // Skip cache lookup (always invoke method)
    @CacheResult(cacheName = "userCache", skipGet = true)
    public User refreshUser(String userId) {
        // Method always invoked, result cached
        return userRepository.findById(userId);
    }

    // Cache exceptions separately
    @CacheResult(cacheName = "userCache",
                 exceptionCacheName = "userExceptions",
                 cachedExceptions = {NotFoundException.class})
    public User getUserOrThrow(String userId) throws NotFoundException {
        return userRepository.findByIdOrThrow(userId);
    }
}
```

### @CachePut - Update Cache

```java
@ApplicationScoped
public class UserService {

    @CachePut(cacheName = "userCache", afterInvocation = true)
    public User updateUser(@CacheKey String userId, @CacheValue User user) {
        // Method invoked, then result cached
        return userRepository.save(user);
    }

    // Put before invocation
    @CachePut(cacheName = "userCache", afterInvocation = false)
    public User preUpdateUser(@CacheKey String userId, @CacheValue User user) {
        // Value cached BEFORE method invocation
        return userRepository.save(user);
    }

    // Conditional caching on exception
    @CachePut(cacheName = "userCache",
              cacheFor = {SuccessException.class},
              noCacheFor = {ValidationException.class})
    public User conditionalUpdate(User user) {
        // ...
    }
}
```

### @CacheRemove - Evict Cache Entries

```java
@ApplicationScoped
public class UserService {

    @CacheRemove(cacheName = "userCache", afterInvocation = true)
    public void deleteUser(@CacheKey String userId) {
        // User deleted, then cache entry removed
        userRepository.delete(userId);
    }

    // Remove before invocation
    @CacheRemove(cacheName = "userCache", afterInvocation = false)
    public void preDeleteUser(@CacheKey String userId) {
        // Cache removed BEFORE method invocation
        userRepository.delete(userId);
    }

    // Conditional eviction
    @CacheRemove(cacheName = "userCache",
                 evictFor = {DeleteSuccessException.class},
                 noEvictFor = {DeleteFailedException.class})
    public void conditionalDelete(String userId) {
        // ...
    }
}
```

### @CacheRemoveAll - Evict All Entries

```java
@ApplicationScoped
public class UserService {

    @CacheRemoveAll(cacheName = "userCache", afterInvocation = true)
    public void clearAllUsers() {
        // Method invoked, then all cache entries removed
    }
}
```

### Cache Injection

```java
@ApplicationScoped
public class DataService {

    // Basic cache injection (cache name = class name)
    @Inject
    private Cache<String, Data> dataCache;

    // Named cache with configuration
    @Inject
    @NamedCache(cacheName = "myCache",
                keyClass = String.class,
                valueClass = Data.class,
                expiryPolicyFactoryClass = ModifiedExpiryPolicy.class,
                statisticsEnabled = true,
                managementEnabled = true)
    private Cache<String, Data> configuredCache;

    // Use cache
    public Data getData(String key) {
        Data data = dataCache.get(key);
        if (data == null) {
            data = loadFromDatabase(key);
            dataCache.put(key, data);
        }
        return data;
    }
}
```

### @CacheDefaults - Class-Level Configuration

```java
@CacheDefaults(cacheName = "userServiceCache",
               cacheKeyGenerator = CustomKeyGenerator.class,
               cacheResolverFactory = CustomResolverFactory.class)
@ApplicationScoped
public class UserService {

    // Uses cacheName from @CacheDefaults
    @CacheResult
    public User getUser(String userId) {
        // ...
    }

    // Override cache name
    @CacheResult(cacheName = "adminUserCache")
    public User getAdminUser(String adminId) {
        // ...
    }
}
```

## Annotation Reference

### @CacheResult

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `cacheName` | String | "" | Cache name |
| `skipGet` | boolean | false | Skip cache lookup |
| `cacheKeyGenerator` | Class | CacheKeyGenerator.class | Custom key generator |
| `cacheResolverFactory` | Class | CacheResolverFactory.class | Custom resolver factory |
| `exceptionCacheName` | String | "" | Exception cache name |
| `cachedExceptions` | Class[] | {} | Exceptions to cache |
| `nonCachedExceptions` | Class[] | {} | Exceptions NOT to cache |

### @CachePut

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `cacheName` | String | "" | Cache name |
| `afterInvocation` | boolean | true | Put after method invocation |
| `cacheKeyGenerator` | Class | CacheKeyGenerator.class | Custom key generator |
| `cacheResolverFactory` | Class | CacheResolverFactory.class | Custom resolver factory |
| `cacheFor` | Class[] | {} | Exceptions that trigger caching |
| `noCacheFor` | Class[] | {} | Exceptions that prevent caching |

### @CacheRemove

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `cacheName` | String | "" | Cache name |
| `afterInvocation` | boolean | true | Remove after method invocation |
| `cacheKeyGenerator` | Class | CacheKeyGenerator.class | Custom key generator |
| `cacheResolverFactory` | Class | CacheResolverFactory.class | Custom resolver factory |
| `evictFor` | Class[] | {} | Exceptions that trigger eviction |
| `noEvictFor` | Class[] | {} | Exceptions that prevent eviction |

### @CacheRemoveAll

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `cacheName` | String | "" | Cache name |
| `afterInvocation` | boolean | true | Remove all after invocation |

### @NamedCache

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `cacheName` | String | "" | Cache name |
| `keyClass` | Class | Object.class | Cache key type |
| `valueClass` | Class | Object.class | Cache value type |
| `expiryPolicyFactoryClass` | Class | Object.class | Expiry policy factory |
| `cacheWriterFactoryClass` | Class | Object.class | Cache writer factory |
| `cacheLoaderFactoryClass` | Class | Object.class | Cache loader factory |
| `writeThrough` | boolean | false | Enable write-through |
| `readThrough` | boolean | false | Enable read-through |
| `managementEnabled` | boolean | false | Enable management |
| `statisticsEnabled` | boolean | false | Enable statistics |

## Configuration

### Hazelcast Configuration

```xml
<!-- hazelcast-config.xml -->
<hazelcast>
    <cache name="userCache">
        <eviction size="1000" max-size-policy="ENTRY_COUNT"/>
        <expiry-policy ttl-seconds="3600"/>
    </cache>
    <cache name="productCache">
        <eviction size="10000" max-size-policy="ENTRY_COUNT"/>
        <expiry-policy ttl-seconds="7200"/>
    </cache>
</hazelcast>
```

## Package Structure

```
payara-jsr107/
└── src/main/java/fish/payara/cdi/jsr107/
    ├── PayaraHazelcastCDIExtension.java       # CDI extension
    ├── JSR107Producer.java                   # Cache producer
    ├── AbstractJSR107Interceptor.java        # Base interceptor
    ├── CacheResultInterceptor.java           # @CacheResult interceptor
    ├── CachePutInterceptor.java              # @CachePut interceptor
    ├── CacheRemoveInterceptor.java           # @CacheRemove interceptor
    ├── CacheRemoveAllInterceptor.java        # @CacheRemoveAll interceptor
    └── implementation/
        ├── PayaraCacheKeyInvocationContext.java    # Invocation context
        ├── PayaraCacheKeyGenerator.java            # Key generator
        ├── PayaraGeneratedCacheKey.java            # Generated key wrapper
        ├── PayaraCacheResolverFactory.java         # Cache resolver
        ├── PayaraCacheResolver.java                # Resolver implementation
        ├── PayaraCacheInvocationParameter.java     # Method parameter wrapper
        └── PayaraTCCLObjectInputStream.java        # Deserialization helper
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `hazelcast-bootstrap` | Hazelcast integration and JCache provider |
| `jakarta.jakartaee-api` | Jakarta EE APIs (CDI, annotations) |
| `payara-api` | Payara public APIs |

## Notes

- **Hazelcast Required**: All caching functionality requires Hazelcast to be enabled
- **Null Returns**: `@CacheResult` treats null as a valid cached value
- **Exception Handling**: Exceptions can be cached separately using `exceptionCacheName`
- **Cache Auto-Creation**: Caches are automatically created if they don't exist
- **Key Generation**: Use `@CacheKey` to specify which parameters form the cache key
- **Value Parameter**: Use `@CacheValue` to specify the return value for `@CachePut`
- **Interceptor Priority**: Uses `PLATFORM_AFTER` priority, can be overridden
- **CDI Scope**: Injected caches use `@Dependent` scope by default
