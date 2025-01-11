package com.graphhopper.example;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SaveGeoJson {
    public static void saveToFile(String geoJson, String fileName) throws IOException {
        // 절대 경로로 지정
        String absolutePath = "C:\\Users\\Owner\\graphhopper\\example\\src\\main\\java\\com\\graphhopper\\example";
        File currentDir = new File(absolutePath);

        // 경로가 존재하지 않으면 생성
        if (!currentDir.exists()) {
            boolean created = currentDir.mkdirs();
            if (!created) {
                throw new IOException("디렉토리를 생성할 수 없습니다: " + currentDir.getAbsolutePath());
            }
        }

        // 파일 저장 경로 설정
        File targetFile = new File(currentDir, fileName);

        try (FileWriter fileWriter = new FileWriter(targetFile)) {
            fileWriter.write(geoJson);
            System.out.println("GeoJSON 파일이 저장되었습니다: " + targetFile.getAbsolutePath());
        }
    }
}
