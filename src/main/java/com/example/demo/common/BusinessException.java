package com.example.demo.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：只要代码里出现"用户做错了事"这种可预期的失败，
 * 就 throw 这个异常，最后统一被 GlobalExceptionHandler 接住并转成 {@link Result}。
 *
 * <p>异常只携带一个 HTTP 状态码和一条错误信息；错误信息会原样放进 {@code Result.msg}，
 * {@code Result.data} 固定为 null。</p>
 *
 * <pre>
 * throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
 * </pre>
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 该业务失败对应的 HTTP 状态码 */
    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
