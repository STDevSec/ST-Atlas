package com.stdevsec.atlas.report.internal;

import com.stdevsec.atlas.report.TemplateConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends a rendered bundle to Gotenberg (Chromium) and returns the PDF bytes. Gotenberg runs
 * on an internal network without egress, with JavaScript disabled (ADR 0005, T-12).
 */
@Component
public class GotenbergClient {

    public static class RenderFailedException extends RuntimeException {
        RenderFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GotenbergClient.class);

    private final RestClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    GotenbergClient(@Value("${atlas.gotenberg-url}") String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public byte[] convert(ReportRenderer.Bundle bundle, TemplateConfig t, Map<String, String> metadata) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        file(body, "index.html", bundle.indexHtml().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        file(body, "header.html", bundle.headerHtml().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        file(body, "footer.html", bundle.footerHtml().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        bundle.assets().forEach((name, bytes) -> file(body, name, bytes));
        boolean letter = t.paperSize() == TemplateConfig.PaperSize.LETTER;
        body.add("paperWidth", letter ? "8.5in" : "210mm");
        body.add("paperHeight", letter ? "11in" : "297mm");
        body.add("marginTop", t.margins().topMm() + "mm");
        body.add("marginBottom", t.margins().bottomMm() + "mm");
        body.add("marginLeft", t.margins().leftMm() + "mm");
        body.add("marginRight", t.margins().rightMm() + "mm");
        body.add("landscape", String.valueOf(t.orientation() == TemplateConfig.Orientation.LANDSCAPE));
        body.add("printBackground", "true");
        // Gotenberg 9: PDF metadata is a post-processing stage, not a form field.
        java.util.Map<String, String> stage = new java.util.LinkedHashMap<>();
        stage.put("op", "set-metadata");
        metadata.forEach((k, v) -> stage.put(k.toLowerCase(java.util.Locale.ROOT), v));
        body.add("stages", json.writeValueAsString(java.util.List.of(stage)));
        try {
            byte[] pdf = http.post().uri("/forms/chromium/convert/html")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            if (pdf == null || pdf.length < 5 || !new String(pdf, 0, 5).equals("%PDF-")) {
                throw new RenderFailedException("Renderer returned no PDF", null);
            }
            return pdf;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("Gotenberg rejected the render: HTTP {} {}", e.getStatusCode().value(),
                    e.getResponseBodyAsString().substring(0, Math.min(500, e.getResponseBodyAsString().length())));
            throw new RenderFailedException("PDF renderer rejected the document", e);
        } catch (RestClientException e) {
            log.warn("Gotenberg unavailable: {}", e.getMessage());
            throw new RenderFailedException("PDF renderer unavailable", e);
        }
    }

    private static void file(MultiValueMap<String, Object> body, String name, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.formData().name("files").filename(name).build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        body.add("files", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return name;
            }
        }, headers));
    }

    /** Page objects in a Chromium-generated PDF (informational only). */
    static Integer countPages(byte[] pdf) {
        String s = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/Type\\s*/Page(?!s)").matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n == 0 ? null : n;
    }
}
