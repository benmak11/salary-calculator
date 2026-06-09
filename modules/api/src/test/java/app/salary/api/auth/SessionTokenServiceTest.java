package app.salary.api.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTokenServiceTest {

    private static byte[] secret() {
        byte[] s = new byte[32];
        for (int i = 0; i < s.length; i++) s[i] = (byte) i;
        return s;
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new SessionTokenService(new byte[16]));
    }

    @Test
    void roundTripsUserId() {
        SessionTokenService svc = new SessionTokenService(secret());
        SessionTokenService.IssuedSession session = svc.mint("user_apple_sub");
        Optional<String> back = svc.validate(session.token());
        assertTrue(back.isPresent());
        assertEquals("user_apple_sub", back.get());
    }

    @Test
    void rejectsExpiredToken() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofDays(60)), ZoneOffset.UTC);
        SessionTokenService old = new SessionTokenService(secret(), Duration.ofDays(30), past);
        String token = old.mint("u").token();

        SessionTokenService current = new SessionTokenService(secret());
        assertFalse(current.validate(token).isPresent());
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        SessionTokenService a = new SessionTokenService(secret());
        byte[] otherSecret = secret();
        otherSecret[0] = (byte) (otherSecret[0] ^ 0xFF);
        SessionTokenService b = new SessionTokenService(otherSecret);
        String tokenFromA = a.mint("u").token();
        assertFalse(b.validate(tokenFromA).isPresent());
    }

    @Test
    void rejectsGarbageInput() {
        SessionTokenService svc = new SessionTokenService(secret());
        assertFalse(svc.validate(null).isPresent());
        assertFalse(svc.validate("").isPresent());
        assertFalse(svc.validate("not-a-jwt").isPresent());
    }
}
