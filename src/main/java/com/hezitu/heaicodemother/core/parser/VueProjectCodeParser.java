package com.hezitu.heaicodemother.core.parser;

import cn.hutool.core.util.StrUtil;
import com.hezitu.heaicodemother.ai.model.VueProjectCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VueProjectCodeParser implements CodeParser<VueProjectCodeResult> {

    private static final Pattern FILE_BLOCK_PATTERN = Pattern.compile(
            "<<FILE_START:(.*?)>>\\s*([\\s\\S]*?)(?=(<<FILE_START:|\\z))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile(
            "^```[\\w-]*\\s*\\R([\\s\\S]*?)\\R```\\s*(?:<<FILE_END>>)?\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public VueProjectCodeResult parseCode(String codeContent) {
        VueProjectCodeResult result = new VueProjectCodeResult();
        Matcher matcher = FILE_BLOCK_PATTERN.matcher(StrUtil.nullToEmpty(codeContent));
        while (matcher.find()) {
            String path = normalizePath(matcher.group(1));
            if (StrUtil.isBlank(path)) {
                continue;
            }
            String rawBody = StrUtil.nullToEmpty(matcher.group(2)).trim();
            String content = extractFileContent(rawBody);
            VueProjectCodeResult.ProjectFile file = new VueProjectCodeResult.ProjectFile();
            file.setPath(path);
            file.setContent(content);
            result.getFiles().add(file);
        }
        int markerIndex = codeContent.indexOf("<<FILE_START:");
        if (markerIndex > 0) {
            result.setSummary(codeContent.substring(0, markerIndex).trim());
        }
        return result;
    }

    private String extractFileContent(String rawBody) {
        String cleaned = rawBody.replace("<<FILE_END>>", "").trim();
        Matcher codeFenceMatcher = CODE_FENCE_PATTERN.matcher(cleaned);
        if (codeFenceMatcher.find()) {
            return stripDanglingFence(StrUtil.nullToEmpty(codeFenceMatcher.group(1)).strip());
        }
        return stripDanglingFence(cleaned);
    }

    private String stripDanglingFence(String content) {
        String sanitized = StrUtil.nullToEmpty(content);
        sanitized = sanitized.replaceAll("(?m)^```[\\w-]*\\s*$", "");
        sanitized = sanitized.replaceAll("(?m)^```\\s*$", "");
        return sanitized.strip();
    }

    private String normalizePath(String rawPath) {
        String path = StrUtil.trim(rawPath);
        path = StrUtil.removeSuffix(path, ">>");
        path = StrUtil.removePrefix(path, "./");
        path = StrUtil.removePrefix(path, "/");
        return path.replace("\\", "/");
    }
}
