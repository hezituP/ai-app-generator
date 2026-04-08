package com.hezitu.heaicodemother.ai.tools;

import cn.hutool.json.JSONObject;

public abstract class BaseTool {

    public abstract String getToolName();

    public abstract String getDisplayName();

    public abstract String generateToolExecutedResult(JSONObject arguments);
}
