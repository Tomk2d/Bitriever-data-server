package com.bitreiver.fetch_server.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "회원가입 요청")
public class UserSignUpRequest {
    @Schema(description = "이메일 주소", example = "user@example.com", required = true)
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;
    
    @Schema(description = "닉네임 (1-20자)", example = "user123", required = true)
    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 1, max = 20, message = "닉네임은 1자 이상 20자 이하여야 합니다.")
    private String nickname;
    
    @Schema(description = "가입 타입 (0: 로컬, 1: SNS)", example = "0", required = true)
    @NotNull(message = "가입 타입은 필수입니다.")
    private Short signupType;
    
    @Schema(description = "비밀번호 (로컬 가입시에만 필요)", example = "password123")
    private String password;
    
    @Schema(description = "SNS 제공자 (1:naver, 2:kakao, 3:google, 4:apple)", example = "1")
    private Short snsProvider;
    
    @Schema(description = "SNS 제공자 고유 식별자", example = "sns_user_id_12345")
    private String snsId;
}

