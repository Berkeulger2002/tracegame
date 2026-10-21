package com.trace.backend.service;

import com.trace.backend.dto.AdminGameRequest;
import com.trace.backend.dto.PublishGameRequest;
import com.trace.backend.model.TraceGame;
import com.trace.backend.model.TraceUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AdminGameService {
    private final MongoTemplate mongoTemplate;
    private final GuestAuthService authService;
    private final Clock gameClock;

    public AdminGameService(
            MongoTemplate mongoTemplate,
            GuestAuthService authService,
            Clock gameClock
    ) {
        this.mongoTemplate = mongoTemplate;
        this.authService = authService;
        this.gameClock = gameClock;
    }

    public List<TraceGame> list(
            String token,
            TraceGame.GameStatus status,
            int page,
            int size
    ) {
        authService.requireAdmin(token);

        if (page < 0 || page > 100_000 || size < 1 || size > 100) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "page 0–100000, size 1–100 aralığında olmalı."
            );
        }

        Query query = new Query();

        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }

        query.with(Sort.by(
                Sort.Order.desc("publishDate"),
                Sort.Order.desc("_id")
        ));

        query.skip((long) page * size);
        query.limit(size);

        return mongoTemplate.find(query, TraceGame.class);
    }

    public TraceGame get(String token, String id) {
        authService.requireAdmin(token);
        return findGame(id);
    }

    public TraceGame create(String token, AdminGameRequest request) {
        TraceUser admin = authService.requireAdmin(token);
        validateDate(request.publishDate());

        List<TraceGame.Step> steps = buildSteps(request.steps());
        Instant now = now();

        TraceGame game = new TraceGame(
                null,
                request.title().strip(),
                request.langCode(),
                request.publishDate().toString(),
                request.difficulty(),
                TraceGame.GameStatus.DRAFT,
                request.startEvent().strip(),
                request.endEvent().strip(),
                steps,
                now,
                now,
                admin.id(),
                admin.id(),
                cleanExplanation(request.startExplanation()),
                cleanExplanation(request.endExplanation())
        );

        try {
            return mongoTemplate.insert(game);
        } catch (DuplicateKeyException exception) {
            throw dateConflict();
        }
    }

    public TraceGame update(
            String token,
            String id,
            AdminGameRequest request
    ) {
        TraceUser admin = authService.requireAdmin(token);
        TraceGame existing = findGame(id);

        requireEditable(existing, request.expectedUpdatedAt());
        validateDate(request.publishDate());

        List<TraceGame.Step> steps = buildSteps(request.steps());

        Update update = new Update()
                .set("title", request.title().strip())
                .set("langCode", request.langCode())
                .set("publishDate", request.publishDate().toString())
                .set("difficulty", request.difficulty())
                .set("startEvent", request.startEvent().strip())
                .set("endEvent", request.endEvent().strip())
                .set(
                        "startExplanation",
                        cleanExplanation(request.startExplanation())
                )
                .set(
                        "endExplanation",
                        cleanExplanation(request.endExplanation())
                )
                .set("steps", steps)
                .set("updatedAt", nextUpdatedAt(existing.updatedAt()))
                .set("updatedBy", admin.id());

        try {
            TraceGame saved = mongoTemplate.findAndModify(
                    editableQuery(existing),
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    TraceGame.class
            );

            if (saved == null) {
                throw concurrentChange();
            }

            return saved;
        } catch (DuplicateKeyException exception) {
            throw dateConflict();
        }
    }

    public TraceGame publish(
            String token,
            String id,
            PublishGameRequest request
    ) {
        TraceUser admin = authService.requireAdmin(token);
        TraceGame existing = findGame(id);

        requireEditable(existing, request.expectedUpdatedAt());
        validateStoredGame(existing);

        Update update = new Update()
                .set("status", TraceGame.GameStatus.PUBLISHED)
                .set("updatedAt", nextUpdatedAt(existing.updatedAt()))
                .set("updatedBy", admin.id());

        TraceGame saved = mongoTemplate.findAndModify(
                editableQuery(existing),
                update,
                FindAndModifyOptions.options().returnNew(true),
                TraceGame.class
        );

        if (saved == null) {
            throw concurrentChange();
        }

        return saved;
    }

    private TraceGame findGame(String id) {
        TraceGame game = mongoTemplate.findById(id, TraceGame.class);

        if (game == null) {
            throw error(HttpStatus.NOT_FOUND, "Oyun bulunamadı.");
        }

        return game;
    }

    private void requireEditable(
            TraceGame game,
            Instant expectedUpdatedAt
    ) {
        if (game.status() != TraceGame.GameStatus.DRAFT) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Yayınlanmış oyun değiştirilemez."
            );
        }

        validateDate(LocalDate.parse(game.publishDate()));

        if (expectedUpdatedAt == null) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "expectedUpdatedAt alanını göndermelisin."
            );
        }

        if (!Objects.equals(game.updatedAt(), expectedUpdatedAt)) {
            throw concurrentChange();
        }
    }

    private Query editableQuery(TraceGame game) {
        return Query.query(
                Criteria.where("_id").is(game.id())
                        .and("status").is(TraceGame.GameStatus.DRAFT)
                        .and("updatedAt").is(game.updatedAt())
                        .and("publishDate")
                        .gte(LocalDate.now(gameClock).toString())
        );
    }

    private List<TraceGame.Step> buildSteps(
            List<AdminGameRequest.StepInput> inputs
    ) {
        List<TraceGame.Step> steps = new ArrayList<>();

        for (int index = 0; index < inputs.size(); index++) {
            int position = index + 1;
            AdminGameRequest.StepInput input = inputs.get(index);

            List<String> texts = input.options().stream()
                    .map(String::strip)
                    .toList();

            if (texts.stream().distinct().count() != 3) {
                throw error(
                        HttpStatus.BAD_REQUEST,
                        position + ". aşamadaki seçenekler farklı olmalı."
                );
            }

            List<TraceGame.Option> options = new ArrayList<>();

            for (int optionIndex = 0; optionIndex < 3; optionIndex++) {
                options.add(new TraceGame.Option(
                        optionId(position, optionIndex),
                        texts.get(optionIndex)
                ));
            }

            steps.add(new TraceGame.Step(
                    "step-" + position,
                    position,
                    List.copyOf(options),
                    optionId(position, input.correctOptionIndex()),
                    cleanExplanation(input.explanation())
            ));
        }

        return List.copyOf(steps);
    }

    private String optionId(int position, int optionIndex) {
        char letter = (char) ('a' + optionIndex);
        return "s" + position + "-" + letter;
    }

    private String cleanExplanation(String value) {
        return value == null ? "" : value.strip();
    }

    private void validateStoredGame(TraceGame game) {
        validateDate(LocalDate.parse(game.publishDate()));

        if (game.title() == null || game.title().isBlank()
                || game.langCode() == null
                || !game.langCode().matches("[a-z]{2}")
                || game.difficulty() == null
                || game.startEvent() == null || game.startEvent().isBlank()
                || game.endEvent() == null || game.endEvent().isBlank()
                || game.steps() == null || game.steps().isEmpty()
                || game.steps().size() > 30) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Oyunun zorunlu alanları eksik veya geçersiz."
            );
        }

        for (int index = 0; index < game.steps().size(); index++) {
            TraceGame.Step step = game.steps().get(index);

            if (step == null
                    || step.position() != index + 1
                    || !Objects.equals(step.id(), "step-" + (index + 1))
                    || step.options() == null
                    || step.options().size() != 3) {
                throw error(
                        HttpStatus.BAD_REQUEST,
                        "Aşama sırası veya seçenek sayısı geçersiz."
                );
            }

            for (int optionIndex = 0; optionIndex < 3; optionIndex++) {
                TraceGame.Option option = step.options().get(optionIndex);

                if (option == null
                        || !Objects.equals(
                        option.id(),
                        optionId(index + 1, optionIndex)
                )
                        || option.text() == null
                        || option.text().isBlank()) {
                    throw error(
                            HttpStatus.BAD_REQUEST,
                            "Seçenek bilgileri geçersiz."
                    );
                }
            }

            long differentTexts = step.options().stream()
                    .map(option -> option.text().strip())
                    .distinct()
                    .count();

            boolean correctExists = step.options().stream()
                    .anyMatch(option ->
                            option.id().equals(step.correctOptionId())
                    );

            if (differentTexts != 3 || !correctExists) {
                throw error(
                        HttpStatus.BAD_REQUEST,
                        "Seçenekler veya doğru cevap geçersiz."
                );
            }
        }
    }

    private void validateDate(LocalDate date) {
        if (date.isBefore(LocalDate.now(gameClock))) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Geçmiş tarih için oyun oluşturulamaz veya yayınlanamaz."
            );
        }
    }

    private Instant now() {
        return gameClock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private Instant nextUpdatedAt(Instant previous) {
        Instant current = now();

        if (previous != null && !current.isAfter(previous)) {
            return previous.plusMillis(1);
        }

        return current;
    }

    private ResponseStatusException dateConflict() {
        return error(
                HttpStatus.CONFLICT,
                "Bu tarihe ait bir taslak veya yayınlanmış oyun zaten var."
        );
    }

    private ResponseStatusException concurrentChange() {
        return error(
                HttpStatus.CONFLICT,
                "Oyun başka bir istek tarafından değiştirildi. "
                        + "Güncel detayını yükleyip tekrar dene."
        );
    }

    private ResponseStatusException error(
            HttpStatus status,
            String message
    ) {
        return new ResponseStatusException(status, message);
    }
}