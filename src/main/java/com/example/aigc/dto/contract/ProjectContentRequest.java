package com.example.aigc.dto.contract;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * PUT /api/projects/{id}/content：保存项目正文。
 * scenes/media 既接受 JSON 数组/对象，也接受已序列化的字符串（字符串按原样保存）。
 */
@Data
public class ProjectContentRequest {

    /** 省略时保存为空串。 */
    private String script;

    /** 省略时保存为 []。 */
    private JsonNode scenes;

    /** 省略时保存为 {}。 */
    private JsonNode media;
}
