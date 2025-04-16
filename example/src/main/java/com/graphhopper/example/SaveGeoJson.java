package com.graphhopper.example;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SaveGeoJson {
    public static void saveToFile(String geoJson, String filePath) throws IOException {
        if (geoJson == null || filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("GeoJSON string and file path must not be null or empty");
        }

        File outputFile = new File(filePath);

        // 폴더가 없으면 생성
        File parentDir = outputFile.getParentFile();
        if (!parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (!created) {
                throw new IOException("❌ 디렉토리 생성 실패: " + parentDir.getAbsolutePath());
            }
        }

        // 파일 쓰기
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, StandardCharsets.UTF_8))) {
            writer.write(geoJson);
            System.out.println("✅ GeoJSON 저장 완료: " + outputFile.getAbsolutePath());
        }
    }
}

