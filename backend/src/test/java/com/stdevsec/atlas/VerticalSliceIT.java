package com.stdevsec.atlas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

/** Client → assessment → finding → global search (the first part of the spec §29 slice). */
class VerticalSliceIT extends IntegrationTestBase {

    @Test
    void clientAssessmentFindingAndSearch(CapturedOutput output) {
        ApiClient c = admin(output);
        String ws = c.post("/api/v1/workspaces", Map.of("name", "Slice " + System.nanoTime())).body().get("id").asString();
        String base = "/api/v1/workspaces/" + ws;

        String clientId = c.post(base + "/clients", Map.of("name", "Globex", "legalName", "Globex S.A. de C.V.",
                "contacts", List.of(Map.of("name", "Hank", "email", "hank@globex.example", "kind", "TECHNICAL"))))
                .body().get("id").asString();

        ApiClient.Response a = c.post(base + "/assessments", Map.of("title", "Portal web externo",
                "typeCode", "WEB_PENTEST", "clientId", clientId, "scope", "https://portal.globex.example",
                "tags", List.of("#Web", "owasp")));
        assertThat(a.status()).isEqualTo(201);
        String assessmentId = a.body().get("id").asString();
        assertThat(a.body().get("code").asString()).matches("ATL-\\d{4}-\\d{4}");
        assertThat(a.body().get("tags").toString()).contains("web").contains("owasp").doesNotContain("#");

        ApiClient.Response f = c.post(base + "/assessments/" + assessmentId + "/findings", Map.of(
                "title", "Reflected XSS in search parameter",
                "description", "El parámetro de búsqueda refleja entrada sin codificar (cross-site scripting).",
                "cvss31Vector", "CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N",
                "cwe", List.of("cwe-79"), "tags", List.of("xss")));
        assertThat(f.status()).isEqualTo(201);
        assertThat(f.body().get("cvss31Score").asDouble()).isEqualTo(6.1);
        assertThat(f.body().get("severity").asString()).isEqualTo("MEDIUM");
        assertThat(f.body().get("code").asString()).isEqualTo("FND-001");

        // Lifecycle: illegal jump rejected, legal moves accepted.
        assertThat(c.post(base + "/assessments/" + assessmentId + "/transition", Map.of("status", "COMPLETED"))
                .status()).isEqualTo(409);
        assertThat(c.post(base + "/assessments/" + assessmentId + "/transition", Map.of("status", "IN_PROGRESS"))
                .status()).isEqualTo(200);

        // Search: English stem, Spanish word, partial word, tag filters, other filters.
        assertThat(titles(c.get(base + "/search?q=scripting"))).contains("Reflected XSS in search parameter");
        assertThat(titles(c.get(base + "/search?q=b%C3%BAsqueda"))).contains("Reflected XSS in search parameter");
        assertThat(titles(c.get(base + "/search?q=reflec"))).contains("Reflected XSS in search parameter");
        assertThat(titles(c.get(base + "/search?tags=web&tags=owasp&tagMode=AND"))).contains("Portal web externo");
        assertThat(titles(c.get(base + "/search?tags=xss&tags=nothing&tagMode=AND"))).isEmpty();
        assertThat(titles(c.get(base + "/search?tags=xss&tags=nothing&tagMode=OR")))
                .contains("Reflected XSS in search parameter");
        assertThat(titles(c.get(base + "/search?q=xss&severity=CRITICAL"))).isEmpty();

        // Workspace-wide findings list with filters (and without them).
        ApiClient.Response all = c.get(base + "/findings");
        assertThat(all.status()).as(all.raw()).isEqualTo(200);
        assertThat(all.body().get("items").get(0).get("assessmentCode").asString()).startsWith("ATL-");
        assertThat(c.get(base + "/findings?severity=MEDIUM&q=xss").body().get("total").asLong()).isEqualTo(1);
        assertThat(c.get(base + "/findings?severity=CRITICAL").body().get("total").asLong()).isZero();
        assertThat(c.get(base + "/findings?status=FIXED").body().get("total").asLong()).isZero();

        ApiClient.Response hit = c.get(base + "/search?q=scripting");
        assertThat(hit.body().get("items").get(0).get("snippet").asString()).contains("⁣");

        // Invalid CVSS vector and CVSS on a type that does not use it are rejected.
        assertThat(c.post(base + "/assessments/" + assessmentId + "/findings", Map.of("title", "bad",
                "cvss31Vector", "CVSS:3.1/AV:X")).status()).isEqualTo(400);
        String forensic = c.post(base + "/assessments", Map.of("title", "Memory acquisition case",
                "typeCode", "DIGITAL_FORENSICS")).body().get("id").asString();
        assertThat(c.post(base + "/assessments/" + forensic + "/findings", Map.of("title", "Artifact",
                "severity", "INFORMATIONAL", "cvss31Vector", "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"))
                .status()).isEqualTo(400);
        assertThat(c.post(base + "/assessments/" + forensic + "/findings", Map.of("title", "Volatility pslist output",
                "severity", "INFORMATIONAL")).status()).isEqualTo(201);
        assertThat(titles(c.get(base + "/search?q=volatility"))).contains("Volatility pslist output");
    }

    private static List<String> titles(ApiClient.Response r) {
        assertThat(r.status()).isEqualTo(200);
        return r.body().get("items").valueStream().map(n -> n.get("title").asString()).toList();
    }
}
