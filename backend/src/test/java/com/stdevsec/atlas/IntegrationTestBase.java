package com.stdevsec.atlas;

import com.stdevsec.atlas.system.SetupService;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Full application on a random port against PostgreSQL 18 (Testcontainers). The context
 * (and database) is shared by all integration tests, so each test uses unique names.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"atlas.data-dir=target/test-data", "atlas.gotenberg-url=http://127.0.0.1:1"})
@ExtendWith(OutputCaptureExtension.class)
public abstract class IntegrationTestBase {

    protected static final String ADMIN = "admin";
    protected static final String ADMIN_PASSWORD = "correct horse battery staple";
    private static final Pattern TOKEN = Pattern.compile("setup token:\\s+([0-9a-f]{32})");

    @LocalServerPort
    protected int port;

    @Autowired
    protected SetupService setupService;

    protected ApiClient client() {
        return new ApiClient(port);
    }

    /** Completes first-run setup once per test JVM, reading the token from the log like an admin would. */
    protected synchronized void ensureSetup(CapturedOutput output) {
        if (!setupService.isPending()) { // per application context (each has its own database)
            return;
        }
        setupService.issueTokenIfPending();
        Matcher m = TOKEN.matcher(output.getAll().replaceAll("\\s+", " "));
        String token = null;
        while (m.find()) {
            token = m.group(1);
        }
        ApiClient c = client();
        ApiClient.Response r = c.post("/api/v1/setup", Map.of("token", token, "organizationName", "Acme Security",
                "username", ADMIN, "displayName", "Admin", "password", ADMIN_PASSWORD, "locale", "en",
                "timeZone", "America/Mexico_City", "workspaceName", "Main"));
        if (r.status() != 201) {
            throw new IllegalStateException("setup failed: " + r.raw());
        }
    }

    protected ApiClient admin(CapturedOutput output) {
        ensureSetup(output);
        ApiClient c = client();
        ApiClient.Response r = c.login(ADMIN, ADMIN_PASSWORD);
        if (r.status() != 200) {
            throw new IllegalStateException("admin login failed: " + r.raw());
        }
        return c;
    }

    protected String firstWorkspace(ApiClient c) {
        return c.get("/api/v1/auth/me").body().get("workspaces").get(0).get("id").asString();
    }
}
