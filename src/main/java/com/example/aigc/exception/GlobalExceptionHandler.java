package com.example.aigc.exception;

import com.example.aigc.utils.SchemaDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import javax.persistence.PersistenceException;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        // 业务异常属于可预期的客户端错误，使用 warn 且保留调用方声明的状态码
        Integer code = e.getCode();
        if (code == null || code < 100 || code > 599) {
            code = HttpStatus.BAD_REQUEST.value();
        }
        logger.warn("业务异常 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(code, e.getMessage());
        return ResponseEntity.status(code).body(response);
    }
    
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorizedException(UnauthorizedException e, HttpServletRequest request) {
        logger.warn("未授权访问 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Object>> handleForbiddenException(ForbiddenException e, HttpServletRequest request) {
        logger.warn("禁止访问 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        logger.warn("访问被拒绝 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(403, "没有权限访问");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }


    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(NotFoundException e, HttpServletRequest request) {
        logger.warn("资源不存在 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleConflictException(ConflictException e, HttpServletRequest request) {
        logger.warn("版本/冲突 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(NodeOwnedServiceException.class)
    public ResponseEntity<ApiResponse<Object>> handleNodeOwned(NodeOwnedServiceException e, HttpServletRequest request) {
        logger.info("Node 归属能力 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(503, e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /** 显式声明的 HTTP 状态（501 未接入、签名 403 等）必须保留原状态码，不能被 500 兜底吞掉。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleResponseStatusException(ResponseStatusException e, HttpServletRequest request) {
        int status = e.getStatus().value();
        logger.warn("业务状态异常 - {}: {} {}", request.getRequestURI(), status, e.getReason());
        ApiResponse<Object> response = new ApiResponse<>(status, e.getReason() == null ? "请求无法完成" : e.getReason());
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParameter(MissingServletRequestParameterException e, HttpServletRequest request) {
        logger.warn("缺少请求参数 - {}: {}", request.getRequestURI(), e.getParameterName());
        ApiResponse<Object> response = new ApiResponse<>(400, "缺少必需参数: " + e.getParameterName());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        logger.warn("参数类型不匹配 - {}: {}", request.getRequestURI(), e.getName());
        ApiResponse<Object> response = new ApiResponse<>(400, "参数类型不正确: " + e.getName());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        logger.warn("请求体无法解析 - {}", request.getRequestURI());
        ApiResponse<Object> response = new ApiResponse<>(400, "请求体格式错误");
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        logger.warn("请求方法不支持 - {}: {}", request.getRequestURI(), e.getMethod());
        ApiResponse<Object> response = new ApiResponse<>(405, "不支持的请求方法: " + e.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        logger.warn("请求媒体类型不支持 - {}: {}", request.getRequestURI(), e.getContentType());
        ApiResponse<Object> response = new ApiResponse<>(415, "不支持的内容类型");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        logger.warn("参数验证失败 - {}: {}", request.getRequestURI(), errors);
        ApiResponse<Object> response = new ApiResponse<>(400, "参数验证失败");
        response.setData(errors);
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBindException(BindException e, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        logger.warn("参数绑定失败 - {}: {}", request.getRequestURI(), errors);
        ApiResponse<Object> response = new ApiResponse<>(400, "参数绑定失败");
        response.setData(errors);
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        logger.warn("非法参数 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(400, e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 数据库结构未初始化、库不存在、连接不可用：属于部署环境问题而非请求错误。
     * 缺表只返回“服务器内部错误”会让联调方无从判断，这里把可直接执行的补救动作回显出去；
     * 其余数据库异常（SQL 语法、约束冲突等）仍按通用 500 处理，不回显底层信息。
     */
    @ExceptionHandler({DataAccessException.class, PersistenceException.class})
    public ResponseEntity<ApiResponse<Object>> handleDatabaseException(Exception e, HttpServletRequest request) {
        String hint = SchemaDiagnostics.hint(e);
        if (hint == null) {
            String root = SchemaDiagnostics.rootMessage(e);
            // 字段超长/字符集不支持属于请求数据问题，不应回 500；具体字段由业务层提前校验。
            if (isClientDataProblem(root)) {
                logger.warn("数据库数据异常 - {}: {}", request.getRequestURI(), root);
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(400, "提交的数据长度或格式超出数据库限制"));
            }
            logger.error("数据库访问异常 - {}: {}", request.getRequestURI(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(500, "服务器内部错误"));
        }
        logger.error("数据库结构/连接异常 - {}: {}（底层原因：{}）",
                request.getRequestURI(), hint, SchemaDiagnostics.rootMessage(e), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, hint));
    }

    private static boolean isClientDataProblem(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("data too long")
                || lower.contains("data truncation")
                || lower.contains("incorrect string value")
                || lower.contains("out of range");
    }

    /** 兜底：原始异常信息只进日志，不回显给调用方（避免泄漏内部实现与数据）。 */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        logger.error("运行时异常 - {}: {}", request.getRequestURI(), e.getMessage(), e);
        ApiResponse<Object> response = new ApiResponse<>(500, "服务器内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e, HttpServletRequest request) {
        logger.error("系统异常 - {}: {}", request.getRequestURI(), e.getMessage(), e);
        ApiResponse<Object> response = new ApiResponse<>(500, "系统异常，请联系管理员");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
