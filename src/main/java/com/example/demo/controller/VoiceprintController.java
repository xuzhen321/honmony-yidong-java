package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.config.OpenApiConfig;
import com.example.demo.pojo.vo.EnrollResult;
import com.example.demo.pojo.vo.VoiceprintStatusVO;
import com.example.demo.security.AuthInterceptor;
import com.example.demo.service.VoiceprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 声纹接口：注册数字模板、查询注册状态、管理员重置。
 *
 * <p>所有接口都要求 {@code Authorization: Bearer {token}}；身份只从 token 取，
 * 注册接口不接受 userId。</p>
 */
@Tag(name = "声纹认证", description = "注册数字模板、查询注册状态、管理员重置声纹")
@RestController
@RequestMapping("/voiceprint")
@RequiredArgsConstructor
public class VoiceprintController {

    private final VoiceprintService voiceprintService;

    @Operation(summary = "注册一个数字模板",
            description = "所有登录用户；multipart/form-data；phraseTag 为 DIGIT_0 ~ DIGIT_9；"
                    + "audio 为 16kHz/16bit/单声道 WAV，时长 0.5~1.5 秒、不超过 500KB")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping(value = "/enroll", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<EnrollResult> enroll(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @Parameter(description = "数字标签：DIGIT_0 ~ DIGIT_9", example = "DIGIT_3")
            @RequestParam("phraseTag") String phraseTag,
            @Parameter(description = "WAV 录音文件")
            @RequestPart("audio") MultipartFile audio) {
        return Result.success(voiceprintService.enroll(userId, phraseTag, audio));
    }

    @Operation(summary = "查询自己的声纹注册状态",
            description = "当前登录用户；返回 status、已录数字数、缺失数字和 readyForCheckin")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/status")
    public Result<VoiceprintStatusVO> status(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        return Result.success(voiceprintService.getStatus(userId));
    }

    @Operation(summary = "管理员重置某人的声纹",
            description = "仅管理员；账号行保留，状态置为已失效，模板逻辑删除、注册音频物理删除；"
                    + "目标用户没有声纹档案时幂等成功")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/reset/{userId}")
    public Result<Void> reset(
            @Parameter(hidden = true) @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @Parameter(description = "目标用户 ID", example = "2") @PathVariable Long userId) {
        voiceprintService.reset(operatorId, userId);
        return Result.success();
    }
}
