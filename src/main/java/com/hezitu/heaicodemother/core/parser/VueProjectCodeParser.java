package com.hezitu.heaicodemother.core.parser;

import cn.hutool.core.util.StrUtil;
import com.hezitu.heaicodemother.ai.model.VueProjectCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VueProjectCodeParser implements CodeParser<VueProjectCodeResult> {

    private static final Pattern FILE_PATTERN = Pattern.compile(
            "<<FILE_START:(.*?)>>\\s*```[\\w-]*\\s*\\n([\\s\\S]*?)```\\s*<<FILE_END>>",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public VueProjectCodeResult parseCode(String codeContent) {
        VueProjectCodeResult result = new VueProjectCodeResult();
        Matcher matcher = FILE_PATTERN.matcher(codeContent);
        while (matcher.find()) {
            String path = StrUtil.trim(matcher.group(1));
            if (StrUtil.isBlank(path)) {
                continue;
            }
            VueProjectCodeResult.ProjectFile file = new VueProjectCodeResult.ProjectFile();
            file.setPath(path);
            file.setContent(StrUtil.nullToDefault(matcher.group(2), "").strip());
            result.getFiles().add(file);
        }
        int markerIndex = codeContent.indexOf("<<FILE_START:");
        if (markerIndex > 0) {
            result.setSummary(codeContent.substring(0, markerIndex).trim());
        }
        return result;
    }
}
