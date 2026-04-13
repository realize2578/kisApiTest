package com.example.kistest.domain.kis;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Slf4j
public class KisWebSocketHandler extends TextWebSocketHandler {

    private final String approvalKey; // REST API로 미리 발급받은 실시간 접속키
    private final List<String> targetSymbols; // 💡 이 세션이 담당할 종목 리스트

    // 생성자를 통해 접속키와 담당 종목 리스트를 주입받음
    public KisWebSocketHandler(String approvalKey, List<String> targetSymbols) {
        this.approvalKey = approvalKey;
        this.targetSymbols = targetSymbols;
    }

    // 1. 웹소켓 연결이 성공적으로 이루어지면 실행됨
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("한국투자증권 웹소켓 서버에 연결되었습니다.");

        // 구독을 위한 JSON 메시지 조립 (삼성전자 실시간 체결가 구독 예시)
        String trId = "H0STCNT0"; // 국내주식 실시간 체결가
        // 배열을 순회하며 각 종목마다 구독 메시지를 전송합니다.
        for (String symbol : targetSymbols) {
            String requestJson = String.format(
                    "{\"header\": {\"approval_key\": \"%s\", \"custtype\": \"P\", \"tr_type\": \"1\", \"content-type\": \"utf-8\"}," +
                            "\"body\": {\"input\": {\"tr_id\": \"%s\", \"tr_key\": \"%s\"}}}",
                    approvalKey, trId, symbol
            );

            // 해당 종목 구독 요청 쏘기
            session.sendMessage(new TextMessage(requestJson));

            // 💡 팁: 외부 API 서버 부하 방지 및 안정적인 구독을 위해 메시지 사이에 0.1초 딜레이 추가
//            Thread.sleep(100);
        }
    }

    // 2. KIS 서버로부터 실시간 데이터가 들어올 때마다 실행됨
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        // KIS 응답 데이터는 JSON 응답과 '|' 및 '^' 로 구분된 평문 데이터가 혼재되어 옴
        if (payload.startsWith("{")) {
            // 구독 성공/실패 응답 또는 PingPong 메시지 (JSON 형태)
            log.info("JSON 응답 수신: {}", payload);
        } else {
            // 실시간 체결 데이터 (예: 0|H0STCNT0|001|005930^110010^... )
            parseRealtimeData(payload);
        }
    }

    // 3. 수신한 평문 데이터 파싱 로직
    private void parseRealtimeData(String payload) {
        // KIS 데이터 포맷: 암호화여부|TR_ID|데이터건수|실시간데이터
        // 예: 0|H0STCNT0|001|005930^1^102530^74500^...
        String[] parts = payload.split("\\|");

        if (parts.length >= 4) {
            String trId = parts[1]; // H0STCNT0 (체결가) 또는 H0STASP0 (호가)
            String realData = parts[3]; // 실제 데이터 뭉치

            if ("H0STCNT0".equals(trId)) {
                String[] dataFields = realData.split("\\^");

                try {
                    // 모의투자 서버 응답에 맞춘 인덱스 수정 완료!
                    String symbol = dataFields[0];                          // [0] 종목코드
                    String tradeTime = dataFields[1];           // [1] 체결시간
                    String currentPrice = dataFields[2];                    // [2] 현재가(체결가)

                    // [3]은 전일 대비 부호(1:상한, 2:상승, 3:보합, 4:하한, 5:하락)
                    String vsSign = dataFields[3];
                    String vsYesterday = dataFields[4];                     // [4] 전일대비

                    // 데이터가 전체적으로 당겨졌으므로 거래량도 11, 12번을 확인합니다.
                    String volume = dataFields[12];                         // [11] 체결거래량 (또는 12번 누적거래량)

                    log.info("📊 [실시간 체결] 종목: {}, 시간: {}, 현재가: {}원, 전일대비: {}원, 거래량: {}",
                            symbol, tradeTime, currentPrice, vsYesterday, volume);

                } catch (ArrayIndexOutOfBoundsException e) {
                    log.error("데이터 파싱 중 오류 발생. 원본: {}", realData);
                }
            }
        }
    }
}