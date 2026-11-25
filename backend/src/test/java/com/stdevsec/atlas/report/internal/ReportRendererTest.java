package com.stdevsec.atlas.report.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.stdevsec.atlas.report.TemplateConfig;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T-09/T-12/T-14: the renderer only emits markup it generated itself. */
class ReportRendererTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID IMG = UUID.fromString("0190a000-0000-7000-8000-000000000001");

    private static String render(String doc, Map<String, String> vars) {
        JsonNode node = JSON.readTree(doc);
        return ReportRenderer.render(new ReportRenderer.Input(node, TemplateConfig.standard(), vars,
                id -> id.equals(IMG) ? new ReportRenderer.Image(new byte[] {1, 2, 3}, "image/png") : null, null, null,
                Labels.EN)).indexHtml();
    }

    @Test
    void escapesTextAndDropsScriptCarryingMarkup() {
        String html = render("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"<script>alert(1)</script><img src=x onerror=alert(2)>"}]}]}""", Map.of());
        assertThat(html).doesNotContain("<script>alert").doesNotContain("<img src=x")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void newlinesInTextBecomeLineBreaksAfterEscaping() {
        String html = render("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"portal.example\\n<b>api</b>.example"}]}]}""", Map.of());
        assertThat(html).contains("portal.example<br>&lt;b&gt;api&lt;/b&gt;.example");
    }

    @Test
    void onlySafeLinkSchemesSurvive() {
        String html = render("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"bad","marks":[{"type":"link","attrs":{"href":"javascript:alert(1)"}}]},
                  {"type":"text","text":"good","marks":[{"type":"link","attrs":{"href":"https://owasp.org/x?a=1&b=\\"2"}}]}
                ]}]}""", Map.of());
        assertThat(html).doesNotContain("javascript:").contains("href=\"https://owasp.org/x?a=1&amp;b=&quot;2\"");
    }

    @Test
    void imagesAreLocalFilesNeverUrls() {
        String doc = """
                {"type":"doc","content":[{"type":"image","attrs":{"attachmentId":"%s","caption":"Login <b>page</b>",
                 "src":"http://169.254.169.254/latest/meta-data"}}]}""".formatted(IMG);
        ReportRenderer.Bundle b = ReportRenderer.render(new ReportRenderer.Input(JSON.readTree(doc),
                TemplateConfig.standard(), Map.of(), id -> new ReportRenderer.Image(new byte[] {1}, "image/png"), null,
                null, Labels.EN));
        assertThat(b.indexHtml()).doesNotContain("169.254").contains("src=\"img-")
                .contains("Login &lt;b&gt;page&lt;/b&gt;");
        assertThat(b.assets()).hasSize(1);
    }

    @Test
    void variablesAreAllowListedAndEscaped() {
        String html = render("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"Client: {{client.name}} / {{system.env}} / {{ assessment.title }}"}]}]}""",
                Map.of("client.name", "<Evil & Co>", "assessment.title", "Web test"));
        assertThat(html).contains("Client: &lt;Evil &amp; Co&gt; / {{system.env}} / Web test");
    }

    @Test
    void unknownNodesProduceNothing() {
        String html = render("""
                {"type":"doc","content":[{"type":"iframe","attrs":{"src":"https://evil"}},{"type":"paragraph"}]}""",
                Map.of());
        assertThat(html).doesNotContain("iframe").doesNotContain("evil");
    }

    @Test
    void headerFooterUsePagePlaceholders() {
        String footer = Variables.applyHeaderFooter("Page {{page.number}} of {{page.total}} <b>", Map.of());
        assertThat(footer).contains("<span class=\"pageNumber\"></span>").contains("&lt;b&gt;");
    }

    @Test
    void tidyRemovesDanglingSeparators() {
        assertThat(Variables.tidy("Web test — ")).isEqualTo("Web test");
        assertThat(Variables.tidy(" · Acme · ")).isEqualTo("Acme");
        assertThat(Variables.tidy("A — B")).isEqualTo("A — B");
    }
}
