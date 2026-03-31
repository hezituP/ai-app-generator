package com.hezitu.heaicodemother.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * html代码生成结果
 */
@Description("生成html代码的结果")
@Data
public class HtmlCodeResult {
    @Description("html代码")
    private String htmlCode;
/**
 * 描述
 */
    @Description("生成代码的描述")
    private String description;
}
