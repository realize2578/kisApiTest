package com.example.kistest.domain.kis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class KisToken {

    @Id
    private String id; // 테이블에 단 1줄만 저장하기 위해 고정 ID 사용 ("REST_ACCESS_TOKEN")

    @Column(length = 1000)
    private String accessToken;
    private LocalDateTime expirationTime;

    // 토큰 갱신용 메서드
    public void updateToken(String accessToken, LocalDateTime expirationTime) {
        this.accessToken = accessToken;
        this.expirationTime = expirationTime;
    }
}