package com.graphhopper.example;

import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;

/**
 * GeoJSON 파일로 변환하는 유틸리티 클래스
 */
public class GeoJsonExporter2 {

    /**
     * ResponsePath를 GeoJSON 형식으로 변환합니다.
     * 출발지, 경유지, 도착지와 경로를 모두 포함합니다.
     *
     * @param path GraphHopper ResponsePath 객체
     * @param waypoints 경로에 포함된 경유지 배열
     * @param startPoint 출발지 (반복 루프의 시작과 끝)
     * @return GeoJSON 문자열
     */
    public static String toGeoJSON(ResponsePath path, PointList waypoints, PointList startPoint) {
        if (path == null || path.getPoints().isEmpty()) {
            throw new IllegalArgumentException("경로가 유효하지 않습니다.");
        }

        StringBuilder geoJson = new StringBuilder();

        // GeoJSON FeatureCollection 시작
        geoJson.append("{\"type\": \"FeatureCollection\", \"features\": [");

        // 경로 (LineString) 추가
        geoJson.append("{\"type\": \"Feature\", \"geometry\": {\"type\": \"LineString\", \"coordinates\": [");

        PointList pathPoints = path.getPoints();
        for (int i = 0; i < pathPoints.size(); i++) {
            double lat = pathPoints.getLat(i);
            double lon = pathPoints.getLon(i);

            geoJson.append("[").append(lon).append(", ").append(lat).append("]");
            if (i < pathPoints.size() - 1) {
                geoJson.append(", ");
            }
        }

        geoJson.append("]}, \"properties\": {\"type\": \"route\"}}");

        // 경유지 (Point) 추가
        for (int i = 0; i < waypoints.size(); i++) {
            double lat = waypoints.getLat(i);
            double lon = waypoints.getLon(i);

            geoJson.append(", {\"type\": \"Feature\", \"geometry\": {\"type\": \"Point\", \"coordinates\": [").append(lon).append(", ").append(lat).append("]}, \"properties\": {\"type\": \"waypoint\", \"index\": ").append(i).append("}}");
        }

        // 출발/도착지 (Point) 추가
        for (int i = 0; i < startPoint.size(); i++) {
            double lat = startPoint.getLat(i);
            double lon = startPoint.getLon(i);

            geoJson.append(", {\"type\": \"Feature\", \"geometry\": {\"type\": \"Point\", \"coordinates\": [").append(lon).append(", ").append(lat).append("]}, \"properties\": {\"type\": \"start/end\"}}");
        }

        // FeatureCollection 닫기
        geoJson.append("]}");

        return geoJson.toString();
    }
} 
