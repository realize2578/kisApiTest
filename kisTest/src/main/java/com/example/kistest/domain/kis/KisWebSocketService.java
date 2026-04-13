package com.example.kistest.domain.kis;

import com.example.kistest.domain.kis.dto.AccessTokenReq;
import com.example.kistest.domain.kis.dto.AccessTokenRes;
import com.example.kistest.domain.kis.dto.ApprovalKeyRequest;
import com.example.kistest.domain.kis.dto.ApprovalKeyResponse;
import com.example.kistest.domain.kis.entity.KisToken;
import com.example.kistest.domain.kis.repository.KisTokenRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisWebSocketService {
    private static final String KIS_REST_URL = "https://openapivts.koreainvestment.com:29443";
    private static final String KIS_WS_URL = "ws://ops.koreainvestment.com:21000";
    private static final int MAX_SUBSCRIPTION_PER_SESSION = 40;

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    // 💡 AppConfig에서 등록한 Bean을 주입받음
    private final RestTemplate restTemplate;

    // 💡 메모리 변수(cachedAccessToken)를 삭제하고 Repository를 주입받습니다.
    private final KisTokenRepository kisTokenRepository;

    private static final String TOKEN_ID = "REST_ACCESS_TOKEN"; // 단일 레코드 관리를 위한 고정 키

    private String cachedAccessToken;
    private LocalDateTime tokenExpirationTime;

    // 💡 Tomcat 서버가 다 켜진 후 안전하게 실행됨 (블로킹 방지)
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            String approvalKey = issueApprovalKey();
            log.info("Approval Key 발급 성공: {}", approvalKey);

            List<String> allSymbols = createDummySymbols(100);
            for (int i = 0; i < allSymbols.size(); i += MAX_SUBSCRIPTION_PER_SESSION) {
                int end = Math.min(i + MAX_SUBSCRIPTION_PER_SESSION, allSymbols.size());
                List<String> chunkedSymbols = allSymbols.subList(i, end);
                connectToKisServer(approvalKey, chunkedSymbols);
            }
        } catch (Exception e) {
            log.error("KIS 초기화 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    // REST API를 호출하여 Approval Key를 받아오는 메서드
    private String issueApprovalKey() {
        String url = KIS_REST_URL + "/oauth2/Approval";

        // 헤더 설정 (Content-Type: application/json; utf-8)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 바디 설정
        ApprovalKeyRequest requestBody = new ApprovalKeyRequest("client_credentials", appKey, appSecret);

        // 요청 객체 조합
        HttpEntity<ApprovalKeyRequest> entity = new HttpEntity<>(requestBody, headers);

        // POST 요청 전송 및 응답 받기
        ApprovalKeyResponse response = restTemplate.postForObject(url, entity, ApprovalKeyResponse.class);

        if (response == null || response.getApproval_key() == null) {
            throw new RuntimeException("Approval Key를 발급받지 못했습니다.");
        }

        return response.getApproval_key();
    }

    // 웹소켓을 연결하는 메서드
    private void connectToKisServer(String approvalKey, List<String> chunkedSymbols) throws Exception {
        try {
            WebSocketClient webSocketClient = new StandardWebSocketClient();

            // 핸들러 생성 시, 잘라낸 리스트(chunkedSymbols)를 주입
            KisWebSocketHandler handler = new KisWebSocketHandler(approvalKey, chunkedSymbols);

            // 비동기로 웹소켓 연결 실행
            webSocketClient.execute(handler, KIS_WS_URL).get();

            // 세션 연결 간격을 살짝 벌려주어 안전하게 통신 시작 (선택 사항)
            Thread.sleep(500);

        } catch (InterruptedException | ExecutionException e) {
            log.error("웹소켓 분산 세션 연결 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    // 테스트용 더미 종목 생성 메서드 (실제 구현 시 삭제)
    private List<String> createDummySymbols(int count) {
        List<String> symbols = new ArrayList<>();
        // 삼성전자(005930), 카카오(035720) 등을 포함하여 가상의 코드 생성
        symbols.add("005930");
        symbols.add("035720");
        for (int i = 2; i < count; i++) {
            symbols.add(String.format("%06d", i)); // 000002, 000003 ...
        }
        return symbols;
    }


    /**
     * 외부에서 REST API 호출 시 이 메서드를 통해 토큰을 확보합니다.
     * 유효한 토큰이 있으면 캐시를 반환하고, 없거나 만료 임박 시 새로 발급합니다.
     */
    public String getValidAccessToken() {
        // 1. DB에서 토큰을 꺼내옵니다.
        KisToken tokenEntity = kisTokenRepository.findById(TOKEN_ID).orElse(null);

        // 2. DB에 토큰이 아예 없거나, 유효기간이 지났다면 새로 발급받고 DB에 저장합니다.
        if (tokenEntity == null || isTokenExpired(tokenEntity.getExpirationTime())) {
            return refreshAndSaveAccessToken(tokenEntity);
        }
//        else if (cachedAccessToken == null || isTokenExpired()) {
//            refreshAccessToken();
//        }
//        return cachedAccessToken;

        // 3. 유효기간이 남아있다면 API 호출 없이 DB에 있던 토큰을 바로 꺼내 씁니다.
        log.info("💾 DB에 캐싱된 유효한 Access Token을 재사용합니다.");
        return tokenEntity.getAccessToken();
    }

    private boolean isTokenExpired(LocalDateTime expirationTime) {
        return expirationTime == null || LocalDateTime.now().isAfter(expirationTime.minusMinutes(1));
    }

    private String refreshAndSaveAccessToken(KisToken existingToken) {
        log.info("🌐 새로운 Access Token 발급을 KIS 서버에 요청합니다...");
        String url = KIS_REST_URL + "/oauth2/tokenP";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        AccessTokenReq requestBody = new AccessTokenReq("client_credentials", appKey, appSecret);
        HttpEntity<AccessTokenReq> entity = new HttpEntity<>(requestBody, headers);

        try {
            AccessTokenRes response = restTemplate.postForObject(url, entity, AccessTokenRes.class);

            if (response != null && response.getAccessToken() != null) {
                String newToken = response.getAccessToken();
                LocalDateTime newExpTime = LocalDateTime.now().plusSeconds(response.getExpiresIn());

                if (existingToken == null) {
                    existingToken = new KisToken(TOKEN_ID, newToken, newExpTime);
                } else {
                    existingToken.updateToken(newToken, newExpTime);
                }

                kisTokenRepository.save(existingToken);

                log.info("✅ Access Token 신규 발급 및 DB 저장 완료. 만료 예정: {}", newExpTime);
                return newToken;
            }
        } catch (HttpStatusCodeException e) {
            // 💡 KIS 서버가 거절한 진짜 이유(응답 바디)를 상세하게 출력합니다.
            log.error("❌ KIS API 발급 거절: 상태코드={}, 사유={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("KIS 토큰 발급 실패: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("❌ 기타 오류 발생: {}", e.getMessage());
            throw new RuntimeException("알 수 없는 오류로 토큰 발급 실패");
        }
        return null;
    }

//    private boolean isTokenExpired() {
//        // 만료 1분 전이면 만료된 것으로 간주하여 안전하게 갱신
//        return tokenExpirationTime == null || LocalDateTime.now().isAfter(tokenExpirationTime.minusMinutes(1));
//    }

//    private void refreshAccessToken() {
//        log.info("새로운 Access Token 발급을 시도합니다.");
//        String url = KIS_REST_URL + "/oauth2/tokenP";
//        // 💡 1. 필수 헤더 추가 (Content-Type: application/json)
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        AccessTokenReq requestBody = new AccessTokenReq("client_credentials", appKey, appSecret);
//
//        // 💡 2. 헤더와 바디를 HttpEntity로 포장
//        HttpEntity<AccessTokenReq> entity = new HttpEntity<>(requestBody, headers);
//
//        try {
//            AccessTokenRes response = restTemplate.postForObject(url, entity, AccessTokenRes.class);
//            if (response != null && response.getAccessToken() != null) {
//                this.cachedAccessToken = response.getAccessToken();
//                this.tokenExpirationTime = LocalDateTime.now().plusSeconds(response.getExpiresIn());
//                log.info("Access Token 갱신 완료");
//            }
//        } catch (HttpStatusCodeException e) {
//            // 💡 KIS 서버가 보내는 진짜 에러 메세지를 로그에 출력하도록 개선
//            log.error("Access Token 발급 거부됨. 상태코드: {}, 응답내용: {}", e.getStatusCode(), e.getResponseBodyAsString());
//            throw new RuntimeException("KIS 인증 토큰 발급 실패: " + e.getResponseBodyAsString());
//        } catch (Exception e) {
//            log.error("Access Token 발급 중 알 수 없는 오류 발생: {}", e.getMessage());
//            throw new RuntimeException("KIS 인증 토큰 발급 실패");
//        }
//    }


}