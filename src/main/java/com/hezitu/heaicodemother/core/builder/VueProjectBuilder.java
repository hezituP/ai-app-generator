package com.hezitu.heaicodemother.core.builder;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Vue 项目构建器
 */
@Component
@Slf4j
public class VueProjectBuilder {

    private static final int NPM_INSTALL_TIMEOUT_SECONDS = 300;

    private static final int NPM_BUILD_TIMEOUT_SECONDS = 180;

    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return false;
        }
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return false;
        }
        File distDir = new File(projectDir, "dist");
        if (distDir.exists()) {
            FileUtil.del(distDir);
            log.info("已清理旧 dist 目录: {}", distDir.getAbsolutePath());
        }
        log.info("开始构建 Vue 项目: {}", projectPath);
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install 执行失败");
            return false;
        }
        if (!executeNpmBuild(projectDir)) {
            log.error("npm run build 执行失败");
            return false;
        }
        if (!distDir.exists() || !distDir.isDirectory()) {
            log.error("构建完成但 dist 目录未生成: {}", distDir.getAbsolutePath());
            return false;
        }
        log.info("Vue 项目构建成功，dist 目录: {}", distDir.getAbsolutePath());
        return true;
    }

    private boolean executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        return executeCommand(projectDir, NPM_INSTALL_TIMEOUT_SECONDS, getNpmCommand(), "install");
    }

    private boolean executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        return executeCommand(projectDir, NPM_BUILD_TIMEOUT_SECONDS, getNpmCommand(), "run", "build");
    }

    private boolean executeCommand(File workingDir, int timeoutSeconds, String... commandParts) {
        try {
            String command = String.join(" ", commandParts);
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            Process process = RuntimeUtil.exec(null, workingDir, commandParts);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return false;
            }
            String result = RuntimeUtil.getResult(process, StandardCharsets.UTF_8);
            if (process.exitValue() == 0) {
                log.info("命令执行成功: {}", command);
                if (result != null && !result.isBlank()) {
                    log.info("命令输出: {}", result);
                }
                return true;
            }
            log.error("命令执行失败，退出码: {}，输出: {}", process.exitValue(), result);
            return false;
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", String.join(" ", commandParts), e.getMessage(), e);
            return false;
        }
    }

    private String getNpmCommand() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("win") ? "npm.cmd" : "npm";
    }
}
