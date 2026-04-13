package com.example.kistest.domain.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 2. Access Token 응답 Body
@Getter
@NoArgsConstructor
public class AccessTokenRes {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String token_type;

    @JsonProperty("expires_in")
    private int expiresIn; // 유효 기간 (초 단위, 보통 86400초)
}