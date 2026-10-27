package com.example.demo.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 统一响应体。
 *
 * <p>字段名必须和鸿蒙端 HttpUtil.ets 里的接口一模一样：</p>
 * <pre>
 * interface ApiResponse&lt;T&gt; {
 *   code: number;
 *   msg: string;
 *   data: T;
 * }
 * </pre>
 *
 * <p><b>响应体里的 code 只有两个值：</b></p>
 * <ul>
 *   <li>{@code 1} = 成功，此时 msg 为空字符串；</li>
 *   <li>{@code 0} = 失败，具体原因在 msg 里，可直接弹给用户看。</li>
 * </ul>
 *
 * <p>HTTP 状态码由异常携带的 {@code HttpStatus} 决定，不放在响应体里。</p>
 *
 * @param <T> 真正的业务数据类型
 */
@Schema(description = "统一响应体")
@Data
public class Result<T> {

    /** 成功 */
    public static final int CODE_SUCCESS = 1;

    /** 失败 */
    public static final int CODE_FAIL = 0;

    /** 成功时的提示信息：空字符串 */
    public static final String SUCCESS_MSG = "";

    /** 1 = 成功，0 = 失败 */
    @Schema(description = "1 = 成功，0 = 失败", example = "1")
    private int code;

    /** 提示信息，只有失败时才有内容 */
    @Schema(description = "提示信息，只有失败时才有内容；成功时为空字符串", example = "")
    private String msg;

    /** 业务数据，失败时为 null */
    @Schema(description = "业务数据，失败时为 null")
    private T data;

    public Result() {
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /** 成功且带数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(CODE_SUCCESS, SUCCESS_MSG, data);
    }

    /** 成功但不返回数据（比如退出登录） */
    public static Result<Void> success() {
        return new Result<>(CODE_SUCCESS, SUCCESS_MSG, null);
    }

    /** 失败：code 固定为 0，具体原因放在 msg 里 */
    public static <T> Result<T> fail(String msg) {
        return new Result<>(CODE_FAIL, msg, null);
    }
}
