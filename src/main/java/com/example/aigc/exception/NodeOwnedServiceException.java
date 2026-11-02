package com.example.aigc.exception;

/**
 * 该能力由 Node 服务提供，Java 不再重复实现。HTTP 503。
 * 配合前端网关将 Node 归属请求直接送 Node。
 */
public class NodeOwnedServiceException extends RuntimeException {

    public NodeOwnedServiceException(String message) {
        super(message);
    }
}
