package com.hezitu.heaicodemother.ai;

import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiCodeGeneratorServiceFactoryTest {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Test
    void shouldCreateHtmlService() {
        AiCodeGeneratorService service = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(1L);
        Assertions.assertNotNull(service);
    }

    @Test
    void shouldCreateMultiFileService() {
        AiCodeGeneratorService service =
                aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(1L, CodeGenTypeEnum.MULTI_FILE);
        Assertions.assertNotNull(service);
    }

    @Test
    void shouldCreateUserScopedService() {
        AiCodeGeneratorService service =
                aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(1L, CodeGenTypeEnum.HTML, 1001L);
        Assertions.assertNotNull(service);
    }
}
