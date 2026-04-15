package com.hezitu.heaicodemother.core.builder;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ReUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class VueProjectBuilder {

    private static final int NPM_INSTALL_TIMEOUT_SECONDS = 300;

    private static final int NPM_BUILD_TIMEOUT_SECONDS = 180;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Map<String, String> MIN_DEPENDENCY_VERSIONS = Map.of(
            "vue", "^3.4.38",
            "@vitejs/plugin-vue", "^5.1.4",
            "vite", "^5.4.10",
            "typescript", "^5.6.3",
            "vue-tsc", "^2.1.8",
            "vue-router", "^4.4.5",
            "sass", "^1.79.5",
            "postcss", "^8.4.47",
            "autoprefixer", "^10.4.20"
    );

    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("Project directory does not exist: {}", projectPath);
            return false;
        }
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json does not exist: {}", packageJson.getAbsolutePath());
            return false;
        }

        sanitizeGeneratedFiles(projectDir);
        normalizeBuildTooling(projectDir);

        File distDir = new File(projectDir, "dist");
        if (distDir.exists()) {
            FileUtil.del(distDir);
        }

        log.info("Building Vue project: {}", projectPath);
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install failed");
            return false;
        }
        if (!executeNpmBuild(projectDir)) {
            log.warn("Initial Vue build failed, attempting bootstrap rescue");
            if (!repairBootstrapFiles(projectDir)) {
                log.error("Vue bootstrap rescue was not applied or failed");
                return false;
            }
            if (!executeNpmBuild(projectDir)) {
                log.error("npm run build failed after bootstrap rescue");
                return false;
            }
        }
        if (!distDir.exists() || !distDir.isDirectory()) {
            log.error("Build completed but dist directory is missing: {}", distDir.getAbsolutePath());
            return false;
        }
        log.info("Vue project built successfully: {}", distDir.getAbsolutePath());
        return true;
    }

    private boolean executeNpmInstall(File projectDir) {
        return executeCommand(projectDir, NPM_INSTALL_TIMEOUT_SECONDS, getNpmCommand(), "install");
    }

    private boolean executeNpmBuild(File projectDir) {
        if (executeCommand(projectDir, NPM_BUILD_TIMEOUT_SECONDS, getNpmCommand(), "run", "build", "--", "--base=./")) {
            return true;
        }
        log.warn("npm run build failed, trying direct vite build fallback");
        return executeViteBuildFallback(projectDir);
    }

    private boolean executeViteBuildFallback(File projectDir) {
        String npmCommand = getNpmCommand();
        return executeCommand(projectDir, NPM_BUILD_TIMEOUT_SECONDS, npmCommand, "exec", "--", "vite", "build", "--base=./");
    }

    private boolean executeCommand(File workingDir, int timeoutSeconds, String... commandParts) {
        Process process = null;
        try {
            String command = String.join(" ", commandParts);
            log.info("Executing in {}: {}", workingDir.getAbsolutePath(), command);
            process = new ProcessBuilder(commandParts)
                    .directory(workingDir)
                    .redirectErrorStream(false)
                    .start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Command timed out after {} seconds: {}", timeoutSeconds, command);
                return false;
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() == 0) {
                if (!stdout.isBlank()) {
                    log.info("Command stdout:\n{}", stdout);
                }
                if (!stderr.isBlank()) {
                    log.warn("Command stderr:\n{}", stderr);
                }
                return true;
            }

            log.error("Command failed with exit code {}: {}", process.exitValue(), command);
            if (!stdout.isBlank()) {
                log.error("stdout:\n{}", stdout);
            }
            if (!stderr.isBlank()) {
                log.error("stderr:\n{}", stderr);
            }
            return false;
        } catch (Exception e) {
            log.error("Execute command failed: {}", String.join(" ", commandParts), e);
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private void sanitizeGeneratedFiles(File projectDir) {
        List<File> files = FileUtil.loopFiles(projectDir, file ->
                file.isFile()
                        && !file.getAbsolutePath().contains(File.separator + "node_modules" + File.separator)
                        && !file.getAbsolutePath().contains(File.separator + "dist" + File.separator)
                        && hasTextLikeExtension(file.getName()));
        for (File file : files) {
            try {
                String content = FileUtil.readUtf8String(file);
                String sanitized = removeDanglingCodeFence(content);
                if (!content.equals(sanitized)) {
                    FileUtil.writeUtf8String(sanitized, file);
                    log.info("Sanitized generated file: {}", file.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("Sanitize generated file failed: {}", file.getAbsolutePath(), e);
            }
        }
    }

    private void normalizeBuildTooling(File projectDir) {
        try {
            File packageJsonFile = new File(projectDir, "package.json");
            if (!packageJsonFile.exists()) {
                return;
            }
            String packageJson = FileUtil.readUtf8String(packageJsonFile);
            ObjectNode root = (ObjectNode) OBJECT_MAPPER.readTree(packageJson);
            ObjectNode dependencies = getOrCreateObject(root, "dependencies");
            ObjectNode devDependencies = getOrCreateObject(root, "devDependencies");

            boolean hasScss = hasFileWithExtension(projectDir, ".scss") || hasFileWithExtension(projectDir, ".sass");
            boolean hasRouter = FileUtil.exist(new File(projectDir, "src/router/index.ts"))
                    || FileUtil.exist(new File(projectDir, "src/router/index.js"))
                    || containsInAnyTextFile(projectDir, "vue-router");
            boolean hasPostcssConfig = FileUtil.exist(new File(projectDir, "postcss.config.js"))
                    || FileUtil.exist(new File(projectDir, "postcss.config.cjs"))
                    || containsInFile(new File(projectDir, "vite.config.ts"), "postcss")
                    || containsInFile(new File(projectDir, "vite.config.js"), "postcss");

            boolean changed = false;
            changed |= ensureVersion(dependencies, "vue");
            changed |= ensureVersion(devDependencies, "@vitejs/plugin-vue");
            changed |= ensureVersion(devDependencies, "vite");
            changed |= ensureVersion(devDependencies, "typescript");
            changed |= ensureVersion(devDependencies, "vue-tsc");
            if (hasScss) {
                changed |= ensureVersion(devDependencies, "sass");
            }
            if (hasRouter) {
                changed |= ensureVersion(dependencies, "vue-router");
            }
            if (hasPostcssConfig) {
                changed |= ensureVersion(devDependencies, "postcss");
                changed |= ensureVersion(devDependencies, "autoprefixer");
                ensurePostcssConfig(projectDir);
            }

            ObjectNode scripts = getOrCreateObject(root, "scripts");
            changed |= setScriptIfNeeded(scripts, "build", "vite build");

            if (changed) {
                FileUtil.writeUtf8String(OBJECT_MAPPER.writeValueAsString(root) + System.lineSeparator(), packageJsonFile);
                log.info("Normalized build tooling dependencies: {}", packageJsonFile.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Normalize build tooling failed: {}", projectDir.getAbsolutePath(), e);
        }
    }

    private boolean hasTextLikeExtension(String fileName) {
        return fileName.endsWith(".ts")
                || fileName.endsWith(".js")
                || fileName.endsWith(".vue")
                || fileName.endsWith(".json")
                || fileName.endsWith(".html")
                || fileName.endsWith(".css")
                || fileName.endsWith(".scss")
                || fileName.endsWith(".md");
    }

    private String removeDanglingCodeFence(String content) {
        String sanitized = content == null ? "" : content;
        sanitized = sanitized.replace("`r`n", System.lineSeparator());
        sanitized = sanitized.replace("`n", System.lineSeparator());
        sanitized = sanitized.replaceAll("(?m)^```[\\w-]*\\s*$", "");
        sanitized = sanitized.replaceAll("(?m)^```\\s*$", "");
        sanitized = repairCollapsedStyleLines(sanitized);
        sanitized = sanitizeVueSingleFileComponent(sanitized);
        return sanitized.trim() + System.lineSeparator();
    }

    private String sanitizeVueSingleFileComponent(String content) {
        String trimmed = StrUtil.trim(content);
        if (StrUtil.isBlank(trimmed) || !trimmed.contains("<template")) {
            return content;
        }

        int templateStart = trimmed.indexOf("<template");
        if (templateStart > 0) {
            trimmed = trimmed.substring(templateStart);
        }

        int lastValidEnd = findLastValidVueBlockEnd(trimmed);
        if (lastValidEnd > 0 && lastValidEnd < trimmed.length()) {
            trimmed = trimmed.substring(0, lastValidEnd);
        }

        trimmed = ReUtil.replaceAll(trimmed,
                "(?s)(</style>|</script>|</template>)\\s*[\\p{IsHan}A-Za-z0-9#*].*$",
                "$1");
        return trimmed;
    }

    private int findLastValidVueBlockEnd(String content) {
        int lastTemplate = content.lastIndexOf("</template>");
        int lastScript = content.lastIndexOf("</script>");
        int lastStyle = content.lastIndexOf("</style>");
        return Math.max(lastTemplate >= 0 ? lastTemplate + "</template>".length() : -1,
                Math.max(lastScript >= 0 ? lastScript + "</script>".length() : -1,
                        lastStyle >= 0 ? lastStyle + "</style>".length() : -1));
    }

    private String repairCollapsedStyleLines(String content) {
        String repaired = content;
        repaired = repaired.replaceAll("(?m)^(\\s*//.*?)(--[\\w-]+\\s*:)", "$1\n  $2");
        repaired = repaired.replaceAll("(?m)^(\\s*//.*?)([.#][\\w-]+\\s*\\{)", "$1\n$2");
        repaired = repaired.replaceAll("(?m)^(\\s*//.*?)(::[-\\w]+\\s*\\{)", "$1\n$2");
        repaired = repaired.replaceAll("(?m)^(\\s*//.*?)(@media\\s*\\()", "$1\n$2");
        repaired = repaired.replaceAll("(?m)^\\s+([.#][\\w-]+\\s*\\{)", "$1");
        repaired = repaired.replaceAll("(?m)^\\s+(::[-\\w]+\\s*\\{)", "$1");
        repaired = repaired.replaceAll("(?m)^\\s+(@media\\s*\\()", "$1");
        repaired = repaired.replaceAll("(?m)^max-width:\\s*([^\\n]+\\{)", "@media (max-width: $1");
        repaired = repaired.replaceAll("(?m)^\\s*:root\\s*\\{", ":root {");
        return repaired;
    }

    private void ensurePostcssConfig(File projectDir) {
        File postcssConfig = new File(projectDir, "postcss.config.js");
        if (postcssConfig.exists()) {
            return;
        }
        FileUtil.writeUtf8String("""
                export default {
                  plugins: {
                    autoprefixer: {}
                  }
                }
                """, postcssConfig);
        log.info("Created missing PostCSS config: {}", postcssConfig.getAbsolutePath());
    }

    private boolean ensureVersion(ObjectNode dependencies, String dependencyName) {
        String targetVersion = MIN_DEPENDENCY_VERSIONS.get(dependencyName);
        if (targetVersion == null) {
            return false;
        }
        String currentVersion = dependencies.path(dependencyName).asText(null);
        if (StrUtil.equals(currentVersion, targetVersion)) {
            return false;
        }
        dependencies.put(dependencyName, targetVersion);
        return true;
    }

    private boolean setScriptIfNeeded(ObjectNode scripts, String scriptName, String targetCommand) {
        String currentCommand = scripts.path(scriptName).asText(null);
        if (StrUtil.equals(currentCommand, targetCommand)) {
            return false;
        }
        scripts.put(scriptName, targetCommand);
        return true;
    }

    private boolean repairBootstrapFiles(File projectDir) {
        boolean changed = false;
        changed |= repairFileIfInvalid(projectDir, "vite.config.ts", this::isValidViteConfig, defaultViteConfigTs());
        changed |= repairFileIfInvalid(projectDir, "vite.config.js", this::isValidViteConfig, defaultViteConfigJs());
        changed |= repairFileIfInvalid(projectDir, "src/styles/_variables.scss", this::isValidVariablesScss, defaultVariablesScss());
        changed |= repairFileIfInvalid(projectDir, "src/styles/main.scss", this::isValidMainScss, defaultMainScss());

        boolean routerExists = new File(projectDir, "src/router/index.ts").exists()
                || new File(projectDir, "src/router/index.js").exists();
        if (routerExists) {
            changed |= repairFileIfInvalid(projectDir, "src/router/index.ts", this::isValidRouterFile, defaultRouterTs());
            changed |= repairFileIfInvalid(projectDir, "src/router/index.js", this::isValidRouterFile, defaultRouterJs());
            changed |= repairFileIfInvalid(projectDir, "src/App.vue", this::isValidAppVue, defaultAppVueWithRouter());
            changed |= repairFileIfInvalid(projectDir, "src/main.ts", this::isValidMainEntry, defaultMainTsWithRouter());
            changed |= repairFileIfInvalid(projectDir, "src/main.js", this::isValidMainEntry, defaultMainJsWithRouter());
        } else {
            changed |= repairFileIfInvalid(projectDir, "src/main.ts", this::isValidMainEntry, defaultMainTsWithoutRouter());
            changed |= repairFileIfInvalid(projectDir, "src/main.js", this::isValidMainEntry, defaultMainJsWithoutRouter());
        }
        return changed;
    }

    private boolean repairFileIfInvalid(File projectDir, String relativePath, FileContentValidator validator, String fallbackContent) {
        File file = new File(projectDir, relativePath);
        if (!file.exists()) {
            return false;
        }
        String content = FileUtil.readUtf8String(file);
        if (validator.isValid(content)) {
            return false;
        }
        FileUtil.writeUtf8String(fallbackContent, file);
        log.warn("Replaced invalid bootstrap file with fallback: {}", file.getAbsolutePath());
        return true;
    }

    private boolean writeFile(File projectDir, String relativePath, String content) {
        File file = new File(projectDir, relativePath);
        String existing = file.exists() ? FileUtil.readUtf8String(file) : null;
        if (StrUtil.equals(existing, content)) {
            return false;
        }
        FileUtil.writeUtf8String(content, file);
        log.warn("Wrote fallback bootstrap file: {}", file.getAbsolutePath());
        return true;
    }

    private boolean isValidOptionalFile(File file, FileContentValidator validator) {
        return file.exists() && validator.isValid(FileUtil.readUtf8String(file));
    }

    private boolean isValidViteConfig(String content) {
        return containsAll(content, "defineConfig", "plugins", "base")
                && !containsSuspiciousBrokenQuote(content);
    }

    private boolean isValidMainEntry(String content) {
        return containsAll(content, "createApp", "mount('#app')")
                && !containsSuspiciousBrokenQuote(content);
    }

    private boolean isValidRouterFile(String content) {
        return containsAll(content, "createRouter", "routes")
                && !containsSuspiciousBrokenQuote(content);
    }

    private boolean isValidAppVue(String content) {
        return containsAll(content, "<template>", "</template>")
                && !containsSuspiciousBrokenQuote(content);
    }

    private boolean isValidVariablesScss(String content) {
        return containsAll(content, ":root", "--color-primary", "--gradient-primary")
                && !containsBrokenScssToken(content);
    }

    private boolean isValidMainScss(String content) {
        return containsAll(content, "@use", ".container", "@media (max-width: 768px)")
                && !containsBrokenScssToken(content);
    }

    private boolean containsBrokenScssToken(String content) {
        return containsSuspiciousBrokenQuote(content)
                || content.contains("//") && content.contains(":root {")
                || content.contains("max-width: 768px) {")
                || content.contains("\n\n  width: 100%;")
                || content.contains("\n\n  opacity: 0;")
                || content.contains("\n\n  width: 8px;")
                || content.contains("\n   linear-gradient(")
                || content.contains("\n   0 2px 4px")
                || content.contains("\n   0.5rem;")
                || content.contains("\n   4px;")
                || content.contains("\n   0.2s ease;")
                || content.contains("\n   -1;");
    }

    private boolean containsSuspiciousBrokenQuote(String content) {
        String[] lines = StrUtil.nullToEmpty(content).split("\\R");
        for (String line : lines) {
            if ((line.contains("title: '") || line.contains("text: '") || line.contains("description: '")
                    || line.contains("location: '") || line.contains("duration: '") || line.contains("season: '")
                    || line.contains("fullDescription: '") || line.contains("features: ['"))
                    && !line.trim().endsWith("'")
                    && !line.trim().endsWith("',")
                    && !line.trim().endsWith("'],")) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAll(String content, String... markers) {
        if (content == null) {
            return false;
        }
        for (String marker : markers) {
            if (!content.contains(marker)) {
                return false;
            }
        }
        return true;
    }

    private ObjectNode getOrCreateObject(ObjectNode root, String fieldName) {
        if (root.has(fieldName) && root.get(fieldName).isObject()) {
            return (ObjectNode) root.get(fieldName);
        }
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        root.set(fieldName, node);
        return node;
    }

    private boolean putIfMissing(ObjectNode objectNode, String key, String value) {
        if (objectNode.hasNonNull(key)) {
            return false;
        }
        objectNode.put(key, value);
        return true;
    }

    private boolean hasFileWithExtension(File projectDir, String extension) {
        return !FileUtil.loopFiles(projectDir, file -> file.isFile() && file.getName().endsWith(extension)).isEmpty();
    }

    private boolean containsInAnyTextFile(File projectDir, String marker) {
        List<File> files = FileUtil.loopFiles(projectDir, file ->
                file.isFile()
                        && !file.getAbsolutePath().contains(File.separator + "node_modules" + File.separator)
                        && !file.getAbsolutePath().contains(File.separator + "dist" + File.separator)
                        && hasTextLikeExtension(file.getName()));
        for (File file : files) {
            if (containsInFile(file, marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsInFile(File file, String marker) {
        return file.exists() && FileUtil.readUtf8String(file).contains(marker);
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

    private String defaultViteConfigJs() {
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

    private String defaultMainTsWithRouter() {
        return """
                import { createApp } from 'vue'
                import App from './App.vue'
                import router from './router'
                import './styles/main.scss'

                createApp(App).use(router).mount('#app')
                """;
    }

    private String defaultMainJsWithRouter() {
        return """
                import { createApp } from 'vue'
                import App from './App.vue'
                import router from './router'
                import './styles/main.scss'

                createApp(App).use(router).mount('#app')
                """;
    }

    private String defaultMainTsWithoutRouter() {
        return """
                import { createApp } from 'vue'
                import App from './App.vue'
                import './styles/main.scss'

                createApp(App).mount('#app')
                """;
    }

    private String defaultMainJsWithoutRouter() {
        return """
                import { createApp } from 'vue'
                import App from './App.vue'
                import './styles/main.scss'

                createApp(App).mount('#app')
                """;
    }

    private String defaultRouterTs() {
        return """
                import { createRouter, createWebHashHistory } from 'vue-router'
                import App from '../App.vue'

                export default createRouter({
                  history: createWebHashHistory(),
                  routes: [
                    {
                      path: '/',
                      name: 'home',
                      component: App,
                      meta: {
                        title: 'Generated App'
                      }
                    }
                  ]
                })
                """;
    }

    private String defaultRouterJs() {
        return """
                import { createRouter, createWebHashHistory } from 'vue-router'
                import App from '../App.vue'

                export default createRouter({
                  history: createWebHashHistory(),
                  routes: [
                    {
                      path: '/',
                      name: 'home',
                      component: App,
                      meta: {
                        title: 'Generated App'
                      }
                    }
                  ]
                })
                """;
    }

    private String defaultVariablesScss() {
        return """
                :root {
                  --color-primary: #667eea;
                  --color-primary-dark: #5a67d8;
                  --color-secondary: #764ba2;
                  --color-accent: #f093fb;
                  --color-background: #ffffff;
                  --color-surface: #f8fafc;
                  --color-text: #1a202c;
                  --color-text-light: #4a5568;
                  --color-border: #e2e8f0;
                  --gradient-primary: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
                  --gradient-accent: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
                  --gradient-dark: linear-gradient(135deg, #1a202c 0%, #2d3748 100%);
                  --shadow-sm: 0 2px 4px rgba(0, 0, 0, 0.05);
                  --shadow-md: 0 4px 16px rgba(0, 0, 0, 0.1);
                  --shadow-lg: 0 8px 32px rgba(0, 0, 0, 0.15);
                  --shadow-xl: 0 16px 48px rgba(0, 0, 0, 0.2);
                  --spacing-xs: 0.5rem;
                  --spacing-sm: 1rem;
                  --spacing-md: 1.5rem;
                  --spacing-lg: 2rem;
                  --spacing-xl: 4rem;
                  --spacing-xxl: 8rem;
                  --radius-sm: 4px;
                  --radius-md: 8px;
                  --radius-lg: 16px;
                  --radius-xl: 32px;
                  --radius-full: 9999px;
                  --transition-fast: 0.2s ease;
                  --transition-normal: 0.3s ease;
                  --transition-slow: 0.5s ease;
                  --transition-bounce: 0.5s cubic-bezier(0.68, -0.55, 0.265, 1.55);
                  --z-base: -1;
                  --z-elevate: 1;
                  --z-dropdown: 10;
                  --z-sticky: 100;
                  --z-fixed: 1000;
                  --z-modal: 2000;
                  --z-popover: 3000;
                  --z-tooltip: 4000;
                }
                """;
    }

    private String defaultMainScss() {
        return """
                @use './variables' as *;

                *,
                *::before,
                *::after {
                  margin: 0;
                  padding: 0;
                  box-sizing: border-box;
                }

                :root {
                  --app-height: 100vh;
                }

                html {
                  font-size: 16px;
                  scroll-behavior: smooth;
                  overflow-x: hidden;
                  height: 100%;
                }

                body {
                  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                  line-height: 1.6;
                  color: var(--color-text);
                  background: var(--color-background);
                  min-height: 100vh;
                  min-height: var(--app-height);
                  overflow-x: hidden;
                }

                #app {
                  min-height: 100vh;
                  min-height: var(--app-height);
                  position: relative;
                }

                .container {
                  width: 100%;
                  max-width: 1400px;
                  margin: 0 auto;
                  padding: 0 2rem;
                }

                .section {
                  padding: var(--spacing-xxl) 0;
                }

                .text-serif {
                  font-family: 'Cormorant Garamond', serif;
                }

                .text-gradient {
                  background: var(--gradient-primary);
                  -webkit-background-clip: text;
                  background-clip: text;
                  color: transparent;
                }

                .fade-up {
                  opacity: 0;
                  transform: translateY(30px);
                  transition: opacity 0.8s ease, transform 0.8s ease;
                }

                .fade-up.visible {
                  opacity: 1;
                  transform: translateY(0);
                }

                .parallax {
                  will-change: transform;
                }

                ::-webkit-scrollbar {
                  width: 8px;
                }

                ::-webkit-scrollbar-track {
                  background: rgba(0, 0, 0, 0.05);
                }

                ::-webkit-scrollbar-thumb {
                  background: rgba(0, 0, 0, 0.2);
                  border-radius: 4px;
                }

                ::-webkit-scrollbar-thumb:hover {
                  background: rgba(0, 0, 0, 0.3);
                }

                @media (max-width: 768px) {
                  html {
                    font-size: 14px;
                  }

                  .container {
                    padding: 0 1rem;
                  }

                  .section {
                    padding: var(--spacing-xl) 0;
                  }
                }
                """;
    }

    private String defaultAppVueWithRouter() {
        return """
                <template>
                  <router-view />
                </template>
                """;
    }

    @FunctionalInterface
    private interface FileContentValidator {
        boolean isValid(String content);
    }

    private String getNpmCommand() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("win") ? "npm.cmd" : "npm";
    }
}
