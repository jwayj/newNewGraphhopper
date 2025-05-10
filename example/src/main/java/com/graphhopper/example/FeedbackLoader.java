// package com.graphhopper.example;

// import com.google.gson.Gson;
// import com.google.gson.reflect.TypeToken;

// import java.io.FileReader;
// import java.io.IOException;
// import java.lang.reflect.Type;
// import java.util.*;
// import java.util.stream.Collectors;

// public class FeedbackLoader {

//     public static class FeedbackEntry {
//         List<EdgeWrapper> selectedEdges;
//     }

//     public static class EdgeWrapper {
//         int edge;
//     }

//     /**
//      * 📥 feedback_log.json 파일에서 "selectedEdges" 리스트를 로드하여 정수 Set으로 반환
//      *
//      * @param path JSON 파일 경로
//      * @return edge_id 정수의 Set
//      */
//     public static Set<Integer> loadPenalizedEdgeIds(String path) {
//         try (FileReader reader = new FileReader(path)) {
//             Gson gson = new Gson();
//             Type listType = new TypeToken<List<FeedbackEntry>>() {}.getType();
//             List<FeedbackEntry> entries = gson.fromJson(reader, listType);

//             return entries.stream()
//                     .flatMap(entry -> entry.selectedEdges.stream())
//                     .map(wrapper -> wrapper.edge)
//                     .collect(Collectors.toSet());

//         } catch (IOException e) {
//             System.err.println("⚠️ 피드백 로그 읽기 실패: " + e.getMessage());
//             return Collections.emptySet();
//         } catch (Exception e) {
//             System.err.println("⚠️ JSON 파싱 오류: " + e.getMessage());
//             return Collections.emptySet();
//         }
//     }
// }
