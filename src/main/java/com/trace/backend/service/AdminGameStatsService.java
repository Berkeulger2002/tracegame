package com.trace.backend.service;

import com.trace.backend.dto.AdminGameStatsResponse;
import com.trace.backend.model.Attempt;
import com.trace.backend.model.TraceGame;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class AdminGameStatsService {

    private static final Logger log =
            LoggerFactory.getLogger(AdminGameStatsService.class);

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final AdminGameService adminGameService;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;

    public AdminGameStatsService(
            AdminGameService adminGameService,
            MongoTemplate mongoTemplate,
            StringRedisTemplate redisTemplate
    ) {
        this.adminGameService = adminGameService;
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
    }

    public AdminGameStatsResponse getStats(String token, String gameId) {
        // Önbelleğe bakmadan önce her istekte admin kontrolü yapılır.
        // Oyunun varlığı ve güncel başlığı da mevcut servisten alınır.
        TraceGame game = adminGameService.get(token, gameId);

        String key = "trace:admin:game-stats:v1:" + game.id();

        Counts counts = readCache(key);

        if (counts == null) {
            counts = calculateCounts(game.id());
            writeCache(key, counts);
        }

        long started = counts.started();

        double winRate = started == 0
                ? 0.0
                : Math.round(counts.won() * 1000.0 / started) / 10.0;

        return new AdminGameStatsResponse(
                game.id(),
                game.title(),
                game.publishDate(),
                started,
                counts.won(),
                counts.lost(),
                counts.unfinished(),
                winRate
        );
    }

    private Counts readCache(String key) {
        String cached;

        try {
            cached = redisTemplate.opsForValue().get(key);
        } catch (DataAccessException exception) {
            log.warn(
                    "İstatistik önbelleği okunamadı; MongoDB kullanılacak. Tür: {}",
                    exception.getClass().getSimpleName()
            );
            return null;
        }

        if (cached == null) {
            log.debug("İstatistik önbelleğinde kayıt yok: {}", key);
            return null;
        }

        try {
            // Saklama biçimi: kazanan:elenen:tamamlamayan
            String[] values = cached.split(":", -1);

            if (values.length != 3) {
                throw new IllegalArgumentException("Geçersiz sayaç biçimi.");
            }

            Counts counts = new Counts(
                    Long.parseLong(values[0]),
                    Long.parseLong(values[1]),
                    Long.parseLong(values[2])
            );

            log.debug("İstatistik önbellekten okundu: {}", key);
            return counts;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            log.warn(
                    "İstatistik önbelleği geçersiz; MongoDB kullanılacak: {}",
                    key
            );
            return null;
        }
    }

    private void writeCache(String key, Counts counts) {
        String value = counts.won()
                + ":" + counts.lost()
                + ":" + counts.unfinished();

        try {
            // Değer ve 30 saniyelik ömür aynı işlemde yazılır.
            redisTemplate.opsForValue().set(key, value, CACHE_TTL);
        } catch (DataAccessException exception) {
            log.warn(
                    "İstatistik önbelleğe yazılamadı; hesaplanan sonuç dönecek. Tür: {}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private Counts calculateCounts(String gameId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(
                        Criteria.where("gameId").is(gameId)
                ),
                Aggregation.group("status")
                        .count()
                        .as("playerCount")
        );

        List<Document> results = mongoTemplate.aggregate(
                aggregation,
                mongoTemplate.getCollectionName(Attempt.class),
                Document.class
        ).getMappedResults();

        long won = 0;
        long lost = 0;
        long unfinished = 0;

        for (Document result : results) {
            String status = result.getString("_id");
            long count = ((Number) result.get("playerCount")).longValue();

            if (Attempt.Status.WON.name().equals(status)) {
                won = count;
            } else if (Attempt.Status.LOST.name().equals(status)) {
                lost = count;
            } else if (Attempt.Status.PLAYING.name().equals(status)) {
                unfinished = count;
            } else {
                throw new IllegalStateException(
                        "Bilinmeyen oyun denemesi durumu: " + status
                );
            }
        }

        log.debug("İstatistik MongoDB üzerinden hesaplandı: {}", gameId);

        return new Counts(won, lost, unfinished);
    }

    private record Counts(
            long won,
            long lost,
            long unfinished
    ) {
        Counts {
            if (won < 0 || lost < 0 || unfinished < 0) {
                throw new IllegalArgumentException(
                        "Oyuncu sayıları negatif olamaz."
                );
            }

            Math.addExact(Math.addExact(won, lost), unfinished);
        }

        long started() {
            return Math.addExact(Math.addExact(won, lost), unfinished);
        }
    }
}