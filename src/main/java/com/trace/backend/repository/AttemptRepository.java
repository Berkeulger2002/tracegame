package com.trace.backend.repository;

import com.trace.backend.model.Attempt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AttemptRepository
        extends MongoRepository<Attempt, String> {

    List<Attempt> findByUserIdAndStatus(
            String userId,
            Attempt.Status status
    );
}