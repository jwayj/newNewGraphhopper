package com.graphhopper.example;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.Translation;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.shapes.GHPoint;


public class RoutingExample {
    public static void main(String[] args) {
        String relDir = args.length == 1 ? args[0] : "";
        GraphHopper hopper = createGraphHopperInstance(relDir + "core/files/andorra.osm.pbf");
        
        double desiredDistance = 5000;
    
        GHPoint start = new GHPoint(42.505552, 1.535936);
        GHPoint end = new GHPoint(42.510508, 1.528773);
        
        ResponsePath path1 = routingWithDesiredDistance(hopper, desiredDistance, start, end);
        if (path1 != null) {
            System.out.println("경로 거리: " + path1.getDistance() + " 미터");
            
            String geoJson1 = GeoJsonExporter.toGeoJSON(path1);
            System.out.println("GeoJSON:\n" + geoJson1);
            try {
                SaveGeoJson.saveToFile(geoJson1, "route1.geojson");
                System.out.println("GeoJSON1 saved to route1.geojson");
            } catch (Exception e) {
                System.err.println("Error saving GeoJSON: " + e.getMessage());
            }
        } else {
            System.out.println("원하는 거리의 경로를 찾을 수 없습니다.");
        }
    
        ResponsePath path2 = routing(hopper, start, end);
        if (path2 != null) {
            System.out.println("경로 거리: " + path2.getDistance() + " 미터");
            
            String geoJson2 = GeoJsonExporter.toGeoJSON(path2);
            System.out.println("GeoJSON:\n" + geoJson2);
            try {
                SaveGeoJson.saveToFile(geoJson2, "route2.geojson");
                System.out.println("GeoJSON2 saved to route2.geojson");
            } catch (Exception e) {
                System.err.println("Error saving GeoJSON: " + e.getMessage());
            }
        } else {
            System.out.println("경로를 찾을 수 없습니다.");
        }
    
        hopper.close();
    }
    

    public static ResponsePath routingWithDesiredDistance(GraphHopper hopper, double desiredDistance, GHPoint start, GHPoint end) {
        double tolerance = 100; // 200미터 오차 허용
        double searchRadius = desiredDistance * 0.75; // 원하는 거리의 75%로 검색 반경 설정
        
        ResponsePath bestPath = null;
        double closestDifference = Double.MAX_VALUE;
        
        LocationIndex locationIndex = hopper.getLocationIndex();
        List<GHPoint> nearbyPoints = new ArrayList<>();
        
        DistanceCalc distCalc = DistanceCalcEarth.DIST_EARTH;
    
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
            ResponsePath path1 = findPath(hopper, start, intermediatePoint, "foot");
            ResponsePath path2 = findPath(hopper, intermediatePoint, end, "foot");

            
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
            return findPath(hopper, start, end, "foot");
        }
        
        System.out.println("가장 가까운 경로를 찾았습니다. 차이: " + closestDifference + " 미터");
        return bestPath;
    }
    
    
    
    
    


    private static ResponsePath findPath(GraphHopper hopper, GHPoint start, GHPoint end, String profile) {
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
    


    static GraphHopper createGraphHopperInstance(String ghLoc) {
        // 기존 캐시 삭제
        File cacheDir = new File("target/routing-graph-cache");
        if (cacheDir.exists()) {
            for (File file : cacheDir.listFiles()) {
                file.delete();
            }
            cacheDir.delete();
            System.out.println("Existing cache deleted.");
        }

        // GraphHopper 인스턴스 생성 및 설정
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(ghLoc);
        hopper.setGraphHopperLocation("target/routing-graph-cache");

        // 필요한 모든 Encoded Values 추가
        hopper.setEncodedValuesString("foot_access, foot_average_speed, road_class, max_speed");

        // CustomModel 설정
        CustomModel customModel = new CustomModel();

        // 우선순위 설정
        customModel.getPriority().add(Statement.If("road_class == RESIDENTIAL", Statement.Op.MULTIPLY, "0.8"));
        customModel.getPriority().add(Statement.ElseIf("road_class == FOOTWAY", Statement.Op.MULTIPLY, "1.2"));
        customModel.getPriority().add(Statement.Else(Statement.Op.MULTIPLY, "1.0")); // 기본값

        // 속도 설정
        customModel.getSpeed().add(Statement.If("max_speed > 50", Statement.Op.LIMIT, "50"));
        customModel.getSpeed().add(Statement.Else(Statement.Op.LIMIT, "30")); // 기본 속도 제한

        // 거리 영향도 설정
        customModel.setDistanceInfluence(70.0);

        // Profile 설정
        Profile footProfile = new Profile("foot")
                .setWeighting("custom")
                .setCustomModel(customModel);
        hopper.setProfiles(footProfile);

        // CH 및 LM 설정
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("foot"));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("foot"));

        hopper.importOrLoad();
        return hopper;
    }

    

    public static ResponsePath routing(GraphHopper hopper, GHPoint start, GHPoint end) {
        GHRequest req = new GHRequest(start, end)
            .setProfile("foot")  // 보행자 프로필 사용
            .setLocale(Locale.US);
        
        GHResponse rsp = hopper.route(req);
    
        if (rsp.hasErrors()) {
            throw new RuntimeException(rsp.getErrors().toString());
        }
    
        ResponsePath path = rsp.getBest();
    
        // 경로 정보 출력
        System.out.println("총 거리: " + path.getDistance() + " 미터");
        System.out.println("예상 소요 시간: " + (path.getTime() / 60000) + " 분");
    
        // 경로 안내 출력
        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.UK);
        InstructionList il = path.getInstructions();
        for (Instruction instruction : il) {
            System.out.println(instruction.getDistance() + "m: " + instruction.getTurnDescription(tr));
        }
    
        return path;
    }
    
    
    

    public static void speedModeVersusFlexibleMode(GraphHopper hopper) {
        GHRequest req = new GHRequest(42.508552, 1.532936, 42.507508, 1.528773).
                setProfile("car").setAlgorithm(Parameters.Algorithms.ASTAR_BI).putHint(Parameters.CH.DISABLE, true);
        GHResponse res = hopper.route(req);
        if (res.hasErrors())
            throw new RuntimeException(res.getErrors().toString());
        assert Helper.round(res.getBest().getDistance(), -2) == 600;
    }

    public static void alternativeRoute(GraphHopper hopper) {
        // calculate alternative routes between two points (supported with and without CH)
        GHRequest req = new GHRequest().setProfile("car").
                addPoint(new GHPoint(42.502904, 1.514714)).addPoint(new GHPoint(42.508774, 1.537094)).
                setAlgorithm(Parameters.Algorithms.ALT_ROUTE);
        req.getHints().putObject(Parameters.Algorithms.AltRoute.MAX_PATHS, 3);
        GHResponse res = hopper.route(req);
        if (res.hasErrors())
            throw new RuntimeException(res.getErrors().toString());
        assert res.getAll().size() == 2;
        assert Helper.round(res.getBest().getDistance(), -2) == 2200;
    }

    /**
     * To customize profiles in the config.yml file you can use a json or yml file or embed it directly. See this list:
     * web/src/test/resources/com/graphhopper/application/resources and https://www.graphhopper.com/?s=customizable+routing
     */
    public static void customizableRouting(String ghLoc) {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(ghLoc);
        hopper.setGraphHopperLocation("target/routing-custom-graph-cache");
        hopper.setEncodedValuesString("car_access, car_average_speed");
        hopper.setProfiles(new Profile("car_custom").setCustomModel(GHUtility.loadCustomModelFromJar("car.json")));

        // The hybrid mode uses the "landmark algorithm" and is up to 15x faster than the flexible mode (Dijkstra).
        // Still it is slower than the speed mode ("contraction hierarchies algorithm") ...
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car_custom"));
        hopper.importOrLoad();

        // ... but for the hybrid mode we can customize the route calculation even at request time:
        // 1. a request with default preferences
        GHRequest req = new GHRequest().setProfile("car_custom").
                addPoint(new GHPoint(42.506472, 1.522475)).addPoint(new GHPoint(42.513108, 1.536005));

        GHResponse res = hopper.route(req);
        if (res.hasErrors())
            throw new RuntimeException(res.getErrors().toString());

        assert Math.round(res.getBest().getTime() / 1000d) == 94;

        // 2. now avoid the secondary road and reduce the maximum speed, see docs/core/custom-models.md for an in-depth explanation
        // and also the blog posts https://www.graphhopper.com/?s=customizable+routing
        CustomModel model = new CustomModel();
        model.addToPriority(If("road_class == SECONDARY", MULTIPLY, "0.5"));

        // unconditional limit to 20km/h
        model.addToSpeed(If("true", LIMIT, "30"));
        

        req.setCustomModel(model);
        res = hopper.route(req);
        if (res.hasErrors())
            throw new RuntimeException(res.getErrors().toString());

        assert Math.round(res.getBest().getTime() / 1000d) == 184;
    }
}
