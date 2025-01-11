package com.graphhopper.example;

import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;

// GeoJson 파일로 변환
public class GeoJsonExporter {
    public static String toGeoJSON(ResponsePath path) {
        PointList points = path.getPoints();
        StringBuilder geoJson = new StringBuilder("{\"type\": \"LineString\", \"coordinates\": [");
        
        for (int i = 0; i < points.size(); i++) {
            double lat = points.getLat(i);
            double lon = points.getLon(i);
            geoJson.append("[").append(lon).append(", ").append(lat).append("]");
            if (i < points.size() - 1) {
                geoJson.append(", ");
            }
        }
        geoJson.append("]}");
        return geoJson.toString();
    }
}
