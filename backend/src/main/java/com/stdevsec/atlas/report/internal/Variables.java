package com.stdevsec.atlas.report.internal;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {{variable}} substitution with a fixed allow-list (T-14): no expressions, no lookups by
 * arbitrary path. Unknown variables are left as written so authors notice typos.
 */
final class Variables {

    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([a-zA-Z]+\\.[a-zA-Z]+)\\s*}}");

    static final java.util.Set<String> SUPPORTED = java.util.Set.of("client.name", "client.logo", "organization.name",
            "organization.logo", "assessment.title", "assessment.type", "assessment.startDate", "assessment.endDate",
            "assessment.code", "report.author", "report.version", "report.classification", "report.date",
            "page.number", "page.total");

    private Variables() {}

    /** Replaces variables with plain values; the result is plain text (escape it afterwards). */
    static String apply(String text, Map<String, String> values) {
        if (text == null || text.indexOf("{{") < 0) {
            return text;
        }
        Matcher m = VAR.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement = SUPPORTED.contains(key) && values.containsKey(key) ? values.get(key) : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement == null ? "" : replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Removes separators left dangling by empty variables ("Web test — " → "Web test"). */
    static String tidy(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("^[\\s—·|-]+", "").replaceAll("[\\s—·|-]+$", "")
                .replaceAll("\\s*([—·|])\\s*[—·|]\\s*", " $1 ").trim();
    }

    /** For header/footer: page variables become Chromium print placeholders (escaped text otherwise). */
    static String applyHeaderFooter(String text, Map<String, String> values) {
        String withValues = tidy(apply(text == null ? "" : text, values));
        String escaped = Html.esc(withValues);
        return escaped.replace("{{page.number}}", "<span class=\"pageNumber\"></span>")
                .replace("{{page.total}}", "<span class=\"totalPages\"></span>");
    }
}
