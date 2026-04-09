package com.hezitu.heaicodemother.service;

/**
 * 鎴浘鏈嶅姟
 */
public interface ScreenshotService {


    /**
     * 閫氱敤鐨勬埅鍥炬湇鍔★紝鍙互寰楀埌璁块棶鍦板潃
     *
     * @param webUrl 缃戝潃
     * @return
     */
    String generateAndUploadScreenshot(String webUrl);

}

