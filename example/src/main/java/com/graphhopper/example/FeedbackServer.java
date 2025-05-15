package com.graphhopper.example;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.json.JSONObject;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.gson.Gson;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.staticFiles;

public class FeedbackServer {
    public static void start() {
        try {
            FileInputStream serviceAccount = new FileInputStream(
                    "backend/runpt-aaae1-firebase-adminsdk-fbsvc-24d537642e.json");
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            System.out.println("✅ Firebase 초기화 성공");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("❌ Firebase 초기화 실패");
            return;
        }

        port(4567);
        staticFiles.externalLocation("example/resources"); // HTML 정적 파일 경로

        post("/submit", (req, res) -> {
            System.out.println("📬 요청 수신:\\n" + req.body());

            Gson gson = new Gson();
            Map<String, Object> input = gson.fromJson(req.body(), Map.class);
            // 요청값 파싱 및 RoutingExample 필드에 저장
            double startLat = Double.parseDouble(input.get("startLat").toString());
            double startLon = Double.parseDouble(input.get("startLon").toString());
            double distance = Double.parseDouble(input.get("distance").toString());
            boolean same = Boolean.parseBoolean(input.getOrDefault("same", "false").toString());
            // slope 처리
            String slopeStr = input.get("slope") != null
                    ? input.get("slope").toString()
                    : null;
            CustomSlopeEncodedValue.SlopePreference slopePref = null;
            if (slopeStr != null) {
                slopePref = CustomSlopeEncodedValue.SlopePreference.valueOf(slopeStr);
            }
            // 이제 slopePref를 RoutingExample 필드에 저장
            CustomSlopeEncodedValue.SlopePreference pref=slopePref;

            CustomSlopeEncodedValue slopeEV = new CustomSlopeEncodedValue("custom_slope_penalty", RoutingExample.slopeData, pref);

            // 기본 Weighting과 slopeEV를 사용하여 CustomSlopeWeighting 생성
            RoutingExample.customWeighting = new CustomSlopeWeighting(RoutingExample.baseWeighting, slopeEV);
            ResponsePath path;

            // firestore 선언
            Firestore db;

            if (same) {
                // 원형 경로: 출발 == 도착, 랜덤 경유지 경로
                GHPoint start = new GHPoint(startLat, startLon);
                PointList waypoints = RoutingExample.generateRandomWaypoints(RoutingExample.hopper, start, 3, 500,
                        1500);
                path = RoutingExample.findPathWithWaypoints(RoutingExample.hopper, start, waypoints);

                // 2. 다양화된 원형 경로도 생성
                List<Integer> avoidEdges = new ArrayList<>();
                PointList avoidPoints = new PointList();
                ResponsePath diversePath = RoutingExample.findDiverseOptimalPath(
                        RoutingExample.hopper, start, distance, avoidEdges, avoidPoints,
                        new HashSet<>(), RoutingExample.customWeighting, 1);

                if (diversePath != null) {
                    PointList points = diversePath.getPoints();
                    String geoJson = GeoJsonExporter2.toGeoJSON(diversePath, new PointList(), points);
                    SaveGeoJson.saveToFile(geoJson, "example/resources/route1.geojson");
                    System.out.println("📂 route1.geojson 저장 완료");

                    // Firestore 인스턴스 가져오기
                    db = FirestoreClient.getFirestore();

                    // 저장할 맵 구성
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("content", geoJson);
                    String geoJsonId = "";
                    // ① add(doc) 한 번만 호출해서 Future 얻기
                    ApiFuture<DocumentReference> future = db.collection("geojson").add(doc);
                    try {
                        DocumentReference ref = future.get();
                        System.out.println("✅ Firestore 저장 성공, 문서 ID: " + ref.getId());
                        geoJsonId = ref.getId();
                    } catch (InterruptedException | ExecutionException e) {
                        System.err.println("❌ Firestore 저장 실패");
                        e.printStackTrace();
                    }

                    // ① 메타 JSON 객체 생성
                    JSONObject meta = new JSONObject();
                    meta.put("geoJsonId", geoJsonId);

                    // ② 웹 서버가 읽는 경로에 덮어쓰기
                    Path metaPath = Path.of("example/resources/route_meta.json");
                    Files.write(metaPath,
                            meta.toString(2).getBytes(StandardCharsets.UTF_8));
                    System.out.println("▶ route_meta.json 업데이트 완료: " + geoJsonId);

                    try {
                        // ② 결과 대기 및 로그 출력
                        DocumentReference ref = future.get();
                        System.out.println("Uploaded to Firestore geojson collection, doc ID: " + ref.getId());
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("❌ route.geojson 저장 실패: 경로가 null입니다.");
                }

            } else {
                // 출발지 ≠ 도착지: 원하는 거리 기반의 직선 경로
                double endLat = Double.parseDouble(input.get("endLat").toString());
                double endLon = Double.parseDouble(input.get("endLon").toString());

                GHPoint start = new GHPoint(startLat, startLon);
                GHPoint end = new GHPoint(endLat, endLon);

                // 1. 거리 기반 경로 생성
                ResponsePath path1 = RoutingExample.routingWithDesiredDistance(
                        RoutingExample.hopper, distance, start, end, RoutingExample.customWeighting);

                if (path1 != null) {
                    String geoJson1 = GeoJsonExporter1.toGeoJSON(path1);
                    SaveGeoJson.saveToFile(geoJson1, "example/resources/route1.geojson");
                    System.out.println("📂 route1.geojson 저장 완료");

                    // Firestore 인스턴스 가져오기
                    db = FirestoreClient.getFirestore();

                    // 저장할 맵 구성
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("content", geoJson1);
                    String geoJsonId = "";
                    // ① add(doc) 한 번만 호출해서 Future 얻기
                    ApiFuture<DocumentReference> future = db.collection("geojson").add(doc);
                    try {
                        DocumentReference ref = future.get();
                        System.out.println("✅ Firestore 저장 성공, 문서 ID: " + ref.getId());
                        geoJsonId = ref.getId();
                    } catch (InterruptedException | ExecutionException e) {
                        System.err.println("❌ Firestore 저장 실패");
                        e.printStackTrace();
                    }

                    // ① 메타 JSON 객체 생성
                    JSONObject meta = new JSONObject();
                    meta.put("geoJsonId", geoJsonId);

                    // ② 웹 서버가 읽는 경로에 덮어쓰기
                    Path metaPath = Path.of("example/resources/route_meta.json");
                    Files.write(metaPath,
                            meta.toString(2).getBytes(StandardCharsets.UTF_8));
                    System.out.println("▶ route_meta.json 업데이트 완료: " + geoJsonId);

                    try {
                        // ② 결과 대기 및 로그 출력
                        DocumentReference ref = future.get();
                        System.out.println("Uploaded to Firestore geojson collection, doc ID: " + ref.getId());
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("❌ route1.geojson 저장 실패: 경로가 null입니다.");
                }

            }

            System.out.println("✅ 경로 생성 및 저장 완료");
            res.type("text/plain");
            return "🟢 경로 생성 성공";
        });
    }
}