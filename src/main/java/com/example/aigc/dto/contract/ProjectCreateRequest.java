package com.example.aigc.dto.contract;

import lombok.Data;

import javax.validation.constraints.Size;

/** POST /api/projects：创建普通项目。userId 取当前登录用户，不接受请求体传入。 */
@Data
public class ProjectCreateRequest {

    @Size(max = 100, message = "title 长度不能超过 100 个字符")
    private String title;

    @Size(max = 32, message = "type 长度不能超过 32 个字符")
    private String type;

    @Size(max = 32, message = "style 长度不能超过 32 个字符")
    private String style;

    private Integer duration;

    /** 省略或空串时使用 script。 */
    @Size(max = 64, message = "module 长度不能超过 64 个字符")
    private String module;
}
