package com.hezitu.heaicodemother.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum AppChatIntentEnum {

    CODE_GEN("Generate or modify project code", "code_gen"),
    CUSTOMER_REPLY("Answer customer questions without changing code", "customer_reply");

    private final String text;

    private final String value;

    AppChatIntentEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static AppChatIntentEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (AppChatIntentEnum anEnum : values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
