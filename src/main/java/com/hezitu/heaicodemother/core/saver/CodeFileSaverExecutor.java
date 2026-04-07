package com.hezitu.heaicodemother.core.saver;

import com.hezitu.heaicodemother.ai.model.HtmlCodeResult;
import com.hezitu.heaicodemother.ai.model.MultiFileCodeResult;
import com.hezitu.heaicodemother.ai.model.VueProjectCodeResult;
import com.hezitu.heaicodemother.exception.BusinessException;
import com.hezitu.heaicodemother.exception.ErrorCode;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;

import java.io.File;

public class CodeFileSaverExecutor {

    private static final HtmlCodeFileSaverTemplate HTML_CODE_FILE_SAVER = new HtmlCodeFileSaverTemplate();

    private static final MultiFileCodeFileSaverTemplate MULTI_FILE_CODE_FILE_SAVER = new MultiFileCodeFileSaverTemplate();

    private static final VueProjectCodeFileSaverTemplate VUE_PROJECT_CODE_FILE_SAVER = new VueProjectCodeFileSaverTemplate();

    public static File executeSaver(Object codeResult, CodeGenTypeEnum codeGenType, Long appId) {
        return switch (codeGenType) {
            case HTML -> HTML_CODE_FILE_SAVER.saveCode((HtmlCodeResult) codeResult, appId);
            case MULTI_FILE -> MULTI_FILE_CODE_FILE_SAVER.saveCode((MultiFileCodeResult) codeResult, appId);
            case VUE_PROJECT -> VUE_PROJECT_CODE_FILE_SAVER.saveCode((VueProjectCodeResult) codeResult, appId);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };
    }
}
