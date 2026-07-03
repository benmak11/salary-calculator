package app.salary.api.auth;

import app.salary.api.store.UserDirectory;
import app.salary.api.validation.RequestValidator;
import app.salary.common.constants.ApiConstants;
import app.salary.common.dto.AppleSignInRequest;
import app.salary.common.dto.AppleSignInResponse;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AppleIdentityVerifier verifier;
    private final SessionTokenService sessionTokens;
    private final UserDirectory users;
    private final RequestValidator validator;

    public AuthController(AppleIdentityVerifier verifier,
                          SessionTokenService sessionTokens,
                          UserDirectory users,
                          RequestValidator validator) {
        this.verifier = verifier;
        this.sessionTokens = sessionTokens;
        this.users = users;
        this.validator = validator;
    }

    public void register(RoutesConfig routes) {
        routes.post("/v1/auth/apple", this::signInWithApple);
    }

    private void signInWithApple(Context ctx) {
        AppleSignInRequest req = ctx.bodyAsClass(AppleSignInRequest.class);
        validator.validate(req);

        VerifiedAppleIdentity identity;
        try {
            identity = verifier.verify(req.getIdentityToken(), req.getNonce());
        } catch (AppleVerificationException e) {
            log.warn("Apple identity verification failed: {}", e.getMessage());
            ctx.status(HttpStatus.UNAUTHORIZED)
                    .json(Map.of(ApiConstants.ERROR, "Invalid Apple identity token"));
            return;
        }

        String userId = identity.appleSub();
        MDC.put(ApiConstants.MDC_USER_ID, userId);

        // We intentionally do not persist identity.email() — see UserDirectory.
        users.upsertOnSignIn(userId, req.getDisplayName());

        SessionTokenService.IssuedSession session = sessionTokens.mint(userId);
        String storedDisplayName = users.displayName(userId).orElse(req.getDisplayName());

        AppleSignInResponse.User user = new AppleSignInResponse.User(userId, storedDisplayName);
        AppleSignInResponse response = new AppleSignInResponse(
                session.token(),
                DateTimeFormatter.ISO_INSTANT.format(session.expiresAt().atOffset(ZoneOffset.UTC)),
                user);

        log.info("Apple sign-in succeeded");
        ctx.json(response);
    }
}
