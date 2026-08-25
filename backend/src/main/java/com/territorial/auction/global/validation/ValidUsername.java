package com.territorial.auction.global.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@NotBlank
@Size(min = 4, max = 50)
@Pattern(regexp = "^[a-zA-Z0-9]+$", message = "영문, 숫자만 사용 가능합니다.")
public @interface ValidUsername {}
