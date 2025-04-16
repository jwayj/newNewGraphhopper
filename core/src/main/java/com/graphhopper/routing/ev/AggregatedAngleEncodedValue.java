package com.graphhopper.routing.ev;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.graphhopper.storage.IntsRef;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "className")
public class AggregatedAngleEncodedValue implements EncodedValue {

    private final String name;
    // 예시로 scaleFactor를 사용 (값의 정밀도 및 범위에 따라 조정 필요)
    private final double scaleFactor = 100.0;

    public AggregatedAngleEncodedValue(String name) {
        this.name = name;
    }

    @Override
    public int init(InitializerConfig init) {
        // 예시: 8비트 사용 (값의 범위에 따라 조절)
        init.next(8);
        return 8;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isStoreTwoDirections() {
        return false;
    }

    /**
     * 각 edge의 aggregated_angle 값을 bit-packed 방식으로 저장합니다.
     * 현재 IntsRef 클래스에서는 내부 배열 이름이 'ints'이며, offset 필드를 사용합니다.
     * 실제 구현 시에는 bit-level 연산을 통해 저장해야 하지만, 아래 코드는 간단한 예시입니다.
     */
    public void setValue(IntsRef intsRef, double value) {
        int intValue = (int) (value * scaleFactor);
        // 기존의 intsRef.values가 아니라, intsRef.ints 배열을 사용합니다.
        intsRef.ints[intsRef.offset] = intValue;
    }

    /**
     * 저장된 aggregated_angle 값을 복원합니다.
     */
    public double getValue(IntsRef intsRef) {
        int intValue = intsRef.ints[intsRef.offset];
        return intValue / scaleFactor;
    }
}
