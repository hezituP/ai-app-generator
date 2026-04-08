package com.hezitu.heaicodemother.core.saver;

import cn.hutool.core.util.StrUtil;
import com.hezitu.heaicodemother.ai.model.VueProjectCodeResult;
import com.hezitu.heaicodemother.exception.BusinessException;
import com.hezitu.heaicodemother.exception.ErrorCode;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;

public class VueProjectCodeFileSaverTemplate extends CodeFileSaverTemplate<VueProjectCodeResult> {

    @Override
    protected void saveFiles(VueProjectCodeResult result, String baseDirPath) {
        for (VueProjectCodeResult.ProjectFile file : result.getFiles()) {
            writeToFile(baseDirPath, file.getPath(), file.getContent());
        }
    }

    @Override
    protected void validateInput(VueProjectCodeResult result) {
        super.validateInput(result);
        if (result.getFiles() == null || result.getFiles().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 工程文件不能为空");
        }
        boolean hasPackageJson = result.getFiles().stream()
                .anyMatch(file -> StrUtil.equals(file.getPath(), "package.json"));
        boolean hasMainEntry = result.getFiles().stream()
                .anyMatch(file -> StrUtil.equalsAny(file.getPath(), "src/main.ts", "src/main.js"));
        if (!hasPackageJson || !hasMainEntry) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 工程缺少必要入口文件");
        }
    }

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.VUE_PROJECT;
    }
}
