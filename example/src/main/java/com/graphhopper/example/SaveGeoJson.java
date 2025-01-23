package com.graphhopper.example;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SaveGeoJson {
    public static void saveToFile(String geoJson, String fileName) throws IOException {
        if (geoJson == null || fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("GeoJSON string and file name must not be null or empty");
        }

        // 현재 작업 디렉토리를 기준으로 상대 경로 사용
        String relativePath = "./example/src/main/java/com/graphhopper/example";
        File currentDir = new File(System.getProperty("user.dir"), relativePath);

        if (!currentDir.exists() && !currentDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + currentDir.getAbsolutePath());
        }

        File targetFile = new File(currentDir, fileName);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile, StandardCharsets.UTF_8))) {
            writer.write(geoJson);
            System.out.println("GeoJSON file saved: " + targetFile.getAbsolutePath());
        }
    }
}
