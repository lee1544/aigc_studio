package com.example.aigc.constant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账号功能权限（26 项布尔 feature）。
 * <p>
 * 对应前端契约 account-feature-permissions.md 与 OpenAPI 的 PermissionPatch / Permissions schema。
 * 这些 feature 在 t_permission 中以 permissionCode 形式登记，并通过创作者的"个人角色"承载授权。
 */
public final class FeaturePermissions {

    private FeaturePermissions() {}

    /** 26 项 feature 标识，顺序与 OpenAPI PermissionPatch 一致。 */
    public static final List<String> CODES = Collections.unmodifiableList(java.util.Arrays.asList(
            "text-studio", "script", "storyboard", "grid",
            "textToImage", "imageToImage", "imageEdit", "image",
            "textToVideo", "imageToVideo", "firstLastFrameVideo", "video",
            "voice", "music", "lyricRecognition", "digital-human",
            "edit", "3d", "short-drama-agent", "comic-agent",
            "canvas", "director", "workflows", "transform", "recreate", "files"
    ));

    /** feature -> 中文名，用于 t_permission.permissionName。 */
    public static final Map<String, String> NAMES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("text-studio", "文本工作室");
        m.put("script", "剧本创作");
        m.put("storyboard", "分镜");
        m.put("grid", "九宫格入口");
        m.put("textToImage", "文生图");
        m.put("imageToImage", "图生图");
        m.put("imageEdit", "图片编辑");
        m.put("image", "图片生成");
        m.put("textToVideo", "文生视频");
        m.put("imageToVideo", "图生视频");
        m.put("firstLastFrameVideo", "首尾帧视频");
        m.put("video", "视频生成");
        m.put("voice", "配音");
        m.put("music", "音乐");
        m.put("lyricRecognition", "歌词识别");
        m.put("digital-human", "数字人");
        m.put("edit", "本地剪辑");
        m.put("3d", "3D Studio");
        m.put("short-drama-agent", "短剧 Agent");
        m.put("comic-agent", "漫剧 Agent");
        m.put("canvas", "画布");
        m.put("director", "本地 3D 导演台");
        m.put("workflows", "通用工作流");
        m.put("transform", "素材转换");
        m.put("recreate", "参考视频再创作");
        m.put("files", "我的生成文件");
        NAMES = Collections.unmodifiableMap(m);
    }

    /** 个人角色编码前缀：每个创作者一个个人角色，承载其 26 项 feature 授权。 */
    public static final String PERSONAL_ROLE_PREFIX = "USER_FEATURES_";

    /** 创作者角色编码（role='user' 的账号自动绑定）。 */
    public static final String PERSONAL_ROLE(Integer userId) {
        return PERSONAL_ROLE_PREFIX + userId;
    }

    /** 返回全 false 的 feature 快照（ LinkedHashMap 保持顺序）。 */
    public static Map<String, Boolean> allFalse() {
        Map<String, Boolean> snap = new LinkedHashMap<>();
        for (String code : CODES) {
            snap.put(code, false);
        }
        return snap;
    }

    /** 是否为已知 feature 标识。 */
    public static boolean isKnown(String code) {
        return NAMES.containsKey(code);
    }
}
