package com.example.aigc.dto.contract;

import lombok.Data;

import javax.validation.constraints.Size;
import java.util.Map;

/**
 * PUT /api/admin/agent/{short-drama|comic|canvas}/config。
 * short-drama/comic 使用 models（step -> model）；canvas 使用 model。
 * 省略字段表示不修改。
 */
@Data
public class AgentConfigRequest {

    @Size(max = 20000, message = "systemPrompt 最多 20000 字符")
    private String systemPrompt;

    /** shortDrama: script/storyboard/image/video/voice；comic: script/storyboard/image/video。 */
    private Map<String, String> models;

    /** canvas 单模型。 */
    @Size(max = 128, message = "model 长度不能超过 128 个字符")
    private String model;
}
