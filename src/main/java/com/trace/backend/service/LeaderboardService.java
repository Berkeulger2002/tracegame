package com.trace.backend.service;

import com.trace.backend.dto.LeaderboardResponse;
import com.trace.backend.model.Attempt;
import com.trace.backend.model.TraceUser;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final MongoTemplate mongoTemplate;
    private final Clock gameClock;
    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public LeaderboardService(
            MongoTemplate mongoTemplate,
            Clock gameClock,
            StringRedisTemplate redis,
            JsonMapper jsonMapper
    ) {
        this.mongoTemplate = mongoTemplate;
        this.gameClock = gameClock;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    public LeaderboardResponse today(int page, int size) {
        if (page < 0 || page > 10_000) {
            throw badRequest("Sayfa 0 ile 10000 arasında olmalı.");
        }

        if (size < 1 || size > 100) {
            throw badRequest("Sayfa boyutu 1 ile 100 arasında olmalı.");
        }

        String today = LocalDate.now(gameClock).toString();
        String key = "trace:leaderboard:today:v1:"
                + today + ":" + page + ":" + size;

        LeaderboardResponse cached = readCached(key, today, page, size);
        if (cached != null) {
            return cached;
        }

        LeaderboardResponse result = loadToday(today, page, size);
        writeCached(key, result);
        return result;
    }

    private LeaderboardResponse loadToday(String today, int page, int size) {
        long offset = (long) page * size;

        long totalPlayers = mongoTemplate.count(
                Query.query(finishedToday(today)),
                Attempt.class
        );

        if (offset >= totalPlayers) {
            return new LeaderboardResponse(
                    today,
                    page,
                    size,
                    totalPlayers,
                    List.of()
            );
        }

        // LOST kayıtları, eski kayıtlı puanı olsa bile 0 sayılır.
        // Eksik puan alanları da 0 olarak değerlendirilir.
        Document effectiveScore = new Document(
                "$cond",
                List.of(
                        new Document(
                                "$eq",
                                List.of("$status", "LOST")
                        ),
                        0,
                        new Document(
                                "$ifNull",
                                List.of("$score", 0)
                        )
                )
        );

        // Büyük oyun snapshot'larını ve cevapları sorgu sonucuna alma.
        Document projection = new Document("_id", 1)
                .append("userId", 1)
                .append("status", 1)
                .append("score", effectiveScore);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(finishedToday(today)),

                context -> new Document("$project", projection),

                Aggregation.sort(
                        Sort.by(
                                Sort.Order.desc("score"),
                                Sort.Order.asc("_id")
                        )
                ),

                Aggregation.skip(offset),
                Aggregation.limit(size)
        );

        List<Document> rows = mongoTemplate.aggregate(
                aggregation,
                mongoTemplate.getCollectionName(Attempt.class),
                Document.class
        ).getMappedResults();

        if (rows.isEmpty()) {
            return new LeaderboardResponse(
                    today,
                    page,
                    size,
                    totalPlayers,
                    List.of()
            );
        }

        Map<String, String> userNames = loadUserNames(rows);

        int firstScore = scoreOf(rows.get(0));

        // Sayfa aynı puanlı oyuncuların ortasından başlayabilir.
        // İlk oyuncudan yüksek puanlı kazananların sayısı gerçek sırayı verir.
        long higherScoreCount = mongoTemplate.count(
                Query.query(
                        Criteria.where("date").is(today)
                                .and("status").is("WON")
                                .and("score").gt(firstScore)
                ),
                Attempt.class
        );

        long rank = higherScoreCount + 1;
        Integer previousScore = null;

        List<LeaderboardResponse.Entry> entries = new ArrayList<>();

        for (int index = 0; index < rows.size(); index++) {
            Document row = rows.get(index);
            int score = scoreOf(row);

            if (previousScore != null && score != previousScore) {
                rank = offset + index + 1;
            }

            String userId = row.getString("userId");
            String userName = userNames.getOrDefault(
                    userId,
                    "Oyuncu"
            );

            entries.add(
                    new LeaderboardResponse.Entry(
                            rank,
                            userName,
                            score,
                            row.getString("status")
                    )
            );

            previousScore = score;
        }

        return new LeaderboardResponse(
                today,
                page,
                size,
                totalPlayers,
                List.copyOf(entries)
        );
    }

    private LeaderboardResponse readCached(
            String key,
            String date,
            int page,
            int size
    ) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return null;
            }

            LeaderboardResponse cached = jsonMapper.readValue(
                    json,
                    LeaderboardResponse.class
            );

            if (cached == null
                    || !date.equals(cached.date())
                    || cached.page() != page
                    || cached.size() != size
                    || cached.totalPlayers() < 0
                    || cached.entries() == null
                    || cached.entries().size() > size) {
                return null;
            }

            return cached;
        } catch (DataAccessException | JacksonException exception) {
            log.debug("Sıralama önbelleği okunamadı; MongoDB kullanılacak.", exception);
            return null;
        }
    }

    private void writeCached(String key, LeaderboardResponse result) {
        try {
            redis.opsForValue().set(
                    key,
                    jsonMapper.writeValueAsString(result),
                    CACHE_TTL
            );
        } catch (DataAccessException | JacksonException exception) {
            log.debug("Sıralama önbelleğe yazılamadı; hesaplanan yanıt dönecek.", exception);
        }
    }

    public LeaderboardResponse.MyRanking todayForUser(String userId) {
        String today = LocalDate.now(gameClock).toString();
        String attemptId = userId + ":" + today;

        // Bu küçük modelde id alanı yok; MongoDB alan adını açıkça kullan.
        Query query = Query.query(
                Criteria.where("_id").is(attemptId)
        );

        query.fields()
                .include("status")
                .include("score");

        AttemptRankRow attempt = mongoTemplate.findOne(
                query,
                AttemptRankRow.class,
                mongoTemplate.getCollectionName(Attempt.class)
        );

        if (attempt == null) {
            return new LeaderboardResponse.MyRanking(
                    today,
                    "NOT_PLAYED",
                    false,
                    null,
                    0
            );
        }

        String status = attempt.status();

        if ("PLAYING".equals(status)) {
            return new LeaderboardResponse.MyRanking(
                    today,
                    status,
                    false,
                    null,
                    0
            );
        }

        if (!"WON".equals(status) && !"LOST".equals(status)) {
            throw new IllegalStateException(
                    "Bilinmeyen oyun durumu: " + status
            );
        }

        int score = "LOST".equals(status)
                ? 0
                : attempt.score() == null ? 0 : attempt.score();

        long higherScoreCount = mongoTemplate.count(
                Query.query(
                        Criteria.where("date").is(today)
                                .and("status").is("WON")
                                .and("score").gt(score)
                ),
                Attempt.class
        );

        return new LeaderboardResponse.MyRanking(
                today,
                status,
                true,
                higherScoreCount + 1,
                score
        );
    }

    private Criteria finishedToday(String date) {
        return Criteria.where("date").is(date)
                .and("status").in("WON", "LOST");
    }

    private Map<String, String> loadUserNames(List<Document> rows) {
        List<String> userIds = rows.stream()
                .map(row -> row.getString("userId"))
                .distinct()
                .toList();

        Query query = Query.query(
                Criteria.where("id").in(userIds)
        );

        query.fields()
                .include("id")
                .include("userName");

        List<UserNameRow> users = mongoTemplate.find(
                query,
                UserNameRow.class,
                mongoTemplate.getCollectionName(TraceUser.class)
        );

        Map<String, String> names = new HashMap<>();

        for (UserNameRow user : users) {
            String name = user.userName();

            names.put(
                    user.id(),
                    name == null || name.isBlank()
                            ? "Oyuncu"
                            : name
            );
        }

        return names;
    }

    private int scoreOf(Document row) {
        return ((Number) row.get("score")).intValue();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
    // Kullanıcı adlarını tam TraceUser modeline dönüştürmeden okur.
    public record UserNameRow(
            @Id String id,
            String userName
    ) {
    }

    // Günlük denemeden yalnızca sıralama için gereken alanları okur.
    public record AttemptRankRow(
            String status,
            Integer score
    ) {
    }
}
