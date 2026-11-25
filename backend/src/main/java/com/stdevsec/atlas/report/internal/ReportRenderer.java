package com.stdevsec.atlas.report.internal;

import com.stdevsec.atlas.report.TemplateConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

/**
 * Converts a snapshot document into print HTML. Pure function of its inputs; it never
 * fetches anything. Only allow-listed nodes produce markup, all text is escaped, links are
 * filtered, and images are referenced as local files sent alongside (no URLs) (T-09, T-12).
 */
public final class ReportRenderer {

    /** What the renderer needs besides the document. */
    public record Input(JsonNode document, TemplateConfig template, Map<String, String> variables,
            Function<UUID, Image> images, Image organizationLogo, Image clientLogo, Labels labels) {}

    public record Image(byte[] bytes, String mimeType) {
        String extension() {
            return switch (mimeType) {
                case "image/png" -> "png";
                case "image/jpeg" -> "jpg";
                case "image/gif" -> "gif";
                case "image/webp" -> "webp";
                default -> "bin";
            };
        }
    }

    /** Rendered files for the HTML-to-PDF converter. */
    public record Bundle(String indexHtml, String headerHtml, String footerHtml, Map<String, byte[]> assets) {}

    private final Input in;
    private final Map<String, byte[]> assets = new LinkedHashMap<>();
    private final StringBuilder html = new StringBuilder(64 * 1024);

    private ReportRenderer(Input in) {
        this.in = in;
    }

    public static Bundle render(Input in) {
        return new ReportRenderer(in).run();
    }

    private Bundle run() {
        TemplateConfig t = in.template();
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>")
                .append(Html.esc(Variables.apply(t.title(), in.variables()))).append("</title><style>")
                .append(css(t)).append("</style></head><body>");
        if (t.watermarkText() != null && !t.watermarkText().isBlank()) {
            html.append("<div class=\"watermark\">").append(Html.esc(t.watermarkText())).append("</div>");
        }
        if (t.showCover()) {
            cover(t);
        }
        html.append("<main>");
        for (JsonNode child : in.document().path("content")) {
            block(child);
        }
        html.append("</main></body></html>");
        return new Bundle(html.toString(), headerFooter(t.headerText(), true), headerFooter(t.footerText(), false),
                assets);
    }

    // ------------------------------------------------------------------ layout
    private void cover(TemplateConfig t) {
        html.append("<section class=\"cover\"><div class=\"logos logos-").append(t.logoPosition().name().toLowerCase())
                .append("\">");
        if (t.showOrganizationLogo() && in.organizationLogo() != null) {
            html.append("<img class=\"logo\" alt=\"\" src=\"").append(asset("org-logo", in.organizationLogo())).append("\">");
        }
        if (t.showClientLogo() && in.clientLogo() != null) {
            html.append("<img class=\"logo\" alt=\"\" src=\"").append(asset("client-logo", in.clientLogo())).append("\">");
        }
        html.append("</div><div class=\"cover-body\">");
        html.append("<p class=\"classification\">").append(Html.esc(in.labels().classification(t.classification())))
                .append("</p>");
        html.append("<h1 class=\"cover-title\">").append(Html.esc(Variables.tidy(Variables.apply(t.title(), in.variables()))))
                .append("</h1>");
        if (t.subtitle() != null && !t.subtitle().isBlank()) {
            html.append("<p class=\"cover-subtitle\">").append(Html.esc(Variables.tidy(Variables.apply(t.subtitle(), in.variables()))))
                    .append("</p>");
        }
        html.append("<table class=\"cover-meta\">");
        meta(in.labels().get("assessment"), in.variables().get("assessment.code"));
        meta(in.labels().get("client"), in.variables().get("client.name"));
        meta(in.labels().get("period"), period());
        meta(in.labels().get("author"), in.variables().get("report.author"));
        meta(in.labels().get("version"), in.variables().get("report.version"));
        meta(in.labels().get("date"), in.variables().get("report.date"));
        html.append("</table></div></section>");
    }

    private String period() {
        String s = in.variables().getOrDefault("assessment.startDate", "");
        String e = in.variables().getOrDefault("assessment.endDate", "");
        return s.isEmpty() && e.isEmpty() ? null : (s + " — " + e);
    }

    private void meta(String label, String value) {
        if (value != null && !value.isBlank()) {
            html.append("<tr><th>").append(Html.esc(label)).append("</th><td>").append(Html.esc(value))
                    .append("</td></tr>");
        }
    }

    private String headerFooter(String text, boolean header) {
        TemplateConfig t = in.template();
        String content = Variables.applyHeaderFooter(text, in.variables());
        if (!header && t.pageNumbers() && !content.contains("pageNumber")) {
            content += (content.isEmpty() ? "" : "&nbsp;·&nbsp;")
                    + "<span class=\"pageNumber\"></span>&nbsp;/&nbsp;<span class=\"totalPages\"></span>";
        }
        String font = t.fontFamily() == TemplateConfig.FontFamily.SERIF ? "serif" : "sans-serif";
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>body{margin:0 " + t.margins().leftMm()
                + "mm;font-family:" + font + ";font-size:8pt;color:#555;width:100%;" + (header
                ? "border-bottom:0.5pt solid " + t.primaryColor() + ";padding-bottom:2mm"
                : "border-top:0.5pt solid #ccc;padding-top:2mm;text-align:right") + "}</style></head><body>"
                + content + "</body></html>";
    }

    // ------------------------------------------------------------------ blocks
    private void block(JsonNode node) {
        String type = DocumentValidator.type(node);
        if (type == null) {
            return;
        }
        switch (type) {
            case "paragraph" -> {
                html.append("<p").append(align(node)).append('>');
                inline(node);
                html.append("</p>");
            }
            case "heading" -> {
                int level = Math.max(1, Math.min(4, node.path("attrs").path("level").asInt(1)));
                html.append("<h").append(level + 1).append('>');
                inline(node);
                html.append("</h").append(level + 1).append('>');
            }
            case "bulletList" -> list("ul", node);
            case "orderedList" -> list("ol", node);
            case "blockquote" -> {
                html.append("<blockquote>");
                node.path("content").forEach(this::block);
                html.append("</blockquote>");
            }
            case "codeBlock" -> {
                String lang = node.path("attrs").path("language").asString("");
                html.append("<pre class=\"code\"");
                if (lang.matches("[a-zA-Z0-9+#-]{1,20}")) {
                    html.append(" data-lang=\"").append(Html.esc(lang)).append('"');
                }
                html.append("><code>");
                for (JsonNode t : node.path("content")) {
                    html.append(Html.esc(t.path("text").asString("")));
                }
                html.append("</code></pre>");
            }
            case "horizontalRule" -> html.append("<hr>");
            case "pageBreak" -> html.append("<div class=\"page-break\"></div>");
            case "image" -> image(node);
            case "table" -> table(node);
            case "findingSnapshot" -> finding(node.path("attrs").path("finding"));
            case "findingsTable" -> findingsTable(node.path("attrs").path("rows"));
            default -> { } // unknown or live-only nodes produce nothing
        }
    }

    private void list(String tag, JsonNode node) {
        html.append('<').append(tag).append('>');
        for (JsonNode item : node.path("content")) {
            html.append("<li>");
            item.path("content").forEach(this::block);
            html.append("</li>");
        }
        html.append("</").append(tag).append('>');
    }

    private void image(JsonNode node) {
        JsonNode attrs = node.path("attrs");
        UUID id;
        try {
            id = UUID.fromString(attrs.path("attachmentId").asString(""));
        } catch (IllegalArgumentException e) {
            return;
        }
        Image img = in.images().apply(id);
        if (img == null) {
            return;
        }
        int width = Math.max(10, Math.min(100, attrs.path("width").asInt(100)));
        String caption = attrs.path("caption").asString("");
        html.append("<figure><img alt=\"").append(Html.esc(attrs.path("alt").asString(""))).append("\" src=\"")
                .append(asset("img-" + id, img)).append("\" style=\"width:").append(width).append("%\">");
        if (!caption.isBlank()) {
            html.append("<figcaption>").append(Html.esc(caption)).append("</figcaption>");
        }
        html.append("</figure>");
    }

    private void table(JsonNode node) {
        html.append("<table class=\"content-table\">");
        for (JsonNode row : node.path("content")) {
            html.append("<tr>");
            for (JsonNode cell : row.path("content")) {
                String tag = "tableHeader".equals(DocumentValidator.type(cell)) ? "th" : "td";
                int colspan = Math.max(1, Math.min(20, cell.path("attrs").path("colspan").asInt(1)));
                html.append('<').append(tag);
                if (colspan > 1) {
                    html.append(" colspan=\"").append(colspan).append('"');
                }
                html.append('>');
                cell.path("content").forEach(this::block);
                html.append("</").append(tag).append('>');
            }
            html.append("</tr>");
        }
        html.append("</table>");
    }

    private void finding(JsonNode f) {
        if (f.isMissingNode() || f.isNull()) {
            html.append("<p class=\"missing\">").append(Html.esc(in.labels().get("findingMissing"))).append("</p>");
            return;
        }
        String severity = f.path("severity").asString("");
        // Heading + metadata kept together so a finding title is never orphaned at a page end.
        html.append("<section class=\"finding\"><div class=\"finding-head\"><h3><span class=\"code\">").append(Html.esc(f.path("code").asString("")))
                .append("</span> ").append(Html.esc(f.path("title").asString(""))).append(" <span class=\"sev sev-")
                .append(Html.esc(severity.toLowerCase())).append("\">").append(Html.esc(in.labels().severity(severity)))
                .append("</span></h3><table class=\"finding-meta\">");
        meta(in.labels().get("status"), in.labels().status(f.path("status").asString("")));
        if (in.template().showCvss()) {
            String v31 = f.path("cvss31Vector").asString("");
            if (!v31.isEmpty()) {
                meta("CVSS 3.1", f.path("cvss31Score").asString("") + " (" + v31 + ")");
            }
            String v40 = f.path("cvss40Vector").asString("");
            if (!v40.isEmpty()) {
                meta("CVSS 4.0", (f.path("cvss40Score").asString("") + " (" + v40 + ")").trim());
            }
        }
        meta("CWE", join(f.path("cwe")));
        meta("CVE", join(f.path("cve")));
        html.append("</table></div>");
        section(in.labels().get("description"), f.path("description").asString(""));
        section(in.labels().get("technicalDetails"), f.path("technicalDetails").asString(""));
        section(in.labels().get("impact"), f.path("impact").asString(""));
        section(in.labels().get("recommendation"), f.path("recommendation").asString(""));
        if (f.path("references").size() > 0) {
            html.append("<h4>").append(Html.esc(in.labels().get("references"))).append("</h4><ul class=\"refs\">");
            for (JsonNode r : f.path("references")) {
                String ref = r.asString("");
                String href = Html.safeHref(ref);
                html.append("<li>").append(href == null ? Html.esc(ref)
                        : "<a href=\"" + Html.esc(href) + "\">" + Html.esc(ref) + "</a>").append("</li>");
            }
            html.append("</ul>");
        }
        html.append("</section>");
    }

    private void findingsTable(JsonNode rows) {
        html.append("<table class=\"findings-summary\"><thead><tr><th>ID</th><th>")
                .append(Html.esc(in.labels().get("title"))).append("</th><th>")
                .append(Html.esc(in.labels().get("severity"))).append("</th>");
        if (in.template().showCvss()) {
            html.append("<th>CVSS</th>");
        }
        html.append("<th>").append(Html.esc(in.labels().get("status"))).append("</th></tr></thead><tbody>");
        for (JsonNode f : rows) {
            String severity = f.path("severity").asString("");
            html.append("<tr><td class=\"code\">").append(Html.esc(f.path("code").asString(""))).append("</td><td>")
                    .append(Html.esc(f.path("title").asString(""))).append("</td><td><span class=\"sev sev-")
                    .append(Html.esc(severity.toLowerCase())).append("\">")
                    .append(Html.esc(in.labels().severity(severity))).append("</span></td>");
            if (in.template().showCvss()) {
                html.append("<td>").append(Html.esc(f.path("cvss31Score").asString(f.path("cvss40Score").asString(""))))
                        .append("</td>");
            }
            html.append("<td>").append(Html.esc(in.labels().status(f.path("status").asString("")))).append("</td></tr>");
        }
        html.append("</tbody></table>");
    }

    private void section(String title, String text) {
        if (text != null && !text.isBlank()) {
            html.append("<h4>").append(Html.esc(title)).append("</h4>").append(Html.multiline(text));
        }
    }

    // ------------------------------------------------------------------ inline
    private void inline(JsonNode node) {
        for (JsonNode child : node.path("content")) {
            String type = DocumentValidator.type(child);
            if ("hardBreak".equals(type)) {
                html.append("<br>");
                continue;
            }
            if (!"text".equals(type)) {
                continue;
            }
            // Newlines typed into plain-text fields survive as "\n" in text nodes; HTML would collapse them.
            String text = Html.esc(Variables.apply(child.path("text").asString(""), in.variables())).replace("\n", "<br>");
            StringBuilder open = new StringBuilder();
            StringBuilder close = new StringBuilder();
            for (JsonNode mark : child.path("marks")) {
                String m = DocumentValidator.type(mark);
                String tag = switch (m == null ? "" : m) {
                    case "bold" -> "strong";
                    case "italic" -> "em";
                    case "underline" -> "u";
                    case "strike" -> "s";
                    case "code" -> "code";
                    case "highlight" -> "mark";
                    case "subscript" -> "sub";
                    case "superscript" -> "sup";
                    case "link" -> "a";
                    default -> null;
                };
                if (tag == null) {
                    continue;
                }
                if ("a".equals(tag)) {
                    String href = Html.safeHref(mark.path("attrs").path("href").asString(""));
                    if (href == null) {
                        continue;
                    }
                    open.append("<a href=\"").append(Html.esc(href)).append("\">");
                } else {
                    open.append('<').append(tag).append('>');
                }
                close.insert(0, "</" + tag + ">");
            }
            html.append(open).append(text).append(close);
        }
    }

    private static String align(JsonNode node) {
        String a = node.path("attrs").path("textAlign").asString("");
        return switch (a) {
            case "center", "right", "justify" -> " style=\"text-align:" + a + "\"";
            default -> "";
        };
    }

    private static String join(JsonNode arr) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode v : arr) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(v.asString(""));
        }
        return sb.toString();
    }

    private String asset(String base, Image img) {
        String name = base.replaceAll("[^a-zA-Z0-9-]", "") + "." + img.extension();
        assets.putIfAbsent(name, img.bytes());
        return name;
    }

    // --------------------------------------------------------------------- css
    private static String css(TemplateConfig t) {
        String font = t.fontFamily() == TemplateConfig.FontFamily.SERIF
                ? "'Noto Serif', 'DejaVu Serif', serif" : "'Noto Sans', 'DejaVu Sans', Arial, sans-serif";
        return """
                *{box-sizing:border-box}
                body{font-family:%1$s;font-size:%2$dpt;line-height:1.5;color:#1f2328;margin:0}
                h2,h3,h4,h5{color:%3$s;line-height:1.25;break-after:avoid;page-break-after:avoid}
                h2{font-size:1.6em;border-bottom:1.5pt solid %3$s;padding-bottom:3pt;margin-top:18pt}
                h3{font-size:1.3em;margin-top:14pt} h4{font-size:1.1em;margin:10pt 0 4pt} h5{font-size:1em}
                p{margin:0 0 6pt;orphans:3;widows:3}
                a{color:%4$s;text-decoration:none}
                blockquote{margin:8pt 0;padding:4pt 10pt;border-left:3pt solid %4$s;color:#555;background:#f6f8fa}
                pre.code{background:#f6f8fa;border:0.5pt solid #d0d7de;border-radius:3pt;padding:6pt 8pt;
                  font-family:'Noto Sans Mono','DejaVu Sans Mono',monospace;font-size:0.85em;white-space:pre-wrap;
                  word-break:break-all;break-inside:avoid}
                code{font-family:'Noto Sans Mono','DejaVu Sans Mono',monospace;font-size:0.9em;background:#f0f2f4;padding:0 2pt}
                pre code{background:none;padding:0}
                figure{margin:8pt 0;text-align:center;break-inside:avoid}
                figure img{max-width:100%%;height:auto;border:0.5pt solid #d0d7de}
                figcaption{font-size:0.85em;color:#555;margin-top:3pt;font-style:italic}
                table{border-collapse:collapse;width:100%%;margin:6pt 0;break-inside:avoid}
                th,td{border:0.5pt solid #d0d7de;padding:3pt 5pt;text-align:left;vertical-align:top;font-size:0.92em}
                th{background:#f0f3f7;color:#1f2328}
                hr{border:none;border-top:0.5pt solid #d0d7de;margin:10pt 0}
                .page-break{break-after:page;page-break-after:always}
                .cover{height:240mm;display:flex;flex-direction:column;break-after:page;page-break-after:always}
                .logos{display:flex;gap:12mm;align-items:center;min-height:22mm}
                .logos-center{justify-content:center} .logos-right{justify-content:flex-end}
                .logo{max-height:20mm;max-width:60mm}
                .cover-body{margin-top:55mm}
                .cover-title{font-size:28pt;color:%3$s;margin:4mm 0;border:none}
                .cover-subtitle{font-size:14pt;color:#555;margin-bottom:14mm}
                .classification{display:inline-block;border:1pt solid %3$s;color:%3$s;padding:1pt 6pt;font-size:8pt;
                  letter-spacing:1pt;text-transform:uppercase;font-weight:bold}
                .cover-meta{width:auto;border:none} .cover-meta th,.cover-meta td{border:none;background:none;padding:1.5pt 8pt 1.5pt 0}
                .cover-meta th{color:#555;font-weight:normal}
                .finding{break-inside:auto;margin-bottom:10pt}
                .finding-head{break-inside:avoid;page-break-inside:avoid}
                .finding h3{border-bottom:0.5pt solid #d0d7de;padding-bottom:3pt}
                .finding-meta{width:auto;min-width:60%%} .finding-meta th{width:30mm}
                .code{font-family:'Noto Sans Mono','DejaVu Sans Mono',monospace;color:#555;font-size:0.9em}
                .sev{display:inline-block;padding:0 5pt;border-radius:2pt;color:#fff;font-size:0.75em;
                  font-weight:bold;vertical-align:middle;text-transform:uppercase}
                .sev-critical{background:#7f1d1d}.sev-high{background:#b91c1c}.sev-medium{background:#b45309}
                .sev-low{background:#1d4ed8}.sev-informational{background:#4b5563}.sev-not_applicable{background:#9ca3af}
                .watermark{position:fixed;top:45%%;left:0;width:100%%;text-align:center;font-size:64pt;color:rgba(0,0,0,0.06);
                  transform:rotate(-30deg);z-index:-1;font-weight:bold}
                .missing{color:#b91c1c;font-style:italic}
                """.formatted(font, t.baseFontSizePt(), t.primaryColor(), t.accentColor());
    }
}
