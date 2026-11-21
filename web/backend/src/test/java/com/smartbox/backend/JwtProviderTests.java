package com.smartbox.backend;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.smartbox.backend.config.JwtProvider;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtProviderTests {
    private final JwtProvider provider = new JwtProvider();
    private final Logger logger = (Logger) LoggerFactory.getLogger(JwtProvider.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(provider, "jwtSecret", "test-secret-that-is-long-enough-for-hs256-signing");
        ReflectionTestUtils.setField(provider, "accessExpirationMs", 60_000L);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void supabaseEs256TokenIsSkippedQuietlySoFilterFallsBackToSupabase() {
        var keys = Jwts.SIG.ES256.keyPair().build();
        String token = Jwts.builder().subject(UUID.randomUUID().toString()).signWith(keys.getPrivate()).compact();

        assertFalse(provider.validateToken(token));
        assertTrue(logs.list.stream().noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.WARN)),
                "ES256 token must not be logged as an error: " + logs.list);
    }

    @Test
    void hs256TokenSignedWithOurSecretIsStillAccepted() {
        UUID id = UUID.randomUUID();
        String token = provider.generateAccessToken(id, "a@b.c", "customer", "A");

        assertTrue(provider.validateToken(token));
        assertEquals(id, provider.extractUserId(token));
    }

    @Test
    void tamperedHs256TokenIsStillRejectedWithError() {
        String token = provider.generateAccessToken(UUID.randomUUID(), "a@b.c", "customer", "A");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertFalse(provider.validateToken(tampered));
        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR));
    }
}
