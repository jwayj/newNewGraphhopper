package com.graphhopper.example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class FeedbackLoader {
    public static List<String> loadFeedbackEdges(String path) {
        try (FileReader reader = new FileReader(path)) {
            return new Gson().fromJson(reader, new TypeToken<List<String>>() {}.getType());
        } catch (IOException e) {
            System.err.println("⚠️ 피드백 로그 읽기 실패: " + e.getMessage());
            return Collections.emptyList(); // 비어 있는 리스트 반환
        }
    }
}
