package com.graphhopper.example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class FeedbackLoader {

    /**
     * 📥 feedback_log.json 파일에서 "selectedEdges" 리스트를 로드하여 정수 Set으로 반환
     *
     * @param path JSON 파일 경로 (예: "example/resources/feedback_log.json")
     * @return edge_id 정수의 Set
     */
    public static Set<Integer> loadPenalizedEdgeIds(String path) {
        try (FileReader reader = new FileReader(path)) {
            List<String> rawList = new Gson().fromJson(reader, new TypeToken<List<String>>() {}.getType());

            return rawList.stream()
                    .map(s -> {
                        try {
                            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

        } catch (IOException e) {
            System.err.println("⚠️ 피드백 로그 읽기 실패: " + e.getMessage());
            return Collections.emptySet();
        }
    }
}
