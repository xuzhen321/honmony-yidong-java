package com.example.demo.exception;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;

/**
 * 全局异常处理器。
 *
 * <p>作用：Controller / Service / 拦截器里任何地方抛出来的异常，都在这里被接住，
 * 统一转成 {@link Result} 返回，而不是让 Spring 甩一坨 Whitelabel 错误页或者 500 HTML 给客户端。</p>
 *
 * <p><b>状态码规则：</b></p>
 * <ul>
 *   <li>HTTP 状态码直接使用 Spring 的 {@link HttpStatus}，不再自定义状态码枚举；</li>
 *   <li>响应体 {@code code}：成功 1，失败 0；</li>
 *   <li>错误信息只放在 {@code Result.msg} 里，成功时为空字符串。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 参数类错误的默认提示 */
    private static final String PARAM_ERROR_MSG = "请求参数有误";

    /** 系统异常对外的统一提示 */
    private static final String SYSTEM_ERROR_MSG = "服务器开小差了，请稍后再试";

    /**
     * 1. 业务异常：我们自己主动 throw 的，属于"可预期"的失败。
     * HTTP 状态取异常里携带的 {@link HttpStatus}，错误信息原样放进 msg。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 [{} {}] http={} msg={}",
                request.getMethod(), request.getRequestURI(), e.getStatus().value(), e.getMessage());
        return this.fail(e.getStatus(), e.getMessage());
    }

    /**
     * 2. @RequestBody 上的参数校验失败（@Valid + @NotBlank 等），
     * 只取第一条错误提示返回给用户，不要一次弹一堆。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getDefaultMessage())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(PARAM_ERROR_MSG);
        log.warn("参数校验失败: {}", msg);
        return this.fail(HttpStatus.BAD_REQUEST, msg);
    }

    /**
     * 3. 方法参数上的校验失败（比如 @RequestParam / @PathVariable 上加了 @NotBlank）。
     * Spring 6.1+ 对 Controller 方法参数的内置校验抛的是 {@link HandlerMethodValidationException}。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        String msg = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(PARAM_ERROR_MSG);
        log.warn("参数校验失败: {}", msg);
        return this.fail(HttpStatus.BAD_REQUEST, msg);
    }

    /** 兼容旧版校验方式抛出的 ConstraintViolationException */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(PARAM_ERROR_MSG);
        log.warn("参数校验失败: {}", msg);
        return this.fail(HttpStatus.BAD_REQUEST, msg);
    }

    /** 4. 请求体不是合法 JSON，或者该传 body 却没传。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return this.fail(HttpStatus.BAD_REQUEST, "请求体格式不正确，请传入合法的 JSON");
    }

    /** 5. 少传了必填的请求参数，例如 ?userName= 没带。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("缺少必要参数: {}", e.getParameterName());
        return this.fail(HttpStatus.BAD_REQUEST, "缺少必要参数: " + e.getParameterName());
    }

    /** 缺少必要的请求头（比如该带 Authorization 却没带）或路径变量 */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<Result<Void>> handleServletRequestBinding(ServletRequestBindingException e) {
        log.warn("请求绑定失败: {}", e.getMessage());
        return this.fail(HttpStatus.BAD_REQUEST, "请求参数不完整: " + e.getMessage());
    }

    /** 参数类型不对，比如 /user/1 本该是数字却传了 abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}={}", e.getName(), e.getValue());
        return this.fail(HttpStatus.BAD_REQUEST, "参数 " + e.getName() + " 的类型不正确");
    }

    /** 6. 请求方式不对，比如 GET 调了只支持 POST 的 /user/login。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方式不支持: {}", e.getMethod());
        return this.fail(HttpStatus.METHOD_NOT_ALLOWED,
                "不支持 " + e.getMethod() + " 请求，该接口支持: " + e.getSupportedHttpMethods());
    }

    /** 7. Content-Type 不对，比如没传 application/json。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("Content-Type 不支持: {}", e.getContentType());
        return this.fail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "不支持 " + e.getContentType() + "，请使用 application/json");
    }

    /** 8. 接口地址写错了：Spring 6.1+ 访问不存在的资源/接口时抛 NoResourceFoundException。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("接口不存在: /{}", e.getResourcePath());
        return this.fail(HttpStatus.NOT_FOUND, "接口不存在: /" + e.getResourcePath());
    }

    /** 开启 throw-exception-if-no-handler-found 时抛 NoHandlerFoundException，同样按 404 返回。 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNoHandlerFound(NoHandlerFoundException e) {
        log.warn("接口不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return this.fail(HttpStatus.NOT_FOUND, "接口不存在: " + e.getRequestURL());
    }

    /**
     * 9. 兜底：任何没被上面接住的异常都到这里，返回 HTTP 500。
     * 打 error + 完整堆栈方便排查，但返回给用户的只有通用提示，不泄露内部细节。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 [{} {}]", request.getMethod(), request.getRequestURI(), e);
        return this.fail(HttpStatus.INTERNAL_SERVER_ERROR, SYSTEM_ERROR_MSG);
    }

    /** 统一构造失败响应：HTTP 状态码由 HttpStatus 决定，body 的 code 固定为 0 */
    private ResponseEntity<Result<Void>> fail(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(Result.fail(msg));
    }
}
