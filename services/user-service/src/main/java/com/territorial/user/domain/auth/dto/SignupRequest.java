package com.territorial.user.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(min = 4, max = 20) @Pattern(regexp = "^[a-zA-Z0-9]+$") String username,
        @NotBlank @Email String email,
        @NotBlank
                @Size(min = 8, max = 20)
                @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$")
                String password,
        @NotBlank @Size(min = 2, max = 20) String nickname) {}
