package com.hezitu.heaicodemother.core;

import cn.hutool.core.lang.Assert;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class AiCodeGeneratorFacadeTest {


    @Resource
    
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Test
    void generateAndSaveCode () {
        File file = aiCodeGeneratorFacade.generateAndSaveCode("做一个程序员hezitu的博客，最好不超过20行", CodeGenTypeEnum.HTML, 0L);
        Assertions.assertNotNull(file);

    }
}