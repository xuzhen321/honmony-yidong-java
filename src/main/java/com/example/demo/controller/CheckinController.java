package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.config.OpenApiConfig;
import com.example.demo.pojo.vo.ChallengeVO;
import com.example.demo.pojo.vo.CheckinResult;
import com.example.demo.security.AuthInterceptor;
import com.example.demo.service.CheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 声纹 + 位置打卡接口。
 *
 * <p>身份只从 token 取，不接受客户端传 userId；先申请挑战码，再提交音频和坐标。</p>
 */
@Tag(name = "声纹打卡", description = "申请挑战码、提交声纹音频和位置完成打卡")
@RestController
@RequestMapping("/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @Operation(summary = "申请声纹挑战码",
            description = "当前登录用户；任务必须在时间窗内、本人在名单中且尚未打卡、声纹已录满 10 个数字")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/voice-challenge")
    public Result<ChallengeVO> challenge(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @Parameter(description = "考勤任务 ID", example = "501")
            @RequestParam Long taskId) {
        return Result.success(checkinService.issueChallenge(userId, taskId));
    }

    @Operation(summary = "提交声纹 + 位置打卡",
            description = "multipart/form-data；位置和声纹都通过才完成打卡，失败返回 400/409/410/422/429")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<CheckinResult> checkin(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @Parameter(description = "考勤任务 ID", example = "501")
            @RequestParam Long taskId,
            @Parameter(description = "挑战码 nonce")
            @RequestParam String nonce,
            @Parameter(description = "挑战码 4 位数字", example = "3815")
            @RequestParam String digits,
            @Parameter(description = "纬度 WGS84；任务有打卡点时必须传", example = "31.230416")
            @RequestParam(required = false) String latitude,
            @Parameter(description = "经度 WGS84；任务有打卡点时必须传", example = "121.473701")
            @RequestParam(required = false) String longitude,
            @Parameter(description = "定位精度（米）；任务有打卡点时必须传", example = "12.5")
            @RequestParam(required = false) String accuracy,
            @Parameter(description = "系统是否判定为模拟位置", example = "false")
            @RequestParam(defaultValue = "false") boolean isMock,
            @Parameter(description = "WAV 录音，3~6 秒，16kHz/16bit/单声道，≤500KB")
            @RequestPart("audio") MultipartFile audio) {
        return Result.success(checkinService.checkin(userId, taskId, nonce, digits,
                latitude, longitude, accuracy, isMock, audio));
    }
}
