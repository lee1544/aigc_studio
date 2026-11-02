package com.example.aigc.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义模型适配器矩阵，与 static/docs/backend-handoff/model-adapters.json 保持一致。
 * 写入时校验 (feature, adapter) 是否可执行；读取时据此判定 supported，避免伪支持。
 * textToImage/imageToImage -> image，textToVideo/imageToVideo/firstLastFrameVideo -> video。
 */
public final class ModelAdapterCatalog {

    private ModelAdapterCatalog() {}

    private static final Map<String, List<String>> ADAPTERS;
    private static final Map<String, String> ALIASES;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("textGeneration", Arrays.asList("chat", "autodl-chat"));
        m.put("imageEdit", Arrays.asList("qwen-image-edit", "wanx-image-edit"));
        m.put("lyricRecognition", Arrays.asList("tencent-asr", "dashscope-asr"));
        m.put("script", Arrays.asList("chat", "autodl-chat"));
        m.put("storyboard", Arrays.asList("chat", "autodl-chat"));
        m.put("videoReverse", Arrays.asList("chat", "autodl-chat"));
        m.put("image", Arrays.asList(
                "autodl-api", "autodl-workflow", "tencent-image", "autodl-image",
                "dashscope-image", "dashscope-image-sync", "qwen-image", "wanx-image-edit", "ark-image"));
        m.put("video", Arrays.asList(
                "tencent-vega", "dashscope-videoretalk", "autodl-api", "autodl-workflow", "fal-lipsync",
                "tencent-video", "dashscope-video", "dashscope-image-video", "dashscope-s2v",
                "ark-video", "kling-video"));
        m.put("voice", Arrays.asList("autodl-api", "autodl-workflow", "dashscope-tts"));
        m.put("music", Arrays.asList(
                "autodl-api", "autodl-workflow", "tencent-mureka", "tencent-music",
                "dashscope-music", "minimax-music", "hf-audio", "ace-step-music"));
        m.put("threeD", Arrays.asList("autodl-api", "autodl-workflow", "tripo-3d", "dashscope-3d", "tencent-3d"));
        ADAPTERS = Collections.unmodifiableMap(m);

        Map<String, String> a = new HashMap<>();
        a.put("textToImage", "image");
        a.put("imageToImage", "image");
        a.put("textToVideo", "video");
        a.put("imageToVideo", "video");
        a.put("firstLastFrameVideo", "video");
        ALIASES = Collections.unmodifiableMap(a);
    }

    /** feature 是否在契约枚举内（含别名）。 */
    public static boolean supportsFeature(String feature) {
        return feature != null && ADAPTERS.containsKey(resolve(feature));
    }

    /** 该 feature 是否允许此适配器执行。 */
    public static boolean supports(String feature, String adapter) {
        if (feature == null || adapter == null || adapter.isEmpty()) return false;
        List<String> list = ADAPTERS.get(resolve(feature));
        return list != null && list.contains(adapter);
    }

    public static List<String> adaptersOf(String feature) {
        List<String> list = ADAPTERS.get(resolve(feature));
        return list == null ? Collections.<String>emptyList() : list;
    }

    private static String resolve(String feature) {
        String base = ALIASES.get(feature);
        return base == null ? feature : base;
    }
}
