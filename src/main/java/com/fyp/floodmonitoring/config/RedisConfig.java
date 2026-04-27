package com.fyp.floodmonitoring.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Cache configuration.
 *
 * When REDIS_URL is set  → uses Redis (Upstash free tier: 500K commands/month).
 * When REDIS_URL is absent → falls back to in-memory ConcurrentMapCache for local dev.
 *
 * Cache names and TTLs:
 *   "analytics"   — 5 minutes  (expensive aggregation queries on 200k+ events)
 *   "sensors"     — 30 seconds (sensor list, changes on ingest)
 *   "blogs"       — 60 minutes (static content, rarely changes)
 *   "dashboard"   — 1 minute
 *   "safety"      — 60 minutes (seeded text, rarely changes)
 */
@Configuration
@EnableCaching
public class RedisConfig {

    private static final String[] CACHE_NAMES =
            {"analytics", "sensors", "blogs", "dashboard", "safety", "zones"};

    /**
     * Single CacheManager bean.
     * Uses Redis when REDIS_URL resolves to a non-blank URL, in-memory otherwise.
     * The YAML default  `url: ${REDIS_URL:}`  sets the property to an empty string
     * when the env var is absent, so we must check for blank explicitly — plain
     * @ConditionalOnProperty would still match an empty string.
     */
    @Bean
    public CacheManager cacheManager(
            @Value("${spring.data.redis.url:}") String redisUrl,
            org.springframework.beans.factory.ObjectProvider<RedisConnectionFactory> factoryProvider) {

        if (redisUrl != null && !redisUrl.isBlank()) {
            RedisConnectionFactory factory = factoryProvider.getIfAvailable();
            if (factory != null) {
                GenericJackson2JsonRedisSerializer serializer =
                        new GenericJackson2JsonRedisSerializer();

                RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(5))
                        .disableCachingNullValues()
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(serializer));

                return RedisCacheManager.builder(factory)
                        .cacheDefaults(defaults)
                        .withCacheConfiguration("analytics",
                                defaults.entryTtl(Duration.ofMinutes(5)))
                        .withCacheConfiguration("sensors",
                                defaults.entryTtl(Duration.ofSeconds(30)))
                        .withCacheConfiguration("blogs",
                                defaults.entryTtl(Duration.ofMinutes(60)))
                        .withCacheConfiguration("dashboard",
                                defaults.entryTtl(Duration.ofMinutes(1)))
                        .withCacheConfiguration("safety",
                                defaults.entryTtl(Duration.ofMinutes(60)))
                        .withCacheConfiguration("zones",
                                defaults.entryTtl(Duration.ofMinutes(60)))
                        .build();
            }
        }

        // Fallback: in-memory cache for local dev (no Redis)
        return new ConcurrentMapCacheManager(CACHE_NAMES);
    }
}
