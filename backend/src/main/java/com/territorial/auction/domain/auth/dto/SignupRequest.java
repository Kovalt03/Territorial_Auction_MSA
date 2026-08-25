package com.territorial.auction.domain.auth.dto;

import com.territorial.auction.global.validation.ValidNickname;
import com.territorial.auction.global.validation.ValidUsername;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @ValidUsername String username,
        @NotBlank @Email String email,
        @NotBlank
                @Size(min = 8, max = 20)
                @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "영문과 숫자를 모두 포함해야 합니다.")
                String password,
        @ValidNickname String nickname) {}
