package com.territorial.user.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 20) @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$")
                String password,
        @NotBlank @Size(max = 30) String nickname) {}
