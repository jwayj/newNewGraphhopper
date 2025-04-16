package com.graphhopper.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

import java.io.File;
import java.io.Writer;

public class LogGeoJson {
    private static final String FILE_PATH = "./example/resources/feedback_log.json";

    /**
     * feedbackMap: key = 구분 이름 (예: routingWithCircle_14)
     *              value = edge ID 리스트 (예: [123, 456])
     */
    public static void writeFeedback(Map<String, List<Integer>> feedbackMap) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<Map<String, List<Map<String, Integer>>>> allFeedback = new ArrayList<>();

        // 기존 파일이 존재하면 이전 데이터 로드
        File file = new File(FILE_PATH);
        if (file.exists()) {
            try (Reader reader = new FileReader(file)) {
                Type type = new TypeToken<List<Map<String, List<Map<String, Integer>>>>>() {}.getType();
                allFeedback = gson.fromJson(reader, type);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 새 피드백 변환
        Map<String, List<Map<String, Integer>>> converted = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : feedbackMap.entrySet()) {
            List<Map<String, Integer>> edgeList = new ArrayList<>();
            for (Integer edgeId : entry.getValue()) {
                Map<String, Integer> edgeMap = new HashMap<>();
                edgeMap.put("edge", edgeId);
                edgeList.add(edgeMap);
            }
            converted.put(entry.getKey(), edgeList);
        }

        // 누적 목록에 추가
        allFeedback.add(converted);

        // 다시 파일에 저장
        try (Writer writer = new FileWriter(FILE_PATH)) {
            gson.toJson(allFeedback, writer);
            System.out.println("✅ feedback_log.json 누적 저장 완료!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
