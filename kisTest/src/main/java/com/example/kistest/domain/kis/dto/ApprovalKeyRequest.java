package com.example.kistest.domain.kis.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApprovalKeyRequest {
    private String grant_type; // 권한 부여 타입 (항상 "client_credentials" 사용)
    private String appkey;     // KIS 개발자 센터에서 발급받은 앱 키
    private String secretkey;  // KIS 개발자 센터에서 발급받은 시크릿 키
}