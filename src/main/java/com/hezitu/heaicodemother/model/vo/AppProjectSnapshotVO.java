package com.hezitu.heaicodemother.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AppProjectSnapshotVO implements Serializable {

    private String codeGenType;

    private String rootDirName;

    private String previewUrl;

    private String entryFilePath;

    private String summary;

    private List<AppProjectFileVO> files;

    private static final long serialVersionUID = 1L;
}
