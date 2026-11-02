package com.example.aigc.web;

import com.example.aigc.exception.NodeOwnedServiceException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Node 归属能力的占位路由：积分/计费/生成/执行/任务/文件/模型目录等由 Node 服务提供。
 * Java 不再重复实现；这些路径统一返回 503，配合前端网关将请求直接送 Node。
 * 保留源码与既有数据，不删除表、不迁移余额。
 */
@RestController
public class NodeOwnedController {

    private static final String MSG = "该能力由 Node 服务提供，Java 不再重复实现";

    private void reject(HttpServletRequest req) {
        throw new NodeOwnedServiceException(MSG);
    }

    // 积分（管理端 + 用户端）
    @RequestMapping(value = {"/api/admin/points", "/api/admin/points/**"}, method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public void points(HttpServletRequest req) { reject(req); }

    // 计费报价
    @RequestMapping(value = {"/api/billing", "/api/billing/**"}, method = {RequestMethod.GET, RequestMethod.POST})
    public void billing(HttpServletRequest req) { reject(req); }

    // 用户端积分
    @RequestMapping(value = {"/api/points", "/api/points/**"}, method = {RequestMethod.GET, RequestMethod.POST})
    public void userPoints(HttpServletRequest req) { reject(req); }

    // 生成执行
    @RequestMapping(value = {"/api/generate", "/api/generate/**"}, method = {RequestMethod.GET, RequestMethod.POST})
    public void generate(HttpServletRequest req) { reject(req); }

    // 运行记录与统计（任务/概览归 Node）
    @RequestMapping(value = {
            "/api/admin/generation-records", "/api/admin/generation-records/**",
            "/api/admin/generation-filters", "/api/admin/generation-summary",
            "/api/admin/overview", "/api/admin/stats", "/api/admin/generations"
    }, method = RequestMethod.GET)
    public void reporting(HttpServletRequest req) { reject(req); }

    // 签名媒体文件（生成/参考/数字人）
    @RequestMapping(value = {"/api/generated", "/api/generated/**", "/api/reference-input/**", "/api/digital-human-media/**"}, method = {RequestMethod.GET, RequestMethod.POST})
    public void media(HttpServletRequest req) { reject(req); }

    // 文本工作室 SSE / 工作流运行 / 音乐 / 画布对话 / 数字人 / AutoDL 实例
    @RequestMapping(value = {
            "/api/text-studio", "/api/text-studio/**",
            "/api/workflows", "/api/workflows/**",
            "/api/music", "/api/music/**",
            "/api/canvas", "/api/canvas/**",
            "/api/digital-humans", "/api/digital-humans/**",
            "/api/admin/autodl/instances"
    }, method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public void execution(HttpServletRequest req) { reject(req); }

    // 用户端模型目录（执行侧由 Node 提供）
    @RequestMapping(value = {"/api/models", "/api/published-models"}, method = RequestMethod.GET)
    public void modelCatalog(HttpServletRequest req) { reject(req); }
}
