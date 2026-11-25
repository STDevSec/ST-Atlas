package com.stdevsec.atlas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Report → pasted image → finding → real PDF via Gotenberg (same hardened flags as compose.yaml)
 * → immutable snapshot versions.
 */
class ReportPdfIT extends IntegrationTestBase {

    static final GenericContainer<?> GOTENBERG = new GenericContainer<>("gotenberg/gotenberg:9.10.0")
            .withCommand("gotenberg", "--chromium-disable-javascript=true", "--webhook-disable=true",
                    "--api-disable-download-from=true")
            .withExposedPorts(3000)
            .waitingFor(Wait.forHttp("/health").forPort(3000));

    static {
        GOTENBERG.start();
    }

    @DynamicPropertySource
    static void gotenberg(DynamicPropertyRegistry registry) {
        registry.add("atlas.gotenberg-url", () -> "http://" + GOTENBERG.getHost() + ":" + GOTENBERG.getMappedPort(3000));
    }

    @Autowired
    JdbcClient jdbc;

    @Test
    void endToEndPdfWithImageAndImmutableVersions(CapturedOutput output) throws Exception {
        ApiClient c = admin(output);
        String ws = firstWorkspace(c);
        String base = "/api/v1/workspaces/" + ws;
        String assessment = c.post(base + "/assessments", Map.of("title", "Report IT web test",
                "typeCode", "WEB_PENTEST")).body().get("id").asString();
        String finding = c.post(base + "/assessments/" + assessment + "/findings", Map.of(
                "title", "SQL injection in login", "description", "Original description",
                "cvss31Vector", "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H")).body().get("id").asString();

        // "Paste" a screenshot: upload a real PNG as an attachment.
        BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        ApiClient.Response up = c.upload(base + "/attachments", "screenshot.png", "image/png", png.toByteArray());
        assertThat(up.status()).as(up.raw()).isEqualTo(201);
        String attachment = up.body().get("id").asString();

        ApiClient.Response report = c.post(base + "/assessments/" + assessment + "/reports", Map.of());
        assertThat(report.status()).isEqualTo(201);
        String reportId = report.body().get("id").asString();
        String doc = """
                {"type":"doc","content":[
                 {"type":"heading","attrs":{"level":1},"content":[{"type":"text","text":"Executive summary"}]},
                 {"type":"paragraph","content":[{"type":"text","text":"Prepared for {{client.name}} by {{report.author}}."}]},
                 {"type":"image","attrs":{"attachmentId":"%s","caption":"Login page"}},
                 {"type":"findingsTable"},
                 {"type":"findingRef","attrs":{"findingId":"%s"}},
                 {"type":"codeBlock","attrs":{"language":"bash"},"content":[{"type":"text","text":"sqlmap -u https://target"}]}
                ]}""".formatted(attachment, finding);
        ApiClient.Response saved = c.put(base + "/reports/" + reportId, Map.of("title", "Web assessment report",
                "document", doc, "revision", report.body().get("revision").asInt(), "classification", "CONFIDENTIAL"));
        assertThat(saved.status()).isEqualTo(200);

        // Stale revision → conflict (two tabs editing).
        assertThat(c.put(base + "/reports/" + reportId, Map.of("title", "x", "document", doc, "revision", 0,
                "classification", "CONFIDENTIAL")).status()).isEqualTo(409);

        ApiClient.Response v1 = c.post(base + "/reports/" + reportId + "/render", Map.of());
        assertThat(v1.status()).as(v1.raw()).isEqualTo(201);
        assertThat(v1.body().get("number").asInt()).isEqualTo(1);
        ApiClient.Response pdf = c.get(base + "/attachments/" + v1.body().get("pdfAttachmentId").asString() + "/content");
        assertThat(pdf.status()).isEqualTo(200);
        assertThat(new String(pdf.bytes(), 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.http().headers().firstValue("Content-Type").orElse("")).isEqualTo("application/pdf");
        assertThat(pdf.bytes().length).isGreaterThan(5_000);
        java.nio.file.Files.write(java.nio.file.Path.of("target", "report-it-sample.pdf"), pdf.bytes());

        // Change the finding, render again: v1's snapshot keeps the old text, v2 has the new one.
        c.put(base + "/findings/" + finding, Map.of("title", "SQL injection in login",
                "description", "Updated description", "severity", "CRITICAL"));
        ApiClient.Response v2 = c.post(base + "/reports/" + reportId + "/render", Map.of("finalized", true));
        assertThat(v2.body().get("number").asInt()).isEqualTo(2);
        String v1Doc = jdbc.sql("select document::text from report_version where report_id = cast(:r as uuid) and number = 1")
                .param("r", reportId).query(String.class).single();
        String v2Doc = jdbc.sql("select document::text from report_version where report_id = cast(:r as uuid) and number = 2")
                .param("r", reportId).query(String.class).single();
        assertThat(v1Doc).contains("Original description").doesNotContain("Updated description");
        assertThat(v2Doc).contains("Updated description");

        // Finalized versions cannot be modified, even with direct SQL (T-15).
        assertThatThrownBy(() -> jdbc.sql("update report_version set page_count = 99 where report_id = cast(:r as uuid) and number = 2")
                .param("r", reportId).update()).hasMessageContaining("immutable");

        assertThat(c.get(base + "/reports/" + reportId + "/versions").body().size()).isEqualTo(2);
        // The report text is searchable.
        assertThat(c.get(base + "/search?q=sqlmap").body().get("items").valueStream()
                .map(n -> n.get("entityType").asString()).toList()).contains("report");
    }

    @Test
    void documentsCannotReferenceForeignAttachmentsOrFindings(CapturedOutput output) {
        ApiClient c = admin(output);
        String ws = firstWorkspace(c);
        String base = "/api/v1/workspaces/" + ws;
        String assessment = c.post(base + "/assessments", Map.of("title", "Isolation report", "typeCode", "CUSTOM"))
                .body().get("id").asString();
        ApiClient.Response report = c.post(base + "/assessments/" + assessment + "/reports", Map.of());
        String id = report.body().get("id").asString();
        String foreign = "{\"type\":\"doc\",\"content\":[{\"type\":\"image\",\"attrs\":{\"attachmentId\":\""
                + java.util.UUID.randomUUID() + "\"}}]}";
        assertThat(c.put(base + "/reports/" + id, Map.of("title", "t", "document", foreign, "revision",
                report.body().get("revision").asInt(), "classification", "INTERNAL")).status()).isEqualTo(404);
        String base64 = "{\"type\":\"doc\",\"content\":[{\"type\":\"image\",\"attrs\":{\"src\":\"data:image/png;base64,AA\",\"attachmentId\":\""
                + java.util.UUID.randomUUID() + "\"}}]}";
        assertThat(c.put(base + "/reports/" + id, Map.of("title", "t", "document", base64, "revision",
                report.body().get("revision").asInt(), "classification", "INTERNAL")).status()).isEqualTo(400);
    }

    @Test
    void timeTrackingTimerAndValidation(CapturedOutput output) {
        ApiClient c = admin(output);
        String ws = firstWorkspace(c);
        String base = "/api/v1/workspaces/" + ws;
        String assessment = c.post(base + "/assessments", Map.of("title", "Timed work", "typeCode", "NETWORK_PENTEST"))
                .body().get("id").asString();
        if (c.get(base + "/time-entries/timer").status() == 200) {
            c.post(base + "/time-entries/timer/stop", Map.of());
        }
        assertThat(c.post(base + "/time-entries/timer/start", Map.of("assessmentId", assessment, "billable", true))
                .status()).isEqualTo(201);
        assertThat(c.post(base + "/time-entries/timer/start", Map.of("assessmentId", assessment, "billable", true))
                .status()).isEqualTo(409);
        assertThat(c.get(base + "/time-entries/timer").body().get("running").asBoolean()).isTrue();
        assertThat(c.post(base + "/time-entries/timer/stop", Map.of()).body().get("running").asBoolean()).isFalse();

        assertThat(c.post(base + "/time-entries", Map.of("assessmentId", assessment, "startedAt",
                "2026-01-10T10:00:00Z", "endedAt", "2026-01-10T09:00:00Z", "billable", true)).status()).isEqualTo(400);
        assertThat(c.post(base + "/time-entries", Map.of("assessmentId", assessment, "startedAt",
                "2026-01-10T09:00:00Z", "endedAt", "2026-01-10T11:30:00Z", "billable", true,
                "description", "=cmd|' /C calc'!A0")).status()).isEqualTo(201);
        String csv = c.get(base + "/time-entries/export.csv?from=2026-01-01&to=2026-01-31").raw();
        assertThat(csv).contains("2.50").contains("\"'=cmd");

        ApiClient.Response dash = c.get(base + "/dashboard?from=2026-01-01");
        assertThat(dash.status()).isEqualTo(200);
        assertThat(dash.body().get("totalHours").asDouble()).isGreaterThanOrEqualTo(2.5);
        assertThat(dash.body().get("assessmentsByType").has("NETWORK_PENTEST")).isTrue();
        List.of();
    }
}
