package com.hezitu.heaicodemother.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface CustomerSupportService {

    @SystemMessage(fromResource = "prompt/customer-support-system-prompt.txt")
    String reply(@UserMessage String userMessage);
}
