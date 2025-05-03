package com.graphhopper.example;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;

import java.util.Map;

public class FeedbackUtil {

    /**
     * 사용자의 경사도 선호 수준("LOW", "NORMAL", "HIGH")을 받아 적절한 Weighting 생성
     */
    public static Weighting createSlopeWeighting(GraphHopper hopper, Map<Long, Double> slopeData, String slopePref) {
        var profile = hopper.getProfile("foot");
        if (profile == null) throw new IllegalArgumentException("Profile 'foot' not found.");

        var baseWeighting = hopper.createWeighting(profile, new PMap());

        // 문자열 → Enum 변환
        var pref = CustomSlopeEncodedValue.SlopePreference.valueOf(slopePref.toUpperCase());

        var slopeEV = new CustomSlopeEncodedValue("custom_slope_penalty", slopeData, pref);
        return new CustomSlopeWeighting(baseWeighting, slopeEV);
    }
}
