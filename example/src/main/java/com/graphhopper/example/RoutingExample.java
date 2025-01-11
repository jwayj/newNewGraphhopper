package com.graphhopper.example;

import java.io.IOException;
import java.util.Locale;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.Translation;
import com.graphhopper.util.shapes.GHPoint;

// 새로 추가한 부분
// import java.io.IOException;

public class RoutingExample {
    public static void main(String[] args) {

        // 여러 메소드 호출하여서 graphhopper를 초기화, 다양한 기능을 테스트
        String relDir = args.length == 1 ? args[0] : "";
        GraphHopper hopper = createGraphHopperInstance(relDir + "core/files/andorra.osm.pbf");
        routing(hopper);
        speedModeVersusFlexibleMode(hopper);
        alternativeRoute(hopper);
        customizableRouting(relDir + "core/files/andorra.osm.pbf");

        // release resources to properly shutdown or start a new instance

        // 새로 추가한 부분
        // GeoJson에 저장

        ResponsePath path = routing(hopper);
        String geoJson = GeoJsonExporter.toGeoJSON(path);
        System.out.println("GeoJSON:\n" + geoJson);

        try {
            SaveGeoJson.saveToFile(geoJson, "route.geojson");
            System.out.println("GeoJSON saved to route.geojson");
        } catch (IOException e) {
            System.err.println("Error saving GeoJSON: " + e.getMessage());
        }

        // 새로 추가한 부분

        hopper.close();
    }


    static GraphHopper createGraphHopperInstance(String ghLoc) {
        GraphHopper hopper = new GraphHopper();
        
        // core/files/andorra.osm.pbf 파일에서 OSM 데이터 로드
        hopper.setOSMFile(ghLoc);

        // specify where to store graphhopper files
        // graphhopper 파일을 어디에 저장할지 명시
        hopper.setGraphHopperLocation("target/routing-graph-cache");
        // add all encoded values that are used in the custom model, these are also available as path details or for client-side custom models
        // 
        hopper.setEncodedValuesString("car_access, car_average_speed");
        // see docs/core/profiles.md to learn more about profiles
        //프로필 추가?
        hopper.setProfiles(new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json")));

        // this enables speed mode for the profile we called car
        // car profile의 스피드 모드
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));

        // now this can take minutes if it imports or a few seconds for loading of course this is dependent on the area you import
        // 
        hopper.importOrLoad();
        return hopper;
    }


    // 25.01.09 수정한 부분
    // void-> ResponsePath, return rsp.getBest(); 추가함


    // 두 지점간의 경로를 계산하여 결과를 출력
    public static ResponsePath routing(GraphHopper hopper) {
        // simple configuration of the request object
        // GHRequest를 생성해 출발지 목적지 좌표, 프로필, 언어 설정
        GHRequest req = new GHRequest(42.518552, 1.542936, 42.507508, 1.528773).
                // note that we have to specify which profile we are using even when there is only one like here
                        setProfile("car").
                // define the language for the turn instructions
                        setLocale(Locale.US);
        // 경로 요청 결과 저장
        GHResponse rsp = hopper.route(req);

        // handle errors
        if (rsp.hasErrors())
            throw new RuntimeException(rsp.getErrors().toString());

        // use the best path, see the GHResponse class for more possibilities.
        // 결과들 중에서 best 결과
        ResponsePath path = rsp.getBest();

        // points, distance in meters and time in millis of the full path
        // 경로 정보(거리, 시간, 지점 등)
        PointList pointList = path.getPoints();
        double distance = path.getDistance();
        long timeInMs = path.getTime();

        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.UK);
        InstructionList il = path.getInstructions();
        
        // iterate over all turn instructions
        // 경로 지침을 출력하도록 설정
        for (Instruction instruction : il) {
            System.out.println("distance " + instruction.getDistance() + " for instruction: " + instruction.getTurnDescription(tr));
        }
        assert il.size() == 6;
        assert Helper.round(path.getDistance(), -2) == 600;

        return rsp.getBest();
    }

    // 속도 모드와 유연 모드간의 성능 비교
    public static void speedModeVersusFlexibleMode(GraphHopper hopper) {
        // 요청&결과 저장
        GHRequest req = new GHRequest(42.518552, 1.542936, 42.507508, 1.528773).
                setProfile("car").setAlgorithm(Parameters.Algorithms.ASTAR_BI).putHint(Parameters.CH.DISABLE, true);
        GHResponse res = hopper.route(req);
        //에러 처리
        if (res.hasErrors())
            throw new RuntimeException(res.getErrors().toString());
        assert Helper.round(res.getBest().getDistance(), -2) == 600;
    }

    // 대안 경로를 계산
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
    // 사용자 정의 경로 계산
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
        // 1. a request with default preferences 기본 요청 처리
        GHRequest req = new GHRequest().setProfile("car_custom").
                addPoint(new GHPoint(42.506472, 1.522475)).addPoint(new GHPoint(42.513108, 1.536005));

        GHResponse res = hopper.route(req);
        if (res.hasErrors())
            throw new RuntimeException(res.getErrors().toString());

        assert Math.round(res.getBest().getTime() / 1000d) == 94;

        // 2. now avoid the secondary road and reduce the maximum speed, see docs/core/custom-models.md for an in-depth explanation
        // and also the blog posts https://www.graphhopper.com/?s=customizable+routing
        // 터널이라던가 특정 도로를 피하도록 커스텀 하는쪽인듯?
        // 사용자 정의 규칙을 추가
        CustomModel model = new CustomModel();
        model.addToPriority(If("road_class == SECONDARY", MULTIPLY, "0.5")); 

        // unconditional limit to 20km/h 속도 제한
        model.addToSpeed(If("true", LIMIT, "30"));

        req.setCustomModel(model); //커스텀 모델 설정
        res = hopper.route(req); //결과 저장
        if (res.hasErrors())
            throw new RuntimeException(res.getErrors().toString());

        assert Math.round(res.getBest().getTime() / 1000d) == 184;
    }
}
