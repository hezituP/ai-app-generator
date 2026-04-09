package com.hezitu.heaicodemother.monitor;

import lombok.extern.slf4j.Slf4j;

/**
 * 鐩戞帶涓婁笅鏂囨寔鏈夎€咃紙鍚岀嚎绋嬪唴鍏变韩锛? */
@Slf4j
public class MonitorContextHolder {

    private static final ThreadLocal<MonitorContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 璁剧疆鐩戞帶涓婁笅鏂?     */
    public static void setContext(MonitorContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 鑾峰彇褰撳墠鐩戞帶涓婁笅鏂?     */
    public static MonitorContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 娓呴櫎鐩戞帶涓婁笅鏂?     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }
}
