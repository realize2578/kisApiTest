package com.example.kistest.domain.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 1. Access Token 발급 요청 Body
@Getter
@AllArgsConstructor
public class AccessTokenReq {
    private String grant_type;
    private String appkey;
    private String appsecret;
}
