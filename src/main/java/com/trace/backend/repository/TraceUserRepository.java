package com.trace.backend.repository;

import com.trace.backend.model.TraceUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TraceUserRepository
        extends MongoRepository<TraceUser, String> {

    Optional<TraceUser> findByGuestTokenHash(String guestTokenHash);
}