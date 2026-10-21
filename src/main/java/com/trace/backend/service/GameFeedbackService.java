package com.trace.backend.service;

import com.trace.backend.dto.GameFeedbackDto;
import com.trace.backend.model.Attempt;
import com.trace.backend.model.GameFeedback;
import com.trace.backend.model.TraceGame;
import com.trace.backend.model.TraceUser;
import com.trace.backend.repository.AttemptRepository;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class GameFeedbackService {

    private final AttemptRepository attemptRepository;
    private final GuestAuthService authService;
    private final AdminGameService adminGameService;
    private final MongoTemplate mongoTemplate;
    private final Clock gameClock;

    public GameFeedbackService(
            AttemptRepository attemptRepository,
            GuestAuthService authService,
            AdminGameService adminGameService,
            MongoTemplate mongoTemplate,
            Clock gameClock
    ) {
        this.attemptRepository = attemptRepository;
        this.authService = authService;
        this.adminGameService = adminGameService;
        this.mongoTemplate = mongoTemplate;
        this.gameClock = gameClock;
    }

    public GameFeedbackDto.Response getToday(String token) {
        Attempt attempt = requireFinishedAttempt(token);

        GameFeedback feedback = mongoTemplate.findById(
                attempt.id(),
                GameFeedback.class
        );

        return new GameFeedbackDto.Response(
                attempt.gameId(),
                attempt.date(),
                feedback == null ? null : feedback.rating(),
                feedback == null ? null : feedback.updatedAt()
        );
    }

    public GameFeedbackDto.Response saveToday(
            String token,
            GameFeedbackDto.Request request
    ) {
        Attempt attempt = requireFinishedAttempt(token);
        Instant now = gameClock.instant();

        // Günlük attempt kimliği aynı zamanda geri bildirim kimliğidir.
        // MongoDB'nin benzersiz _id alanı ikinci kaydı engeller.
        Query query = Query.query(
                Criteria.where("_id").is(attempt.id())
        );

        Update update = new Update()
                .setOnInsert("userId", attempt.userId())
                .setOnInsert("gameId", attempt.gameId())
                .setOnInsert("date", attempt.date())
                .setOnInsert("createdAt", now)
                .set("rating", request.rating())
                .set("updatedAt", now);

        GameFeedback saved;

        try {
            saved = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options()
                            .upsert(true)
                            .returnNew(true),
                    GameFeedback.class
            );
        } catch (DuplicateKeyException exception) {
            // Aynı anda gelen ilk iki istekte kayıt diğer istekçe
            // oluşturulmuş olabilir. Var olan kaydı güncelle.
            saved = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    GameFeedback.class
            );
        }

        if (saved == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Değerlendirme kaydedilemedi. Tekrar dene."
            );
        }

        return new GameFeedbackDto.Response(
                saved.gameId(),
                saved.date(),
                saved.rating(),
                saved.updatedAt()
        );
    }

    public GameFeedbackDto.Stats getStats(String token, String gameId) {
        // Her istekte admin yetkisini ve oyunun varlığını kontrol eder.
        TraceGame game = adminGameService.get(token, gameId);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(
                        Criteria.where("gameId").is(game.id())
                ),
                Aggregation.group("rating").count().as("count")
        );

        long good = 0;
        long bad = 0;

        for (Document row : mongoTemplate.aggregate(
                aggregation,
                mongoTemplate.getCollectionName(GameFeedback.class),
                Document.class
        ).getMappedResults()) {
            long count = ((Number) row.get("count")).longValue();
            String rating = row.getString("_id");

            if (GameFeedback.Rating.GOOD.name().equals(rating)) {
                good = count;
            } else if (GameFeedback.Rating.BAD.name().equals(rating)) {
                bad = count;
            } else {
                throw new IllegalStateException(
                        "Bilinmeyen değerlendirme: " + rating
                );
            }
        }

        return new GameFeedbackDto.Stats(
                game.id(),
                good + bad,
                good,
                bad
        );
    }

    private Attempt requireFinishedAttempt(String token) {
        TraceUser user = authService.requireGuest(token);
        String today = LocalDate.now(gameClock).toString();

        Attempt attempt = attemptRepository
                .findById(user.id() + ":" + today)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Önce bugünün oyununu oynamalısın."
                ));

        if (attempt.status() != Attempt.Status.WON
                && attempt.status() != Attempt.Status.LOST) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Değerlendirme için oyunu tamamlamalısın."
            );
        }

        return attempt;
    }
}