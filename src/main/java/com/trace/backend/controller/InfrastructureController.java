package com.trace.backend.controller;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class InfrastructureController {

    private final MongoTemplate mongoTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    public InfrastructureController(
            MongoTemplate mongoTemplate,
            RedisConnectionFactory redisConnectionFactory
    ) {
        this.mongoTemplate = mongoTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> result = new LinkedHashMap<>();

        boolean mongoUp = checkMongo();
        boolean redisUp = checkRedis();
        boolean allUp = mongoUp && redisUp;

        result.put("application", "trace-backend");
        result.put("status", allUp ? "UP" : "DOWN");
        result.put("mongodb", mongoUp ? "UP" : "DOWN");
        result.put("redis", redisUp ? "UP" : "DOWN");

        HttpStatus status = allUp
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;

        return ResponseEntity.status(status).body(result);
    }

    private boolean checkMongo() {
        try {
            Document response = mongoTemplate.executeCommand(
                    new Document("ping", 1)
            );

            Object ok = response.get("ok");

            return ok instanceof Number number
                    && number.doubleValue() == 1.0;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean checkRedis() {
        try (RedisConnection connection =
                     redisConnectionFactory.getConnection()) {

            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (Exception exception) {
            return false;
        }
    }
}