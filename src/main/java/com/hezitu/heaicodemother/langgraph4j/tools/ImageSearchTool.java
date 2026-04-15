package com.hezitu.heaicodemother.langgraph4j.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hezitu.heaicodemother.langgraph4j.model.ImageCategoryEnum;
import com.hezitu.heaicodemother.langgraph4j.model.ImageResource;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ImageSearchTool {

    private static final String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    private static final int REQUEST_TIMEOUT_MILLIS = 8000;

    @Value("${pexels.api-key:}")
    private String pexelsApiKey;

    @Tool("Search for website content images by keyword")
    public List<ImageResource> searchContentImages(@P("Search keyword") String query) {
        List<ImageResource> imageList = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return imageList;
        }
        if (pexelsApiKey == null || pexelsApiKey.isBlank()) {
            log.warn("pexels.api-key is not configured, skip image search");
            return imageList;
        }
        try (HttpResponse response = HttpRequest.get(PEXELS_API_URL)
                .timeout(REQUEST_TIMEOUT_MILLIS)
                .header("Authorization", pexelsApiKey)
                .form("query", query)
                .form("per_page", 8)
                .form("page", 1)
                .execute()) {
            if (!response.isOk()) {
                log.warn("Pexels request failed, status={}", response.getStatus());
                return imageList;
            }
            JSONObject result = JSONUtil.parseObj(response.body());
            JSONArray photos = result.getJSONArray("photos");
            for (int i = 0; i < photos.size(); i++) {
                JSONObject photo = photos.getJSONObject(i);
                JSONObject src = photo.getJSONObject("src");
                imageList.add(ImageResource.builder()
                        .category(ImageCategoryEnum.CONTENT)
                        .description(photo.getStr("alt", query))
                        .url(src.getStr("medium"))
                        .build());
            }
        } catch (Exception e) {
            log.warn("Image search failed: {}", e.getMessage());
        }
        return imageList;
    }
}
