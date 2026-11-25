package com.stdevsec.atlas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

class AuthFlowIT extends IntegrationTestBase {

    @Test
    void setupIsSingleUseAndRequiresTheToken(CapturedOutput output) {
        ApiClient anon = client();
        if (setupService.isPending()) {
            ApiClient.Response wrong = anon.post("/api/v1/setup", Map.of("token", "00000000000000000000000000000000",
                    "organizationName", "X", "username", "mallory", "displayName", "M", "password",
                    "a very long password 123", "locale", "en", "timeZone", "UTC", "workspaceName", "W"));
            assertThat(wrong.status()).isEqualTo(400);
        }
        ensureSetup(output);
        assertThat(anon.get("/api/v1/setup/status").body().get("pending").asBoolean()).isFalse();
        ApiClient.Response again = anon.post("/api/v1/setup", Map.of("token", "x", "organizationName", "X",
                "username", "mallory", "displayName", "M", "password", "a very long password 123", "locale", "en",
                "timeZone", "UTC", "workspaceName", "W"));
        assertThat(again.status()).isEqualTo(404);
    }

    @Test
    void loginSessionAndLogout(CapturedOutput output) {
        ensureSetup(output);
        ApiClient c = client();
        assertThat(c.get("/api/v1/auth/me").status()).isEqualTo(401);
        assertThat(c.login(ADMIN, "wrong password!!").status()).isEqualTo(401);
        assertThat(c.login(ADMIN, ADMIN_PASSWORD).status()).isEqualTo(200);
        ApiClient.Response me = c.get("/api/v1/auth/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().get("user").get("admin").asBoolean()).isTrue();
        assertThat(me.raw()).doesNotContain("password").doesNotContain("$argon2");
        assertThat(c.post("/api/v1/auth/logout", Map.of()).status()).isEqualTo(204);
        assertThat(c.get("/api/v1/auth/me").status()).isEqualTo(401);
    }

    @Test
    void stateChangingRequestsNeedCsrfToken(CapturedOutput output) throws Exception {
        ensureSetup(output);
        var http = java.net.http.HttpClient.newHttpClient();
        var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"admin\",\"password\":\"" + ADMIN_PASSWORD + "\"}")).build();
        assertThat(http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void sessionCookieIsHardened(CapturedOutput output) {
        ensureSetup(output);
        ApiClient c = client();
        ApiClient.Response r = c.login(ADMIN, ADMIN_PASSWORD);
        String setCookie = String.join(";", r.http().headers().allValues("Set-Cookie"));
        assertThat(setCookie).contains("ATLAS_SESSION=").containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("SameSite=Strict");
    }

    @Test
    void passwordPolicyRejectsShortPasswords(CapturedOutput output) {
        ApiClient admin = admin(output);
        ApiClient.Response r = admin.post("/api/v1/admin/users", Map.of("username", "shorty", "displayName", "S",
                "password", "short"));
        assertThat(r.status()).isEqualTo(400);
    }

    @Test
    void nonAdminsCannotCreateUsers(CapturedOutput output) {
        ApiClient admin = admin(output);
        String name = "user" + System.nanoTime();
        admin.post("/api/v1/admin/users", Map.of("username", name, "displayName", "U",
                "password", "another long password 42"));
        ApiClient user = client();
        user.login(name, "another long password 42");
        assertThat(user.post("/api/v1/admin/users", Map.of("username", "x" + name, "displayName", "X",
                "password", "another long password 42")).status()).isEqualTo(403);
    }
}
