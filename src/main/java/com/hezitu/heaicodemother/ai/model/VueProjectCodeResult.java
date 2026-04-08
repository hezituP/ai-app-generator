package com.hezitu.heaicodemother.ai.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VueProjectCodeResult {

    private String summary;

    private List<ProjectFile> files = new ArrayList<>();

    @Data
    public static class ProjectFile {
        private String path;
        private String content;
    }
}
