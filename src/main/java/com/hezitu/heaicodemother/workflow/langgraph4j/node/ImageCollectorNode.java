package com.hezitu.heaicodemother.workflow.langgraph4j.node;

import com.hezitu.heaicodemother.workflow.langgraph4j.model.ImageCategoryEnum;
import com.hezitu.heaicodemother.workflow.langgraph4j.model.ImageResource;
import com.hezitu.heaicodemother.workflow.langgraph4j.model.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.Arrays;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ImageCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 图片收集");

            List<ImageResource> imageList = Arrays.asList(
                    ImageResource.builder()
                            .category(ImageCategoryEnum.CONTENT)
                            .description("假数据内容图片")
                            .url("https://www.codefather.cn/logo.png")
                            .build(),
                    ImageResource.builder()
                            .category(ImageCategoryEnum.LOGO)
                            .description("假数据 Logo 图片")
                            .url("https://www.codefather.cn/logo.png")
                            .build(),
                    ImageResource.builder()
                            .category(ImageCategoryEnum.ILLUSTRATION)
                            .description("假数据插画图片")
                            .url("https://www.codefather.cn/logo.png")
                            .build(),
                    ImageResource.builder()
                            .category(ImageCategoryEnum.ARCHITECTURE)
                            .description("假数据架构图片")
                            .url("https://www.codefather.cn/logo.png")
                            .build()
            );

            if (context != null) {
                context.setCurrentStep("图片收集");
                context.setImageList(imageList);
                context.setImageListStr("已收集内容图、Logo、插画图、架构图");
            }
            log.info("图片收集完成，共收集 {} 张图片", imageList.size());
            return WorkflowContext.saveContext(context);
        });
    }
}
