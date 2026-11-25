package com.stdevsec.atlas.report.internal;

/** HTML escaping for text and attribute values. Everything user-controlled goes through here. */
final class Html {

    private Html() {}

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> {
                    if (c < 0x20 && c != '\n' && c != '\t') {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Only absolute http(s) and mailto links survive; anything else (javascript:, data:, file:) is dropped. */
    static String safeHref(String href) {
        if (href == null) {
            return null;
        }
        String h = href.trim();
        String lower = h.toLowerCase();
        if ((lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:"))
                && h.length() <= 2000 && h.chars().noneMatch(ch -> ch < 0x20)) {
            return h;
        }
        return null;
    }

    /** Paragraph text that may contain newlines (plain-text fields such as finding descriptions). */
    static String multiline(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String para : s.strip().split("\\n\\s*\\n")) {
            out.append("<p>").append(esc(para).replace("\n", "<br>")).append("</p>");
        }
        return out.toString();
    }
}
