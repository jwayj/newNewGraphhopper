package com.graphhopper.example;

import static spark.Spark.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class FeedbackServer {

    // ✅ 진입점: 따로 실행할 때도 가능
    public static void main(String[] args) {
        start();
    }

    // ✅ Spark 서버를 실행하고 라우트 설정
    public static void start() {
        // 정적 파일 경로 설정 (HTML, GeoJSON 등)
        staticFiles.externalLocation("example/resources");

        // 포트 설정 (기본값은 4567이지만 명시적으로 설정)
        port(4567);

        // POST 요청 처리 (/feedback)
        post("/feedback", (req, res) -> {
        String body = req.body();
        System.out.println("📥 받은 피드백: " + body);

        try {
            Gson gson = new Gson();
            // 1. 먼저 문자열 리스트로 파싱
            Map<String, List<String>> feedbackMap = gson.fromJson(
                body, new TypeToken<Map<String, List<String>>>() {}.getType()
            );

            List<String> selectedEdges = feedbackMap.get("selectedEdges");

            // 2. 문자열에서 숫자만 추출해서 integer 리스트로 변환
            Map<String, List<Integer>> cleanedMap = new LinkedHashMap<>();
            List<Integer> parsedEdges = selectedEdges.stream()
                .map(s -> {
                    try {
                        return Integer.parseInt(s.replaceAll("[^0-9]", ""));
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            cleanedMap.put("selectedEdges", parsedEdges);

            // 3. 파일 저장
            LogGeoJson.writeFeedback(cleanedMap);
            System.out.println("✅ feedback_log.json 작성 완료!");

            return "피드백 수신 완료!";

        } catch (Exception e) {
            e.printStackTrace();
            res.status(500);
            return "서버 에러: " + e.getMessage();
        }
    });

        System.out.println("✅ FeedbackServer is running at: http://localhost:4567");
    }
}
