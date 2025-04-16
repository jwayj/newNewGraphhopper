package com.graphhopper.example;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "className")
public class CustomSlopeEncodedValue implements EncodedValue {

    // osm_id(Long) -> aggregated_angle 값(Double) 매핑
    private final java.util.Map<Long, Double> slopeMap;
    // 사용자가 선택한 경사 선호 옵션
    private final SlopePreference slopePreference;
    // 경사 구간 경계값
    private final double lowThreshold = 0.0001;
    private final double midThreshold = 0.4358;
    private final String name;
    
    // scaleFactor 선언 (예: 100.0)
    private final double scaleFactor = 100.0;
    
    public enum SlopePreference {
        LOW,
        NORMAL,
        HIGH
    }
    
    public CustomSlopeEncodedValue(String name, java.util.Map<Long, Double> slopeMap, SlopePreference slopePreference) {
        this.name = name;
        this.slopeMap = slopeMap;
        this.slopePreference = slopePreference;
    }
    
    @Override
    public int init(InitializerConfig init) {
        // 동적 계산이므로 별도의 비트 저장은 없음
        return 0;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public boolean isStoreTwoDirections() {
        return false;
    }
    
    // osm_id(Long)에 해당하는 aggregated_angle 값과 선호 옵션에 따른 추가 비용 계산
    public double computePenalty(Long osmId) {
        Double slope = slopeMap.get(osmId);
        if (slope == null) {
            return 0.0;
        }
        double penalty = 0.0;
        switch (slopePreference) {
            case LOW:
                // LOW 선호: 낮은 경사(edge의 slope가 낮은 경우)를 우대해야 하므로,
                // slope가 낮으면 penalty가 낮고, slope가 높으면 penalty가 커야 합니다.
                // → 만약 slope가 3.1911 미만이면 높은 비용을 부과하고, 3.1911 이상이면 1.0
                penalty = (slope < midThreshold) ? 50.0 * (midThreshold - slope) : 1.0;
                break;
            case NORMAL:
                if (slope >= lowThreshold && slope <= midThreshold) {
                    penalty = 1.0;
                } else if (slope < lowThreshold) {
                    penalty = 50.0 * (lowThreshold - slope);
                } else { // slope > midThreshold
                    penalty = 50.0 * (slope - midThreshold);
                }
                break;
            case HIGH:
                // HIGH 선호: 높은 경사를 우대해야 하므로,
                // slope가 높으면 penalty가 낮고, slope가 낮으면 penalty가 커야 합니다.
                // → 만약 slope가 0.7637보다 클 경우에는 낮은 비용, 0.7637 이하이면 높은 비용
                penalty = (slope > lowThreshold) ? 50.0 * (slope - lowThreshold) : 1.0;
                break;
        }
        return penalty;
    }
    
    // 보통 이 메서드는 실제 edge의 encoded 데이터(IntsRef)에 값을 기록하는 역할을 합니다.
    public void setValue(IntsRef intsRef, double value) {
        int intValue = (int) (value * scaleFactor);
        // 최신 IntsRef 클래스에서는 내부 배열 이름이 'ints'를 사용합니다.
        intsRef.ints[intsRef.offset] = intValue;
    }
    
    public double getValue(IntsRef intsRef) {
        int intValue = intsRef.ints[intsRef.offset];
        return intValue / scaleFactor;
    }
    
    // 각 edge의 osm_id를 기반으로 추가 penalty를 반환
    // 여기서는 edgeState.getEdge() 결과를 Long으로 변환한다고 가정합니다.
    public double addPenalty(EdgeIteratorState edgeState) {
        // 실제로는 OSM way id를 얻어야 합니다.
        Long osmId = Long.valueOf(edgeState.getEdge());
        return computePenalty(osmId);
    }
}
