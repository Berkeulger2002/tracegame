package com.trace.backend.repository;

import com.trace.backend.model.TraceGame;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TraceGameRepository
        extends MongoRepository<TraceGame, String> {

    boolean existsByPublishDate(String publishDate);

    Optional<TraceGame> findByPublishDateAndStatus(
            String publishDate,
            TraceGame.GameStatus status
    );
}