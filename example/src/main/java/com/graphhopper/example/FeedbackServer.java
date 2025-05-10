package com.graphhopper.example;

import com.google.gson.Gson;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import java.util.*;

import static spark.Spark.*;

public class FeedbackServer {

    public static void start() {
        port(4567);
        staticFiles.externalLocation("example/resources"); // HTML 정적 파일 경로

        post("/submit", (req, res) -> {
            System.out.println("📬 요청 수신:\n" + req.body());

            Gson gson = new Gson();
            Map<String, Object> input = gson.fromJson(req.body(), Map.class);

            // 요청값 파싱 및 RoutingExample 필드에 저장
            double startLat = Double.parseDouble(input.get("startLat").toString());
            double startLon = Double.parseDouble(input.get("startLon").toString());
            double distance = Double.parseDouble(input.get("distance").toString());
            boolean same = Boolean.parseBoolean(input.getOrDefault("same", "false").toString());

            ResponsePath path;

            if (same) {
                // 원형 경로: 출발 == 도착, 랜덤 경유지 경로
                GHPoint start = new GHPoint(startLat, startLon);
                PointList waypoints = RoutingExample.generateRandomWaypoints(RoutingExample.hopper, start, 3, 500, 1500);
                path = RoutingExample.findPathWithWaypoints(RoutingExample.hopper, start, waypoints);

                if (path != null) {
                    String geoJson = GeoJsonExporter1.toGeoJSON(path);
                    SaveGeoJson.saveToFile(geoJson, "example/resources/route2.geojson");
                    System.out.println("📂 route2.geojson 저장 완료");
                } else {
                    System.err.println("❌ route2.geojson 저장 실패: 경로가 null입니다.");
                }

            } else {
                // 출발지 ≠ 도착지: 원하는 거리 기반의 직선 경로
                double endLat = Double.parseDouble(input.get("endLat").toString());
                double endLon = Double.parseDouble(input.get("endLon").toString());

                GHPoint start = new GHPoint(startLat, startLon);
                GHPoint end = new GHPoint(endLat, endLon);

                // 1. 거리 기반 경로 생성
                ResponsePath path1 = RoutingExample.routingWithDesiredDistance(
                    RoutingExample.hopper, distance, start, end, RoutingExample.customWeighting
                );

                if (path1 != null) {
                    String geoJson1 = GeoJsonExporter1.toGeoJSON(path1);
                    SaveGeoJson.saveToFile(geoJson1, "example/resources/route1.geojson");
                    System.out.println("📂 route1.geojson 저장 완료");
                } else {
                    System.err.println("❌ route1.geojson 저장 실패: 경로가 null입니다.");
                }

                // 2. 다양화된 원형 경로도 생성
                List<Integer> avoidEdges = new ArrayList<>();
                PointList avoidPoints = new PointList();
                ResponsePath diversePath = RoutingExample.findDiverseOptimalPath(
                    RoutingExample.hopper, start, distance, avoidEdges, avoidPoints,
                    new HashSet<>(), RoutingExample.customWeighting
                );

                if (diversePath != null) {
                    PointList points = diversePath.getPoints();
                    String geoJson = GeoJsonExporter2.toGeoJSON(diversePath, new PointList(), points);
                    SaveGeoJson.saveToFile(geoJson, "example/resources/route.geojson");
                    System.out.println("📂 route.geojson 저장 완료");
                } else {
                    System.err.println("❌ route.geojson 저장 실패: 경로가 null입니다.");
                }
            }

            System.out.println("✅ 경로 생성 및 저장 완료");
            res.type("text/plain");
            return "🟢 경로 생성 성공";
        });
    }
}
