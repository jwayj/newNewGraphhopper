package com.graphhopper.example;
    
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.shapes.GHPoint;

public class RoutingExample {

    // 사용자 경사 선호: "LOW", "NORMAL", "HIGH" 중 하나를 문자열로 입력 (예: "NORMAL")
    private static final String SLOPE_PREFERENCE = "HIGH";

    
    public static void main(String[] args) {
    
    try {
        FileInputStream serviceAccount = new FileInputStream("backend/runpt-aaae1-firebase-adminsdk-fbsvc-620de08279.json");
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build();
        FirebaseApp.initializeApp(options);
    } catch (IOException e) {
        e.printStackTrace();
        return;
    } 

    // ✅ 1. 피드백 서버 시작
    FeedbackServer.start();

    // ✅ 2. 피드백 파일 로드 (새 JSON 구조 대응)
    Set<Integer> penalizedEdgeIds = new HashSet<>();
    try (Reader reader = new FileReader("example/resources/feedback_log.json")) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, List<Map<String, Integer>>>>() {}.getType();
        Map<String, List<Map<String, Integer>>> feedback = gson.fromJson(reader, type);

        for (List<Map<String, Integer>> edgeList : feedback.values()) {
            for (Map<String, Integer> edgeObj : edgeList) {
                if (edgeObj.containsKey("edge")) {
                    penalizedEdgeIds.add(edgeObj.get("edge"));
                }
            }
        }

        System.out.println("✅ feedback_log.json 에서 penalized edge IDs 로드 완료: " + penalizedEdgeIds);
    } catch (Exception e) {
        System.err.println("❌ feedback_log.json 읽기 실패: " + e.getMessage());
    }

     // ✅ 3. gpkg 파일에서 경사도 데이터를 읽어오는 테스트 코드 추가
     System.out.println("=== gpkg slope 데이터 로딩 테스트 ===");
     // 이 값은 gpkg 파일 내에서 사용할 레이어 이름입니다. 실제 사용 중인 레이어 이름에 맞게 수정하세요.
     String slopeLayerName = "_";
     GpkgSlopeReader slopeReader = new GpkgSlopeReader(slopeLayerName);
     // load된 slope 데이터 전체를 가져오기 (osm_id와 aggregated_angle_angle의 매핑)
     Map<Long, Double> slopeData = slopeReader.getAllSlopeData();
     
     if (slopeData.isEmpty()) {
         System.out.println("gpkg 파일로부터 경사도 데이터를 불러오지 못했습니다. 레이어 이름과 파일을 확인하세요.");
     } else {
         System.out.println("gpkg 파일로부터 읽은 경사도 데이터:");
         slopeData.forEach((osmId, slope) -> 
             System.out.println("osm_id: " + osmId + ", aggregated_angle_angle: " + slope)
         );
     }
     System.out.println("====================================\n");

    // ✅ 4. GraphHopper 인스턴스 생성 (penalizedEdges 함께 전달)
    double desiredDistance = 5500;
    String relDir = System.getProperty("user.dir") + File.separator;
    GraphHopper hopper = createGraphHopperInstance(relDir + "seoul-non-military.osm.pbf", penalizedEdgeIds);

    // 최신 방식: 프로필("foot")을 통해 기본 Weighting 생성
        Profile footProfile = hopper.getProfile("foot");
        if (footProfile == null) {
            throw new IllegalArgumentException("Profile 'foot' not found.");
        }
        com.graphhopper.util.PMap pmap = new com.graphhopper.util.PMap();
        Weighting baseWeighting = hopper.createWeighting(footProfile, pmap);

        // 사용자 선호 옵션 문자열을 enum으로 변환 후 CustomSlopeEncodedValue 생성
        CustomSlopeEncodedValue.SlopePreference pref = CustomSlopeEncodedValue.SlopePreference.valueOf(SLOPE_PREFERENCE);
        CustomSlopeEncodedValue slopeEV = new CustomSlopeEncodedValue("custom_slope_penalty", slopeData, pref);

        // 기본 Weighting과 slopeEV를 사용하여 CustomSlopeWeighting 생성
        CustomSlopeWeighting customWeighting = new CustomSlopeWeighting(baseWeighting, slopeEV);

    // ✅ 5. 경로 계산 요청
    GHPoint start = new GHPoint(37.566535, 126.977969);
    GHPoint end = new GHPoint(37.5581, 126.9458);

    GHRequest request = new GHRequest(start, end).setProfile("foot");
    ResponsePath path = hopper.route(request).getBest();

    System.out.println("🚶 경로 거리: " + path.getDistance() + "m");



        ResponsePath path1 = routingWithDesiredDistance(hopper, desiredDistance, start, end, customWeighting);
        if (path1 != null) {
            System.out.println("경로 거리: " + path1.getDistance() + " 미터");
            
            String geoJson1 = GeoJsonExporter1.toGeoJSON(path1);
            System.out.println("GeoJSON:\n" + geoJson1);
            try {
                SaveGeoJson.saveToFile(geoJson1, "example/resources/route1.geojson");
                System.out.println("GeoJSON1 saved to route1.geojson");

                // Firestore 인스턴스 가져오기
                Firestore db = FirestoreClient.getFirestore();

                // 저장할 맵 구성
                Map<String,Object> doc = new HashMap<>();
                doc.put("content", geoJson1);

                // ① add(doc) 한 번만 호출해서 Future 얻기
                ApiFuture<DocumentReference> future = db.collection("geojson").add(doc);

                try {
                // ② 결과 대기 및 로그 출력
                    DocumentReference ref = future.get();
                    System.out.println("Uploaded to Firestore geojson collection, doc ID: " + ref.getId());
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
               
            } catch (Exception e) {
                System.err.println("Error saving GeoJSON: " + e.getMessage());
            }
        } else {
            System.out.println("원하는 거리의 경로를 찾을 수 없습니다.(RoutingWithDesiredDistance)");
        }
    
        // 🚀 랜덤 경유지 생성 (500m ~ 1500m 범위 내 3개)
        PointList randomWaypoints = generateRandomWaypoints(hopper, start, 3, 500, 1500);
        System.out.println("🔍 route()에서 강제 삽입된 랜덤 경유지: " + randomWaypoints);

        // 🚀 랜덤 경유지를 사용하여 경로 탐색
        ResponsePath path2 = findPathWithWaypoints(hopper, start, randomWaypoints, penalizedEdgeIds);

        if (path2 != null) {
            System.out.println("✅ 새로운 경로 거리: " + path2.getDistance() + " 미터");

            String geoJson2 = GeoJsonExporter1.toGeoJSON(path2);
            System.out.println("GeoJSON:\n" + geoJson2);
            try {
                SaveGeoJson.saveToFile(geoJson2, "example/resources/route2.geojson");
                System.out.println("📂 GeoJSON2 saved to route2.geojson");
            } catch (Exception e) {
                System.err.println("❌ Error saving GeoJSON: " + e.getMessage());
            }
        } else {
            System.out.println("❌ 경로를 찾을 수 없습니다.");
        }

        try {
            // 출발지 정의
            GHPoint startPoint = new GHPoint(37.566535, 126.977969); // 서울 시청 근처->이화여대
            List<Integer> globalAvoidEdges = new ArrayList<>(); // 🔥 전역적으로 Edge 회피 저장
            PointList globalAvoidPoints = new PointList(); // 🔥 전역적으로 Point 회피 저장
            ResponsePath previousPath = null;
        
            for (int attempt = 0; attempt < 3; attempt++) { // 🔥 3번의 다른 경로 탐색 시도
                System.out.println("🚀 " + (attempt + 1) + "번째 경로 탐색 시작...");
        
                ResponsePath diversePath = findDiverseOptimalPath(
                    hopper, startPoint, desiredDistance, globalAvoidEdges, globalAvoidPoints, penalizedEdgeIds, customWeighting
                );
                        
                if (diversePath != null) {
                    System.out.println("✅ 최종 경로 거리: " + diversePath.getDistance() + " 미터");
        
                    // 📌 경로가 동일하면 다시 시도하도록 설정
                    if (previousPath != null && Math.abs(diversePath.getDistance() - previousPath.getDistance()) < 5) {
                        System.out.println("⚠️ 동일한 경로가 감지됨. 다시 탐색...");
                        continue;
                    }
        
                    // 📌 diversePath에서 PointList 추출
                    PointList pathPoints = diversePath.getPoints();
        
                    // 📌 GeoJSON 생성
                    String geoJson = GeoJsonExporter2.toGeoJSON(diversePath, new PointList(), pathPoints);
                    System.out.println("GeoJSON:\n" + geoJson);
        
                    // 📌 GeoJSON 저장->경로 example파일로
                    SaveGeoJson.saveToFile(geoJson, "example/resources/route.geojson");
                     System.out.println("GeoJSON saved to route.geojson");

                    
                    List<String> geoJsonList = new ArrayList<>();

                    if (path1 != null) {
                        String geoJson1 = GeoJsonExporter1.toGeoJSON(path1);
                        geoJsonList.add(geoJson1);
                    }

                    if (path2 != null) {
                        String geoJson2 = GeoJsonExporter1.toGeoJSON(path2);
                        geoJsonList.add(geoJson2);
                    }

                    if (diversePath != null) {
                        pathPoints = diversePath.getPoints();
                        String geoJson3 = GeoJsonExporter2.toGeoJSON(diversePath, new PointList(), pathPoints);
                        geoJsonList.add(geoJson3);
                    }
                    
        
                    // 🔥 회피할 Edge 및 Points 저장
                    for (int i = 0; i < pathPoints.size(); i++) {
                        globalAvoidPoints.add(pathPoints.getLat(i), pathPoints.getLon(i));
                    }
                    diversePath.getPathDetails().getOrDefault("edge_id", new ArrayList<>())
                            .forEach(detail -> globalAvoidEdges.add((Integer) detail.getValue()));
        
                    previousPath = diversePath;
                } else {
                    System.out.println("❌ 적절한 경로를 찾을 수 없습니다.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            hopper.close();
        }
    
        
    }

    public static ResponsePath routingWithDesiredDistance(GraphHopper hopper, double desiredDistance, GHPoint start, GHPoint end,Weighting customWeighting) {
        double tolerance = 100; // 200미터 오차 허용
        double searchRadius = desiredDistance * 0.75; // 원하는 거리의 75%로 검색 반경 설정
        
        ResponsePath bestPath = null;
        double closestDifference = Double.MAX_VALUE;
        
        LocationIndex locationIndex = hopper.getLocationIndex();
        List<GHPoint> nearbyPoints = new ArrayList<>();
        
    
        // 여러 개의 가까운 지점을 찾기
        for (int i = 0; i < 10000; i++) { // 포인트 생성 수 증가
            double angle = Math.random() * 2 * Math.PI;
            double distance = Math.random() * searchRadius;
            double lat = start.lat + (distance / 111000) * Math.cos(angle);
            double lon = start.lon + (distance / (111000 * Math.cos(Math.toRadians(start.lat)))) * Math.sin(angle);
            Snap qr = locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
            if (qr.isValid()) {
                GHPoint nearbyPoint = qr.getSnappedPoint();
                nearbyPoints.add(nearbyPoint);
            }
        }
        
        // 랜덤으로 지점 선택
        Collections.shuffle(nearbyPoints);
        
        // 각 랜덤 지점에 대해 경로 찾기
        for (GHPoint intermediatePoint : nearbyPoints) {
            ResponsePath path1 = findPath(hopper, start, intermediatePoint, "foot",customWeighting);
            ResponsePath path2 = findPath(hopper, intermediatePoint, end, "foot",customWeighting);

            
            if (path1 != null && path2 != null) {
                double totalDistance = path1.getDistance() + path2.getDistance();
                double difference = Math.abs(totalDistance - desiredDistance);
                
                if (difference < closestDifference) {
                    closestDifference = difference;
                    bestPath = combinePaths(path1, path2);
                    
                    if (difference <= tolerance) {
                        return bestPath; // 충분히 가까운 경로를 찾았으면 즉시 반환
                    }
                }
            }
        }
    
        if (bestPath == null) {
            System.out.println("원하는 거리의 경로를 찾을 수 없습니다. 직접 연결 경로를 반환합니다.");
            return findPath(hopper, start, end, "foot",customWeighting);
        }
        
        System.out.println("가장 가까운 경로를 찾았습니다. 차이: " + closestDifference + " 미터");
        return bestPath;
    }
    
    static PointList generateRandomWaypoints(GraphHopper hopper, GHPoint start, int numWaypoints, double minDistance, double maxDistance) {
        Random random = new Random();
        LocationIndex locationIndex = hopper.getLocationIndex();
        PointList waypoints = new PointList();
    
        for (int i = 0; i < numWaypoints; i++) {
            for (int attempts = 0; attempts < 100; attempts++) { // 최대 100번 시도
                double distance = minDistance + (maxDistance - minDistance) * random.nextDouble();
                double angle = random.nextDouble() * 2 * Math.PI;
    
                double deltaLat = (distance / 111000) * Math.cos(angle);
                double deltaLon = (distance / (111000 * Math.cos(Math.toRadians(start.lat)))) * Math.sin(angle);
    
                double lat = start.lat + deltaLat;
                double lon = start.lon + deltaLon;
    
                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
    
                GHPoint candidate = new GHPoint(lat, lon);
                Snap snap = locationIndex.findClosest(candidate.lat, candidate.lon, EdgeFilter.ALL_EDGES);
    
                if (snap.isValid()) {
                    boolean isTooClose = false;
                    GHPoint snappedPoint = snap.getSnappedPoint();
                    
                    for (int j = 0; j < waypoints.size(); j++) {
                        double existingLat = waypoints.getLat(j);
                        double existingLon = waypoints.getLon(j);
                        GHPoint existingPoint = new GHPoint(existingLat, existingLon);
    
                        // calculateDistance 메서드 호출
                        if (calculateDistance(existingPoint, snappedPoint) < minDistance / 2) {
                            isTooClose = true;
                            break;
                        }
                    }
    
                    if (!isTooClose) {
                        waypoints.add(snappedPoint.lat, snappedPoint.lon);
                        break;
                    }
                }
            }
        }
    
        return waypoints;
    } 

    private static ResponsePath findPath(GraphHopper hopper, GHPoint start, GHPoint end, String profile, Weighting customWeighting) {
        GHRequest req = new GHRequest(start, end)
            .setAlgorithm(Parameters.Algorithms.ASTAR_BI)
            .setProfile(profile);
        GHResponse rsp = hopper.route(req);
        if (rsp.hasErrors()) {
            return null;
        }
        return rsp.getBest();
    }

    private static ResponsePath combinePaths(ResponsePath path1, ResponsePath path2) {
        ResponsePath combinedPath = new ResponsePath();
        
        // 포인트 리스트 병합
        PointList combinedPoints = new PointList(path1.getPoints().size() + path2.getPoints().size() - 1, path1.getPoints().is3D());
        combinedPoints.add(path1.getPoints());
        combinedPoints.add(path2.getPoints().copy(1, path2.getPoints().size()));
        combinedPath.setPoints(combinedPoints);
        
        // 거리, 시간, 가중치 합산
        combinedPath.setDistance(path1.getDistance() + path2.getDistance());
        combinedPath.setTime(path1.getTime() + path2.getTime());
        combinedPath.setRouteWeight(path1.getRouteWeight() + path2.getRouteWeight());
        
        // 안내 정보 병합
        InstructionList combinedInstructions = new InstructionList(path1.getInstructions().getTr());
        combinedInstructions.addAll(path1.getInstructions());
        combinedInstructions.addAll(path2.getInstructions());
        combinedPath.setInstructions(combinedInstructions);
        
        // 경로 세부 정보 병합
        Map<String, List<PathDetail>> combinedDetails = new HashMap<>();
        for (Map.Entry<String, List<PathDetail>> entry : path1.getPathDetails().entrySet()) {
            combinedDetails.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        for (Map.Entry<String, List<PathDetail>> entry : path2.getPathDetails().entrySet()) {
            combinedDetails.merge(entry.getKey(), entry.getValue(), (v1, v2) -> {
                v1.addAll(v2);
                return v1;
            });
        }
        combinedPath.addPathDetails(combinedDetails);


        
        // 기타 필요한 정보 설정
        combinedPath.setAscend(path1.getAscend() + path2.getAscend());
        combinedPath.setDescend(path1.getDescend() + path2.getDescend());
        
        return combinedPath;
    }

    static double calculateDistance(GHPoint point1, GHPoint point2) {
        double earthRadius = 6371000; // 지구 반지름 (미터 단위)
        double dLat = Math.toRadians(point2.lat - point1.lat);
        double dLon = Math.toRadians(point2.lon - point1.lon);
        double lat1 = Math.toRadians(point1.lat);
        double lat2 = Math.toRadians(point2.lat);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadius * c;
    }
    static GraphHopper createGraphHopperInstance(String osmFilePath, Set<Integer> penalizedEdgeIds) {
        GraphHopper hopper = new GraphHopper();
    
        GraphHopperConfig config = new GraphHopperConfig()
            .putObject("graph.location", "target/routing-graph-cache")
            .putObject("datareader.file", osmFilePath)
            //.putObject("graph.encoded_values", "edge_id") // 👈 이거 꼭 필요! edge_id 접근 가능하게
            //.putObject("graph.encoded_values", "aggregated_angle_angle") // aggregated_angle_angle에 접근하기 위해
            .putObject("import.osm.ignored_highways", "platform,rest_area,services");
    
        // ✅ CustomModel 생성 및 페널티 적용
        // ⚠️ 여기선 avoidEdges 쓰면 안 됨!

        CustomModel model = new CustomModel();
        model.addToSpeed(If("true", LIMIT, "5"));

        // ✅ penalizedEdgeIds만 적용 (createGraphHopperInstance에서)
        for (Integer edgeId : penalizedEdgeIds) {
            model.addToPriority(If("edge_id == " + edgeId, MULTIPLY, "0.1"));
        }

        // ===== 경사도 조건 추가 (SLOPE_PREFERENCE에 따른 조건 적용) =====
        // 예를 들어, SLOPE_PREFERENCE가 "NORMAL"이면, 0.7637 ~ 3.1911 범위를 선호하도록
        // 해당 범위에 포함되면 multiplier 1.0, 그 외에선 multiplier 5.0을 적용하도록 합니다.
        /* 
        if ("NORMAL".equals(SLOPE_PREFERENCE)) {
            model.getPriority().add(
                Statement.If("aggregated_angle_angle < 0.7637 || aggregated_angle_angle > 3.1911", MULTIPLY, "5.0")
            );
            model.getPriority().add(Statement.Else(MULTIPLY, "1.0"));
        } else if ("LOW".equals(SLOPE_PREFERENCE)) {
        // LOW를 선호하는 경우: 낮은 경사를 우대하기 위해, 경사도가 0.7637보다 큰 경우에만 페널티 부과
            model.getPriority().add(
                Statement.If("aggregated_angle_angle > 0.7637", MULTIPLY, "5.0")
            );
            model.getPriority().add(Statement.Else(MULTIPLY, "1.0"));
        } else if ("HIGH".equals(SLOPE_PREFERENCE)) {
        // HIGH를 선호하는 경우: 높은 경사를 우대하기 위해, 경사도가 3.1911보다 작은 경우에만 페널티 부과
            model.getPriority().add(
                Statement.If("aggregated_angle_angle < 3.1911", MULTIPLY, "5.0")
            );
            model.getPriority().add(Statement.Else(MULTIPLY, "1.0"));
        }
            */
    // =========================================================

    // 거리 영향도 설정
    model.setDistanceInfluence(70.0);

    
        // ✅ Profile 설정
        config.setProfiles(List.of(
            new Profile("foot")
                .setWeighting("custom")
                .setCustomModel(model)
        ));
    
        hopper.getCHPreparationHandler().setCHProfiles(List.of()); // CH 비활성화
        hopper.init(config);
        hopper.importOrLoad();
    
        return hopper;
    }
    
    //-----------------여기서부터가 cycle 만들때 필요한 함수 추가(수정)---------------------
    //다양한 경로 생성
    // 🚀 1. 새로운 랜덤 경유지 생성 (더 넓은 범위에서)
    static PointList generateDiverseWaypoints(GraphHopper hopper, GHPoint start, int numWaypoints, double minDistance, double maxDistance) {
        Random random = new Random();
        LocationIndex locationIndex = hopper.getLocationIndex();
        PointList waypoints = new PointList();
        
        List<GHPoint> usedPoints = new ArrayList<>();
    
        for (int i = 0; i < numWaypoints; i++) {
            for (int attempts = 0; attempts < 50; attempts++) {  // 🔥 시도 횟수 줄이기
                double distance = minDistance + (maxDistance - minDistance) * random.nextDouble();
                double angle = random.nextDouble() * 2 * Math.PI;
    
                double deltaLat = (distance / 111000) * Math.cos(angle);
                double deltaLon = (distance / (111000 * Math.cos(Math.toRadians(start.lat)))) * Math.sin(angle);
    
                double lat = start.lat + deltaLat;
                double lon = start.lon + deltaLon;
    
                GHPoint candidate = new GHPoint(lat, lon);
                Snap snap = locationIndex.findClosest(candidate.lat, candidate.lon, EdgeFilter.ALL_EDGES);
    
                if (snap.isValid()) {
                    GHPoint snappedPoint = snap.getSnappedPoint();
                    
                    // 📌 **중복된 지점 회피 + 거리 조건 완화**
                    boolean isValid = true;
                    for (GHPoint used : usedPoints) {
                        double dist = calculateDistance(used, snappedPoint);
                        if (dist < minDistance * 0.8 || dist > maxDistance * 1.2) { // 🔥 오차 허용 범위 추가
                            isValid = false;
                            break;
                        }
                    }
    
                    if (isValid) {
                        waypoints.add(snappedPoint.lat, snappedPoint.lon);
                        usedPoints.add(snappedPoint);
                        break;
                    }
                }
            }
        }
    
        return waypoints;
    }

    // 🚀 2. 경로 탐색 시 동일한 경로 회피 (강제적으로 다른 경로 찾기)
    static ResponsePath findDifferentPath(GraphHopper hopper, GHPoint start, GHPoint end, PointList avoidPoints, List<Integer> avoidEdges) {
        GHRequest request = new GHRequest()
            .addPoint(start)
            .addPoint(end)
            .setProfile("foot")
            .setAlgorithm("astarbi")  // 🔥 CH와 호환되는 알고리즘으로 변경
            .putHint("ch.disable", true);  // 🔥 CH 비활성화
    
        if (!avoidPoints.isEmpty()) {
            request.putHint("routing.avoid_points", avoidPoints);
        }
    
        if (!avoidEdges.isEmpty()) {
            request.putHint("routing.avoid_edges", avoidEdges);
            System.out.println("🚧 Avoiding edges: " + avoidEdges);
        }
    
        GHResponse response = hopper.route(request);
        if (response.hasErrors()) {
            System.err.println("❌ 경로 탐색 오류: " + response.getErrors());
            return null;
        }
    
        ResponsePath bestPath = response.getBest();
        if (bestPath == null || bestPath.getDistance() < 50) { // 🔥 너무 짧은 경로면 다시 시도
            System.out.println("⚠️ 경로가 너무 짧음. 다시 탐색...");
            return null;
        }
    
        // 🔥 **모든 지나온 Edge를 회피하도록 설정 (강력한 회피 적용)**
        List<PathDetail> edgeDetails = bestPath.getPathDetails().getOrDefault("edge_id", new ArrayList<>());
        for (PathDetail detail : edgeDetails) {
            avoidEdges.add((Integer) detail.getValue());
        }
    
        return bestPath;
    }

    static ResponsePath findDiverseOptimalPath(GraphHopper hopper, GHPoint startPoint, double desiredDistance,
    List<Integer> avoidEdges, PointList avoidPoints,
    Set<Integer> penalizedEdgeIds, Weighting customWeighting) {

    int numWaypoints = 3;
    double minDistance = desiredDistance * 0.15;
    double maxDistance = desiredDistance * 0.4;
    double lowerBound = desiredDistance * 0.9;
    double upperBound = desiredDistance * 1.1;

    PointList waypoints = generateDiverseWaypoints(hopper, startPoint, numWaypoints, minDistance, maxDistance);
    ResponsePath fullPath = null;
    GHPoint previousPoint = startPoint;
    double totalDistance = 0;

    if (waypoints.isEmpty()) {
        System.out.println("❌ 유효한 경유지를 찾지 못했습니다. 기본 경로를 사용합니다.");
        return findPath(hopper, startPoint, startPoint, "foot", customWeighting);
    }

    for (int i = 0; i < waypoints.size(); i++) {
        GHPoint waypoint = new GHPoint(waypoints.getLat(i), waypoints.getLon(i));

        // ✅ 회피용 CustomModel 생성
        CustomModel customModel = new CustomModel();
        for (Integer edgeId : avoidEdges) {
            // edge_id가 해당하는 곳의 우선순위를 낮춤
            customModel.addToPriority(If("edge_id == " + edgeId, MULTIPLY, "0.1"));
        }

        GHRequest req = new GHRequest(previousPoint, waypoint)
            .setProfile("foot")
            .setCustomModel(customModel)
            .putHint("ch.disable", true);

        GHResponse rsp = hopper.route(req);
        if (rsp.hasErrors()) {
            System.out.println("❌ 경유지 경로 탐색 실패: " + rsp.getErrors());
            continue;
        }

        ResponsePath segment = rsp.getBest();
        fullPath = (fullPath == null) ? segment : combinePaths(fullPath, segment);
        totalDistance += segment.getDistance();

        // ✅ 지나온 Edge 기록
        segment.getPathDetails().getOrDefault("edge_id", new ArrayList<>())
            .forEach(detail -> avoidEdges.add((Integer) detail.getValue()));

        avoidPoints.add(waypoint.lat, waypoint.lon);
        previousPoint = waypoint;
    }

    // ✅ 돌아오는 경로에도 회피 적용
    CustomModel customModel = new CustomModel();
    for (Integer edgeId : avoidEdges)
    customModel.addToPriority(If("edge_id == " + edgeId, MULTIPLY, "0.1"));

    for (Integer edgeId : penalizedEdgeIds)
    customModel.addToPriority(If("edge_id == " + edgeId, MULTIPLY, "0.1"));

    GHRequest returnReq = new GHRequest(previousPoint, startPoint)
        .setProfile("foot")
        .setCustomModel(customModel)
        .putHint("ch.disable", true);

    GHResponse returnRsp = hopper.route(returnReq);
    if (!returnRsp.hasErrors()) {
        ResponsePath returnSegment = returnRsp.getBest();
        fullPath = (fullPath == null) ? returnSegment : combinePaths(fullPath, returnSegment);
        totalDistance += returnSegment.getDistance();
    }

    // ✅ 거리 체크
    if (totalDistance < lowerBound || totalDistance > upperBound) {
        System.out.println("❌ 경로 거리 초과 또는 부족. 다시 탐색...");
        return findDiverseOptimalPath(hopper, startPoint, desiredDistance, avoidEdges, avoidPoints, penalizedEdgeIds,customWeighting);
    }

    return fullPath;
}

    static ResponsePath findAlternativeReturnPath(GraphHopper hopper, GHPoint start, GHPoint end, List<Integer> avoidEdges, PointList avoidPoints) {
        GHRequest request = new GHRequest()
            .addPoint(start)
            .addPoint(end)
            .setProfile("foot")
            .setAlgorithm(Parameters.Algorithms.ALT_ROUTE)  // 🔥 기존 경로를 강하게 회피
            .putHint("ch.disable", true)  // 🔥 CH 비활성화하여 다양한 경로 탐색 가능
            .putHint("alternative_route.max_paths", 3)  // 🔥 최대 3개의 대체 경로 탐색
            .putHint("alternative_route.max_weight_factor", 3.0) // 🔥 최단 경로보다 3배 긴 경로도 허용
    
            // 🔥 지나온 Edge는 강제로 회피하도록 설정
            .putHint("routing.avoid_edges", avoidEdges);
    
        GHResponse response = hopper.route(request);
    
        if (response.hasErrors()) {
            System.out.println("❌ 대체 경로 탐색 실패: " + response.getErrors());
            return null;
        }
    
        return response.getBest();
    }

    static ResponsePath findPathWithWaypoints(GraphHopper hopper, GHPoint start, PointList waypoints, Set<Integer> penalizedEdgeIds) {
        CustomModel model = new CustomModel();
        for (Integer edgeId : penalizedEdgeIds) {
            model.addToPriority(If("edge_id == " + edgeId, MULTIPLY, "0.1"));
        }
    
        GHRequest req = new GHRequest()
            .setProfile("foot")
            .setCustomModel(model) // 🔥 여기!
            .setAlgorithm(Parameters.Algorithms.ALT_ROUTE)
            .putHint("ch.disable", true)
            .putHint("alternative_route.max_paths", 3)
            .putHint("alternative_route.max_weight_factor", 2.0)
            .addPoint(start);
    
        for (int i = 0; i < waypoints.size(); i++) {
            req.addPoint(new GHPoint(waypoints.getLat(i), waypoints.getLon(i)));
        }
    
        req.addPoint(start);
    
        GHResponse rsp = hopper.route(req);
        if (rsp.hasErrors()) {
            System.out.println("❌ 경로 탐색 실패: " + rsp.getErrors());
            return null;
        }
    
        return rsp.getBest();
    }    
}
