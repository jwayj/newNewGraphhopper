package com.graphhopper.example;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Map;

public class CustomSlopeWeighting implements Weighting {

    private final Weighting baseWeighting;
    private final CustomSlopeEncodedValue slopeEV; // CustomSlopeEncodedValue에는 computePenalty(Long osmId) 메서드가 필요

    public CustomSlopeWeighting(Weighting baseWeighting, CustomSlopeEncodedValue slopeEV) {
        this.baseWeighting = baseWeighting;
        this.slopeEV = slopeEV;
    }

    @Override
    public double calcMinWeightPerDistance() {
        return baseWeighting.calcMinWeightPerDistance();
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edge, boolean reverse) {
        // 기본 비용 계산
        double baseCost = baseWeighting.calcEdgeWeight(edge, reverse);
        double penalty = 0.0;
        
        // edge의 key-values에서 "osm_way_id" 추출 (OSMReader 단계에서 저장됨)
        Map<String, ?> keyValues = edge.getKeyValues();  // 이 메서드가 내부적으로 edge의 태그/속성을 반환한다고 가정
        if (keyValues != null && keyValues.containsKey("osm_way_id")) {
            try {
                Long osmId = Long.valueOf((String) keyValues.get("osm_way_id"));
                // CustomSlopeEncodedValue의 computePenalty() 메서드를 호출하여 추가 비용 계산
                penalty = slopeEV.computePenalty(osmId);
            } catch (NumberFormatException e) {
                System.err.println("osm_way_id 파싱 오류: " + keyValues.get("osm_way_id"));
            }
        }
        return baseCost + penalty;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edge, boolean reverse) {
        return baseWeighting.calcEdgeMillis(edge, reverse);
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return baseWeighting.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return baseWeighting.calcTurnMillis(inEdge, viaNode, outEdge);
    }

    @Override
    public boolean hasTurnCosts() {
        return baseWeighting.hasTurnCosts();
    }

    @Override
    public String getName() {
        return "custom_slope_weighting";
    }
}
