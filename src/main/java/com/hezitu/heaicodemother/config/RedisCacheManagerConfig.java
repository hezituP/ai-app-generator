package com.hezitu.heaicodemother.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 缂撳瓨绠＄悊鍣ㄩ厤缃? */
@Configuration
public class RedisCacheManagerConfig {

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Bean
    public CacheManager cacheManager() {
        // 閰嶇疆 ObjectMapper 鏀寔 Java8 鏃堕棿绫诲瀷
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // 榛樿閰嶇疆
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30)) // 榛樿 30 鍒嗛挓杩囨湡
                .disableCachingNullValues() // 绂佺敤 null 鍊肩紦瀛?                // key 浣跨敤 String 搴忓垪鍖栧櫒
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()));
//                // value 浣跨敤 JSON 搴忓垪鍖栧櫒锛堟敮鎸佸鏉傚璞★級浣嗘槸瑕佹敞鎰忓紑鍚悗闇€瑕佺粰搴忓垪鍖栧鍔犻粯璁ょ被鍨嬮厤缃紝鍚﹀垯鏃犳硶鍙嶅簭鍒楀寲
//                .serializeValuesWith(RedisSerializationContext.SerializationPair
//                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                // 閽堝 good_app_page 閰嶇疆5鍒嗛挓杩囨湡
                .withCacheConfiguration("good_app_page",
                        defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .build();
    }
} 
