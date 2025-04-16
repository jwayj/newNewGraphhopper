package com.graphhopper.example;

import static spark.Spark.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Server {
    public static void main(String[] args) {
        // 정적 파일 제공할 폴더 설정
        staticFiles.externalLocation("example/resources");

        // 포트 지정
        port(4567);

        // 피드백 처리용 POST API
        post("/feedback", (req, res) -> {
            String body = req.body();
            System.out.println("✅ 받은 피드백: " + body);

            // 피드백 저장
            Files.write(Paths.get("example/resources/feedback_log.json"), body.getBytes());
            return "OK";
        });
    }
}
