package com.hezitu.heaicodemother.core;

import com.hezitu.heaicodemother.ai.AiCodeGeneratorService;
import com.hezitu.heaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.hezitu.heaicodemother.ai.model.HtmlCodeResult;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.File;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    private static final String PROMPT = "build a simple hezitu blog page";

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @MockBean
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Test
    void generateAndSaveCode() {
        AiCodeGeneratorService aiCodeGeneratorService = Mockito.mock(AiCodeGeneratorService.class);
        HtmlCodeResult htmlCodeResult = new HtmlCodeResult();
        htmlCodeResult.setHtmlCode("<html><body><h1>hezitu</h1></body></html>");
        htmlCodeResult.setDescription("test");

        Mockito.when(aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(0L, CodeGenTypeEnum.HTML, null))
                .thenReturn(aiCodeGeneratorService);
        Mockito.when(aiCodeGeneratorService.generateHtmlCode(1, PROMPT))
                .thenReturn(htmlCodeResult);

        File file = aiCodeGeneratorFacade.generateAndSaveCode(PROMPT, CodeGenTypeEnum.HTML, 0L);

        Assertions.assertNotNull(file);
        Assertions.assertTrue(file.exists());
        Assertions.assertTrue(file.isDirectory());
    }
}