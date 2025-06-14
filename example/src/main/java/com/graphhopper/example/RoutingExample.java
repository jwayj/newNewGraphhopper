package com.graphhopper.example;
    
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

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
    public static GraphHopper hopper;
    public static RoutingExample routingExample;
    public static CustomSlopeWeighting customWeighting;

    public static double startLat;
    public static double startLon;
    public static double endLat;
    public static double endLon;
    public static boolean same;
    public static String slope;
    public static double distance;
    public static Map<Long, Double> slopeData;
    public static Weighting baseWeighting;
    //피드백 추가
    public static List<GHPoint> globalAvoidPoints = new ArrayList<>();

    public static void main(String[] args) {
    // ✅ 1. 피드백 서버 시작

    // // ✅ 2. 피드백 파일 로드 (새 JSON 구조 대응)
    // Set<Integer> penalizedEdgeIds = new HashSet<>();
    // InputStream is = RoutingExample.class.getResourceAsStream("/feedback_log.json");
    // try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
    //     Gson gson = new Gson();
    //     Type type = new TypeToken<Map<String, List<Map<String, Integer>>>>() {}.getType();
    //     Map<String, List<Map<String, Integer>>> feedback = gson.fromJson(reader, type);

    //     for (List<Map<String, Integer>> edgeList : feedback.values()) {
    //         for (Map<String, Integer> edgeObj : edgeList) {
    //             if (edgeObj.containsKey("edge")) {
    //                 penalizedEdgeIds.add(edgeObj.get("edge"));
    //             }
    //         }
    //     }

    //     System.out.println("✅ feedback_log.json 에서 penalized edge IDs 로드 완료: " + penalizedEdgeIds);
    // } catch (Exception e) {
    //     System.err.println("❌ feedback_log.json 읽기 실패: " + e.getMessage());
    // }

     // ✅ 3. gpkg 파일에서 경사도 데이터를 읽어오는 테스트 코드 추가
     System.out.println("=== gpkg slope 데이터 로딩 테스트 ===");
     // 이 값은 gpkg 파일 내에서 사용할 레이어 이름입니다. 실제 사용 중인 레이어 이름에 맞게 수정하세요.
     String slopeLayerName = "_";
     GpkgSlopeReader slopeReader = new GpkgSlopeReader(slopeLayerName);
     // load된 slope 데이터 전체를 가져오기 (osm_id와 aggregated_angle_angle의 매핑)
     slopeData = slopeReader.getAllSlopeData();
     
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
    // 이걸로 덮어쓰기
    RoutingExample.hopper = createGraphHopperInstance(relDir + "seoul-non-military.osm.pbf");

    // 최신 방식: 프로필("foot")을 통해 기본 Weighting 생성
   
        Profile footProfile = hopper.getProfile("foot");
        if (footProfile == null) {
            throw new IllegalArgumentException("Profile 'foot' not found.");
        }
        com.graphhopper.util.PMap pmap = new com.graphhopper.util.PMap();
        baseWeighting = hopper.createWeighting(footProfile, pmap);
    
        // 사용자 선호 옵션 문자열을 enum으로 변환 후 CustomSlopeEncodedValue 생성
        CustomSlopeEncodedValue.SlopePreference pref = CustomSlopeEncodedValue.SlopePreference.valueOf(SLOPE_PREFERENCE);
        CustomSlopeEncodedValue slopeEV = new CustomSlopeEncodedValue("custom_slope_penalty", slopeData, pref);

        // 기본 Weighting과 slopeEV를 사용하여 CustomSlopeWeighting 생성
        RoutingExample.customWeighting = new CustomSlopeWeighting(baseWeighting, slopeEV);

        // 🔽 여기에 삽입
        List<GHPoint> avoidPointsFromFile = new ArrayList<>();
        try (Reader reader = new FileReader("avoid_points.json")) {
            Type listType = new TypeToken<List<Map<String, Double>>>() {}.getType();
            List<Map<String, Double>> rawList = new Gson().fromJson(reader, listType);
            for (Map<String, Double> p : rawList) {
                avoidPointsFromFile.add(new GHPoint(p.get("lat"), p.get("lon")));
            }
            System.out.println("✅ avoid_points.json 로드됨, 회피 포인트 수: " + avoidPointsFromFile.size());
        } catch (Exception e) {
            System.err.println("❌ avoid_points.json 읽기 실패: " + e.getMessage());
        }

        // ✅ 전역 변수에 저장
        globalAvoidPoints = avoidPointsFromFile;


        //서버 시작 위치 변경
        try {
            FeedbackServer.start(80); // 예시
        } catch (IOException e) {
            e.printStackTrace();
        }

        
    }

    public static ResponsePath routingWithDesiredDistance(GraphHopper hopper, double desiredDistance, GHPoint start, GHPoint end,Weighting customWeighting, List<GHPoint> avoidPoints) {
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
                ResponsePath combinedPath = combinePaths(path1, path2); // ✅ [추가] 경로 결합 먼저 수행

                // ✅ [추가] 회피 지점 근처를 지나면 무시
                if (avoidPoints != null && pathTouchesAvoidPoint(combinedPath, avoidPoints, 50)) {
                    System.out.println("🚫 경로가 회피 지점 근처를 지나므로 무시");
                    continue;
                }

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
    
          // ✅ [기존 fallback 경로에도 회피 검사 적용]
        if (bestPath == null) {
            System.out.println("❌ 원하는 거리의 경로를 찾을 수 없습니다. 직접 연결 경로를 반환합니다.");
            ResponsePath fallback = findPath(hopper, start, end, "foot", customWeighting);
            if (avoidPoints != null && pathTouchesAvoidPoint(fallback, avoidPoints, 50)) {
                System.out.println("🚫 fallback 경로도 회피 지점과 충돌 → null 반환");
                return null;
            }
            return fallback;
        }
        
        System.out.println("가장 가까운 경로를 찾았습니다. 차이: " + closestDifference + " 미터");
        return bestPath;
    }
    
    static PointList generateRandomWaypoints(GraphHopper hopper, GHPoint start, int numWaypoints, double minDistance, double maxDistance, List<GHPoint> avoidPoints) {
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
    static GraphHopper createGraphHopperInstance(String osmFilePath) {
        String graphLocation = "target/routing-graph-cache";
    
        // 1️⃣ 캐시 폴더 강제 삭제
        File cacheFolder = new File(graphLocation);
        if (cacheFolder.exists()) {
            for (File file : Objects.requireNonNull(cacheFolder.listFiles())) {
                file.delete();
            }
            cacheFolder.delete();
            System.out.println("🗑️ 기존 GraphHopper 캐시 삭제 완료");
        }
    
        // 2️⃣ 기존 코드 실행
        GraphHopper hopper = new GraphHopper();
    
        GraphHopperConfig config = new GraphHopperConfig()
            .putObject("graph.location", graphLocation)
            .putObject("datareader.file", osmFilePath)
            .putObject("import.osm.ignored_highways", "platform,rest_area,services");
    
        CustomModel model = new CustomModel();
        model.addToSpeed(If("true", LIMIT, "5"));
        model.setDistanceInfluence(70.0);
    
        config.setProfiles(List.of(
            new Profile("foot").setWeighting("custom").setCustomModel(model)
        ));
    
        hopper.getCHPreparationHandler().setCHProfiles(List.of());
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
    static ResponsePath findDiverseOptimalPath(GraphHopper hopper, GHPoint startPoint, double desiredDistance,
    List<Integer> avoidEdges, List<GHPoint> avoidPoints,
    Set<Integer> penalizedEdgeIds, Weighting customWeighting, int attempt) {

    final int MAX_ATTEMPTS = 100;

    if (attempt > MAX_ATTEMPTS) {
        System.out.println("🚫 최대 시도 횟수 초과(" + MAX_ATTEMPTS + "). 기본 경로를 반환합니다.");
        //return findPath(hopper, startPoint, startPoint, "foot", customWeighting);
        return null;
    }

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

        CustomModel customModel = new CustomModel();

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

        // avoidPoints.add(new GHPoint(waypoint.lat, waypoint.lon)); //이거 넣으면 이전의 경유지 회피
        previousPoint = waypoint;
    }

    GHRequest returnReq = new GHRequest(previousPoint, startPoint)
        .setProfile("foot")
        .putHint("ch.disable", true);

    GHResponse returnRsp = hopper.route(returnReq);
    if (!returnRsp.hasErrors()) {
        ResponsePath returnSegment = returnRsp.getBest();
        fullPath = (fullPath == null) ? returnSegment : combinePaths(fullPath, returnSegment);
        totalDistance += returnSegment.getDistance();

         // ✅ ✅ ✅ [추가된 핵심 로직 시작] — 회피 지점 체크 로직
        if (pathTouchesAvoidPoint(fullPath, avoidPoints, 50)) {
            System.out.println("🚫 경로가 회피 지점 근처를 지남 → 재시도");
            return findDiverseOptimalPath(hopper, startPoint, desiredDistance, avoidEdges, avoidPoints, penalizedEdgeIds, customWeighting, attempt + 1);
        }
    // ✅ ✅ ✅ [추가된 핵심 로직 끝]
    }

    // 거리 확인 후 재시도
    if (totalDistance < lowerBound || totalDistance > upperBound) {
        System.out.println("❌ 경로 거리 초과 또는 부족 (거리: " + totalDistance + " m). 다시 탐색 중... (시도 " + attempt + ")");
        return findDiverseOptimalPath(hopper, startPoint, desiredDistance, avoidEdges, avoidPoints, penalizedEdgeIds, customWeighting, attempt + 1);
    }

    // ✅ 정상 종료일 경우 여기에 도달함
    System.out.println("✅ distance를 고려한 경로를 찾았습니다. 최종 거리: " + totalDistance + " m");

    return fullPath;
    }

    /**
     * 경로가 회피 지점 근처를 지나는지 확인하는 함수
     * @param path          GHResponse에서 반환된 ResponsePath
     * @param avoidPoints   회피할 GHPoint 좌표 리스트
     * @param thresholdMeters 회피 반경 (미터 단위)
     * @return true: 회피 지점 근처를 지남, false: 안전함
     */
    public static boolean pathTouchesAvoidPoint(ResponsePath path, List<GHPoint> avoidPoints, double thresholdMeters) {
        PointList pathPoints = path.getPoints();
        for (GHPoint routePt : pathPoints) {
            for (GHPoint avoidPt : avoidPoints) {
                if (calculateDistance(routePt, avoidPt) < thresholdMeters) {
                    System.out.printf("🚫 회피 지점 (%.5f, %.5f) 근처 통과 (%.1f m)%n", avoidPt.lat, avoidPt.lon, calculateDistance(routePt, avoidPt));
                    return true;
                }
            }
        }
        return false;
    }

}
