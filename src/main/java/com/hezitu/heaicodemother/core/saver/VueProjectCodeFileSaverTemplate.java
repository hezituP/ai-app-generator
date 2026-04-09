package com.hezitu.heaicodemother.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hezitu.heaicodemother.ai.model.VueProjectCodeResult;
import com.hezitu.heaicodemother.exception.BusinessException;
import com.hezitu.heaicodemother.exception.ErrorCode;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;

import java.util.LinkedHashMap;
import java.util.Map;

public class VueProjectCodeFileSaverTemplate extends CodeFileSaverTemplate<VueProjectCodeResult> {

    @Override
    protected void saveFiles(VueProjectCodeResult result, String baseDirPath) {
        Map<String, String> normalizedFiles = normalizeFiles(result);
        boolean projectAlreadyExists = isProjectInitialized(baseDirPath);
        if (!projectAlreadyExists) {
            ensureBootstrapFiles(normalizedFiles);
        }
        normalizedFiles.forEach((path, content) -> writeToFile(baseDirPath, path, content));
    }

    @Override
    protected void validateInput(VueProjectCodeResult result, String baseDirPath) {
        super.validateInput(result, baseDirPath);
        if (result.getFiles() == null || result.getFiles().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 工程文件不能为空");
        }
    }

    @Override
    protected boolean shouldResetDirectory() {
        return false;
    }

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.VUE_PROJECT;
    }

    private Map<String, String> normalizeFiles(VueProjectCodeResult result) {
        Map<String, String> files = new LinkedHashMap<>();
        for (VueProjectCodeResult.ProjectFile file : result.getFiles()) {
            if (file == null || StrUtil.isBlank(file.getPath())) {
                continue;
            }
            String path = normalizePath(file.getPath());
            if (StrUtil.isBlank(path)) {
                continue;
            }
            files.put(path, StrUtil.nullToEmpty(file.getContent()));
        }
        return files;
    }

    private void ensureBootstrapFiles(Map<String, String> files) {
        String appVue = resolveAppVue(files);
        files.putIfAbsent("package.json", defaultPackageJson());
        files.putIfAbsent("index.html", defaultIndexHtml());
        if (!files.containsKey("vite.config.ts") && !files.containsKey("vite.config.js")) {
            files.put("vite.config.ts", defaultViteConfigTs());
        }
        if (!files.containsKey("src/main.ts") && !files.containsKey("src/main.js")) {
            files.put("src/main.ts", defaultMainTs());
        }
        files.putIfAbsent("src/App.vue", appVue);
    }

    private boolean isProjectInitialized(String baseDirPath) {
        return FileUtil.exist(baseDirPath + "/package.json")
                || FileUtil.exist(baseDirPath + "/src/main.ts")
                || FileUtil.exist(baseDirPath + "/src/main.js")
                || FileUtil.exist(baseDirPath + "/src/App.vue");
    }

    private String resolveAppVue(Map<String, String> files) {
        String appVue = files.get("src/App.vue");
        if (StrUtil.isNotBlank(appVue)) {
            return appVue;
        }
        for (Map.Entry<String, String> entry : files.entrySet()) {
            if (entry.getKey().endsWith(".vue") && StrUtil.isNotBlank(entry.getValue())) {
                return entry.getValue();
            }
        }
        return defaultAppVue();
    }

    private String normalizePath(String rawPath) {
        String path = StrUtil.trim(rawPath);
        path = StrUtil.removePrefix(path, "./");
        path = StrUtil.removePrefix(path, "/");
        path = path.replace("\\", "/");
        return path;
    }

    private String defaultPackageJson() {
        return """
                {
                  "name": "generated-vue-app",
                  "private": true,
                  "version": "0.0.0",
                  "type": "module",
                  "scripts": {
                    "dev": "vite",
                    "build": "vite build"
                  },
                  "dependencies": {
                    "vue": "^3.5.13",
                    "vue-router": "^4.5.1"
                  },
                  "devDependencies": {
                    "@vitejs/plugin-vue": "^5.2.1",
                    "typescript": "^5.7.2",
                    "vite": "^6.0.5"
                  }
                }
                """;
    }

    private String defaultIndexHtml() {
        return """
                <!doctype html>
                <html lang="zh-CN">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>生成应用</title>
                  </head>
                  <body>
                    <div id="app"></div>
                    <script type="module" src="./src/main.ts"></script>
                  </body>
                </html>
                """;
    }

    private String defaultViteConfigTs() {
        return """
                import { defineConfig } from 'vite'
                import vue from '@vitejs/plugin-vue'
                import { fileURLToPath, URL } from 'node:url'

                export default defineConfig({
                  base: './',
                  plugins: [vue()],
                  resolve: {
                    alias: {
                      '@': fileURLToPath(new URL('./src', import.meta.url))
                    }
                  }
                })
                """;
    }

    private String defaultMainTs() {
        return """
                import { createApp } from 'vue'
                import App from './App.vue'

                createApp(App).mount('#app')
                """;
    }

    private String defaultAppVue() {
        return """
                <template>
                  <main class="page">
                    <section class="card">
                      <h1>应用已生成</h1>
                      <p>当前返回结果缺少完整入口文件，我已自动补齐最小可运行工程。</p>
                    </section>
                  </main>
                </template>

                <style scoped>
                .page {
                  min-height: 100vh;
                  display: grid;
                  place-items: center;
                  background: linear-gradient(135deg, #f6f7fb, #e9eefc);
                  font-family: "Microsoft YaHei", sans-serif;
                }

                .card {
                  padding: 32px;
                  border-radius: 20px;
                  background: #ffffff;
                  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.12);
                  text-align: center;
                }
                </style>
                """;
    }
}
