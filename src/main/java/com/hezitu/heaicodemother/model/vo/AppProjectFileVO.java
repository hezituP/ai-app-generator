package com.hezitu.heaicodemother.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppProjectFileVO implements Serializable {

    private String path;

    private String content;

    private static final long serialVersionUID = 1L;
}
