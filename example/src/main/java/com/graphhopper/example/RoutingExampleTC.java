package com.graphhopper.example;


import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Parameters;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.util.Parameters.Curbsides.CURBSIDE_ANY;
import static com.graphhopper.util.Parameters.Curbsides.CURBSIDE_RIGHT;

/**
 * Routing with turn costs. Also see {@link RoutingExample} for more details.
 * 회전 비용을 고려한 경로탐색 교차로에서의 특정 회전에 대한 추가적인 시간/비용 설정해서 현실적인 경로 탐색 가능하게 함
 * 도로 가장자리와 같은 요소도 고려
 */
public class RoutingExampleTC {
    public static void main(String[] args) {
        String relDir = args.length == 1 ? args[0] : "";
        GraphHopper hopper = createGraphHopperInstance(relDir + "core/files/andorra.osm.pbf");
        routeWithTurnCosts(hopper);
        routeWithTurnCostsAndCurbsides(hopper);
        routeWithTurnCostsAndCurbsidesAndOtherUTurnCosts(hopper);
    }

    public static void routeWithTurnCosts(GraphHopper hopper) {
        //두 좌표간 경로 계산, car 프로필 설정
        GHRequest req = new GHRequest(42.50822, 1.533966, 42.506899, 1.525372).
                setProfile("car");
        //예상 거리와 예상 시간 값 검증
        route(hopper, req, 1038, 63_000);
    }

    public static void routeWithTurnCostsAndCurbsides(GraphHopper hopper) {
        GHRequest req = new GHRequest(42.50822, 1.533966, 42.506899, 1.525372).
                setCurbsides(Arrays.asList(CURBSIDE_ANY, CURBSIDE_RIGHT)). //curbside(도로 가장자리) 설정
                setProfile("car");
        //검증
        route(hopper, req, 1370, 88_000);
    }

    public static void routeWithTurnCostsAndCurbsidesAndOtherUTurnCosts(GraphHopper hopper) {
        GHRequest req = new GHRequest(42.50822, 1.533966, 42.506899, 1.525372)
                .setCurbsides(Arrays.asList(CURBSIDE_ANY, CURBSIDE_RIGHT))
                // to change u-turn costs per request we have to disable CH. otherwise the u-turn costs we set per request
                // will be ignored and those set for our profile will be used.
                .putHint(Parameters.CH.DISABLE, true)
                .setProfile("car");
        //검증
        route(hopper, req.putHint(Parameters.Routing.U_TURN_COSTS, 10), 1370, 88_000);
        route(hopper, req.putHint(Parameters.Routing.U_TURN_COSTS, 100), 1635, 120_000);
        route(hopper, req.putHint(Parameters.Routing.U_TURN_COSTS, 200), 1635, 120_000);
    }

    // 경로 요청 수행, 결과값 검증
    private static void route(GraphHopper hopper, GHRequest req, int expectedDistance, int expectedTime) {
        GHResponse rsp = hopper.route(req);
        // handle errors
        if (rsp.hasErrors())
            // if you get: Impossible curbside constraint: 'curbside=right'
            // you can specify 'curbside=any' or Parameters.Routing.CURBSIDE_STRICTNESS="soft" to avoid an error
            throw new RuntimeException(rsp.getErrors().toString());
        ResponsePath path = rsp.getBest();
        // 결과 시간,거리 값과 예상값 비교
        assert Math.abs(expectedDistance - path.getDistance()) < 1 : "unexpected distance : " + path.getDistance() + " vs. " + expectedDistance;
        assert Math.abs(expectedTime - path.getTime()) < 1000 : "unexpected time : " + path.getTime() + " vs. " + expectedTime;
    }

    // see RoutingExample for more details
    static GraphHopper createGraphHopperInstance(String ghLoc) {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(ghLoc);
        hopper.setGraphHopperLocation("target/routing-tc-graph-cache");
        // add all encoded values that are used in the custom model, these are also available as path details or for client-side custom models
        hopper.setEncodedValuesString("car_access, car_average_speed");
        Profile profile = new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json"))
                // enabling turn costs means OSM turn restriction constraints like 'no_left_turn' will be taken into account for the specified access restrictions
                // we can also set u_turn_costs (in seconds). i.e. we will consider u-turns at all junctions with a 40s time penalty
                .setTurnCostsConfig(new TurnCostsConfig(List.of("motorcar", "motor_vehicle"), 40));
        hopper.setProfiles(profile);
        // enable CH for our profile. since turn costs are enabled this will take more time and memory to prepare than
        // without turn costs.
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile.getName()));
        hopper.importOrLoad();
        return hopper;
    }
}
