package app.salary.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/app")
@Tag(name = "App", description = "Application metadata endpoints")
public class AppController {

    // ── GET /v1/app/legal ────────────────────────────────────────────────────────

    @GetMapping("/legal")
    @Operation(summary = "Return current legal document and support URLs")
    public ResponseEntity<Map<String, String>> getLegal() {
        // TODO: Source from a CMS or application configuration rather than hardcoding.
        return ResponseEntity.ok(Map.of(
            "helpCenterUrl",    "https://help.incomatic.app",
            "privacyPolicyUrl", "https://incomatic.app/privacy",
            "termsUrl",         "https://incomatic.app/terms"
        ));
    }

    // ── GET /v1/app/version ──────────────────────────────────────────────────────

    @GetMapping("/version")
    @Operation(summary = "Return current app version info and minimum supported version for force-upgrade enforcement")
    public ResponseEntity<Map<String, Object>> getVersion() {
        // TODO: Drive from application.yml or a release management system.
        return ResponseEntity.ok(Map.of(
            "latestVersion",          "2.4.0",
            "minimumSupportedVersion","2.0.0",
            "forceUpgrade",           false,
            "releaseNotesUrl",        "https://incomatic.app/release-notes/2.4.0"
        ));
    }
}
