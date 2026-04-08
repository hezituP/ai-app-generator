package com.hezitu.heaicodemother;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@ComponentScan(excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.hezitu\\.heaicodemother\\.workflow\\.langgraph4j\\..*"
))
@MapperScan("com.hezitu.heaicodemother.mapper")
public class HeAiCodeMotherApplication {

    public static void main (String[] args) {
        SpringApplication.run(HeAiCodeMotherApplication.class, args);
    }

}
