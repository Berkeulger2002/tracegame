package com.trace.backend.service;

import com.trace.backend.dto.LoginRequest;
import com.trace.backend.dto.RegisterRequest;
import com.trace.backend.model.TraceUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class GuestAuthService {
    private static final Duration GUEST_LIFETIME = Duration.ofDays(30);
    private static final Duration ACCOUNT_LIFETIME = Duration.ofDays(7);

    private final MongoTemplate mongoTemplate;
    private final PasswordEncoder passwordEncoder;
    private final Clock gameClock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String dummyPasswordHash;

    public GuestAuthService(
            MongoTemplate mongoTemplate,
            PasswordEncoder passwordEncoder,
            Clock gameClock
    ) {
        this.mongoTemplate = mongoTemplate;
        this.passwordEncoder = passwordEncoder;
        this.gameClock = gameClock;

        // Bulunmayan hesaplarda da parola karşılaştırması çalıştırılır.
        this.dummyPasswordHash = passwordEncoder.encode(
                UUID.randomUUID().toString()
        );
    }

    public GuestSession getOrCreateGuest(String token) {
        Optional<TraceUser> existing = findSession(token);

        if (existing.isPresent()) {
            // Kayıtlı oturumu yanlışlıkla yeni misafire dönüştürme.
            return new GuestSession(existing.get(), token);
        }

        Instant now = gameClock.instant();
        String newToken = generateToken();

        TraceUser user = new TraceUser(
                null,
                "Misafir-" + UUID.randomUUID().toString().substring(0, 8),
                true,
                false,
                hashToken(newToken),
                now.plus(GUEST_LIFETIME),
                now,
                null,
                null,
                TraceUser.Role.USER
        );

        return new GuestSession(
                mongoTemplate.insert(user),
                newToken
        );
    }

    public GuestSession register(String token, RegisterRequest request) {
        checkPasswordBytes(request.password());

        String userName = request.userName().strip();
        String email = normalizeEmail(request.email());

        if (userName.length() < 2) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Kullanıcı adı en az iki karakter olmalı."
            );
        }

        TraceUser current = findSession(token).orElse(null);

        if (current != null && !current.isAnonymous()) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Zaten kayıtlı bir hesapla giriş yaptın."
            );
        }

        String passwordHash = passwordEncoder.encode(request.password());
        String newToken = generateToken();
        String newTokenHash = hashToken(newToken);
        Instant now = gameClock.instant();
        Instant expiresAt = now.plus(ACCOUNT_LIFETIME);

        try {
            if (current == null) {
                TraceUser user = new TraceUser(
                        null,
                        userName,
                        false,
                        false,
                        newTokenHash,
                        expiresAt,
                        now,
                        email,
                        passwordHash,
                        TraceUser.Role.USER
                );

                return new GuestSession(
                        mongoTemplate.insert(user),
                        newToken
                );
            }

            // Aynı kullanıcı ID'si korunur.
            // Eş zamanlı ikinci kayıt isteği bu koşula artık uymaz.
            Query query = Query.query(
                    Criteria.where("_id").is(current.id())
                            .and("isAnonymous").is(true)
                            .and("guestTokenHash").is(current.guestTokenHash())
                            .and("guestExpiresAt").gt(now)
            );

            Update update = new Update()
                    .set("userName", userName)
                    .set("email", email)
                    .set("passwordHash", passwordHash)
                    .set("isAnonymous", false)
                    .set("role", TraceUser.Role.USER)
                    .set("guestTokenHash", newTokenHash)
                    .set("guestExpiresAt", expiresAt);

            TraceUser saved = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    TraceUser.class
            );

            if (saved == null) {
                throw error(
                        HttpStatus.CONFLICT,
                        "Oturum değişti. Güncel oturumla tekrar dene."
                );
            }

            return new GuestSession(saved, newToken);

        } catch (DuplicateKeyException exception) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Bu e-posta adresiyle kayıt yapılamıyor."
            );
        }
    }

    public GuestSession login(LoginRequest request) {
        checkPasswordBytes(request.password());

        String email = normalizeEmail(request.email());

        TraceUser user = mongoTemplate.findOne(
                Query.query(
                        Criteria.where("email").is(email)
                                .and("isAnonymous").is(false)
                ),
                TraceUser.class
        );

        String storedHash = user == null || user.passwordHash() == null
                ? dummyPasswordHash
                : user.passwordHash();

        boolean matches = passwordEncoder.matches(
                request.password(),
                storedHash
        );

        if (user == null || user.passwordHash() == null || !matches) {
            throw error(
                    HttpStatus.UNAUTHORIZED,
                    "E-posta veya şifre hatalı."
            );
        }

        String newToken = generateToken();

        Query query = Query.query(
                Criteria.where("_id").is(user.id())
                        .and("passwordHash").is(user.passwordHash())
                        .and("isAnonymous").is(false)
        );

        Update update = new Update()
                .set("guestTokenHash", hashToken(newToken))
                .set(
                        "guestExpiresAt",
                        gameClock.instant().plus(ACCOUNT_LIFETIME)
                );

        TraceUser saved = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                TraceUser.class
        );

        if (saved == null) {
            throw error(
                    HttpStatus.UNAUTHORIZED,
                    "Hesap bilgileri değişti. Tekrar giriş yap."
            );
        }

        return new GuestSession(saved, newToken);
    }

    public void logout(String token) {
        if (!validTokenFormat(token)) {
            return;
        }

        // Yalnızca istekte kullanılan oturumu kapat.
        Query query = Query.query(
                Criteria.where("guestTokenHash").is(hashToken(token))
        );

        mongoTemplate.updateFirst(
                query,
                new Update()
                        .unset("guestTokenHash")
                        .unset("guestExpiresAt"),
                TraceUser.class
        );
    }

    public TraceUser requireUser(String token) {
        return findSession(token).orElseThrow(() ->
                error(
                        HttpStatus.UNAUTHORIZED,
                        "Oturum geçersiz veya süresi dolmuş."
                )
        );
    }

    // Eski oyun servisleriyle uyumluluk.
    public TraceUser requireGuest(String token) {
        return requireUser(token);
    }

    public TraceUser requireAdmin(String token) {
        TraceUser user = requireUser(token);

        if (user.isAnonymous() || user.role() != TraceUser.Role.ADMIN) {
            throw error(
                    HttpStatus.FORBIDDEN,
                    "Bu işlem için admin yetkisi gerekli."
            );
        }

        return user;
    }

    private Optional<TraceUser> findSession(String token) {
        if (!validTokenFormat(token)) {
            return Optional.empty();
        }

        TraceUser user = mongoTemplate.findOne(
                Query.query(
                        Criteria.where("guestTokenHash").is(hashToken(token))
                                .and("guestExpiresAt")
                                .gt(gameClock.instant())
                ),
                TraceUser.class
        );

        return Optional.ofNullable(user);
    }

    private boolean validTokenFormat(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void checkPasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Şifre UTF-8 olarak en fazla 72 bayt olabilir."
            );
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 kullanılamıyor.",
                    exception
            );
        }
    }

    private ResponseStatusException error(
            HttpStatus status,
            String message
    ) {
        return new ResponseStatusException(status, message);
    }

    public record GuestSession(
            TraceUser user,
            String token
    ) {
    }
}