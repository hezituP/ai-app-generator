package com.hezitu.heaicodemother;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.hezitu.heaicodemother.mapper")
public class HeAiCodeMotherApplication {

    public static void main (String[] args) {
        SpringApplication.run(HeAiCodeMotherApplication.class, args);
    }

}
