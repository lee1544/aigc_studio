package com.example.aigc.exception;

/**
 * 版本冲突 / 并发修改 / 幂等键同键异参 等场景，HTTP 409。
 */
public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(409, message);
    }
}
