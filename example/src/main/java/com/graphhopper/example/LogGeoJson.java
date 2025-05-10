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
     * 기존 방식: Map<String, List<Integer>> → 변환 후 저장
     */
    public static void writeFeedback(Map<String, List<Integer>> feedbackMap) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<Map<String, List<Map<String, Integer>>>> allFeedback = new ArrayList<>();

        // 기존 파일 읽기
        File file = new File(FILE_PATH);
        if (file.exists()) {
            try (Reader reader = new FileReader(file)) {
                Type type = new TypeToken<List<Map<String, List<Map<String, Integer>>>>>() {}.getType();
                allFeedback = gson.fromJson(reader, type);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 새 피드백 변환 및 추가
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

        allFeedback.add(converted);

        // 다시 저장
        try (Writer writer = new FileWriter(FILE_PATH)) {
            gson.toJson(allFeedback, writer);
            System.out.println("✅ feedback_log.json 누적 저장 완료!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 새로운 방식: 클라이언트에서 전달된 raw JSON 문자열을 직접 누적 저장
     */
    public static void writeFeedbackRaw(String rawJson) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File file = new File(FILE_PATH);
        List<Object> allFeedback = new ArrayList<>();

        // 기존 파일 읽기
        if (file.exists()) {
            try (Reader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<Object>>() {}.getType();
                allFeedback = gson.fromJson(reader, listType);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 새 피드백 JSON 문자열 파싱
        try {
            Type listType = new TypeToken<List<Object>>() {}.getType();
            List<Object> newFeedback = gson.fromJson(rawJson, listType);
            allFeedback.addAll(newFeedback);
        } catch (Exception e) {
            System.err.println("⚠️ 새 피드백 JSON 파싱 실패: " + e.getMessage());
            return;
        }

        // 저장
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(allFeedback, writer);
            System.out.println("✅ feedback_log.json에 raw JSON 누적 저장 완료!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
