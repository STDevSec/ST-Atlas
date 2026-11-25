package com.stdevsec.atlas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

/** T-05/T-06: data never crosses workspaces, and roles limit what members can do. */
class WorkspaceIsolationIT extends IntegrationTestBase {

    private ApiClient newUser(ApiClient admin, String name) {
        admin.post("/api/v1/admin/users", Map.of("username", name, "displayName", name,
                "password", "isolation test password"));
        ApiClient c = client();
        assertThat(c.login(name, "isolation test password").status()).isEqualTo(200);
        return c;
    }

    @Test
    void membersOfOtherWorkspacesSeeNothing(CapturedOutput output) {
        ApiClient admin = admin(output);
        String suffix = Long.toString(System.nanoTime());
        ApiClient alice = newUser(admin, "alice" + suffix);
        ApiClient bob = newUser(admin, "bob" + suffix);

        String wsA = alice.post("/api/v1/workspaces", Map.of("name", "Alice WS")).body().get("id").asString();
        String clientId = alice.post("/api/v1/workspaces/" + wsA + "/clients", Map.of("name", "Secret Corp"))
                .body().get("id").asString();
        String assessmentId = alice.post("/api/v1/workspaces/" + wsA + "/assessments", Map.of(
                "title", "Confidential pentest", "typeCode", "WEB_PENTEST", "clientId", clientId,
                "tags", java.util.List.of("secretproject"))).body().get("id").asString();

        // Bob is not a member: everything is 404, including search and the client by id.
        assertThat(bob.get("/api/v1/workspaces/" + wsA + "/clients/" + clientId).status()).isEqualTo(404);
        assertThat(bob.get("/api/v1/workspaces/" + wsA + "/assessments/" + assessmentId).status()).isEqualTo(404);
        assertThat(bob.get("/api/v1/workspaces/" + wsA + "/search?q=confidential").status()).isEqualTo(404);
        assertThat(bob.get("/api/v1/workspaces/" + wsA + "/tags").status()).isEqualTo(404);
        assertThat(bob.put("/api/v1/workspaces/" + wsA + "/clients/" + clientId, Map.of("name", "pwned")).status())
                .isEqualTo(404);

        // Bob's own workspace search does not leak Alice's records (results or counts).
        String wsB = bob.post("/api/v1/workspaces", Map.of("name", "Bob WS")).body().get("id").asString();
        ApiClient.Response search = bob.get("/api/v1/workspaces/" + wsB + "/search?q=confidential&tags=secretproject");
        assertThat(search.status()).isEqualTo(200);
        assertThat(search.body().get("total").asLong()).isZero();

        // Referencing another workspace's client id from Bob's workspace is rejected.
        assertThat(bob.post("/api/v1/workspaces/" + wsB + "/assessments", Map.of("title", "x", "typeCode",
                "WEB_PENTEST", "clientId", clientId)).status()).isEqualTo(400);
    }

    @Test
    void viewersCannotWrite(CapturedOutput output) {
        ApiClient admin = admin(output);
        String suffix = Long.toString(System.nanoTime());
        ApiClient manager = newUser(admin, "mgr" + suffix);
        ApiClient viewer = newUser(admin, "view" + suffix);
        String ws = manager.post("/api/v1/workspaces", Map.of("name", "Team")).body().get("id").asString();
        assertThat(manager.post("/api/v1/workspaces/" + ws + "/members",
                Map.of("username", "view" + suffix, "role", "VIEWER")).status()).isEqualTo(201);

        assertThat(viewer.get("/api/v1/workspaces/" + ws + "/clients").status()).isEqualTo(200);
        assertThat(viewer.post("/api/v1/workspaces/" + ws + "/clients", Map.of("name", "Nope")).status())
                .isEqualTo(403);
        assertThat(viewer.post("/api/v1/workspaces/" + ws + "/members",
                Map.of("username", "mgr" + suffix, "role", "VIEWER")).status()).isEqualTo(403);
    }
}
