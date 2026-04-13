package com.example.kistest.domain.kis.controller;

import com.example.kistest.domain.kis.KisWebSocketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "Chart API", description = "주식 차트 데이터 조회 API")
@RestController
@RequestMapping("/api/charts")
@RequiredArgsConstructor
public class ChartController {

    // KIS API 기본 정보 (환경 변수로 관리 권장)
    private static final String KIS_REST_URL = "https://openapivts.koreainvestment.com:29443";
    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    // 💡 AppConfig에서 만든 Bean과 Token 발급 서비스 주입
    private final RestTemplate restTemplate;
    private final KisWebSocketService kisWebSocketService;


    @Operation(summary = "일봉 데이터 조회", description = "특정 종목의 과거 일봉 데이터를 조회합니다.")
    @GetMapping("/{symbol}/daily")
    public ResponseEntity<List<CandleDataDto>> getDailyChartData(
            @Parameter(description = "종목 코드 (예: 005930)", example = "005930")
            @PathVariable String symbol
    ) {

        // 1. KIS 일봉 조회 API 엔드포인트 세팅
        String url = KIS_REST_URL + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";

        String accessToken = kisWebSocketService.getValidAccessToken();

        // 2. KIS 규격에 맞춘 헤더 셋팅
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken); // Authorization: Bearer {토큰}
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST03010100"); // 주식 일별 추이 TR_ID (모의투자용)

        // 3. 쿼리 파라미터 셋팅 (삼성전자, 일봉, 시작일, 종료일 등)
        String queryParams = "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=" + symbol +
                "&FID_INPUT_DATE_1=20260301&FID_INPUT_DATE_2=20260413&FID_PERIOD_DIV_CODE=D&FID_ORG_ADJ_PRC=0";

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url + queryParams, HttpMethod.GET, entity, Map.class);
            List<CandleDataDto> chartData = parseKisResponse(response.getBody());
            return ResponseEntity.ok(chartData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // KIS 응답 JSON -> 프론트엔드용 DTO 변환 메서드
    private List<CandleDataDto> parseKisResponse(Map<String, Object> responseBody) {
        List<CandleDataDto> resultList = new ArrayList<>();

        // KIS API 응답 명세서 참조: 'output2' 배열에 일별 시세 데이터가 담겨 옵니다.
        List<Map<String, String>> outputList = (List<Map<String, String>>) responseBody.get("output2");

        if (outputList != null) {
            for (Map<String, String> item : outputList) {
                if (item.get("stck_bsop_date") == null || item.get("stck_bsop_date").isEmpty()) continue;

                CandleDataDto dto = new CandleDataDto(
                        formatDate(item.get("stck_bsop_date")), // 날짜 포맷팅 (YYYY-MM-DD)
                        Long.parseLong(item.get("stck_oprc")),  // 시가
                        Long.parseLong(item.get("stck_hgpr")),  // 고가
                        Long.parseLong(item.get("stck_lwpr")),  // 저가
                        Long.parseLong(item.get("stck_clpr"))   // 종가 (현재가)
                );

                // 프론트엔드 차트 라이브러리(Lightweight Charts)는 과거 날짜부터 오름차순으로 데이터를 넣어야 하므로,
                // 보통 최신 날짜부터 내려주는 KIS 데이터를 역순으로 담거나 0번에 삽입합니다.
                resultList.add(0, dto);
            }
        }
        return resultList;
    }

    private String formatDate(String dateStr) {
        return dateStr.substring(0,4) + "-" + dateStr.substring(4,6) + "-" + dateStr.substring(6,8);
    }
}

// 프론트엔드로 전달할 깔끔한 DTO (React에서 정의한 인터페이스와 동일)
record CandleDataDto(String time, long open, long high, long low, long close) {}