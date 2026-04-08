package com.hezitu.heaicodemother.langgraph4j.model;

import lombok.Getter;

@Getter
public enum ImageCategoryEnum {

    CONTENT("content"),
    LOGO("logo"),
    ILLUSTRATION("illustration"),
    ARCHITECTURE("architecture");

    private final String text;

    ImageCategoryEnum(String text) {
        this.text = text;
    }
}
