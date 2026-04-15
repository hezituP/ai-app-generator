package com.hezitu.heaicodemother.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.hezitu.heaicodemother.langgraph4j.ai.ImageCollectionPlanService;
import com.hezitu.heaicodemother.langgraph4j.model.ImageCollectionPlan;
import com.hezitu.heaicodemother.langgraph4j.model.ImageResource;
import com.hezitu.heaicodemother.langgraph4j.state.WorkflowContext;
import com.hezitu.heaicodemother.langgraph4j.tools.ImageSearchTool;
import com.hezitu.heaicodemother.langgraph4j.tools.LogoGeneratorTool;
import com.hezitu.heaicodemother.langgraph4j.tools.UndrawIllustrationTool;
import com.hezitu.heaicodemother.model.vo.AgentStreamEvent;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public final class ImageCollectorNode {

    private static final int IMAGE_PLAN_TIMEOUT_SECONDS = 4;
    private static final int CONTENT_TASK_TIMEOUT_SECONDS = 8;
    private static final int ILLUSTRATION_TASK_TIMEOUT_SECONDS = 8;
    private static final int LOGO_TASK_TIMEOUT_SECONDS = 20;

    private ImageCollectorNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create(ImageCollectionPlanService imageCollectionPlanService,
                                                                ImageSearchTool imageSearchTool,
                                                                UndrawIllustrationTool undrawIllustrationTool,
                                                                LogoGeneratorTool logoGeneratorTool) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            Consumer<AgentStreamEvent> publisher = context.getEventPublisher();
            context.setCurrentStep("image_collection");
            context.setImageListStr("");
            context.setImageList(List.of());

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                CompletableFuture<ImageCollectionPlan> planFuture = CompletableFuture.supplyAsync(
                        () -> imageCollectionPlanService.planImageCollection(context.getOriginalPrompt()),
                        executor
                );
                ImageCollectionPlan plan = planFuture.get(IMAGE_PLAN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                context.setImageCollectionPlan(plan);
                List<ImageCollectionPlan.ImageSearchTask> contentTasks =
                        plan == null ? List.of() : CollUtil.defaultIfEmpty(plan.getContentImageTasks(), List.of());
                List<ImageCollectionPlan.ImageSearchTask> illustrationTasks =
                        plan == null ? List.of() : CollUtil.defaultIfEmpty(plan.getIllustrationTasks(), List.of());
                List<ImageCollectionPlan.LogoTask> logoTasks =
                        plan == null ? List.of() : CollUtil.defaultIfEmpty(plan.getLogoTasks(), List.of());

                if (CollUtil.isEmpty(contentTasks) && CollUtil.isEmpty(illustrationTasks) && CollUtil.isEmpty(logoTasks)) {
                    if (publisher != null) {
                        publisher.accept(AgentStreamEvent.status("image-search-skipped"));
                    }
                    return WorkflowContext.saveContext(context);
                }

                List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
                futures.addAll(createContentSearchFutures(contentTasks, imageSearchTool, executor));
                futures.addAll(createIllustrationFutures(illustrationTasks, undrawIllustrationTool, executor));
                futures.addAll(createLogoFutures(logoTasks, logoGeneratorTool, executor));

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(LOGO_TASK_TIMEOUT_SECONDS + 2L, TimeUnit.SECONDS);

                List<ImageResource> collectedImages = new ArrayList<>();
                for (CompletableFuture<List<ImageResource>> future : futures) {
                    List<ImageResource> images = future.getNow(List.of());
                    if (CollUtil.isNotEmpty(images)) {
                        collectedImages.addAll(images);
                    }
                }

                context.setImageList(collectedImages);
                context.setImageListStr(buildImageListStr(collectedImages));
                if (CollUtil.isEmpty(collectedImages) && publisher != null) {
                    publisher.accept(AgentStreamEvent.status("image-search-skipped"));
                }
            } catch (java.util.concurrent.TimeoutException e) {
                log.warn("Image collection planning timed out for appId={}", context.getAppId());
                if (publisher != null) {
                    publisher.accept(AgentStreamEvent.status("image-search-timeout"));
                }
            } catch (Exception e) {
                log.warn("Image collection failed: {}", e.getMessage(), e);
                if (publisher != null) {
                    publisher.accept(AgentStreamEvent.status("image-search-skipped"));
                }
            }
            return WorkflowContext.saveContext(context);
        });
    }

    private static List<CompletableFuture<List<ImageResource>>> createContentSearchFutures(
            List<ImageCollectionPlan.ImageSearchTask> tasks,
            ImageSearchTool imageSearchTool,
            java.util.concurrent.ExecutorService executor) {
        return tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(
                                () -> imageSearchTool.searchContentImages(task.query()),
                                executor
                        )
                        .completeOnTimeout(List.of(), CONTENT_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .exceptionally(error -> {
                            log.warn("Content image search failed for query={}: {}", task.query(), error.getMessage());
                            return List.of();
                        }))
                .toList();
    }

    private static List<CompletableFuture<List<ImageResource>>> createIllustrationFutures(
            List<ImageCollectionPlan.ImageSearchTask> tasks,
            UndrawIllustrationTool undrawIllustrationTool,
            java.util.concurrent.ExecutorService executor) {
        return tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(
                                () -> undrawIllustrationTool.searchIllustrations(task.query()),
                                executor
                        )
                        .completeOnTimeout(List.of(), ILLUSTRATION_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .exceptionally(error -> {
                            log.warn("Illustration search failed for query={}: {}", task.query(), error.getMessage());
                            return List.of();
                        }))
                .toList();
    }

    private static List<CompletableFuture<List<ImageResource>>> createLogoFutures(
            List<ImageCollectionPlan.LogoTask> tasks,
            LogoGeneratorTool logoGeneratorTool,
            java.util.concurrent.ExecutorService executor) {
        return tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(
                                () -> logoGeneratorTool.generateLogos(task.description()),
                                executor
                        )
                        .completeOnTimeout(List.of(), LOGO_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .exceptionally(error -> {
                            log.warn("Logo generation failed for description={}: {}", task.description(), error.getMessage());
                            return List.of();
                        }))
                .toList();
    }

    private static String buildImageListStr(List<ImageResource> imageList) {
        if (CollUtil.isEmpty(imageList)) {
            return "";
        }
        return imageList.stream()
                .filter(image -> StrUtil.isNotBlank(image.getUrl()))
                .map(image -> "- [" + image.getCategory().getText() + "] "
                        + StrUtil.blankToDefault(image.getDescription(), "image") + ": " + image.getUrl())
                .collect(Collectors.joining("\n"));
    }
}
