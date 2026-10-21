package com.trace.backend.config;

import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public ApplicationRunner createAuthIndexes(MongoTemplate mongoTemplate) {
        return args -> mongoTemplate.getCollection("user")
                .createIndex(
                        new Document("email", 1),
                        new IndexOptions()
                                .name("unique_registered_email")
                                .unique(true)
                                .partialFilterExpression(
                                        new Document(
                                                "email",
                                                new Document("$type", "string")
                                        )
                                )
                );
    }
}