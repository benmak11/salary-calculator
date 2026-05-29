package app.salary.api.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

public class AppController {

    public void register(Javalin app) {
        app.get("/v1/app/legal", this::getLegal);
        app.get("/v1/app/version", this::getVersion);
    }

    private void getLegal(Context ctx) {
        // TODO: Source from a CMS or application configuration rather than hardcoding.
        ctx.json(Map.of(
                "helpCenterUrl",    "https://help.incomatic.app",
                "privacyPolicyUrl", "https://incomatic.app/privacy",
                "termsUrl",         "https://incomatic.app/terms"
        ));
    }

    private void getVersion(Context ctx) {
        // TODO: Drive from a build manifest or release management system.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("latestVersion",           "2.4.0");
        body.put("minimumSupportedVersion", "2.0.0");
        body.put("forceUpgrade",            false);
        body.put("releaseNotesUrl",         "https://incomatic.app/release-notes/2.4.0");
        ctx.json(body);
    }
}
