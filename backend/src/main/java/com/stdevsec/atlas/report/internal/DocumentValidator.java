package com.stdevsec.atlas.report.internal;

import com.stdevsec.atlas.shared.BadRequestException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates editor documents before they are stored: size, depth, known node types,
 * images by attachment reference only (no inline Base64), safe links.
 */
public final class DocumentValidator {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DEPTH = 40;

    static final Set<String> NODES = Set.of("doc", "paragraph", "heading", "text", "bulletList", "orderedList",
            "listItem", "blockquote", "codeBlock", "horizontalRule", "hardBreak", "image", "table", "tableRow",
            "tableHeader", "tableCell", "pageBreak", "findingRef", "findingsTable", "findingSnapshot");
    static final Set<String> MARKS = Set.of("bold", "italic", "underline", "strike", "code", "link", "highlight",
            "subscript", "superscript");

    public record Result(JsonNode root, Set<UUID> attachmentIds, Set<UUID> findingIds) {}

    private DocumentValidator() {}

    public static Result validate(String json, JsonMapper mapper) {
        if (json == null || json.length() > MAX_BYTES) {
            throw new BadRequestException("Document is empty or too large");
        }
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (RuntimeException e) {
            throw new BadRequestException("Document is not valid JSON");
        }
        if (!"doc".equals(type(root))) {
            throw new BadRequestException("Document root must be a doc node");
        }
        Set<UUID> attachments = new HashSet<>();
        Set<UUID> findings = new HashSet<>();
        walk(root, 0, attachments, findings);
        return new Result(root, attachments, findings);
    }

    private static void walk(JsonNode node, int depth, Set<UUID> attachments, Set<UUID> findings) {
        if (depth > MAX_DEPTH) {
            throw new BadRequestException("Document is nested too deeply");
        }
        String type = type(node);
        if (type == null || !NODES.contains(type)) {
            throw new BadRequestException("Unsupported document element: " + (type == null ? "?" : type));
        }
        JsonNode attrs = node.path("attrs");
        switch (type) {
            case "image" -> {
                String src = attrs.path("src").isString() ? attrs.path("src").stringValue() : null;
                if (src != null && (src.startsWith("data:") || src.length() > 2048)) {
                    throw new BadRequestException("Images must be uploaded, not embedded");
                }
                attachments.add(uuid(attrs.path("attachmentId"), "image"));
            }
            case "findingRef" -> findings.add(uuid(attrs.path("findingId"), "finding reference"));
            default -> { }
        }
        for (JsonNode mark : node.path("marks")) {
            String m = type(mark);
            if (m == null || !MARKS.contains(m)) {
                throw new BadRequestException("Unsupported text mark");
            }
            if ("link".equals(m) && Html.safeHref(mark.path("attrs").path("href").asString("")) == null) {
                throw new BadRequestException("Links must be http, https or mailto");
            }
        }
        for (JsonNode child : node.path("content")) {
            walk(child, depth + 1, attachments, findings);
        }
    }

    static String type(JsonNode node) {
        JsonNode t = node.get("type");
        return t != null && t.isString() ? t.stringValue() : null;
    }

    private static UUID uuid(JsonNode node, String what) {
        try {
            return UUID.fromString(node.asString(""));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid " + what);
        }
    }
}
