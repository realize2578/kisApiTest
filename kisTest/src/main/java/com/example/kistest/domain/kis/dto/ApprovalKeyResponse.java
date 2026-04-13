package com.example.kistest.domain.kis.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApprovalKeyResponse {
    private String approval_key; // 우리가 최종적으로 필요한 웹소켓 접속 키
}