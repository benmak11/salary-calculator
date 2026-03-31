package app.salary.integration;

import app.salary.api.SalaryCalculatorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SalaryCalculatorApplication.class)
@AutoConfigureMockMvc
class AppEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ── /v1/app/legal ─────────────────────────────────────────────────────────────

    @Test
    void getAppLegal_shouldReturnThreeUrls() throws Exception {
        mockMvc.perform(get("/v1/app/legal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpCenterUrl",    notNullValue()))
                .andExpect(jsonPath("$.privacyPolicyUrl", notNullValue()))
                .andExpect(jsonPath("$.termsUrl",         notNullValue()));
    }

    // ── /v1/app/version ───────────────────────────────────────────────────────────

    @Test
    void getAppVersion_shouldReturnVersionInfo() throws Exception {
        mockMvc.perform(get("/v1/app/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestVersion",          notNullValue()))
                .andExpect(jsonPath("$.minimumSupportedVersion",notNullValue()))
                .andExpect(jsonPath("$.forceUpgrade",           is(false)))
                .andExpect(jsonPath("$.releaseNotesUrl",        notNullValue()));
    }

    // ── /v1/countries/US/states ───────────────────────────────────────────────────

    @Test
    void getUsStates_shouldReturn51Entries() throws Exception {
        mockMvc.perform(get("/v1/countries/US/states"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(51)))
                .andExpect(jsonPath("$[?(@.code == 'CA')].name", contains("California")))
                .andExpect(jsonPath("$[?(@.code == 'TX')].name", contains("Texas")))
                .andExpect(jsonPath("$[?(@.code == 'DC')].name", contains("District of Columbia")));
    }

    // ── /v1/tax-years ─────────────────────────────────────────────────────────────

    @Test
    void getSupportedTaxYears_forUS_shouldReturnAtLeast2025() throws Exception {
        mockMvc.perform(get("/v1/tax-years?country=US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country", is("US")))
                .andExpect(jsonPath("$.supportedTaxYears", hasItem(2025)))
                .andExpect(jsonPath("$.defaultTaxYear", notNullValue()));
    }

    // ── /v1/auth ──────────────────────────────────────────────────────────────────

    @Test
    void login_shouldReturnAccessAndRefreshTokens() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "test@example.com", "password": "password123" }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken",  notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.expiresIn",    is(3600)));
    }

    @Test
    void logout_shouldReturn204() throws Exception {
        mockMvc.perform(post("/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "refreshToken": "tok_abc" }
                    """))
                .andExpect(status().isNoContent());
    }

    @Test
    void refreshToken_withValidToken_shouldReturnNewAccess() throws Exception {
        mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "refreshToken": "tok_existing" }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    // ── /v1/users ─────────────────────────────────────────────────────────────────

    @Test
    void getUserProfile_shouldReturnProfile() throws Exception {
        mockMvc.perform(get("/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id",          notNullValue()))
                .andExpect(jsonPath("$.displayName", notNullValue()))
                .andExpect(jsonPath("$.email",       notNullValue()));
    }

    @Test
    void patchUserProfile_shouldReturnUpdatedProfile() throws Exception {
        mockMvc.perform(patch("/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "displayName": "Updated Name" }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Updated Name")));
    }

    @Test
    void getUserSecurity_shouldReturnSecuritySettings() throws Exception {
        mockMvc.perform(get("/v1/users/me/security"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled", notNullValue()))
                .andExpect(jsonPath("$.biometricEnabled", notNullValue()));
    }

    @Test
    void updateBiometrics_shouldReturn204() throws Exception {
        mockMvc.perform(put("/v1/users/me/security/biometrics")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "biometricEnabled": true }
                    """))
                .andExpect(status().isNoContent());
    }

    @Test
    void getUserPreferences_shouldReturnDefaults() throws Exception {
        mockMvc.perform(get("/v1/users/me/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language",           notNullValue()))
                .andExpect(jsonPath("$.appearance",         notNullValue()))
                .andExpect(jsonPath("$.notifications",      notNullValue()));
    }
}
