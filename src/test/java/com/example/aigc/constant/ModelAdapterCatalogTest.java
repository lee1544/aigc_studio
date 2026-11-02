package com.example.aigc.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** J08：适配器矩阵校验，泛化 adapter 不得被当作受支持。 */
class ModelAdapterCatalogTest {

    @Test
    void matrixAdaptersAreSupported() {
        assertTrue(ModelAdapterCatalog.supports("script", "chat"));
        assertTrue(ModelAdapterCatalog.supports("image", "autodl-api"));
        assertTrue(ModelAdapterCatalog.supports("video", "kling-video"));
        assertTrue(ModelAdapterCatalog.supports("voice", "dashscope-tts"));
    }

    @Test
    void genericAdapterIsRejected() {
        // 文档示例里的泛化写法不能直接标 supported
        assertFalse(ModelAdapterCatalog.supports("script", "openai-compatible"));
        assertFalse(ModelAdapterCatalog.supports("image", "chat"));
        assertFalse(ModelAdapterCatalog.supports("script", ""));
        assertFalse(ModelAdapterCatalog.supports("unknown-feature", "chat"));
    }

    @Test
    void aliasesResolveToBaseFeature() {
        assertTrue(ModelAdapterCatalog.supportsFeature("textToImage"));
        assertTrue(ModelAdapterCatalog.supports("textToImage", "autodl-api"));
        assertTrue(ModelAdapterCatalog.supports("firstLastFrameVideo", "dashscope-video"));
        assertTrue(ModelAdapterCatalog.supportsFeature("firstLastFrameVideo"));
    }
}
