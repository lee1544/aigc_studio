package com.example.aigc.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException {
    
    private Integer code;
    
    public NotFoundException(String message) {
        super(message);
        this.code = 404;
    }
}
