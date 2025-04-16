package com.graphhopper.example;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.feature.simple.SimpleFeature;

public class GpkgSlopeReader {
    // 절대 경로를 사용하여 GeoPackage 파일 지정
    private static final String DEFAULT_GPKG_FILE = "C:/Users/Owner/graphhopper/west2.gpkg";

    // osm_id(Long)와 aggregated_angle_angle(Double)을 저장하는 맵으로 변경
    private final Map<Long, Double> slopeMap;

    /**
     * 생성자: 지정한 레이어 이름을 이용해 DEFAULT_GPKG_FILE에서 데이터를 읽어 slopeMap에 저장합니다.
     *
     * @param layerName GeoPackage 파일 내 읽어올 레이어 이름
     */
    public GpkgSlopeReader(String layerName) {
        slopeMap = new HashMap<>();
        try {
            loadSlopeData(DEFAULT_GPKG_FILE, layerName);
        } catch (Exception e) {
            System.err.println("Error loading slope data from gpkg file:");
            e.printStackTrace();
        }
    }

    /**
     * GeoPackage 파일에서 "osm_id"와 "aggregated_angle_angle" 필드를 읽어 slopeMap에 저장합니다.
     *
     * GeoPackage 데이터스토어를 생성하기 위해 필요한 파라미터:
     *   - "dbtype" : "geopkg" (GeoPackage 형식임을 명시)
     *   - "database" : 파일 URL (절대 경로)
     *
     * @param gpkgFilePath GeoPackage 파일 경로 (절대 경로 사용)
     * @param layerName    읽어올 레이어 이름
     * @throws MalformedURLException
     * @throws IOException
     */
    private void loadSlopeData(String gpkgFilePath, String layerName) throws MalformedURLException, IOException {
        File file = new File(gpkgFilePath);

        // 파일 존재 여부 및 절대 경로 출력 (디버깅용)
        if (!file.exists()) {
            throw new IOException("GeoPackage file not found: " + file.getAbsolutePath());
        } else {
            System.out.println("GeoPackage file found: " + file.getAbsolutePath());
        }

        // GeoPackage DataStore에 필요한 파라미터 설정
        Map<String, Object> params = new HashMap<>();
        params.put("dbtype", "geopkg");
        params.put("database", file.toURI().toURL().toString());

        DataStore dataStore = DataStoreFinder.getDataStore(params);
        if (dataStore == null) {
            throw new IOException("Could not connect to data store for file: " + gpkgFilePath);
        }

        SimpleFeatureSource featureSource = dataStore.getFeatureSource(layerName);
        SimpleFeatureCollection collection = featureSource.getFeatures();
        try (SimpleFeatureIterator features = collection.features()) {
            while (features.hasNext()) {
                SimpleFeature feature = features.next();
                // "osm_id"와 "aggregated_angle_angle" 필드가 있다고 가정
                Object osmIdObj = feature.getAttribute("osm_id");
                Object slopeAttr = feature.getAttribute("aggregated_angle_angle");

                Long osmId = null;
                if (osmIdObj instanceof Number) {
                    osmId = ((Number) osmIdObj).longValue();
                } else if (osmIdObj instanceof String) {
                    try {
                        osmId = Long.parseLong((String) osmIdObj);
                    } catch (NumberFormatException nfe) {
                        System.err.println("Failed to parse osm_id: " + osmIdObj);
                    }
                }
                Double slopeValue = null;
                if (slopeAttr instanceof Number) {
                    slopeValue = ((Number) slopeAttr).doubleValue();
                } else if (slopeAttr instanceof String) {
                    try {
                        slopeValue = Double.parseDouble((String) slopeAttr);
                    } catch (NumberFormatException nfe) {
                        System.err.println("Failed to parse aggregated_angle_angle value: " + slopeAttr);
                    }
                }
                if (osmId != null && slopeValue != null) {
                    slopeMap.put(osmId, slopeValue);
                }
            }
        }
        dataStore.dispose();
    }

    /**
     * 주어진 osm_id에 해당하는 aggregated_angle_angle 값을 반환합니다.
     *
     * @param osmId osm 식별자 (Long 타입)
     * @return 해당 osm_id의 aggregated_angle_angle 값, 없으면 null
     */
    public Double getSlope(Long osmId) {
        return slopeMap.get(osmId);
    }

    /**
     * 내부적으로 로드한 모든 slope 데이터를 반환합니다.
     *
     * @return osm_id와 aggregated_angle_angle 값의 매핑 (Long 타입 키)
     */
    public Map<Long, Double> getAllSlopeData() {
        return slopeMap;
    }
}
