package com.graphhopper.example;

import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;

public class GeoJsonExporter1 {
    public static String toGeoJSON(ResponsePath path) {
        if (path == null) {
            throw new IllegalArgumentException("ResponsePath cannot be null");
        }

        PointList points = path.getPoints();
        StringBuilder geoJson = new StringBuilder();
        geoJson.append("{\"type\": \"FeatureCollection\", \"features\": [{")
               .append("\"type\": \"Feature\", \"geometry\": {")
               .append("\"type\": \"LineString\", \"coordinates\": [");
        
        for (int i = 0; i < points.size(); i++) {
            double lon = points.getLon(i);
            double lat = points.getLat(i);
            geoJson.append("[").append(lon).append(", ").append(lat).append("]");
            if (i < points.size() - 1) {
                geoJson.append(", ");
            }
        }
        geoJson.append("]}, \"properties\": {}}]}");
        return geoJson.toString();
    }
}
