package com.graphhopper.example;

import static spark.Spark.*;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import java.io.File;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.shapes.GHPoint;

public class FeedbackServer {

    public static void main(String[] args) {
        start();
    }

    public static void start() {
        // ✅ 아래 세 줄로 교체
        String resourcePath = new File("example/resources").getAbsolutePath();
        System.out.println("📂 정적 파일 경로: " + resourcePath);
        staticFiles.externalLocation(resourcePath);
        // 포트 명시
        port(4567);

        // ✅ 피드백 수신 라우트
        post("/feedback", (req, res) -> {
            String body = req.body();
            System.out.println("📥 받은 피드백: " + body);

            try {
                Gson gson = new Gson();
                Map<String, List<String>> feedbackMap = gson.fromJson(req.body(), new TypeToken<Map<String, List<String>>>() {}.getType());

                List<String> selectedEdges = feedbackMap.get("selectedEdges");

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

                Map<String, List<Integer>> cleanedMap = new LinkedHashMap<>();
                cleanedMap.put("selectedEdges", parsedEdges);

                LogGeoJson.writeFeedback(cleanedMap);
                System.out.println("✅ feedback_log.json 작성 완료!");

                return "피드백 수신 완료!";
            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return "서버 에러: " + e.getMessage();
            }
        });

        // ✅ 경로 요청 라우트 (/submit)
        post("/submit", (req, res) -> {
            System.out.println("🔍 수신된 raw JSON:\n" + req.body());
            
            Gson gson = new Gson();
            Map<String, String> input = gson.fromJson(req.body(), new TypeToken<Map<String, String>>() {}.getType());


            String startLatStr = Objects.toString(input.get("startLat"), null);
            String startLonStr = Objects.toString(input.get("startLon"), null);
            String endLatStr = Objects.toString(input.get("endLat"), null);
            String endLonStr = Objects.toString(input.get("endLon"), null);
            String distanceStr = Objects.toString(input.get("distance"), null);
            String slopePref = Objects.toString(input.get("slope"), null);
            


            // 🖨️ 콘솔 로그 출력 추가
            System.out.println("📬 클라이언트 요청 값 수신:");
            System.out.println("출발지: " + startLatStr + ", " + startLonStr);
            System.out.println("도착지: " + (endLatStr != null ? endLatStr : "(출발지와 같음)") + ", " + (endLonStr != null ? endLonStr : "(출발지와 같음)"));
            System.out.println("거리 (km): " + distanceStr);
            System.out.println("경사도: " + slopePref);

            if (startLatStr == null || startLonStr == null || distanceStr == null) {
                res.status(400);
                return "❌ 입력값 누락";
            }

            try {
                // 좌표 및 거리 파싱
                GHPoint start = new GHPoint(Double.parseDouble(startLatStr), Double.parseDouble(startLonStr));
                GHPoint end = (endLatStr != null && !endLatStr.isEmpty()) ?
                        new GHPoint(Double.parseDouble(endLatStr), Double.parseDouble(endLonStr)) : start;
                double desiredDistance = Double.parseDouble(distanceStr);

                // ✅ 페널티 edge ID 및 경사도 데이터 로딩
                Set<Integer> penalizedEdgeIds = FeedbackLoader.loadPenalizedEdgeIds("example/resources/feedback_log.json");
                Map<Long, Double> slopeData = new GpkgSlopeReader("_").getAllSlopeData();

                // ✅ GraphHopper 인스턴스 및 커스텀 Weighting 생성
                String osmPath = System.getProperty("user.dir") + "/seoul-non-military.osm.pbf";
                GraphHopper hopper = RoutingExample.createGraphHopperInstance(osmPath, penalizedEdgeIds);
                Weighting customWeighting = FeedbackUtil.createSlopeWeighting(hopper, slopeData, slopePref);

                // ✅ 경로 생성
                ResponsePath path = RoutingExample.routingWithDesiredDistance(hopper, desiredDistance, start, end, customWeighting);

                if (path != null) {
                    String geojson = GeoJsonExporter1.toGeoJSON(path);
                    SaveGeoJson.saveToFile(geojson, "example/resources/route1.geojson");
                    System.out.println("📂 route1.geojson 저장 완료!");
                    return "경로 생성 완료!";
                } else {
                    return "❌ 경로 생성 실패";
                }

            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return "서버 오류: " + e.getMessage();
            }
        });

        System.out.println("✅ FeedbackServer is running at: http://localhost:4567");
    }
}
